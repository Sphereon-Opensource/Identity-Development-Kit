package com.sphereon.data.store.blob.http

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreConfigBase
import com.sphereon.data.store.blob.BlobStoreFactory
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.call.body
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

/**
 * Factory for creating [HttpBlobStoreAdapter] instances backed by [HttpBlobServiceClient].
 *
 * Uses [HttpClientFactory] to create the underlying HTTP client with proper platform engine.
 * For CLIENT_CREDENTIALS auth mode, installs Ktor Auth plugin for automatic token management.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<BlobStoreFactory>())
class HttpBlobStoreFactoryImpl(
    private val httpClientFactory: HttpClientFactory,
) : BlobStoreFactory {

    override val backendId: String = HttpBlobServiceClientConfig.BACKEND_ID

    override fun create(config: BlobStoreConfigBase, execution: SessionExecution?): BlobStore {
        require(config.backendId.equals(backendId, ignoreCase = true)) {
            "HttpBlobStoreFactory cannot create backend '${config.backendId}'. Supported: '$backendId'"
        }

        val typedConfig = config as? HttpBlobServiceClientConfig
            ?: throw IllegalArgumentException(
                "HTTP blob store requires HttpBlobServiceClientConfig, got ${config::class.simpleName}. " +
                "Ensure 'blob.stores.${config.id}.type=http' is set in config."
            )

        require(typedConfig.baseUrl.isNotBlank()) {
            "HTTP blob store config '${config.id}' must have a non-blank 'baseUrl'"
        }

        val httpClient = createHttpClient(typedConfig)
        val client = HttpBlobServiceClient(config = typedConfig, http = httpClient, execution = execution)

        val tenantId = try {
            execution?.sessionContext?.context?.tenant?.tenantId?.takeIf {
                it.isNotBlank() && it != "<anonymous>"
            } ?: "default"
        } catch (_: Exception) { "default" }

        return HttpBlobStoreAdapter(client = client, tenantId = tenantId)
    }

    private fun createHttpClient(config: HttpBlobServiceClientConfig): HttpClient {
        val baseClient = httpClientFactory.createClient(
            HttpClientOptions(
                enableContentNegotiation = true,
            )
        )

        return when (config.auth.mode) {
            HttpBlobAuthMode.BEARER, HttpBlobAuthMode.STATIC_TOKEN -> {
                // Auth headers applied per-request by HttpBlobServiceClient.applyAuth()
                baseClient
            }
            HttpBlobAuthMode.CLIENT_CREDENTIALS -> {
                val tokenUri = config.auth.tokenUri
                    ?: throw IllegalArgumentException("HTTP blob client credentials requires 'auth.tokenUri'")
                val clientId = config.auth.clientId
                    ?: throw IllegalArgumentException("HTTP blob client credentials requires 'auth.clientId'")
                val clientSecret = config.auth.clientSecret
                    ?: throw IllegalArgumentException("HTTP blob client credentials requires 'auth.clientSecret'")
                val scopes = config.auth.scopes

                // Create a new client with the Auth plugin wrapping the factory-created engine
                HttpClient(baseClient.engine) {
                    install(ContentNegotiation) {
                        json(Json {
                            encodeDefaults = true
                            ignoreUnknownKeys = true
                        })
                    }
                    install(Auth) {
                        bearer {
                            loadTokens {
                                fetchToken(tokenUri, clientId, clientSecret, scopes)
                            }
                            refreshTokens {
                                fetchToken(tokenUri, clientId, clientSecret, scopes)
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
        clientSecret: String,
        scopes: List<String>,
    ): BearerTokens {
        val tokenClient = httpClientFactory.createClient(
            HttpClientOptions(enableContentNegotiation = true)
        )
        try {
            val response = tokenClient.submitForm(
                url = tokenUri,
                formParameters = Parameters.build {
                    append("grant_type", "client_credentials")
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    if (scopes.isNotEmpty()) {
                        append("scope", scopes.joinToString(" "))
                    }
                },
            )
            val body = response.body<JsonObject>()
            val accessToken = (body["access_token"] as? JsonPrimitive)?.content
                ?: throw IllegalStateException("Token endpoint did not return access_token")
            return BearerTokens(accessToken = accessToken, refreshToken = "")
        } finally {
            tokenClient.close()
        }
    }
}
