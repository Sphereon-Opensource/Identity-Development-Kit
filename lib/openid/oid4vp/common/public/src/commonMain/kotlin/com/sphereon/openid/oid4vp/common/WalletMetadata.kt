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
import io.konform.validation.jsonschema.pattern
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

/**
 * Wallet Metadata for OpenID4VP
 *
 * Per OpenID4VP 1.0 Final Section 5.2, when using `request_uri_method=post`, the wallet
 * sends its metadata to the verifier in the POST request body. This allows the verifier
 * to tailor the authorization request based on the wallet's capabilities.
 *
 * The wallet metadata includes:
 * - VP formats supported by the wallet
 * - Algorithm support for various credential types
 * - Key proof types supported
 * - Optional wallet identification
 *
 * Reference: OpenID4VP 1.0 Final Section 5.2 and Section 9
 *
 * @property vpFormatsSupported VP formats supported by the wallet (REQUIRED)
 * @property clientIdSchemesSupported Client ID schemes supported by the wallet
 * @property requestObjectSigningAlgValuesSupported Algorithms supported for JAR verification
 * @property authorizationEncryptionAlgValuesSupported JWE key encryption algorithms supported
 * @property authorizationEncryptionEncValuesSupported JWE content encryption algorithms supported
 * @property walletName Optional display name for the wallet
 * @property walletUri Optional URI for the wallet (website/documentation)
 * @property logoUri Optional logo URI for the wallet
 * @property additionalParameters Additional extension parameters
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("WalletMetadata", exact = true)
@Serializable
@JsExportCompat
data class WalletMetadata(
    /**
     * VP formats supported by the wallet.
     *
     * Keys are format identifiers (e.g., "dc+sd-jwt", "mso_mdoc", "jwt_vp_json").
     * Values contain algorithm support information for each format.
     *
     * Required per OpenID4VP 1.0 Section 9.
     */
    @SerialName("vp_formats_supported")
    val vpFormatsSupported: Map<String, VpFormatSupport>,

    /**
     * Client ID schemes supported by the wallet.
     *
     * Indicates which client_id_scheme values the wallet supports.
     * Example: ["pre-registered", "redirect_uri", "x509_san_dns", "x509_san_uri"]
     */
    @SerialName("client_id_schemes_supported")
    val clientIdSchemesSupported: List<String>? = null,

    /**
     * JWS algorithms supported for JAR (JWT-secured Authorization Request) verification.
     *
     * Example: ["ES256", "ES384", "RS256"]
     */
    @SerialName("request_object_signing_alg_values_supported")
    val requestObjectSigningAlgValuesSupported: List<String>? = null,

    /**
     * JWE key encryption algorithms supported for encrypted authorization responses.
     *
     * Example: ["ECDH-ES+A256KW", "RSA-OAEP-256"]
     */
    @SerialName("authorization_encryption_alg_values_supported")
    val authorizationEncryptionAlgValuesSupported: List<String>? = null,

    /**
     * JWE content encryption algorithms supported for encrypted authorization responses.
     *
     * Example: ["A256GCM", "A128CBC-HS256"]
     */
    @SerialName("authorization_encryption_enc_values_supported")
    val authorizationEncryptionEncValuesSupported: List<String>? = null,

    /**
     * Optional display name for the wallet.
     */
    @SerialName("wallet_name")
    val walletName: String? = null,

    /**
     * Optional URI for the wallet (e.g., website or documentation).
     */
    @SerialName("wallet_uri")
    val walletUri: String? = null,

    /**
     * Optional logo URI for the wallet.
     */
    @SerialName("logo_uri")
    val logoUri: String? = null,

    /**
     * Additional extension parameters.
     *
     * Allows for future extensibility without breaking changes.
     */
    val additionalParameters: Map<String, JsonElement> = emptyMap()
)

