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

package com.sphereon.openid.oid4vci.common.dsl

import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vc.common.ImageProperties
import com.sphereon.openid.oid4vc.common.LogoProperties
import com.sphereon.openid.oid4vc.common.ProofType
import com.sphereon.openid.oid4vci.common.model.BatchCredentialIssuance
import com.sphereon.openid.oid4vci.common.model.ClaimDisplay
import com.sphereon.openid.oid4vci.common.model.ClaimMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialDefinition
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialMetadataClaim
import com.sphereon.openid.oid4vci.common.model.CredentialResponseEncryption
import com.sphereon.openid.oid4vci.common.model.KeyAttestationsRequired
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialResponseEncryption
import com.sphereon.openid.oid4vci.common.model.ProofTypeSupported
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * DSL marker to prevent receiver leaking between nested builders.
 */
@DslMarker
annotation class Oid4vciDsl

// ---------------------------------------------------------------------------
// Top-level entry points
// ---------------------------------------------------------------------------

/**
 * Build a [CredentialIssuerMetadata] using the DSL.
 *
 * The [issuerUrl] is used to derive default endpoint URLs
 * (`<issuerUrl>/credential`, `<issuerUrl>/nonce`).
 * Override any endpoint by setting it explicitly inside the builder block.
 *
 * Example:
 * ```kotlin
 * val metadata = issuerMetadata("https://issuer.example.com") {
 *     authorizationServer("https://auth.example.com")
 *     credentialConfiguration("MyCredential", CredentialFormat.SD_JWT_DC) {
 *         vct = "https://credentials.example.com/my"
 *         display { name = "My Credential" }
 *     }
 * }
 * ```
 */
fun issuerMetadata(
    issuerUrl: String,
    builder: IssuerMetadataBuilder.() -> Unit,
): CredentialIssuerMetadata = IssuerMetadataBuilder(issuerUrl).apply(builder).build()

/**
 * Build a standalone [CredentialConfigurationSupported] using the DSL.
 *
 * Useful when assembling a map of configurations outside a full metadata block.
 */
fun credentialConfiguration(
    format: CredentialFormat,
    builder: CredentialConfigurationBuilder.() -> Unit,
): CredentialConfigurationSupported = CredentialConfigurationBuilder(format).apply(builder).build()

// ---------------------------------------------------------------------------
// IssuerMetadataBuilder
// ---------------------------------------------------------------------------

