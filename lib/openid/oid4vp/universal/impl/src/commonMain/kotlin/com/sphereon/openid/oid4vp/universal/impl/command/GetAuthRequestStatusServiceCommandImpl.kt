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
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.events.SessionEventService
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.SessionError
import com.sphereon.openid.oid4vp.universal.GetAuthRequestStatusInput
import com.sphereon.openid.oid4vp.universal.GetAuthRequestStatusServiceCommand
import com.sphereon.openid.oid4vp.universal.GetAuthorizationRequestStatusOutput
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpEventTypes
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Typed service command implementation for getting authorization request status.
 *
 * GET /oid4vp/backend/auth/requests/{correlation_id}
 *
 * This implementation uses the Binary API v5 pattern with:
 * - Typed input: [GetAuthRequestStatusInput] (path param: correlationId)
 * - Typed output: [GetAuthorizationRequestStatusOutput]
 * - HTTP binding via [PublicApiCommand] on [GetAuthRequestStatusServiceCommand]
 *
 * The input is automatically deserialized from path parameters by the BinaryCommandAdapter.
 * The output is automatically serialized to JSON.
 */
@Inject
@SingleIn(SessionScope::class)
class GetAuthRequestStatusServiceCommandImpl(
    execution: SessionExecution,
    private val authorizationSessionStore: AuthorizationSessionStore,
    private val sessionEventService: SessionEventService,
) : TypedServiceCommandAdapter<GetAuthRequestStatusInput, GetAuthorizationRequestStatusOutput>(
        commandId = GetAuthRequestStatusServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetAuthRequestStatusInput>(),
        outputTypeToken = typeToken<GetAuthorizationRequestStatusOutput>(),
    ),
    GetAuthRequestStatusServiceCommand {
    override val commandId: String get() = GetAuthRequestStatusServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetAuthRequestStatusInput,
        applyDuring: (GetAuthRequestStatusInput) -> GetAuthRequestStatusInput,
    ): IdkResult<GetAuthorizationRequestStatusOutput, IdkError> {
        val input = applyDuring(args)
        val correlationId = input.correlationId

        // 1. Get session from store
        val session =
            authorizationSessionStore.getByCorrelationId(correlationId).getOrNull()
                ?: return Err(
                    IdkError.NOT_FOUND_ERROR(
                        message = "Authorization request not found: $correlationId",
                    ),
                )

        // 2. Build verified data if session is verified
        val verifiedData =
            if (session.status == AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_VERIFIED) {
                buildVerifiedData(session)
            } else {
                null
            }

        // 3. Emit STATUS_POLLED event
        emitStatusPolledEvent(correlationId, session.status)

        // 4. Build response
        val sessionError =
            session.error?.let { error ->
                SessionError(code = error.code, message = error.message)
            }

        val output =
            GetAuthorizationRequestStatusOutput(
                correlationId = session.correlationId,
                queryId = session.queryId,
                status = session.status,
                lastUpdated = session.updatedAt,
                error = sessionError,
                verifiedData = verifiedData,
            )

        return Ok(output)
    }

    private suspend fun emitStatusPolledEvent(
        correlationId: String,
        status: AuthorizationSessionStatus,
    ) {
        try {
            sessionEventService.emit(
                sessionEventService
                    .eventBuilder()
                    .type(UniversalOid4vpEventTypes.STATUS_POLLED)
                    .origin(GetAuthRequestStatusServiceCommand.COMMAND_ID)
                    .payload(
                        buildJsonObject {
                            put("correlationId", correlationId)
                            put("status", status.name)
                        },
                    ).build(),
            )
        } catch (expected: Exception) {
            // Best effort - don't fail the request if event emission fails
            log.debug("Failed to emit STATUS_POLLED event: ${expected.message}")
        }
    }
}
