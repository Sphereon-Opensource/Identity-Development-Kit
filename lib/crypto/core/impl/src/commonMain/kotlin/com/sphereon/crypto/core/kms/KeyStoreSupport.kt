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
 *
 */

package com.sphereon.crypto.core.kms

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.core.api.log.AppLogManager
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet
import com.sphereon.core.api.conf.CamelCaseKeyDenormalizerImpl
import com.sphereon.core.api.conf.DefaultPolymorphicConfigBinder
import com.sphereon.core.api.conf.PropertyKeyNormalizerImpl
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.TypeSuffixEntryDetection
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.json.CryptoJsonSupport
import com.sphereon.crypto.core.kms.KeyStoreConfigBinder.Companion.KMS_KEYSTORES_PREFIX

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("NoOpKeyStoreFactory", exact = true)
class NoOpKeyStoreFactory : KeyStoreFactory {
    override val keyStoreType: String = KEY_STORE_TYPE
    override fun create(config: KeyStoreConfig): KeyStore {
        throw IllegalArgumentException("Please register your Keystore Factory or Factories!. No KeyStore Factory found")
    }

    companion object {
        const val KEY_STORE_TYPE = "___NO_OP___"
    }
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyStoreManagerImpl", exact = true)
class KeyStoreManagerImpl(factories: Set<KeyStoreFactory>, private val binder: KeyStoreConfigBinder) : KeyStoreManager {

    // Let's filter out the NO OP Factory which is only there to ensure injection works at all times
    private val factories = factories.filter { it.keyStoreType != NoOpKeyStoreFactory.KEY_STORE_TYPE }.toSet()

    override fun createFromKeyStoreConfig(config: KeyStoreConfig): KeyStore {
        return createFromKeyStoreConfig(config, null)
    }

    override fun createFromKeyStoreConfig(config: KeyStoreConfig, execution: SessionExecution?): KeyStore {
        if (factories.isEmpty()) throw IllegalArgumentException("No keystore factories found! Make sure to register a key store module if you need keystore support")
        val factory = factories.find { it.keyStoreType == config.keyStoreType }
            ?: throw IllegalArgumentException("No keystore factory found for type ${config.keyStoreType}, available factories: ${factories.joinToString(",")}")

        return factory.create(config, execution)
    }

    override fun createFromProperties(configService: ConfigService): Set<KeyStore> {
        return binder.getKeyStoreConfigs(configService).map { createFromKeyStoreConfig(it) }.toSet()
    }

    @ContributesTo(AppScope::class)
    interface Component {
        val keyStoreManager: KeyStoreManager
    }
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<KeyStoreConfigBinder>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyStoreConfigBinderImpl", exact = true)
class KeyStoreConfigBinderImpl(logManager: AppLogManager) : KeyStoreConfigBinder {
    private val log = logManager.withTag("KeyStoreConfigBinder")
    private val keyNormalizer = PropertyKeyNormalizerImpl()
    private val entryDetection = TypeSuffixEntryDetection(discriminatorSuffix = ".type")
    private val notFoundCode = "NOT_FOUND_ERROR"

    private fun buildPrefixes(configService: ConfigService) = setOf(keyNormalizer.normalize("${configService.getNamespace()}.$KMS_KEYSTORES_PREFIX"), KMS_KEYSTORES_PREFIX)
    private fun createPolymorphicBinder(prefix: String) = DefaultPolymorphicConfigBinder(
        prefix = prefix,
        baseClass = KeyStoreConfig::class,
        json = CryptoJsonSupport.serializer,
        entryDetection = entryDetection,
        idFieldName = "id",
        keyNormalizer = keyNormalizer,
        keyDenormalizer = CamelCaseKeyDenormalizerImpl(),
        propertyNameAliases = KeyStorePropertyNameAliases,
        redact = false
    )

    override fun getKeyStoreIds(configService: ConfigService): Array<String> {
        val prefixes = buildPrefixes(configService)
        log.debug("Getting keystore IDs from config service at level: ${configService.configLevel}")
        log.debug("Using prefixes: ${prefixes.joinToString(",")}")
        val allIds = mutableSetOf<String>()
        for (prefix in prefixes) {
            val binder = createPolymorphicBinder(prefix)
            val configsResult = binder.getEntryConfigsAsMapResult(configService, strict = true)
            if (configsResult.isErr) {
                throw IllegalArgumentException(
                    "Failed to bind keystore configs for prefix '$prefix': ${configsResult.error.message.defaultMessage}"
                )
            }
            val configs = configsResult.value
            if (configs.isNotEmpty()) {
                allIds.addAll(configs.values.map { it.id })
            } else {
                allIds.addAll(binder.getEntryIds(configService))
            }
        }
        val ids = allIds.toTypedArray()
        log.debug("Found ${ids.size} keystores in ${configService.configLevel}: ${ids.joinToString(",")}")
        return ids
    }

    override fun getKeyStoreConfig(configService: ConfigService, providerId: String): KeyStoreConfig {
        val prefixes = buildPrefixes(configService)
        log.debug("Getting keystore config for $providerId, from config service at level: ${configService.configLevel}")
        for (prefix in prefixes) {
            val binder = createPolymorphicBinder(prefix)
            val configResult = binder.getEntryConfigResult(configService, providerId)
            if (configResult.isOk) {
                log.debug("Keystore config for $providerId: ${configResult.value}")
                return configResult.value
            }
            if (configResult.error.code != notFoundCode) {
                throw IllegalArgumentException(
                    "Failed to bind keystore config for '$providerId' under prefix '$prefix': ${configResult.error.message.defaultMessage}"
                )
            }
        }
        throw IllegalArgumentException("KeyStore config not found for provider ID: $providerId")
    }

    override fun getKeyStoreConfigs(configService: ConfigService): Array<KeyStoreConfig> {
        val prefixes = buildPrefixes(configService)
        val allConfigs = mutableMapOf<String, KeyStoreConfig>()
        for (prefix in prefixes) {
            val binder = createPolymorphicBinder(prefix)
            val configsResult = binder.getEntryConfigsAsMapResult(configService, strict = true)
            if (configsResult.isErr) {
                throw IllegalArgumentException(
                    "Failed to bind keystore configs for prefix '$prefix': ${configsResult.error.message.defaultMessage}"
                )
            }
            allConfigs.putAll(configsResult.value)
        }
        return allConfigs.values.filter { it.enabled }.toTypedArray()
    }

    @ContributesTo(AppScope::class)
    interface Component {
        val keyStoreConfigBinder: KeyStoreConfigBinder
    }
}

private val KeyStorePropertyNameAliases = mapOf(
    "defaultconfigvalues" to "defaultConfigValues",
    "keyvisibility" to "keyVisibility",
    "overwritealias" to "overwriteAlias",
    "accessmode" to "accessMode",
    "scopebinding" to "scopeBinding",
    // Legacy config field alias
    "keystoretype" to "type"
)
