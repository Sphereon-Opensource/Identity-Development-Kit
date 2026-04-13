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

package com.sphereon.oauth2.server.resource.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import dev.zacsweers.metro.Named
import com.sphereon.oauth2.client.command.FetchAuthorizationServerMetadataCommand
import com.sphereon.oauth2.client.command.FetchServerMetadataArgs
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationArgs
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationCommand
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.server.resource.command.IntrospectTokenArgs
import com.sphereon.oauth2.server.resource.command.IntrospectTokenCommand
import com.sphereon.oauth2.server.resource.error.ResourceServerError
import com.sphereon.oauth2.server.resource.model.TokenPayload
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import com.sphereon.di.session.SessionScope
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of IntrospectTokenCommand for resource server
 *
 * Introspects tokens using RFC 7662 (OAuth 2.0 Token Introspection).
 *
 * **Flow**:
 * 1. Fetch authorization server metadata (to get introspection endpoint)
 * 2. Call introspection endpoint with client authentication
 * 3. Validate introspection response (active=true)
 * 4. Return TokenPayload.Introspection
 *
 * **Note**: This implementation requires the resource server to be
 * registered as a client with the authorization server and have
 * introspection endpoint access.
 *
 * **Configuration**: The client authentication config must be provided
 * via @Named("resourceServer.clientAuthentication") injection.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResourceServerIntrospectTokenCommandImpl", exact = true)
class ResourceServerIntrospectTokenCommandImpl(
    execution: SessionExecution,
    private val fetchAuthorizationServerMetadataCommand: FetchAuthorizationServerMetadataCommand,
    private val applyClientAuthenticationCommand: ApplyClientAuthenticationCommand,
    private val httpClientFactory: HttpClientFactory,
    @Named("resourceServer.clientAuthentication") private val clientAuthentication: ClientAuthenticationConfig?
) : TypedServiceCommandAdapter<IntrospectTokenArgs, TokenPayload.Introspection>(
    commandId = IntrospectTokenCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<IntrospectTokenArgs>(),
    outputTypeToken = typeToken<TokenPayload.Introspection>(),
), IntrospectTokenCommand {

    private val json = Json { ignoreUnknownKeys = true }

    override val commandId: String get() = IntrospectTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is IntrospectTokenArgs

    override suspend fun doExecute(
        args: IntrospectTokenArgs,
        applyDuring: (IntrospectTokenArgs) -> IntrospectTokenArgs
    ): IdkResult<TokenPayload.Introspection, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.token, applied.authorizationServer).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        token: String,
        authorizationServer: String
    ): IdkResult<TokenPayload.Introspection, ResourceServerError> {
        // 1. Check if client authentication is configured
        if (clientAuthentication == null) {
            return Err(ResourceServerError.InvalidToken(
                "Token introspection requires client authentication configuration"
            ))
        }

        // 2. Fetch authorization server metadata
        val metadataResult = fetchAuthorizationServerMetadataCommand.execute(FetchServerMetadataArgs(authorizationServer))
        if (metadataResult.isErr) {
            return Err(ResourceServerError.InvalidToken(
                "Failed to fetch authorization server metadata: ${metadataResult.error.message.defaultMessage}"
            ))
        }

        val metadata = metadataResult.value

        // 3. Check if introspection endpoint is available
        val introspectionEndpoint = metadata.introspectionEndpoint
            ?: return Err(ResourceServerError.InvalidToken(
                "Authorization server does not support token introspection"
            ))

        // 4. Apply client authentication
        val authResult = applyClientAuthenticationCommand.execute(
            ApplyClientAuthenticationArgs(clientAuthentication, introspectionEndpoint)
        )

        if (authResult.isErr) {
            return Err(ResourceServerError.InvalidToken(
                "Client authentication failed: ${authResult.error.message.defaultMessage}"
            ))
        }

        val authData = authResult.value

        // 5. Build introspection request body
        val bodyParameters = mutableMapOf<String, String>()
        bodyParameters["token"] = token
        bodyParameters["token_type_hint"] = "access_token"
        bodyParameters.putAll(authData.bodyParameters)

        return try {
            // 6. Create HTTP client
            val httpClient = httpClientFactory.createClient(
                HttpClientOptions(
                    engine = null,
                    enableContentNegotiation = true
                )
            )

            // 7. Send introspection request
            val response: HttpResponse = httpClient.post(introspectionEndpoint) {
                contentType(ContentType.Application.FormUrlEncoded)
                headers {
                    authData.headers.forEach { (key, value) ->
                        append(key, value)
                    }
                }
                setBody(encodeFormData(bodyParameters))
            }

            // 8. Check response status
            if (response.status != HttpStatusCode.OK) {
                return Err(ResourceServerError.InvalidToken(
                    "Introspection endpoint returned status ${response.status.value}"
                ))
            }

            // 9. Parse introspection response
            val introspectionResponse = json.decodeFromString<TokenIntrospectionResponse>(
                response.body<String>()
            )

            // 10. Validate that token is active
            if (!introspectionResponse.active) {
                return Err(ResourceServerError.InvalidToken("Token is not active"))
            }

            // 11. Return TokenPayload.Introspection
            Ok(TokenPayload.Introspection(introspectionResponse))

        } catch (e: Exception) {
            Err(ResourceServerError.InvalidToken(
                "Token introspection failed: ${e.message}"
            ))
        }
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
