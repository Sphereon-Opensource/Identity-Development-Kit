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
 *
 */

package com.sphereon.crypto.core.kms

import com.sphereon.core.api.conf.CachedConfigValue
import com.sphereon.core.api.conf.CamelCaseKeyDenormalizerImpl
import com.sphereon.core.api.conf.ConfigBootstrapGuard
import com.sphereon.core.api.conf.ConfigEnvironment
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.ConfigSnapshot
import com.sphereon.core.api.conf.DefaultPolymorphicConfigBinder
import com.sphereon.core.api.conf.PropertyKeyNormalizerImpl
import com.sphereon.core.api.conf.ResolutionMetadata
import com.sphereon.core.api.conf.SnapshotKey
import com.sphereon.core.api.conf.SyncConfigSnapshotCache
import com.sphereon.core.api.conf.TypeSuffixEntryDetection
import com.sphereon.core.api.conf.refreshableContentRevision
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.crypto.core.json.CryptoJsonSupport
import com.sphereon.crypto.core.kms.KmsProviderConfigBinder.Companion.KMS_PROVIDERS_PREFIX
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("NoOpKmsProviderFactoryImpl", exact = true)
class NoOpKmsProviderFactoryImpl : KmsProviderFactory {
    override val kmsProviderType: String = KMS_PROVIDER_TYPE

    override fun create(
        config: KmsProviderConfigBase,
        execution: SessionExecution,
    ): KmsProvider = throw IllegalArgumentException("Please register your KMS Provider Factory or Factories!. No KMS Provider Factory found")

    companion object {
        const val KMS_PROVIDER_TYPE = "___NO_OP___"
    }
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("KmsProviderManagerImpl", exact = true)
class KmsProviderManagerImpl(
    factories: Set<KmsProviderFactory>,
    private val binder: KmsProviderConfigBinder,
    private val snapshotCache: SyncConfigSnapshotCache,
    appLogManager: AppLogManager,
) : KmsProviderManager {
    // Filter out the NO OP Factory which is only there to ensure injection works at all times
    private val factories =
        factories.filter { it.kmsProviderType != NoOpKmsProviderFactoryImpl.KMS_PROVIDER_TYPE }.toSet()
    private val log = appLogManager.withTag("KmsProviderManager")

    /**
     * ==================================================================================================================================
     * WARNING: Make sure this implementation will not contain state. It is scoped to the App and currently only uses factories/builders!
     * ==================================================================================================================================
     * However, KMS providers themselves are typically bound to the context or session scopes.
     * Hence, why you pass in a scoped config service for one of the methods
     */
    override fun createFromProviderConfig(
        config: KmsProviderConfigBase,
        execution: SessionExecution,
    ): KmsProvider =
        factories.find { it.kmsProviderType == config.kmsProviderType }?.create(config, execution)
            ?: throw IllegalArgumentException(
                "No KMSFactory found for type ${config.kmsProviderType}. Available factories: ${
                    factories.joinToString(
                        ",",
                    )
                }",
            )

    override fun createFromProperties(
        configService: ConfigService,
        execution: SessionExecution,
    ): Set<KmsProvider> =
        getBoundKmsProviderConfigs(configService, execution)
            .map {
                createFromProviderConfig(it, execution)
            }.toSet()

