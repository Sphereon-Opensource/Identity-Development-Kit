package com.sphereon.oauth2.client.impl.authorization

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
import com.sphereon.oauth2.client.command.CreateAuthorizationRequestUrlCommand
import com.sphereon.oauth2.client.command.CreateAuthorizationRequestUrlOptions
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.CreatePkceCommand
import com.sphereon.oauth2.common.error.MetadataError
import com.sphereon.oauth2.client.model.AuthorizationRequestUrlResult
import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.common.model.PushedAuthorizationRequest
import com.sphereon.oauth2.common.model.PushedAuthorizationResponse
import com.sphereon.oauth2.client.util.buildUrl
import com.sphereon.oauth2.client.util.encodeQueryParameters
import com.sphereon.oauth2.client.validation.validatePushedAuthorizationResponse
import com.sphereon.oauth2.common.error.Oauth2Error
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationResult
import com.sphereon.oauth2.common.validation.toIdkResult
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of CreateAuthorizationRequestUrlCommand
 *
 * Creates authorization request URLs with support for:
 * - PKCE (RFC 7636)
 * - PAR - Pushed Authorization Requests (RFC 9126)
 */
@Inject
@SingleIn(SessionScope::class)
class CreateAuthorizationRequestUrlCommandImpl(
    execution: SessionExecution,
    private val createPkceCommand: CreatePkceCommand,
    private val applyClientAuthenticationCommand: ApplyClientAuthenticationCommand,
    private val httpClientFactory: HttpClientFactory
) : TypedServiceCommandAdapter<CreateAuthorizationRequestUrlOptions, AuthorizationRequestUrlResult>(
    commandId = CreateAuthorizationRequestUrlCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<CreateAuthorizationRequestUrlOptions>(),
    outputTypeToken = typeToken<AuthorizationRequestUrlResult>(),
), CreateAuthorizationRequestUrlCommand {

    override val commandId: String get() = CreateAuthorizationRequestUrlCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateAuthorizationRequestUrlOptions

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun doExecute(
        args: CreateAuthorizationRequestUrlOptions,
        applyDuring: (CreateAuthorizationRequestUrlOptions) -> CreateAuthorizationRequestUrlOptions
    ): IdkResult<AuthorizationRequestUrlResult, IdkError> {
        val applied = applyDuring(args)
        return createAuthorizationRequestUrlInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun createAuthorizationRequestUrlInternal(options: CreateAuthorizationRequestUrlOptions): IdkResult<AuthorizationRequestUrlResult, Oauth2Error> {
        val metadata = options.authorizationServerMetadata

        // Validate authorization_endpoint exists
        val authorizationEndpoint = metadata.authorizationEndpoint
            ?: return Err(
                MetadataError.InvalidUrl(
                    url = metadata.issuer,
                    reason = "Authorization server has no 'authorization_endpoint'"
                )
            )

        // Generate PKCE if supported
        val supportedMethods = metadata.codeChallengeMethodsSupported
        val pkceData: PkceData? = if (supportedMethods != null && supportedMethods.isNotEmpty()) {
            val allowedMethods = supportedMethods.mapNotNull { method ->
                when (method.uppercase()) {
                    "S256" -> PkceMethod.S256
                    "PLAIN" -> PkceMethod.PLAIN
                    else -> null
                }
            }

            if (allowedMethods.isNotEmpty()) {
                val result = createPkceCommand.execute(
                    CreatePkceArgs(
                        codeVerifier = options.pkceCodeVerifier,
                        allowedMethods = allowedMethods
                    )
                )
                if (result.isOk) {
                    result.value
                } else {
                    return Err(
                        Oauth2Error.ServerError(
                            reason = "Failed to generate PKCE: ${result.error.message.defaultMessage}"
                        )
                    )
                }
            } else {
                null
            }
        } else {
            null
        }

        // Build authorization request with PKCE
        val requestParams = buildRequestParameters(options, pkceData)

        // Check if PAR is required or supported
        val pushedAuthorizationRequestEndpoint = metadata.pushedAuthorizationRequestEndpoint
        val usePar = metadata.requirePushedAuthorizationRequests == true || pushedAuthorizationRequestEndpoint != null

        return if (usePar) {
            // Use PAR flow
            if (pushedAuthorizationRequestEndpoint == null) {
                return Err(
                    Oauth2Error.ServerError(
                        reason = "Authorization server requires PAR but 'pushed_authorization_request_endpoint' is missing"
                    )
                )
            }

            pushAuthorizationRequest(
                endpoint = pushedAuthorizationRequestEndpoint,
                requestParams = requestParams,
                authorizationEndpoint = authorizationEndpoint,
                clientId = options.authorizationRequest.clientId,
                clientAuthentication = options.clientAuthentication,
                pkceData = pkceData
            )
        } else {
            // Standard authorization request (no PAR)
            val authorizationUrl = buildUrl(authorizationEndpoint, requestParams)
            Ok(
                AuthorizationRequestUrlResult(
                    authorizationRequestUrl = authorizationUrl,
                    pkceData = pkceData,
                    dpopNonce = null // TODO: Phase 3 - DPoP support
                )
            )
        }
    }

    private fun buildRequestParameters(
        options: CreateAuthorizationRequestUrlOptions,
        pkceData: PkceData?
    ): Map<String, String?> {
        val request = options.authorizationRequest

        return mapOf(
            "response_type" to request.responseType,
            "client_id" to request.clientId,
            "redirect_uri" to request.redirectUri,
            "scope" to request.scope,
            "state" to request.state,
            "resource" to request.resource,
            "issuer_state" to request.issuerState,
            "nonce" to request.nonce,
            "response_mode" to request.responseMode,
            "request_uri" to request.requestUri,
            "request" to request.request,
            "code_challenge" to pkceData?.codeChallenge,
            "code_challenge_method" to pkceData?.codeChallengeMethod?.value,
            // OIDC parameters (OpenID Connect Core 1.0 Section 3.1.2.1)
            "prompt" to request.prompt,
            "login_hint" to request.loginHint,
            "max_age" to request.maxAge?.toString(),
            "ui_locales" to request.uiLocales,
            "id_token_hint" to request.idTokenHint,
            "acr_values" to request.acrValues,
            "display" to request.display
            // TODO: Phase 3 - Add dpop_jkt if DPoP is used
        )
    }

    private suspend fun pushAuthorizationRequest(
        endpoint: String,
        requestParams: Map<String, String?>,
        authorizationEndpoint: String,
        clientId: String,
        clientAuthentication: ClientAuthenticationConfig?,
        pkceData: PkceData?
    ): IdkResult<AuthorizationRequestUrlResult, Oauth2Error> {
        val httpClient = httpClientFactory.createClient(
            HttpClientOptions(
                engine = null,
                enableContentNegotiation = true
            )
        )

        return try {
            // Apply client authentication if provided
            val authResult = if (clientAuthentication != null) {
                val result = applyClientAuthenticationCommand.execute(
                    ApplyClientAuthenticationArgs(config = clientAuthentication, tokenEndpoint = endpoint)
                )
                if (result.isOk) {
                    result.value
                } else {
                    return Err(
                        Oauth2Error.ServerError(reason = "Client authentication failed: ${result.error.message.defaultMessage ?: result.error.code}")
                    )
                }
            } else {
                ClientAuthenticationResult(
                    headers = emptyMap(),
                    bodyParameters = emptyMap()
                )
            }

            // Merge authentication body parameters with request parameters
            val mergedParams = requestParams + authResult.bodyParameters.filterValues { it != null }
            val formBody = encodeQueryParameters(mergedParams)

            // TODO: Phase 3 - Add DPoP headers

            val response: HttpResponse = httpClient.post(endpoint) {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                // Apply authentication headers
                authResult.headers.forEach { (key, value) ->
                    if (value != null) {
                        header(key, value)
                    }
                }
                setBody(formBody)
            }

            if (!response.status.isSuccess()) {
                val errorBody = try { response.body<String>() } catch (_: Exception) { "" }
                return Err(
                    MetadataError.FetchFailed(
                        url = endpoint,
                        reason = "PAR request failed with status ${response.status.value}: ${response.status.description}. Response: $errorBody"
                    )
                )
            }

            val bodyText = response.body<String>()
            val parResponse = try {
                json.decodeFromString<PushedAuthorizationResponse>(bodyText)
            } catch (e: Exception) {
                return Err(
                    MetadataError.FetchFailed(
                        url = endpoint,
                        reason = "Failed to parse PAR response: ${e.message}",
                        exception = e
                    )
                )
            }

            // Validate PAR response
            val validationResult = validatePushedAuthorizationResponse(parResponse)
                .toIdkResult { errors ->
                    MetadataError.ValidationFailed(
                        url = endpoint,
                        details = errors
                    )
                }

            if (validationResult.isErr) {
                return Err(validationResult.error)
            }

            // Build authorization URL with request_uri
            val parRequest = PushedAuthorizationRequest(
                requestUri = parResponse.requestUri,
                clientId = clientId
            )

            val parParams = mapOf(
                "request_uri" to parRequest.requestUri,
                "client_id" to parRequest.clientId
            )

            val authorizationUrl = buildUrl(authorizationEndpoint, parParams)

            Ok(
                AuthorizationRequestUrlResult(
                    authorizationRequestUrl = authorizationUrl,
                    pkceData = pkceData,
                    dpopNonce = null // TODO: Phase 3 - Extract DPoP nonce from response headers
                )
            )
        } catch (e: Exception) {
            Err(
                MetadataError.FetchFailed(
                    url = endpoint,
                    reason = "Network error during PAR request: ${e.message}",
                    exception = e
                )
            )
        }
    }
}