/**
 * VP Format Support information for a specific credential format.
 *
 * Per OpenID4VP 1.0 Section 9, this describes the wallet's capabilities
 * for a specific VP format.
 *
 * @property sdJwtAlgValuesSupported JWS algorithms supported for SD-JWT (for dc+sd-jwt format)
 * @property kbJwtAlgValuesSupported JWS algorithms for Key Binding JWT (for dc+sd-jwt format)
 * @property algValuesSupported General JWS algorithms supported (for JWT formats)
 * @property proofTypesSupported Proof types supported (for LDP formats)
 * @property issuerAuthAlgValuesSupported COSE algorithms for IssuerAuth (for mso_mdoc format)
 * @property deviceAuthAlgValuesSupported COSE algorithms for DeviceAuth (for mso_mdoc format)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VpFormatSupport", exact = true)
@Serializable
@JsExportCompat
data class VpFormatSupport(
    /**
     * JWS algorithms supported for SD-JWT issuer signature.
     * Applicable to "dc+sd-jwt" and "vc+sd-jwt" formats.
     */
    @SerialName("sd-jwt_alg_values")
    val sdJwtAlgValuesSupported: List<String>? = null,

    /**
     * JWS algorithms supported for Key Binding JWT.
     * Applicable to "dc+sd-jwt" and "vc+sd-jwt" formats.
     */
    @SerialName("kb-jwt_alg_values")
    val kbJwtAlgValuesSupported: List<String>? = null,

    /**
     * General JWS algorithms supported.
     * Applicable to "jwt_vp_json" and similar JWT formats.
     */
    @SerialName("alg_values")
    val algValuesSupported: List<String>? = null,

    /**
     * Proof types supported for Linked Data Proofs.
     * Applicable to "ldp_vp" and similar LD formats.
     */
    @SerialName("proof_types_supported")
    val proofTypesSupported: List<String>? = null,

    /**
     * COSE algorithms supported for IssuerAuth in mdoc.
     * Values are COSE algorithm numbers (e.g., -7 for ES256).
     * Applicable to "mso_mdoc" format.
     */
    @SerialName("issuerauth_alg_values")
    val issuerAuthAlgValuesSupported: List<Int>? = null,

    /**
     * COSE algorithms supported for DeviceAuth in mdoc.
     * Values are COSE algorithm numbers (e.g., -7 for ES256).
     * Applicable to "mso_mdoc" format.
     */
    @SerialName("deviceauth_alg_values")
    val deviceAuthAlgValuesSupported: List<Int>? = null
)

/**
 * Konform validator for VpFormatSupport
 */
val validateVpFormatSupport = Validation<VpFormatSupport> {
    // At least one algorithm type must be specified
    constrain("At least one algorithm specification is required") {
        !it.sdJwtAlgValuesSupported.isNullOrEmpty() ||
        !it.kbJwtAlgValuesSupported.isNullOrEmpty() ||
        !it.algValuesSupported.isNullOrEmpty() ||
        !it.proofTypesSupported.isNullOrEmpty() ||
        !it.issuerAuthAlgValuesSupported.isNullOrEmpty() ||
        !it.deviceAuthAlgValuesSupported.isNullOrEmpty()
    }

    // Validate algorithm format if present (simple constraint-based validation)
    constrain("sd-jwt_alg_values must contain valid JWS algorithm identifiers") {
        it.sdJwtAlgValuesSupported?.all { alg -> alg.matches("[A-Z0-9-]+".toRegex()) } ?: true
    }

    constrain("kb-jwt_alg_values must contain valid JWS algorithm identifiers") {
        it.kbJwtAlgValuesSupported?.all { alg -> alg.matches("[A-Z0-9-]+".toRegex()) } ?: true
    }

    constrain("alg_values must contain valid JWS algorithm identifiers") {
        it.algValuesSupported?.all { alg -> alg.matches("[A-Z0-9-]+".toRegex()) } ?: true
    }
}

/**
 * Valid client_id_scheme values per OpenID4VP 1.0 Final
 */
private val validClientIdSchemes = setOf(
    "pre-registered", "redirect_uri", "x509_san_dns", "x509_san_uri",
    "x509_hash", "verifier_attestation", "decentralized_identifier",
    "openid_federation"
)

/**
 * Konform validator for WalletMetadata
 */
