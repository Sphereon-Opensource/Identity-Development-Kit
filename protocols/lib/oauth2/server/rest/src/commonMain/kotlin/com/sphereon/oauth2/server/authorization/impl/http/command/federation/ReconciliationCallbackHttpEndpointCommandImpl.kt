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

// Role-neutral OAuth REST capability. Executable server startup remains in services-oauth2-as-rest.
package com.sphereon.oauth2.server.authorization.impl.http.command.federation

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.federation.FederationCallbackOutcomeType
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationCallbackArgs
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationCallbackCommand
import com.sphereon.oauth2.server.authorization.command.federation.ReconciliationCallbackHttpEndpointCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Dedicated HTTP shell for the reconciliation (IDV) callback path. Delegates to the shared
 * [HandleFederationCallbackCommand] and rejects federation-flow callbacks so deployments that
 * mount distinct callback URLs per flow type get clear errors instead of silent cross-routing.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(ReconciliationCallbackHttpEndpointCommand.COMMAND_ID)
class ReconciliationCallbackHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val handleFederationCallbackCommand: HandleFederationCallbackCommand,
) : HttpEndpointCommandAdapter(
        id = ReconciliationCallbackHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ReconciliationCallbackHttpEndpointCommand.ENDPOINT,
    ),
    ReconciliationCallbackHttpEndpointCommand {
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
        if (outcome.outcomeType != FederationCallbackOutcomeType.RECONCILIATION_COMPLETE) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "State did not correspond to a reconciliation flow; use /federation/callback",
                ),
            )
        }
        return Ok(
            GenericHttpResponse(
                statusCode = 302,
                headers =
                    mapOf(
                        "Location" to outcome.reconciliation!!.redirectUrl,
                        "Cache-Control" to "no-store",
                    ),
                body = "",
            ),
        )
    }
}
