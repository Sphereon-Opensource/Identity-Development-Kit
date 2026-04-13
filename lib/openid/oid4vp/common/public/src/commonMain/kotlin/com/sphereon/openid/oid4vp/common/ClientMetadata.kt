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

package com.sphereon.openid.oid4vp.common

import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.ClientRegistration
import com.sphereon.oauth2.common.model.ClientType
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.ResponseType
import com.sphereon.oauth2.common.model.validateClientRegistration
import io.konform.validation.Validation
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Custom serializer for ClientMetadata that properly handles composition with ClientRegistration
 */
internal object ClientMetadataSerializer : KSerializer<ClientMetadata> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ClientMetadata")

    private val oid4vpSpecificKeys =
        setOf(
            "vp_formats",
            "client_purpose",
            "authorization_signed_response_alg",
            "authorization_encrypted_response_alg",
            "authorization_encrypted_response_enc",
        )

    override fun serialize(
        encoder: Encoder,
        value: ClientMetadata,
    ) {
        require(encoder is JsonEncoder) { "ClientMetadataSerializer only works with JSON format" }

        // First encode the OAuth2/RFC 7591 base
        val oauth2Json = encoder.json.encodeToJsonElement(value.baseMetadata).jsonObject

        // Then add OID4VP-specific fields
        val jsonObject =
            buildJsonObject {
                // Copy all OAuth2/RFC 7591 fields
                oauth2Json.forEach { (key, jsonValue) ->
                    put(key, jsonValue)
                }

                // Add OID4VP extensions
                value.vpFormats?.let { put("vp_formats", encoder.json.encodeToJsonElement(it)) }
                value.clientPurpose?.let { put("client_purpose", JsonPrimitive(it)) }
                value.authorizationSignedResponseAlg?.let { put("authorization_signed_response_alg", JsonPrimitive(it)) }
                value.authorizationEncryptedResponseAlg?.let { put("authorization_encrypted_response_alg", JsonPrimitive(it)) }
                value.authorizationEncryptedResponseEnc?.let { put("authorization_encrypted_response_enc", JsonPrimitive(it)) }
            }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): ClientMetadata {
        require(decoder is JsonDecoder) { "ClientMetadataSerializer only works with JSON format" }

        val jsonObject = decoder.decodeJsonElement().jsonObject

        // Separate OID4VP-specific fields from RFC 7591 base fields
        val oid4vpFields = jsonObject.filterKeys { it in oid4vpSpecificKeys }
        val oauth2Fields = jsonObject.filterKeys { it !in oid4vpSpecificKeys }

        // Decode RFC 7591 base
        val baseMetadata = decoder.json.decodeFromJsonElement<ClientRegistration>(JsonObject(oauth2Fields))

        // Decode OID4VP extensions
        val vpFormats =
            oid4vpFields["vp_formats"]?.let {
                decoder.json.decodeFromJsonElement<Map<String, VpFormatInfo>>(it)
            }

        return ClientMetadata(
            baseMetadata = baseMetadata,
            vpFormats = vpFormats,
            clientPurpose = oid4vpFields["client_purpose"]?.let { decoder.json.decodeFromJsonElement(it) },
            authorizationSignedResponseAlg =
                oid4vpFields["authorization_signed_response_alg"]?.let {
                    decoder.json.decodeFromJsonElement(it)
                },
            authorizationEncryptedResponseAlg =
                oid4vpFields["authorization_encrypted_response_alg"]?.let {
                    decoder.json.decodeFromJsonElement(it)
                },
            authorizationEncryptedResponseEnc =
                oid4vpFields["authorization_encrypted_response_enc"]?.let {
                    decoder.json.decodeFromJsonElement(it)
                },
        )
    }
}