@Oid4vciDsl
class IssuerMetadataBuilder(
    private val issuerUrl: String,
) {
    /** Override the credential endpoint (default: `<issuerUrl>/credential`). */
    var credentialEndpoint: String = "$issuerUrl/credential"

    /** Optional batch credential endpoint (OID4VCI 1.0). */
    var batchCredentialEndpoint: String? = null

    /** Optional deferred credential endpoint. */
    var deferredCredentialEndpoint: String? = null

    /** Optional notification endpoint. */
    var notificationEndpoint: String? = null

    /** Optional nonce endpoint (default: omitted; set to enable). */
    var nonceEndpoint: String? = null

    /** Optional signed metadata JWT. */
    var signedMetadata: String? = null

    /** Optional batch credential issuance (OID4VCI 1.1). */
    var batchCredentialIssuance: BatchCredentialIssuance? = null

    private val authorizationServers = mutableListOf<String>()
    private val credentialConfigurations = mutableMapOf<String, CredentialConfigurationSupported>()
    private val displayEntries = mutableListOf<DisplayProperties>()
    private var responseEncryption: MetadataCredentialResponseEncryption? = null

    /** Add an authorization server URL. */
    fun authorizationServer(url: String) {
        authorizationServers.add(url)
    }

    /**
     * Add a credential configuration by [id].
     *
     * The [format] determines which format-specific fields are applicable.
     */
    fun credentialConfiguration(
        id: String,
        format: CredentialFormat,
        builder: CredentialConfigurationBuilder.() -> Unit,
    ) {
        credentialConfigurations[id] = CredentialConfigurationBuilder(format).apply(builder).build()
    }

    /**
     * Add an already-built [CredentialConfigurationSupported] under the given [id].
     */
    fun credentialConfiguration(
        id: String,
        config: CredentialConfigurationSupported,
    ) {
        credentialConfigurations[id] = config
    }

    /** Add an issuer-level display entry. */
    fun display(builder: DisplayBuilder.() -> Unit) {
        displayEntries.add(DisplayBuilder().apply(builder).build())
    }

    /** Configure top-level credential response encryption support. */
    fun credentialResponseEncryption(builder: MetadataEncryptionBuilder.() -> Unit) {
        responseEncryption = MetadataEncryptionBuilder().apply(builder).build()
    }

    /** Set the batch credential issuance limit (OID4VCI 1.1). */
    fun batchCredentialIssuance(batchSize: Int) {
        batchCredentialIssuance = BatchCredentialIssuance(batchSize = batchSize)
    }

    internal fun build(): CredentialIssuerMetadata =
        CredentialIssuerMetadata(
            credentialIssuer = issuerUrl,
            authorizationServers = authorizationServers.takeIf { it.isNotEmpty() },
            credentialEndpoint = credentialEndpoint,
            batchCredentialEndpoint = batchCredentialEndpoint,
            deferredCredentialEndpoint = deferredCredentialEndpoint,
            notificationEndpoint = notificationEndpoint,
            nonceEndpoint = nonceEndpoint,
            credentialConfigurationsSupported = credentialConfigurations.toMap(),
            signedMetadata = signedMetadata,
            display = displayEntries.takeIf { it.isNotEmpty() },
            credentialResponseEncryption = responseEncryption,
            batchCredentialIssuance = batchCredentialIssuance,
        )
}

// ---------------------------------------------------------------------------
// CredentialConfigurationBuilder
// ---------------------------------------------------------------------------

