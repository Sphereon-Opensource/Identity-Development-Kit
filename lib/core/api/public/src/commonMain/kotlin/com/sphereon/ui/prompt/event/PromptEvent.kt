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

package com.sphereon.ui.prompt.event

import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventType
import com.sphereon.ui.prompt.core.CancelReason
import com.sphereon.ui.prompt.core.PromptId
import com.sphereon.ui.prompt.core.PromptOutcome
import com.sphereon.ui.prompt.core.PromptRequest
import com.sphereon.ui.prompt.core.PromptResponse
import com.sphereon.ui.prompt.core.PromptState
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Instant

/**
 * Event subsystem for prompt-related events.
 */
object PromptEventSubsystems {
    val PROMPT = EventSubsystem("prompt")
}

/**
 * Event types for prompt lifecycle events.
 */
object PromptEventTypes {
    val PROMPT_REQUESTED = EventType("prompt.requested")
    val PROMPT_PRESENTED = EventType("prompt.presented")
    val PROMPT_STATE_UPDATED = EventType("prompt.state_updated")
    val PROMPT_COMPLETED = EventType("prompt.completed")
    val PROMPT_DEFERRED = EventType("prompt.deferred")
    val PROMPT_RESPONDED = EventType("prompt.responded")
    val PROMPT_CANCELLED = EventType("prompt.cancelled")
    val PROMPT_TIMED_OUT = EventType("prompt.timed_out")
    val PROMPT_ERROR = EventType("prompt.error")
}

/**
 * Base interface for prompt lifecycle events.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PromptEvent", exact = true)
sealed interface PromptEvent {
    /**
     * The prompt ID associated with this event.
     */
    val promptId: PromptId

    /**
     * Timestamp when this event occurred.
     */
    val timestamp: Instant

    /**
     * Prompt was requested.
     */
    data class Requested(
        override val promptId: PromptId,
        override val timestamp: Instant,
        val request: PromptRequest,
    ) : PromptEvent

    /**
     * Prompt was presented to the user.
     */
    data class Presented(
        override val promptId: PromptId,
        override val timestamp: Instant,
    ) : PromptEvent

    /**
     * Prompt state was updated (for wizard/multi-step prompts).
     */
    data class StateUpdated(
        override val promptId: PromptId,
        override val timestamp: Instant,
        val previousState: PromptState?,
        val newState: PromptState,
    ) : PromptEvent

    /**
     * Prompt was completed (success, cancelled, timed out, or error).
     */
    data class Completed(
        override val promptId: PromptId,
        override val timestamp: Instant,
        val outcome: PromptOutcome<*>,
    ) : PromptEvent

    /**
     * Prompt was deferred (e.g., app went to background).
     */
    data class Deferred(
        override val promptId: PromptId,
        override val timestamp: Instant,
        val reason: String,
    ) : PromptEvent

    /**
     * Prompt received a response.
     */
    data class Responded(
        override val promptId: PromptId,
        override val timestamp: Instant,
        val response: PromptResponse,
    ) : PromptEvent

    /**
     * Prompt was cancelled.
     */
    data class Cancelled(
        override val promptId: PromptId,
        override val timestamp: Instant,
        val reason: CancelReason,
    ) : PromptEvent

    /**
     * Prompt timed out.
     */
    data class TimedOut(
        override val promptId: PromptId,
        override val timestamp: Instant,
    ) : PromptEvent

    /**
     * Prompt encountered an error.
     */
    data class Error(
        override val promptId: PromptId,
        override val timestamp: Instant,
        val message: String,
        val exception: Throwable? = null,
    ) : PromptEvent
}

/**
 * Extension to convert a PromptEvent to an EventType.
 */
fun PromptEvent.toEventType(): EventType =
    when (this) {
        is PromptEvent.Requested -> PromptEventTypes.PROMPT_REQUESTED
        is PromptEvent.Presented -> PromptEventTypes.PROMPT_PRESENTED
        is PromptEvent.StateUpdated -> PromptEventTypes.PROMPT_STATE_UPDATED
        is PromptEvent.Completed -> PromptEventTypes.PROMPT_COMPLETED
        is PromptEvent.Deferred -> PromptEventTypes.PROMPT_DEFERRED
        is PromptEvent.Responded -> PromptEventTypes.PROMPT_RESPONDED
        is PromptEvent.Cancelled -> PromptEventTypes.PROMPT_CANCELLED
        is PromptEvent.TimedOut -> PromptEventTypes.PROMPT_TIMED_OUT
        is PromptEvent.Error -> PromptEventTypes.PROMPT_ERROR
    }

/**
 * Extension to convert a PromptState to the corresponding event type.
 */
fun PromptState.toEventType(): EventType =
    when (this) {
        PromptState.PENDING -> PromptEventTypes.PROMPT_REQUESTED
        PromptState.ACTIVE -> PromptEventTypes.PROMPT_PRESENTED
        PromptState.SUCCESS -> PromptEventTypes.PROMPT_RESPONDED
        PromptState.CANCELLED -> PromptEventTypes.PROMPT_CANCELLED
        PromptState.TIMED_OUT -> PromptEventTypes.PROMPT_TIMED_OUT
        PromptState.ERROR -> PromptEventTypes.PROMPT_ERROR
    }