/**
 * Client Metadata for OpenID4VP
 *
 * Based on:
 * - RFC 7591: OAuth 2.0 Dynamic Client Registration Protocol (Section 2)
 * - OpenID4VP 1.0 Section 5.5: Client Metadata
 *
 * This class extends OAuth 2.0 client metadata (RFC 7591) with OpenID4VP-specific
 * extensions for verifiable presentation flows.
 *
 * ## RFC 7591 Base Fields (via baseMetadata)
 * All standard OAuth 2.0 client metadata fields from RFC 7591 Section 2:
 * - redirect_uris, token_endpoint_auth_method, grant_types, response_types
 * - client_name, client_uri, logo_uri, scope, contacts
 * - tos_uri, policy_uri, jwks_uri, jwks
 * - software_id, software_version, software_statement
 * - additionalParameters (for unknown extension fields)
 *
 * ## OpenID4VP 1.0 Extensions (Section 5.5)
 * - vp_formats: VP formats supported by the verifier
 * - client_purpose: Purpose for requesting credentials
 * - authorization_signed_response_alg: JWS alg for signing authorization response (JARM)
 * - authorization_encrypted_response_alg: JWE alg for encrypting authorization response (JARM)
 * - authorization_encrypted_response_enc: JWE enc for encrypting authorization response (JARM)
 *
 * @property baseMetadata OAuth 2.0/RFC 7591 base client metadata
 * @property vpFormats VP formats supported by the verifier (OpenID4VP extension)
 * @property clientPurpose Purpose for requesting credentials (OpenID4VP extension)
 * @property authorizationSignedResponseAlg JWS alg for signed responses (JARM - OpenID4VP Section 8.4)
 * @property authorizationEncryptedResponseAlg JWE alg for encrypted responses (JARM - OpenID4VP Section 8.4)
 * @property authorizationEncryptedResponseEnc JWE enc for encrypted responses (JARM - OpenID4VP Section 8.4)
 */
@Serializable(with = ClientMetadataSerializer::class)
data class ClientMetadata(
    val baseMetadata: ClientRegistration,
    @SerialName("vp_formats")
    val vpFormats: Map<String, VpFormatInfo>? = null,
    @SerialName("client_purpose")
    val clientPurpose: String? = null,
    @SerialName("authorization_signed_response_alg")
    val authorizationSignedResponseAlg: String? = null,
    @SerialName("authorization_encrypted_response_alg")
    val authorizationEncryptedResponseAlg: String? = null,
    @SerialName("authorization_encrypted_response_enc")
    val authorizationEncryptedResponseEnc: String? = null,
) {
    // Delegate common OAuth2/RFC 7591 properties for convenience
    val clientId: String get() = baseMetadata.clientId
    val clientSecret: String? get() = baseMetadata.clientSecret
    val clientName: String? get() = baseMetadata.clientName
    val clientUri: String? get() = baseMetadata.clientUri
    val logoUri: String? get() = baseMetadata.logoUri
    val clientType: ClientType get() = baseMetadata.clientType
    val grantTypes: List<GrantType> get() = baseMetadata.grantTypes
    val responseTypes: List<ResponseType> get() = baseMetadata.responseTypes
    val redirectUris: List<String> get() = baseMetadata.redirectUris
    val allowedScopes: List<String>? get() = baseMetadata.allowedScopes
    val scope: String? get() = baseMetadata.scope
    val tokenEndpointAuthMethod: ClientAuthenticationMethod get() = baseMetadata.tokenEndpointAuthMethod
    val jwks: JwkSet? get() = baseMetadata.jwks
    val jwksUri: String? get() = baseMetadata.jwksUri
    val contacts: List<String>? get() = baseMetadata.contacts
    val tosUri: String? get() = baseMetadata.tosUri
    val policyUri: String? get() = baseMetadata.policyUri
    val softwareId: String? get() = baseMetadata.softwareId
    val softwareVersion: String? get() = baseMetadata.softwareVersion
    val softwareStatement: String? get() = baseMetadata.softwareStatement
    val additionalParameters: Map<String, JsonElement> get() = baseMetadata.additionalParameters
}