    private fun getBoundKmsProviderConfigs(
        configService: ConfigService,
        execution: SessionExecution,
    ): Array<KmsProviderConfigBase> {
        val allowSnapshotCache = !ConfigBootstrapGuard.isContextRegistrationInProgress()
        val snapshotKey = providerConfigSnapshotKey(configService, execution, refresh = allowSnapshotCache)
        if (!allowSnapshotCache) {
            log.debug(
                "VDX_KMS_PROVIDER_CONFIG_CACHE_BYPASS_BOOTSTRAP scope=${snapshotKey.scope} " +
                    "tenant=${snapshotKey.tenantId ?: "none"} principal=${snapshotKey.principalId ?: "none"} " +
                    "prefix=${snapshotKey.prefix}",
            )
            return binder.getKmsProviderConfigs(configService)
        }

        readProviderConfigSnapshot(snapshotKey)?.let {
            log.debug(
                "VDX_KMS_PROVIDER_CONFIG_CACHE_HIT scope=${snapshotKey.scope} tenant=${snapshotKey.tenantId ?: "none"} " +
                    "principal=${snapshotKey.principalId ?: "none"} prefix=${snapshotKey.prefix} size=${it.size}",
            )
            return it
        }

        log.debug(
            "VDX_KMS_PROVIDER_CONFIG_CACHE_MISS scope=${snapshotKey.scope} tenant=${snapshotKey.tenantId ?: "none"} " +
                "principal=${snapshotKey.principalId ?: "none"} prefix=${snapshotKey.prefix}",
        )
        val configs = binder.getKmsProviderConfigs(configService)
        writeProviderConfigSnapshot(snapshotKey, configs)
        return configs
    }

    private fun providerConfigSnapshotKey(
        configService: ConfigService,
        execution: SessionExecution,
        refresh: Boolean,
    ): SnapshotKey {
        val revision = configRevision(configService, refresh)
        return SnapshotKey(
            scope = configService.configLevel,
            tenantId =
                when (configService.configLevel) {
                    ConfigLevel.APP -> null
                    ConfigLevel.TENANT, ConfigLevel.PRINCIPAL -> execution.tenantId
                },
            principalId =
                when (configService.configLevel) {
                    ConfigLevel.PRINCIPAL -> execution.principalId
                    ConfigLevel.APP, ConfigLevel.TENANT -> null
                },
            prefix = "$KMS_PROVIDER_CONFIG_SNAPSHOT_PREFIX.$revision",
        )
    }

    private fun readProviderConfigSnapshot(snapshotKey: SnapshotKey): Array<KmsProviderConfigBase>? {
        val value =
            snapshotCache
                .getSnapshot(snapshotKey)
                ?.values
                ?.get(KMS_PROVIDER_CONFIGS_SNAPSHOT_VALUE)
                ?.value as? Array<*> ?: return null
        return value
            .filterIsInstance<KmsProviderConfigBase>()
            .takeIf { it.size == value.size }
            ?.toTypedArray()
    }

    private fun writeProviderConfigSnapshot(
        snapshotKey: SnapshotKey,
        configs: Array<KmsProviderConfigBase>,
    ) {
        val now = Clock.System.now()
        snapshotCache.putSnapshot(
            snapshotKey,
            ConfigSnapshot(
                values =
                    mapOf(
                        KMS_PROVIDER_CONFIGS_SNAPSHOT_VALUE to
                            CachedConfigValue(
                                value = configs,
                                metadata =
                                    ResolutionMetadata(
                                        source = "KmsProviderConfigBinder",
                                        scope = snapshotKey.scope,
                                        originalKey = KMS_PROVIDERS_PREFIX,
                                        normalizedKey = KMS_PROVIDERS_PREFIX,
                                        order = 0,
                                        isSecret = false,
                                        isInterpolated = false,
                                        resolvedAt = now,
                                        ttl = null,
                                    ),
                                cachedAt = now,
                                expiresAt = null,
                                preserveType = true,
                            ),
                    ),
                createdAt = now,
                expiresAt = null,
            ),
        )
        log.debug(
            "VDX_KMS_PROVIDER_CONFIG_CACHE_STORED scope=${snapshotKey.scope} tenant=${snapshotKey.tenantId ?: "none"} " +
                "principal=${snapshotKey.principalId ?: "none"} prefix=${snapshotKey.prefix} size=${configs.size}",
        )
    }

    private fun configRevision(
        config: ConfigEnvironment?,
        refresh: Boolean,
    ): Long {
        if (config == null) {
            return 0L
        }
        val sources = config.getPropertySources(includeParents = false)
        val localRevision = (sources.revision * 31L) + sources.refreshableContentRevision(refresh)
        return (localRevision * 31L) + configRevision(config.parent, refresh)
    }

