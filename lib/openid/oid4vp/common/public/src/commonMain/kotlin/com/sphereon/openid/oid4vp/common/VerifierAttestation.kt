/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.common

import io.konform.validation.Validation
import io.konform.validation.constraints.minLength
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

// =============================================================================
// Verifier Attestation Models (OpenID4VP 1.0 Section 5.1.1)
// =============================================================================

/**
 * A verifier attestation entry in the verifier_info parameter.
 *
 * Verifier attestations allow a verifier to prove their identity and authorization
 * to request specific credentials. Each attestation entry specifies:
 * - The format of the attestation (e.g., "jwt", custom format)
 * - The attestation data itself (as a JWT string or JSON object)
 * - Optionally, which credential queries this attestation applies to
 *
 * Reference: OpenID4VP 1.0 Section 5.1.1
 *
 * @property format The format identifier of the attestation (e.g., "jwt", "verifier-attestation+jwt")
 * @property data The attestation data - either a string (e.g., JWT) or a JSON object
 * @property credentialIds Optional list of credential query IDs this attestation applies to.
 *                         If not provided, the attestation applies to all credential queries.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifierAttestation", exact = true)
@JsExportCompat
@Serializable
data class VerifierAttestation(
    val format: String,
    @Serializable(with = VerifierAttestationDataSerializer::class)
    val data: VerifierAttestationData,
    @SerialName("credential_ids")
    val credentialIds: List<String>? = null
)

/**
 * The attestation data, which can be either a string (e.g., JWT) or a JSON object.
 *
 * For JWT-based attestations like verifier_attestation client_id scheme,
 * the data is typically a signed JWT string with the following structure:
 *
 * Header:
 * - typ: "verifier-attestation+jwt"
 * - alg: Signing algorithm
 *
 * Payload:
 * - iss: Issuer of the attestation (attestation provider)
 * - sub: Subject - must match the client_id
 * - exp: Expiration time
 * - cnf: Confirmation claim containing the verifier's public key (jwk)
 *
 * Reference: OpenID4VP 1.0 Section 10.4 (verifier_attestation scheme)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifierAttestationData", exact = true)
//@JsExportCompat
@Serializable
sealed interface VerifierAttestationData {
    /**
     * String-based attestation data (e.g., a JWT).
     */
    @Serializable
    data class StringData(val value: String) : VerifierAttestationData
    
    /**
     * JSON object-based attestation data.
     */
    @Serializable
    data class ObjectData(val value: JsonObject) : VerifierAttestationData
}

/**
 * Serializer that handles VerifierAttestationData as either a string or JSON object.
 */
object VerifierAttestationDataSerializer : kotlinx.serialization.KSerializer<VerifierAttestationData> {
    override val descriptor = JsonElement.serializer().descriptor
    
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: VerifierAttestationData) {
        val jsonEncoder = encoder as kotlinx.serialization.json.JsonEncoder
        when (value) {
            is VerifierAttestationData.StringData -> jsonEncoder.encodeJsonElement(JsonPrimitive(value.value))
            is VerifierAttestationData.ObjectData -> jsonEncoder.encodeJsonElement(value.value)
        }
    }
    
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): VerifierAttestationData {
        val jsonDecoder = decoder as kotlinx.serialization.json.JsonDecoder
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> VerifierAttestationData.StringData(element.content)
            is JsonObject -> VerifierAttestationData.ObjectData(element)
            else -> throw kotlinx.serialization.SerializationException("Expected string or object for attestation data")
        }
    }
}

/**
 * Type alias for a list of verifier attestations.
 */
typealias VerifierAttestations = List<VerifierAttestation>

// =============================================================================
// Well-Known Attestation Formats
// =============================================================================

/**
 * Well-known verifier attestation format identifiers.
 */
object VerifierAttestationFormat {
    /**
     * JWT-based verifier attestation (OpenID4VP verifier_attestation scheme).
     * Expected typ header: "verifier-attestation+jwt"
     */
    const val VERIFIER_ATTESTATION_JWT = "verifier-attestation+jwt"
    
    /**
     * Generic JWT format.
     */
    const val JWT = "jwt"
}

/**
 * Constants for verifier attestation JWT validation.
 */
object VerifierAttestationJwtConstants {
    /**
     * Required typ header value for verifier attestation JWTs.
     */
    const val TYP_HEADER = "verifier-attestation+jwt"
    
    /**
     * Required claims in verifier attestation JWT payload.
     */
    val REQUIRED_CLAIMS = listOf("iss", "sub", "exp", "cnf")
}

