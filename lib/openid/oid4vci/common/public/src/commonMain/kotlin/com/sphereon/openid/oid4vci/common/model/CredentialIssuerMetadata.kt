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

package com.sphereon.openid.oid4vci.common.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.serializer.CredentialIssuerMetadataSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * OID4VCI 1.0 Credential Issuer Metadata (Section 12.2.4)
 */
@JsExportCompat
@Serializable(with = CredentialIssuerMetadataSerializer::class)
data class CredentialIssuerMetadata(
    @SerialName("credential_issuer") val credentialIssuer: String,
    @SerialName("authorization_servers") val authorizationServers: List<String>? = null,
    @SerialName("credential_endpoint") val credentialEndpoint: String,
    @SerialName("batch_credential_endpoint") val batchCredentialEndpoint: String? = null,
    @SerialName("deferred_credential_endpoint") val deferredCredentialEndpoint: String? = null,
    @SerialName("notification_endpoint") val notificationEndpoint: String? = null,
    @SerialName("nonce_endpoint") val nonceEndpoint: String? = null,
    @SerialName("credential_configurations_supported") val credentialConfigurationsSupported: Map<String, CredentialConfigurationSupported>,
    @SerialName("signed_metadata") val signedMetadata: String? = null,
    val display: List<DisplayProperties>? = null,
    @SerialName("credential_response_encryption") val credentialResponseEncryption: MetadataCredentialResponseEncryption? = null,
    @SerialName("credential_request_encryption") val credentialRequestEncryption: MetadataCredentialRequestEncryption? = null,
    @SerialName("batch_credential_issuance") val batchCredentialIssuance: BatchCredentialIssuance? = null,
    val additionalMetadata: Map<String, JsonElement> = emptyMap(),
)

/**
 * OID4VCI 1.1 top-level credential response encryption metadata.
 *
 * Declares what encryption algorithms the issuer supports for encrypting credential responses.
 */
@JsExportCompat
@Serializable
data class MetadataCredentialResponseEncryption(
    @SerialName("alg_values_supported") val algValuesSupported: List<String>,
    @SerialName("enc_values_supported") val encValuesSupported: List<String>,
    @SerialName("zip_values_supported") val zipValuesSupported: List<String>? = null,
    @SerialName("encryption_required") val encryptionRequired: Boolean = false,
)

/**
 * OID4VCI 1.1 credential request encryption metadata.
 *
 * Declares the issuer's encryption key and supported algorithms for receiving encrypted requests.
 */
@JsExportCompat
@Serializable
data class MetadataCredentialRequestEncryption(
    val jwks: JsonObject,
    @SerialName("enc_values_supported") val encValuesSupported: List<String>,
    @SerialName("zip_values_supported") val zipValuesSupported: List<String>? = null,
    @SerialName("encryption_required") val encryptionRequired: Boolean = false,
)

/**
 * OID4VCI 1.1 batch credential issuance metadata.
 */
@JsExportCompat
@Serializable
data class BatchCredentialIssuance(
    @SerialName("batch_size") val batchSize: Int,
)
