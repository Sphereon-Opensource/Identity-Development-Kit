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

package com.sphereon.ui.prompt.coordinator

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.session.SessionScope
import com.sphereon.ui.prompt.core.CancelReason
import com.sphereon.ui.prompt.core.PromptHandle
import com.sphereon.ui.prompt.core.PromptId
import com.sphereon.ui.prompt.core.PromptRequest
import com.sphereon.ui.prompt.core.PromptResponse
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.flow.SharedFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Central coordinator for the prompt system.
 *
 * The coordinator is responsible for:
 * - Managing prompt lifecycle (request → active → response)
 * - Validating request/response type pairings
 * - Rejecting late responses
 * - Proper cleanup when prompts complete
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PromptCoordinator", exact = true)
interface PromptCoordinator {
    /**
     * Flow of prompts that are ready to be presented.
     * UI layer should collect this to display prompts.
     */
    val activePrompts: SharedFlow<PromptRequest>

    /**
     * Gets the current number of active prompts.
     */
    val activePromptCount: Int

    /**
     * Requests a prompt to be shown.
     * Returns a handle that can be used to await the response.
     *
     * @param Req The type of request.
     * @param Res The expected response type.
     * @param request The prompt request.
     * @return A handle for tracking and awaiting the prompt.
     */
    suspend fun <Req : PromptRequest, Res : PromptResponse> request(request: Req): PromptHandle<Req, Res>

    /**
     * Responds to an active prompt.
     *
     * This method validates:
     * - The prompt ID exists and is active
     * - The response type matches the request type
     *
     * @param Res The response type.
     * @param id The prompt ID to respond to.
     * @param response The response.
     * @return Ok(Unit) if successful, Err with appropriate error otherwise.
     */
    suspend fun <Res : PromptResponse> respond(
        id: PromptId,
        response: Res,
    ): IdkResult<Unit, IdkError>

    /**
     * Cancels an active prompt.
     *
     * @param id The prompt ID to cancel.
     * @param reason The reason for cancellation.
     * @return Ok(Unit) if cancelled, Err if prompt not found or already completed.
     */
    suspend fun cancel(
        id: PromptId,
        reason: CancelReason = CancelReason.USER,
    ): IdkResult<Unit, IdkError>

    /**
     * Gets a prompt handle by ID, if it exists and is still active.
     */
    fun <Req : PromptRequest, Res : PromptResponse> getHandle(id: PromptId): PromptHandle<Req, Res>?

    /**
     * Checks if a prompt is currently active.
     */
    fun isActive(id: PromptId): Boolean

    @ContributesTo(SessionScope::class)
    interface Graph {
        val promptCoordinator: PromptCoordinator
    }
}
