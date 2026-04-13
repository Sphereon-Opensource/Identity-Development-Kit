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

package com.sphereon.data.store.kv.impl

import com.sphereon.core.api.conf.CamelCaseKeyDenormalizerImpl
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.DefaultPolymorphicConfigBinder
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.PropertyKeyNormalizerImpl
import com.sphereon.core.api.conf.TypeSuffixEntryDetection
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreFactory
import com.sphereon.data.store.kv.KvStoreJsonSupport
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

// ================================================================================================
// KvStoreConfigBinder (polymorphic, using DefaultPolymorphicConfigBinder)
// ================================================================================================

/**
 * Property name aliases for relaxed config binding.
 */
private val KvStorePropertyNameAliases =
    mapOf(
        "scopebinding" to "scopeBinding",
        "scope.binding" to "scopeBinding",
        "backendid" to "backendId",
        "backend.id" to "backendId",
        "defaultconfigs" to "defaultConfigValues",
        "defaultconfigvalues" to "defaultConfigValues",
        "databasedir" to "databaseDir",
        "database.dir" to "databaseDir",
        "storagenameprefix" to "storageNamePrefix",
        "storage.name.prefix" to "storageNamePrefix",
    )

@OptIn(ExperimentalObjCName::class)
@ObjCName("KvStoreConfigBinder", exact = true)
interface KvStoreConfigBinder {
    fun getKvStoreIds(configService: ConfigService): Array<String>

    fun getKvStoreConfig(
        configService: ConfigService,
        storeId: String,
    ): KvStoreConfigBase

    fun getKvStoreConfigs(configService: ConfigService): Array<KvStoreConfigBase>

    @ContributesTo(AppScope::class)
    interface Graph {
        val kvStoreConfigBinder: KvStoreConfigBinder
    }

    companion object {
        const val KV_STORES_PREFIX: String = "kv.stores"
    }
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<KvStoreConfigBinder>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("KvStoreConfigBinderImpl", exact = true)
class KvStoreConfigBinderImpl(
    appLogManager: AppLogManager,
) : KvStoreConfigBinder {
    private val log = appLogManager.withTag("KvStoreConfigBinder")
    private val keyNormalizer = PropertyKeyNormalizerImpl.Default

    private fun buildPrefixes(configService: ConfigService) = setOf(KvStoreConfigBinder.KV_STORES_PREFIX)

    private fun createPolymorphicBinder(prefix: String) =
        DefaultPolymorphicConfigBinder(
            prefix = prefix,
            baseClass = KvStoreConfigBase::class,
            json = KvStoreJsonSupport.serializer,
            entryDetection = TypeSuffixEntryDetection(),
            idFieldName = "id",
            keyNormalizer = keyNormalizer,
            keyDenormalizer = CamelCaseKeyDenormalizerImpl(),
            propertyNameAliases = KvStorePropertyNameAliases,
        )

    override fun getKvStoreIds(configService: ConfigService): Array<String> {
        val prefixes = buildPrefixes(configService)
        val allIds = mutableSetOf<String>()
        for (prefix in prefixes) {
            val binder = createPolymorphicBinder(prefix)
            val configsResult = binder.getEntryConfigsAsMapResult(configService, strict = true)
            require(configsResult.isOk) {
                "Failed to bind KV store configs for prefix '$prefix': ${configsResult.error.message.defaultMessage}"
            }
            val configs = configsResult.value
            if (configs.isNotEmpty()) {
                allIds.addAll(configs.values.map { it.id })
            } else {
                allIds.addAll(binder.getEntryIds(configService))
            }
        }

        return allIds.toTypedArray().also {
            log.debug("Found ${it.size} KV stores: ${it.joinToString(",")}")
        }
    }

    override fun getKvStoreConfig(
        configService: ConfigService,
        storeId: String,
    ): KvStoreConfigBase {
        val prefixes = buildPrefixes(configService)
        val notFoundCode = "NOT_FOUND_ERROR"

        for (prefix in prefixes) {
            val binder = createPolymorphicBinder(prefix)
            val configResult = binder.getEntryConfigResult(configService, storeId)
            if (configResult.isOk) {
                log.debug("KV store config for '$storeId': ${configResult.value}")
                return configResult.value
            }
            if (configResult.error.code != notFoundCode) {
                throw IllegalArgumentException(
                    "Failed to bind KV store config for '$storeId' under prefix '$prefix': ${configResult.error.message.defaultMessage}",
                )
            }
        }

        throw IllegalArgumentException("KV store config not found for store ID: $storeId")
    }

    override fun getKvStoreConfigs(configService: ConfigService): Array<KvStoreConfigBase> {
        val prefixes = buildPrefixes(configService)
        val allConfigs = mutableMapOf<String, KvStoreConfigBase>()
        for (prefix in prefixes) {
            val binder = createPolymorphicBinder(prefix)
            val configsResult = binder.getEntryConfigsAsMapResult(configService, strict = true)
            if (configsResult.isErr) {
                throw IllegalArgumentException(
                    "Failed to bind KV store configs for prefix '$prefix': ${configsResult.error.message.defaultMessage}",
                )
            }
            allConfigs.putAll(configsResult.value)
        }

        return allConfigs.values
            .filter { it.enabled }
            .toTypedArray()
    }
}

// ================================================================================================
// KvStoreManager
// ================================================================================================

@OptIn(ExperimentalObjCName::class)
@ObjCName("KvStoreManager", exact = true)
interface KvStoreManager {
    fun createFromKvStoreConfig(config: KvStoreConfigBase): KvStore

