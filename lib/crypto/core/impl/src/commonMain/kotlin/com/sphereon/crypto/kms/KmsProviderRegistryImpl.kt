/*
 * Copyright (c) 2026 Sphereon International B.V.
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

package com.sphereon.crypto.kms

import com.sphereon.core.api.conf.ConfigEnvironment
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.SyncConfigSnapshotCache
import com.sphereon.core.api.conf.refreshableContentRevision
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.KmsProviderConfigBinder.Companion.KMS_PROVIDERS_PREFIX
import com.sphereon.crypto.core.kms.KmsProviderManagerImpl
import com.sphereon.crypto.core.kms.KmsProviderRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Session-scoped implementation of [KmsProviderRegistry].
 *
 * This registry creates and manages KMS providers for a session. Providers are created
 * from tenant/principal configuration lazily on first access, enabling proper cache
 * warmup before KMS initialization.
 *
 * The lazy initialization pattern is critical for:
 * 1. Allowing async cache warmup before sync property access
 * 2. Supporting external caches (Redis, DB) that require async access
 * 3. Avoiding blocking calls during SessionScope constructor on JS platform
 *
 * Commands can inject this interface directly instead of `Lazy<KeyManagerService>`,
 * enabling them to work independently without circular dependencies.
 */
@JsExportCompat
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<KmsProviderRegistry>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("KmsProviderRegistryImpl", exact = true)
class KmsProviderRegistryImpl(
    private val contextConfig: PrincipalConfigService,
    private val kmsProviderManager: KmsProviderManagerImpl,
    private val execution: SessionExecution,
    private val snapshotCache: SyncConfigSnapshotCache,
) : KmsProviderRegistry {
    // Lazy initialization: providers are created on first access
    // This allows cache warmup to complete before property resolution
    // Sort by ID to ensure deterministic iteration order for default selection
    private val kmsProvidersById: MutableMap<String, KmsProvider> by lazy {
        mutableMapOf<String, KmsProvider>().also { providers ->
            refreshProvidersFromConfig(target = providers, force = true)
        }
    }

    private var defaultProviderIdOverride: String? = null
    private var lastProviderConfigRevision: Long? = null
    private val configManagedProviderIds: MutableSet<String> = mutableSetOf()

    override fun defaultProviderId(): String {
        refreshProvidersFromConfig()
        require(kmsProvidersById.isNotEmpty()) { "At least one KMS provider is required" }
        return defaultProviderIdOverride ?: kmsProvidersById.values.first().id
    }

    override fun getProviderIds(): Array<String> {
        refreshProvidersFromConfig()
        return kmsProvidersById.keys.toTypedArray()
    }

    override suspend fun getProviderById(id: String): KmsProvider {
        kmsProvidersById[id]?.let { return it }
        refreshProvidersFromConfig(force = true)
        return kmsProvidersById[id]
            ?: throw PKIException("Invalid KMS id $id provider. Valid ids are: ${kmsProvidersById.keys.joinToString(",")}")
    }

    override suspend fun getProvider(
        providerId: String?,
        alg: SignatureAlgorithm?,
    ): KmsProvider {
        if (providerId != null) {
            val provider = getProviderById(providerId)
            if (alg != null && !provider.getCapabilities().signatureAlgorithms.contains(alg)) {
                throw PKIException("KMS provider $providerId does not support signature algorithm $alg")
            }
            return provider
        }

        val defaultProvider = getProviderById(defaultProviderId())
        if (alg == null || defaultProvider.getCapabilities().signatureAlgorithms.contains(alg)) {
            return defaultProvider
        }
        return getKmsBySignatureAlgorithm(alg)
    }

    override suspend fun getKmsBySignatureAlgorithm(signatureAlgorithm: SignatureAlgorithm): KmsProvider {
        kmsProvidersById.values.firstOrNull { it.getCapabilities().signatureAlgorithms.contains(signatureAlgorithm) }?.let { return it }
        refreshProvidersFromConfig(force = true)
        return kmsProvidersById.values.firstOrNull { it.getCapabilities().signatureAlgorithms.contains(signatureAlgorithm) }
            ?: throw PKIException("No KMS found for signature algorithm $signatureAlgorithm")
    }

    override fun registerProvider(
        provider: KmsProvider,
        makeDefaultKms: Boolean?,
    ) {
        kmsProvidersById[provider.id] = provider
        if (makeDefaultKms == true) {
            defaultProviderIdOverride = provider.id
        }
    }

    private fun refreshProvidersFromConfig(
        target: MutableMap<String, KmsProvider> = kmsProvidersById,
        force: Boolean = false,
    ) {
        val revision = configRevision(contextConfig)
        if (!force && lastProviderConfigRevision == revision) {
            return
        }
        if (force || (lastProviderConfigRevision != null && lastProviderConfigRevision != revision)) {
            snapshotCache.invalidateByPrefix(KMS_PROVIDERS_PREFIX)
        }
        // Provider selection is tenant-scoped. Include tenant providers in a
        // principal session registry so alias-only signing can find the tenant's
        // KMS key instead of falling back to a principal software provider.
        val configProviders =
            listOf(ConfigLevel.APP, ConfigLevel.TENANT, ConfigLevel.PRINCIPAL)
                .map { level -> execution.conf.conf(level) }
                .distinctBy { it }
                .flatMap { config -> kmsProviderManager.createFromProperties(config, execution) }
                .sortedBy { it.id }
        val nextConfigManagedProviderIds = configProviders.mapTo(mutableSetOf()) { it.id }

        (configManagedProviderIds - nextConfigManagedProviderIds).forEach { removedProviderId ->
            target.remove(removedProviderId)?.close()
            if (defaultProviderIdOverride == removedProviderId) {
                defaultProviderIdOverride = null
            }
        }
        configProviders.forEach { provider ->
            val previous = target.put(provider.id, provider)
            if (previous !== provider) {
                previous?.close()
            }
        }
        configManagedProviderIds.clear()
        configManagedProviderIds.addAll(nextConfigManagedProviderIds)
        lastProviderConfigRevision = revision
    }

    private fun configRevision(config: ConfigEnvironment?): Long {
        if (config == null) {
            return 0L
        }
        val sources = config.getPropertySources(includeParents = false)
        val localRevision = (sources.revision * 31L) + sources.refreshableContentRevision(refresh = true)
        return (localRevision * 31L) + configRevision(config.parent)
    }
}