/**
 * VP Format Information (vp_formats_supported)
 *
 * Per OpenID4VP 1.0 Final Section 9.1, describes the VP formats and algorithms
 * supported by a verifier (client). This is used in client_metadata to indicate
 * which credential formats the verifier can accept.
 *
 * Different credential formats have different algorithm fields:
 * - **dc+sd-jwt** / **vc+sd-jwt**: Uses `sd-jwt_alg_values` and `kb-jwt_alg_values`
 * - **mso_mdoc**: Uses `issuerauth_alg_values` and `deviceauth_alg_values` (COSE algorithm numbers)
 * - **jwt_vp_json** / **jwt_vc_json**: Uses `alg_values` (JWS algorithms)
 * - **ldp_vp** / **ldp_vc**: Uses `proof_types_supported` (Linked Data Proof types)
 *
 * Reference: OpenID4VP 1.0 Final Section 9.1
 *
 * @property sdJwtAlgValuesSupported JWS algorithms for SD-JWT issuer signature (dc+sd-jwt, vc+sd-jwt)
 * @property kbJwtAlgValuesSupported JWS algorithms for Key Binding JWT (dc+sd-jwt, vc+sd-jwt)
 * @property algValuesSupported General JWS algorithms (jwt_vp_json, jwt_vc_json)
 * @property proofTypesSupported Linked Data Proof types (ldp_vp, ldp_vc)
 * @property issuerAuthAlgValuesSupported COSE algorithms for IssuerAuth in mdoc (mso_mdoc)
 * @property deviceAuthAlgValuesSupported COSE algorithms for DeviceAuth in mdoc (mso_mdoc)
 */
@Serializable
data class VpFormatInfo(
    /**
     * JWS algorithms supported for SD-JWT issuer signature.
     * Applicable to "dc+sd-jwt" and "vc+sd-jwt" formats.
     * Example: ["ES256", "ES384", "ES512"]
     */
    @SerialName("sd-jwt_alg_values")
    val sdJwtAlgValuesSupported: List<String>? = null,
    /**
     * JWS algorithms supported for Key Binding JWT.
     * Applicable to "dc+sd-jwt" and "vc+sd-jwt" formats.
     * Example: ["ES256", "ES384"]
     */
    @SerialName("kb-jwt_alg_values")
    val kbJwtAlgValuesSupported: List<String>? = null,
    /**
     * General JWS algorithms supported.
     * Applicable to "jwt_vp_json", "jwt_vc_json" and similar JWT formats.
     * Example: ["ES256", "ES384", "RS256"]
     */
    @SerialName("alg_values")
    val algValuesSupported: List<String>? = null,
    /**
     * Proof types supported for Linked Data Proofs.
     * Applicable to "ldp_vp", "ldp_vc" and similar LD formats.
     * Example: ["Ed25519Signature2018", "JsonWebSignature2020"]
     */
    @SerialName("proof_types_supported")
    val proofTypesSupported: List<String>? = null,
    /**
     * COSE algorithms supported for IssuerAuth in mdoc.
     * Values are COSE algorithm numbers (e.g., -7 for ES256, -35 for ES384, -36 for ES512).
     * Applicable to "mso_mdoc" format.
     */
    @SerialName("issuerauth_alg_values")
    val issuerAuthAlgValuesSupported: List<Int>? = null,
    /**
     * COSE algorithms supported for DeviceAuth in mdoc.
     * Values are COSE algorithm numbers (e.g., -7 for ES256, -35 for ES384, -36 for ES512).
     * Applicable to "mso_mdoc" format.
     */
    @SerialName("deviceauth_alg_values")
    val deviceAuthAlgValuesSupported: List<Int>? = null,
)

/**
 * Konform validator for VpFormatInfo
 *
 * Validates that at least one algorithm specification is provided and
 * that algorithm identifiers follow expected formats.
 */
