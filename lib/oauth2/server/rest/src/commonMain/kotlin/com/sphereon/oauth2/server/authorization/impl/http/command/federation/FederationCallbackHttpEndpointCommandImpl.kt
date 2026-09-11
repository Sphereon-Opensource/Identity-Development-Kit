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
import com.sphereon.oauth2.server.authorization.command.federation.FederationCallbackHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.federation.FederationCallbackOutcomeType
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationCallbackArgs
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationCallbackCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseCommand
import com.sphereon.oauth2.server.authorization.storage.PendingAuthorizationSessionStore
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.http.command.authorization.renderAuthorizationDeliveryResponse
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * HTTP shell over [HandleFederationCallbackCommand]. Dispatches to the service command for the
 * business decision (federation vs reconciliation) and renders a 302:
 *   - reconciliation outcomes redirect to the auth-bridge URL returned by the handler;
 *   - federation outcomes redirect to the tenant authorization server's
 *     `/authorize/callback?session_id=...` where the adapter's existing local-login callback path
 *     finishes OAuth2 code issuance against the same pending authorization session map.
 *
 * The callback is built from the resolved public issuer rather than a root-relative path. Tenant
 * authorization servers are commonly mounted below a path prefix (for example
 * `/as/{slug}`); dropping that prefix would resume the transaction on the wrong authorization
 * server and can turn a successful upstream login into an unrelated local-login loop.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(FederationCallbackHttpEndpointCommand.COMMAND_ID)
class FederationCallbackHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val handleFederationCallbackCommand: HandleFederationCallbackCommand,
    private val pendingAuthorizationSessionStore: PendingAuthorizationSessionStore,
    private val createAuthorizationErrorResponseCommand: CreateAuthorizationErrorResponseCommand,
    private val configProvider: OAuth2ServersConfigProvider,
    private val baseUrlResolver: FederationBaseUrlResolver,
) : HttpEndpointCommandAdapter(
        id = FederationCallbackHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = FederationCallbackHttpEndpointCommand.ENDPOINT,
    ),
    FederationCallbackHttpEndpointCommand {
    override suspend fun supports(args: Any): Boolean =
        if (args is GenericHttpRequest) {
            (args.method.equals("GET", ignoreCase = true) || args.method.equals("POST", ignoreCase = true)) &&
                args.path == endpoint.pathPattern
        } else {
            false
        }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val state =
            request.queryParameters["state"]
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing 'state' parameter"))

        val result =
            handleFederationCallbackCommand.execute(
                HandleFederationCallbackArgs(
                    code = request.queryParameters["code"],
                    state = state,
                    error = request.queryParameters["error"],
                    errorDescription = request.queryParameters["error_description"],
                ),
            )
        if (result.isErr) return Err(IdkError.fromDTO(result.error))

        val outcome = result.value
        val location =
            when (outcome.outcomeType) {
                FederationCallbackOutcomeType.RECONCILIATION_COMPLETE -> {
                    outcome.reconciliation!!.redirectUrl
                }

                FederationCallbackOutcomeType.FEDERATION_COMPLETE -> {
                    val baseUrl = baseUrlResolver.resolveBaseUrl(request, configProvider).trimEnd('/')
                    "$baseUrl/authorize/callback?session_id=${outcome.federation!!.sessionId}"
                }

                FederationCallbackOutcomeType.UPSTREAM_ERROR -> {
                    val upstream = requireNotNull(outcome.upstreamError)
                    val downstream = pendingAuthorizationSessionStore.findById(upstream.sessionId)
                    if (downstream.isErr || downstream.value == null) {
                        return Err(IdkError.INVALID_STATE(message = "Downstream authorization transaction is unavailable"))
                    }
                    val session = requireNotNull(downstream.value)
                    val response = createAuthorizationErrorResponseCommand.execute(
                        CreateAuthorizationErrorResponseArgs(
                            error = upstream.error,
                            errorDescription = upstream.errorDescription,
                            state = session.state,
                            redirectUri = session.redirectUri,
                            responseMode = session.responseMode,
                        ),
                    )
                    if (response.isErr) return Err(IdkError.fromDTO(response.error))
                    return Ok(renderAuthorizationDeliveryResponse(
                        response.value.responseMode,
                        response.value.redirectUri,
                        response.value.formPostHtml,
                    ))
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