val validateWalletMetadata = Validation<WalletMetadata> {
    // vpFormatsSupported is required and must have at least one format
    WalletMetadata::vpFormatsSupported {
        constrain("vp_formats_supported must contain at least one format") {
            it.isNotEmpty()
        }
        // Validate each entry's value
        constrain("All VP format support entries must be valid") { formats ->
            formats.values.all { support ->
                validateVpFormatSupport(support).isValid
            }
        }
    }

    // Validate client_id_schemes_supported format if present
    constrain("client_id_schemes_supported must not be empty if specified") {
        it.clientIdSchemesSupported?.isNotEmpty() ?: true
    }

    constrain("client_id_schemes_supported must contain valid scheme values") {
        it.clientIdSchemesSupported?.all { scheme -> scheme in validClientIdSchemes } ?: true
    }

    // Validate algorithm lists if present
    constrain("request_object_signing_alg_values_supported must not be empty if specified") {
        it.requestObjectSigningAlgValuesSupported?.isNotEmpty() ?: true
    }

    constrain("authorization_encryption_alg_values_supported must not be empty if specified") {
        it.authorizationEncryptionAlgValuesSupported?.isNotEmpty() ?: true
    }

    // Validate URIs if present
    WalletMetadata::walletUri ifPresent {
        pattern("^https?://.*".toRegex()) hint "wallet_uri must be a valid HTTP(S) URL"
    }

    WalletMetadata::logoUri ifPresent {
        pattern("^https?://.*".toRegex()) hint "logo_uri must be a valid HTTP(S) URL"
    }
}

/**
 * Request body for POST request_uri method.
 *
 * Per OpenID4VP 1.0 Section 5.2, when `request_uri_method=post`, the wallet
 * sends its metadata and nonce in the POST request body.
 *
 * @property walletMetadata The wallet's metadata
 * @property walletNonce Optional nonce for replay protection (provided by verifier)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("WalletRequestUriPostBody", exact = true)
@Serializable
@JsExportCompat
data class WalletRequestUriPostBody(
    @SerialName("wallet_metadata")
    val walletMetadata: WalletMetadata? = null,

    @SerialName("wallet_nonce")
    val walletNonce: String? = null
)

/**
 * Konform validator for WalletRequestUriPostBody
 */
val validateWalletRequestUriPostBody = Validation<WalletRequestUriPostBody> {
    // At least one of wallet_metadata or wallet_nonce should be present
    // (though wallet_metadata is the primary purpose)
    
    // Validate wallet_metadata if present
    WalletRequestUriPostBody::walletMetadata ifPresent {
        run(validateWalletMetadata)
    }

    // Validate wallet_nonce format if present
    WalletRequestUriPostBody::walletNonce ifPresent {
        constrain("wallet_nonce cannot be empty") { it.isNotBlank() }
        constrain("wallet_nonce should be sufficiently random (at least 16 characters)") {
            it.length >= 16
        }
    }
}

/**
 * Builder for creating WalletMetadata instances.
 *
 * Provides a convenient DSL for configuring wallet metadata.
 */
class WalletMetadataBuilder {
    private val vpFormatsSupported = mutableMapOf<String, VpFormatSupport>()
    private var clientIdSchemesSupported: MutableList<ClientIdScheme>? = null
    private var requestObjectSigningAlgValuesSupported: MutableList<String>? = null
    private var authorizationEncryptionAlgValuesSupported: MutableList<String>? = null
    private var authorizationEncryptionEncValuesSupported: MutableList<String>? = null
    private var walletName: String? = null
    private var walletUri: String? = null
    private var logoUri: String? = null

    /**
     * Add support for SD-JWT DC format.
     */
    fun supportSdJwtDc(
        sdJwtAlgValues: List<String> = listOf("ES256", "ES384"),
        kbJwtAlgValues: List<String> = listOf("ES256", "ES384")
    ) = apply {
        vpFormatsSupported["dc+sd-jwt"] = VpFormatSupport(
            sdJwtAlgValuesSupported = sdJwtAlgValues,
            kbJwtAlgValuesSupported = kbJwtAlgValues
        )
    }

