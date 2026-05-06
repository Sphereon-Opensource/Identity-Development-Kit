/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.client.impl.token

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.client.command.ExchangeTokenArgs
import com.sphereon.oauth2.client.command.ExchangeTokenCommand
import com.sphereon.oauth2.client.util.isSecureUrl
import com.sphereon.oauth2.client.validation.validateTokenRequest
import com.sphereon.oauth2.common.error.Oauth2Error
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.TokenErrorResponse
import com.sphereon.oauth2.common.model.TokenRequest
import com.sphereon.oauth2.common.model.TokenResponse
import dev.zacsweers.metro.Inject
import io.konform.validation.Invalid
import io.konform.validation.Valid
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of ExchangeTokenCommand for OAuth 2.0 token endpoint operations
 *
 * Supports multiple grant types:
 * - authorization_code (RFC 6749 Section 4.1.3)
 * - refresh_token (RFC 6749 Section 6)
 * - client_credentials (RFC 6749 Section 4.4.2)
 * - pre-authorized_code (OpenID4VCI)
 *
 * Uses application/x-www-form-urlencoded encoding for token requests (RFC 6749 Section 4.1.3)
 *
 * @property httpClientFactory Factory for creating HTTP clients
 */
@Inject
@OptIn(ExperimentalObjCName::class)
@ObjCName("ExchangeTokenCommandImpl", exact = true)
class ExchangeTokenCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
) : TypedServiceCommandAdapter<ExchangeTokenArgs, TokenResponse, IdkError>(
        commandId = ExchangeTokenCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ExchangeTokenArgs>(),
        outputTypeToken = typeToken<TokenResponse>(),
    ),
    ExchangeTokenCommand {
    override val commandId: String get() = ExchangeTokenCommand.COMMAND_ID

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    override suspend fun supports(args: Any): Boolean = args is ExchangeTokenArgs

    override suspend fun doExecute(
        args: ExchangeTokenArgs,
        applyDuring: (ExchangeTokenArgs) -> ExchangeTokenArgs,
    ): IdkResult<TokenResponse, IdkError> {
        val applied = applyDuring(args)
        return exchangeTokenInternal(applied.tokenEndpoint, applied.request).mapError { IdkError.fromDTO(it) }
    }

    /**
     * Exchanges a token at the token endpoint
     *
     * @param tokenEndpoint The token endpoint URL (HTTPS URL)
     * @param request The token request with grant-specific parameters
     * @return IdkResult containing token response or error
     */
    private suspend fun exchangeTokenInternal(
        tokenEndpoint: String,
        request: TokenRequest,
    ): IdkResult<TokenResponse, Oauth2Error> {
        // Validate token endpoint URL (HTTPS required, HTTP allowed for localhost)
        if (!isSecureUrl(tokenEndpoint)) {
            return Err(
                Oauth2Error.ValidationFailed(
                    failureMessage = "Token endpoint must be an HTTPS URL (HTTP only allowed for localhost)",
                    validationErrors = listOf("token_endpoint must start with https:// (HTTP only allowed for localhost)"),
                ),
            )
        }

        // Validate token request
        when (val validationResult = validateTokenRequest(request)) {
            is Invalid -> {
                val errors =
                    validationResult.errors.map { error ->
                        "${error.dataPath}: ${error.message}"
                    }
                return Err(
                    Oauth2Error.ValidationFailed(
                        failureMessage = "Token request validation failed",
                        validationErrors = errors,
                    ),
                )
            }

            is Valid -> {
                // Validation passed, continue
            }
        }

        // Resolve client authentication method. Null defaults to client_secret_post,
        // preserving legacy body-only credential placement for callers that did not opt in.
        val authMethod = request.tokenEndpointAuthMethod ?: ClientAuthenticationMethod.CLIENT_SECRET_POST

        // Build form-encoded request body, omitting body credentials when Basic auth is in use.
        val formParameters = buildFormParameters(request, authMethod)
        val formBody =
            formParameters.joinToString("&") { (key, value) ->
                "$key=${urlEncode(value)}"
            }

        val basicAuthHeader = buildBasicAuthHeader(authMethod, request)

        // Make HTTP POST request to token endpoint
        return try {
            val httpClient =
                httpClientFactory.createClient(
                    HttpClientOptions(
                        engine = null,
                        enableContentNegotiation = true,
                    ),
                )

            val response: HttpResponse =
                httpClient.post(tokenEndpoint) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    headers {
                        append("Accept", "application/json")

                        basicAuthHeader?.let { append("Authorization", it) }

                        // Add DPoP header if DPoP proof is provided
                        request.dpop?.let { dpopProof ->
                            append("DPoP", dpopProof)
                        }
                    }

                    setBody(formBody)
                }

            // Extract DPoP-Nonce header if present (can be in both success and error responses)
            val dpopNonce = response.headers["DPoP-Nonce"]

            when {
                response.status.isSuccess() -> {
                    // Success response
                    val responseBody = response.body<String>()
                    var tokenResponse = json.decodeFromString<TokenResponse>(responseBody)

                    // Update token response with DPoP nonce from header if not already in body
                    if (dpopNonce != null && tokenResponse.dpopNonce == null) {
                        tokenResponse = tokenResponse.copy(dpopNonce = dpopNonce)
                    }

                    Ok(tokenResponse)
                }

                response.status == HttpStatusCode.Unauthorized -> {
                    // 401 Unauthorized - check for DPoP nonce error
                    val responseBody = response.body<String>()
                    val errorResponse =
                        try {
                            json.decodeFromString<TokenErrorResponse>(responseBody)
                        } catch (e: Exception) {
                            TokenErrorResponse(
                                error = "invalid_token",
                                errorDescription = "Failed to parse error response: ${e.message}",
                            )
                        }

                    // If error is use_dpop_nonce and DPoP-Nonce header is present, return special error
                    if (errorResponse.error == "use_dpop_nonce" && dpopNonce != null) {
                        Err(
                            Oauth2Error.DpopNonceRequired(
                                nonce = dpopNonce,
                            ),
                        )
                    } else {
                        Err(
                            Oauth2Error.ErrorResponse(
                                error = errorResponse.error,
                                errorDescription = errorResponse.errorDescription,
                                errorUri = errorResponse.errorUri,
                            ),
                        )
                    }
                }

                else -> {
                    // Other error response
                    val responseBody = response.body<String>()
                    val errorResponse =
                        try {
                            json.decodeFromString<TokenErrorResponse>(responseBody)
                        } catch (e: Exception) {
                            TokenErrorResponse(
                                error = "unknown_error",
                                errorDescription = "Failed to parse error response: ${e.message}",
                            )
                        }

                    Err(
                        Oauth2Error.ErrorResponse(
                            error = errorResponse.error,
                            errorDescription = errorResponse.errorDescription,
                            errorUri = errorResponse.errorUri,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Err(
                Oauth2Error.FetchFailed(
                    failureMessage = "Failed to exchange token at $tokenEndpoint",
                    cause = e,
                ),
            )
        }
    }

    /**
     * Builds form parameters from token request.
     *
     * Returns List<Pair> instead of Map to support repeated parameter names
     * (e.g., resource=A&resource=B per RFC 8693/RFC 8707).
     *
     * When [authMethod] is [ClientAuthenticationMethod.CLIENT_SECRET_BASIC], the client_id and
     * client_secret are intentionally omitted from the body: they ride in the Authorization
     * header instead, per RFC 6749 Section 2.3.1 and OIDF
     * `OIDCCValidateClientAuthenticationWithClientSecretBasic`.
     */
    private fun buildFormParameters(
        request: TokenRequest,
        authMethod: ClientAuthenticationMethod,
    ): List<Pair<String, String>> {
        val parameters = mutableListOf<Pair<String, String>>()

        // Always required
        parameters.add("grant_type" to request.grantType)

        // Grant-specific parameters
        request.code?.let { parameters.add("code" to it) }
        request.redirectUri?.let { parameters.add("redirect_uri" to it) }
        request.codeVerifier?.let { parameters.add("code_verifier" to it) }
        request.refreshToken?.let { parameters.add("refresh_token" to it) }
        request.preAuthorizedCode?.let { parameters.add("pre-authorized_code" to it) }
        request.txCode?.let { parameters.add("tx_code" to it) }

        // Client authentication. With CLIENT_SECRET_BASIC the credentials live in the
        // Authorization header (RFC 6749 Section 2.3.1), omit them from the body. With NONE
        // only the client_id is sent. The JWT-based methods carry client_assertion in the body.
        when (authMethod) {
            ClientAuthenticationMethod.CLIENT_SECRET_BASIC -> {
                // Body credentials intentionally omitted.
            }

            ClientAuthenticationMethod.NONE -> {
                request.clientId?.let { parameters.add("client_id" to it) }
            }

            else -> {
                request.clientId?.let { parameters.add("client_id" to it) }
                request.clientSecret?.let { parameters.add("client_secret" to it) }
            }
        }
        request.clientAssertionType?.let { parameters.add("client_assertion_type" to it) }
        request.clientAssertion?.let { parameters.add("client_assertion" to it) }

        // Common parameters
        request.scope?.let { parameters.add("scope" to it) }

        // Multi-value parameters (RFC 8707 + RFC 8693: repeated form parameters)
        request.resource.forEach { parameters.add("resource" to it) }
        request.audience.forEach { parameters.add("audience" to it) }

        // Token Exchange (RFC 8693)
        request.subjectToken?.let { parameters.add("subject_token" to it) }
        request.subjectTokenType?.let { parameters.add("subject_token_type" to it) }
        request.actorToken?.let { parameters.add("actor_token" to it) }
        request.actorTokenType?.let { parameters.add("actor_token_type" to it) }
        request.requestedTokenType?.let { parameters.add("requested_token_type" to it) }

        // OpenID4VCI extensions
        request.authorizationDetails?.let {
            parameters.add("authorization_details" to it.toString())
        }

        return parameters
    }

    /**
     * URL-encodes a string for application/x-www-form-urlencoded format
     *
     * Encodes all characters except unreserved characters: A-Z, a-z, 0-9, -, _, ., ~
     */
    private fun urlEncode(value: String): String =
        value.encodeToByteArray().joinToString("") { byte ->
            val char = byte.toInt().toChar()
            when {
                char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' ||
                    char == '-' || char == '_' || char == '.' || char == '~' -> char.toString()

                else -> "%${byte.toUByte().toString(HEX_RADIX).uppercase().padStart(HEX_PAD_LENGTH, '0')}"
            }
        }

    /**
     * Builds the `Authorization: Basic` header value for the token endpoint when the request
     * is configured for [ClientAuthenticationMethod.CLIENT_SECRET_BASIC].
     *
     * Per RFC 6749 Section 2.3.1 the client_id and client_secret are first form-urlencoded
     * (`application/x-www-form-urlencoded`) and joined with a single colon, then base64-encoded
     * (standard alphabet, with padding). Returns null for any other auth method, or when the
     * request lacks the credentials needed to build the header.
     */
    private fun buildBasicAuthHeader(
        authMethod: ClientAuthenticationMethod,
        request: TokenRequest,
    ): String? {
        if (authMethod != ClientAuthenticationMethod.CLIENT_SECRET_BASIC) {
            return null
        }
        val clientId = request.clientId ?: return null
        val clientSecret = request.clientSecret ?: return null
        val credentials = "${urlEncode(clientId)}:${urlEncode(clientSecret)}"
        val encoded = credentials.encodeToByteArray().encodeToBase64(urlSafe = false)
        return "Basic $encoded"
    }

    companion object {
        private const val HEX_RADIX = 16
        private const val HEX_PAD_LENGTH = 2
    }
}