    private companion object {
        const val KMS_PROVIDER_CONFIGS_SNAPSHOT_VALUE = "configs"
        const val KMS_PROVIDER_CONFIG_SNAPSHOT_PREFIX = "_derived.kms.provider-configs.bound"
    }
}

/**
 * KMS-specific entry detection strategy that excludes nested type fields like "keystore.type".
 */
private val KmsEntryDetection =
    TypeSuffixEntryDetection(
        discriminatorSuffix = ".type",
        excludedSuffixes =
            setOf(
                ".keystore.type",
                ".key.store.type",
            ),
    )

/**
 * Field aliases used only for config ingestion.
 *
 * This allows relaxed property keys (e.g., lowercase flat keys from property files/settings)
 * while preserving strict serializer field names for REST JSON contracts.
 */
private val KmsPropertyNameAliases =
    mapOf(
        // Base provider fields
        "exposeprivatekeysduringgeneration" to "exposePrivateKeysDuringGeneration",
        "exposeprivatekeys" to "exposePrivateKeysDuringGeneration",
        "persistkeysduringgeneration" to "persistKeysDuringGeneration",
        "defaultconfigvalues" to "defaultConfigValues",
        // Shared provider-specific fields
        "autocreatecertificate" to "autoCreateCertificate",
        "cryptographyprovider" to "cryptographyProvider",
        "cryptoprovider" to "cryptographyProvider",
        "restproviderid" to "restProviderId",
        "restkmsurl" to "url",
        "httpclientoptions" to "httpClientOptions",
        "authconfig" to "authConfig",
        "applicationid" to "applicationId",
        "keyvaulturl" to "keyvaultUrl",
        "credentialopts" to "credentialOpts",
        "hsmtype" to "hsmType",
        "exponentialbackoffretryopts" to "exponentialBackoffRetryOpts",
        // Keystore-related fields
        "keystore" to "keyStore",
        "keyvisibility" to "keyVisibility",
        "overwritealias" to "overwriteAlias",
        "accessmode" to "accessMode",
        "scopebinding" to "scopeBinding",
        // Legacy config field aliases
        "keystoretype" to "type",
        // Rest auth nested fields
        "authheader" to "authHeader",
        "tenantheader" to "tenantHeader",
        "principalheader" to "principalHeader",
        "usetenantfromcontext" to "useTenantFromContext",
        "useprincipalfromcontext" to "usePrincipalFromContext",
        "tenantid" to "tenantId",
        "principalid" to "principalId",
        // Azure credential nested fields
        "credentialmode" to "credentialMode",
        "secretcredentialopts" to "secretCredentialOpts",
        "certificatecredentialopts" to "certificateCredentialOpts",
        "interactivebrowsercredentialopts" to "interactiveBrowserCredentialOpts",
        "usernamepasswordcredentialopts" to "usernamePasswordCredentialOpts",
        "clientid" to "clientId",
        "clientsecret" to "clientSecret",
        "pemcertificatepath" to "pemCertificatePath",
        "redirecturl" to "redirectUrl",
        "username" to "userName",
        "maxretries" to "maxRetries",
        "basedelayinms" to "baseDelayInMS",
        "maxdelayinms" to "maxDelayInMS",
        // AWS KMS nested credential fields
        "accesskeycredentialopts" to "accessKeyCredentialOpts",
        "profilecredentialopts" to "profileCredentialOpts",
        "accesskeyid" to "accessKeyId",
        "secretaccesskey" to "secretAccessKey",
        "sessiontoken" to "sessionToken",
        "profilename" to "profileName",
    )

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<KmsProviderConfigBinder>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("KmsProviderConfigBinderImpl", exact = true)
class KmsProviderConfigBinderImpl(
    appLogManager: AppLogManager,
) : KmsProviderConfigBinder {
    private val log = appLogManager.withTag("KmsProviderConfigBinder")
    private val keyNormalizer = PropertyKeyNormalizerImpl.Default
    private val notFoundCode = "NOT_FOUND_ERROR"

    /**
     * Build prefixes for KMS provider configuration.
     */
    private fun buildPrefixes(_configService: ConfigService) = setOf(KMS_PROVIDERS_PREFIX)

    /**
     * Creates a polymorphic config binder for KMS providers using the given prefix.
     */
    private fun createPolymorphicBinder(prefix: String) =
        DefaultPolymorphicConfigBinder(
            prefix = prefix,
            baseClass = KmsProviderConfigBase::class,
            json = CryptoJsonSupport.serializer,
            entryDetection = KmsEntryDetection,
            idFieldName = "id",
            keyNormalizer = keyNormalizer,
            keyDenormalizer = CamelCaseKeyDenormalizerImpl(),
            nestedPrefixAliases =
                mapOf(
                    "keystore" to "keyStore",
                    "key.store" to "keyStore",
                ),
            propertyNameAliases = KmsPropertyNameAliases,
            ignoredPropertyNames = KmsOperationalMetadataKeys,
            redact = false,
        )

    override fun getKmsProviderIds(configService: ConfigService): Array<String> {
        log.debug("[KmsProviderConfigBinder] Getting KMS provider IDs from config service at level: ${configService.configLevel}")
        val prefixes = buildPrefixes(configService)
        log.debug("[KmsProviderConfigBinder] Using prefix: ${prefixes.joinToString(",")}")

        // Use polymorphic binder to detect entry IDs for each prefix.
        // Prefer the explicit config id field when present to preserve original IDs (e.g., with hyphens).
        val allIds = mutableSetOf<String>()
        for (prefix in prefixes) {
            val binder = createPolymorphicBinder(prefix)
            val configsResult = binder.getEntryConfigsAsMapResult(configService, strict = true)
            require(configsResult.isOk) {
                "Failed to bind KMS provider configs for prefix '$prefix': ${configsResult.error.message.defaultMessage}"
            }
            val configs = configsResult.value
            if (configs.isNotEmpty()) {
                allIds.addAll(configs.values.map { it.id })
            } else {
                allIds.addAll(binder.getEntryIds(configService))
            }
        }

        return allIds.toTypedArray().also {
            log.debug("[KmsProviderConfigBinder] Kms provider ids: ${it.joinToString(",")}")
        }
    }

    override fun getKmsProviderConfig(
        configService: ConfigService,
        providerId: String,
    ): KmsProviderConfigBase {
        log.debug("Getting Kms provider config for $providerId, from config service at level: ${configService.level}")
        val prefixes = buildPrefixes(configService)

        // Try each prefix until we find the config
        for (prefix in prefixes) {
            val binder = createPolymorphicBinder(prefix)
            val configResult = binder.getEntryConfigResult(configService, providerId)
            if (configResult.isOk) {
                log.debug("Kms provider config for $providerId: ${configResult.value}")
                return configResult.value
            }
            require(configResult.error.code == notFoundCode) {
                "Failed to bind KMS provider config for '$providerId' under prefix '$prefix': ${configResult.error.message.defaultMessage}"
            }
        }

        throw IllegalArgumentException(
            "KMS provider config not found for provider ID: $providerId",
        )
    }

    override fun getKmsProviderConfigs(configService: ConfigService): Array<KmsProviderConfigBase> {
        log.debug("[KmsProviderConfigBinder] Getting all KMS provider configs in batch")
        val prefixes = buildPrefixes(configService)

        // Collect all configs from all prefixes
        val allConfigs = mutableMapOf<String, KmsProviderConfigBase>()
        for (prefix in prefixes) {
            val binder = createPolymorphicBinder(prefix)
            val configsResult = binder.getEntryConfigsAsMapResult(configService, strict = true)
            require(configsResult.isOk) {
                "Failed to bind KMS provider configs for prefix '$prefix': ${configsResult.error.message.defaultMessage}"
            }
            val configs = configsResult.value
            // Later prefixes override earlier ones
            allConfigs.putAll(configs)
        }

        log.debug("[KmsProviderConfigBinder] Found ${allConfigs.size} provider configs")

        // Filter by enabled status
        return allConfigs.values
            .filter { it.enabled }
            .toTypedArray()
    }
}

private val KmsOperationalMetadataKeys =
    setOf(
        "system",
        "role",
    )
