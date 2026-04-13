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
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.dsl.credentialConfiguration
import com.sphereon.openid.oid4vci.common.model.BatchCredentialIssuance
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialRequestEncryption
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialResponseEncryption
import com.sphereon.openid.oid4vci.issuer.config.CredentialIssuancePolicyConfig
import com.sphereon.openid.oid4vci.issuer.config.CredentialSigningConfig
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
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
            val algValues = configService.getPropertyAsString("$prefix.algValuesSupported")?.splitComma() ?: return null
            val encValues = configService.getPropertyAsString("$prefix.encValuesSupported")?.splitComma() ?: return null
            val zipValues = configService.getPropertyAsString("$prefix.zipValuesSupported")?.splitComma()
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
            val jwksJson = configService.getPropertyAsString("$prefix.jwks") ?: return null
            val jwks = Json.parseToJsonElement(jwksJson).jsonObject
            val encValues = configService.getPropertyAsString("$prefix.encValuesSupported")?.splitComma() ?: return null
            val zipValues = configService.getPropertyAsString("$prefix.zipValuesSupported")?.splitComma()
            val required = configService.getPropertyAsString("$prefix.encryptionRequired")?.toBoolean() ?: false
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

            for (claimName in claimNames) {
                val mandatory =
                    configService.getProperty("$prefix.claims.$claimName.mandatory", Boolean::class, false)
                        ?: false
                claim(claimName) {
                    this.mandatory = mandatory
                    display(claimName.replace('_', ' ').replaceFirstChar { it.uppercase() })
                }
            }

            signingAlgorithms.forEach { alg -> signingAlg(alg) }
            bindingMethods.forEach { method -> bindingMethod(method) }

            proofTypes.forEach { (proofTypeKey, algValues) ->
                proofType(proofTypeKey, *algValues.toTypedArray())
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

    private fun discoverClaimNames(prefix: String): List<String> {
        val candidates =
            listOf(
                "given_name",
                "family_name",
                "email",
                "birth_date",
                "age_over_18",
                "nationality",
                "resident_country",
                "resident_city",
                "resident_postal_code",
                "resident_street",
                "issuing_authority",
                "issuing_country",
                "document_number",
                "gender",
                "birth_place",
            )
        return candidates.filter { name ->
            configService.getProperty("$prefix.claims.$name.mandatory", Boolean::class, null) != null
        }
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
            configService.getPropertyAsString("$prefix.expiration.in.days")?.toIntOrNull()?.takeIf { it > 0 }

        return CredentialSigningConfig(
            signingKeyAlias = signingKeyAlias,
            signingKeyMode = signingKeyMode,
            signingCertChainPath = signingCertChainPath,
            expirationInDays = expirationInDays,
        )
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
