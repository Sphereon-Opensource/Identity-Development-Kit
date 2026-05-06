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

package com.sphereon.oauth2.server.authorization.impl.http.command.federation

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.federation.FederationCallbackHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.federation.FederationCallbackOutcomeType
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationCallbackArgs
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationCallbackCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * HTTP shell over [HandleFederationCallbackCommand]. Dispatches to the service command for the
 * business decision (federation vs reconciliation) and renders a 302:
 *   - reconciliation outcomes redirect to the auth-bridge URL returned by the handler;
 *   - federation outcomes redirect to `/authorize/callback?session_id=...` where the adapter's
 *     existing local-login callback path finishes OAuth2 code issuance against the same pending
 *     authorization session map.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FederationCallbackHttpEndpointCommand>())
class FederationCallbackHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val handleFederationCallbackCommand: HandleFederationCallbackCommand,
) : HttpEndpointCommandAdapter(
        id = FederationCallbackHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = FederationCallbackHttpEndpointCommand.ENDPOINT,
    ),
    FederationCallbackHttpEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val code =
            request.queryParameters["code"]
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing 'code' parameter"))
        val state =
            request.queryParameters["state"]
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing 'state' parameter"))

        val result =
            handleFederationCallbackCommand.execute(
                HandleFederationCallbackArgs(code = code, state = state),
            )
        if (result.isErr) return Err(IdkError.fromDTO(result.error))

        val outcome = result.value
        val location =
            when (outcome.outcomeType) {
                FederationCallbackOutcomeType.RECONCILIATION_COMPLETE -> {
                    outcome.reconciliation!!.redirectUrl
                }

                FederationCallbackOutcomeType.FEDERATION_COMPLETE -> {
                    "/authorize/callback?session_id=${outcome.federation!!.sessionId}"
                }
            }
        return Ok(
            GenericHttpResponse(
                statusCode = 302,
                headers = mapOf("Location" to location, "Cache-Control" to "no-store"),
                body = "",
            ),
        )
    }
}
