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
 */

package com.sphereon.oauth2.client.impl.revocation

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationArgs
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationCommand
import com.sphereon.oauth2.common.command.ClientRevokeTokenArgs
import com.sphereon.oauth2.common.command.ClientRevokeTokenCommand
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of client-side token revocation command.
 * RFC 7009 - OAuth 2.0 Token Revocation
 *
 * Sends revocation requests to an external authorization server
 * to invalidate a previously obtained token.
 *
 * Per RFC 7009 Section 2:
 * - The AS responds with HTTP 200 regardless of outcome
 * - Invalid or unknown tokens do not cause an error
 * - The client MUST authenticate with the AS
 */
@Inject
@SingleIn(SessionScope::class)
class ClientRevokeTokenCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
    private val applyClientAuthenticationCommand: ApplyClientAuthenticationCommand
) : TypedServiceCommandAdapter<ClientRevokeTokenArgs, Unit>(
    commandId = ClientRevokeTokenCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ClientRevokeTokenArgs>(),
    outputTypeToken = typeToken<Unit>(),
), ClientRevokeTokenCommand {

    override val commandId: String get() = ClientRevokeTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ClientRevokeTokenArgs

    override suspend fun doExecute(
        args: ClientRevokeTokenArgs,
        applyDuring: (ClientRevokeTokenArgs) -> ClientRevokeTokenArgs
    ): IdkResult<Unit, IdkError> {
        val applied = applyDuring(args)
        return revokeTokenInternal(
            token = applied.token,
            clientAuthentication = applied.clientAuthentication,
            tokenTypeHint = applied.tokenTypeHint,
            revocationEndpoint = applied.revocationEndpointOverride
                ?: applied.authorizationServerMetadata?.revocationEndpoint
        )
    }

    private suspend fun revokeTokenInternal(
        token: String,
        clientAuthentication: com.sphereon.oauth2.common.model.ClientAuthenticationConfig,
        tokenTypeHint: String?,
        revocationEndpoint: String?
    ): IdkResult<Unit, IdkError> {
        if (revocationEndpoint == null) {
            return Err(IdkError(
                code = "revocation_endpoint_not_configured",
                message = IdkError.Message(
                    i18nKey = "oauth2.client.error.revocation_endpoint_not_configured",
                    defaultMessage = "Revocation endpoint not found in server metadata and no override provided"
                )
            ))
        }

        return try {
            // Apply client authentication
            val authResult = applyClientAuthenticationCommand.execute(
                ApplyClientAuthenticationArgs(clientAuthentication, revocationEndpoint)
            )

            if (authResult.isErr) {
                return Err(IdkError(
                    code = "client_authentication_failed",
                    message = IdkError.Message(
                        i18nKey = "oauth2.client.error.client_auth_failed",
                        defaultMessage = "Client authentication failed: ${authResult.error.message.defaultMessage}"
                    )
                ))
            }

            val authData = authResult.value

            // Build revocation body
            val bodyParameters = buildMap<String, String> {
                put("token", token)
                tokenTypeHint?.let { put("token_type_hint", it) }
                putAll(authData.bodyParameters)
            }

            // Create HTTP client
            val httpClient = httpClientFactory.createClient(
                HttpClientOptions(
                    engine = null,
                    enableContentNegotiation = true
                )
            )

            // Send revocation request
            val response: HttpResponse = httpClient.post(revocationEndpoint) {
                contentType(ContentType.Application.FormUrlEncoded)
                headers {
                    authData.headers.forEach { (key, value) ->
                        append(key, value)
                    }
                }
                setBody(encodeFormData(bodyParameters))
            }

            // RFC 7009: 200 = success, regardless of whether the token was valid
            // Only client auth errors (401/403) are real errors
            when (response.status.value) {
                200 -> Ok(Unit)
                401, 403 -> Err(IdkError(
                    code = "unauthorized_client",
                    message = IdkError.Message(
                        i18nKey = "oauth2.client.error.revocation_unauthorized",
                        defaultMessage = "Client authentication failed at revocation endpoint (HTTP ${response.status.value})"
                    )
                ))
                else -> {
                    // Non-standard error, but still consider revocation done
                    // since some servers may return non-200 for invalid tokens
                    Ok(Unit)
                }
            }
        } catch (e: Exception) {
            Err(IdkError(
                code = "revocation_request_failed",
                message = IdkError.Message(
                    i18nKey = "oauth2.client.error.revocation_request_failed",
                    defaultMessage = "Failed to send revocation request: ${e.message}"
                ),
                exception = e
            ))
        }
    }

    private fun encodeFormData(parameters: Map<String, String>): String {
        return parameters.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
    }

    private fun urlEncode(value: String): String {
        return value.replace("%", "%25")
            .replace(" ", "+")
            .replace("&", "%26")
            .replace("=", "%3D")
            .replace("+", "%2B")
    }
}
