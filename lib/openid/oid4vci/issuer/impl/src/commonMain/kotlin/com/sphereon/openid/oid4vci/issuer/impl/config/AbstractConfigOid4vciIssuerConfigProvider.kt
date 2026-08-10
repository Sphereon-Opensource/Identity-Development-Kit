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
import com.sphereon.openid.oid4vci.issuer.config.MissingRequiredClaimsPolicy
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciSpecVersion
import com.sphereon.openid.oid4vci.issuer.config.VctTypeMetadataProvider
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import com.sphereon.openid.oid4vci.issuer.spi.IssuerKeyNameResolver
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
 *  - per-credential keys: `<namespace>.credentials.[<configId>].<key>`
 *
 * Subclasses choose the namespace:
 *  - [ConfigDrivenOid4vciIssuerConfigProvider] pins it to the singular `oid4vci.issuer` namespace.
 *  - [RegistryBackedOid4vciIssuerConfigProvider] computes a per-instance namespace
 *    (`oid4vci.issuers.<instanceId>`) selected at request time.
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
    private val fallbackToSingularNamespace: Boolean = true,
    /**
     * Active issuer instance id, evaluated per read like [namespaceProvider]. It identifies the
     * binding a bound [IssuerKeyNameResolver] resolves against; it never itself names a key.
     */
    private val instanceIdProvider: () -> String? = { null },
    /**
     * Bound by deployments that manage key material centrally. While bound it is the only source of
     * the metadata-signing, credential-signing, and request-decryption key names: `signingKeyAlias`,
     * `signingKmsProviderId`, `credentials.[<id>].signingKeyAlias`,
     * `credentialDefaults.signingKeyAlias`, `encryption.request.decryptionKeyAlias`, and
     * `encryption.request.decryptionKmsProviderId` are not read at all, and a null answer refuses.
     */
    private val keyNameResolver: Provider<IssuerKeyNameResolver>? = null,
) : Oid4vciIssuerConfigProvider,
    VctTypeMetadataProvider {
    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    /** Active root namespace for this read; e.g. `oid4vci.issuer` or `oid4vci.issuers.<id>`. */
    protected val namespace: String
        get() = namespaceProvider()

    private val singularNamespace: String
        get() = ConfigDrivenOid4vciIssuerConfigProvider.NAMESPACE

    private fun namespaceProperty(relativeKey: String): String? =
        propertyInNamespace(namespace, relativeKey)
            ?: if (fallbackToSingularNamespace && namespace != singularNamespace) propertyInNamespace(singularNamespace, relativeKey) else null

    private fun namespaceSubProperties(relativePrefix: String): Map<String, String> {
        val active = subPropertiesInNamespace(namespace, relativePrefix)
        if (!fallbackToSingularNamespace || namespace == singularNamespace) return active
        val fallback = subPropertiesInNamespace(singularNamespace, relativePrefix)
        return fallback + active
    }

    private fun propertyInNamespace(
        ns: String,
        relativeKey: String,
    ): String? = configService.getPropertyAsString("$ns.$relativeKey")

    private fun subPropertiesInNamespace(
        ns: String,
        relativePrefix: String,
    ): Map<String, String> = configService.getSubPropertiesAsString(setOf("$ns.$relativePrefix"), stripPrefix = true, redact = false)

    private fun credentialConfigIds(): List<String>? = namespaceProperty("credentialConfigurationIds")?.splitComma()

    private fun credentialProperty(
        configId: String,
        relativeKey: String,
    ): String? = namespaceProperty("credentials.[$configId].$relativeKey")

    private fun credentialDefaultProperty(relativeKey: String): String? = namespaceProperty("credentialDefaults.$relativeKey")

    private fun credentialOrDefaultProperty(
        configId: String,
        relativeKey: String,
    ): String? =
        credentialProperty(configId, relativeKey)
            ?: credentialDefaultProperty(relativeKey)

    private fun credentialClaimProperty(
        configId: String,
        claimName: String,
        relativeKey: String,
    ): String? =
        credentialProperty(configId, "claims.[$claimName].$relativeKey")
            ?: credentialProperty(configId, "claims.$claimName.$relativeKey")

    override val issuerIdentifier: String
        get() {
            val ns = namespace
            val value = namespaceProperty("identifier")
            require(!value.isNullOrBlank()) {
                "$ns.identifier is required: every OID4VCI issuer surface (metadata, credential offers, " +
                    "issuance) needs an absolute http(s) URL identifying this issuer per OID4VCI 1.0 §11.2.2"
            }
            return value
        }

    override val oid4vciSpecVersion: Oid4vciSpecVersion
        get() =
            Oid4vciSpecVersion.parse(
                namespaceProperty("spec.version")
                    ?: namespaceProperty("specVersion"),
            )

    override val authorizationServers: List<String>?
        get() =
            namespaceProperty("authorizationServers")
                ?.splitComma()
                ?.takeIf { it.isNotEmpty() }

    /**
     * Metadata-signing key name. A bound [IssuerKeyNameResolver] answers from the deployment's own
     * server-side binding for the active tenant and issuer instance; `<namespace>.signingKeyAlias`
     * is then not read at all. Without one, the deployment's own configured alias is used.
     */
    override suspend fun metadataSigningKeyName(): String? {
        val resolver = keyNameResolver?.invoke() ?: return namespaceProperty("signingKeyAlias")?.takeIf { it.isNotBlank() }
        val tenantId = tenantId() ?: return null
        val instanceId = instanceId() ?: return null
        return resolver.resolveMetadataSigningKeyName(tenantId, instanceId)?.takeIf { it.isNotBlank() }
    }

    /**
     * KMS provider for the metadata-signing key, read only while no [IssuerKeyNameResolver] is
     * bound. A deployment that manages key material centrally derives the provider from its own
     * binding, so no provider may be selected from configuration there.
     */
    private fun metadataSigningKmsProviderId(): String? =
        if (keyNameResolver == null) namespaceProperty("signingKmsProviderId")?.takeIf { it.isNotBlank() } else null

    private fun tenantId(): String? = execution.tenantId.takeIf { it.isNotBlank() }

    private fun instanceId(): String? = instanceIdProvider()?.takeIf { it.isNotBlank() }

    /**
     * Issuer-wide clock-skew tolerance (seconds). YAML:
     * `<namespace>.issuance-clock-skew-in-seconds` (kebab-case). The config
     * layer normalises to dot-delimited lowercase for lookup.
     */
    override val issuanceClockSkewInSeconds: Long
        get() =
            namespaceProperty("issuance.clock.skew.in.seconds")
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 60L

    /**
     * Credential-endpoint completeness-gate policy for missing required claims. YAML:
     * `<namespace>.missing-required-claims` (kebab-case), values `reject` (default) / `defer`.
     * See [MissingRequiredClaimsPolicy].
     */
    override val missingRequiredClaims: MissingRequiredClaimsPolicy
        get() =
            when (namespaceProperty("missing-required-claims")?.trim()?.lowercase()) {
                "defer" -> MissingRequiredClaimsPolicy.DEFER
                else -> MissingRequiredClaimsPolicy.REJECT
            }

    /**
     * Signing key for issuer metadata, used to produce `signed_metadata` (OID4VCI §11.2.4).
     *
     * `signed_metadata` is OPTIONAL, and publishing it commits the issuer to a signature the
     * holder MUST be able to verify (it resolves the signer via the issuer's published JWKS).
     * Emitting it therefore requires an explicit opt-in (`<namespace>.signed-metadata.enabled`,
     * default false) and a resolvable signing key. This keeps it decoupled from the issuer
     * signing key's primary role (credential signing): a tenant having a signing key does not
     * by itself force unverifiable signed metadata onto holders.
     *
     * A key that does not resolve yields null, so the metadata is served unsigned and a JWT request
     * is refused. Nothing is generated and no configured alias stands in.
     */
    override suspend fun signingKey(): ManagedIdentifierOptsOrResult? {
        val enabled = namespaceProperty("signed-metadata.enabled")?.toBoolean() ?: false
        if (!enabled) return null
        val keyName = metadataSigningKeyName() ?: return null
        return ManagedOptsKeyInfo(
            identifier =
                KeyInfo<KeyType>(
                    alias = keyName,
                    providerId = metadataSigningKmsProviderId(),
                ),
        )
    }

    /**
     * ECDH-ES decryption key for OID4VCI 1.0 §11.2.4 `credential_request_encryption`. A bound
     * [IssuerKeyNameResolver] answers from the deployment's own server-side binding and neither
     * `<namespace>.encryption.request.decryptionKeyAlias` nor
     * `<namespace>.encryption.request.decryptionKmsProviderId` is read. Without one, the
     * deployment's own configured alias and provider are used. The metadata builder resolves the
     * public JWK at runtime via the KMS so private key material never lives in YAML or git.
     *
     * Uses `ManagedOptsKeyInfo` with `keyVisibility = PRIVATE` rather than the simpler
     * `ManagedOptsAlias` because the latter defaults to PUBLIC visibility — the keystore then
     * strips the private scalar (`d`) on read and `DecryptJweCommandImpl` rejects with
     * "Decryptor key must be a private key (must have 'd' parameter)". Same gotcha the
     * OID4VP verifier's `DirectPostResponseEndpointCommand` calls out for JARM decryption.
     */
    override suspend fun credentialRequestDecryptionKey(): ManagedIdentifierOptsOrResult? {
        val resolver = keyNameResolver?.invoke()
        val keyName =
            if (resolver == null) {
                namespaceProperty("encryption.request.decryptionKeyAlias")?.takeIf { it.isNotBlank() }
            } else {
                val tenantId = tenantId() ?: return null
                val instanceId = instanceId() ?: return null
                resolver.resolveRequestDecryptionKeyName(tenantId, instanceId)?.takeIf { it.isNotBlank() }
            } ?: return null
        return ManagedOptsKeyInfo(
            identifier =
                KeyInfo<KeyType>(
                    alias = keyName,
                    providerId =
                        if (resolver == null) {
                            namespaceProperty("encryption.request.decryptionKmsProviderId")?.takeIf { it.isNotBlank() }
                        } else {
                            null
                        },
                    keyVisibility = KeyVisibility.PRIVATE,
                ),
        )
    }

    override val display: List<DisplayProperties>?
        get() {
            val name =
                namespaceProperty("display.name")
                    ?: return null
            val locale = namespaceProperty("display.locale")
            return listOf(DisplayProperties(name = name, locale = locale))
        }

    override val credentialConfigurations: Map<String, CredentialConfigurationSupported>
        get() {
            val ids = credentialConfigIds() ?: return emptyMap()

            return ids.associateWith { id -> buildCredentialConfiguration(id) }
        }

    override val credentialResponseEncryption: MetadataCredentialResponseEncryption?
        get() {
            val mode =
                encryptionMode(
                    modeKey = "encryption.response.mode",
                    legacyRequiredKey = "encryption.response.encryptionRequired",
                )
            if (mode == EncryptionMode.DISABLED) return null

            val algValues =
                namespaceProperty("encryption.response.algValuesSupported")
                    ?.splitComma()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null
            val encValues =
                namespaceProperty("encryption.response.encValuesSupported")
                    ?.splitComma()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null
            // Empty `zip_values_supported` → omit the field on the wire (it's OPTIONAL per
            // OID4VCI 1.0 §11.2.4 and a 1.1 addition; 1.0 conformance fails on `[]` vs absent).
            val zipValues =
                namespaceProperty("encryption.response.zipValuesSupported")
                    ?.splitComma()
                    ?.takeIf { it.isNotEmpty() }
            return MetadataCredentialResponseEncryption(
                algValuesSupported = algValues,
                encValuesSupported = encValues,
                zipValuesSupported = zipValues,
                encryptionRequired = mode == EncryptionMode.REQUIRED,
            )
        }

    override val credentialRequestEncryption: MetadataCredentialRequestEncryption?
        get() {
            // Two ways to opt in: either a static `jwks` JSON-string (legacy / testing only —
            // pastes a full JWKS into config), or a `decryption-key-alias` that points at a KMS
            // alias whose public JWK the metadata builder publishes at runtime. The KMS path is
            // the production answer; never commit a private key to YAML / git.
            val mode =
                encryptionMode(
                    modeKey = "encryption.request.mode",
                    legacyRequiredKey = "encryption.request.encryptionRequired",
                )
            if (mode == EncryptionMode.DISABLED) return null

            val staticJwksJson = namespaceProperty("encryption.request.jwks")
            // While an IssuerKeyNameResolver is bound the decryption key is server-derived and the
            // configured alias is not read, so the opt-in rests on the mode alone. The metadata
            // builder still omits the field when [credentialRequestDecryptionKey] refuses.
            val hasKmsDecryptionKey =
                keyNameResolver != null || namespaceProperty("encryption.request.decryptionKeyAlias")?.isNotBlank() == true
            if (staticJwksJson == null && !hasKmsDecryptionKey) return null

            val encValues =
                namespaceProperty("encryption.request.encValuesSupported")
                    ?.splitComma()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null
            val zipValues =
                namespaceProperty("encryption.request.zipValuesSupported")
                    ?.splitComma()
                    ?.takeIf { it.isNotEmpty() }

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
                encryptionRequired = mode == EncryptionMode.REQUIRED,
            )
        }

    override val batchCredentialIssuance: BatchCredentialIssuance?
        get() {
            val batchSize = namespaceProperty("batch.maxSize")?.toIntOrNull() ?: return null
            return BatchCredentialIssuance(batchSize = batchSize)
        }

    override val preferredKeyStorageStatusPeriodSeconds: Int?
        get() = namespaceProperty("preferredKeyStorageStatusPeriodSeconds")?.toIntOrNull()?.takeIf { it > 0 }

    override suspend fun credentialSigningConfigs(): Map<String, CredentialSigningConfig> {
        val ids = credentialConfigIds() ?: return emptyMap()
        val issuerKeyName = metadataSigningKeyName()
        return ids.associateWith { id -> buildCredentialSigningConfig(id, issuerKeyName) }
    }

    override val keyAttesterTrustConfigs: Map<String, Map<String, KeyAttesterTrustConfig>>
        get() {
            val ids = credentialConfigIds() ?: return emptyMap()

            return ids
                .associateWith { id -> buildKeyAttesterTrustConfigsForCredential(id) }
                .filterValues { it.isNotEmpty() }
        }

    // -------------------------------------------------------------------------
    // Per-credential configuration building
    // -------------------------------------------------------------------------

    private fun buildCredentialConfiguration(configId: String): CredentialConfigurationSupported {
        val formatValue = credentialProperty(configId, "format")
        val format =
            formatValue?.let { CredentialFormat.fromValue(it) }
                ?: CredentialFormat.SD_JWT_VC

        val scope = credentialProperty(configId, "scope")
        val vct = credentialProperty(configId, "vct")?.takeIf { it.isNotEmpty() }
        val doctype = credentialProperty(configId, "doctype")?.takeIf { it.isNotEmpty() }

        val signingAlgorithms =
            credentialOrDefaultProperty(configId, "signingAlgorithms")
                ?.splitComma()
                ?.mapNotNull { JwaAlgorithm.fromValue(it) }
                ?: emptyList()

        val bindingMethods =
            credentialOrDefaultProperty(configId, "bindingMethods")
                ?.splitComma()
                ?: emptyList()

        val proofTypes = buildProofTypes(configId)
        val proofTypeAttestations = buildProofTypeAttestations(configId, proofTypes.keys)
        val credentialDefinitionTypes =
            credentialProperty(configId, "credentialDefinition.types")
                ?.splitComma()

        // Read claim names from config
        val claimNames = discoverClaimNames(configId)

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
                    credentialClaimProperty(configId, claimName, "mandatory")?.toBoolean() ?: false
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
                val claimDisplays = buildClaimDisplays(configId, claimName)
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
            buildCredentialDisplays(configId).forEach { (locale, d) ->
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
        namespaceSubProperties(displayPrefix)
            .keys
            .mapNotNull { key ->
                val first = key.split('.').firstOrNull() ?: return@mapNotNull null
                if (first.startsWith("[") && first.endsWith("]")) {
                    first.removeSurrounding("[", "]").takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }.distinct()

    /** Read the per-locale credential display map under `<namespace>.credentials.[<id>].display.[<locale>]`. */
    private fun buildCredentialDisplays(configId: String): Map<String, CredentialDisplayEntry> =
        discoverDisplayLocales("credentials.[$configId].display")
            .mapNotNull { locale ->
                val name = credentialProperty(configId, "display.[$locale].name") ?: return@mapNotNull null
                locale to
                    CredentialDisplayEntry(
                        name = name,
                        description = credentialProperty(configId, "display.[$locale].description"),
                        backgroundColor = credentialProperty(configId, "display.[$locale].backgroundColor"),
                        textColor = credentialProperty(configId, "display.[$locale].textColor"),
                        logoUri = credentialProperty(configId, "display.[$locale].logo.uri"),
                        logoAltText = credentialProperty(configId, "display.[$locale].logo.altText"),
                        backgroundImageUri = credentialProperty(configId, "display.[$locale].backgroundImage.uri"),
                        backgroundImageAltText = credentialProperty(configId, "display.[$locale].backgroundImage.altText"),
                    )
            }.toMap()

    /** Read the per-locale claim display map under `<namespace>.credentials.[<id>].claims.[<claim>].display.[<locale>]`. */
    private fun buildClaimDisplays(
        configId: String,
        claimName: String,
    ): Map<String, ClaimDisplayEntry> =
        discoverDisplayLocales("credentials.[$configId].claims.[$claimName].display")
            .mapNotNull { locale ->
                val label =
                    credentialClaimProperty(configId, claimName, "display.[$locale].label")
                        ?: credentialClaimProperty(configId, claimName, "display.[$locale].name")
                        ?: return@mapNotNull null
                locale to ClaimDisplayEntry(label = label, description = credentialClaimProperty(configId, claimName, "display.[$locale].description"))
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
        val ids = credentialConfigIds() ?: return emptyMap()
        val out = LinkedHashMap<String, VctRef>()
        for (configId in ids) {
            val vctUrl = credentialProperty(configId, "vct")?.takeIf { it.isNotEmpty() } ?: continue
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
        val displays =
            buildCredentialDisplays(ref.configId).map { (locale, d) ->
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
            discoverClaimNames(ref.configId).map { claimName ->
                val mandatory =
                    credentialClaimProperty(ref.configId, claimName, "mandatory")?.toBoolean() ?: false
                // Selective-disclosure flag per claim (SD-JWT VC type metadata). Defaults to ALWAYS
                // (the claim is individually disclosable, i.e. optional/toggleable for a verifier);
                // set `sd: allowed` or `sd: never` to make a claim required / always-present.
                val sd =
                    credentialClaimProperty(ref.configId, claimName, "sd")?.let { parseClaimSd(it) }
                        ?: ClaimSdMetadata.ALWAYS
                val claimDisplays =
                    buildClaimDisplays(ref.configId, claimName).map { (locale, cd) ->
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
    private fun discoverClaimNames(configId: String): List<String> {
        val claimsPrefix = "credentials.[$configId].claims"
        val attributeSuffixes = setOf("mandatory", "display")
        return namespaceSubProperties(claimsPrefix)
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

    private fun buildCredentialSigningConfig(
        configId: String,
        issuerKeyName: String?,
    ): CredentialSigningConfig {
        // While an [IssuerKeyNameResolver] is bound it is the only source of the signing key name, so
        // neither `credentials.[<id>].signingKeyAlias` nor `credentialDefaults.signingKeyAlias` is
        // read: a per-credential configuration value must never name key material, and a null
        // issuer key means refuse rather than fall back. Without a bound seam the deployment's own
        // configured alias applies, falling back to the issuer-level signing key so a deployment
        // provisioning a single tenant key need not repeat it on every credential configuration.
        val signingKeyName =
            if (keyNameResolver == null) {
                credentialOrDefaultProperty(configId, "signingKeyAlias") ?: issuerKeyName
            } else {
                issuerKeyName
            }
        val signingKeyMode =
            SigningKeyMode.fromConfig(
                credentialOrDefaultProperty(configId, "signingKeyMode"),
            )
        val signingVerificationMethodId = credentialOrDefaultProperty(configId, "signingVerificationMethodId")
        val signingCertChainPath = credentialOrDefaultProperty(configId, "signingCertChainPath")
        val expirationInDays =
            credentialOrDefaultProperty(configId, "validityPeriod")?.toValidityDays()
                ?: credentialOrDefaultProperty(configId, "expirationInDays")?.toPositiveIntOrNull()

        return CredentialSigningConfig(
            signingKeyAlias = signingKeyName,
            signingKeyMode = signingKeyMode,
            signingVerificationMethodId = signingVerificationMethodId,
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
    //   <namespace>.credentials.[<configId>].status.statusListId: revocation

    override val statusListBindings: Map<String, StatusListBinding>
        get() {
            val configIds = credentialConfigIds() ?: return emptyMap()
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
        val listId =
            (
                credentialOrDefaultProperty(credentialConfigId, "status.statusListId")
                    ?: credentialOrDefaultProperty(credentialConfigId, "statusListId")
            )?.takeIf { it.isNotBlank() }
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
        configId: String,
        proofTypeKeys: Set<String>,
    ): Map<String, KeyAttestationsRequired> =
        proofTypeKeys
            .associateWith { proofTypeKey ->
                val enabled =
                    credentialOrDefaultProperty(configId, "proofTypes.$proofTypeKey.keyAttestations.enabled")?.toBoolean() ?: false
                if (!enabled) return@associateWith null
                val keyStorage =
                    credentialOrDefaultProperty(configId, "proofTypes.$proofTypeKey.keyAttestations.keyStorage")
                        ?.splitComma()
                        ?.takeIf { it.isNotEmpty() }
                val userAuth =
                    credentialOrDefaultProperty(configId, "proofTypes.$proofTypeKey.keyAttestations.userAuthentication")
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
        val proofTypeKeys = buildProofTypes(configId).keys
        if (proofTypeKeys.isEmpty()) return emptyMap()
        return proofTypeKeys
            .mapNotNull { proofType ->
                val trustPrefix = "proofTypes.$proofType.keyAttestations.keyAttesterTrust"
                val trustMode = credentialProperty(configId, "$trustPrefix.mode")?.trim()?.takeIf { it.isNotEmpty() }
                val jwks = parseTrustedJwks(credentialProperty(configId, "$trustPrefix.jwks"))
                val issuers = credentialProperty(configId, "$trustPrefix.issuers")?.splitComma()?.takeIf { it.isNotEmpty() }
                val x509Paths =
                    credentialProperty(configId, "$trustPrefix.x509AnchorPaths")
                        ?.splitComma()
                        ?.takeIf { it.isNotEmpty() }
                val requireWalletUnitEvidence =
                    credentialProperty(configId, "$trustPrefix.requireWalletUnitEvidence")
                        ?.toBooleanStrictOrNull()
                        ?: false
                if (trustMode == null && jwks == null && issuers == null && x509Paths == null && !requireWalletUnitEvidence) return@mapNotNull null
                proofType to
                    KeyAttesterTrustConfig(
                        mode = trustMode,
                        trustedJwks = jwks,
                        trustedIssuers = issuers,
                        x509TrustAnchorPaths = x509Paths,
                        requireWalletUnitEvidence = requireWalletUnitEvidence,
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
    private fun buildProofTypes(configId: String): Map<String, List<String>> {
        // Strategy 1: explicit comma-separated list of proof type names
        val explicitTypes = credentialOrDefaultProperty(configId, "proofTypes")?.splitComma()

        // Strategy 2: discover from sub-property keys (e.g. proofTypes.jwt.signingAlgorithms -> "jwt")
        val discoveredTypes =
            if (explicitTypes.isNullOrEmpty()) {
                (namespaceSubProperties("credentialDefaults.proofTypes").keys + namespaceSubProperties("credentials.[$configId].proofTypes").keys)
                    .mapNotNull { key -> key.split(".").firstOrNull() }
                    .distinct()
            } else {
                explicitTypes
            }

        val result = mutableMapOf<String, List<String>>()
        for (proofTypeKey in discoveredTypes) {
            val algorithms =
                credentialOrDefaultProperty(configId, "proofTypes.$proofTypeKey.signingAlgorithms")
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

    private enum class EncryptionMode {
        DISABLED,
        SUPPORTED,
        REQUIRED,
    }

    private fun encryptionMode(
        modeKey: String,
        legacyRequiredKey: String,
    ): EncryptionMode {
        when (namespaceProperty(modeKey)?.trim()?.lowercase()) {
            "disabled" -> return EncryptionMode.DISABLED
            "supported" -> return EncryptionMode.SUPPORTED
            "required" -> return EncryptionMode.REQUIRED
            null, "" -> Unit
            else -> return EncryptionMode.DISABLED
        }
        return if (namespaceProperty(legacyRequiredKey)?.toBoolean() == true) {
            EncryptionMode.REQUIRED
        } else {
            EncryptionMode.DISABLED
        }
    }

    private fun String.toValidityDays(): Int? {
        val match = Regex("""P(\d+)([DMY])""").matchEntire(trim().uppercase()) ?: return null
        val amount = match.groupValues[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val days =
            when (match.groupValues[2]) {
                "D" -> amount
                "M" -> amount * 30L
                "Y" -> amount * 365L
                else -> return null
            }
        return days.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
    }

    private fun String.toPositiveIntOrNull(): Int? =
        trim()
            .toIntOrNull()
            ?.takeIf { it > 0 }

    private fun String.splitComma(): List<String> = split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
