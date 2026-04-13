package com.sphereon.data.store.blob.http

import com.sphereon.data.store.blob.AbstractBlobStoreConfig
import com.sphereon.data.store.blob.BlobStoreConfigBase
import com.sphereon.data.store.blob.BlobStoreScopeBinding
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Authentication mode for outbound HTTP blob service calls.
 */
@Serializable
enum class HttpBlobAuthMode {
    /** Forward session JWT. Server extracts tenant/principal from OIDC claims. */
    BEARER,
    /** OAuth2 client credentials grant for M2M tokens. */
    CLIENT_CREDENTIALS,
    /** Static bearer token (dev/testing). */
    STATIC_TOKEN,
}

/**
 * Authentication configuration for HTTP blob service calls.
 */
@Serializable
data class HttpBlobAuthConfig(
    val mode: HttpBlobAuthMode = HttpBlobAuthMode.BEARER,

    /** Static bearer token (STATIC_TOKEN mode — use ${secret:env:BLOB_TOKEN} in config) */
    val token: String? = null,

    /** OAuth2 token endpoint URL (CLIENT_CREDENTIALS mode) */
    @SerialName("tokenUri")
    val tokenUri: String? = null,
    /** OAuth2 client ID (CLIENT_CREDENTIALS mode) */
    @SerialName("clientId")
    val clientId: String? = null,
    /** OAuth2 client secret (CLIENT_CREDENTIALS mode — use ${secret:env:BLOB_CLIENT_SECRET} in config) */
    @SerialName("clientSecret")
    val clientSecret: String? = null,
    /** OAuth2 scopes to request */
    val scopes: List<String> = emptyList(),

    /** Fallback tenant header (only used when auth is disabled / JWT has no tenant claim) */
    @SerialName("tenantHeader")
    val tenantHeader: String? = "X-Tenant-Id",
)

/**
 * Configuration for an HTTP blob service client connecting to service-data's blob REST API.
 *
 * ```properties
 * blob.stores.remote.type=http
 * blob.stores.remote.baseUrl=http://service-data:8080
 * blob.stores.remote.defaultStoreId=documents
 * blob.stores.remote.auth.mode=BEARER
 * ```
 */
@Serializable
@SerialName("http")
data class HttpBlobServiceClientConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val id: String = "http",
    @SerialName("scopebinding")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val scopeBinding: BlobStoreScopeBinding = BlobStoreScopeBinding.TENANT,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val enabled: Boolean = true,

    /** Base URL of the blob service (e.g., http://service-data:8080) */
    @SerialName("baseUrl")
    val baseUrl: String = "",

    /** Default store ID used when no explicit store ID is provided */
    @SerialName("defaultStoreId")
    val defaultStoreId: String = "default",

    /** Authentication configuration */
    val auth: HttpBlobAuthConfig = HttpBlobAuthConfig(),

    /** Connection timeout in milliseconds */
    @SerialName("connectionTimeoutMs")
    val connectionTimeoutMs: Long = 10_000,

    /** Request timeout in milliseconds */
    @SerialName("requestTimeoutMs")
    val requestTimeoutMs: Long = 60_000,
) : AbstractBlobStoreConfig(), BlobStoreConfigBase {
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val backendId: String = BACKEND_ID

    companion object {
        const val BACKEND_ID = "http"
    }
}
