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

package com.sphereon.openid.oid4vci.holder.impl

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
import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.holder.ExchangeAuthorizationCodeArgs
import com.sphereon.openid.oid4vci.holder.ExchangeAuthorizationCodeCommand
import com.sphereon.openid.oid4vci.holder.TokenResponseWithContext
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Exchanges an authorization code for an access token using the authorization_code grant type.
 *
 * Per OID4VCI 1.0 Section 8 / RFC 6749 Section 4.1.3: HTTP POST (form-encoded) to the token endpoint.
 *
 * Required parameters:
 * - grant_type = authorization_code
 * - code = the authorization code from the redirect
 * - code_verifier = the PKCE code verifier
 * - redirect_uri = the redirect URI used in the authorization request
 *
 * Optional parameters:
 * - client_id = OAuth 2.0 client identifier
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ExchangeAuthorizationCodeCommand>())
class ExchangeAuthorizationCodeCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
) : TypedServiceCommandAdapter<ExchangeAuthorizationCodeArgs, TokenResponseWithContext, IdkError>(
        commandId = ExchangeAuthorizationCodeCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ExchangeAuthorizationCodeArgs>(),
        outputTypeToken = typeToken<TokenResponseWithContext>(),
    ),
    ExchangeAuthorizationCodeCommand {
    override val commandId: String get() = ExchangeAuthorizationCodeCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ExchangeAuthorizationCodeArgs

    override suspend fun doExecute(
        args: ExchangeAuthorizationCodeArgs,
        applyDuring: (ExchangeAuthorizationCodeArgs) -> ExchangeAuthorizationCodeArgs,
    ): IdkResult<TokenResponseWithContext, IdkError> {
        val applied = applyDuring(args)

        log.debug("Exchanging authorization code at: ${applied.tokenEndpoint}")

        val httpClient =
            try {
                httpClientFactory.createClient(HttpClientOptions.createDefault())
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to create HTTP client: ${expected.message}", throwable = expected))
            }

        return try {
            val formParameters =
                Parameters.build {
                    append("grant_type", AUTHORIZATION_CODE_GRANT_TYPE)
                    append("code", applied.code)
                    append("code_verifier", applied.codeVerifier)
                    append("redirect_uri", applied.redirectUri)
                    applied.clientId?.let { append("client_id", it) }
                }

            val response =
                httpClient.submitForm(
                    url = applied.tokenEndpoint,
                    formParameters = formParameters,
                )

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                return try {
                    val errorJson = Oid4vciJson.lenient.parseToJsonElement(errorBody).jsonObject
                    val errorCode = errorJson["error"]?.jsonPrimitive?.contentOrNull ?: "unknown_error"
                    val errorDesc = errorJson["error_description"]?.jsonPrimitive?.contentOrNull
                    Err(IdkError.fromString(message = "Token exchange failed: $errorCode${errorDesc?.let { " — $it" } ?: ""}"))
                } catch (expected: Exception) {
                    log.debug("Failed to parse token error response JSON: ${expected.message}")
                    Err(IdkError.fromString(message = "Token exchange failed: HTTP ${response.status.value} — $errorBody"))
                }
            }

            val body = response.bodyAsText()
            val tokenResponse =
                try {
                    Oid4vciJson.lenient.decodeFromString(TokenResponseWithContext.serializer(), body)
                } catch (expected: Exception) {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Failed to parse token response JSON: ${expected.message}",
                            throwable = expected,
                        ),
                    )
                }

            log.debug("Successfully obtained access token from: ${applied.tokenEndpoint}")
            Ok(tokenResponse)
        } catch (expected: Exception) {
            Err(
                IdkError.fromString(
                    message = "Network error during authorization code exchange at ${applied.tokenEndpoint}: ${expected.message}",
                    code = "TOKEN_NETWORK_ERROR",
                    exception = expected,
                ),
            )
        } finally {
            httpClient.close()
        }
    }

    companion object {
        const val AUTHORIZATION_CODE_GRANT_TYPE = "authorization_code"
    }
}
