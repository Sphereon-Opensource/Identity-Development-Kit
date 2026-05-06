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

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.dsl.credentialConfiguration
import com.sphereon.openid.oid4vci.common.model.BatchCredentialIssuance
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.KeyAttestationsRequired
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialRequestEncryption
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialResponseEncryption
import com.sphereon.openid.oid4vci.issuer.config.CredentialIssuancePolicyConfig
import com.sphereon.openid.oid4vci.issuer.config.CredentialSigningConfig
import com.sphereon.openid.oid4vci.issuer.config.KeyAttesterTrustConfig
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * [Oid4vciIssuerConfigProvider] backed by IDK's ConfigService.
 *
 * Reads issuer metadata and credential configurations from principal config, allowing
 * tenant/app overrides. Credential configuration IDs are declared explicitly via a
 * comma-separated property rather than discovered by prefix scan.
 *
 * Example configuration (application.yml):
 * ```yaml
 * sphereon:
 *   oid4vci:
 *     issuer:
 *       identifier: https://issuer.example.com
 *       baseUrl: https://issuer.example.com
 *       authorizationServers: https://as.example.com
 *       credentialConfigurationIds: UniversityDegree,MembershipCard
 *       display:
 *         name: Example University
 *         locale: en-US
 *       batchSize: 5
 *       credentials:
 *         UniversityDegree:
 *           format: jwt_vc_json
 *           scope: degree
 *           signingAlgorithms: ES256,ES384
 *           bindingMethods: jwk,did:key
 *           proofTypes: jwt,cwt   # optional explicit list; otherwise discovered from sub-keys
 *             jwt:
 *               signingAlgorithms: ES256
 *             cwt:
 *               signingAlgorithms: ES256
 *           credentialDefinition:
 *             types: VerifiableCredential,UniversityDegreeCredential
 *           display:
 *             name: University Degree
 *             locale: en-US
 * ```
 *
 * Or via environment variables:
 * ```
 * SPHEREON_OID4VCI_ISSUER_IDENTIFIER=https://issuer.example.com
 * SPHEREON_OID4VCI_ISSUER_AUTHORIZATIONSERVERS=https://as.example.com
 * SPHEREON_OID4VCI_ISSUER_CREDENTIALCONFIGURATIONIDS=UniversityDegree,MembershipCard
 * SPHEREON_OID4VCI_ISSUER_DISPLAY_NAME=Example University
 * SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_FORMAT=jwt_vc_json
 * SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_SCOPE=degree
 * SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_SIGNINGALGORITHMS=ES256,ES384
 * SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_BINDINGMETHODS=jwk,did:key
 * SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_PROOFTYPES_JWT_SIGNINGALGORITHMS=ES256
 * SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_CREDENTIALDEFINITION_TYPES=VerifiableCredential,UniversityDegreeCredential
 * SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_DISPLAY_NAME=University Degree
 * ```
 *
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciIssuerConfigProvider>())
class ConfigDrivenOid4vciIssuerConfigProvider(
    private val execution: SessionExecution,
) : Oid4vciIssuerConfigProvider {
    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    override val issuerIdentifier: String
        get() = configService.getPropertyAsString("$NAMESPACE.identifier") ?: ""

    override val authorizationServers: List<String>?
        get() =
            configService
                .getPropertyAsString("$NAMESPACE.authorizationServers")
                ?.splitComma()
                ?.takeIf { it.isNotEmpty() }

    /** Raw metadata signing alias read from `sphereon.oid4vci.issuer.signingKeyAlias`. */
    override val metadataSigningKeyAlias: String?
        get() = configService.getPropertyAsString("$NAMESPACE.signingKeyAlias")?.takeIf { it.isNotBlank() }

    /**
     * Issuer-wide clock-skew tolerance (seconds). YAML:
     * `sphereon.oid4vci.issuer.issuance-clock-skew-in-seconds` (kebab-case). The config
     * layer normalises to dot-delimited lowercase for lookup.
     */
    override val issuanceClockSkewInSeconds: Long
        get() =
            configService
                .getPropertyAsString("$NAMESPACE.issuance.clock.skew.in.seconds")
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 60L

    /**
     * Signing key for issuer metadata, read from `sphereon.oid4vci.issuer.signingKeyAlias`.
     * Resolved against the KMS by alias when producing signed_metadata (OID4VCI §13.2.4).
     */
    override val signingKey: ManagedIdentifierOptsOrResult?
        get() = metadataSigningKeyAlias?.let { alias -> ManagedOptsAlias(identifier = alias) }

    /**
     * ECDH-ES decryption key alias for OID4VCI 1.0 §11.2.4 `credential_request_encryption`.
     * Read from `sphereon.oid4vci.issuer.encryption.request.decryptionKeyAlias`. The metadata
     * builder resolves the public JWK at runtime via the KMS so we never store private key
     * material in YAML / git.
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
                .getPropertyAsString("$NAMESPACE.encryption.request.decryptionKeyAlias")
                ?.takeIf { it.isNotBlank() }
                ?.let { alias ->
                    ManagedOptsKeyInfo(
                        identifier =
                            KeyInfo<KeyType>(
                                alias = alias,
                                keyVisibility = KeyVisibility.PRIVATE,
                            ),
                    )
                }

    override val display: List<DisplayProperties>?
        get() {
            val name =
                configService.getPropertyAsString("$NAMESPACE.display.name")
                    ?: return null
            val locale = configService.getPropertyAsString("$NAMESPACE.display.locale")
            return listOf(DisplayProperties(name = name, locale = locale))
        }

    override val credentialConfigurations: Map<String, CredentialConfigurationSupported>
        get() {
            val ids =
                configService
                    .getPropertyAsString("$NAMESPACE.credentialConfigurationIds")
                    ?.splitComma()
                    ?: return emptyMap()

            return ids.associateWith { id -> buildCredentialConfiguration(id) }
        }

    override val credentialResponseEncryption: MetadataCredentialResponseEncryption?
        get() {
            val prefix = "$NAMESPACE.encryption.response"
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
            val prefix = "$NAMESPACE.encryption.request"
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
            val batchSize = configService.getPropertyAsString("$NAMESPACE.batch.maxSize")?.toIntOrNull() ?: return null
            return BatchCredentialIssuance(batchSize = batchSize)
        }

    override val credentialSigningConfigs: Map<String, CredentialSigningConfig>
        get() {
            val ids =
                configService
                    .getPropertyAsString("$NAMESPACE.credentialConfigurationIds")
                    ?.splitComma()
                    ?: return emptyMap()

            return ids.associateWith { id -> buildCredentialSigningConfig(id) }
        }

    override val keyAttesterTrustConfigs: Map<String, Map<String, KeyAttesterTrustConfig>>
        get() {
            val ids =
                configService
                    .getPropertyAsString("$NAMESPACE.credentialConfigurationIds")
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
        val prefix = "${CredentialIssuancePolicyConfig.CONFIG_NAMESPACE}.[$configId]"

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
        val displayName = configService.getPropertyAsString("$prefix.display.name")
        val displayLocale = configService.getPropertyAsString("$prefix.display.locale")
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
                val displayLabel = path.last().replace('_', ' ').replaceFirstChar { it.uppercase() }
                claim(path) {
                    this.mandatory = mandatory
                    display(displayLabel)
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

            if (!displayName.isNullOrEmpty()) {
                display {
                    name = displayName
                    locale = displayLocale
                }
            }
        }
    }

    /**
     * Dynamically enumerate the claim names configured under `<prefix>.claims`.
     *
     * Claim keys MUST be bracket-quoted in the YAML/properties source so the
     * [PropertyKeyNormalizer] preserves them verbatim — otherwise underscore-to-dot
     * normalisation would mangle a name like `family_name` into `family.name` (silently
     * changing the credential's wire-level claim identifier). Bracket-quoting also lets
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
        val prefix = "${CredentialIssuancePolicyConfig.CONFIG_NAMESPACE}.[$configId]"

        val signingKeyAlias = configService.getPropertyAsString("$prefix.signingKeyAlias")
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
     * `oid4vci.issuer.credentials.[<id>].proof-types.<type>.key-attestations.key-attester-trust.{jwks,issuers,x509-anchor-paths}`
     * — trust is nested inside the `key-attestations` group it pairs with, since trust is
     * only meaningful when the policy is enabled.
     *
     * Returns the empty map when no carrier has any trust override; callers then fall back
     * to global X.509 anchors loaded by `lib/trust/x509`.
     */
    private fun buildKeyAttesterTrustConfigsForCredential(configId: String,): Map<String, KeyAttesterTrustConfig> {
        val credentialPrefix = "${CredentialIssuancePolicyConfig.CONFIG_NAMESPACE}.[$configId]"
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

    companion object {
        /** Root config namespace for all issuer metadata properties. */
        const val NAMESPACE = "oid4vci.issuer"
    }
}
