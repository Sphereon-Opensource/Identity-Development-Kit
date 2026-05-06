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

package com.sphereon.openid.oid4vp.holder.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.ClientMetadata
import com.sphereon.openid.oid4vp.holder.command.ResolveClientMetadataCommand
import com.sphereon.openid.oid4vp.holder.command.ResolveClientMetadataCommandService
import com.sphereon.openid.oid4vp.holder.command.ResolvedClientMetadata
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Implementation of ResolveClientMetadataCommand.
 *
 * Resolves client metadata following OpenID4VP 1.0 Section 5.3:
 * 1. Fetch from client_metadata_uri (HTTPS required)
 * 2. Parse embedded client_metadata
 * 3. Return null metadata if not provided (client_metadata is OPTIONAL per spec)
 *
 * All errors are properly converted to IdkError instead of being suppressed.
 */
@Inject
@SingleIn(SessionScope::class)
class ResolveClientMetadataCommandImpl(
    private val httpClientFactory: HttpClientFactory,
    execution: SessionExecution,
) : TypedServiceCommandAdapter<AuthorizationRequest, ResolvedClientMetadata, IdkError>(
        commandId = ResolveClientMetadataCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<AuthorizationRequest>(),
        outputTypeToken = typeToken<ResolvedClientMetadata>(),
    ),
    ResolveClientMetadataCommand,
    ResolveClientMetadataCommandService {
    override val commandId: String get() = ResolveClientMetadataCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is AuthorizationRequest

    override suspend fun resolveClientMetadata(request: AuthorizationRequest): IdkResult<ResolvedClientMetadata, IdkError> = execute(request)

    override suspend fun doExecute(
        args: AuthorizationRequest,
        applyDuring: (AuthorizationRequest) -> AuthorizationRequest,
    ): IdkResult<ResolvedClientMetadata, IdkError> {
        val request = applyDuring(args)

        // Priority 1: Fetch from client_metadata_uri
        request.additionalParameters["client_metadata_uri"]?.let { uriElement ->
            val metadataUri =
                try {
                    Json.decodeFromJsonElement(String.serializer(), uriElement)
                } catch (expected: Exception) {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Failed to parse client_metadata_uri: ${expected.message}",
                        ),
                    )
                }

            // Validate HTTPS requirement per OpenID4VP 1.0
            if (!metadataUri.startsWith("https://", ignoreCase = true)) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "client_metadata_uri must be HTTPS: $metadataUri",
                    ),
                )
            }

            // Fetch metadata via HTTP
            return fetchClientMetadata(metadataUri)
        }

        // Priority 2: Parse embedded client_metadata
        request.additionalParameters["client_metadata"]?.let { metadataJson ->
            return try {
                // `client_metadata` may arrive as:
                // - a JSON object (when coming from a parsed Request Object/JAR)
                // - a JSON string containing the JSON object (when passed/merged via query parameters)
                val normalized: JsonElement =
                    when (metadataJson) {
                        is JsonPrimitive -> {
                            val raw = metadataJson.contentOrNull?.trim()
                            if (raw.isNullOrBlank()) {
                                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to parse embedded client_metadata: empty client_metadata"))
                            }
                            Json.parseToJsonElement(raw)
                        }

                        else -> {
                            metadataJson
                        }
                    }
                val metadata = Json.decodeFromJsonElement(ClientMetadata.serializer(), normalized)
                Ok(ResolvedClientMetadata(metadata))
            } catch (expected: Exception) {
                Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Failed to parse embedded client_metadata: ${expected.message}",
                    ),
                )
            }
        }

        // Priority 3: No metadata provided - return null per spec (client_metadata is OPTIONAL)
        return Ok(ResolvedClientMetadata(null))
    }

    /**
     * Fetches client metadata from a URI via HTTP GET.
     */
    private suspend fun fetchClientMetadata(uri: String): IdkResult<ResolvedClientMetadata, IdkError> {
        return try {
            val httpClient =
                httpClientFactory.createClient(
                    HttpClientOptions(
//                    engine = HttpClientEngineType.CIO,
                        enableContentNegotiation = true,
                    ),
                )

            val response: HttpResponse = httpClient.get(uri)

            if (response.status != HttpStatusCode.OK) {
                return Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to fetch client metadata from $uri: HTTP ${response.status.value}",
                    ),
                )
            }

            val responseBody = response.body<String>()
            val metadata = Json.decodeFromString(ClientMetadata.serializer(), responseBody)

            Ok(ResolvedClientMetadata(metadata))
        } catch (expected: Exception) {
            Err(
                IdkError.UNKNOWN_ERROR(
                    message = "Failed to fetch client metadata from $uri: ${expected.message}",
                ),
            )
        }
    }
}
