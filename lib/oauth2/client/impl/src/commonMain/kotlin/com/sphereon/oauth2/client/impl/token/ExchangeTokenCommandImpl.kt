package com.sphereon.oauth2.client.impl.token

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
import com.sphereon.oauth2.client.command.ExchangeTokenArgs
import com.sphereon.oauth2.client.command.ExchangeTokenCommand
import com.sphereon.oauth2.client.util.isSecureUrl
import com.sphereon.oauth2.common.model.TokenErrorResponse
import com.sphereon.oauth2.common.model.TokenRequest
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.client.validation.validateTokenRequest
import com.sphereon.oauth2.common.error.Oauth2Error
import io.konform.validation.Invalid
import io.konform.validation.Valid
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Inject
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
    private val httpClientFactory: HttpClientFactory
) : TypedServiceCommandAdapter<ExchangeTokenArgs, TokenResponse>(
    commandId = ExchangeTokenCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ExchangeTokenArgs>(),
    outputTypeToken = typeToken<TokenResponse>(),
), ExchangeTokenCommand {

    override val commandId: String get() = ExchangeTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ExchangeTokenArgs

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun doExecute(
        args: ExchangeTokenArgs,
        applyDuring: (ExchangeTokenArgs) -> ExchangeTokenArgs
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
        request: TokenRequest
    ): IdkResult<TokenResponse, Oauth2Error> {
        // Validate token endpoint URL (HTTPS required, HTTP allowed for localhost)
        if (!isSecureUrl(tokenEndpoint)) {
            return Err(
                Oauth2Error.ValidationFailed(
                    failureMessage = "Token endpoint must be an HTTPS URL (HTTP only allowed for localhost)",
                    validationErrors = listOf("token_endpoint must start with https:// (HTTP only allowed for localhost)")
                )
            )
        }

        // Validate token request
        when (val validationResult = validateTokenRequest(request)) {
            is Invalid -> {
                val errors = validationResult.errors.map { error ->
                    "${error.dataPath}: ${error.message}"
                }
                return Err(
                    Oauth2Error.ValidationFailed(
                        failureMessage = "Token request validation failed",
                        validationErrors = errors
                    )
                )
            }
            is Valid -> {
                // Validation passed, continue
            }
        }

        // Build form-encoded request body
        val formParameters = buildFormParameters(request)
        val formBody = formParameters.joinToString("&") { (key, value) ->
            "$key=${urlEncode(value)}"
        }

        // Make HTTP POST request to token endpoint
        return try {
            val httpClient = httpClientFactory.createClient(
                HttpClientOptions(
                    engine = null,
                    enableContentNegotiation = true
                )
            )

            val response: HttpResponse = httpClient.post(tokenEndpoint) {
                contentType(ContentType.Application.FormUrlEncoded)
                headers {
                    append("Accept", "application/json")

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
                response.status == HttpStatusCode.OK || response.status.value in 200..299 -> {
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
                    val errorResponse = try {
                        json.decodeFromString<TokenErrorResponse>(responseBody)
                    } catch (e: Exception) {
                        TokenErrorResponse(
                            error = "invalid_token",
                            errorDescription = "Failed to parse error response: ${e.message}"
                        )
                    }

                    // If error is use_dpop_nonce and DPoP-Nonce header is present, return special error
                    if (errorResponse.error == "use_dpop_nonce" && dpopNonce != null) {
                        Err(
                            Oauth2Error.DpopNonceRequired(
                                nonce = dpopNonce
                            )
                        )
                    } else {
                        Err(
                            Oauth2Error.ErrorResponse(
                                error = errorResponse.error,
                                errorDescription = errorResponse.errorDescription,
                                errorUri = errorResponse.errorUri
                            )
                        )
                    }
                }
                else -> {
                    // Other error response
                    val responseBody = response.body<String>()
                    val errorResponse = try {
                        json.decodeFromString<TokenErrorResponse>(responseBody)
                    } catch (e: Exception) {
                        TokenErrorResponse(
                            error = "unknown_error",
                            errorDescription = "Failed to parse error response: ${e.message}"
                        )
                    }

                    Err(
                        Oauth2Error.ErrorResponse(
                            error = errorResponse.error,
                            errorDescription = errorResponse.errorDescription,
                            errorUri = errorResponse.errorUri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Err(
                Oauth2Error.FetchFailed(
                    failureMessage = "Failed to exchange token at $tokenEndpoint",
                    cause = e
                )
            )
        }
    }

    /**
     * Builds form parameters from token request
     *
     * Returns List<Pair> instead of Map to support repeated parameter names
     * (e.g., resource=A&resource=B per RFC 8693/RFC 8707).
     */
    private fun buildFormParameters(request: TokenRequest): List<Pair<String, String>> {
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

        // Client authentication
        request.clientId?.let { parameters.add("client_id" to it) }
        request.clientSecret?.let { parameters.add("client_secret" to it) }
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
    private fun urlEncode(value: String): String {
        return value.encodeToByteArray().joinToString("") { byte ->
            val char = byte.toInt().toChar()
            when {
                char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' ||
                    char == '-' || char == '_' || char == '.' || char == '~' -> char.toString()
                else -> "%${byte.toUByte().toString(16).uppercase().padStart(2, '0')}"
            }
        }
    }
}
