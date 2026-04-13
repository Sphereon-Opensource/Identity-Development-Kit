package com.sphereon.mdoc.engagement.mocks

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.engagement.EngagementInstance
import com.sphereon.mdoc.engagement.SuspendableEngagement
import com.sphereon.mdoc.engagement.MdocEngagementEvent
import com.sphereon.mdoc.engagement.MdocEngagementStateType
import com.sphereon.mdoc.engagement.MdocEngagementState
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.MdocEngagementMethod
import com.sphereon.mdoc.transfer.TransferInstance
import com.sphereon.mdoc.transfer.TransferManager
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import com.sphereon.mdoc.engagement.EngagementData
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import software.amazon.app.platform.scope.coroutine.CoroutineScopeScoped
import kotlin.uuid.Uuid

/**
 * Test implementation of EngagementInstance that allows full control over behavior.
 * This is a test double that can be configured to simulate various scenarios.
 */
class TestEngagementInstance(
    override val id: Uuid = Uuid.random(),
    override val data: EngagementData = mockk(relaxed = true),
    private val engagementMethods: Set<MdocEngagementMethod> = emptySet()
) : EngagementInstance, SuspendableEngagement {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _events = MutableSharedFlow<MdocEngagementEvent>(replay = 1)
    override val events: SharedFlow<MdocEngagementEvent> = _events

    private val _isActive = MutableStateFlow(true)
    override val isActive: StateFlow<Boolean> = _isActive

    private var currentState: MdocEngagementState = MdocEngagementState.INIT
    private var transferInitialized = false
    private val listeners = mutableListOf<MdocEngagementEvent.Listener>()

    init {
        // Emit initial event asynchronously, matching real EngagementInstanceImpl behavior
        // This allows the manager's event forwarder to start collecting before the event is emitted
        val initialEvent = MdocEngagementEvent.Initializing(role = MdocRole.MDOC, engagementId = id)
        currentState = initialEvent.state
        scope.launch {
            _events.emit(initialEvent)
        }
    }

    // Test control methods
    suspend fun emitEvent(event: MdocEngagementEvent) {
        currentState = event.state
        _events.emit(event)
    }

    fun setActive(active: Boolean) {
        _isActive.value = active
    }

    fun mockTransferInitialized(initialized: Boolean) {
        transferInitialized = initialized
    }

    // SuspendableEngagement implementation
    override fun suspend() {
        _isActive.value = false
    }

    override fun resume() {
        _isActive.value = true
    }

    // EngagementInstance implementation
    override val sessionCoroutineScope: CoroutineScopeScoped
        get() = mockk(relaxed = true)

    override var handover: ByteArray? = null

    override suspend fun start(): TransferManager {
        transferInitialized = true
        return mockk(relaxed = true)
    }

    override suspend fun getEngagementUri(): String {
        // Use mdoc: for Device Engagement (holder QR) per ISO 18013-5
        // In real tests that use ReaderEngagement, this should return mdoc:// per ISO 18013-7
        return "mdoc:test-engagement-${id}"
    }

    override suspend fun getEphemeralKey(): CoseKeyType {
        return mockk(relaxed = true)
    }

    override fun getDeviceEngagement(): CborEncodedItem<DeviceEngagement> {
        return mockk(relaxed = true)
    }

    override fun getReaderEngagement(): ReaderEngagement? = null

    override fun tryOps(): EngagementInstance.Try = object : EngagementInstance.Try {
        override suspend fun start(): IdkResult<TransferManager, IdkErrorType> {
            transferInitialized = true
            return mockk<TransferManager>(relaxed = true).asOkResult()
        }

        override suspend fun getEngagementUri(): IdkResult<String, IdkErrorType> {
            // Use mdoc: for Device Engagement (holder QR) per ISO 18013-5
            return "mdoc:test-engagement-${id}".asOkResult()
        }

        override suspend fun getEphemeralKey(): IdkResult<CoseKeyType, IdkErrorType> {
            return mockk<CoseKeyType>(relaxed = true).asOkResult()
        }
    }

    override val transferInstance: TransferInstance
        get() = if (transferInitialized) {
            mockk(relaxed = true)
        } else {
            throw IllegalStateException("Transfer not initialized")
        }

    override fun getCurrentState(): MdocEngagementStateType = currentState

    override fun getRetrievalMethods(): Set<DeviceRetrievalMethod> = emptySet()

    override fun getConnectionMethods(supportedOnly: Boolean): Set<ConnectionMethod> = emptySet()

    override fun getEngagementMethods(): Set<MdocEngagementMethod> = engagementMethods

    override fun isTransferInitialized(): Boolean = transferInitialized

    override fun close() {
        // Test implementation - just mark as inactive
        _isActive.value = false
    }

    // Event handler implementation
    override fun addEngagementEventListener(vararg listener: MdocEngagementEvent.Listener): MdocEngagementEvent.Handlers {
        listeners.addAll(listener)
        return this
    }

    override fun removeEngagementEventListener(listener: MdocEngagementEvent.Listener): MdocEngagementEvent.Handlers {
        listeners.remove(listener)
        return this
    }

    override fun clearEngagementEventListeners(): MdocEngagementEvent.Handlers {
        listeners.clear()
        return this
    }

    override fun getEngagementEventListeners(): Set<MdocEngagementEvent.Listener> {
        return listeners.toSet()
    }
}

// Helper function to avoid mockk dependency in this file
private fun <T> mockk(relaxed: Boolean = false): T {
    return io.mockk.mockk(relaxed = relaxed)
}
