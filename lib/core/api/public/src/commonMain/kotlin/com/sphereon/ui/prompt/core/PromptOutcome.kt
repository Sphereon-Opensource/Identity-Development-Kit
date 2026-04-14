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

package com.sphereon.ui.prompt.core

import com.sphereon.core.api.error.IdkError
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Instant

/**
 * Represents the outcome of a prompt operation.
 * This is the result returned from awaiting a prompt handle.
 *
 * @param Res The type of response expected for success outcomes.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PromptOutcome", exact = true)
sealed interface PromptOutcome<out Res : PromptResponse> {
    /**
     * The prompt ID associated with this outcome.
     */
    val promptId: PromptId

    /**
     * The final state of the prompt.
     */
    val state: PromptState

    /**
     * Prompt completed successfully with a response.
     */
    data class Success<Res : PromptResponse>(
        override val promptId: PromptId,
        val response: Res,
    ) : PromptOutcome<Res> {
        override val state: PromptState = PromptState.SUCCESS
    }

    /**
     * Prompt was cancelled by user or system.
     */
    data class Cancelled(
        override val promptId: PromptId,
        val reason: CancelReason,
    ) : PromptOutcome<Nothing> {
        override val state: PromptState = PromptState.CANCELLED
    }

    /**
     * Prompt timed out without receiving a response.
     */
    data class TimedOut(
        override val promptId: PromptId,
        val at: Instant,
    ) : PromptOutcome<Nothing> {
        override val state: PromptState = PromptState.TIMED_OUT
    }

    /**
     * Prompt failed due to an error.
     */
    data class Error(
        override val promptId: PromptId,
        val error: IdkError,
    ) : PromptOutcome<Nothing> {
        override val state: PromptState = PromptState.ERROR
    }
}

/**
 * Extension to check if the outcome is successful.
 */
val <Res : PromptResponse> PromptOutcome<Res>.isSuccess: Boolean
    get() = this is PromptOutcome.Success

/**
 * Extension to check if the outcome is cancelled.
 */
val <Res : PromptResponse> PromptOutcome<Res>.isCancelled: Boolean
    get() = this is PromptOutcome.Cancelled

/**
 * Extension to check if the outcome is timed out.
 */
val <Res : PromptResponse> PromptOutcome<Res>.isTimedOut: Boolean
    get() = this is PromptOutcome.TimedOut

/**
 * Extension to check if the outcome is an error.
 */
val <Res : PromptResponse> PromptOutcome<Res>.isError: Boolean
    get() = this is PromptOutcome.Error

/**
 * Extension to get the response or null if not successful.
 */
fun <Res : PromptResponse> PromptOutcome<Res>.responseOrNull(): Res? = (this as? PromptOutcome.Success)?.response

/**
 * Extension to fold over the outcome.
 */
inline fun <Res : PromptResponse, T> PromptOutcome<Res>.fold(
    onSuccess: (Res) -> T,
    onCancelled: (CancelReason) -> T,
    onTimedOut: (Instant) -> T,
    onError: (IdkError) -> T,
): T =
    when (this) {
        is PromptOutcome.Success -> onSuccess(response)
        is PromptOutcome.Cancelled -> onCancelled(reason)
        is PromptOutcome.TimedOut -> onTimedOut(at)
        is PromptOutcome.Error -> onError(error)
    }
