/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.mdoc.engagement

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.core.api.IdkOkResult
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.IdkErrorResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.error.NotFoundException
import com.sphereon.core.api.toException
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.MdocSignService
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.mdoc.transfer.DataInstanceType
import com.sphereon.mdoc.transfer.MdocTransferFactory
import com.sphereon.mdoc.transfer.TransferInstance
import com.sphereon.mdoc.transfer.TransferManager
import com.sphereon.mdoc.transfer.TransferManagerImpl
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
// Removed computeConnectionMethods import - using engagement data methods instead
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import software.amazon.app.platform.scope.coroutine.CoroutineScopeScoped
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalObjCName::class)
@ObjCName("EngagementInstanceImpl", exact = true)
class EngagementInstanceImpl(
    val execution: SessionExecution,
    internal val kms: KeyManagerService,
    override val data: EngagementData,
    override val sessionCoroutineScope: CoroutineScopeScoped,
    private val mdocSignService: MdocSignService,
    private val transferFactory: MdocTransferFactory,
    _handover: ByteArray? = null,
) : EngagementInstance, SuspendableEngagement, MdocEngagementEvent.Dispatcher {

    @OptIn(ExperimentalUuidApi::class)
    override val id: Uuid = Uuid.random()
    override var handover: ByteArray? = _handover

    @OptIn(ExperimentalUuidApi::class)
    private val log by lazy { execution.log.logManager.withTag("EngagementInstance-${id.toString().takeLast(8)}:") }


    internal val coroutineScope = sessionCoroutineScope.createChild(CoroutineName("engagement-service"))

    // TODO: NFC

    private val eventListeners = mutableListOf<MdocEngagementEvent.Listener>()

    // Note: BLE listeners are now managed by the modular transport layer
    // No longer need to track them here

    // FIX ROLE
    @OptIn(ExperimentalUuidApi::class)
    private val _events: MutableSharedFlow<MdocEngagementEvent> = MutableSharedFlow(replay = 1, extraBufferCapacity = 64)

    override val events: SharedFlow<MdocEngagementEvent> = _events

    // Track active/suspended state (Phase 4: Suspension Logic)
    private val _isActive = MutableStateFlow(true)
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    // Track current state separately for getCurrentState() method
    private var currentState: MdocEngagementState = MdocEngagementState.INIT

    init {
        // Emit initial event immediately
        coroutineScope.launch {
            val initialEvent = MdocEngagementEvent.Initializing(role = data.getRole(), engagementId = id)
            currentState = initialEvent.state
            _events.emit(initialEvent)
        }
    }

    /**
     * Suspends this engagement, preventing it from responding to connection attempts.
     * Called internally by the manager when another engagement becomes active.
     */
    override fun suspend() {
        _isActive.value = false
        log.info("$logId: Engagement suspended")
    }

    /**
     * Resumes this engagement, allowing it to respond to connection attempts again.
     * Called internally by the manager when the active engagement completes.
     */
    override fun resume() {
        _isActive.value = true
        log.info("$logId: Engagement resumed")
    }

    private val logId: String
        get() = EngagementInstance.short(this)

    private lateinit var _transfer: TransferInstance


    private val transferType = DataInstanceType.PER_ENGAGEMENT

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun startInternal(): TransferManager {
        // TODO timeout params etc.

        log.info("$logId: Engagement started, initializing and starting transfer methods")
        val event = MdocEngagementEvent.Start(MdocRole.MDOC, engagementId = id)
        currentState = event.state
        dispatch(event)

        log.debug("$logId: Creating transfer manager")
        val transferManager = TransferManagerImpl(
            engagement = this,
            execution = execution,
            mdocSignService = mdocSignService,
            transferFactory = transferFactory
        )
        _transfer = transferManager.instance
        log.debug("$logId: Transfer manager created, starting transfer ${transferManager.instance.id} methods: ${transferManager.instance.connectionMethods.joinToString(", ")}")

        // Note: BLE event listeners are now registered by the modular transport
        // No need to register them here

        // Dispatch QR show event if this is a QR engagement
        if (data.isQrEngagementSupported()) {
            val qrUri = getEngagementUri()
            val deviceEngagement = data.getDeviceEngagement().data()
            dispatch(MdocEngagementEvent.QrShow(
                role = MdocRole.MDOC,
                qrCodeData = qrUri,
                engagement = deviceEngagement,
                engagementId = id
            ))
        }

        val tm = transferManager.start()
        log.info("$logId: Transfer manager started, transfer ${tm.instance.id} methods started: ${tm.instance.connectionMethods.joinToString(", ")}")

        // Observe transport engagement state and dispatch corresponding engagement events
        // This bridges transport-level state changes to engagement-level events
        coroutineScope.launch {
            log.info("$logId: Transport state observer started")
            tm.instance.transfer.engagementState.collect { transportState ->
                log.info("$logId: Transport state changed to: $transportState")

                // Dispatch engagement events based on transport state changes
                val event = when (transportState) {
                    MdocEngagementState.BLE_SCANNING,
                    MdocEngagementState.BLE_ADVERTISING -> {
                        // BLE is advertising/scanning but not yet connecting
                        // Don't emit Connecting event yet - that happens when actual connection starts
                        // For NFC → BLE handover, the Connecting event is emitted by AbstractMdocNfcService
                        log.debug("$logId: Transport $transportState - BLE preparing but not yet connecting")
                        null
                    }

                    MdocEngagementState.NFC_ENABLED -> {
                        // NFC is enabled but handover hasn't completed yet
                        null
                    }

                    MdocEngagementState.CONNECTING -> {
                        // Map transport CONNECTING state to engagement Connecting event
                        log.info("$logId: Transport CONNECTING - emitting engagement Connecting event")
                        MdocEngagementEvent.Connecting(
                            role = data.getRole(),
                            engagementId = id,
                            deviceRetrievalMethods = data.getRetrievalMethods().toTypedArray()
                        )
                    }

                    MdocEngagementState.CONNECTED -> {
                        // Map transport CONNECTED state to engagement Connected event
                        MdocEngagementEvent.Connected(
                            role = data.getRole(),
                            engagementId = id,
                            deviceRetrievalMethod = data.getRetrievalMethods().firstOrNull()
                                ?: throw IllegalStateException("No retrieval method available")
                        )
                    }

                    MdocEngagementState.DISCONNECTED -> {
                        // Map transport DISCONNECTED state to engagement Disconnected event
                        log.info("$logId: DISCONNECTED state - creating Disconnected event")
                        MdocEngagementEvent.Disconnected(
                            role = data.getRole(),
                            engagementId = id,
                            reason = "Transport disconnected"
                        )
                    }

                    MdocEngagementState.ERROR -> {
                        // Map transport ERROR state to engagement Error event
                        MdocEngagementEvent.Error(
                            role = data.getRole(),
                            engagementId = id,
                            reason = "Transport error",
                            error = null
                        )
                    }
                    // Don't emit events for intermediate or initial states
                    else -> null
                }

                if (event != null) {
                    log.info("$logId: Dispatching ${event::class.simpleName} for transport state $transportState")
                    // Use dispatchAndWait for Connected event to ensure it's processed before
                    // subsequent events (like DocumentsSelectionProcessStart) are dispatched.
                    // This ensures the UI shows "Connecting" before "Review request".
                    if (event is MdocEngagementEvent.Connected) {
                        dispatchAndWait(event)
                    } else {
                        dispatch(event)
                    }
                    log.info("$logId: Dispatched ${event::class.simpleName}")
                }
            }
        }

        return tm

    }

    override suspend fun start(): TransferManager {
        val res = tryOps().start()
        return if (res.isOk) res.value else throw res.error.toException().also { log.error("$logId: Error starting engagement: ${it.message}", exception = it) }
    }


    override fun getRetrievalMethods() = data.getRetrievalMethods()
    override fun getEngagementMethods() = data.getEngagementMethods()

    override fun getConnectionMethods(supportedOnly: Boolean): Set<ConnectionMethod> {
        // Use the MdocTransferFactory's registry to parse connection methods from engagement data
        val transportRegistry = (transferFactory as? com.sphereon.mdoc.transfer.MdocTransferFactoryImpl)?.transportRegistry
            ?: throw IllegalStateException("Transfer factory must be MdocTransferFactoryImpl to access transport registry")

        // Convert DeviceRetrievalMethods to ConnectionMethods using the transport registry
        return data.getRetrievalMethods()
            .mapNotNull { retrievalMethod ->
                transportRegistry.getConnectionMethodFactory(retrievalMethod)?.create(retrievalMethod)
            }
            .toSet()
            .let { methods ->
                if (supportedOnly) {
                    // Filter to only supported methods (those with available transport factories)
                    methods.filter { method ->
                        transportRegistry.getFactory(method.transportType) != null
                    }.toSet()
                } else {
                    methods
                }
            }
    }

    override fun getReaderEngagement(): ReaderEngagement? {
        return data.getReaderEngagement()
    }

    override fun getDeviceEngagement(): CborEncodedItem<DeviceEngagement> {
        return data.getDeviceEngagement()
    }


    override fun tryOps(): EngagementInstance.Try = object : EngagementInstance.Try {
        override suspend fun start(): IdkResult<TransferManager, IdkErrorType> = try {
            IdkOkResult(startInternal())
        } catch (t: Throwable) {
            IdkErrorResult(IdkError.UNKNOWN_ERROR(
                message = "Failed to start engagement: ${t.message ?: t::class.simpleName}",
                exception = t
            ))
        }

        override suspend fun getEngagementUri(): IdkResult<String, IdkErrorType> = try {
            IdkOkResult(data.generateEngagementUri())
        } catch (t: Throwable) {
            IdkErrorResult(IdkError.UNKNOWN_ERROR(
                message = "Failed to generate engagement URI: ${t.message ?: t::class.simpleName}",
                exception = t
            ))
        }

        override suspend fun getEphemeralKey(): IdkResult<CoseKeyType, IdkErrorType> = try {
            IdkOkResult(data.getEphemeralKey().key)
        } catch (t: Throwable) {
            IdkErrorResult(IdkError.UNKNOWN_ERROR(
                message = "Failed to get ephemeral key: ${t.message ?: t::class.simpleName}",
                exception = t
            ))
        }
    }

    override suspend fun getEphemeralKey(): CoseKeyType {
        val res = tryOps().getEphemeralKey()
        return if (res.isOk) res.value else throw res.error.toException()
    }

    override suspend fun getEngagementUri(): String {
        val res = tryOps().getEngagementUri()
        return if (res.isOk) res.value else throw res.error.toException()
    }

    override fun isTransferInitialized(): Boolean {
        return ::_transfer.isInitialized
    }

    override val transferInstance: TransferInstance
        get() {
            if (!::_transfer.isInitialized) {
                throw IllegalStateException(
                    "Transfer not initialized for engagement $id. " +
                            "Call engagement.start() before accessing the transferInstance property. " +
                            "Each engagement must be explicitly started to initialize the transfer."
                )
            }
            return _transfer
        }

    override fun getCurrentState(): MdocEngagementStateType {
        return currentState
    }

    override fun close() {
        log.debug("$logId: Closing engagement instance")
        try {
            // Note: BLE listeners are now managed by the modular transport
            // No need to manually remove them here

            if (::_transfer.isInitialized) {
                _transfer.close()
            }
        } catch (e: Throwable) {
            log.error("$logId: Error closing engagement instance: ${e.message}", exception = e)
        }
        eventListeners.clear()

        coroutineScope.launch {
//            _events.emit(MdocEngagementEvent.Canceled(MdocRole.MDOC, engagementId = id))
            val keyInfo = data.getEphemeralKey()
            try {
                log.debug("$logId: Deleting ephemeral key with alias ${keyInfo.alias}")
                kms.deleteKey(keyInfo)
                log.debug("$logId: Deleted ephemeral key with alias ${keyInfo.alias}")
            } catch (nfe: NotFoundException) {
                log.debug("$logId: Ephemeral key with alias ${keyInfo.alias} not found, assuming it was already deleted")
            } catch (e: Throwable) {
                log.error("$logId: Error deleting ephemeral key with alias ${keyInfo.alias}: ${e.message}", exception = e)
            }
        }

        coroutineScope.cancel()

        log.debug("$logId: Engagement instance closed")
    }

    override fun getEngagementEventListeners(): Set<MdocEngagementEvent.Listener> {
        return eventListeners.toSet()
    }

    override fun addEngagementEventListener(vararg listener: MdocEngagementEvent.Listener): MdocEngagementEvent.Handlers = apply {
        this.eventListeners.addAll(listener.asList())
    }

    override fun removeEngagementEventListener(listener: MdocEngagementEvent.Listener): MdocEngagementEvent.Handlers = apply {
        this.eventListeners.remove(listener)
    }

    override fun clearEngagementEventListeners(): MdocEngagementEvent.Handlers = apply {
        this.eventListeners.clear()
    }


    override fun dispatch(event: MdocEngagementEvent) {
        log.debug("$logId: Dispatching engagement event (ASYNC): ${event.state} [isActive=${_isActive.value}]")
        coroutineScope.launch {
            currentState = event.state
            log.debug("$logId: Sending engagement event (ASYNC) with current state ${event.state} for listeners: ${eventListeners.size}")
            _events.emit(event)
            eventListeners.onEngagementEvent(event)
        }
    }

    suspend fun dispatchAndWait(event: MdocEngagementEvent) {
        log.debug("$logId: Dispatching engagement event (sync): ${event.state} [isActive=${_isActive.value}]")
        currentState = event.state
        log.debug("$logId: Emitting engagement event with current state ${event.state} for listeners: ${eventListeners.size}")

        // Emit to SharedFlow synchronously
        _events.emit(event)
        log.debug("$logId: Engagement event emission to SharedFlow completed for ${event.state}")

        // Notify listeners
        eventListeners.onEngagementEvent(event)
        log.debug("$logId: Engagement event listeners notified for ${event.state}")

        // Verify emission completed by checking replay cache
        val cached = _events.replayCache.lastOrNull()
        log.debug("$logId: Engagement event emission fully completed. Replay cache now contains: ${cached?.state} [expected: ${event.state}, match: ${cached?.state == event.state}]")
    }


    // Note: BLE event listening is now handled by the modular transport layer
    // The old registerBleStatusCallback method has been removed
    // Events are dispatched from the transport-ble module directly

    override fun toString(): String {
        return "EngagementInstance(id=$id, currentState=$currentState)"
    }


}
