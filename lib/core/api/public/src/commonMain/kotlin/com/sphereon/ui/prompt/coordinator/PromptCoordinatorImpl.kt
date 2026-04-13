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

package com.sphereon.ui.prompt.coordinator

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.ui.prompt.core.CancelReason
import com.sphereon.ui.prompt.core.PromptHandle
import com.sphereon.ui.prompt.core.PromptHandleImpl
import com.sphereon.ui.prompt.core.PromptId
import com.sphereon.ui.prompt.core.PromptRequest
import com.sphereon.ui.prompt.core.PromptResponse
import com.sphereon.ui.prompt.core.PromptState
import com.sphereon.ui.prompt.presenter.PromptPresenter
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default implementation of PromptCoordinator.
 *
 * Handles:
 * - Tracking active prompts
 * - Validating request/response type pairings
 * - Rejecting late responses (prompt already completed)
 * - Cleanup when prompts complete
 */
@Inject
@ContributesBinding(SessionScope::class, binding = binding<PromptCoordinator>())
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("PromptCoordinatorImpl", exact = true)
class PromptCoordinatorImpl(
    private val presenter: PromptPresenter,
    private val registry: PromptRegistry = DefaultPromptRegistry.INSTANCE
) : PromptCoordinator {

    private val mutex = Mutex()

    // Map of active prompt handles by ID
    private val handles = mutableMapOf<PromptId, PromptHandleImpl<*, *>>()

    // The scheduler for managing prompt queue
    private val scheduler: PromptSchedulerImpl by lazy {
        PromptSchedulerImpl(presenter).also { scheduler ->
            scheduler.setOnRelease { id, _ ->
                // Cleanup when prompts are released
                handles.remove(id)
            }
        }
    }

    override val activePrompts: SharedFlow<PromptRequest>
        get() = scheduler.activePrompts

    override val activePromptCount: Int
        get() = handles.count { it.value.isActive }

    override suspend fun <Req : PromptRequest, Res : PromptResponse> request(
        request: Req
    ): PromptHandle<Req, Res> {
        return mutex.withLock {
            // Create the handle
            val handle = PromptHandleImpl<Req, Res>(request.id, request)
            handles[request.id] = handle

            // Schedule the prompt
            val result = scheduler.schedule(request)
            if (result == ScheduleResult.Immediate) {
                handle.markActive()
            }

            handle
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <Res : PromptResponse> respond(
        id: PromptId,
        response: Res
    ): IdkResult<Unit, IdkError> {
        val handle = mutex.withLock { handles[id] }

        // Case 1: Not found
        if (handle == null) {
            return Err(IdkError.NOT_FOUND_ERROR(message = "Prompt not found: $id"))
        }

        // Case 2: Late response (prompt already completed)
        if (!handle.isActive) {
            return Err(IdkError.INVALID_STATE(message = "Prompt already completed: $id"))
        }

        // Case 3: Type mismatch validation
        val validationError = registry.validatePairing(handle.request, response)
        if (validationError != null) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = validationError))
        }

        // Complete the prompt
        val completed = (handle as PromptHandleImpl<*, Res>).tryComplete(response)
        if (!completed) {
            return Err(IdkError.INVALID_STATE(message = "Failed to complete prompt: $id"))
        }

        // Release from scheduler and cleanup
        scheduler.release(id, response.state)

        return Ok(Unit)
    }

    override suspend fun cancel(id: PromptId, reason: CancelReason): IdkResult<Unit, IdkError> {
        val handle = mutex.withLock { handles[id] }

        if (handle == null) {
            return Err(IdkError.NOT_FOUND_ERROR(message = "Prompt not found: $id"))
        }

        if (!handle.isActive) {
            return Err(IdkError.INVALID_STATE(message = "Prompt already completed: $id"))
        }

        val cancelled = handle.cancel(reason)
        if (!cancelled) {
            return Err(IdkError.INVALID_STATE(message = "Failed to cancel prompt: $id"))
        }

        scheduler.release(id, PromptState.CANCELLED)

        return Ok(Unit)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <Req : PromptRequest, Res : PromptResponse> getHandle(
        id: PromptId
    ): PromptHandle<Req, Res>? {
        val handle = handles[id] ?: return null
        return if (handle.isActive) handle as PromptHandle<Req, Res> else null
    }

    override fun isActive(id: PromptId): Boolean {
        return handles[id]?.isActive == true
    }

    @ContributesTo(SessionScope::class)
    interface Component {
        val promptCoordinator: PromptCoordinator
    }
}
