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

package com.sphereon.mdoc.engagement

import com.sphereon.core.api.log.SessionLogService
import com.sphereon.mdoc.transfer.MdocRetrievalEvent
import com.sphereon.mdoc.transfer.MdocRetrievalEventWithEngagement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Projects raw engagement and retrieval events into simplified UI-friendly SessionEvents and SessionUiState.
 *
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("implements", exact = true)
 * This class implements the deterministic UI projection logic with the following key rules:
 *
 * 1. NFC state determination:
 *    - DISABLED: No NFC engagement exists
 *    - BACKGROUND: NFC engagement exists but is not the active engagement
 *    - FOREGROUND: NFC engagement exists and is the active engagement
 *
 * 2. QR mode determination:
 *    - NONE: No QR engagement
 *    - DISPLAY: QR engagement active (show QR code)
 *    - SCAN: UI-managed; not set by the projector
 *
 * 3. Phase transitions:
 *    - ENGAGEMENT phase when no transfer started
 *    - TRANSFER phase when connection/transfer begins
 *    - TERMINAL phase on completion/error
 *
 * 4. Suspension tracking:
 *    - isSuspended mirrors activeEngagement?.isActive == false
 *
 * 5. De-noising:
 *    - Once transferStarted, ignore engagement-final events for phase changes
 *    - They only end the session if transfer never started
 *
 * @property engagementEvents Flow of engagement events from the manager
 * @property retrievalEvents Flow of retrieval/transfer events from the manager
 * @property activeEngagement StateFlow of the currently active engagement instance
 * @property engagementsByType StateFlow of engagements organized by type
 * @property logService Logger for debugging projection logic
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionUiProjector", exact = true)
class SessionUiProjector(
    private val engagementEvents: Flow<MdocEngagementEvent>,
    private val retrievalEvents: Flow<MdocRetrievalEvent>,
    private val activeEngagement: StateFlow<EngagementInstance?>,
    private val engagementsByType: StateFlow<Map<EngagementType, EngagementInstance>>,
    private val logService: SessionLogService,
) {
    private val log = logService.logManager.withTag("SessionUiProjector")

    /**
     * Creates a flow of simplified session events by mapping raw engagement and retrieval events.
     *
     * Mapping rules:
     * - Only processes events where isActive == true
     * - QrShow -> SessionEvent.QrShow
     * - NfcEngagement -> SessionEvent.NfcPromptShown
     * - Connected (from engagement) when NFC -> SessionEvent.NfcHandoverSuccess
     * - Connecting (retrieval) -> SessionEvent.TransferConnecting
     * - Connected (retrieval) -> SessionEvent.TransferConnected
     * - User Interaction Required -> SessionEvent.UserInteractionRequired
     * - User Accepted -> SessionEvent.UserAccepted
     * - User Declined -> SessionEvent.UserDeclined
     * - Progress events -> SessionEvent.TransferProgress
     * - Terminal events (Error, Canceled, Declined, Terminated) -> SessionEvent.Terminal
     *
     * Events from non-active engagements/transfers are filtered out to prevent UI updates
     * from cleanup/cancellation events of inactive engagements.
     */
    fun sessionEvents(): Flow<SessionEvent> =
        merge(
            engagementEvents
                .filter { event ->
                    // Only process events from the active engagement
                    // Early-stage events (state <= 50) are always considered active since they occur during initialization
                    // Terminal events (Disconnected, Error, Canceled) should always pass through even if engagement is deactivated
                    val activeEng = activeEngagement.value
                    val isEarlyStage = event.state.order <= 50
                    val isTerminalEvent =
                        event is MdocEngagementEvent.Disconnected ||
                            event is MdocEngagementEvent.Error ||
                            event is MdocEngagementEvent.Canceled
                    val isActive = isEarlyStage || isTerminalEvent || (activeEng?.id == event.engagementId && activeEng.isActive.value)

                    // Log terminal events for debugging
                    if (isTerminalEvent) {
                        log.info("Terminal event: ${event::class.simpleName} [engagementId=${event.engagementId}, state=${event.state}, willPass=$isActive]")
                    } else if (!isActive) {
                        log.debug("Filtering out: ${event::class.simpleName} [engagementId=${event.engagementId}, activeEng=${activeEng?.id}]")
                    }
                    isActive
                }.mapNotNull { engagementEvent ->
                    val sessionEvent =
                        when (engagementEvent) {
                            is MdocEngagementEvent.QrShow -> {
                                SessionEvent.QrShow
                            }

                            is MdocEngagementEvent.NfcEngagement -> {
                                SessionEvent.NfcPromptShown
                            }

                            is MdocEngagementEvent.Connecting -> {
                                // Connecting state indicates we're starting the connection
                                // This is especially important for NFC → BLE handover
                                SessionEvent.TransferConnecting
                            }

                            is MdocEngagementEvent.Connected -> {
                    /*// NFC handover success for NFC connections, TransferConnected for others

                    if (engagementEvent.dataRetrievalMethod.name.contains("NFC", ignoreCase = true)) {
                        SessionEvent.NfcHandoverSuccess
                    } else {*/
                                SessionEvent.TransferConnected
//                    }
                            }

                            is MdocEngagementEvent.Error -> {
                                SessionEvent.Terminal(
                                    outcome = TerminalOutcome.ERROR,
                                    message = engagementEvent.reason,
                                )
                            }

                            is MdocEngagementEvent.Canceled -> {
                                SessionEvent.Terminal(
                                    outcome = TerminalOutcome.CANCELED,
                                    message = "Canceled",
                                )
                            }

                            is MdocEngagementEvent.Disconnected -> {
                                // Per ISO 18013-5, in forward engagement the reader disconnects after receiving
                                // the DeviceResponse. This is the normal success flow.
                                // Map Disconnected to success since the reader initiated the disconnect.
                                log.info("Disconnected event -> Terminal(SUCCESS) [reason=${engagementEvent.reason}]")
                                SessionEvent.Terminal(
                                    outcome = TerminalOutcome.SUCCESS,
                                    message = "Transfer completed successfully",
                                )
                            }

                            else -> {
                                null
                            }
                        }
                    if (sessionEvent != null) {
                        log.debug("Engagement event mapped: ${engagementEvent::class.simpleName} -> ${sessionEvent::class.simpleName} [engagementId=${engagementEvent.engagementId}]")
                    } else {
                        log.debug("Engagement event ignored: ${engagementEvent::class.simpleName} [engagementId=${engagementEvent.engagementId}]")
                    }
                    sessionEvent
                },
            retrievalEvents
                .filter { event ->
                    // Filter logic for retrieval events:
                    // 1. If the event is from an active transfer (event.isActive=true), always allow it
                    //    - This includes events from transfers that have reached TransmissionTypeSelected
                    // 2. For final events (TERMINATED, ERROR), allow them if they were from an active transfer
                    //    - Even if the engagement is no longer in activeEngagement (already closed)
                    // 3. Otherwise, only allow if the engagement ID matches the currently active engagement

                    val activeEng = activeEngagement.value
                    val idMatches = activeEng?.id == event.engagementId
                    val eventIsActive = event.isActive
                    val isFinalEvent = event.isFinal

                    // Allow the event if:
                    // - It's from an active transfer (event.isActive), OR
                    // - It's a final event from what was an active transfer (allows cleanup events)
                    // - AND for non-final events, require ID match with active engagement
                    val willPass = eventIsActive && (isFinalEvent || idMatches)

                    log.debug(
                        "Retrieval event filter: ${event::class.simpleName} [engagementId=${event.engagementId}, activeEng=${activeEng?.id}, idMatches=$idMatches, eventIsActive=$eventIsActive, isFinal=$isFinalEvent, willPass=$willPass, state=${event.state}]",
                    )

                    willPass
                }.mapNotNull { retrievalEvent ->
                    // Unwrap MdocRetrievalEventWithEngagement to get the actual event
                    val actualEvent = (retrievalEvent as? MdocRetrievalEventWithEngagement)?.delegate ?: retrievalEvent

                    val sessionEvent =
                        when (actualEvent) {
                            is MdocRetrievalEvent.TransmissionTypeSelected -> {
                                // Transmission type selected - connection is starting
                                SessionEvent.TransferConnecting
                            }

                            is MdocRetrievalEvent.DocumentsSelectionProcessStart -> {
                                // User interaction required - must review and accept/decline
                                SessionEvent.UserInteractionRequired(actualEvent.data)
                            }

                            is MdocRetrievalEvent.DocumentsSelectionProcessAccepted -> {
                                // User accepted the request
                                SessionEvent.UserAccepted
                            }

                            is MdocRetrievalEvent.DocumentsSelectionProcessDeclined -> {
                                // User declined - this will also trigger terminal state
                                SessionEvent.UserDeclined
                            }

                            is MdocRetrievalEvent.SessionDataReceived -> {
                                // Near completion when sending data
                                SessionEvent.TransferProgress(0.3f)
                            }

                            is MdocRetrievalEvent.SessionDataSend -> {
                                // Data sent successfully, now waiting for reader to disconnect
                                // Per ISO 18013-5, the reader should initiate session termination
                                // Show near-complete progress while waiting
                                SessionEvent.TransferProgress(0.95f)
                            }

                            is MdocRetrievalEvent.SessionTerminationSend -> {
                                // NOTE: Per ISO 18013-5, the holder (mdoc) should NOT send session termination.
                                // Only the reader should send session termination.
                                // This event may still be used by the reader side, so keep the mapping.
                                // For holder: this should not be dispatched anymore.
                                SessionEvent.Terminal(
                                    outcome = TerminalOutcome.SUCCESS,
                                    message = "Transfer completed successfully",
                                )
                            }

                            is MdocRetrievalEvent.SessionTerminationReceived -> {
                                // Reader sent termination - session completed successfully
                                SessionEvent.Terminal(
                                    outcome = TerminalOutcome.SUCCESS,
                                    message = "Transfer completed successfully",
                                )
                            }

                            is MdocRetrievalEvent.Error -> {
                                SessionEvent.Terminal(
                                    outcome = TerminalOutcome.ERROR,
                                    message = actualEvent.error.message ?: "Transfer error",
                                )
                            }

                            is MdocRetrievalEvent.Terminated -> {
                                SessionEvent.Terminal(
                                    outcome = TerminalOutcome.SUCCESS,
                                    message = "Session terminated",
                                )
                            }

                            else -> {
                                null
                            }
                        }
                    if (sessionEvent != null) {
                        log.debug("** Retrieval event mapped: ${retrievalEvent::class.simpleName} -> ${sessionEvent::class.simpleName} [engagementId=${retrievalEvent.engagementId}]")
                    } else {
                        log.debug("** Retrieval event ignored: ${retrievalEvent::class.simpleName} [engagementId=${retrievalEvent.engagementId}]")
                    }
                    sessionEvent
                },
        )

    /**
     * Creates a StateFlow of SessionUiState by applying projection logic to session events.
     *
     * The state is built incrementally using runningFold with an internal accumulator that tracks:
     * - transferStarted: whether transfer phase has begun
     * - ui: the current SessionUiState
     *
     * QR visibility is determined by engagement state:
     * - Show QR when QrShow event is received AND engagement state is not CONNECTING
     * - Hide QR when engagement state becomes CONNECTING (connection starting)
     *
     * @param scope CoroutineScope for the StateFlow
     * @return StateFlow of SessionUiState
     */
    fun sessionState(scope: CoroutineScope): StateFlow<SessionUiState> {
        @OptIn(ExperimentalObjCName::class)
        @ObjCName("Accumulator", exact = true)
        data class Accumulator(
            val ui: SessionUiState,
            val transferStarted: Boolean,
        )

        val initial =
            Accumulator(
                ui =
                    SessionUiState(
                        phase = UiPhase.ENGAGEMENT,
                        substateLabel = "Preparing…",
                        nfcMode = NfcMode.DISABLED,
                        qrMode = QrMode.NONE,
                        isSuspended = false,
                        progressHint = null,
                        terminalOutcome = null,
                        terminalMessage = null,
                        userInteractionRequired = false,
                        deviceRequest = null,
                    ),
                transferStarted = false,
            )

        return sessionEvents()
            .runningFold(initial) { accumulator, event ->
                log.debug("Processing SessionEvent: ${event::class.simpleName} [currentPhase=${accumulator.ui.phase}, transferStarted=${accumulator.transferStarted}]")

                val suspended = activeEngagement.value?.isActive?.value == false
                var transferStarted = accumulator.transferStarted
                var ui = accumulator.ui

                // Compute NFC state based on engagements
                val nfcEngagement = engagementsByType.value[EngagementType.NFC]
                val nfcMode =
                    when {
                        nfcEngagement == null -> NfcMode.DISABLED
                        nfcEngagement == activeEngagement.value -> NfcMode.FOREGROUND
                        else -> NfcMode.BACKGROUND
                    }

                // Helper function to compute QR mode based on engagement state
                fun computeQrMode(): QrMode {
                    // NOTE: SCAN mode is UI-managed and set manually by the UI layer.
                    // TO_APP engagements are created AFTER scanning, so they don't trigger SCAN mode.
                    val qrEngagement = engagementsByType.value[EngagementType.QR]
                    return when {
                        // Display QR if we have a QR engagement
                        // QR should remain visible during CONNECTING (BLE scanning/advertising)
                        // QR should only hide when we have an actual connection (CONNECTED or beyond)
                        qrEngagement != null -> {
                            val state = qrEngagement.getCurrentState()
                            // Hide QR only when we reach CONNECTED state or beyond (when remote party has actually connected)
                            if (state.order < MdocEngagementState.CONNECTED.order) {
                                QrMode.DISPLAY
                            } else {
                                QrMode.NONE
                            }
                        }

                        else -> {
                            QrMode.NONE
                        } // TO_APP doesn't set SCAN - that's UI-managed before engagement
                    }
                }

                // Compute QR mode based on current engagement state
                var qrMode = computeQrMode()

                log.debug(
                    "Computed derived state: nfcState=$nfcMode, qrMode=$qrMode, suspended=$suspended [nfcEng=${nfcEngagement != null}, qrEng=${engagementsByType.value[EngagementType.QR] != null}, active=${activeEngagement.value?.id}]",
                )

                when (event) {
                    SessionEvent.QrShow -> {
                        // Recompute QR mode based on engagement state
                        qrMode = computeQrMode()
                        // Determine if showing or scanning based on engagement type
                        val label =
                            if (qrMode == QrMode.SCAN) {
                                "Scan reader's QR code"
                            } else {
                                "Scan the QR with reader"
                            }
                        ui =
                            ui.copy(
                                phase = UiPhase.ENGAGEMENT,
                                substateLabel = label,
                                nfcMode = nfcMode,
                                qrMode = qrMode,
                                isSuspended = suspended,
                            )
                    }

                    SessionEvent.NfcPromptShown -> {
                        ui =
                            ui.copy(
                                phase = UiPhase.ENGAGEMENT,
                                substateLabel = "Hold near reader",
                                nfcMode = nfcMode,
                                qrMode = qrMode,
                                isSuspended = suspended,
                            )
                    }

                    SessionEvent.NfcHandoverSuccess -> {
                        ui =
                            ui.copy(
                                phase = UiPhase.TRANSFER,
                                substateLabel = "Connecting…",
                                nfcMode = nfcMode,
                                qrMode = qrMode,
                                isSuspended = suspended,
                            )
                    }

                    SessionEvent.TransferConnecting -> {
                        transferStarted = true
                        ui =
                            ui.copy(
                                phase = UiPhase.TRANSFER,
                                substateLabel = "Connecting…",
                                nfcMode = nfcMode,
                                qrMode = qrMode,
                                isSuspended = suspended,
                            )
                    }

                    SessionEvent.TransferConnected -> {
                        transferStarted = true
                        // Hide QR on Connected event (dispatched just before TransmissionTypeSelected)
                        // This is the earliest moment we know someone has actually connected
                        qrMode = QrMode.NONE
                        ui =
                            ui.copy(
                                phase = UiPhase.TRANSFER,
                                substateLabel = "Connected",
                                nfcMode = nfcMode,
                                qrMode = qrMode,
                                isSuspended = suspended,
                            )
                    }

                    is SessionEvent.UserInteractionRequired -> {
                        transferStarted = true
                        ui =
                            ui.copy(
                                phase = UiPhase.TRANSFER,
                                substateLabel = "Review request",
                                nfcMode = nfcMode,
                                qrMode = qrMode,
                                userInteractionRequired = true,
                                deviceRequest = event.deviceRequest,
                                isSuspended = suspended,
                            )
                    }

                    SessionEvent.UserAccepted -> {
                        transferStarted = true
                        ui =
                            ui.copy(
                                phase = UiPhase.TRANSFER,
                                substateLabel = "Sharing credentials…",
                                nfcMode = nfcMode,
                                qrMode = qrMode,
                                userInteractionRequired = false,
                                deviceRequest = null,
                                progressHint = 0.5f, // Show progress during sharing
                                isSuspended = suspended,
                            )
                    }

                    SessionEvent.UserDeclined -> {
                        // User declined - transition to terminal with DECLINED outcome
                        transferStarted = true
                        ui =
                            ui.copy(
                                phase = UiPhase.TERMINAL,
                                substateLabel = "Declined",
                                nfcMode = nfcMode,
                                qrMode = qrMode,
                                terminalOutcome = TerminalOutcome.DECLINED,
                                terminalMessage = "User declined to share data",
                                userInteractionRequired = false,
                                deviceRequest = null,
                                isSuspended = false,
                            )
                    }

                    is SessionEvent.TransferProgress -> {
                        transferStarted = true
                        val progress = event.fraction.coerceIn(0f, 1f)
                        // Show "Data sent" when progress >= 0.9 (data sent, waiting for reader)
                        val label =
                            if (progress >= 0.9f) {
                                "Data sent"
                            } else {
                                "Transferring…"
                            }
                        ui =
                            ui.copy(
                                phase = UiPhase.TRANSFER,
                                substateLabel = label,
                                nfcMode = nfcMode,
                                qrMode = qrMode,
                                progressHint = progress,
                                userInteractionRequired = false, // Clear user interaction during transfer
                                deviceRequest = null,
                                isSuspended = suspended,
                            )
                    }

                    is SessionEvent.Terminal -> {
                        // Mark transfer as started on terminal
                        transferStarted = true

                        // Determine substate label based on outcome
                        val label =
                            when (event.outcome) {
                                TerminalOutcome.SUCCESS -> "Completed"
                                TerminalOutcome.DECLINED -> "Declined"
                                TerminalOutcome.CANCELED -> "Canceled"
                                TerminalOutcome.ERROR -> "Error"
//                            TerminalOutcome.TERMINATED -> "Terminated"
                            }

                        ui =
                            ui.copy(
                                phase = UiPhase.TERMINAL,
                                substateLabel = label,
                                nfcMode = nfcMode,
                                qrMode = QrMode.NONE,
                                progressHint = null,
                                terminalOutcome = event.outcome,
                                terminalMessage = event.message,
                                isSuspended = false,
                                userInteractionRequired = false,
                                deviceRequest = null,
                            )
                    }
                }

                val result =
                    Accumulator(
                        ui = ui,
                        transferStarted = transferStarted,
                    )

                log.debug("Resulting UiState: phase=${ui.phase}, qrMode=${ui.qrMode}, nfcState=${ui.nfcMode}, label='${ui.substateLabel}', userInteraction=${ui.userInteractionRequired}")

                result
            }.map { it.ui }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = initial.ui,
            )
    }
}
