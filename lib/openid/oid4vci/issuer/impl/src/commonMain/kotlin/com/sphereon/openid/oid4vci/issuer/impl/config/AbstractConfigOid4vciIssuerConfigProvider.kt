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

package com.sphereon.openid.oid4vci.issuer.impl.config

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.dsl.credentialConfiguration
import com.sphereon.openid.oid4vci.common.model.BatchCredentialIssuance
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.KeyAttestationsRequired
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialRequestEncryption
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialResponseEncryption
import com.sphereon.openid.oid4vci.issuer.config.CredentialSigningConfig
import com.sphereon.openid.oid4vci.issuer.config.KeyAttesterTrustConfig
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.config.VctTypeMetadataProvider
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import com.sphereon.sdjwt.vc.ClaimSdMetadata
import com.sphereon.sdjwt.vc.SdJwtVcTypeMetadata
import com.sphereon.sdjwt.vc.VctClaimDisplayInput
import com.sphereon.sdjwt.vc.VctClaimInput
import com.sphereon.sdjwt.vc.VctDisplayInput
import com.sphereon.sdjwt.vc.VctTypeMetadataInput
import com.sphereon.sdjwt.vc.buildSdJwtVcTypeMetadata
import com.sphereon.statuslist.StatusListBinding
import com.sphereon.statuslist.StatusListDefinitionsProvider
import com.sphereon.statuslist.StatusListErrors
import dev.zacsweers.metro.Provider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Base for [Oid4vciIssuerConfigProvider] / [VctTypeMetadataProvider] implementations that read
 * issuer metadata and per-credential configuration from IDK's ConfigService.
 *
 * The single point of variability is the root config [namespace], supplied by [namespaceProvider]
 * and evaluated fresh on each read. ALL config reads (issuer-level keys AND the per-credential
 * subtree) are resolved relative to that namespace:
 *  - issuer-level keys: `<namespace>.<key>` (e.g. `<namespace>.identifier`)
 *  - per-credential keys: `<namespace>.credentials.[<configId>].<key>` via [credentialsNamespace]
 *
 * Subclasses choose the namespace:
 *  - [ConfigDrivenOid4vciIssuerConfigProvider] pins it to the singular `oid4vci.issuer` namespace,
 *    preserving the pure-IDK config-only single-issuer deploy.
 *  - [RegistryBackedOid4vciIssuerConfigProvider] computes a per-instance namespace
 *    (`oid4vci.issuers.<instanceId>`) selected at request time, falling back to the singular one.
 *
 * Evaluating the supplier per read (rather than caching it) matches the session-scoped lifecycle:
 * the active instance id may be set after this provider is constructed but before its first read.
 */
