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
import com.sphereon.openid.oid4vci.common.serializer.CredentialRequestProofsSerializer
import com.sphereon.openid.oid4vci.common.serializer.CredentialRequestSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.jvm.JvmStatic

/**
 * OID4VCI Credential Request (1.0 Section 8.2, 1.1 Section 9.2)
 *
 * The Wallet sends this to the Credential Endpoint.
 * [credentialConfigurationId] or [credentialIdentifier] identifies what is requested.
 * Format-specific parameters (vct, doctype) per Appendix A format profiles.
 * Any unknown format-specific parameters land in [additionalParameters].
 *
 * Per the spec, only `proofs` (plural) is supported. There is no singular `proof` field.
 */
@JsExportCompat
@Serializable(with = CredentialRequestSerializer::class)
data class CredentialRequest(
    @SerialName("credential_configuration_id") val credentialConfigurationId: String? = null,
    @SerialName("credential_identifier") val credentialIdentifier: String? = null,
    val format: String? = null,
    val proofs: CredentialRequestProofs? = null,
    @SerialName("credential_response_encryption") val credentialResponseEncryption: RequestedCredentialResponseEncryption? = null,
    val vct: String? = null,
    val doctype: String? = null,
    val additionalParameters: Map<String, JsonElement> = emptyMap(),
)

/**
 * OID4VCI proofs container (1.0 final + 1.1 Section 9.2).
 *
 * Wire format: `{"proofs": {"jwt": ["eyJ...", "eyJ..."]}}` — the proof type IS the
 * JSON key, NOT a separate `proof_type` field.
 *
 * [proofType] is the key name (e.g. "jwt", "cwt", "attestation", "di_vp").
 * [proofValues] is the non-empty array of proof values. For JWT/CWT/attestation proofs
 * these are [JsonPrimitive] strings; for di_vp proofs these are [JsonObject] values.
 *
 * Use the factory methods [jwt], [cwt], [attestation], [diVp] for type-safe construction.
 * Use the extension functions [stringValues] and [objectValues] for type-safe extraction.
 */
@Serializable(with = CredentialRequestProofsSerializer::class)
data class CredentialRequestProofs(
    val proofType: String,
    val proofValues: List<JsonElement>,
) {
    companion object {
        /** Create JWT proofs container from compact JWT strings. */
        @JvmStatic
        fun jwt(vararg jwtValues: String): CredentialRequestProofs = CredentialRequestProofs(proofType = "jwt", proofValues = jwtValues.map { JsonPrimitive(it) })

        /** Create JWT proofs container from compact JWT strings. */
        @JvmStatic
        fun jwt(jwtValues: List<String>): CredentialRequestProofs = CredentialRequestProofs(proofType = "jwt", proofValues = jwtValues.map { JsonPrimitive(it) })

        /** Create CWT proofs container from base64url-encoded CWT bytes. */
        @JvmStatic
        fun cwt(vararg cwtValues: String): CredentialRequestProofs = CredentialRequestProofs(proofType = "cwt", proofValues = cwtValues.map { JsonPrimitive(it) })

        /** Create CWT proofs container from base64url-encoded CWT bytes. */
        @JvmStatic
        fun cwt(cwtValues: List<String>): CredentialRequestProofs = CredentialRequestProofs(proofType = "cwt", proofValues = cwtValues.map { JsonPrimitive(it) })

        /** Create attestation proofs container from attestation strings. */
        @JvmStatic
        fun attestation(vararg attestationValues: String): CredentialRequestProofs = CredentialRequestProofs(proofType = "attestation", proofValues = attestationValues.map { JsonPrimitive(it) })

        /** Create di_vp proofs container from Verifiable Presentation objects. */
        @JvmStatic
        fun diVp(vararg vpObjects: JsonObject): CredentialRequestProofs = CredentialRequestProofs(proofType = "di_vp", proofValues = vpObjects.toList())

        /** Create di_vp proofs container from Verifiable Presentation objects. */
        @JvmStatic
        fun diVp(vpObjects: List<JsonObject>): CredentialRequestProofs = CredentialRequestProofs(proofType = "di_vp", proofValues = vpObjects)
    }
}

/**
 * Extract proof values as strings. Use for JWT, CWT, and attestation proof types
 * where each value is a [JsonPrimitive] string.
 *
 * @throws IllegalStateException if any value is not a string primitive
 */
fun CredentialRequestProofs.stringValues(): List<String> = proofValues.map { it.jsonPrimitive.content }

/**
 * Extract proof values as JSON objects. Use for di_vp proof type
 * where each value is a [JsonObject] (Verifiable Presentation).
 *
 * @throws IllegalStateException if any value is not a JSON object
 */
fun CredentialRequestProofs.objectValues(): List<JsonObject> = proofValues.map { it as JsonObject }

/**
 * OID4VCI 1.1 Section 9.2 requested credential response encryption.
 *
 * [alg] is optional in 1.1 because key agreement may come from the JWK itself.
 * [zip] is the compression algorithm (1.1 addition).
 */
@JsExportCompat
@Serializable
data class RequestedCredentialResponseEncryption(
    val jwk: JsonObject,
    val alg: String? = null,
    val enc: String,
    val zip: String? = null,
)
