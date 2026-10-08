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
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationCommand
import com.sphereon.oauth2.server.authorization.command.federation.ReconciliationAuthorizeHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.provider.FlowContext
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRoutePlanner
import com.sphereon.oauth2.server.authorization.storage.PendingAuthorizationSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock

/**
 * HTTP shell over [InitiateProviderAuthenticationCommand] for the reconciliation (IDV) entry
 * point. Requires a verified pending OAuth `session_id`, `oid4vp_session` and `provider`,
 * and stamps [FlowContext] with `flow="reconciliation"` so the callback dispatcher routes the
 * outcome to the auth-bridge rather than issuing a local authorization code.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(ReconciliationAuthorizeHttpEndpointCommand.COMMAND_ID)
class ReconciliationAuthorizeHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val initiateProviderAuthenticationCommand: InitiateProviderAuthenticationCommand,
    private val secureRandom: SecureRandom,
    private val configProvider: OAuth2ServersConfigProvider,
    private val baseUrlResolver: FederationBaseUrlResolver,
    private val pendingAuthorizationSessionStore: PendingAuthorizationSessionStore,
    private val authenticationRoutePlanner: AuthenticationRoutePlanner,
) : HttpEndpointCommandAdapter(
        id = ReconciliationAuthorizeHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ReconciliationAuthorizeHttpEndpointCommand.ENDPOINT,
    ),
    ReconciliationAuthorizeHttpEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val oid4vpSessionId =
            request.queryParameters["oid4vp_session"]
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing 'oid4vp_session' parameter"))
        val providerId =
            request.queryParameters["provider"]
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing 'provider' parameter"))

        val baseUrl = baseUrlResolver.resolveBaseUrl(request, configProvider)
        val sessionId =
            request.queryParameters["session_id"]?.takeIf(String::isNotBlank)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing 'session_id' parameter"))
        val pendingResult = pendingAuthorizationSessionStore.findById(sessionId)
        if (pendingResult.isErr) return Err(pendingResult.error)
        val pending = pendingResult.value
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unknown pending authorization session"))
        if (pending.expiresAt <= Clock.System.now()) {
            return Err(IdkError.INVALID_STATE(message = "Pending authorization session has expired"))
        }
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

        val result =
            initiateProviderAuthenticationCommand.execute(
                InitiateProviderAuthenticationArgs(
                    sessionId = sessionId,
                    returnUrl = baseUrl,
                    providerId = providerId,
                    applicationId = pending.applicationId,
                    flowContext =
                        FlowContext(
                            flow = "reconciliation",
                            oid4vpSessionId = oid4vpSessionId,
                        ),
                    acrValues = request.queryParameters["acr_values"]?.splitToNonEmpty().orEmpty(),
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
