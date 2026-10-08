/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

package com.sphereon.mdoc.engagement

import com.sphereon.core.api.log.SessionLogService
import com.sphereon.mdoc.MdocEvent
import com.sphereon.mdoc.transfer.MdocRetrievalEvent
import com.sphereon.mdoc.transfer.MdocRetrievalEventWithEngagement
import com.sphereon.mdoc.transfer.MdocRetrievalStateType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.Uuid

/**
 * Implementation of MdocEventHub that manages event streams, state tracking, and event history.
 *
 * @param scope CoroutineScope for managing event collection and state flows
 * @param engagementEvents Source flow of engagement events from the manager
 * @param transferEvents Source flow of transfer events from the manager
 * @param transferCompletion Source flow of transfer completion events from the manager
 * @param logService Logger for debugging
 */
private const val DEFAULT_EVENT_BUFFER_SIZE = 10
private const val EVENT_EXTRA_BUFFER_CAPACITY = 64
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
private const val ACTIVE_TRANSFER_MIN_ORDER = 100
private const val ACTIVE_TRANSFER_MAX_ORDER = 199

@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocEventHubImpl", exact = true)
class MdocEventHubImpl(
    private val scope: CoroutineScope,
    override val engagementEvents: SharedFlow<MdocEngagementEvent>,
    override val transferEvents: SharedFlow<MdocRetrievalEvent>,
    override val onTransferCompletion: SharedFlow<MdocRetrievalEvent>,
    private val activeEngagement: StateFlow<EngagementInstance?>,
    private val engagementsByType: StateFlow<Map<EngagementType, EngagementInstance>>,
    private val logService: SessionLogService,
) : MdocEventHub {
    private val log = logService.logManager.withTag("MdocEventHub")

    // Event buffer configuration
    private var eventBufferSize = 10
    private val eventBuffer = mutableListOf<SequencedEvent>()
    private val bufferMutex = Mutex()
    private var sequenceCounter = 0L

    // Listener registration - using synchronized collections for thread safety in KMP
    private val engagementListeners = mutableSetOf<MdocEngagementEvent.Listener>()
    private val retrievalListeners = mutableSetOf<MdocRetrievalEvent.Listener>()

    // Combined event stream
    private val _allEvents = MutableSharedFlow<MdocEvent>(replay = 1, extraBufferCapacity = 64)
    override val allEvents: SharedFlow<MdocEvent> = _allEvents.asSharedFlow()

    // Latest event tracking
    private val mutableLatestEventByEngagement = MutableStateFlow<Map<Uuid, MdocEngagementEvent>>(emptyMap())
    private val mutableLatestTransferEventByEngagement = MutableStateFlow<Map<Uuid, MdocRetrievalEvent>>(emptyMap())

    // Current state tracking
    private val _currentStateByEngagement = MutableStateFlow<Map<Uuid, MdocEngagementState>>(emptyMap())
    private val _currentTransferStateByEngagement = MutableStateFlow<Map<Uuid, MdocRetrievalStateType>>(emptyMap())

    // UI projection layer
    private val sessionUiProjector =
        SessionUiProjector(
            engagementEvents = engagementEvents,
            retrievalEvents = transferEvents,
            activeEngagement = activeEngagement,
            engagementsByType = engagementsByType,
            logService = logService,
        )

    override val sessionEvents: SharedFlow<SessionEvent> =
        sessionUiProjector
            .sessionEvents()
            .shareIn(scope, SharingStarted.Companion.WhileSubscribed(5_000), replay = 1)

    override val sessionState: StateFlow<SessionUiState> = sessionUiProjector.sessionState(scope)

    init {
        // Start collecting engagementEvents and transferEvents flows using launchIn and suspend onEach
        engagementEvents
            .onEach { event ->
                log.debug("Aggregating engagement event to hub: ${event::class.simpleName} [engagementId=${event.engagementId}, state=${event.state}]")
                _allEvents.emit(event)
                addToBuffer(event)
                updateEngagementTracking(event)
                notifyEngagementListeners(event)
            }.launchIn(scope)

        transferEvents
            .onEach { event ->
                log.debug("Aggregating retrieval event to hub: ${event::class.simpleName} [engagementId=${event.engagementId}, state=${event.state}]")
                _allEvents.emit(event)
                addToBuffer(event)
                updateTransferTracking(event)
                notifyRetrievalListeners(event)
            }.launchIn(scope)
    }

    // ========================================
    // Filtered Event Streams
    // ========================================

    override fun eventsInStateRange(
        minOrder: Int,
        maxOrder: Int,
    ): Flow<MdocEvent> =
        allEvents.filter { event ->
            event.state.order in minOrder..maxOrder
        }

    override fun finalStateEvents(): Flow<MdocEvent> =
        allEvents.filter { event ->
            event.isFinal
        }

    override fun activeTransferEvents(): Flow<MdocRetrievalEvent> =
        transferEvents.filter { event ->
            event.state.order in 100..199
        }

    // ========================================
    // State Tracking
    // ========================================

    override fun latestEventByEngagement(): StateFlow<Map<Uuid, MdocEngagementEvent>> = mutableLatestEventByEngagement.asStateFlow()

    override fun latestTransferEventByEngagement(): StateFlow<Map<Uuid, MdocRetrievalEvent>> = mutableLatestTransferEventByEngagement.asStateFlow()

    override fun getCurrentStateByEngagement(): StateFlow<Map<Uuid, MdocEngagementState>> = _currentStateByEngagement.asStateFlow()

    override fun getCurrentTransferStateByEngagement(): StateFlow<Map<Uuid, MdocRetrievalStateType>> = _currentTransferStateByEngagement.asStateFlow()

    private fun updateEngagementTracking(event: MdocEngagementEvent) {
        // Update latest event map
        mutableLatestEventByEngagement.value += (event.engagementId to event)

        // Update current state map
        _currentStateByEngagement.value += (event.engagementId to event.state)

        // Clean up if engagement reached final state
        if (event.isFinal) {
            log.debug("Engagement ${event.engagementId} reached final event state: ${event.state}")
        }
    }

    private fun updateTransferTracking(event: MdocRetrievalEvent) {
        // Update latest transfer event map by engagement ID
        mutableLatestTransferEventByEngagement.value += (event.engagementId to event)

        // Update current transfer state map by engagement ID
        _currentTransferStateByEngagement.value += (event.engagementId to event.state)

        // Clean up if transfer reached final state
        if (event.isFinal) {
            log.debug("Transfer for engagement ${event.engagementId} reached final event state: ${event.state}")
        }
    }

    // ========================================
    // Event History & Debugging
    // ========================================

    override fun setEventBufferSize(size: Int) {
        require(size >= 0) { "Event buffer size must be non-negative" }
        eventBufferSize = size

        // Trim buffer if needed - use async to avoid blocking the dispatcher
        scope.async {
            bufferMutex.withLock {
                while (eventBuffer.size > eventBufferSize) {
                    eventBuffer.removeAt(0)
                }
            }
        }

        log.debug("Event buffer size set to $size")
    }

    override fun getEventBufferSize(): Int = eventBufferSize

    override fun clearAllState() {
        log.info("Clearing all event hub state")
        scope.launch {
            bufferMutex.withLock {
                eventBuffer.clear()
                sequenceCounter = 0L
            }
        }

        // Clear all tracking maps
        mutableLatestEventByEngagement.value = emptyMap()
        mutableLatestTransferEventByEngagement.value = emptyMap()
        _currentStateByEngagement.value = emptyMap()
        _currentTransferStateByEngagement.value = emptyMap()

        log.info("Event hub state cleared successfully")
    }

    override fun clearStateForEngagement(engagementId: Uuid) {
        log.info("Clearing event hub state for engagement $engagementId")

        // Remove engagement from all tracking maps
        mutableLatestEventByEngagement.value -= engagementId
        mutableLatestTransferEventByEngagement.value -= engagementId
        _currentStateByEngagement.value -= engagementId
        _currentTransferStateByEngagement.value -= engagementId

        // Remove events from event buffer that belong to this engagement - use launch to avoid blocking
        scope.launch {
            bufferMutex.withLock {
                eventBuffer.removeAll { sequencedEvent ->
                    when (val event = sequencedEvent.event) {
                        is MdocEngagementEvent -> event.engagementId == engagementId
                        is MdocRetrievalEvent -> event.engagementId == engagementId
                        else -> false
                    }
                }
            }
        }

        log.debug("Event hub state and buffer cleared for engagement $engagementId")
    }

    override fun getRecentEvents(count: Int): List<MdocEvent> {
        // Direct access without locking - accept small risk for non-blocking behavior
        // The buffer is append-only during normal operation, so reads are generally safe
        return eventBuffer
            .takeLast(count)
            .map { it.event }
    }

    override fun getRecentEngagementEvents(count: Int): List<MdocEngagementEvent> =
        eventBuffer
            .filter { it.event is MdocEngagementEvent }
            .takeLast(count)
            .map { it.event as MdocEngagementEvent }

    override fun getRecentTransferEvents(count: Int): List<MdocRetrievalEvent> =
        eventBuffer
            .filter { it.event is MdocRetrievalEvent }
            .takeLast(count)
            .map { it.event as MdocRetrievalEvent }

    override fun getEventTimeline(): List<SequencedEvent> = eventBuffer.toList()

    private suspend fun addToBuffer(event: MdocEvent) {
        if (eventBufferSize == 0) return

        bufferMutex.withLock {
            val sequenced =
                SequencedEvent(
                    event = event,
                    sequenceNumber = sequenceCounter++,
                )

            eventBuffer.add(sequenced)

            // Trim buffer to configured size
            while (eventBuffer.size > eventBufferSize) {
                eventBuffer.removeAt(0)
            }
        }
    }

    // ========================================
    // Handler Registration - MdocEngagementEvent.Handlers
    // ========================================

    override fun addEngagementEventListener(vararg listener: MdocEngagementEvent.Listener): MdocEngagementEvent.Handlers {
        engagementListeners.addAll(listener)
        log.debug("Added ${listener.size} engagement event listener(s)")
        return this
    }

    override fun removeEngagementEventListener(listener: MdocEngagementEvent.Listener): MdocEngagementEvent.Handlers {
        engagementListeners.remove(listener)
        log.debug("Removed engagement event listener")
        return this
    }

    override fun clearEngagementEventListeners(): MdocEngagementEvent.Handlers {
        engagementListeners.clear()
        log.debug("Cleared all engagement event listeners")
        return this
    }

    override fun getEngagementEventListeners(): Set<MdocEngagementEvent.Listener> = engagementListeners.toSet()

    // ========================================
    // Handler Registration - MdocRetrievalEvent.Handlers
    // ========================================

    override fun addRetrievalEventListener(vararg listener: MdocRetrievalEvent.Listener) {
        retrievalListeners.addAll(listener)
        log.debug("Added ${listener.size} retrieval event listener(s)")
    }

    override fun removeRetrievalEventListener(listener: MdocRetrievalEvent.Listener) {
        retrievalListeners.remove(listener)
        log.debug("Removed retrieval event listener")
    }

    override fun clearRetrievalEventListeners() {
        retrievalListeners.clear()
        log.debug("Cleared all retrieval event listeners")
    }

    override fun getRetrievalEventListeners(): Set<MdocRetrievalEvent.Listener> = retrievalListeners.toSet()

    private suspend fun notifyEngagementListeners(event: MdocEngagementEvent) {
        val listeners = engagementListeners.toList()
        logService.debug("Notifying listeners. engagement event: $event")
        listeners.forEach { listener ->
            try {
                when (event) {
                    is MdocEngagementEvent.Initializing -> listener.onInitializing(event)
                    is MdocEngagementEvent.Start -> listener.onStart(event)
                    is MdocEngagementEvent.QrShow -> listener.onQrShow(event)
                    is MdocEngagementEvent.QrHide -> listener.onQrHide(event)
                    is MdocEngagementEvent.NfcEngagement -> listener.onNfcEngagement(event)
                    is MdocEngagementEvent.RestApiEngagement -> listener.onRestApiEngagement(event)
                    is MdocEngagementEvent.Debug -> listener.onDebug(event)
                    is MdocEngagementEvent.Data -> listener.onData(event, event.direction)
                    is MdocEngagementEvent.Transfer -> listener.onTransfer(event)
                    is MdocEngagementEvent.Connecting -> listener.onConnecting(event)
                    is MdocEngagementEvent.Connected -> listener.onConnected(event)
                    is MdocEngagementEvent.Canceled -> listener.onCanceled(event)
                    is MdocEngagementEvent.Disconnected -> listener.onDisconnected(event)
                    is MdocEngagementEvent.Error -> listener.onError(event)
                    is MdocEngagementEvent -> log.warn("Unhandled engagement event: $event")
                }
            } catch (e: Exception) {
                log.error("Error notifying engagement listener", exception = e)
            }
        }
    }

    private suspend fun notifyRetrievalListeners(event: MdocRetrievalEvent) {
        val listeners = retrievalListeners.toList()
        logService.debug("Notifying listeners. retrieval event: $event")
        val actual = if (event is MdocRetrievalEventWithEngagement) event.delegate else event
        listeners.forEach { listener ->
            try {
                when (event) {
                    is MdocRetrievalEventWithEngagement -> {
                        // Unwrap and dispatch the delegate event
                        notifyRetrievalListeners(actual)
                        return // Prevent processing the wrapper itself
                    }

                    else -> {
                        when (actual) {
                            is MdocRetrievalEvent.Initializing -> listener.onInitializing(actual)
                            is MdocRetrievalEvent.TransmissionTypeSelected -> listener.onTransmissionTypeSelected(actual)
                            is MdocRetrievalEvent.SessionEstablishmentReceived -> listener.onSessionEstablishmentReceived(actual)
                            is MdocRetrievalEvent.DeviceRequestReady -> listener.onDeviceRequestReady(actual)
                            is MdocRetrievalEvent.DocumentsSelectionProcessStart -> listener.onDocumentsSelectionProcessStart(actual)
                            is MdocRetrievalEvent.DocumentsSelectionProcessAccepted -> listener.onDocumentsSelectionProcessAccepted(actual)
                            is MdocRetrievalEvent.DocumentsSelectionProcessDeclined -> listener.onDocumentsSelectionProcessDeclined(actual)
                            is MdocRetrievalEvent.SessionDataSend -> listener.onSessionDataSend(actual)
                            is MdocRetrievalEvent.SessionDataReceived -> listener.onSessionDataReceived(actual)
                            is MdocRetrievalEvent.SessionTerminationSend -> listener.onSessionTerminationSend(actual)
                            is MdocRetrievalEvent.SessionTerminationReceived -> listener.onSessionTerminationReceived(actual)
                            is MdocRetrievalEvent.Error -> listener.onError(actual)
                            is MdocRetrievalEvent.Terminated -> listener.onTerminated(actual)
                            else -> log.warn("Received unsupported event type: ${actual::class.simpleName}")
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Error notifying retrieval listener", exception = e)
            }
        }
    }
}