    /**
     * Add support for mso_mdoc format.
     */
    fun supportMsoMdoc(
        issuerAuthAlgValues: List<Int> = listOf(-7, -35, -36), // ES256, ES384, ES512
        deviceAuthAlgValues: List<Int> = listOf(-7, -35, -36)
    ) = apply {
        vpFormatsSupported["mso_mdoc"] = VpFormatSupport(
            issuerAuthAlgValuesSupported = issuerAuthAlgValues,
            deviceAuthAlgValuesSupported = deviceAuthAlgValues
        )
    }

    /**
     * Add support for JWT VP format.
     */
    fun supportJwtVpJson(
        algValues: List<String> = listOf("ES256", "ES384", "RS256")
    ) = apply {
        vpFormatsSupported["jwt_vp_json"] = VpFormatSupport(
            algValuesSupported = algValues
        )
    }

    /**
     * Add support for a custom VP format.
     */
    fun supportFormat(format: String, support: VpFormatSupport) = apply {
        vpFormatsSupported[format] = support
    }

    /**
     * Set supported client ID schemes using the type-safe enum.
     *
     * @param schemes The ClientIdScheme values the wallet supports
     */
    fun clientIdSchemes(vararg schemes: ClientIdScheme) = apply {
        clientIdSchemesSupported = schemes.toMutableList()
    }

    /**
     * Set supported JAR signing algorithms.
     */
    fun requestObjectSigningAlgs(vararg algs: String) = apply {
        requestObjectSigningAlgValuesSupported = algs.toMutableList()
    }

    /**
     * Set supported JWE key encryption algorithms.
     */
    fun authorizationEncryptionAlgs(vararg algs: String) = apply {
        authorizationEncryptionAlgValuesSupported = algs.toMutableList()
    }

    /**
     * Set supported JWE content encryption algorithms.
     */
    fun authorizationEncryptionEncs(vararg encs: String) = apply {
        authorizationEncryptionEncValuesSupported = encs.toMutableList()
    }

    /**
     * Set wallet display name.
     */
    fun walletName(name: String) = apply {
        this.walletName = name
    }

    /**
     * Set wallet URI.
     */
    fun walletUri(uri: String) = apply {
        this.walletUri = uri
    }

    /**
     * Set wallet logo URI.
     */
    fun logoUri(uri: String) = apply {
        this.logoUri = uri
    }

    /**
     * Build the WalletMetadata instance.
     */
    fun build(): WalletMetadata {
        require(vpFormatsSupported.isNotEmpty()) {
            "At least one VP format must be supported"
        }
        return WalletMetadata(
            vpFormatsSupported = vpFormatsSupported.toMap(),
            // Convert ClientIdScheme enums to their prefix strings for JSON serialization
            clientIdSchemesSupported = clientIdSchemesSupported?.mapNotNull { scheme ->
                // PRE_REGISTERED has null prefix, use "pre-registered" string
                scheme.prefix ?: "pre-registered"
            },
            requestObjectSigningAlgValuesSupported = requestObjectSigningAlgValuesSupported?.toList(),
            authorizationEncryptionAlgValuesSupported = authorizationEncryptionAlgValuesSupported?.toList(),
            authorizationEncryptionEncValuesSupported = authorizationEncryptionEncValuesSupported?.toList(),
            walletName = walletName,
            walletUri = walletUri,
            logoUri = logoUri
        )
    }
}

/**
 * Create WalletMetadata using a builder DSL.
 *
 * Example:
 * ```kotlin
 * val metadata = buildWalletMetadata {
 *     supportSdJwtDc()
 *     supportMsoMdoc()
 *     walletName("My Wallet")
 *     clientIdSchemes(
 *         ClientIdScheme.PRE_REGISTERED,
 *         ClientIdScheme.REDIRECT_URI,
 *         ClientIdScheme.X509_SAN_DNS
 *     )
 *     requestObjectSigningAlgs("ES256", "ES384")
 * }
 * ```
 */
fun buildWalletMetadata(block: WalletMetadataBuilder.() -> Unit): WalletMetadata {
    return WalletMetadataBuilder().apply(block).build()
}