val validateVpFormatInfo =
    Validation<VpFormatInfo> {
        // At least one algorithm type must be specified
        constrain("At least one algorithm specification is required") {
            !it.sdJwtAlgValuesSupported.isNullOrEmpty() ||
                !it.kbJwtAlgValuesSupported.isNullOrEmpty() ||
                !it.algValuesSupported.isNullOrEmpty() ||
                !it.proofTypesSupported.isNullOrEmpty() ||
                !it.issuerAuthAlgValuesSupported.isNullOrEmpty() ||
                !it.deviceAuthAlgValuesSupported.isNullOrEmpty()
        }

        // Validate JWS algorithm formats (should be uppercase alphanumeric with hyphens)
        constrain("sd-jwt_alg_values must contain valid JWS algorithm identifiers") {
            it.sdJwtAlgValuesSupported?.all { alg -> alg.matches("[A-Z][A-Z0-9-]*".toRegex()) } ?: true
        }

        constrain("kb-jwt_alg_values must contain valid JWS algorithm identifiers") {
            it.kbJwtAlgValuesSupported?.all { alg -> alg.matches("[A-Z][A-Z0-9-]*".toRegex()) } ?: true
        }

        constrain("alg_values must contain valid JWS algorithm identifiers") {
            it.algValuesSupported?.all { alg -> alg.matches("[A-Z][A-Z0-9-]*".toRegex()) } ?: true
        }

        // Validate lists are not empty if present
        constrain("sd-jwt_alg_values must not be empty if specified") {
            it.sdJwtAlgValuesSupported?.isNotEmpty() ?: true
        }

        constrain("kb-jwt_alg_values must not be empty if specified") {
            it.kbJwtAlgValuesSupported?.isNotEmpty() ?: true
        }

        constrain("alg_values must not be empty if specified") {
            it.algValuesSupported?.isNotEmpty() ?: true
        }

        constrain("proof_types_supported must not be empty if specified") {
            it.proofTypesSupported?.isNotEmpty() ?: true
        }

        constrain("issuerauth_alg_values must not be empty if specified") {
            it.issuerAuthAlgValuesSupported?.isNotEmpty() ?: true
        }

        constrain("deviceauth_alg_values must not be empty if specified") {
            it.deviceAuthAlgValuesSupported?.isNotEmpty() ?: true
        }
    }

/**
 * Konform validator for ClientMetadata
 *
 * Validates both RFC 7591 base fields and OpenID4VP extensions.
 */
val validateClientMetadata =
    Validation<ClientMetadata> {
        // Validate OAuth2/RFC 7591 base metadata
        ClientMetadata::baseMetadata {
            run(validateClientRegistration)
        }

        // OpenID4VP: client_purpose cannot be empty
        ClientMetadata::clientPurpose ifPresent {
            constrain("client_purpose cannot be empty") { it.isNotEmpty() }
        }

        // OpenID4VP: vp_formats must have at least one format if present
        ClientMetadata::vpFormats ifPresent {
            run {
                constrain("vp_formats must contain at least one format") {
                    it.isNotEmpty()
                }
                // Validate each format entry
                constrain("All VP format entries must be valid") { formats ->
                    formats.values.all { formatInfo ->
                        validateVpFormatInfo(formatInfo).isValid
                    }
                }
            }
        }
    }

// =============================================================================
// Builder DSL for VpFormatInfo
// =============================================================================

/**
 * Builder for creating VpFormatInfo instances.
 *
 * Provides a convenient DSL for configuring VP format algorithm support.
 */
class VpFormatInfoBuilder {
    private var sdJwtAlgValues: MutableList<String>? = null
    private var kbJwtAlgValues: MutableList<String>? = null
    private var algValues: MutableList<String>? = null
    private var proofTypes: MutableList<String>? = null
    private var issuerAuthAlgValues: MutableList<Int>? = null
    private var deviceAuthAlgValues: MutableList<Int>? = null

    /**
     * Set SD-JWT signing algorithms.
     */
    fun sdJwtAlgValues(vararg algs: String) =
        apply {
            sdJwtAlgValues = algs.toMutableList()
        }

    /**
     * Set Key Binding JWT algorithms.
     */
    fun kbJwtAlgValues(vararg algs: String) =
        apply {
            kbJwtAlgValues = algs.toMutableList()
        }