@Oid4vciDsl
class CredentialConfigurationBuilder(
    private val format: CredentialFormat,
) {
    /** OAuth2 scope string for this credential configuration. */
    var scope: String? = null

    /**
     * Verifiable Credential Type (for SD-JWT DC / vc+sd-jwt formats).
     *
     * Maps to `vct` in the wire format.
     */
    var vct: String? = null

    /**
     * Document type (for mDoc / mso_mdoc format).
     *
     * Maps to `doctype` in the wire format.
     */
    var doctype: String? = null

    /**
     * Ordered list of claim names for display ordering.
     *
     * Maps to `order` in the wire format.
     */
    var order: List<String>? = null

    private val cryptographicBindingMethods = mutableListOf<String>()
    private val signingAlgorithms = mutableListOf<JwaAlgorithm>()
    private val proofTypes = mutableMapOf<String, ProofTypeSupported>()
    private val displayEntries = mutableListOf<DisplayProperties>()
    private var credentialDefinition: CredentialDefinition? = null
    private var claims: MutableMap<String, ClaimMetadata>? = null
    private var credentialMetadata: CredentialMetadataBuilder? = null
    private var responseEncryption: CredentialResponseEncryption? = null

    /**
     * Add a cryptographic binding method using a raw string.
     *
     * Common values: `"did:key"`, `"did:jwk"`, `"jwk"`, `"cose_key"`.
     */
    fun bindingMethod(method: String) {
        cryptographicBindingMethods.add(method)
    }

    /**
     * Add multiple cryptographic binding methods at once.
     */
    fun bindingMethods(vararg methods: String) {
        cryptographicBindingMethods.addAll(methods.toList())
    }

    /**
     * Add a signing algorithm supported for credential signing.
     */
    fun signingAlg(alg: JwaAlgorithm) {
        signingAlgorithms.add(alg)
    }

    /**
     * Add multiple signing algorithms at once.
     */
    fun signingAlgs(vararg algs: JwaAlgorithm) {
        signingAlgorithms.addAll(algs.toList())
    }

    /**
     * Add a supported proof type with its signing algorithms.
     *
     * [proofType] is typically [ProofType.JWT].
     * [algs] are the signing algorithms accepted in the proof.
     */
    fun proofType(
        proofType: ProofType,
        vararg algs: JwaAlgorithm,
        keyAttestationsRequired: KeyAttestationsRequired? = null,
    ) {
        proofTypes[proofType.value] =
            ProofTypeSupported(
                proofSigningAlgValuesSupported = algs.map { it.value },
                keyAttestationsRequired = keyAttestationsRequired,
            )
    }

    /**
     * Add a supported proof type using the raw string key and algorithm strings.
     *
     * Use this overload when [ProofType] does not cover the needed proof type.
     */
    fun proofType(
        proofTypeKey: String,
        vararg algValues: String,
        keyAttestationsRequired: KeyAttestationsRequired? = null,
    ) {
        proofTypes[proofTypeKey] =
            ProofTypeSupported(
                proofSigningAlgValuesSupported = algValues.toList(),
                keyAttestationsRequired = keyAttestationsRequired,
            )
    }

    /**
     * Add a display entry for this credential configuration.
     */
    fun display(builder: DisplayBuilder.() -> Unit) {
        displayEntries.add(DisplayBuilder().apply(builder).build())
    }

    /**
     * Configure the credential definition (JWT VC JSON / W3C VC formats).
     */
    fun credentialDefinition(builder: CredentialDefinitionBuilder.() -> Unit) {
        credentialDefinition = CredentialDefinitionBuilder().apply(builder).build()
    }

    /**
     * Add an OID4VCI 1.0 map-based claim metadata entry.
     *
     * [claimName] is the key in the claims map.
     */
    fun claim(
        claimName: String,
        builder: ClaimMetadataBuilder.() -> Unit,
    ) {
        if (claims == null) {
            claims = mutableMapOf()
        }
        claims!![claimName] = ClaimMetadataBuilder().apply(builder).build()
    }

    /**
     * Configure OID4VCI 1.1 [CredentialMetadata] (path-based claims with display).
     */
    fun credentialMetadata(builder: CredentialMetadataBuilder.() -> Unit) {
        credentialMetadata = CredentialMetadataBuilder().apply(builder)
    }

    /**
     * Configure per-credential-configuration response encryption.
     */
    fun credentialResponseEncryption(builder: CredentialEncryptionBuilder.() -> Unit) {
        responseEncryption = CredentialEncryptionBuilder().apply(builder).build()
    }

    internal fun build(): CredentialConfigurationSupported =
        CredentialConfigurationSupported(
            format = format.value,
            scope = scope,
            cryptographicBindingMethodsSupported = cryptographicBindingMethods.takeIf { it.isNotEmpty() },
            credentialSigningAlgValuesSupported = signingAlgorithms.map { it.value }.takeIf { it.isNotEmpty() },
            proofTypesSupported = proofTypes.toMap().takeIf { it.isNotEmpty() },
            display = displayEntries.takeIf { it.isNotEmpty() },
            credentialDefinition = credentialDefinition,
            vct = vct,
            claims = claims?.toMap(),
            doctype = doctype,
            order = order,
            credentialResponseEncryption = responseEncryption,
            credentialMetadata = credentialMetadata?.build(),
        )
}

// ---------------------------------------------------------------------------
// DisplayBuilder
// ---------------------------------------------------------------------------

@Oid4vciDsl
class DisplayBuilder {
    /** Human-readable name (required). */
    var name: String = ""

    /** BCP47 locale tag (e.g. `"en-US"`, `"de-DE"`). */
    var locale: String? = null

    /** Human-readable description. */
    var description: String? = null

    /** Background color in CSS hex format (e.g. `"#12107c"`). */
    var backgroundColor: String? = null

    /** Text color in CSS hex format (e.g. `"#FFFFFF"`). */
    var textColor: String? = null

    private var logo: LogoProperties? = null
    private var backgroundImage: ImageProperties? = null

    /** Configure the logo. */
    fun logo(
        uri: String,
        altText: String? = null,
    ) {
        logo = LogoProperties(uri = uri, altText = altText)
    }

