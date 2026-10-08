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

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.mdoc.MdocEvent
import com.sphereon.mdoc.transfer.MdocRetrievalEvent
import com.sphereon.mdoc.transfer.MdocRetrievalStateType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents an event with a sequence number for event ordering and debugging.
 * The timestamp is already available in the event itself via event.time.
 *
 * @property event The mdoc event (engagement or transfer/retrieval event)
 * @property sequenceNumber A monotonically increasing sequence number for ordering events
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SequencedEvent", exact = true)
@JsExportCompat
data class SequencedEvent(
    val event: MdocEvent,
    val sequenceNumber: Long,
)

/**
 * Central hub for managing and observing mdoc events.
 * This is the primary integration point for UI layers to consume events from the engagement manager.
 *
 * Provides:
 * - Access to all event streams (engagement, transfer, combined)
 * - Filtered event streams (by state order ranges)
 * - State tracking (latest events, current states)
 * - Event history/buffer for debugging and state reconstruction
 * - Convenience helpers for common UI patterns
 * - Handler registration for both engagement and retrieval events
 *
 * ## Usage Examples
 *
 * ### Kotlin/Android
 * ```kotlin
 * // Observe all events
 * manager.eventHub.allEvents.collect { event ->
 *     when (event) {
 *         is MdocEngagementEvent.QrShow -> showQrCode(event.qrCodeData)
 *         is MdocEngagementEvent.Connected -> onConnected()
 *         is MdocRetrievalEvent.ResponseSent -> onTransferComplete()
 *     }
 * }
 *
 * // Get current states
 * val currentStates = manager.eventHub.getCurrentStateByEngagement().value
 *
 * // Get event history for debugging
 * val recentEvents = manager.eventHub.getRecentEvents(count = 20)
 * ```
 *
 * ### iOS/Swift
 * ```swift
 * // Observe events using flow adapters
 * for await event in manager.eventHub.allEvents {
 *     // Handle event
 * }
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocEventHub", exact = true)
@JsExportCompat
interface MdocEventHub :
    MdocEngagementEvent.Handlers,
    MdocRetrievalEvent.Handlers {
    /**
     * Combined stream of ALL mdoc events (engagement + transfer).
     * This is a hot SharedFlow with configurable replay buffer.
     *
     * Use this when you need to observe all activity across all engagements and transfers.
     */
    val allEvents: SharedFlow<MdocEvent>

    /**
     * Stream of engagement-related events only.
     * Includes QR display, NFC taps, connections, disconnections, etc.
     *
     * Use this when you only care about engagement lifecycle events.
     */
    val engagementEvents: SharedFlow<MdocEngagementEvent>

    /**
     * Stream of transfer/retrieval events only.
     * Includes device request reception, response sending, transfer states, etc.
     *
     * Use this when you only care about data transfer events.
     */
    val transferEvents: SharedFlow<MdocRetrievalEvent>

    /**
     * Stream of transfer completion events (final states with order >= 200).
     * Emits when any transfer completes successfully or with an error.
     *
     * Use this to handle cleanup, navigation, or auto-restart logic.
     */
    val onTransferCompletion: SharedFlow<MdocRetrievalEvent>

    // ========================================
    // Filtered Event Streams
    // ========================================

    /**
     * Returns a flow of events within a specific state order range.
     *
     * State order ranges:
     * - 0-99: Engagement initialization and setup
     * - 100-199: Active transfer/data exchange
     * - 200+: Final/termination states
     *
     * @param minOrder Minimum state order (inclusive)
     * @param maxOrder Maximum state order (inclusive)
     * @return Flow of events matching the state order range
     */
    fun eventsInStateRange(
        minOrder: Int,
        maxOrder: Int,
    ): Flow<MdocEvent>

    /**
     * Returns a flow of only final state events (order >= 200).
     * These represent termination states: completion, error, cancellation, etc.
     *
     * Use this to detect when engagements or transfers finish.
     */
    fun finalStateEvents(): Flow<MdocEvent>

    /**
     * Returns a flow of only active transfer events (order 100-199).
     * These represent ongoing data exchange states.
     *
     * Use this to track transfer progress.
     */
    fun activeTransferEvents(): Flow<MdocRetrievalEvent>

    // ========================================
    // State Tracking
    // ========================================

    /**
     * A StateFlow containing the most recent engagement event for each engagement.
     * Map key is the engagement ID, value is the latest engagement event for that engagement.
     *
     * Use this to display current status of all active engagements.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun latestEventByEngagement(): StateFlow<Map<Uuid, MdocEngagementEvent>>

    /**
     * A StateFlow containing the most recent transfer event for each engagement.
     * Map key is the engagement ID, value is the latest transfer event for that engagement.
     *
     * Use this to display current transfer status. Since transfer events now include engagementId,
     * you can easily correlate transfer events with their originating engagement.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun latestTransferEventByEngagement(): StateFlow<Map<Uuid, MdocRetrievalEvent>>

    /**
     * A StateFlow containing the current engagement state for each engagement.
     * Map key is the engagement ID, value is the current state.
     *
     * Use this for state-based UI logic (e.g., show spinner when CONNECTING).
     */
    @OptIn(ExperimentalUuidApi::class)
    fun getCurrentStateByEngagement(): StateFlow<Map<Uuid, MdocEngagementState>>

    /**
     * A StateFlow containing the current transfer state for each engagement.
     * Map key is the engagement ID, value is the current transfer state.
     *
     * Use this for state-based UI logic during transfers.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun getCurrentTransferStateByEngagement(): StateFlow<Map<Uuid, MdocRetrievalStateType>>

    // ========================================
    // Event History & Debugging
    // ========================================

    /**
     * Configure the event buffer size for history tracking.
     * Default is 10 events. Set to 0 to disable buffering (not recommended).
     *
     * @param size Number of recent events to keep in buffer
     */
    fun setEventBufferSize(size: Int)

    /**
     * Retrieve recent events from the buffer for debugging or state reconstruction.
     *
     * @param count Maximum number of events to retrieve (default: 10)
     * @return List of recent events, ordered from oldest to newest
     */
    fun getRecentEvents(count: Int = 10): List<MdocEvent>

    /**
     * Retrieve recent engagement events from the buffer.
     *
     * @param count Maximum number of events to retrieve (default: 10)
     * @return List of recent engagement events, ordered from oldest to newest
     */
    fun getRecentEngagementEvents(count: Int = 10): List<MdocEngagementEvent>

    /**
     * Retrieve recent transfer events from the buffer.
     *
     * @param count Maximum number of events to retrieve (default: 10)
     * @return List of recent transfer events, ordered from oldest to newest
     */
    fun getRecentTransferEvents(count: Int = 10): List<MdocRetrievalEvent>

    /**
     * Retrieve the complete event timeline with sequence numbers.
     * Useful for debugging and analytics.
     *
     * @return List of sequenced events, ordered by sequence number
     */
    fun getEventTimeline(): List<SequencedEvent>

    /**
     * Get the current size of the event buffer.
     */
    fun getEventBufferSize(): Int

    // ========================================
    // State Management
    // ========================================

    /**
     * Clears all internal state tracking maps and resets the session state to initial.
     *
     * This method should be called after disposing/closing the active engagement and transfer
     * to ensure a clean UI reset when navigating back to engagement screens. It clears:
     * - Event buffers
     * - Latest event tracking maps
     * - Current state tracking maps
     * - Resets sessionState to initial ENGAGEMENT phase
     *
     * This is crucial for ensuring the UI doesn't retain stale state from previous sessions.
     */
    fun clearAllState()

    /**
     * Clears state tracking for a specific engagement ID.
     *
     * This method should be called when an individual engagement is closed to prevent
     * memory leaks from accumulating tracking state. It removes the engagement from:
     * - Latest event tracking maps
     * - Current state tracking maps
     *
     * @param engagementId The ID of the engagement to clear state for
     */
    @OptIn(ExperimentalUuidApi::class)
    fun clearStateForEngagement(engagementId: Uuid)

    // ========================================
    // UI Projection Layer (Optional)
    // ========================================

    /**
     * Stream of simplified UI-relevant session events.
     * This is an optional convenience layer that maps raw engagement and retrieval events
     * to a minimal set of UI-relevant events for simple integration.
     *
     * Use this when you want a simplified event stream without the complexity of
     * handling all raw engagement and retrieval events.
     */
    val sessionEvents: SharedFlow<SessionEvent>

    /**
     * Opinionated UI state projection for simple integration.
     * This provides a deterministic, single-state view of the session lifecycle
     * with built-in rules for QR visibility, phase transitions, and progress tracking.
     *
     * Key features:
     * - Deterministic QR hiding on transfer start
     * - Clear phase transitions (ENGAGEMENT -> TRANSFER -> TERMINAL)
     * - Suspension state tracking
     * - Progress hints for transfer phase
     *
     * Use this for simple UI integration where you want a single state flow
     * that handles all the complexity of engagement and transfer coordination.
     */
    val sessionState: StateFlow<SessionUiState>

    // Handler registration methods are inherited from:
    // - MdocEngagementEvent.Handlers (addEngagementEventListener, removeEngagementEventListener, etc.)
    // - MdocRetrievalEvent.Handlers (addListener, removeListener, clearListeners)
}