    fun createFromKvStoreConfig(
        config: KvStoreConfigBase,
        execution: SessionExecution?,
    ): KvStore

    fun createFromProperties(
        configService: ConfigService,
        execution: SessionExecution?,
    ): Set<KvStore>

    @ContributesTo(AppScope::class)
    interface Graph {
        val kvStoreManager: KvStoreManager
    }
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<KvStoreManager>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("KvStoreManagerImpl", exact = true)
class KvStoreManagerImpl(
    factories: Set<KvStoreFactory>,
    private val binder: KvStoreConfigBinder,
) : KvStoreManager {
    private val factories = factories.filter { it.backendId != NoOpKvStoreFactoryImpl.BACKEND_ID }.toSet()

    override fun createFromKvStoreConfig(config: KvStoreConfigBase): KvStore = createFromKvStoreConfig(config, null)

    override fun createFromKvStoreConfig(
        config: KvStoreConfigBase,
        execution: SessionExecution?,
    ): KvStore {
        if (factories.isEmpty()) {
            throw IllegalArgumentException("No KvStoreFactory implementations found. Add at least one KV backend module (memory, kottage, etc).")
        }
        val factory =
            factories.firstOrNull { it.backendId.equals(config.backendId, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "No KvStoreFactory found for backendId '${config.backendId}'. Available: ${factories.map { it.backendId }.distinct().sorted()}",
                )
        return factory.create(config, execution)
    }

    override fun createFromProperties(
        configService: ConfigService,
        execution: SessionExecution?,
    ): Set<KvStore> =
        binder
            .getKvStoreConfigs(configService)
            .map {
                createFromKvStoreConfig(it, execution)
            }.toSet()
}

// ================================================================================================
// KvStoreService (session-scoped)
// ================================================================================================

@OptIn(ExperimentalObjCName::class)
@ObjCName("KvStoreService", exact = true)
interface KvStoreService {
    fun getStoreIds(): Array<String>

    fun getStoreConfig(storeId: String): KvStoreConfigBase

    fun getStore(storeId: String): KvStore

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("KvStoreServiceGraph", exact = true)
    @ContributesTo(scope = SessionScope::class)
    interface Graph {
        val kvStoreService: KvStoreService
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<KvStoreService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("KvStoreServiceImpl", exact = true)
class KvStoreServiceImpl(
    private val config: PrincipalConfigService,
    private val binder: KvStoreConfigBinder,
    private val manager: KvStoreManager,
    private val execution: SessionExecution,
) : KvStoreService {
    override fun getStoreIds(): Array<String> = binder.getKvStoreIds(config)

    override fun getStoreConfig(storeId: String): KvStoreConfigBase = binder.getKvStoreConfig(config, storeId)

    override fun getStore(storeId: String): KvStore {
        val storeConfig = getStoreConfig(storeId)
        return manager.createFromKvStoreConfig(storeConfig, execution)
    }
}

// ================================================================================================
// NoOp factory (keeps multibinding stable)
// ================================================================================================

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("NoOpKvStoreFactoryImpl", exact = true)
class NoOpKvStoreFactoryImpl : KvStoreFactory {
    override val backendId: String = BACKEND_ID

    override fun create(
        config: KvStoreConfigBase,
        execution: SessionExecution?,
    ): KvStore = throw IllegalArgumentException("No KvStoreFactory found. Add a KV backend module (memory, kottage, etc) to your app.")

    companion object {
        const val BACKEND_ID: String = "___NO_OP___"
    }
}
