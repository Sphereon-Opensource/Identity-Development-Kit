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
import com.sphereon.core.compat.JsExportCompat
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Handle for tracking and managing an active prompt.
 *
 * @param Req The type of request for this prompt.
 * @param Res The type of response expected for this prompt.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PromptHandle", exact = true)
interface PromptHandle<Req : PromptRequest, Res : PromptResponse> {
    /**
     * Unique identifier for this prompt.
     */
    val id: PromptId

    /**
     * The request that created this prompt.
     */
    val request: Req

    /**
     * Current state of the prompt.
     */
    val state: PromptState

    /**
     * Whether the prompt is still active (can receive responses).
     */
    val isActive: Boolean

    /**
     * Suspends until the prompt is completed and returns the outcome.
     */
    suspend fun await(): PromptOutcome<Res>

    /**
     * Suspends until the prompt is completed with a timeout.
     */
    suspend fun await(timeout: Duration): PromptOutcome<Res>

    /**
     * Attempts to cancel the prompt.
     * Returns true if cancellation was successful.
     */
    fun cancel(reason: CancelReason = CancelReason.USER): Boolean
}

/**
 * Internal implementation of PromptHandle.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PromptHandleImpl", exact = true)
class PromptHandleImpl<Req : PromptRequest, Res : PromptResponse>(
    override val id: PromptId,
    override val request: Req,
) : PromptHandle<Req, Res> {
    private val deferred = CompletableDeferred<PromptOutcome<Res>>()

    private val _state: AtomicRef<PromptState> = atomic(PromptState.PENDING)

    override val state: PromptState
        get() = _state.value

    override val isActive: Boolean
        get() = !_state.value.isTerminal

    /**
     * Marks the prompt as active (being displayed to user).
     */
    fun markActive() {
        if (_state.value == PromptState.PENDING) {
            _state.value = PromptState.ACTIVE
        }
    }

    /**
     * Attempts to complete the prompt with a response.
     * Returns true if the completion was accepted.
     */
    fun tryComplete(response: Res): Boolean {
        if (!isActive) {
            return false
        }

        _state.value = response.state
        return deferred.complete(PromptOutcome.Success(id, response))
    }

    /**
     * Completes the prompt with an error.
     */
    fun completeWithError(error: IdkError): Boolean {
        if (!isActive) {
            return false
        }

        _state.value = PromptState.ERROR
        return deferred.complete(PromptOutcome.Error(id, error))
    }

    /**
     * Completes the prompt as timed out.
     */
    fun completeWithTimeout(): Boolean {
        if (!isActive) {
            return false
        }

        _state.value = PromptState.TIMED_OUT
        return deferred.complete(PromptOutcome.TimedOut(id, Clock.System.now()))
    }

    override suspend fun await(): PromptOutcome<Res> = deferred.await()

    override suspend fun await(timeout: Duration): PromptOutcome<Res> =
        try {
            withTimeout(timeout) {
                deferred.await()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            completeWithTimeout()
            PromptOutcome.TimedOut(id, Clock.System.now())
        }

    override fun cancel(reason: CancelReason): Boolean {
        if (!isActive) {
            return false
        }

        _state.value = PromptState.CANCELLED
        return deferred.complete(PromptOutcome.Cancelled(id, reason))
    }
}