abstract class AbstractConfigOid4vciIssuerConfigProvider(
    private val execution: SessionExecution,
    // Status lists are a standalone, protocol-neutral concern (see StatusListDefinitionsProvider).
    // The issuer only declares per-credential *bindings* and resolves the referenced list's
    // spec/purposes through this provider; the definitions themselves live at the root
    // `statuslists` config namespace and are hosted at their own root path. The injection is
    // optional, but resolution fails closed: a credential configuration that declares a
    // `statusListId` while this provider is absent makes [statusListBindingFor] return an error,
    // aborting issuance instead of silently issuing credentials that could never be revoked.
    private val statusListDefinitionsProvider: Provider<StatusListDefinitionsProvider>? = null,
    private val namespaceProvider: () -> String,
) : Oid4vciIssuerConfigProvider,
    VctTypeMetadataProvider {
    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    /** Active root namespace for this read; e.g. `oid4vci.issuer` or `oid4vci.issuers.<id>`. */
    protected val namespace: String
        get() = namespaceProvider()

    /**
     * Per-credential subtree root, derived from [namespace] so it tracks the selected instance.
     * The singular constant `CredentialIssuancePolicyConfig.CONFIG_NAMESPACE` (`oid4vci.issuer.credentials`)
     * bakes in the singular issuer namespace and therefore cannot be used directly once the issuer
     * namespace is instance-relative; we reconstruct it as `<namespace>.credentials`.
     */
    private val credentialsNamespace: String
        get() = "$namespace.credentials"

    override val issuerIdentifier: String
        get() {
            val ns = namespace
            val value = configService.getPropertyAsString("$ns.identifier")
            require(!value.isNullOrBlank()) {
                "$ns.identifier is required: every OID4VCI issuer surface (metadata, credential offers, " +
                    "issuance) needs an absolute http(s) URL identifying this issuer per OID4VCI 1.0 §11.2.2"
            }
            return value
        }

    override val authorizationServers: List<String>?
        get() =
            configService
                .getPropertyAsString("$namespace.authorizationServers")
                ?.splitComma()
                ?.takeIf { it.isNotEmpty() }

    /** Raw metadata signing alias read from `<namespace>.signingKeyAlias`. */
    override val metadataSigningKeyAlias: String?
        get() = configService.getPropertyAsString("$namespace.signingKeyAlias")?.takeIf { it.isNotBlank() }

    private val metadataSigningKmsProviderId: String?
        get() = configService.getPropertyAsString("$namespace.signingKmsProviderId")?.takeIf { it.isNotBlank() }

    /**
     * Issuer-wide clock-skew tolerance (seconds). YAML:
     * `<namespace>.issuance-clock-skew-in-seconds` (kebab-case). The config
     * layer normalises to dot-delimited lowercase for lookup.
     */
    override val issuanceClockSkewInSeconds: Long
        get() =
            configService
                .getPropertyAsString("$namespace.issuance.clock.skew.in.seconds")
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 60L

    /**
     * Signing key for issuer metadata, used to produce `signed_metadata` (OID4VCI §11.2.4).
     *
     * `signed_metadata` is OPTIONAL, and publishing it commits the issuer to a signature the
     * holder MUST be able to verify (it resolves the signer via the issuer's published JWKS).
     * Emitting it therefore requires an explicit opt-in (`<namespace>.signed-metadata.enabled`,
     * default false) and a configured signing alias. This keeps it decoupled from the issuer
     * signing key's primary role (credential signing): a tenant having a signing key does not
     * by itself force unverifiable signed metadata onto holders.
     */
    override val signingKey: ManagedIdentifierOptsOrResult?
        get() {
            val enabled =
                configService.getPropertyAsString("$namespace.signed-metadata.enabled")?.toBoolean() ?: false
            if (!enabled) return null
            return metadataSigningKeyAlias?.let { alias ->
                ManagedOptsKeyInfo(
                    identifier =
                        KeyInfo<KeyType>(
                            alias = alias,
                            providerId = metadataSigningKmsProviderId,
                        ),
                )
            }
        }

    /**
     * ECDH-ES decryption key alias for OID4VCI 1.0 §11.2.4 `credential_request_encryption`.
     * Read from `<namespace>.encryption.request.decryptionKeyAlias` with an optional
     * `<namespace>.encryption.request.decryptionKmsProviderId`. The metadata builder resolves
     * the public JWK at runtime via the KMS so we never store private key material in YAML / git.
     *
     * Uses `ManagedOptsKeyInfo` with `keyVisibility = PRIVATE` rather than the simpler
     * `ManagedOptsAlias` because the latter defaults to PUBLIC visibility — the keystore then
     * strips the private scalar (`d`) on read and `DecryptJweCommandImpl` rejects with
     * "Decryptor key must be a private key (must have 'd' parameter)". Same gotcha the
     * OID4VP verifier's `DirectPostResponseEndpointCommand` calls out for JARM decryption.
     */
    override val credentialRequestDecryptionKey: ManagedIdentifierOptsOrResult?
        get() =
            configService
                .getPropertyAsString("$namespace.encryption.request.decryptionKeyAlias")
                ?.takeIf { it.isNotBlank() }
                ?.let { alias ->
                    ManagedOptsKeyInfo(
                        identifier =
                            KeyInfo<KeyType>(
                                alias = alias,
                                providerId =
                                    configService
                                        .getPropertyAsString("$namespace.encryption.request.decryptionKmsProviderId")
                                        ?.takeIf { it.isNotBlank() },
                                keyVisibility = KeyVisibility.PRIVATE,
                            ),
                    )
                }

    override val display: List<DisplayProperties>?
        get() {
            val ns = namespace
            val name =
                configService.getPropertyAsString("$ns.display.name")
                    ?: return null
            val locale = configService.getPropertyAsString("$ns.display.locale")
            return listOf(DisplayProperties(name = name, locale = locale))
        }

    override val credentialConfigurations: Map<String, CredentialConfigurationSupported>
        get() {
            val ids =
                configService
                    .getPropertyAsString("$namespace.credentialConfigurationIds")
                    ?.splitComma()
                    ?: return emptyMap()

            return ids.associateWith { id -> buildCredentialConfiguration(id) }
        }

    override val credentialResponseEncryption: MetadataCredentialResponseEncryption?
        get() {
            val prefix = "$namespace.encryption.response"
            val algValues =
                configService
                    .getPropertyAsString("$prefix.algValuesSupported")
                    ?.splitComma()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null
            val encValues =
                configService
                    .getPropertyAsString("$prefix.encValuesSupported")
                    ?.splitComma()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null
            // Empty `zip_values_supported` → omit the field on the wire (it's OPTIONAL per
            // OID4VCI 1.0 §11.2.4 and a 1.1 addition; 1.0 conformance fails on `[]` vs absent).
            val zipValues =
                configService
                    .getPropertyAsString("$prefix.zipValuesSupported")
                    ?.splitComma()
                    ?.takeIf { it.isNotEmpty() }
            val required = configService.getPropertyAsString("$prefix.encryptionRequired")?.toBoolean() ?: false
            return MetadataCredentialResponseEncryption(
                algValuesSupported = algValues,
                encValuesSupported = encValues,
                zipValuesSupported = zipValues,
                encryptionRequired = required,
            )
        }

    override val credentialRequestEncryption: MetadataCredentialRequestEncryption?
        get() {
            val prefix = "$namespace.encryption.request"
            // Two ways to opt in: either a static `jwks` JSON-string (legacy / testing only —
            // pastes a full JWKS into config), or a `decryption-key-alias` that points at a KMS
            // alias whose public JWK the metadata builder publishes at runtime. The KMS path is
            // the production answer; never commit a private key to YAML / git.
            val staticJwksJson = configService.getPropertyAsString("$prefix.jwks")
            val decryptionAlias = configService.getPropertyAsString("$prefix.decryptionKeyAlias")?.takeIf { it.isNotBlank() }
            if (staticJwksJson == null && decryptionAlias == null) return null

            val encValues =
                configService
                    .getPropertyAsString("$prefix.encValuesSupported")
                    ?.splitComma()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null
            val zipValues =
                configService
                    .getPropertyAsString("$prefix.zipValuesSupported")
                    ?.splitComma()
                    ?.takeIf { it.isNotEmpty() }
            val required = configService.getPropertyAsString("$prefix.encryptionRequired")?.toBoolean() ?: false

            // Placeholder JWKS — the metadata builder swaps in the real one resolved from
            // [credentialRequestDecryptionKey] when that opts is set. The static-jwks path is
            // retained for tests/operators who want to paste a JWKS literal.
            val jwks =
                if (staticJwksJson != null) {
                    Json.parseToJsonElement(staticJwksJson).jsonObject
                } else {
                    JsonObject(mapOf("keys" to JsonArray(emptyList())))
                }
            return MetadataCredentialRequestEncryption(
                jwks = jwks,
                encValuesSupported = encValues,
                zipValuesSupported = zipValues,
                encryptionRequired = required,
            )
        }

    override val batchCredentialIssuance: BatchCredentialIssuance?
        get() {
            val batchSize = configService.getPropertyAsString("$namespace.batch.maxSize")?.toIntOrNull() ?: return null
            return BatchCredentialIssuance(batchSize = batchSize)
        }

    override val credentialSigningConfigs: Map<String, CredentialSigningConfig>
        get() {
            val ids =
                configService
                    .getPropertyAsString("$namespace.credentialConfigurationIds")
                    ?.splitComma()
                    ?: return emptyMap()

            return ids.associateWith { id -> buildCredentialSigningConfig(id) }
        }

    override val keyAttesterTrustConfigs: Map<String, Map<String, KeyAttesterTrustConfig>>
        get() {
            val ids =
                configService
                    .getPropertyAsString("$namespace.credentialConfigurationIds")
                    ?.splitComma()
                    ?: return emptyMap()

            return ids
                .associateWith { id -> buildKeyAttesterTrustConfigsForCredential(id) }
                .filterValues { it.isNotEmpty() }
        }

    // -------------------------------------------------------------------------
    // Per-credential configuration building
    // -------------------------------------------------------------------------

    private fun buildCredentialConfiguration(configId: String): CredentialConfigurationSupported {
        val prefix = "$credentialsNamespace.[$configId]"

        val formatValue = configService.getPropertyAsString("$prefix.format")
        val format =
            formatValue?.let { CredentialFormat.fromValue(it) }
                ?: CredentialFormat.SD_JWT_DC

        val scope = configService.getPropertyAsString("$prefix.scope")
        val vct = configService.getPropertyAsString("$prefix.vct")?.takeIf { it.isNotEmpty() }
        val doctype = configService.getPropertyAsString("$prefix.doctype")?.takeIf { it.isNotEmpty() }

        val signingAlgorithms =
            configService
                .getPropertyAsString("$prefix.signingAlgorithms")
                ?.splitComma()
                ?.mapNotNull { JwaAlgorithm.fromValue(it) }
                ?: emptyList()

        val bindingMethods =
            configService
                .getPropertyAsString("$prefix.bindingMethods")
                ?.splitComma()
                ?: emptyList()

        val proofTypes = buildProofTypes(prefix)
        val proofTypeAttestations = buildProofTypeAttestations(prefix, proofTypes.keys)
        val credentialDefinitionTypes =
            configService
                .getPropertyAsString("$prefix.credentialDefinition.types")
                ?.splitComma()

        // Read claim names from config
        val claimNames = discoverClaimNames(prefix)

        return credentialConfiguration(format) {
            this.scope = scope
            this.vct = vct
            this.doctype = doctype

            // Per OID4VCI 1.0 final §A.5 (claims path pointer):
            //   - JWS-based formats (`dc+sd-jwt`, `jwt_vc_json`, …): `path` is field-name
            //     segments. Our claim keys carry the on-the-wire claim name verbatim
            //     (bracket-quoted at the YAML level so `family_name` survives the
            //     PropertyKeyNormalizer's underscore-to-dot pass), so a single-element
            //     path `[claimName]` is correct.
            //   - `mso_mdoc`: `path` MUST be exactly `[namespace, elementIdentifier]` per
            //     §6.5 / Appendix A.3. Our claim keys are namespace-prefixed (e.g.
            //     `org.iso.18013.5.1.family_name`), so split on the LAST dot to recover
            //     the two parts; if the key has no dot, treat the whole doctype as the
            //     namespace (matches MsoMdocFormatHandler.groupAttributesByNamespace).
            val isMdoc = format.value == "mso_mdoc"
            for (claimName in claimNames) {
                val mandatory =
                    configService.getProperty("$prefix.claims.$claimName.mandatory", Boolean::class, false)
                        ?: false
                val path: List<String> =
                    if (isMdoc) {
                        val lastDot = claimName.lastIndexOf('.')
                        if (lastDot > 0) {
                            listOf(claimName.substring(0, lastDot), claimName.substring(lastDot + 1))
                        } else {
                            listOf(doctype ?: claimName, claimName)
                        }
                    } else {
                        listOf(claimName)
                    }
                val claimDisplays = buildClaimDisplays("$prefix.claims.[$claimName]")
                claim(path) {
                    this.mandatory = mandatory
                    if (claimDisplays.isEmpty()) {
                        // No per-locale display configured: fall back to a humanised label derived
                        // from the claim path (no locale), so the claim still carries a name.
                        display(path.last().replace('_', ' ').replaceFirstChar { it.uppercase() })
                    } else {
                        // OID4VCI 1.0 §11.2.2 claim display is multi-locale: emit one entry per
                        // configured locale. The config authors a `label` per locale (VCT term); the
                        // OID4VCI claim display field is `name`, so we map label -> name here. The
                        // VCT projection reuses the same source with `label` semantics intact.
                        claimDisplays.forEach { (locale, d) -> display(name = d.label, locale = locale) }
                    }
                }
            }

            signingAlgorithms.forEach { alg -> signingAlg(alg) }
            bindingMethods.forEach { method -> bindingMethod(method) }

            proofTypes.forEach { (proofTypeKey, algValues) ->
                proofType(
                    proofTypeKey,
                    *algValues.toTypedArray(),
                    keyAttestationsRequired = proofTypeAttestations[proofTypeKey],
                )
            }

            if (!credentialDefinitionTypes.isNullOrEmpty()) {
                credentialDefinition {
                    type(*credentialDefinitionTypes.toTypedArray())
                }
            }

            // Optional branding (OID4VCI 1.0 §11.2.2 display object). Lets the issuer advertise
            // name / description / logo / colors / background image per locale in metadata so
            // consumers (wallets, demo UIs) can brand the credential without a separate type-
            // metadata document — essential for mso_mdoc, which has no SD-JWT VCT to carry branding,
            // and equally for wallets that only read OID4VCI metadata (not VCT type metadata).
            buildCredentialDisplays(prefix).forEach { (locale, d) ->
                display {
                    name = d.name
                    this.locale = locale
                    description = d.description
                    backgroundColor = d.backgroundColor
                    textColor = d.textColor
                    d.logoUri?.let { logo(uri = it, altText = d.logoAltText) }
                    d.backgroundImageUri?.let { backgroundImage(it) }
                }
            }
        }
    }

    /**
     * One per-locale credential display entry sourced from config. Mirrors the OID4VCI 1.0 §11.2.2
     * display object and the SD-JWT VC type-metadata `display[].rendering.simple` shape, so a single
     * config block drives both the OID4VCI metadata display and the served VCT.
     */
    private data class CredentialDisplayEntry(
        val name: String,
        val description: String?,
        val backgroundColor: String?,
        val textColor: String?,
        val logoUri: String?,
        val logoAltText: String?,
        val backgroundImageUri: String?,
        val backgroundImageAltText: String?,
    )

    /** One per-locale claim display entry (`label` is the VCT term; mapped to OID4VCI `name`). */
    private data class ClaimDisplayEntry(
        val label: String,
        val description: String?,
    )

    /**
     * Discover the bracket-quoted locale keys under a `<...>.display` map (e.g. `[en-US]`, `[de-DE]`).
     * Mirrors [discoverClaimNames]: the [com.sphereon.core.api.conf.PropertyKeyNormalizer] preserves
     * bracket-quoted keys verbatim, so a BCP47 tag like `en-US` survives intact (an unquoted key
     * would be mangled by the underscore/case normalisation). Returns the tags with the brackets
     * stripped.
     */
    private fun discoverDisplayLocales(displayPrefix: String): List<String> =
        configService
            .getSubPropertiesAsString(setOf(displayPrefix), stripPrefix = true, redact = false)
            .keys
            .mapNotNull { key ->
                val first = key.split('.').firstOrNull() ?: return@mapNotNull null
                if (first.startsWith("[") && first.endsWith("]")) {
                    first.removeSurrounding("[", "]").takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }.distinct()

    /** Read the per-locale credential display map under `<prefix>.display.[<locale>]`. */
    private fun buildCredentialDisplays(prefix: String): Map<String, CredentialDisplayEntry> =
        discoverDisplayLocales("$prefix.display")
            .mapNotNull { locale ->
                val dp = "$prefix.display.[$locale]"
                val name = configService.getPropertyAsString("$dp.name") ?: return@mapNotNull null
                locale to
                    CredentialDisplayEntry(
                        name = name,
                        description = configService.getPropertyAsString("$dp.description"),
                        backgroundColor = configService.getPropertyAsString("$dp.backgroundColor"),
                        textColor = configService.getPropertyAsString("$dp.textColor"),
                        logoUri = configService.getPropertyAsString("$dp.logo.uri"),
                        logoAltText = configService.getPropertyAsString("$dp.logo.altText"),
                        backgroundImageUri = configService.getPropertyAsString("$dp.backgroundImage.uri"),
                        backgroundImageAltText = configService.getPropertyAsString("$dp.backgroundImage.altText"),
                    )
            }.toMap()

    /** Read the per-locale claim display map under `<claimPrefix>.display.[<locale>]`. */
    private fun buildClaimDisplays(claimPrefix: String): Map<String, ClaimDisplayEntry> =
        discoverDisplayLocales("$claimPrefix.display")
            .mapNotNull { locale ->
                val dp = "$claimPrefix.display.[$locale]"
                val label =
                    configService.getPropertyAsString("$dp.label")
                        ?: configService.getPropertyAsString("$dp.name")
                        ?: return@mapNotNull null
                locale to ClaimDisplayEntry(label = label, description = configService.getPropertyAsString("$dp.description"))
            }.toMap()

    // -------------------------------------------------------------------------
    // VctTypeMetadataProvider — optional SD-JWT VC type-metadata served from this same config.
    //
    // This is the IDK config-driven source the SPI documents: it derives each sd-jwt credential's
    // VCT from the exact display/claims blocks that drive the OID4VCI metadata, funnelling them
    // through the shared pure builder so the wire shape lives in one place. A future EDK/VDX
    // semantic source contributes its own VctTypeMetadataProvider (replacing this binding) and reuses
    // the same builder. Credentials without a `vct` (e.g. mso_mdoc) contribute no VCT.
    // -------------------------------------------------------------------------

    private data class VctRef(
        val configId: String,
        val vctUrl: String
    )

    /** Served-VCT id (last `vct` URL segment) -> its config id + full URL, for every sd-jwt cred. */
    private fun vctRefs(): Map<String, VctRef> {
        val ids = configService.getPropertyAsString("$namespace.credentialConfigurationIds")?.splitComma() ?: return emptyMap()
        val out = LinkedHashMap<String, VctRef>()
        for (configId in ids) {
            val prefix = "$credentialsNamespace.[$configId]"
            val vctUrl = configService.getPropertyAsString("$prefix.vct")?.takeIf { it.isNotEmpty() } ?: continue
            val bare = vctUrl.substringAfterLast('/').takeIf { it.isNotEmpty() } ?: configId
            out[bare] = VctRef(configId, vctUrl)
        }
        return out
    }

    override suspend fun listVcts(): List<String> = vctRefs().keys.toList()

    override suspend fun resolve(vct: String): SdJwtVcTypeMetadata? {
        val ref = vctRefs()[vct] ?: return null
        return buildVctTypeMetadataInput(ref)?.let { buildSdJwtVcTypeMetadata(it) }
    }

    private fun buildVctTypeMetadataInput(ref: VctRef): VctTypeMetadataInput? {
        val prefix = "$credentialsNamespace.[${ref.configId}]"
        val displays =
            buildCredentialDisplays(prefix).map { (locale, d) ->
                VctDisplayInput(
                    locale = locale,
                    name = d.name,
                    description = d.description,
                    logoUri = d.logoUri,
                    logoAltText = d.logoAltText,
                    backgroundImageUri = d.backgroundImageUri,
                    backgroundColor = d.backgroundColor,
                    textColor = d.textColor,
                )
            }
        val claims =
            discoverClaimNames(prefix).map { claimName ->
                val mandatory =
                    configService.getProperty("$prefix.claims.$claimName.mandatory", Boolean::class, false) ?: false
                // Selective-disclosure flag per claim (SD-JWT VC type metadata). Defaults to ALWAYS
                // (the claim is individually disclosable, i.e. optional/toggleable for a verifier);
                // set `sd: allowed` or `sd: never` to make a claim required / always-present.
                val sd =
                    configService.getPropertyAsString("$prefix.claims.[$claimName].sd")?.let { parseClaimSd(it) }
                        ?: ClaimSdMetadata.ALWAYS
                val claimDisplays =
                    buildClaimDisplays("$prefix.claims.[$claimName]").map { (locale, cd) ->
                        VctClaimDisplayInput(locale = locale, label = cd.label, description = cd.description)
                    }
                // sd-jwt claim paths are single-segment field names; the bracketed config key carries
                // the on-the-wire claim name verbatim.
                VctClaimInput(path = listOf(claimName), mandatory = mandatory, sd = sd, displays = claimDisplays)
            }
        if (displays.isEmpty() && claims.isEmpty()) return null
        return VctTypeMetadataInput(vct = ref.vctUrl, displays = displays, claims = claims)
    }

    private fun parseClaimSd(value: String): ClaimSdMetadata? = ClaimSdMetadata.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }

    /**
     * Dynamically enumerate the claim names configured under `<prefix>.claims`.
     *
     * Claim keys MUST be bracket-quoted in the YAML/properties source so the
     * [com.sphereon.core.api.conf.PropertyKeyNormalizer] preserves them verbatim — otherwise
     * underscore-to-dot normalisation would mangle a name like `family_name` into `family.name`
     * (silently changing the credential's wire-level claim identifier). Bracket-quoting also lets
     * mso_mdoc deployments encode the namespace inline as `[org.iso.18013.5.1.birth_date]`
     * without any dot inside being treated as a path separator.
     *
     * Discovery returns the bracketed segments still wrapped (`[family_name].mandatory`);
     * we strip the brackets here to recover the on-the-wire claim name. Each claim
     * contributes at least a `mandatory` attribute; we walk segments from the right until
     * we hit a known attribute name and treat everything before it as the claim name.
     */
    private fun discoverClaimNames(prefix: String): List<String> {
        val claimsPrefix = "$prefix.claims"
        val attributeSuffixes = setOf("mandatory", "display")
        return configService
            .getSubPropertiesAsString(setOf(claimsPrefix), stripPrefix = true, redact = false)
            .keys
            .mapNotNull { key ->
                val segments = key.split('.')
                if (segments.size < 2) return@mapNotNull null
                val attributeIndex = segments.indexOfFirst { it in attributeSuffixes }
                if (attributeIndex <= 0) return@mapNotNull null
                segments
                    .subList(0, attributeIndex)
                    .joinToString(".")
                    // Strip the bracket markers the normalizer left in place.
                    .removeSurrounding("[", "]")
            }.distinct()
    }

    private fun buildCredentialSigningConfig(configId: String): CredentialSigningConfig {
        val prefix = "$credentialsNamespace.[$configId]"

        // Per-credential alias wins; otherwise fall back to the issuer-level signing key
        // (`<namespace>.signingKeyAlias`, written per-tenant by the issuer bootstrap).
        // Credentials sign with the issuer's key by default, so deployments that provision a
        // single tenant signing key need not repeat it on every credential configuration.
        val signingKeyAlias =
            configService.getPropertyAsString("$prefix.signingKeyAlias")
                ?: metadataSigningKeyAlias
        val signingKeyMode =
            SigningKeyMode.fromConfig(
                configService.getPropertyAsString("$prefix.signingKeyMode"),
            )
        val signingCertChainPath = configService.getPropertyAsString("$prefix.signingCertChainPath")
        val expirationInDays =
            configService.getPropertyAsString("$prefix.expirationInDays")?.toIntOrNull()?.takeIf { it > 0 }

        return CredentialSigningConfig(
            signingKeyAlias = signingKeyAlias,
            signingKeyMode = signingKeyMode,
            signingCertChainPath = signingCertChainPath,
            expirationInDays = expirationInDays,
        )
    }

    // region status lists
    //
    // Status-list *definitions* are NOT an OID4VCI concept — they are protocol-neutral (usable for
    // credentials AND tokens) and live at the root `statuslists` namespace, resolved through the
    // injected [StatusListDefinitionsProvider] and hosted at their own root path (`/statuslists/{id}`).
    // The issuer only declares which list each credential type binds to:
    //   <namespace>.credentials.[<configId>].statusListId: revocation

    override val statusListBindings: Map<String, StatusListBinding>
        get() {
            val configIds =
                configService.getPropertyAsString("$namespace.credentialConfigurationIds")?.splitComma()
                    ?: return emptyMap()
            return configIds
                .mapNotNull { configId ->
                    val result = statusListBindingFor(configId)
                    if (result.isOk) result.value?.let { configId to it } else null
                }.toMap()
        }

    /**
     * Fail-closed binding resolution: a `statusListId` declared in config that cannot be resolved
     * to a hosted status-list definition yields an [Err] so issuance aborts — a credential issued
     * without its status claim could never be revoked. `Ok(null)` when the configuration declares
     * no `statusListId`.
     */
    override fun statusListBindingFor(credentialConfigId: String): IdkResult<StatusListBinding?, IdkError> {
        val prefix = "$credentialsNamespace.[$credentialConfigId]"
        val listId =
            configService.getPropertyAsString("$prefix.statusListId")?.takeIf { it.isNotBlank() }
                ?: return Ok(null)
        val definitions =
            statusListDefinitionsProvider?.invoke()
                ?: return Err(
                    StatusListErrors.bindingUnresolvable(
                        credentialConfigurationId = credentialConfigId,
                        statusListId = listId,
                        reason = "no status-list definitions source is available on this deployment (status-list module missing)",
                    ),
                )
        val definition =
            definitions.byId(listId)
                ?: return Err(
                    StatusListErrors.bindingUnresolvable(
                        credentialConfigurationId = credentialConfigId,
                        statusListId = listId,
                        reason = "no status list with id '$listId' is defined under the 'statuslists' namespace",
                    ),
                )
        return Ok(
            StatusListBinding(
                statusListCorrelationId = listId,
                spec = definition.spec,
                purposes = definition.purposes,
            ),
        )
    }

    // endregion

    /**
     * Builds the OID4VCI 1.0 §11.2.3 `key_attestations_required` policy per proof type, if
     * the operator opted in. Mirrors the `*-encryption-required` switches: a boolean
     * `enabled` flag (default false) controls whether the field is published in metadata
     * at all; the structured `key-storage` / `user-authentication` sub-keys carry the
     * minimum-acceptable ISO 18045 attack-potential-resistance levels (defaulted to
     * `iso_18045_moderate` in YAML).
     *
     * When `enabled=false` (or unset), the field is omitted entirely from metadata and
     * proofs without an attestation are accepted. When `enabled=true`, the lists are
     * always non-empty (YAML defaults guarantee this); after stripping empties to be
     * safe we publish whatever remains.
     */
    private fun buildProofTypeAttestations(
        prefix: String,
        proofTypeKeys: Set<String>,
    ): Map<String, KeyAttestationsRequired> =
        proofTypeKeys
            .associateWith { proofTypeKey ->
                val typePrefix = "$prefix.proofTypes.$proofTypeKey.keyAttestations"
                val enabled =
                    configService.getPropertyAsString("$typePrefix.enabled")?.toBoolean() ?: false
                if (!enabled) return@associateWith null
                val keyStorage =
                    configService
                        .getPropertyAsString("$typePrefix.keyStorage")
                        ?.splitComma()
                        ?.takeIf { it.isNotEmpty() }
                val userAuth =
                    configService
                        .getPropertyAsString("$typePrefix.userAuthentication")
                        ?.splitComma()
                        ?.takeIf { it.isNotEmpty() }
                if (keyStorage == null && userAuth == null) return@associateWith null
                KeyAttestationsRequired(keyStorage = keyStorage, userAuthentication = userAuth)
            }.filterValues { it != null }
            .mapValues { (_, v) -> v!! }

    /**
     * Builds per-proof-carrier trust configs for a credential. Reads from
     * `<namespace>.credentials.[<id>].proof-types.<type>.key-attestations.key-attester-trust.{jwks,issuers,x509-anchor-paths}`
     * — trust is nested inside the `key-attestations` group it pairs with, since trust is
     * only meaningful when the policy is enabled.
     *
     * Returns the empty map when no carrier has any trust override; callers then fall back
     * to global X.509 anchors loaded by `lib/trust/x509`.
     */
    private fun buildKeyAttesterTrustConfigsForCredential(configId: String,): Map<String, KeyAttesterTrustConfig> {
        val credentialPrefix = "$credentialsNamespace.[$configId]"
        val proofTypeKeys = buildProofTypes(credentialPrefix).keys
        if (proofTypeKeys.isEmpty()) return emptyMap()
        return proofTypeKeys
            .mapNotNull { proofType ->
                val trustPrefix =
                    "$credentialPrefix.proofTypes.$proofType.keyAttestations.keyAttesterTrust"
                val jwks = parseTrustedJwks(configService.getPropertyAsString("$trustPrefix.jwks"))
                val issuers = configService.getPropertyAsString("$trustPrefix.issuers")?.splitComma()?.takeIf { it.isNotEmpty() }
                val x509Paths =
                    configService
                        .getPropertyAsString("$trustPrefix.x509AnchorPaths")
                        ?.splitComma()
                        ?.takeIf { it.isNotEmpty() }
                if (jwks == null && issuers == null && x509Paths == null) return@mapNotNull null
                proofType to
                    KeyAttesterTrustConfig(
                        trustedJwks = jwks,
                        trustedIssuers = issuers,
                        x509TrustAnchorPaths = x509Paths,
                    )
            }.toMap()
    }

    /**
     * Parses the per-credential `key-attester-trust.jwks` value. Accepts either:
     *  - a raw JSON array of JWK objects: `[{...}, {...}]`
     *  - a JWKS document: `{"keys":[{...}, {...}]}` — the shape the OIDF conformance plan
     *    publishes under its `vci.key_attestation_jwks` config field
     *
     * Returns null on blank / malformed input — the verifier then falls back to other trust
     * sources (issuer allow-list, x509 anchors) when the JWK list is absent.
     */
    private fun parseTrustedJwks(raw: String?): List<Jwk>? {
        if (raw.isNullOrBlank()) return null
        val element = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return null
        val array =
            when (element) {
                is JsonArray -> element
                is JsonObject -> element["keys"] as? JsonArray
                else -> null
            } ?: return null
        if (array.isEmpty()) return null
        return array
            .mapNotNull { item -> runCatching { Jwk.fromJsonObject(item.jsonObject) }.getOrNull() }
            .takeIf { it.isNotEmpty() }
    }

    /**
     * Reads proof type configurations under `<prefix>.proofTypes.<type>.signingAlgorithms`.
     *
     * Discovers configured proof type keys dynamically by:
     * 1. Checking for an explicit comma-separated list at `<prefix>.proofTypes` (e.g. `jwt,cwt`).
     * 2. Falling back to scanning sub-properties under `<prefix>.proofTypes.` and extracting
     *    distinct type names from the first path segment after the prefix.
     *
     * Returns a map of proof type key to list of algorithm string values.
     */
    private fun buildProofTypes(prefix: String): Map<String, List<String>> {
        val proofTypesPrefix = "$prefix.proofTypes"

        // Strategy 1: explicit comma-separated list of proof type names
        val explicitTypes = configService.getPropertyAsString(proofTypesPrefix)?.splitComma()

        // Strategy 2: discover from sub-property keys (e.g. proofTypes.jwt.signingAlgorithms -> "jwt")
        val discoveredTypes =
            if (explicitTypes.isNullOrEmpty()) {
                configService
                    .getSubPropertiesAsString(setOf(proofTypesPrefix), stripPrefix = true, redact = false)
                    .keys
                    .mapNotNull { key -> key.split(".").firstOrNull() }
                    .distinct()
            } else {
                explicitTypes
            }

        val result = mutableMapOf<String, List<String>>()
        for (proofTypeKey in discoveredTypes) {
            val algorithms =
                configService
                    .getPropertyAsString("$proofTypesPrefix.$proofTypeKey.signingAlgorithms")
                    ?.splitComma()
            if (!algorithms.isNullOrEmpty()) {
                result[proofTypeKey] = algorithms
            }
        }

        return result
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun String.splitComma(): List<String> = split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
