package com.sphereon.oauth2.client.impl.introspection

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationArgs
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationCommand
import com.sphereon.oauth2.common.command.IntrospectTokenArgs
import com.sphereon.oauth2.common.command.IntrospectTokenCommand
import com.sphereon.oauth2.common.error.IntrospectionError
import com.sphereon.oauth2.client.validation.validateTokenIntrospectionRequest
import com.sphereon.oauth2.client.validation.validateTokenIntrospectionResponse
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.TokenIntrospectionRequest
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.common.validation.toIdkResult
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import com.sphereon.di.session.SessionScope

/**
 * Implementation of token introspection command.
 * RFC 7662 - OAuth 2.0 Token Introspection
 *
 * Sends introspection requests to the authorization server to determine
 * token metadata and validity.
 */
@Inject
@SingleIn(SessionScope::class)
class ClientIntrospectTokenCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
    private val applyClientAuthenticationCommand: ApplyClientAuthenticationCommand,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : TypedServiceCommandAdapter<IntrospectTokenArgs, TokenIntrospectionResponse>(
    commandId = IntrospectTokenCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<IntrospectTokenArgs>(),
    outputTypeToken = typeToken<TokenIntrospectionResponse>(),
), IntrospectTokenCommand {

    override val commandId: String get() = IntrospectTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is IntrospectTokenArgs

    override suspend fun doExecute(
        args: IntrospectTokenArgs,
        applyDuring: (IntrospectTokenArgs) -> IntrospectTokenArgs
    ): IdkResult<TokenIntrospectionResponse, IdkError> {
        val applied = applyDuring(args)
        return introspectTokenInternal(
            applied.authorizationServerMetadata,
            applied.token,
            applied.clientAuthentication,
            applied.tokenTypeHint,
            applied.additionalParameters
        ).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun introspectTokenInternal(
        authorizationServerMetadata: AuthorizationServerMetadata,
        token: String,
        clientAuthentication: ClientAuthenticationConfig,
        tokenTypeHint: String?,
        additionalParameters: Map<String, String>
    ): IdkResult<TokenIntrospectionResponse, IntrospectionError> {
        // Check that introspection endpoint is configured
        val introspectionEndpoint = authorizationServerMetadata.introspectionEndpoint
            ?: return Err(IntrospectionError.EndpointNotConfigured())

        // Build introspection request
        val introspectionRequest = TokenIntrospectionRequest(
            token = token,
            tokenTypeHint = tokenTypeHint,
            additionalParameters = additionalParameters
        )

        // Validate request
        val requestValidation = validateTokenIntrospectionRequest(introspectionRequest)
            .toIdkResult { errors -> IntrospectionError.ResponseValidationFailed(errors) }

        if (requestValidation.isErr) {
            return Err(requestValidation.error)
        }

        return try {
            // Apply client authentication (need endpoint URL for JWT audience)
            val authResult = applyClientAuthenticationCommand.execute(
                ApplyClientAuthenticationArgs(clientAuthentication, introspectionEndpoint)
            )

            if (authResult.isErr) {
                return Err(
                    IntrospectionError.RequestFailed(
                        reason = "Client authentication failed: ${authResult.error.message.defaultMessage}",
                        exception = null
                    )
                )
            }

            val authData = authResult.value

            // Merge auth body parameters with introspection parameters
            val allBodyParameters = buildIntrospectionBody(introspectionRequest) + authData.bodyParameters

            // Create HTTP client
            val httpClient = httpClientFactory.createClient(
                HttpClientOptions(
                    engine = null,
                    enableContentNegotiation = true
                )
            )

            // Send introspection request
            val response: HttpResponse = httpClient.post(introspectionEndpoint) {
                contentType(ContentType.Application.FormUrlEncoded)
                headers {
                    authData.headers.forEach { (key, value) ->
                        append(key, value)
                    }
                }
                setBody(encodeFormData(allBodyParameters))
            }

            // Check response status
            if (response.status != HttpStatusCode.OK) {
                return Err(
                    IntrospectionError.RequestFailed(
                        reason = "Introspection endpoint returned status ${response.status.value}",
                        exception = null
                    )
                )
            }

            // Parse response
            val introspectionResponse = json.decodeFromString<TokenIntrospectionResponse>(
                response.body<String>()
            )

            // Validate response
            val validationResult = validateTokenIntrospectionResponse(introspectionResponse)
                .toIdkResult { errors -> IntrospectionError.ResponseValidationFailed(errors) }

            if (validationResult.isErr) {
                return validationResult
            }

            Ok(introspectionResponse)

        } catch (e: Exception) {
            Err(
                IntrospectionError.RequestFailed(
                    reason = "Failed to introspect token: ${e.message}",
                    exception = e
                )
            )
        }
    }

    /**
     * Builds introspection request body parameters.
     */
    private fun buildIntrospectionBody(request: TokenIntrospectionRequest): Map<String, String> {
        val body = mutableMapOf<String, String>()
        body["token"] = request.token
        request.tokenTypeHint?.let { body["token_type_hint"] = it }
        body.putAll(request.additionalParameters)
        return body
    }

    /**
     * Encodes form data as application/x-www-form-urlencoded.
     */
    private fun encodeFormData(parameters: Map<String, String>): String {
        return parameters.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
    }

    /**
     * URL-encodes a string.
     * Uses basic encoding for common characters.
     */
    private fun urlEncode(value: String): String {
        return value.replace(" ", "+")
            .replace("!", "%21")
            .replace("\"", "%22")
            .replace("#", "%23")
            .replace("$", "%24")
            .replace("%", "%25")
            .replace("&", "%26")
            .replace("'", "%27")
            .replace("(", "%28")
            .replace(")", "%29")
            .replace("*", "%2A")
            .replace("+", "%2B")
            .replace(",", "%2C")
            .replace("/", "%2F")
            .replace(":", "%3A")
            .replace(";", "%3B")
            .replace("=", "%3D")
            .replace("?", "%3F")
            .replace("@", "%40")
            .replace("[", "%5B")
            .replace("]", "%5D")
    }
}
