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
 */

package com.sphereon.openid.oid4vp.universal.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.events.SessionEventService
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.universal.DeleteAuthRequestOutput
import com.sphereon.openid.oid4vp.universal.DeleteAuthRequestServiceCommand
import com.sphereon.openid.oid4vp.universal.GetAuthRequestStatusInput
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpEventTypes
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Typed service command implementation for deleting authorization requests.
 *
 * DELETE /oid4vp/backend/auth/requests/{correlation_id}
 *
 * This implementation uses the Binary API v5 pattern with:
 * - Typed input: [GetAuthRequestStatusInput] (path param: correlationId)
 * - Typed output: [DeleteAuthRequestOutput]
 * - HTTP binding via [PublicApiCommand] on [DeleteAuthRequestServiceCommand]
 *
 * Deleting a session removes it from the store and cleans up any associated state.
 */
@Inject
@SingleIn(SessionScope::class)
class DeleteAuthRequestServiceCommandImpl(
    execution: SessionExecution,
    private val authorizationSessionStore: AuthorizationSessionStore,
    private val sessionEventService: SessionEventService,
) : TypedServiceCommandAdapter<GetAuthRequestStatusInput, DeleteAuthRequestOutput>(
        commandId = DeleteAuthRequestServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetAuthRequestStatusInput>(),
        outputTypeToken = typeToken<DeleteAuthRequestOutput>(),
    ),
    DeleteAuthRequestServiceCommand {
    override val commandId: String get() = DeleteAuthRequestServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetAuthRequestStatusInput,
        applyDuring: (GetAuthRequestStatusInput) -> GetAuthRequestStatusInput,
    ): IdkResult<DeleteAuthRequestOutput, IdkError> {
        val input = applyDuring(args)
        val correlationId = input.correlationId

        // 1. Check if session exists
        val session =
            authorizationSessionStore.getByCorrelationId(correlationId).getOrNull()
                ?: return Err(
                    IdkError.NOT_FOUND_ERROR(
                        message = "Authorization request not found: $correlationId",
                    ),
                )

        // 2. Delete the session
        val deleteResult = authorizationSessionStore.delete(correlationId)
        if (deleteResult.isErr) {
            return Err(
                IdkError.UNKNOWN_ERROR(
                    message = "Failed to delete authorization request: $correlationId",
                ),
            )
        }

        // 3. Emit SESSION_DELETED event
        emitSessionDeletedEvent(correlationId)

        // 4. Return success
        return Ok(
            DeleteAuthRequestOutput(
                correlationId = correlationId,
                deleted = true,
            ),
        )
    }

    private suspend fun emitSessionDeletedEvent(correlationId: String) {
        try {
            sessionEventService.emit(
                sessionEventService
                    .eventBuilder()
                    .type(UniversalOid4vpEventTypes.SESSION_DELETED)
                    .origin(DeleteAuthRequestServiceCommand.COMMAND_ID)
                    .payload(
                        buildJsonObject {
                            put("correlationId", correlationId)
                        },
                    ).build(),
            )
        } catch (expected: Exception) {
            // Best effort - don't fail the request if event emission fails
            log.debug("Failed to emit SESSION_DELETED event: ${expected.message}")
        }
    }
}