    /**
     * Set general JWS algorithms.
     */
    fun algValues(vararg algs: String) =
        apply {
            algValues = algs.toMutableList()
        }

    /**
     * Set Linked Data Proof types.
     */
    fun proofTypes(vararg types: String) =
        apply {
            proofTypes = types.toMutableList()
        }

    /**
     * Set COSE algorithms for mdoc IssuerAuth.
     */
    fun issuerAuthAlgValues(vararg algs: Int) =
        apply {
            issuerAuthAlgValues = algs.toMutableList()
        }

    /**
     * Set COSE algorithms for mdoc DeviceAuth.
     */
    fun deviceAuthAlgValues(vararg algs: Int) =
        apply {
            deviceAuthAlgValues = algs.toMutableList()
        }

    fun build(): VpFormatInfo =
        VpFormatInfo(
            sdJwtAlgValuesSupported = sdJwtAlgValues?.toList(),
            kbJwtAlgValuesSupported = kbJwtAlgValues?.toList(),
            algValuesSupported = algValues?.toList(),
            proofTypesSupported = proofTypes?.toList(),
            issuerAuthAlgValuesSupported = issuerAuthAlgValues?.toList(),
            deviceAuthAlgValuesSupported = deviceAuthAlgValues?.toList(),
        )
}

/**
 * Create VpFormatInfo using a builder DSL.
 */
fun buildVpFormatInfo(block: VpFormatInfoBuilder.() -> Unit): VpFormatInfo = VpFormatInfoBuilder().apply(block).build()

// =============================================================================
// Convenience factory functions for common VP format configurations
// =============================================================================

/**
 * Create VpFormatInfo for SD-JWT format (dc+sd-jwt, vc+sd-jwt).
 *
 * @param sdJwtAlgValues JWS algorithms for SD-JWT issuer signature (default: ES256, ES384)
 * @param kbJwtAlgValues JWS algorithms for Key Binding JWT (default: ES256, ES384)
 */
fun sdJwtVpFormatInfo(
    sdJwtAlgValues: List<String> = listOf("ES256", "ES384"),
    kbJwtAlgValues: List<String> = listOf("ES256", "ES384"),
): VpFormatInfo =
    VpFormatInfo(
        sdJwtAlgValuesSupported = sdJwtAlgValues,
        kbJwtAlgValuesSupported = kbJwtAlgValues,
    )

/**
 * Create VpFormatInfo for mso_mdoc format.
 *
 * @param issuerAuthAlgValues COSE algorithms for IssuerAuth (default: -7 (ES256), -35 (ES384), -36 (ES512))
 * @param deviceAuthAlgValues COSE algorithms for DeviceAuth (default: -7 (ES256), -35 (ES384), -36 (ES512))
 */
internal const val COSE_ALG_ES256 = -7
internal const val COSE_ALG_ES384 = -35
internal const val COSE_ALG_ES512 = -36
internal val DEFAULT_COSE_ALG_VALUES = listOf(COSE_ALG_ES256, COSE_ALG_ES384, COSE_ALG_ES512)

fun mdocVpFormatInfo(
    issuerAuthAlgValues: List<Int> = DEFAULT_COSE_ALG_VALUES,
    deviceAuthAlgValues: List<Int> = DEFAULT_COSE_ALG_VALUES,
): VpFormatInfo =
    VpFormatInfo(
        issuerAuthAlgValuesSupported = issuerAuthAlgValues,
        deviceAuthAlgValuesSupported = deviceAuthAlgValues,
    )

/**
 * Create VpFormatInfo for JWT VP format (jwt_vp_json, jwt_vc_json).
 *
 * @param algValues JWS algorithms supported (default: ES256, ES384, RS256)
 */
fun jwtVpFormatInfo(algValues: List<String> = listOf("ES256", "ES384", "RS256")): VpFormatInfo =
    VpFormatInfo(
        algValuesSupported = algValues,
    )

/**
 * Create VpFormatInfo for Linked Data Proof format (ldp_vp, ldp_vc).
 *
 * @param proofTypes Proof types supported (default: Ed25519Signature2018, JsonWebSignature2020)
 */
