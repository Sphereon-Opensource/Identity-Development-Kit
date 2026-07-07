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

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.command.federation.FederationAuthorizeHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationCommand
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

private const val SESSION_TOKEN_BYTES = 16

/**
 * HTTP shell over [InitiateProviderAuthenticationCommand] for the federation login entry point.
 * Parses the `provider` (and optional `login_hint`) query params, resolves the base URL through
 * [FederationBaseUrlResolver], generates a local session id, and renders the upstream redirect.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FederationAuthorizeHttpEndpointCommand>())
class FederationAuthorizeHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val initiateProviderAuthenticationCommand: InitiateProviderAuthenticationCommand,
    private val secureRandom: SecureRandom,
    private val configProvider: OAuth2ServersConfigProvider,
    private val baseUrlResolver: FederationBaseUrlResolver,
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

        val baseUrl = baseUrlResolver.resolveBaseUrl(request, configProvider)
        val sessionId = "fed-" + secureRandom.newToken(lengthBytes = SESSION_TOKEN_BYTES, encoding = Encoding.HEX)

        val result =
            initiateProviderAuthenticationCommand.execute(
                InitiateProviderAuthenticationArgs(
                    sessionId = sessionId,
                    returnUrl = baseUrl,
                    providerId = providerId,
                    hint = hint,
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
