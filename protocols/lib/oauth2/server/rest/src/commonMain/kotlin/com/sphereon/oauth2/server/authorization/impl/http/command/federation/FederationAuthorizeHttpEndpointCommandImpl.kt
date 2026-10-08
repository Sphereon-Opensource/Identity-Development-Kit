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
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.command.federation.FederationAuthorizeHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationCommand
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRoutePlanner
import com.sphereon.oauth2.server.authorization.storage.PendingAuthorizationSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * HTTP shell over [InitiateProviderAuthenticationCommand] for the federation login entry point.
 * Parses the `provider` (and optional `login_hint`) query params, resolves the base URL through
 * [FederationBaseUrlResolver], generates a local session id, and renders the upstream redirect.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(FederationAuthorizeHttpEndpointCommand.COMMAND_ID)
class FederationAuthorizeHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val initiateProviderAuthenticationCommand: InitiateProviderAuthenticationCommand,
    private val configProvider: OAuth2ServersConfigProvider,
    private val baseUrlResolver: FederationBaseUrlResolver,
    private val pendingAuthorizationSessionStore: PendingAuthorizationSessionStore,
    private val authenticationRoutePlanner: AuthenticationRoutePlanner,
) : HttpEndpointCommandAdapter(
        id = FederationAuthorizeHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = FederationAuthorizeHttpEndpointCommand.ENDPOINT,
    ),
    FederationAuthorizeHttpEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val providerId =
            request.queryParameters["provider"]
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing 'provider' parameter"))

        val loginHint = request.queryParameters["login_hint"]
        val hint =
            if (loginHint != null) {
                AuthenticationHint(loginHint = loginHint, providerId = providerId)
            } else {
                null
            }

        val baseUrl = baseUrlResolver.resolveBaseUrl(request, configProvider).trimEnd('/')
        val pendingSessionId = request.queryParameters["session_id"]?.takeIf(String::isNotBlank)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing 'session_id' parameter"))
        val pendingResult = pendingAuthorizationSessionStore.findById(pendingSessionId)
        if (pendingResult.isErr) return Err(pendingResult.error)
        val pending = pendingResult.value
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unknown pending authorization session"))
        val decision = pending.authenticationRoute
            ?: return Err(IdkError.INVALID_STATE(message = "Authorization transaction has no authentication route"))
        if (decision.selectedBindingId != null && decision.selectedBindingId != providerId) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Federation binding does not match the transaction route"))
        }
        if (decision.eligibleBindings.none { it.bindingId == providerId }) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Federation binding is not eligible for this transaction"))
        }
        val revalidation = authenticationRoutePlanner.revalidate(decision, providerId)
        if (revalidation.isErr) return Err(revalidation.error)
        val sessionId = pendingSessionId
        val returnUrl = "$baseUrl/authorize/callback?session_id=$pendingSessionId"

        val result =
            initiateProviderAuthenticationCommand.execute(
                InitiateProviderAuthenticationArgs(
                    sessionId = sessionId,
                    returnUrl = returnUrl,
                    providerId = providerId,
                    hint = hint,
                    acrValues = request.queryParameters["acr_values"]?.splitToNonEmpty().orEmpty(),
                    forceReauth = request.queryParameters["force_reauth"].toBoolean(),
                    applicationId = pending.applicationId,
                ),
            )
        return if (result.isOk) {
            Ok(
                GenericHttpResponse(
                    statusCode = 302,
                    headers = mapOf("Location" to result.value.value, "Cache-Control" to "no-store"),
                    body = "",
                ),
            )
        } else {
            Err(IdkError.fromDTO(result.error))
        }
    }
}

private fun String.splitToNonEmpty(): List<String> = split(" ").map { it.trim() }.filter { it.isNotEmpty() }