fun ldpVpFormatInfo(proofTypes: List<String> = listOf("Ed25519Signature2018", "JsonWebSignature2020")): VpFormatInfo =
    VpFormatInfo(
        proofTypesSupported = proofTypes,
    )

// =============================================================================
// Builder DSL for ClientMetadata VP formats
// =============================================================================

/**
 * Builder for VP formats map in ClientMetadata.
 *
 * Provides a convenient DSL for configuring which VP formats a verifier supports.
 */
class VpFormatsBuilder {
    private val formats = mutableMapOf<String, VpFormatInfo>()

    /**
     * Add support for SD-JWT DC format (dc+sd-jwt).
     */
    fun sdJwtDc(
        sdJwtAlgValues: List<String> = listOf("ES256", "ES384"),
        kbJwtAlgValues: List<String> = listOf("ES256", "ES384"),
    ) = apply {
        formats["dc+sd-jwt"] = sdJwtVpFormatInfo(sdJwtAlgValues, kbJwtAlgValues)
    }

    /**
     * Add support for SD-JWT VC format (vc+sd-jwt).
     */
    fun sdJwtVc(
        sdJwtAlgValues: List<String> = listOf("ES256", "ES384"),
        kbJwtAlgValues: List<String> = listOf("ES256", "ES384"),
    ) = apply {
        formats["vc+sd-jwt"] = sdJwtVpFormatInfo(sdJwtAlgValues, kbJwtAlgValues)
    }

    /**
     * Add support for mso_mdoc format.
     */
    fun msoMdoc(
        issuerAuthAlgValues: List<Int> = DEFAULT_COSE_ALG_VALUES,
        deviceAuthAlgValues: List<Int> = DEFAULT_COSE_ALG_VALUES,
    ) = apply {
        formats["mso_mdoc"] = mdocVpFormatInfo(issuerAuthAlgValues, deviceAuthAlgValues)
    }

    /**
     * Add support for jwt_vp_json format.
     */
    fun jwtVpJson(algValues: List<String> = listOf("ES256", "ES384", "RS256")) =
        apply {
            formats["jwt_vp_json"] = jwtVpFormatInfo(algValues)
        }

    /**
     * Add support for jwt_vc_json format.
     */
    fun jwtVcJson(algValues: List<String> = listOf("ES256", "ES384", "RS256")) =
        apply {
            formats["jwt_vc_json"] = jwtVpFormatInfo(algValues)
        }

    /**
     * Add support for ldp_vp format.
     */
    fun ldpVp(proofTypes: List<String> = listOf("Ed25519Signature2018", "JsonWebSignature2020")) =
        apply {
            formats["ldp_vp"] = ldpVpFormatInfo(proofTypes)
        }

    /**
     * Add support for ldp_vc format.
     */
    fun ldpVc(proofTypes: List<String> = listOf("Ed25519Signature2018", "JsonWebSignature2020")) =
        apply {
            formats["ldp_vc"] = ldpVpFormatInfo(proofTypes)
        }

    /**
     * Add support for a custom format.
     */
    fun format(
        name: String,
        info: VpFormatInfo,
    ) = apply {
        formats[name] = info
    }

    /**
     * Add support for a custom format using a builder DSL.
     */
    fun format(
        name: String,
        block: VpFormatInfoBuilder.() -> Unit,
    ) = apply {
        formats[name] = buildVpFormatInfo(block)
    }

    fun build(): Map<String, VpFormatInfo> = formats.toMap()
}

/**
 * Create a VP formats map using a builder DSL.
 *
 * Example:
 * ```kotlin
 * val vpFormats = buildVpFormats {
 *     sdJwtDc()
 *     msoMdoc()
 *     jwtVpJson(listOf("ES256"))
 * }
 * ```
 */
fun buildVpFormats(block: VpFormatsBuilder.() -> Unit): Map<String, VpFormatInfo> = VpFormatsBuilder().apply(block).build()
