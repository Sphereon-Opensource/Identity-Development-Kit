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

import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.model.IWithKeyStoreType
import com.sphereon.di.Order
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsName
import kotlin.native.ObjCName

@JsExportCompat
@Serializable
@SerialName("KeyStoreConfig")
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyStoreConfigImpl", exact = true)
data class KeyStoreConfigImpl(
    override val keyStoreType: String,
    override val id: String,
    override val enabled: Boolean = true,
    override val order: Int = Order.MEDIUM.orderValue,
    override val defaultConfigValues: Map<String, String> = emptyMap(),
    override val keyVisibility: String = KeyVisibility.PUBLIC.keyVisibility,
    override val overwriteAlias: Boolean = false,
) : AbstractKeyStoreConfig(),
    KeyStoreConfig

@Serializable
abstract class AbstractKeyStoreConfig {
    @SerialName("type")
    abstract val keyStoreType: String
    abstract val id: String
    abstract val enabled: Boolean
    abstract val order: Int

    @SerialName("defaultConfigValues")
    abstract val defaultConfigValues: Map<String, String>

    @SerialName("keyVisibility")
    abstract val keyVisibility: String

    @SerialName("overwriteAlias")
    abstract val overwriteAlias: Boolean
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyStoreConfig", exact = true)
interface KeyStoreConfig {
    val keyStoreType: String
    val id: String
    val enabled: Boolean
    val order: Int
    val defaultConfigValues: Map<String, String>
    val keyVisibility: String
    val overwriteAlias: Boolean
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyStoreFactory", exact = true)
interface KeyStoreFactory : IWithKeyStoreType {
    fun create(config: KeyStoreConfig): KeyStore

    /**
     * Creates a KeyStore with optional execution context for scope-aware implementations.
     * Default implementation delegates to create(config) for backward compatibility.
     */
    fun create(
        config: KeyStoreConfig,
        execution: SessionExecution?,
    ): KeyStore = create(config)
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyStore", exact = true)
interface KeyStore :
    IWithKeyStoreType,
    KeyStoreService,
    CertificateStoreService {
    val order: Int
        get() = Order.MEDIUM.orderValue
    val id: String
    val enabled: Boolean
        get() = true
    val keyTypesSupported: Array<KeyTypeMapping>
    val signatureAlgorithmsSupported: Array<SignatureAlgorithm>
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyStoreManager", exact = true)
interface KeyStoreManager {
    /**
     * Creates a KeyStore from a config object (sync).
     */
    fun createFromKeyStoreConfig(config: KeyStoreConfig): KeyStore

    /**
     * Creates a KeyStore from a config object with execution context (sync).
     */
    @JsName("createFromKeyStoreConfigWithExecution")
    fun createFromKeyStoreConfig(
        config: KeyStoreConfig,
        execution: SessionExecution?,
    ): KeyStore

    /**
     * Creates KeyStores from config properties (sync).
     * Requires sync cache to be warmed up if using external cache.
     */
    fun createFromProperties(configService: ConfigService): Set<KeyStore>

    @JsExportIgnoreCompat
    @ContributesTo(AppScope::class)
    interface Graph {
        val keyStoreManager: KeyStoreManager
    }
}

/**
 * Extension interface for async KeyStore operations.
 * Not exported to JS since suspend functions are not supported in JS exports.
 * Implementations should implement this interface alongside [KeyStoreManager].
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyStoreManagerAsync", exact = true)
interface KeyStoreManagerAsync : KeyStoreManager {
    /**
     * Creates KeyStores from config properties (async).
     * Can access external caches (Redis, DB) directly without warmup.
     */
    suspend fun createFromPropertiesAsync(configService: ConfigService): Set<KeyStore> = createFromProperties(configService)
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyStoreConfigBinder", exact = true)
interface KeyStoreConfigBinder {
    /**
     * Gets all KeyStore IDs from configuration (sync).
     * Requires sync cache to be warmed up if using external cache.
     */
    fun getKeyStoreIds(configService: ConfigService): Array<String>

    /**
     * Gets a specific KeyStore config (sync).
     * Requires sync cache to be warmed up if using external cache.
     */
    fun getKeyStoreConfig(
        configService: ConfigService,
        providerId: String,
    ): KeyStoreConfig

    /**
     * Gets all KeyStore configs (sync).
     * Requires sync cache to be warmed up if using external cache.
     */
    fun getKeyStoreConfigs(configService: ConfigService): Array<KeyStoreConfig>

    /**
     * Gets all KeyStore IDs from configuration (async).
     * Can access external caches (Redis, DB) directly without warmup.
     * Default implementation delegates to sync version.
     */
    suspend fun getKeyStoreIdsAsync(configService: ConfigService): Array<String> = getKeyStoreIds(configService)

    /**
     * Gets a specific KeyStore config (async).
     * Can access external caches (Redis, DB) directly without warmup.
     * Default implementation delegates to sync version.
     */
    suspend fun getKeyStoreConfigAsync(
        configService: ConfigService,
        providerId: String,
    ): KeyStoreConfig = getKeyStoreConfig(configService, providerId)

    /**
     * Gets all KeyStore configs (async).
     * Can access external caches (Redis, DB) directly without warmup.
     * Default implementation delegates to sync version.
     */
    suspend fun getKeyStoreConfigsAsync(configService: ConfigService): Array<KeyStoreConfig> = getKeyStoreConfigs(configService)

    @ContributesTo(AppScope::class)
    interface Graph {
        val keyStoreConfigBinder: KeyStoreConfigBinder
    }

    companion object {
        const val KMS_KEYSTORES_PREFIX = "kms.keystores"
    }
}
