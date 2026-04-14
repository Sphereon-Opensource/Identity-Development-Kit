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
import com.sphereon.openid.oid4vci.common.serializer.CredentialConfigurationSupportedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@JsExportCompat
@Serializable(with = CredentialConfigurationSupportedSerializer::class)
data class CredentialConfigurationSupported(
    val format: String,
    val scope: String? = null,
    @SerialName("cryptographic_binding_methods_supported") val cryptographicBindingMethodsSupported: List<String>? = null,
    @SerialName("credential_signing_alg_values_supported") val credentialSigningAlgValuesSupported: List<String>? = null,
    @SerialName("proof_types_supported") val proofTypesSupported: Map<String, ProofTypeSupported>? = null,
    val display: List<DisplayProperties>? = null,
    @SerialName("credential_definition") val credentialDefinition: CredentialDefinition? = null,
    val vct: String? = null,
    val claims: Map<String, ClaimMetadata>? = null,
    val doctype: String? = null,
    val order: List<String>? = null,
    @SerialName("credential_response_encryption") val credentialResponseEncryption: CredentialResponseEncryption? = null,
    @SerialName("credential_metadata") val credentialMetadata: CredentialMetadata? = null,
    val additionalParameters: Map<String, JsonElement> = emptyMap(),
)

@JsExportCompat
@Serializable
data class KeyAttestationsRequired(
    @SerialName("key_storage") val keyStorage: List<String>? = null,
    @SerialName("user_authentication") val userAuthentication: List<String>? = null,
)

@JsExportCompat
@Serializable
data class ProofTypeSupported(
    @SerialName("proof_signing_alg_values_supported") val proofSigningAlgValuesSupported: List<String>,
    @SerialName("key_attestations_required") val keyAttestationsRequired: KeyAttestationsRequired? = null,
)

@JsExportCompat
@Serializable
data class CredentialDefinition(
    val type: List<String>? = null,
    @SerialName("@context") val context: List<String>? = null,
    @SerialName("credentialSubject") val credentialSubject: Map<String, ClaimMetadata>? = null,
)

@JsExportCompat
@Serializable
data class ClaimMetadata(
    val mandatory: Boolean? = null,
    @SerialName("value_type") val valueType: String? = null,
    val display: List<ClaimDisplay>? = null,
)

@JsExportCompat
@Serializable
data class ClaimDisplay(
    val name: String,
    val locale: String? = null,
)

@JsExportCompat
@Serializable
data class CredentialResponseEncryption(
    @SerialName("alg_values_supported") val algValuesSupported: List<String>,
    @SerialName("enc_values_supported") val encValuesSupported: List<String>,
    @SerialName("zip_values_supported") val zipValuesSupported: List<String>? = null,
    @SerialName("encryption_required") val encryptionRequired: Boolean = false,
)

/**
 * OID4VCI 1.1 credential metadata within a credential configuration.
 */
@JsExportCompat
@Serializable
data class CredentialMetadata(
    val display: List<DisplayProperties>? = null,
    val claims: List<CredentialMetadataClaim>? = null,
)

/**
 * OID4VCI 1.1 path-based claim metadata for credential_metadata.
 *
 * [path] elements can be strings (field names) or integers (array indices),
 * hence modeled as [JsonElement].
 */
@JsExportCompat
@Serializable
data class CredentialMetadataClaim(
    val path: List<JsonElement>,
    val mandatory: Boolean? = null,
    val display: List<ClaimDisplay>? = null,
)
