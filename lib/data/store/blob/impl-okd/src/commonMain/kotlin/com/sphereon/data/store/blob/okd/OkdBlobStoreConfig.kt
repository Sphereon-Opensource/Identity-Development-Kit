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

import com.sphereon.data.store.blob.AbstractBlobStoreConfig
import com.sphereon.data.store.blob.BlobStoreConfigBase
import com.sphereon.data.store.blob.BlobStoreScopeBinding
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Authentication mode for outbound OKD API calls.
 */
@Serializable
enum class OkdAuthMode {
    /** Forward the user's bearer token from their OAuth2/OIDC session */
    PASSTHROUGH,

    /** Use client credentials (M2M / background access) */
    CLIENT_CREDENTIALS,
}

/**
 * Authentication configuration for OKD API calls.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdAuthConfig", exact = true)
data class OkdAuthConfig(
    val mode: OkdAuthMode = OkdAuthMode.PASSTHROUGH,
    /** Static bearer token (PASSTHROUGH mode — set by framework layer per-request, or from config) */
    val token: String? = null,
    /** OAuth2 token endpoint URL (CLIENT_CREDENTIALS mode) */
    @SerialName("tokenUri")
    val tokenUri: String? = null,
    /** OAuth2 client ID (CLIENT_CREDENTIALS mode) */
    @SerialName("clientId")
    val clientId: String? = null,
    /** OAuth2 client secret — use env var substitution: ${OKD_CLIENT_SECRET} */
    @SerialName("clientSecret")
    val clientSecret: String? = null,
    /** OAuth2 scopes to request */
    val scopes: List<String> = emptyList(),
    /** HTTP header name for the bearer token */
    @SerialName("authHeader")
    val authHeader: String = "Authorization",
    /** Forward tenant ID from session context as a header */
    @SerialName("useTenantFromContext")
    val useTenantFromContext: Boolean = true,
    /** Header name for tenant ID */
    @SerialName("tenantHeader")
    val tenantHeader: String? = null,
)

/**
 * Configuration for an OKD-compliant DMS as a blob store backend.
 *
 * ```properties
 * blob.stores.school-dms.type=okd
 * blob.stores.school-dms.scopeBinding=TENANT
 * blob.stores.school-dms.baseUrl=https://dms.school.nl/api/v5
 * blob.stores.school-dms.auth.mode=CLIENT_CREDENTIALS
 * blob.stores.school-dms.auth.tokenUri=https://dms.school.nl/oauth2/token
 * blob.stores.school-dms.auth.clientId=portal-service
 * blob.stores.school-dms.auth.clientSecret=${OKD_CLIENT_SECRET}
 * blob.stores.school-dms.auth.scopes=okd:alldocuments
 * ```
 */
@Serializable
@SerialName("okd")
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdBlobStoreConfig", exact = true)
data class OkdBlobStoreConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val id: String = "okd",
    @SerialName("scopebinding")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val scopeBinding: BlobStoreScopeBinding = BlobStoreScopeBinding.TENANT,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val enabled: Boolean = true,
    /** Base URL of the OKD API (e.g., https://dms.school.nl/api/v5) */
    @SerialName("baseUrl")
    val baseUrl: String = "",
    /** Authentication configuration */
    val auth: OkdAuthConfig = OkdAuthConfig(),
    /** Connection timeout in milliseconds */
    @SerialName("connectionTimeoutMs")
    val connectionTimeoutMs: Long = 10_000,
    /** Request timeout in milliseconds */
    @SerialName("requestTimeoutMs")
    val requestTimeoutMs: Long = 30_000,
) : AbstractBlobStoreConfig(),
    BlobStoreConfigBase {
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val backendId: String = BACKEND_ID

    companion object {
        const val BACKEND_ID = "okd"
    }
}
