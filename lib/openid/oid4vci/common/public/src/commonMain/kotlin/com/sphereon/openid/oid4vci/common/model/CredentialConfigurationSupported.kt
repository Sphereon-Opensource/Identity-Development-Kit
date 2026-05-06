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
    /**
     * Per OID4VCI 1.0 final §12.2.3 the algorithm-identifier element type is format-specific:
     * - JWS-based formats (`dc+sd-jwt`, `jwt_vc_json`, …): JSON strings — JWA names per IANA
     *   JOSE (e.g. `"ES256"`).
     * - `mso_mdoc`: JSON integers — numeric COSE algorithm identifiers per IANA COSE
     *   (e.g. `-7` for ECDSA w/ SHA-256), per §A.3.2 of the spec.
     *
     * The list element is therefore [JsonElement] so both shapes round-trip without lossy
     * normalisation; the metadata builder picks the right element type from the format.
     */
    @SerialName("credential_signing_alg_values_supported") val credentialSigningAlgValuesSupported: List<JsonElement>? = null,
    @SerialName("proof_types_supported") val proofTypesSupported: Map<String, ProofTypeSupported>? = null,
    val display: List<DisplayProperties>? = null,
    @SerialName("credential_definition") val credentialDefinition: CredentialDefinition? = null,
    val vct: String? = null,
    /**
     * Per OID4VCI 1.0 final §12.2.3 + Appendix A JSON schema, `claims` is a NON-EMPTY ARRAY of
     * claim-description objects each with a `path` claims-path-pointer (per §A.5). The
     * pre-final draft shape (`Map<String, ClaimMetadata>`) is no longer in any current draft
     * and the conformance suite's `VCICredentialIssuerMetadataValidation` rejects it
     * outright — there is no compatibility window to honour.
     */
    val claims: List<CredentialClaim>? = null,
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

/**
 * Path-based claim description per OID4VCI 1.0 final §12.2.3 (`claims` array entry).
 *
 * `path` is a claims-path-pointer per OID4VCI §A.5 / OID4VP §6.5:
 * - SD-JWT VC and JWT-based credentials: ordered field names → typically a single-element
 *   array `["family_name"]` for top-level claims, longer for nested.
 * - mso_mdoc: exactly two strings — `[namespace, elementIdentifier]`, e.g.
 *   `["org.iso.18013.5.1", "family_name"]`.
 */
@JsExportCompat
@Serializable
data class CredentialClaim(
    val path: List<String>,
    val mandatory: Boolean? = null,
    @SerialName("value_type") val valueType: String? = null,
    val display: List<ClaimDisplay>? = null,
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
