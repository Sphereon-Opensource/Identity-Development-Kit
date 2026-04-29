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

package com.sphereon.oauth2.client.impl.introspection

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
import com.sphereon.oauth2.client.validation.validateTokenIntrospectionRequest
import com.sphereon.oauth2.client.validation.validateTokenIntrospectionResponse
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationArgs
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationCommand
import com.sphereon.oauth2.common.command.IntrospectTokenArgs
import com.sphereon.oauth2.common.command.IntrospectTokenCommand
import com.sphereon.oauth2.common.error.IntrospectionError
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.TokenIntrospectionRequest
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.core.api.validation.toIdkResult
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
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
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TypedServiceCommandAdapter<IntrospectTokenArgs, TokenIntrospectionResponse>(
        commandId = IntrospectTokenCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<IntrospectTokenArgs>(),
        outputTypeToken = typeToken<TokenIntrospectionResponse>(),
    ),
    IntrospectTokenCommand {
    override val commandId: String get() = IntrospectTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is IntrospectTokenArgs

    override suspend fun doExecute(
        args: IntrospectTokenArgs,
        applyDuring: (IntrospectTokenArgs) -> IntrospectTokenArgs,
    ): IdkResult<TokenIntrospectionResponse, IdkError> {
        val applied = applyDuring(args)
        return introspectTokenInternal(
            applied.authorizationServerMetadata,
            applied.token,
            applied.clientAuthentication,
            applied.tokenTypeHint,
            applied.additionalParameters,
        ).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun introspectTokenInternal(
        authorizationServerMetadata: AuthorizationServerMetadata,
        token: String,
        clientAuthentication: ClientAuthenticationConfig,
        tokenTypeHint: String?,
        additionalParameters: Map<String, String>,
    ): IdkResult<TokenIntrospectionResponse, IntrospectionError> {
        // Check that introspection endpoint is configured
        val introspectionEndpoint =
            authorizationServerMetadata.introspectionEndpoint
                ?: return Err(IntrospectionError.EndpointNotConfigured())

        // Build introspection request
        val introspectionRequest =
            TokenIntrospectionRequest(
                token = token,
                tokenTypeHint = tokenTypeHint,
                additionalParameters = additionalParameters,
            )

        // Validate request
        val requestValidation =
            validateTokenIntrospectionRequest(introspectionRequest)
                .toIdkResult { errors -> IntrospectionError.ResponseValidationFailed(errors) }

        if (requestValidation.isErr) {
            return Err(requestValidation.error)
        }

        return try {
            // Apply client authentication (need endpoint URL for JWT audience)
            val authResult =
                applyClientAuthenticationCommand.execute(
                    ApplyClientAuthenticationArgs(clientAuthentication, introspectionEndpoint),
                )

            if (authResult.isErr) {
                return Err(
                    IntrospectionError.RequestFailed(
                        reason = "Client authentication failed: ${authResult.error.message.defaultMessage}",
                        exception = null,
                    ),
                )
            }

            val authData = authResult.value

            // Merge auth body parameters with introspection parameters
            val allBodyParameters = buildIntrospectionBody(introspectionRequest) + authData.bodyParameters

            // Create HTTP client
            val httpClient =
                httpClientFactory.createClient(
                    HttpClientOptions(
                        engine = null,
                        enableContentNegotiation = true,
                    ),
                )

            // Send introspection request
            val response: HttpResponse =
                httpClient.post(introspectionEndpoint) {
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
                        exception = null,
                    ),
                )
            }

            // Parse response
            val introspectionResponse =
                json.decodeFromString<TokenIntrospectionResponse>(
                    response.body<String>(),
                )

            // Validate response
            val validationResult =
                validateTokenIntrospectionResponse(introspectionResponse)
                    .toIdkResult { errors -> IntrospectionError.ResponseValidationFailed(errors) }

            if (validationResult.isErr) {
                return validationResult
            }

            Ok(introspectionResponse)
        } catch (expected: Exception) {
            Err(
                IntrospectionError.RequestFailed(
                    reason = "Failed to introspect token: ${expected.message}",
                    exception = expected,
                ),
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
    private fun encodeFormData(parameters: Map<String, String>): String =
        parameters.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }

    /**
     * URL-encodes a string.
     * Uses basic encoding for common characters.
     */
    private fun urlEncode(value: String): String =
        value
            .replace(" ", "+")
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
