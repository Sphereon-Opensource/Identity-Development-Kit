/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.mdoc.engagement

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.cose.CoseKeyCborCodec
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.DeviceEngagementCborCodec
import com.sphereon.mdoc.transfer.MdocRetrievalEvent
import com.sphereon.mdoc.transfer.MdocRetrievalState
import com.sphereon.mdoc.transfer.TransferInstance
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.reader.ReaderEngagementCborCodec
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import software.amazon.app.platform.scope.coroutine.CoroutineScopeScoped
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/*
 * Enum representing the type of engagement (ISO 18013-5 and 18013-7).
 */

/**
 * Constants for MdocEngagementManagerImpl timing and configuration.
 */
private object EngagementManagerConstants {
    /** Delay to allow event propagation before cleanup after disconnect (ms) */
    const val EVENT_PROPAGATION_DELAY_MS = 100L

    /** Timeout waiting for reader to disconnect after sending response (ms) */
    const val READER_DISCONNECT_TIMEOUT_MS = 5000L

    /** Delay before auto-restart to allow cleanup to complete (ms) */
    const val AUTO_RESTART_CLEANUP_DELAY_MS = 100L
}

@Inject
@ContributesBinding(SessionScope::class, binding = binding<MdocEngagementManager>())
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocEngagementManagerImpl", exact = true)
class MdocEngagementManagerImpl(
    factory: MdocEngagementFactory,
    private val kms: KeyManagerService,
    val logService: SessionLogService,
    private val coseKeyCborCodec: CoseKeyCborCodec,
    private val deviceEngagementCborCodec: DeviceEngagementCborCodec,
    private val readerEngagementCborCodec: ReaderEngagementCborCodec,
    scopeScoped: CoroutineScopeScoped? = null,
) : MdocEngagementManager,
    MdocEngagementFactory.Holder {
    private val scope: CoroutineScope = scopeScoped?.createChild() ?: CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    private val holder = factory.holder

    private val log = logService.logManager.withTag(logService.id)

    // Shared parameters for automatic sharing across engagements
    override val sharedParameters: SharedParameters = SharedParametersImpl()

    // Map of engagements by type - the source of truth
    // Each engagement type can have at most ONE instance
    // Map only contains entries for active engagement types (no null values)
    private val _engagementsByType = MutableStateFlow<Map<EngagementType, EngagementInstance>>(emptyMap())
    override val engagementsByType: StateFlow<Map<EngagementType, EngagementInstance>> = _engagementsByType.asStateFlow()

    // Direct typed access properties - kept in sync via updateEngagement() helper
    // Note: These are maintained synchronously rather than derived because tests and external
    // code expect immediate value availability after createEngagement() returns
    private val _nfcEngagement = MutableStateFlow<EngagementInstance?>(null)
    override val nfcEngagement: StateFlow<EngagementInstance?> = _nfcEngagement.asStateFlow()

    private val _qrEngagement = MutableStateFlow<EngagementInstance?>(null)
    override val qrEngagement: StateFlow<EngagementInstance?> = _qrEngagement.asStateFlow()

    private val _toAppEngagement = MutableStateFlow<EngagementInstance?>(null)
    override val toAppEngagement: StateFlow<EngagementInstance?> = _toAppEngagement.asStateFlow()

    /**
     * Centralized helper to update engagement state atomically.
     * Updates both the map and typed properties in a single operation.
     */
    private fun updateEngagement(
        type: EngagementType,
        engagement: EngagementInstance?,
    ) {
        // Update map
        _engagementsByType.value =
            if (engagement != null) {
                _engagementsByType.value + (type to engagement)
            } else {
                _engagementsByType.value - type
            }
        // Update typed property
        when (type) {
            EngagementType.NFC -> _nfcEngagement.value = engagement
            EngagementType.QR -> _qrEngagement.value = engagement
            EngagementType.TO_APP -> _toAppEngagement.value = engagement
        }
    }

    /**
     * Clears all engagements atomically in a single operation.
     */
    private fun clearAllEngagements() {
        _engagementsByType.value = emptyMap()
        _nfcEngagement.value = null
        _qrEngagement.value = null
        _toAppEngagement.value = null
    }

    /**
     * Closes any existing engagement of the given type.
     * Returns an error if closing fails, allowing the caller to abort the creation flow.
     */
    private suspend fun closeExistingEngagementIfPresent(engagementType: EngagementType): IdkResult<Unit, IdkError> {
        val existing = _engagementsByType.value[engagementType]
        if (existing != null) {
            logService.info("${engagementType.name} engagement already exists (${existing.id}). Closing existing engagement to create new one.")
            val closeResult = closeEngagementByInstance(existing)
            if (closeResult.isErr) {
                logService.error("Failed to close existing ${engagementType.name} engagement: ${closeResult.error}")
                return IdkError
                    .UNKNOWN_ERROR(
                        message = "Failed to close existing ${engagementType.name} engagement before creating new one: ${closeResult.error.message}",
                        exception = closeResult.error.exception,
                    ).asErrorResult()
            }
            logService.info("Successfully closed existing ${engagementType.name} engagement. Creating new one.")
        }
        return Unit.asOkResult()
    }

    /**
     * Registers a new engagement by setting up event forwarders and adding to the map.
     * Should be called AFTER the engagement has been successfully created.
     */
    private suspend fun registerNewEngagement(
        engagement: EngagementInstance,
        engagementType: EngagementType,
    ) {
        logService.info("Registering engagement ${engagement.id} with methods: ${engagement.getEngagementMethods()}")

        // Set up event forwarders BEFORE adding to map to avoid race condition
        // This ensures we don't miss any events that are emitted when engagement starts
        setupEngagementForwarders(engagement)

        // Add engagement using centralized helper
        updateEngagement(engagementType, engagement)
    }

    private val _activeEngagement = MutableStateFlow<EngagementInstance?>(null)
    override val activeEngagement: StateFlow<EngagementInstance?> = _activeEngagement.asStateFlow()

    // A manager-wide stream that survives across instance switches
    private val mutableEngagementEvents = MutableSharedFlow<MdocEngagementEvent>(replay = 1, extraBufferCapacity = 64)

    // Renamed from transferInstanceEvents to transferEvents
    private val mutableTransferEvents = MutableSharedFlow<com.sphereon.mdoc.transfer.MdocRetrievalEvent>(replay = 1, extraBufferCapacity = 64)

    // Transfer completion callback
    private val mutableOnTransferCompletion = MutableSharedFlow<com.sphereon.mdoc.transfer.MdocRetrievalEvent>()

    // Event hub for UI integration - all events are managed in the current class though
    override val eventHub: MdocEventHub =
        MdocEventHubImpl(
            scope = scope,
            engagementEvents = mutableEngagementEvents.asSharedFlow(),
            transferEvents = mutableTransferEvents.asSharedFlow(),
            onTransferCompletion = mutableOnTransferCompletion.asSharedFlow(),
            activeEngagement = _activeEngagement.asStateFlow(),
            engagementsByType = _engagementsByType.asStateFlow(),
            logService = logService,
        )

    // Track forwarder jobs per engagement ID
    private val engagementEventForwarders = mutableMapOf<Uuid, Job>()
    private val transferEventForwarders = mutableMapOf<Uuid, Job>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val mutableTransferInstancesState: StateFlow<Map<Uuid, TransferInstance>> =
        _engagementsByType
            .mapLatest { engagementsByTypeMap ->
                engagementsByTypeMap.values
                    .filter { it.isTransferInitialized() && it.getCurrentState().order >= MdocEngagementState.START.order }
                    .associate { engagement ->
                        engagement.id to engagement.transferInstance
                    }
            }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override val transferInstances: StateFlow<Map<Uuid, TransferInstance>> = mutableTransferInstancesState

    // Auto-restart configuration
    private var autoRestartConfig: (EngagementConfiguration.() -> Unit)? = null

    /*
     * Initialize the engagement manager background monitors.
     */
    init {
        startForwarderCleanupMonitor()
        startTransferCompletionMonitor()
    }

    /**
     * Monitors engagementsByType for cleanup of removed engagements.
     * Cancels forwarder jobs when engagements are removed from the map.
     *
     * Note: New engagements are set up synchronously in createEngagement()
     * to avoid race conditions.
     */
    private fun startForwarderCleanupMonitor() {
        scope.launch {
            _engagementsByType.collect { engagementsByTypeMap ->
                val engagementsById = engagementsByTypeMap.values.associateBy { it.id }

                // Cancel forwarders for removed engagements
                val removedEngagementIds = engagementEventForwarders.keys.toSet() - engagementsById.keys
                removedEngagementIds.forEach { id ->
                    engagementEventForwarders[id]?.let { job ->
                        try {
                            job.cancelAndJoin()
                        } catch (expected: Throwable) {
                            log.error("engagement event forwarder Job $id cancellation not successful", exception = expected)
                        }
                    }
                    log.info("Removing engagement event forwarder: $id...")
                    engagementEventForwarders.remove(id)
                    log.info("Removed engagement event forwarder: $id")
                }

                val removedTransferIds = transferEventForwarders.keys.toSet() - engagementsById.keys
                removedTransferIds.forEach { id ->
                    transferEventForwarders[id]?.let { job ->
                        try {
                            job.cancelAndJoin()
                        } catch (expected: Throwable) {
                            log.error("transfer event forwarder Job $id cancellation not successful", exception = expected)
                        }
                    }
                    log.info("Removing transfer event forwarder: $id...")
                    transferEventForwarders.remove(id)
                    log.info("Removed transfer event forwarder: $id")
                }
            }
        }
    }

    /**
     * Monitors transfer events for final states (completion detection).
     * Handles auto-close and auto-restart logic.
     *
     * State ranges:
     * - Below 100: engagement states
     * - 100-199: transfer process states
     * - >= 200: termination states (isFinal = true)
     */
    private fun startTransferCompletionMonitor() {
        scope.launch {
            eventHub.transferEvents.collect { event ->
                if (event.isFinal) {
                    handleTransferCompletion(event)
                }
            }
        }
    }

    /**
     * Handles a completed transfer event by auto-closing the engagement
     * and optionally triggering auto-restart.
     */
    private suspend fun handleTransferCompletion(event: MdocRetrievalEvent) {
        logService.info("Transfer completed. Outcome event state: ${event.state}")
        // Emit completion event
        mutableOnTransferCompletion.emit(event)

        // Determine auto-close strategy based on event type
        val shouldAutoCloseImmediately =
            event.state == MdocRetrievalState.ERROR ||
                event.state == MdocRetrievalState.TERMINATED ||
                event is MdocRetrievalEvent.SessionTerminationReceived

        val isDataSentAwaitingReaderDisconnect = event is MdocRetrievalEvent.SessionDataSend

        when {
            shouldAutoCloseImmediately -> autoCloseImmediately(event)
            isDataSentAwaitingReaderDisconnect -> autoCloseWithTimeout(event)
        }

        // Auto-restart if enabled (only after successful completion)
        if (autoRestartConfig != null) {
            handleAutoRestart(event)
        }
    }

    /**
     * Auto-closes an engagement immediately due to error or termination.
     */
    private suspend fun autoCloseImmediately(event: MdocRetrievalEvent) {
        logService.info("Auto-closing engagement ${event.engagementId} immediately due to final state: ${event.state}")
        try {
            val closeResult = closeEngagementById(event.engagementId)
            if (closeResult.isErr) {
                logService.error("Failed to auto-close engagement ${event.engagementId}: ${closeResult.error.message}")
            }
        } catch (expected: Exception) {
            logService.error("Exception auto-closing engagement ${event.engagementId}", exception = expected)
        }
    }

    /**
     * Auto-closes an engagement after waiting for the reader to disconnect.
     * Per ISO 18013-5, the reader should disconnect after receiving the DeviceResponse.
     * Uses a timeout in case the reader doesn't disconnect.
     */
    private fun autoCloseWithTimeout(event: MdocRetrievalEvent) {
        logService.info("Data sent for engagement ${event.engagementId}, waiting for reader to disconnect (timeout: ${EngagementManagerConstants.READER_DISCONNECT_TIMEOUT_MS}ms)")
        scope.launch {
            kotlinx.coroutines.delay(EngagementManagerConstants.READER_DISCONNECT_TIMEOUT_MS)

            // Check if engagement still exists (reader might have disconnected already)
            val engagement = _engagementsByType.value.values.find { it.id == event.engagementId }
            if (engagement != null) {
                logService.info("Reader did not disconnect within timeout, auto-closing engagement ${event.engagementId}")
                try {
                    val closeResult = closeEngagementById(event.engagementId)
                    if (closeResult.isErr) {
                        logService.error("Failed to auto-close engagement ${event.engagementId} after timeout: ${closeResult.error.message}")
                    }
                } catch (expected: Exception) {
                    logService.error("Exception auto-closing engagement ${event.engagementId} after timeout", exception = expected)
                }
            } else {
                logService.debug("Engagement ${event.engagementId} already closed (reader disconnected)")
            }
        }
    }

    /**
     * Set up event forwarders for a new engagement.
     * This must be called before the engagement starts emitting events.
     * Waits for engagement event subscription to be active before returning.
     */
    private suspend fun setupEngagementForwarders(engagement: EngagementInstance) {
        setupEngagementEventForwarder(engagement)
        setupTransferEventForwarder(engagement)
    }

    /**
     * Sets up the engagement event forwarder for a single engagement.
     * Forwards events to the manager-wide stream and handles active state transitions.
     * Waits for subscription to be active before returning.
     */
    private suspend fun setupEngagementEventForwarder(engagement: EngagementInstance) {
        val id = engagement.id
        if (id in engagementEventForwarders) {
            return
        }

        val ready = CompletableDeferred<Unit>()

        engagementEventForwarders[id] =
            scope.launch {
                try {
                    // Signal readiness immediately in the launched coroutine
                    ready.complete(Unit)

                    engagement.events.collect { event ->
                        log.debug("${engagement.id} - Forwarding engagement event from instance: ${event.state}")
                        mutableEngagementEvents.emit(event)
                        handleEngagementEvent(engagement, event)
                    }
                } catch (expected: Throwable) {
                    logService.error("Engagement event forwarder failed for $id", exception = expected)
                    if (!ready.isCompleted) {
                        ready.completeExceptionally(expected)
                    }
                }
            }

        // Suspend until the forwarder has signalled readiness
        ready.await()
        log.debug("Engagement ${engagement.id} event forwarder subscribed and ready")
    }

    /**
     * Handles an engagement event by managing active state transitions.
     */
    private fun handleEngagementEvent(
        engagement: EngagementInstance,
        event: MdocEngagementEvent,
    ) {
        when {
            isActiveStateEvent(event) -> {
                log.info("Engagement ${engagement.id} is becoming/staying active at event state ${event.state}")
                setActiveEngagement(engagement)
            }

            event is MdocEngagementEvent.Disconnected -> {
                handleDisconnectEvent(engagement)
            }

            event.isFinal -> {
                handleFinalEvent(engagement, event)
            }
        }
    }

    /**
     * Checks if an event indicates the engagement should become active.
     */
    private fun isActiveStateEvent(event: MdocEngagementEvent): Boolean =
        event.state == MdocEngagementState.START ||
            event.state == MdocEngagementState.CONNECTING ||
            event.state == MdocEngagementState.CONNECTED

    /**
     * Handles a reader disconnect event per ISO 18013-5.
     * In forward engagement, the reader disconnects after receiving the DeviceResponse.
     * This is the normal success completion path.
     */
    private fun handleDisconnectEvent(engagement: EngagementInstance) {
        log.info("Reader disconnected engagement ${engagement.id} (ISO 18013-5 success flow), triggering cleanup")

        // Clear active engagement first
        if (_activeEngagement.value == engagement) {
            log.info("Clearing active engagement ${engagement.id} after reader disconnect")
            clearActiveEngagement()
        }

        // Close the engagement with a small delay to allow event propagation
        scope.launch {
            // Small delay to allow SessionUiProjector to receive the Disconnected event
            // before we close the engagement and potentially cancel coroutines
            kotlinx.coroutines.delay(EngagementManagerConstants.EVENT_PROPAGATION_DELAY_MS)
            try {
                val closeResult = closeEngagementById(engagement.id)
                if (closeResult.isErr) {
                    log.error("Failed to close engagement ${engagement.id} after reader disconnect: ${closeResult.error.message}")
                }
            } catch (expected: Exception) {
                log.error("Exception closing engagement ${engagement.id} after reader disconnect", exception = expected)
            }
        }
    }

    /**
     * Handles other final states (Error, Canceled, etc.) by clearing active state.
     */
    private fun handleFinalEvent(
        engagement: EngagementInstance,
        event: MdocEngagementEvent,
    ) {
        if (_activeEngagement.value == engagement) {
            log.info("Active engagement ${engagement.id} completed with event state: ${event.state}")
            clearActiveEngagement()
        }
    }

    /**
     * Sets up the transfer event forwarder for a single engagement.
     * Waits for the transfer to be initialized before forwarding events.
     */
    private fun setupTransferEventForwarder(engagement: EngagementInstance) {
        val id = engagement.id
        if (id in transferEventForwarders) {
            return
        }

        transferEventForwarders[id] =
            scope.launch {
                // Wait for transfer to be initialized
                engagement.events.collect { event ->
                    if (event.state.order >= MdocEngagementState.START.order && engagement.isTransferInitialized()) {
                        val transferInstance = engagement.transferInstance
                        transferInstance.events.collect { transferEvent ->
                            log.debug("${transferInstance.id} - Forwarding transfer event from instance: ${transferEvent.state}")
                            mutableTransferEvents.emit(transferEvent)
                        }
                    }
                }
            }
    }

    /**
     * Set the active engagement and suspend others.
     * Phase 4: Implements engagement suspension logic.
     */
    private fun setActiveEngagement(engagement: EngagementInstance) {
        log.info("Setting active engagement: ${engagement.id}, isActive before resume: ${engagement.isActive.value}")
        _activeEngagement.value = engagement
        // Ensure the active engagement is resumed/active
        (engagement as? SuspendableEngagement)?.resume()
        log.info("Active engagement set: ${engagement.id}, isActive after resume: ${engagement.isActive.value}")
        suspendOtherEngagements(engagement)
    }

    /**
     * Suspends all engagements except the active one.
     * Phase 4: Engagement Suspension Logic
     */
    private fun suspendOtherEngagements(activeEngagement: EngagementInstance) {
        listOfNotNull(nfcEngagement.value, qrEngagement.value, toAppEngagement.value)
            .filter { it != activeEngagement }
            .forEach { engagement ->
                (engagement as? SuspendableEngagement)?.suspend()
            }
    }

    /**
     * Resumes all suspended engagements.
     * Phase 4: Engagement Suspension Logic
     */
    private fun resumeOtherEngagements() {
        listOfNotNull(nfcEngagement.value, qrEngagement.value, toAppEngagement.value)
            .forEach { engagement ->
                (engagement as? SuspendableEngagement)?.resume()
            }
    }

    /**
     * Clear the active engagement and resume suspended engagements.
     * Phase 4: Implements engagement resumption logic.
     */
    private fun clearActiveEngagement() {
        _activeEngagement.value = null
        resumeOtherEngagements()
    }

    /**
     * Determine engagement type from configuration.
     */
    private fun getEngagementType(config: EngagementConfiguration): EngagementType? {
        val methods = config.engagementMethods
        return when {
            methods.any { it is NfcEngagementMethod } -> EngagementType.NFC
            methods.any { it is QREngagementMethod } -> EngagementType.QR
            methods.any { it is ReaderEngagementMethod || it is Oid4vpEngagementMethod } -> EngagementType.TO_APP
            else -> null
        }
    }

    override suspend fun toApp(
        mdocUri: String,
        autoStart: Boolean,
    ): IdkResult<EngagementInstance, IdkError> {
        val engagementResult =
            when {
                mdocUri.startsWith("mdoc-openid4vp://") -> {
                    createEngagement {
                        engagement {
                            oid4vp {
                                fromUri(mdocUri)
                            }
                        }
                        retrieval {
                            oid4vp {}
                        }
                    }
                }

                mdocUri.startsWith("mdoc:") && !mdocUri.startsWith("mdoc://") -> {
                    val readerEngagement =
                        readerEngagementCborCodec
                            .decodeUri(mdocUri)
                            .getOrElse { return it.asErrorResult() }
                            .value
                    val readerBleOptions = readerEngagement.getBleRetrievalOptions()
                    val readerNfcOptions = readerEngagement.getNfcRetrievalOptions()

                    createEngagement {
                        engagement {
                            reader {
                                withReaderEngagement(readerEngagement)
                            }
                        }

                        readerBleOptions?.let { bleOptions ->
                            retrieval {
                                ble {
                                    if (bleOptions.peripheralServerMode == true) {
                                        centralClientMode = true
                                        peripheralServerMode = false
                                        centralClientUuid = bleOptions.peripheralServerModeUuid
                                    } else if (bleOptions.centralClientMode == true) {
                                        peripheralServerMode = true
                                        centralClientMode = false
                                        peripheralServerUuid = bleOptions.centralClientModeUuid
                                    }
                                }
                            }
                        }

                        readerNfcOptions?.let { nfcOptions ->
                            retrieval {
                                nfc {
                                    maxCommandDataFieldLength = nfcOptions.maxCommandDataFieldLength
                                    maxResponseDataFieldLength = nfcOptions.maxResponseDataFieldLength
                                }
                            }
                        }
                    }
                }

                mdocUri.startsWith("mdoc://") -> {
                    val readerEngagement =
                        readerEngagementCborCodec
                            .decodeUri(mdocUri)
                            .getOrElse { return it.asErrorResult() }
                            .value
                    val readerRestApiOptions = readerEngagement.getWebsiteRetrievalOptions()

                    createEngagement {
                        engagement {
                            reader {
                                withReaderEngagement(readerEngagement)
                            }
                        }

                        readerRestApiOptions?.let { restApiOpts ->
                            retrieval {
                                website {
                                    uri = restApiOpts.uri
                                }
                            }
                        }
                    }
                }

                else -> {
                    val scheme =
                        when {
                            mdocUri.contains("://") -> mdocUri.substringBefore("://")
                            mdocUri.contains(":") -> mdocUri.substringBefore(":")
                            else -> "unknown"
                        }

                    IdkError
                        .ILLEGAL_ARGUMENT_ERROR(
                            message =
                                "Unsupported URI scheme for toApp. Expected:\n" +
                                    "  - 'mdoc:' (ISO 18013-5 reverse engagement)\n" +
                                    "  - 'mdoc://' (ISO 18013-7 website retrieval)\n" +
                                    "  - 'mdoc-openid4vp://' (ISO 18013-7 OID4VP with OAuth)\n" +
                                    "Got: $scheme\n\n",
                            arg = mdocUri,
                        ).asErrorResult()
                }
            }

        engagementResult.onSuccess {
            if (autoStart && engagementResult.isOk) {
                val engagement = engagementResult.value
                val startResult = engagement.tryOps().start()
                if (!startResult.isOk) {
                    closeEngagementByInstance(engagement)
                    return IdkError.fromDTO(startResult.error).asErrorResult()
                }
            }
        }

        return engagementResult
    }

    override suspend fun createEngagement(configBuilder: EngagementConfiguration.() -> Unit): IdkResult<EngagementInstance, IdkError> {
        return try {
            val config = EngagementConfiguration().apply(configBuilder)

            // Determine engagement type from config
            val engagementType =
                getEngagementType(config) ?: return IdkError
                    .ILLEGAL_ARGUMENT_ERROR(
                        message =
                            "No engagement method specified in configuration. " +
                                "Add at least one engagement method: engagement { qr { } }, engagement { nfc { } }, or engagement { toApp { } }",
                    ).asErrorResult()

            // Close any existing engagement of this type
            closeExistingEngagementIfPresent(engagementType).getOrElse { return it.asErrorResult() }

            // Resolve shared ephemeral key from alias for automatic key sharing across engagements
            val sharedKeyAlias = sharedParameters.ephemeralKeyAlias.value
            val sharedKey = resolveEphemeralKey(sharedKeyAlias)
            logService.debug("Using shared ephemeral key alias: $sharedKeyAlias")

            // Apply shared BLE UUIDs to configuration (separate for central/peripheral modes)
            val sharedCentralClientUuid = sharedParameters.bleCentralClientUuid.value
            val sharedPeripheralServerUuid = sharedParameters.blePeripheralServerUuid.value
            val retrievalMethodsWithSharedUuid =
                config.getRetrievalMethodsWithSharedUuids(
                    sharedCentralClientUuid = sharedCentralClientUuid,
                    sharedPeripheralServerUuid = sharedPeripheralServerUuid,
                )
            logService.debug("Using shared BLE UUIDs - Central: $sharedCentralClientUuid, Peripheral: $sharedPeripheralServerUuid")

            // Log explicit UUID configuration for filtering
            logService.info("*** ENGAGEMENT CREATION (${engagementType.name}): BLE UUID Configuration ***")
            retrievalMethodsWithSharedUuid.forEach { method ->
                val bleOptions = method.retrievalOptions as? BleOptions
                if (bleOptions != null) {
                    if (bleOptions.centralClientMode) {
                        logService.info("*** ENGAGEMENT CREATION (${engagementType.name}): Will use Central Client Mode - Scan for UUID: ${bleOptions.centralClientModeUuid} ***")
                    }
                    if (bleOptions.peripheralServerMode) {
                        logService.info("*** ENGAGEMENT CREATION (${engagementType.name}): Will use Peripheral Server Mode - Advertise UUID: ${bleOptions.peripheralServerModeUuid} ***")
                    }
                }
            }

            // Build engagement data with shared parameters
            val builder =
                EngagementData
                    .holderBuilder(
                        coseKeyCborCodec = coseKeyCborCodec,
                        deviceEngagementCborCodec = deviceEngagementCborCodec,
                        readerEngagementCborCodec = readerEngagementCborCodec,
                    ).apply {
                        withEphemeralKey(sharedKey)
                        withRetrievalMethods(retrievalMethodsWithSharedUuid)
                        withEngagementMethods(*config.engagementMethods.toTypedArray())
                    }

            // Create engagement through factory
            val result = holder.createFromBuilder(builder)

            if (result.isOk) {
                val engagement = result.value
                logService.info("Created engagement ${engagement.id} using shared BLE UUIDs - Central: $sharedCentralClientUuid, Peripheral: $sharedPeripheralServerUuid")
                registerNewEngagement(engagement, engagementType)
            }
            result
        } catch (e: IllegalStateException) {
            logService.error("Illegal state during engagement creation", exception = e)
            IdkError
                .ILLEGAL_ARGUMENT_ERROR(
                    message = e.message ?: "Invalid state for creating engagement",
                    throwable = e,
                ).asErrorResult()
        } catch (e: IllegalArgumentException) {
            logService.error("Invalid argument during engagement creation", exception = e)
            IdkError
                .ILLEGAL_ARGUMENT_ERROR(
                    message = e.message ?: "Invalid configuration for engagement",
                    throwable = e,
                ).asErrorResult()
        } catch (expected: Throwable) {
            logService.error("Unexpected error creating engagement", exception = expected)
            IdkError
                .UNKNOWN_ERROR(
                    message = "Failed to create engagement: ${expected.message}",
                    exception = expected,
                ).asErrorResult()
        }
    }

    override suspend fun closeNfcEngagement(): IdkResult<Unit, IdkError> {
        val engagement = _engagementsByType.value[EngagementType.NFC]
        return if (engagement != null) {
            closeEngagementByInstance(engagement)
        } else {
            IdkError.NOT_FOUND_ERROR(message = "No NFC engagement found").asErrorResult()
        }
    }

    override suspend fun closeQrEngagement(): IdkResult<Unit, IdkError> {
        val engagement = _engagementsByType.value[EngagementType.QR]
        return if (engagement != null) {
            closeEngagementByInstance(engagement)
        } else {
            IdkError.NOT_FOUND_ERROR(message = "No QR engagement found").asErrorResult()
        }
    }

    override suspend fun closeToAppEngagement(): IdkResult<Unit, IdkError> {
        val engagement = _engagementsByType.value[EngagementType.TO_APP]
        return if (engagement != null) {
            closeEngagementByInstance(engagement)
        } else {
            IdkError.NOT_FOUND_ERROR(message = "No TO_APP engagement found").asErrorResult()
        }
    }

    override suspend fun closeEngagementByInstance(engagement: EngagementInstance): IdkResult<Unit, IdkError> =
        try {
            logService.info("Closing engagement ${engagement.id}")

            // Check if this is the active engagement and clear it if so
            if (_activeEngagement.value == engagement) {
                logService.info("Closing active engagement ${engagement.id}, clearing active state and resuming others")
                clearActiveEngagement()
            }

            engagement.close()

            // Cancel forwarder jobs for this engagement
            // The init block collector will also cancel when engagement is removed from _engagementsByType
            engagementEventForwarders[engagement.id]?.let { job ->
                try {
                    job.cancelAndJoin()
                } catch (expected: Throwable) {
                    logService.error("Failed to cancel engagement forwarder for ${engagement.id}", exception = expected)
                }
            }
            engagementEventForwarders.remove(engagement.id)
            transferEventForwarders[engagement.id]?.let { job ->
                try {
                    job.cancelAndJoin()
                } catch (expected: Throwable) {
                    logService.error("Failed to cancel transfer forwarder for ${engagement.id}", exception = expected)
                }
            }
            transferEventForwarders.remove(engagement.id)

            // Clean up EventHub state for this engagement to prevent memory leaks
            eventHub.clearStateForEngagement(engagement.id)

            // Remove engagement using centralized helper
            val typeToRemove =
                _engagementsByType.value.entries
                    .firstOrNull { it.value == engagement }
                    ?.key
            if (typeToRemove != null) {
                updateEngagement(typeToRemove, null)
            }

            // Regenerate shared parameters (BLE UUIDs and ephemeral key) for next engagement
            // This ensures each new engagement gets fresh UUIDs, preventing readers from
            // connecting to stale GATT servers from previous sessions
            logService.info("Regenerating shared parameters after closing engagement ${engagement.id}")
            sharedParameters.regenerate()

            Unit.asOkResult()
        } catch (expected: Throwable) {
            logService.error("Error closing engagement ${engagement.id}", exception = expected)
            IdkError
                .UNKNOWN_ERROR(
                    message = "Failed to close engagement: ${expected.message}",
                    exception = expected,
                ).asErrorResult()
        }

    override suspend fun closeEngagementById(id: Uuid): IdkResult<Unit, IdkError> {
        val engagement = _engagementsByType.value.values.find { it.id == id }
        return if (engagement != null) {
            closeEngagementByInstance(engagement)
        } else {
            log.debug("Engagement $id not found. Assuming already closed")
            return Unit.asOkResult()
        }
    }

    override suspend fun closeAll(): IdkResult<Unit, IdkError> =
        try {
            logService.info("Closing all engagements (${_engagementsByType.value.size} active).")

            // Reset replay caches
            mutableEngagementEvents.resetReplayCache()
            mutableTransferEvents.resetReplayCache()
            mutableOnTransferCompletion.resetReplayCache()

            // Emit canceled events and close all engagements
            _engagementsByType.value.values.forEach { engagement ->
                mutableEngagementEvents.tryEmit(MdocEngagementEvent.Canceled(role = MdocRole.MDOC, engagementId = engagement.id))
                engagement.close()
            }

            // Cancel all forwarder jobs
            engagementEventForwarders.values.forEach { job ->
                try {
                    job.cancelAndJoin()
                } catch (expected: Throwable) {
                    logService.error("Error cancelling engagement forwarder job", exception = expected)
                }
            }
            engagementEventForwarders.clear()
            transferEventForwarders.values.forEach { job ->
                try {
                    job.cancelAndJoin()
                } catch (expected: Throwable) {
                    logService.error("Error cancelling transfer forwarder job", exception = expected)
                }
            }
            transferEventForwarders.clear()

            // Clear all EventHub state to prevent stale events/states in flows
            eventHub.clearAllState()

            // Regenerate shared parameters for next verification session
            logService.info("Regenerating shared parameters after closeAll()")
            sharedParameters.regenerate()

            // Clear all engagements using centralized helper
            clearAllEngagements()
            _activeEngagement.value = null

            Unit.asOkResult()
        } catch (expected: Throwable) {
            logService.error("Error closing all engagements", exception = expected)
            IdkError
                .UNKNOWN_ERROR(
                    message = "Failed to close all engagements: ${expected.message}",
                    exception = expected,
                ).asErrorResult()
        }

    override suspend fun createFromBuilder(builder: EngagementData.HolderBuilder): IdkResult<EngagementInstance, IdkError> {
        return try {
            // Create the engagement instance first to determine its type
            val result = holder.createFromBuilder(builder)
            if (result.isErr) {
                return result
            }

            val engagement = result.value

            // Determine engagement type from the created instance
            val engagementType =
                when {
                    engagement.getEngagementMethods().any { it is NfcEngagementMethod } -> EngagementType.NFC
                    engagement.getEngagementMethods().any { it is QREngagementMethod } -> EngagementType.QR
                    engagement.getEngagementMethods().any { it is ReaderEngagementMethod || it is Oid4vpEngagementMethod } -> EngagementType.TO_APP
                    else -> null
                }

            if (engagementType == null) {
                logService.error("Created engagement ${engagement.id} has no recognized engagement method")
                return IdkError
                    .ILLEGAL_ARGUMENT_ERROR(
                        message = "Engagement has no recognized engagement method",
                    ).asErrorResult()
            }

            // Close any existing engagement of this type (with cleanup if it fails)
            val closeResult = closeExistingEngagementIfPresent(engagementType)
            if (closeResult.isErr) {
                engagement.close() // Clean up the newly created engagement since we can't replace
                return closeResult.error.asErrorResult()
            }

            // Register the new engagement
            registerNewEngagement(engagement, engagementType)

            result
        } catch (expected: Throwable) {
            logService.error("Error in createFromBuilder", exception = expected)
            IdkError
                .UNKNOWN_ERROR(
                    message = "Failed to create engagement from builder: ${expected.message}",
                    exception = expected,
                ).asErrorResult()
        }
    }

    override suspend fun createFromEphemeralKey(
        ephemeralKey: ResolvedKeyInfoType<*>,
        configBuilder: EngagementConfiguration.() -> Unit,
    ): IdkResult<EngagementInstance, IdkError> {
        return try {
            val config = EngagementConfiguration().apply(configBuilder)

            // Determine engagement type from config
            val engagementType =
                getEngagementType(config) ?: return IdkError
                    .ILLEGAL_ARGUMENT_ERROR(
                        message =
                            "No engagement method specified in configuration. " +
                                "Add at least one engagement method: engagement { qr { } }, engagement { nfc { } }, or engagement { toApp { } }",
                    ).asErrorResult()

            // Close any existing engagement of this type
            closeExistingEngagementIfPresent(engagementType).getOrElse { return it.asErrorResult() }

            // Create engagement through factory
            val result = holder.createFromEphemeralKey(ephemeralKey, configBuilder)

            if (result.isOk) {
                registerNewEngagement(result.value, engagementType)
            }
            result
        } catch (expected: Throwable) {
            logService.error("Error in createFromEphemeralKey", exception = expected)
            IdkError
                .UNKNOWN_ERROR(
                    message = "Failed to create engagement from ephemeral key: ${expected.message}",
                    exception = expected,
                ).asErrorResult()
        }
    }

    override suspend fun enableAutoRestart(backgroundNfcConfig: (EngagementConfiguration.() -> Unit)?): IdkResult<Unit, IdkError> {
        return try {
            autoRestartConfig = backgroundNfcConfig

            if (backgroundNfcConfig != null) {
                logService.info("Enabling auto-restart with background NFC")
                val result = createEngagement(backgroundNfcConfig)
                if (result.isErr) {
                    logService.error("Failed to create initial background NFC engagement: ${result.error}")
                    return result.error.asErrorResult()
                }
                logService.info("Auto-restart enabled successfully")
            } else {
                logService.info("Auto-restart disabled")
            }

            Unit.asOkResult()
        } catch (expected: Throwable) {
            logService.error("Error enabling auto-restart", exception = expected)
            IdkError
                .UNKNOWN_ERROR(
                    message = "Failed to enable auto-restart: ${expected.message}",
                    exception = expected,
                ).asErrorResult()
        }
    }

    private suspend fun resolveEphemeralKey(alias: String): ResolvedKeyInfoType<*> {
        val key = kms.getKmsBySignatureAlgorithm(SignatureAlgorithm.ECDSA_SHA256).generateKeyAsync(alias = alias)
        return key.toManagedKeyInfo<CoseKeyType>(
            visibility = KeyVisibility.PRIVATE,
            keyEncoding = KeyEncoding.COSE,
        )
    }

    private suspend fun handleAutoRestart(event: com.sphereon.mdoc.transfer.MdocRetrievalEvent) {
        logService.info("Transfer completed: ${event.state}")

        // Close ALL engagements
        val closeResult = closeAll()
        if (closeResult.isErr) {
            logService.error("Failed to close engagements: ${closeResult.error}")
        }

        // Recreate background NFC
        val config = autoRestartConfig
        if (config != null) {
            // Wait brief moment for cleanup
            kotlinx.coroutines.delay(EngagementManagerConstants.AUTO_RESTART_CLEANUP_DELAY_MS)

            logService.info("Auto-restarting background NFC")
            val result = createEngagement(config)
            if (result.isErr) {
                logService.error("Failed to restart background NFC: ${result.error}")
            }
        }
    }

    override fun close() {
        log.debug("Closing engagement manager")
        // Cancel the scope immediately to stop all background coroutines (event collectors, etc.)
        // This allows runTest to complete without waiting for uncompleted coroutines
        scope.coroutineContext[Job]?.cancel()

        // Then close all engagements synchronously (uses runBlocking internally)
        runBlocking {
            closeAll()
        }
        log.debug("Engagement manager closed")
    }

    @ContributesTo(SessionScope::class)
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Graph", exact = true)
    interface Graph : MdocEngagementManagerProvider
}