    /** Configure the background image. */
    fun backgroundImage(uri: String) {
        backgroundImage = ImageProperties(uri = uri)
    }

    internal fun build(): DisplayProperties =
        DisplayProperties(
            name = name,
            locale = locale,
            logo = logo,
            description = description,
            backgroundColor = backgroundColor,
            backgroundImage = backgroundImage,
            textColor = textColor,
        )
}

// ---------------------------------------------------------------------------
// CredentialDefinitionBuilder  (JWT VC JSON / W3C VC)
// ---------------------------------------------------------------------------

@Oid4vciDsl
class CredentialDefinitionBuilder {
    private val types = mutableListOf<String>()
    private val contexts = mutableListOf<String>()
    private val credentialSubject = mutableMapOf<String, ClaimMetadata>()

    /** Add a type string (e.g. `"VerifiableCredential"`, `"UniversityDegreeCredential"`). */
    fun type(vararg typeValues: String) {
        types.addAll(typeValues.toList())
    }

    /** Add a `@context` URL. */
    fun context(vararg contextUrls: String) {
        contexts.addAll(contextUrls.toList())
    }

    /** Add a credential subject claim definition. */
    fun claim(
        claimName: String,
        builder: ClaimMetadataBuilder.() -> Unit,
    ) {
        credentialSubject[claimName] = ClaimMetadataBuilder().apply(builder).build()
    }

    internal fun build(): CredentialDefinition =
        CredentialDefinition(
            type = types.takeIf { it.isNotEmpty() },
            context = contexts.takeIf { it.isNotEmpty() },
            credentialSubject = credentialSubject.toMap().takeIf { it.isNotEmpty() },
        )
}

// ---------------------------------------------------------------------------
// ClaimMetadataBuilder  (OID4VCI 1.0 map-based claims)
// ---------------------------------------------------------------------------

@Oid4vciDsl
class ClaimMetadataBuilder {
    /** Whether the claim is mandatory. */
    var mandatory: Boolean? = null

    /** The value type hint (e.g. `"string"`, `"integer"`). */
    var valueType: String? = null

    private val displayEntries = mutableListOf<ClaimDisplay>()

    /** Add a localized display name for this claim. */
    fun display(
        name: String,
        locale: String? = null,
    ) {
        displayEntries.add(ClaimDisplay(name = name, locale = locale))
    }

    internal fun build(): ClaimMetadata =
        ClaimMetadata(
            mandatory = mandatory,
            valueType = valueType,
            display = displayEntries.takeIf { it.isNotEmpty() },
        )
}

// ---------------------------------------------------------------------------
// CredentialMetadataBuilder  (OID4VCI 1.1 credential_metadata)
// ---------------------------------------------------------------------------

@Oid4vciDsl
class CredentialMetadataBuilder {
    private val displayEntries = mutableListOf<DisplayProperties>()
    private val claimEntries = mutableListOf<CredentialMetadataClaim>()

    /** Add a display entry for the credential_metadata section. */
    fun display(builder: DisplayBuilder.() -> Unit) {
        displayEntries.add(DisplayBuilder().apply(builder).build())
    }

    /**
     * Add a path-based claim (OID4VCI 1.1).
     *
     * [path] elements are string field names or integer array indices.
     * Mixed paths are supported (e.g. `listOf("nationalities", 0)`).
     */
    fun claim(
        vararg path: Any,
        builder: ClaimBuilder.() -> Unit = {},
    ) {
        claimEntries.add(ClaimBuilder(path.toList()).apply(builder).build())
    }

    /**
     * Add a path-based claim using pre-built [JsonElement] path elements.
     */
    fun claimWithJsonPath(
        path: List<JsonElement>,
        builder: ClaimBuilder.() -> Unit = {},
    ) {
        val delegate = ClaimBuilder(emptyList()).apply(builder)
        claimEntries.add(
            CredentialMetadataClaim(
                path = path,
                mandatory = delegate.mandatory,
                display = delegate.buildDisplay(),
            ),
        )
    }

