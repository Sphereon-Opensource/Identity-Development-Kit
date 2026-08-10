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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Authentication mode for outbound OKD API calls.
 */
@Serializable
enum class OkdAuthMode {
    /** Forward the authenticated session's bearer token. */
    BEARER,

    /** Use client credentials (M2M / background access). */
    CLIENT_CREDENTIALS,

    /** Resolve a static bearer token from an opaque secret handle. */
    STATIC_TOKEN,
}

/**
 * Authentication configuration for OKD API calls.
 */
@Serializable(with = OkdAuthConfigSerializer::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdAuthConfig", exact = true)
data class OkdAuthConfig(
    val mode: OkdAuthMode = OkdAuthMode.BEARER,
    /** Opaque ID for a static bearer token. The token itself is never serialized. */
    @SerialName("tokenSecretId")
    val tokenSecretId: String? = null,
    /** OAuth2 token endpoint URL (CLIENT_CREDENTIALS mode). */
    @SerialName("tokenUri")
    val tokenUri: String? = null,
    /** OAuth2 client ID (CLIENT_CREDENTIALS mode). */
    @SerialName("clientId")
    val clientId: String? = null,
    /** Opaque ID for the OAuth2 client secret. The secret itself is never serialized. */
    @SerialName("clientSecretId")
    val clientSecretId: String? = null,
    /** OAuth2 scopes to request. */
    val scopes: List<String> = emptyList(),
) {
    init {
        tokenSecretId?.requireOkdOpaqueSecretId("tokenSecretId")
        clientSecretId?.requireOkdOpaqueSecretId("clientSecretId")

        when (mode) {
            OkdAuthMode.BEARER -> {
                require(tokenSecretId == null && tokenUri == null && clientId == null && clientSecretId == null && scopes.isEmpty()) {
                    "BEARER authentication derives credentials only from the authenticated session"
                }
            }

            OkdAuthMode.STATIC_TOKEN -> {
                require(tokenSecretId != null && tokenUri == null && clientId == null && clientSecretId == null && scopes.isEmpty()) {
                    "STATIC_TOKEN authentication requires only tokenSecretId"
                }
            }

            OkdAuthMode.CLIENT_CREDENTIALS -> {
                require(
                    tokenSecretId == null &&
                        !tokenUri.isNullOrBlank() &&
                        !clientId.isNullOrBlank() &&
                        clientSecretId != null,
                ) {
                    "CLIENT_CREDENTIALS authentication requires tokenUri, clientId, and clientSecretId"
                }
            }
        }
    }
}

internal fun String.requireOkdOpaqueSecretId(fieldName: String) {
    require(OKD_OPAQUE_SECRET_ID_PATTERN.matches(this)) {
        "$fieldName must be an opaque server-generated secret ID"
    }
}

private val OKD_OPAQUE_SECRET_ID_PATTERN = Regex("""^sec_[A-Za-z0-9_-]{16,128}$""")

@Serializable
private data class OkdAuthConfigWire(
    val mode: OkdAuthMode = OkdAuthMode.BEARER,
    val tokenSecretId: String? = null,
    val tokenUri: String? = null,
    val clientId: String? = null,
    val clientSecretId: String? = null,
    val scopes: List<String> = emptyList(),
)

/**
 * Rejects removed plaintext credential keys even when the surrounding JSON codec tolerates
 * unrelated forward-compatible fields.
 */
object OkdAuthConfigSerializer : KSerializer<OkdAuthConfig> {
    override val descriptor: SerialDescriptor = OkdAuthConfigWire.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: OkdAuthConfig,
    ) {
        encoder.encodeSerializableValue(
            OkdAuthConfigWire.serializer(),
            OkdAuthConfigWire(
                mode = value.mode,
                tokenSecretId = value.tokenSecretId,
                tokenUri = value.tokenUri,
                clientId = value.clientId,
                clientSecretId = value.clientSecretId,
                scopes = value.scopes,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): OkdAuthConfig {
        val wire =
            if (decoder is JsonDecoder) {
                val element = decoder.decodeJsonElement()
                val keys = element.jsonObject.keys
                require("token" !in keys && "clientSecret" !in keys && "authHeader" !in keys) {
                    "Plaintext or caller-selected OKD authentication fields are forbidden"
                }
                decoder.json.decodeFromJsonElement(OkdAuthConfigWire.serializer(), element)
            } else {
                decoder.decodeSerializableValue(OkdAuthConfigWire.serializer())
            }
        return OkdAuthConfig(
            mode = wire.mode,
            tokenSecretId = wire.tokenSecretId,
            tokenUri = wire.tokenUri,
            clientId = wire.clientId,
            clientSecretId = wire.clientSecretId,
            scopes = wire.scopes,
        )
    }
}

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
 * blob.stores.school-dms.auth.clientSecretId=sec_01JZZZZZZZZZZZZZ
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
    /** Base URL of the OKD API (e.g., https://dms.school.nl/api/v5). */
    @SerialName("baseUrl")
    val baseUrl: String = "",
    /** Authentication configuration. */
    val auth: OkdAuthConfig = OkdAuthConfig(),
    /** Connection timeout in milliseconds. */
    @SerialName("connectionTimeoutMs")
    val connectionTimeoutMs: Long = 10_000,
    /** Request timeout in milliseconds. */
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
