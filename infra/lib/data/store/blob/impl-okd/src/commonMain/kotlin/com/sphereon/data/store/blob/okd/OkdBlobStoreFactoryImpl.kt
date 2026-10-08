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

package com.sphereon.data.store.blob.okd

import com.sphereon.core.api.conf.OpaqueSecretResolver
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreConfigBase
import com.sphereon.data.store.blob.BlobStoreFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Factory for creating [OkdBlobStore] instances that talk to external OKD-compliant DMS systems.
 *
 * Auth modes:
 * - **BEARER**: User's bearer token forwarded from authenticated session context per-request.
 * - **STATIC_TOKEN**: Opaque token handle resolved immediately before each request.
 * - **CLIENT_CREDENTIALS**: Ktor Auth plugin acquires tokens from the configured token endpoint.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<BlobStoreFactory>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdBlobStoreFactoryImpl", exact = true)
class OkdBlobStoreFactoryImpl(
    private val opaqueSecretResolver: OpaqueSecretResolver,
) : BlobStoreFactory {
    override val backendId: String = OkdBlobStoreConfig.BACKEND_ID

    override fun create(
        config: BlobStoreConfigBase,
        execution: SessionExecution?,
    ): BlobStore {
        require(config.backendId.equals(backendId, ignoreCase = true)) {
            "OkdBlobStoreFactory cannot create backend '${config.backendId}'. Supported: '$backendId'"
        }

        val typedConfig =
            config as? OkdBlobStoreConfig
                ?: throw IllegalArgumentException(
                    "OKD blob store requires OkdBlobStoreConfig, got ${config::class.simpleName}. " +
                        "Ensure 'blob.stores.${config.id}.type=okd' is set in config.",
                )

        require(typedConfig.baseUrl.isNotBlank()) {
            "OKD blob store config '${config.id}' must have a non-blank 'baseUrl'"
        }

        val httpClient = createHttpClient(typedConfig)
        return OkdBlobStore(
            config = typedConfig,
            http = httpClient,
            execution = execution,
            opaqueSecretResolver = opaqueSecretResolver,
        )
    }

    private fun createHttpClient(config: OkdBlobStoreConfig): HttpClient {
        val jsonConfig = Json { ignoreUnknownKeys = true }

        return when (config.auth.mode) {
            OkdAuthMode.BEARER, OkdAuthMode.STATIC_TOKEN -> {
                HttpClient {
                    install(ContentNegotiation) { json(jsonConfig) }
                }
            }

            OkdAuthMode.CLIENT_CREDENTIALS -> {
                val tokenUri =
                    config.auth.tokenUri
                        ?: throw IllegalArgumentException("OKD client credentials requires 'auth.tokenUri'")
                val clientId =
                    config.auth.clientId
                        ?: throw IllegalArgumentException("OKD client credentials requires 'auth.clientId'")
                val clientSecretId =
                    config.auth.clientSecretId
                        ?: throw IllegalArgumentException("OKD client credentials requires 'auth.clientSecretId'")
                val scopes = config.auth.scopes

                HttpClient {
                    install(ContentNegotiation) { json(jsonConfig) }
                    install(Auth) {
                        bearer {
                            loadTokens {
                                fetchToken(tokenUri, clientId, clientSecretId, scopes)
                            }
                            refreshTokens {
                                fetchToken(tokenUri, clientId, clientSecretId, scopes)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchToken(
        tokenUri: String,
        clientId: String,
        clientSecretId: String,
        scopes: List<String>,
    ): BearerTokens {
        val clientSecret = resolveCredential(clientSecretId)
        val tokenClient =
            HttpClient {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        try {
            val response =
                tokenClient.submitForm(
                    url = tokenUri,
                    formParameters =
                        Parameters.build {
                            append("grant_type", "client_credentials")
                            append("client_id", clientId)
                            append("client_secret", clientSecret)
                            if (scopes.isNotEmpty()) {
                                append("scope", scopes.joinToString(" "))
                            }
                        },
                )
            val body = response.body<JsonObject>()
            val accessToken =
                checkNotNull((body["access_token"] as? JsonPrimitive)?.content) {
                    "Token endpoint did not return access_token"
                }
            return BearerTokens(accessToken = accessToken, refreshToken = "")
        } finally {
            tokenClient.close()
        }
    }

    private suspend fun resolveCredential(secretId: String): String {
        secretId.requireOkdOpaqueSecretId("clientSecretId")
        val result = opaqueSecretResolver.resolve(secretId)
        if (result.isErr || result.value.isBlank()) {
            throw IllegalStateException("OKD credential is unavailable")
        }
        return result.value
    }
}
