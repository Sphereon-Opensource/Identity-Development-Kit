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

package com.sphereon.mdoc.engagement.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.sphereon.cbor.CborItem
import com.sphereon.core.api.log.LogService
import com.sphereon.data.link.nfc.NfcApduDispatcher
import com.sphereon.data.link.nfc.model.CommandApdu
import com.sphereon.di.session.SessionComponent
import com.sphereon.mdoc.engagement.EngagementInstance
import com.sphereon.mdoc.engagement.MdocEngagementEvent
import com.sphereon.mdoc.engagement.MdocEngagementManager
import com.sphereon.mdoc.engagement.MdocEngagementManagerProvider
import com.sphereon.mdoc.engagement.MdocEngagementState
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.util.getCurrentTimeMillis
import com.sphereon.mdoc.transport.ConnectionMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Abstract base class for implementing NFC engagement services for mdoc credential presentation.
 *
 * This is an abstract class that needs to be extended in an Android app service.
 * The easiest approach is to make the session component available in the companion of the App/MainActivity,
 * then create a subclass that provides the session component and apdu dispatcher.
 *
 * ## Migration from Previous Versions
 *
 * This class is designed to be a **drop-in replacement** for the previous `AbstractMdocNfcService`.
 * It maintains API compatibility while using the refactored architecture under the hood.
 *
 * ## Usage Example
 *
 * ```kotlin
 * class MyNfcService : AbstractMdocNfcService() {
 *
 *     override val sessionComponent: SessionComponent
 *         get() = (application as MyApp).sessionComponent
 *
 *     override val apduService: NfcApduDispatcher
 *         get() = sessionComponent.sessionNfcApduDispatcher
 * }
 * ```
 *
 * ## Android Manifest Configuration
 *
 * Don't forget to declare your service in `AndroidManifest.xml`:
 *
 * ```xml
 * <service
 *     android:name=".MyNfcService"
 *     android:exported="true"
 *     android:permission="android.permission.BIND_NFC_SERVICE">
 *     <intent-filter>
 *         <action android:name="android.nfc.cardemulation.action.HOST_APDU_SERVICE" />
 *     </intent-filter>
 *     <meta-data
 *         android:name="android.nfc.cardemulation.host_apdu_service"
 *         android:resource="@xml/nfc_engagement_apdu_service" />
 * </service>
 * ```
 *
 * ## Automatic Engagement Management
 *
 * This class automatically:
 * - Creates NFC engagements when needed
 * - Processes APDU commands
 * - Handles handover lifecycle
 * - Manages BLE transition after handover
 * - Cleans up resources appropriately
 *
 * ## Protected Properties Available
 *
 * Subclasses have access to:
 * - `engagementManager` - The MdocEngagementManager from the session component
 * - `log` - Logger instance for debugging
 * - `engagementInstance` - Flow of the current engagement instance
 *
 * @see MdocNfcEngagementHelper
 * @see EngagementInstance
 * @see NfcApduDispatcher
 */
abstract class AbstractMdocNfcService : HostApduService() {

    companion object {
        private const val TAG = "AbstractMdocNfcService"
        private const val APDU_TIMEOUT_MS = 5000L
        private const val ENGAGEMENT_STARTUP_TIMEOUT_MS = 10000L
    }

    // ===================================================================================
    // Abstract properties - must be provided by subclass
    // ===================================================================================

    /**
     * The session component providing access to DI services.
     *
     * Example implementation:
     * ```kotlin
     * override val sessionComponent: SessionComponent
     *     get() = (application as MyApp).sessionComponent
     * ```
     */
    protected abstract val sessionComponent: SessionComponent

    /**
     * The NFC APDU dispatcher for receiving commands.
     *
     * Typically obtained from the session component:
     * ```kotlin
     * override val apduService: NfcApduDispatcher
     *     get() = sessionComponent.sessionNfcApduDispatcher
     * ```
     */
    protected abstract val apduService: NfcApduDispatcher

    // ===================================================================================
    // Internal state
    // ===================================================================================