// =============================================================================
// Validation
// =============================================================================

/**
 * Validates a verifier attestation entry.
 */
val validateVerifierAttestation: Validation<VerifierAttestation> = Validation {
    VerifierAttestation::format {
        minLength(1) hint "Attestation format must not be empty"
    }
    
    VerifierAttestation::credentialIds ifPresent {
        constrain("If credential_ids is provided, it must not be empty") { it.isNotEmpty() }
        constrain("All credential IDs must be non-empty") { ids -> ids.all { it.isNotBlank() } }
    }
}

/**
 * Validates a list of verifier attestations.
 */
val validateVerifierAttestations: Validation<List<VerifierAttestation>> = Validation {
    constrain("Verifier attestations must be valid") { attestations ->
        attestations.all { attestation ->
            validateVerifierAttestation(attestation).isValid
        }
    }
}

// =============================================================================
// Builder DSL
// =============================================================================

/**
 * Builder for creating verifier attestations.
 */
class VerifierAttestationBuilder {
    private var format: String = ""
    private var data: VerifierAttestationData? = null
    private var credentialIds: List<String>? = null
    
    /**
     * Set the attestation format.
     */
    fun format(format: String) = apply { this.format = format }
    
    /**
     * Set the attestation data as a string (e.g., JWT).
     */
    fun data(jwt: String) = apply { 
        this.data = VerifierAttestationData.StringData(jwt) 
    }
    
    /**
     * Set the attestation data as a JSON object.
     */
    fun data(obj: JsonObject) = apply { 
        this.data = VerifierAttestationData.ObjectData(obj) 
    }
    
    /**
     * Set the credential IDs this attestation applies to.
     */
    fun credentialIds(vararg ids: String) = apply { 
        this.credentialIds = ids.toList().takeIf { it.isNotEmpty() }
    }
    
    /**
     * Set the credential IDs this attestation applies to.
     */
    fun credentialIds(ids: List<String>) = apply { 
        this.credentialIds = ids.takeIf { it.isNotEmpty() }
    }
    
    /**
     * Build the verifier attestation.
     */
    fun build(): VerifierAttestation {
        require(format.isNotBlank()) { "Attestation format must be set" }
        requireNotNull(data) { "Attestation data must be set" }
        
        return VerifierAttestation(
            format = format,
            data = data!!,
            credentialIds = credentialIds
        )
    }
}

/**
 * Build a verifier attestation using a type-safe builder DSL.
 *
 * Example:
 * ```kotlin
 * val attestation = buildVerifierAttestation {
 *     format(VerifierAttestationFormat.VERIFIER_ATTESTATION_JWT)
 *     data(attestationJwt)
 *     credentialIds("credential_query_1", "credential_query_2")
 * }
 * ```
 */
inline fun buildVerifierAttestation(block: VerifierAttestationBuilder.() -> Unit): VerifierAttestation {
    return VerifierAttestationBuilder().apply(block).build()
}

/**
 * Build a JWT verifier attestation.
 *
 * Example:
 * ```kotlin
 * val attestation = buildJwtVerifierAttestation(jwt = myAttestationJwt)
 * ```
 */
fun buildJwtVerifierAttestation(
    jwt: String,
    credentialIds: List<String>? = null
): VerifierAttestation = VerifierAttestation(
    format = VerifierAttestationFormat.VERIFIER_ATTESTATION_JWT,
    data = VerifierAttestationData.StringData(jwt),
    credentialIds = credentialIds
)

// =============================================================================
// Extension Functions
// =============================================================================

/**
 * Get the attestation data as a string, or null if it's an object.
 */
fun VerifierAttestationData.asStringOrNull(): String? = when (this) {
    is VerifierAttestationData.StringData -> value
    is VerifierAttestationData.ObjectData -> null
}

/**
 * Get the attestation data as a JSON object, or null if it's a string.
 */
fun VerifierAttestationData.asObjectOrNull(): JsonObject? = when (this) {
    is VerifierAttestationData.StringData -> null
    is VerifierAttestationData.ObjectData -> value
}

/**
 * Check if this attestation applies to a specific credential query ID.
 * If credentialIds is null, the attestation applies to all queries.
 */
fun VerifierAttestation.appliesTo(credentialQueryId: String): Boolean {
    return credentialIds == null || credentialQueryId in credentialIds
}

/**
 * Find all attestations that apply to a specific credential query ID.
 */
fun VerifierAttestations.filterByCredentialId(credentialQueryId: String): List<VerifierAttestation> {
    return filter { it.appliesTo(credentialQueryId) }
}
