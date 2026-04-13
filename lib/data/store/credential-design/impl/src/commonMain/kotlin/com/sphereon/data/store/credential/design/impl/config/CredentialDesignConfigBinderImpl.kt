/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.credential.design.impl.config

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.data.store.credential.design.config.CredentialDesignConfigProvider
import com.sphereon.data.store.credential.design.model.CredentialDesignModuleConfig
import com.sphereon.data.store.credential.design.model.CredentialDesignRefreshConfig
import com.sphereon.data.store.credential.design.model.CredentialDesignValidationConfig
import com.sphereon.data.store.credential.design.model.CredentialMetadataPreference
import com.sphereon.data.store.credential.design.model.DesignResolutionPolicy
import com.sphereon.data.store.credential.design.model.IssuerMetadataPreference
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Binds credential-design configuration from IDK's ConfigService.
 *
 * Configuration properties use the prefix "credential-design":
 *
 * ```properties
 * credential-design.policy.schema-hints-enabled=true
 * credential-design.policy.json-ld-context-hints-enabled=true
 * credential-design.policy.markdown-rendering-enabled=false
 * credential-design.policy.issuer-metadata-preference=OID4VCI_FIRST
 * credential-design.policy.credential-metadata-preference=SD_JWT_FIRST
 * credential-design.policy.prefer-integrity-protected-sources=true
 * credential-design.policy.allow-remote-template-fetch=false
 * credential-design.policy.allow-remote-asset-fetch=false
 * credential-design.refresh.enabled=false
 * credential-design.refresh.default-ttl-seconds=86400
 * credential-design.refresh.rehost-remote-assets=true
 * credential-design.refresh.max-asset-size-bytes=5000000
 * credential-design.validation.max-bindings-per-design=16
 * credential-design.validation.max-displays-per-design=64
 * credential-design.validation.max-claims-per-design=256
 * credential-design.validation.max-render-variants-per-design=32
 * credential-design.validation.max-entry-codes-per-claim=1024
 * credential-design.validation.fail-on-unknown-source-type=true
 * credential-design.validation.validate-on-read=false
 * ```
 *
 * ## YAML Configuration
 *
 * ```yaml
 * sphereon:
 *   app:
 *     credential-design:
 *       policy:
 *         schema-hints-enabled: true
 *         json-ld-context-hints-enabled: true
 *         markdown-rendering-enabled: false
 *         issuer-metadata-preference: OID4VCI_FIRST
 *         credential-metadata-preference: SD_JWT_FIRST
 *         prefer-integrity-protected-sources: true
 *         allow-remote-template-fetch: false
 *         allow-remote-asset-fetch: false
 *       refresh:
 *         enabled: false
 *         default-ttl-seconds: 86400
 *         rehost-remote-assets: true
 *         max-asset-size-bytes: 5000000
 *       validation:
 *         max-bindings-per-design: 16
 *         max-displays-per-design: 64
 *         max-claims-per-design: 256
 *         max-render-variants-per-design: 32
 *         max-entry-codes-per-claim: 1024
 *         fail-on-unknown-source-type: true
 *         validate-on-read: false
 * ```
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CredentialDesignConfigProvider>())
class CredentialDesignConfigBinderImpl(
    private val execution: SessionExecution,
) : CredentialDesignConfigProvider {
    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    private val cachedConfig: CredentialDesignModuleConfig by lazy { bindConfig() }

    override fun getConfig(): CredentialDesignModuleConfig = cachedConfig

    private fun bindConfig(): CredentialDesignModuleConfig {
        val prefix = CONFIG_PREFIX
        val defaults = CredentialDesignModuleConfig()

        return CredentialDesignModuleConfig(
            policy = bindPolicy("$prefix.policy", defaults.policy),
            refresh = bindRefresh("$prefix.refresh", defaults.refresh),
            validation = bindValidation("$prefix.validation", defaults.validation),
        )
    }

    private fun bindPolicy(
        prefix: String,
        defaults: DesignResolutionPolicy,
    ): DesignResolutionPolicy {
        // getProperty returns T? but always returns the non-null default when no config value is found
        return DesignResolutionPolicy(
            schemaHintsEnabled =
                configService.getProperty(
                    "$prefix.schema-hints-enabled",
                    Boolean::class,
                    defaults.schemaHintsEnabled,
                )!!,
            jsonLdContextHintsEnabled =
                configService.getProperty(
                    "$prefix.json-ld-context-hints-enabled",
                    Boolean::class,
                    defaults.jsonLdContextHintsEnabled,
                )!!,
            markdownRenderingEnabled =
                configService.getProperty(
                    "$prefix.markdown-rendering-enabled",
                    Boolean::class,
                    defaults.markdownRenderingEnabled,
                )!!,
            issuerMetadataPreference =
                configService
                    .getPropertyAsString("$prefix.issuer-metadata-preference")
                    ?.let { enumValueOfOrNull<IssuerMetadataPreference>(it) }
                    ?: defaults.issuerMetadataPreference,
            credentialMetadataPreference =
                configService
                    .getPropertyAsString("$prefix.credential-metadata-preference")
                    ?.let { enumValueOfOrNull<CredentialMetadataPreference>(it) }
                    ?: defaults.credentialMetadataPreference,
            preferIntegrityProtectedSources =
                configService.getProperty(
                    "$prefix.prefer-integrity-protected-sources",
                    Boolean::class,
                    defaults.preferIntegrityProtectedSources,
                )!!,
            allowRemoteTemplateFetch =
                configService.getProperty(
                    "$prefix.allow-remote-template-fetch",
                    Boolean::class,
                    defaults.allowRemoteTemplateFetch,
                )!!,
            allowRemoteAssetFetch =
                configService.getProperty(
                    "$prefix.allow-remote-asset-fetch",
                    Boolean::class,
                    defaults.allowRemoteAssetFetch,
                )!!,
        )
    }

    private fun bindRefresh(
        prefix: String,
        defaults: CredentialDesignRefreshConfig,
    ): CredentialDesignRefreshConfig =
        CredentialDesignRefreshConfig(
            enabled =
                configService.getProperty(
                    "$prefix.enabled",
                    Boolean::class,
                    defaults.enabled,
                )!!,
            defaultTtlSeconds =
                configService.getProperty(
                    "$prefix.default-ttl-seconds",
                    Long::class,
                    defaults.defaultTtlSeconds,
                )!!,
            rehostRemoteAssets =
                configService.getProperty(
                    "$prefix.rehost-remote-assets",
                    Boolean::class,
                    defaults.rehostRemoteAssets,
                )!!,
            maxAssetSizeBytes =
                configService.getProperty(
                    "$prefix.max-asset-size-bytes",
                    Long::class,
                    defaults.maxAssetSizeBytes,
                )!!,
        )

    private fun bindValidation(
        prefix: String,
        defaults: CredentialDesignValidationConfig,
    ): CredentialDesignValidationConfig =
        CredentialDesignValidationConfig(
            maxBindingsPerDesign =
                configService.getProperty(
                    "$prefix.max-bindings-per-design",
                    Int::class,
                    defaults.maxBindingsPerDesign,
                )!!,
            maxDisplaysPerDesign =
                configService.getProperty(
                    "$prefix.max-displays-per-design",
                    Int::class,
                    defaults.maxDisplaysPerDesign,
                )!!,
            maxClaimsPerDesign =
                configService.getProperty(
                    "$prefix.max-claims-per-design",
                    Int::class,
                    defaults.maxClaimsPerDesign,
                )!!,
            maxRenderVariantsPerDesign =
                configService.getProperty(
                    "$prefix.max-render-variants-per-design",
                    Int::class,
                    defaults.maxRenderVariantsPerDesign,
                )!!,
            maxEntryCodesPerClaim =
                configService.getProperty(
                    "$prefix.max-entry-codes-per-claim",
                    Int::class,
                    defaults.maxEntryCodesPerClaim,
                )!!,
            failOnUnknownSourceType =
                configService.getProperty(
                    "$prefix.fail-on-unknown-source-type",
                    Boolean::class,
                    defaults.failOnUnknownSourceType,
                )!!,
            validateOnRead =
                configService.getProperty(
                    "$prefix.validate-on-read",
                    Boolean::class,
                    defaults.validateOnRead,
                )!!,
        )

    companion object {
        const val CONFIG_PREFIX = "credential-design"

        private inline fun <reified T : Enum<T>> enumValueOfOrNull(name: String): T? =
            try {
                enumValueOf<T>(name.trim().uppercase().replace('-', '_'))
            } catch (_: IllegalArgumentException) {
                // Ignored: enum value not recognized
                null
            }
    }
}