    // Coroutine scope for async operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Mutex for thread-safe engagement initialization
    private val startMutex = Mutex()

    // The underlying NFC engagement helper
    private val _engagementHelper = MutableStateFlow<MdocNfcEngagementHelper?>(null)
    private val engagementHelper: StateFlow<MdocNfcEngagementHelper?> = _engagementHelper.asStateFlow()

    // The active engagement instance
    private val _engagementInstance = MutableStateFlow<EngagementInstance?>(null)
    protected val engagementInstance: StateFlow<EngagementInstance?> = _engagementInstance.asStateFlow()

    // Saved NFC session state for short tap recovery (per ISO 18013-5, 30-second validity)
    @Volatile
    private var savedNfcSessionState: NfcSessionState? = null

    // Logging
    protected lateinit var log: LogService

    // Engagement manager - derived from session component
    protected val engagementManager: MdocEngagementManager by lazy {
        provideEngagementManager()
    }

    // ===================================================================================
    // Lifecycle
    // ===================================================================================

    override fun onCreate() {
        super.onCreate()
        try {
            log = sessionComponent.logManager.withTag(TAG)
            logDebug("NFC Service created")
        } catch (e: Exception) {
            android.util.Log.d(TAG, "NFC Service created (logger not yet available)")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
        serviceScope.cancel()
        logDebug("NFC Service destroyed")
    }

    override fun onDeactivated(reason: Int) {
        val reasonText = when (reason) {
            DEACTIVATION_LINK_LOSS -> "Link lost"
            DEACTIVATION_DESELECTED -> "Deselected"
            else -> "Unknown ($reason)"
        }
        logDebug("NFC deactivated: $reasonText")

        // Capture state before clearing helper - enables short tap recovery
        val helper = _engagementHelper.value
        if (helper != null) {
            val capturedState = helper.captureState()
            if (capturedState?.isRecoverable() == true) {
                savedNfcSessionState = capturedState
                logInfo("Saved NFC state for continuation (staticHandover=${capturedState.staticHandoverState})")
            } else {
                // State is not recoverable (handover complete or error) - clear saved state
                savedNfcSessionState = null
                if (helper.isHandoverComplete()) {
                    logDebug("Handover already complete, not saving state")
                }
            }
        }

        // Clear NFC helper - NFC part is done for now.
        // The engagement stays alive for BLE to continue.
        // State will be checked fresh on next NFC tap in ensureEngagementReady().
        _engagementHelper.value = null
    }

    // ===================================================================================
    // APDU Processing
    // ===================================================================================

    /**
     * Process incoming APDU commands from the NFC reader.
     *
     * This method is called by Android's NFC framework and must return synchronously.
     * It delegates to [MdocNfcEngagementHelper] for actual processing.
     */
    override fun processCommandApdu(commandApduBytes: ByteArray?, extras: Bundle?): ByteArray? {
        if (commandApduBytes == null) {
            logWarn("Received null APDU command")
            return errorResponse()
        }

        try {
            // Decode the APDU command
            val commandApdu = CommandApdu.decode(commandApduBytes)

            logDebug("Processing APDU: INS=0x${commandApdu.ins.toString(16)}")

            // Ensure engagement is ready (creates if needed)
            val helper = runBlocking {
                ensureEngagementReady()
            }

            if (helper == null || !helper.isOperational()) {
                logError("NFC helper not available or not operational")
                return errorResponse()
            }

            // Process the APDU command
            return processApduBlocking(helper, commandApdu)

        } catch (e: Exception) {
            logError("Error processing APDU: ${e.message}", e)
            return errorResponse()
        }
    }

    /**
     * Process an APDU command in a blocking manner.
     * This is necessary because [processCommandApdu] must be synchronous.
     */
    private fun processApduBlocking(helper: MdocNfcEngagementHelper, command: CommandApdu): ByteArray {
        val latch = CountDownLatch(1)
        val responseRef = AtomicReference<ByteArray>()

        // Launch coroutine to process APDU
        serviceScope.launch {
            try {
                val responseApdu = helper.processApdu(command)
                responseRef.set(responseApdu.encode())
            } catch (e: Exception) {
                logError("Error in APDU processing coroutine: ${e.message}", e)
                responseRef.set(errorResponse())
            } finally {
                latch.countDown()
            }
        }

        // Wait for completion with timeout
        val completed = latch.await(APDU_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!completed) {
            logError("APDU processing timeout after ${APDU_TIMEOUT_MS}ms")
            return errorResponse()
        }

        return responseRef.get() ?: errorResponse()
    }

    // ===================================================================================
    // Engagement Management
    // ===================================================================================

    /**
     * Ensure the engagement and helper are ready, creating them if needed.
     *
     * Decision matrix:
     * 1. **Active BLE connection** -> Reuse engagement for NFC retry
     * 2. **Recoverable saved state** -> Restore state and continue handover
     * 3. **Existing but not recoverable** -> Cleanup and start fresh
     * 4. **No existing** -> Start fresh
     */
    private suspend fun ensureEngagementReady(): MdocNfcEngagementHelper? {
        // Fast-path: helper already present and operational
        _engagementHelper.value?.let {
            if (it.isOperational()) {
                return it
            }
        }

        // Serialize startup to avoid concurrent calls
        return startMutex.withLock {
            // Re-check under lock
            _engagementHelper.value?.let {
                if (it.isOperational()) {
                    logDebug("Engagement helper became available while waiting for lock")
                    return@withLock it
                }
            }

            // Check existing engagement state
            val existingEngagement = _engagementInstance.value
            val savedState = savedNfcSessionState

            // Case 1: Check for active BLE connection
            if (existingEngagement != null) {
                val currentState = existingEngagement.getCurrentState()

                // Only reuse engagement when there's an ACTIVE BLE connection
                // For "waiting" states (CONNECTING, BLE_ADVERTISING, etc.), check saved state
                val hasActiveConnection = when (currentState) {
                    // Active connection states - BLE is connected and data exchange may be happening
                    MdocEngagementState.CONNECTED,
                    MdocEngagementState.DATA,
                    MdocEngagementState.TRANSFER -> true
                    // CONNECTING state - BLE handshake in progress, also reuse
                    MdocEngagementState.CONNECTING -> true
                    // All other states - either waiting or terminal
                    else -> false
                }

                if (hasActiveConnection) {
                    // BLE is actively connected or connecting - resend same DeviceEngagement
                    // This is useful when reader needs to retry after brief NFC read failure
                    logInfo("Reusing active engagement ${existingEngagement.id} (state=$currentState) for NFC retry")
                    createNfcHelper(existingEngagement)
                    // Re-emit engagement to trigger navigation (it may have been cleared after previous tap)
                    _engagementInstance.value = existingEngagement
                    return@withLock _engagementHelper.value
                }
            }

            // Case 2: Check for recoverable saved state (short tap recovery)
            if (savedState?.isRecoverable() == true && existingEngagement != null) {
                logInfo("Restoring saved NFC state (age=${getCurrentTimeMillis() - savedState.captureTimeMs}ms)")

                // Create new helper and restore state
                createNfcHelper(existingEngagement)
                val helper = _engagementHelper.value
                if (helper != null && helper.restoreState(savedState)) {
                    logInfo("Successfully restored NFC state - continuing handover")
                    savedNfcSessionState = null  // Clear after successful restore
                    // Re-emit engagement to trigger navigation (it was cleared after previous tap timeout)
                    _engagementInstance.value = existingEngagement
                    return@withLock helper
                } else {
                    logWarn("Failed to restore NFC state - starting fresh")
                    savedNfcSessionState = null
                }
            }

            // Case 3: Existing engagement but not recoverable - cleanup
            if (existingEngagement != null) {
                val currentState = existingEngagement.getCurrentState()
                logInfo("Previous engagement ${existingEngagement.id} (state=$currentState) not recoverable - cleaning up")
                savedNfcSessionState = null
                _engagementInstance.value = null
            }

            // Case 4: No active engagement - start a new one
            logInfo("Starting new NFC engagement")
            try {
                startEngagement()

                // Wait for helper to be initialized
                try {
                    withTimeout(ENGAGEMENT_STARTUP_TIMEOUT_MS) {
                        engagementHelper.filterNotNull().first()
                    }
                } catch (e: Exception) {
                    logError("Helper not initialized within ${ENGAGEMENT_STARTUP_TIMEOUT_MS}ms", e)
                    null
                }
            } catch (e: Exception) {
                logError("Failed to start engagement", e)
                null
            }
        }
    }

    /**
     * Start the NFC engagement and create the helper.
     *
     * CRITICAL FIX: This method now returns IMMEDIATELY after creating the engagement
     * and NFC helper. BLE preparation (GATT server setup, service registration, advertising)
     * happens ASYNCHRONOUSLY in the background. This prevents deadlocks where:
     * 1. NFC APDU thread blocks in runBlocking waiting for BLE
     * 2. BLE callbacks need the main thread to resume continuations
     * 3. System hangs because threads are waiting on each other
     *
     * The NFC helper can process APDUs immediately, and BLE will be ready by the time
     * the handover completes (which takes multiple APDU exchanges).
     */
    private suspend fun startEngagement() {
        try {
            // Close any existing engagement first (should already be done in ensureEngagementReady,
            // but be safe in case this is called directly)
            _engagementInstance.value?.let { existingEngagement ->
                logInfo("Closing existing engagement ${existingEngagement.id} before starting new one")
                try {
                    engagementManager.closeEngagementByInstance(existingEngagement)
                    // Give time for old GATT server connections to fully disconnect
                    kotlinx.coroutines.delay(200)
                } catch (e: Exception) {
                    logError("Failed to close existing engagement", e)
                }
                _engagementInstance.value = null
                _engagementHelper.value = null
            }

            // Create NFC engagement with BLE transport using the DSL API
            val bleOptions = getBleOptions()
            val result = engagementManager.createEngagement {
                engagement {
                    nfc()
                }
                retrieval {
                    ble {
                        peripheralServerMode = bleOptions.peripheralServerMode
                        peripheralServerUuid = bleOptions.peripheralServerModeUuid
                        centralClientMode = bleOptions.centralClientMode
                        centralClientUuid = bleOptions.centralClientModeUuid
                    }
                }
            }

            if (result.isOk) {
                val engagement = result.get()!!
                _engagementInstance.value = engagement

                logInfo("Created NFC engagement: ${engagement.id}")

                // Create NFC helper FIRST so we can respond to APDUs immediately.
                // NFC has strict timing requirements - we can't block.
                createNfcHelper(engagement)
                logInfo("NFC helper created - can process APDUs while BLE starts in background")

                // Start BLE in the background. NFC proceeds immediately without waiting.
                // By the time the reader receives the UUID via NFC, scans for us, and connects,
                // BLE will be ready. Stale connections from previous sessions are rejected
                // (before advertising starts) so the reader must scan fresh.
                logInfo("*** Starting BLE preparation in background ***")
                serviceScope.launch {
                    try {
                        logInfo("*** BLE preparation: Starting engagement to prepare transport ***")
                        engagement.start()
                        logInfo("*** BLE preparation complete: GATT services added, advertising started ***")
                    } catch (e: Exception) {
                        logError("*** BLE preparation failed: ${e.message} ***", e)
                    }
                }

            } else {
                logError("Failed to create NFC engagement: ${result.error}")
                throw Exception("Failed to create NFC engagement: ${result.error}")
            }
        } catch (e: Exception) {
            logError("Error starting engagement", e)
            throw e
        }
    }

    /**
     * Create the NFC engagement helper for the given engagement instance.
     */
    private fun createNfcHelper(engagement: EngagementInstance) {
        try {
            val helper = MdocNfcEngagementHelper(
                instance = engagement,
                onHandoverComplete = { instance, handover ->
                    handleHandoverComplete(instance, handover)
                },
                onError = { error ->
                    handleError(error)
                },
                negotiatedHandoverPicker = getNegotiatedConnectionMethod(),
                sessionComponent = sessionComponent,
                bleHandoverMapper = provideBleHandoverMapper(),
            )

            _engagementHelper.value = helper
            logInfo("NFC helper created for engagement ${engagement.id}")

        } catch (e: Exception) {
            logError("Failed to create NFC helper", e)
            throw e
        }
    }

    /**
     * Handle handover completion.
     */
    private suspend fun handleHandoverComplete(instance: EngagementInstance, handover: CborItem<*>) {
        try {
            logInfo("Handover complete for engagement ${instance.id}")

            // BLE is started in the background and should be ready by now (or very soon).
            // The reader needs time to process the NFC response and scan for our UUID,
            // so BLE will be ready when the reader connects.
            logInfo("NFC handover complete - reader will now scan for our BLE UUID")

            // Emit Connecting event to indicate BLE connection is about to start
            // This is crucial for UI updates after NFC handover completes
            val connectingEvent = MdocEngagementEvent.Connecting(
                role = instance.data.getRole(),
                engagementId = instance.id,
                deviceRetrievalMethods = instance.getRetrievalMethods().toTypedArray()
            )
            (instance as? MdocEngagementEvent.Dispatcher)?.dispatch(connectingEvent)
            logInfo("Dispatched Connecting event after NFC handover completion")

            // Call the optional callback for subclasses
            onHandoverComplete(instance, handover)

        } catch (e: Exception) {
            logError("Error in handover completion handler", e)
        }
    }

    /**
     * Handle errors during NFC processing.
     */
    private fun handleError(error: Throwable) {
        logError("NFC engagement error: ${error.message}", error)

        // Call the optional callback for subclasses
        try {
            onNfcError(error)
        } catch (e: Exception) {
            logError("Error in error handler callback", e)
        }
    }

    /**
     * Cleanup resources.
     */
    private fun cleanup() {
        logDebug("Cleaning up NFC service resources")

        _engagementHelper.value = null

        // Only close the engagement if it's terminal (BLE might still be using it)
        val engagement = _engagementInstance.value
        if (engagement != null && isEngagementTerminal(engagement)) {
            serviceScope.launch {
                try {
                    engagementManager.closeEngagementByInstance(engagement)
                } catch (e: Exception) {
                    logError("Error closing engagement", e)
                }
            }
            _engagementInstance.value = null
        }
    }

    /**
     * Check if an engagement is in a terminal state (done, cancelled, error, disconnected).
     */
    private fun isEngagementTerminal(engagement: EngagementInstance): Boolean {
        return when (engagement.getCurrentState()) {
            MdocEngagementState.CANCELED,
            MdocEngagementState.ERROR,
            MdocEngagementState.DISCONNECTED -> true

            else -> false
        }
    }

    // ===================================================================================
    // Helper methods
    // ===================================================================================

    /**
     * Generate an error response APDU.
     */
    private fun errorResponse(): ByteArray {
        return byteArrayOf(0x6F, 0x00.toByte()) // SW1=6F, SW2=00 (generic error)
    }

    // ===================================================================================
    // Overridable methods for customization
    // ===================================================================================

    /**
     * Provide the engagement manager from the session component.
     *
     * The default implementation casts the session component to [MdocEngagementManagerProvider].
     * Override this if you need custom logic:
     *
     * ```kotlin
     * override fun provideEngagementManager(): MdocEngagementManager {
     *     return sessionComponent.mdocEngagementManager
     * }
     * ```
     */
    protected open fun provideEngagementManager(): MdocEngagementManager {
        // Cast session component to the engagement manager component interface
        return try {
            (sessionComponent as MdocEngagementManagerProvider).mdocEngagementManager
        } catch (e: ClassCastException) {
            throw IllegalStateException(
                "SessionComponent does not implement MdocEngagementManagerProvider. " +
                        "Please ensure your session component provides the mdocEngagementManager property " +
                        "or override provideEngagementManager() to provide it explicitly.",
                e
            )
        }
    }

    /**
     * Provide an optional BLE handover mapper for NFC engagement.
     *
     * Override to supply a BLE mapper from your DI component if available.
     */
    protected open fun provideBleHandoverMapper(): BleHandoverMapper? {
        return (sessionComponent as? BleHandoverMapperProvider)?.bleHandoverMapper
    }

    /**
     * Get BLE options for the NFC engagement (optional).
     *
     * Override to customize BLE behavior.
     *
     * Default: BLE peripheral server mode enabled.
     */
    protected open fun getBleOptions(): BleOptions {
        return BleOptions(
            peripheralServerMode = true,
            centralClientMode = false
        )
    }

    /**
     * Get the negotiated connection method function (optional).
     *
     * Override to implement negotiated handover (where the reader sends its supported
     * connection methods and you choose the best one).
     *
     * Default: null (uses static handover)
     */
    protected open fun getNegotiatedConnectionMethod(): ((List<ConnectionMethod>) -> ConnectionMethod)? {
        return null
    }

    /**
     * Called when NFC handover completes successfully (optional).
     *
     * Override to be notified when handover completes.
     *
     * @param instance The engagement instance for which handover completed
     * @param handover The complete handover CBOR structure
     */
    protected open suspend fun onHandoverComplete(instance: EngagementInstance, handover: CborItem<*>) {
        // Default: no action
    }

    /**
     * Called when an error occurs during NFC processing (optional).
     *
     * Override to handle errors.
     *
     * @param error The error that occurred
     */
    protected open fun onNfcError(error: Throwable) {
        // Default: just log
        logError("NFC error (no custom handler): ${error.message}", error)
    }

    // ===================================================================================
    // Protected accessors for subclasses
    // ===================================================================================

    /**
     * Get the current engagement instance.
     */
    protected fun getCurrentEngagement(): EngagementInstance? = _engagementInstance.value

    /**
     * Get the current NFC helper.
     */
    protected fun getNfcHelper(): MdocNfcEngagementHelper? = _engagementHelper.value

    /**
     * Check if an in-progress engagement exists (BLE transfer might be running).
     * Returns true if the engagement is in a non-terminal state.
     */
    protected fun hasInProgressEngagement(): Boolean {
        val engagement = _engagementInstance.value ?: return false
        return !isEngagementTerminal(engagement)
    }

    /**
     * Reset state to allow a new NFC engagement session.
     *
     * **IMPORTANT:** Call this after BLE transfer completes to enable NFC to work
     * for subsequent sessions. Without this, the service may still have stale state
     * from the previous session.
     *
     * This method:
     * - Clears saved NFC session state
     * - Resets the NFC helper (if any)
     * - Clears the engagement instance reference
     *
     * Does NOT close the engagement manager or stop BLE - that should be done
     * separately through the engagement manager.
     *
     * ## Usage
     * ```kotlin
     * // In your transfer completion handler:
     * override fun onTransferComplete(result: TransferResult) {
     *     // ... process result ...
     *     resetForNewSession()
     * }
     * ```
     */
    protected fun resetForNewSession() {
        logInfo("Resetting NFC service for new session")

        // Clear saved NFC state
        savedNfcSessionState = null

        // Reset NFC helper if present
        _engagementHelper.value?.reset(clearErrorState = true)
        _engagementHelper.value = null

        // Clear engagement instance reference (but don't close it - caller handles that)
        _engagementInstance.value = null

        logInfo("NFC service ready for new session")
    }

    // ===================================================================================
    // Logging helpers
    // ===================================================================================

    private fun logDebug(message: String) {
        log.debug(message) ?: android.util.Log.d(TAG, message)
    }

    private fun logInfo(message: String) {
        log.info(message) ?: android.util.Log.i(TAG, message)
    }

    private fun logWarn(message: String) {
        log.warn(message) ?: android.util.Log.w(TAG, message)
    }

    private fun logError(message: String, exception: Throwable? = null) {
        log.error(message, exception = exception) ?: android.util.Log.e(TAG, message, exception)
    }
}