    internal fun build(): CredentialMetadata =
        CredentialMetadata(
            display = displayEntries.takeIf { it.isNotEmpty() },
            claims = claimEntries.takeIf { it.isNotEmpty() },
        )
}

// ---------------------------------------------------------------------------
// ClaimBuilder  (OID4VCI 1.1 path-based claim)
// ---------------------------------------------------------------------------

@Oid4vciDsl
class ClaimBuilder(
    private val rawPath: List<Any>,
) {
    /** Whether this claim is mandatory. */
    var mandatory: Boolean? = null

    private val displayEntries = mutableListOf<ClaimDisplay>()

    /** Add a localized display name for this claim. */
    fun display(
        name: String,
        locale: String? = null,
    ) {
        displayEntries.add(ClaimDisplay(name = name, locale = locale))
    }

    internal fun buildDisplay(): List<ClaimDisplay>? = displayEntries.takeIf { it.isNotEmpty() }

    internal fun build(): CredentialMetadataClaim {
        val pathElements: List<JsonElement> =
            rawPath.map { element ->
                when (element) {
                    is String -> JsonPrimitive(element)
                    is Int -> JsonPrimitive(element)
                    is Long -> JsonPrimitive(element)
                    is JsonElement -> element
                    else -> JsonPrimitive(element.toString())
                }
            }
        return CredentialMetadataClaim(
            path = pathElements,
            mandatory = mandatory,
            display = displayEntries.takeIf { it.isNotEmpty() },
        )
    }
}

// ---------------------------------------------------------------------------
// MetadataEncryptionBuilder  (top-level credential_response_encryption)
// ---------------------------------------------------------------------------

@Oid4vciDsl
class MetadataEncryptionBuilder {
    private val algValues = mutableListOf<String>()
    private val encValues = mutableListOf<String>()
    private val zipValues = mutableListOf<String>()

    /** Whether encryption is required. */
    var encryptionRequired: Boolean = false

    /** Add a supported key-wrapping algorithm (e.g. `"ECDH-ES"`, `"RSA-OAEP"`). */
    fun alg(vararg values: String) {
        algValues.addAll(values.toList())
    }

    /** Add a supported content-encryption algorithm (e.g. `"A256GCM"`). */
    fun enc(vararg values: String) {
        encValues.addAll(values.toList())
    }

    /** Add a supported compression algorithm (e.g. `"DEF"`). */
    fun zip(vararg values: String) {
        zipValues.addAll(values.toList())
    }

    internal fun build(): MetadataCredentialResponseEncryption =
        MetadataCredentialResponseEncryption(
            algValuesSupported = algValues.toList(),
            encValuesSupported = encValues.toList(),
            zipValuesSupported = zipValues.takeIf { it.isNotEmpty() },
            encryptionRequired = encryptionRequired,
        )
}

// ---------------------------------------------------------------------------
// CredentialEncryptionBuilder  (per-config credential_response_encryption)
// ---------------------------------------------------------------------------

@Oid4vciDsl
class CredentialEncryptionBuilder {
    private val algValues = mutableListOf<String>()
    private val encValues = mutableListOf<String>()
    private val zipValues = mutableListOf<String>()

    /** Whether encryption is required for this credential configuration. */
    var encryptionRequired: Boolean = false

    /** Add a supported key-wrapping algorithm. */
    fun alg(vararg values: String) {
        algValues.addAll(values.toList())
    }

    /** Add a supported content-encryption algorithm. */
    fun enc(vararg values: String) {
        encValues.addAll(values.toList())
    }

    /** Add a supported compression algorithm. */
    fun zip(vararg values: String) {
        zipValues.addAll(values.toList())
    }

    internal fun build(): CredentialResponseEncryption =
        CredentialResponseEncryption(
            algValuesSupported = algValues.toList(),
            encValuesSupported = encValues.toList(),
            zipValuesSupported = zipValues.takeIf { it.isNotEmpty() },
            encryptionRequired = encryptionRequired,
        )
}
