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

package com.sphereon.data.store.kv

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.di.Order
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Defines the partitioning level for KV storage.
 *
 * This determines who can see the stored data, independent of the DI lifetime of a [KvStore] instance.
 *
 * - APP: shared across the entire application
 * - TENANT: shared across all principals within a tenant
 * - PRINCIPAL_TENANT: isolated per principal within a tenant
 * - SESSION: isolated per session within a principal+tenant context
 */
@Serializable
@JsExportCompat
enum class KvStoreScopeBinding {
    APP,
    TENANT,
    PRINCIPAL_TENANT,
    SESSION,
}

object KvStoreBackends {
    const val MEMORY: String = "memory"
    const val KOTTAGE: String = "kottage"
}

/**
 * Base interface for all KV store configurations.
 *
 * Each backend provides a typed subclass (e.g., [InMemoryKvStoreConfig], [KottageKvStoreConfig]).
 * The polymorphic config binder deserializes the correct type based on the `type` discriminator field.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("KvStoreConfigBase", exact = true)
@JsExportCompat
interface KvStoreConfigBase {
    val id: String
    val scopeBinding: KvStoreScopeBinding
    val backendId: String
    val enabled: Boolean
    val order: Int
}

/**
 * Abstract serializable base for KV store configs.
 * Subclasses add backend-specific typed fields.
 */
@Serializable
@JsExportCompat
abstract class AbstractKvStoreConfig {
    abstract val id: String

    @SerialName("scopeBinding")
    abstract val scopeBinding: KvStoreScopeBinding
    abstract val backendId: String
    abstract val enabled: Boolean
    abstract val order: Int
}

/**
 * Generic KV store config. Used as the fallback for unknown backend types
 * and for backward compatibility with existing code that constructs `KvStoreConfig` directly.
 *
 * For typed backend configs, use [InMemoryKvStoreConfig] or [KottageKvStoreConfig].
 */
@Serializable
@SerialName("KvStoreConfig")
@JsExportCompat
data class KvStoreConfig(
    override val id: String,
    @SerialName("scopeBinding")
    override val scopeBinding: KvStoreScopeBinding = KvStoreScopeBinding.TENANT,
    override val backendId: String = KvStoreBackends.MEMORY,
    override val enabled: Boolean = true,
    override val order: Int = Order.MEDIUM.orderValue,
    @SerialName("defaultconfigs")
    @JsExportIgnoreCompat
    val defaultConfigValues: Map<String, String> = emptyMap(),
) : AbstractKvStoreConfig(),
    KvStoreConfigBase

/**
 * In-memory KV store configuration.
 *
 * ```properties
 * kv.stores.session-cache.type=memory
 * kv.stores.session-cache.scopeBinding=SESSION
 * ```
 */
@Serializable
@SerialName("memory")
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryKvStoreConfig", exact = true)
@JsExportCompat
data class InMemoryKvStoreConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val id: String = "memory",
    @SerialName("scopeBinding")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val scopeBinding: KvStoreScopeBinding = KvStoreScopeBinding.TENANT,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val enabled: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val order: Int = Order.MEDIUM.orderValue,
) : AbstractKvStoreConfig(),
    KvStoreConfigBase {
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val backendId: String = KvStoreBackends.MEMORY
}

/**
 * Kottage (embedded SQLite) KV store configuration.
 *
 * ```properties
 * kv.stores.persistent.type=kottage
 * kv.stores.persistent.scopeBinding=TENANT
 * kv.stores.persistent.databaseDir=/var/data/kottage
 * ```
 */
@Serializable
@SerialName("kottage")
@OptIn(ExperimentalObjCName::class)
@ObjCName("KottageKvStoreConfig", exact = true)
@JsExportCompat
data class KottageKvStoreConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val id: String = "kottage",
    @SerialName("scopeBinding")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val scopeBinding: KvStoreScopeBinding = KvStoreScopeBinding.TENANT,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val enabled: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val order: Int = Order.MEDIUM.orderValue,
    @SerialName("databaseDir")
    val databaseDir: String? = null,
    @SerialName("storageNamePrefix")
    val storageNamePrefix: String = "idk-kv",
) : AbstractKvStoreConfig(),
    KvStoreConfigBase {
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val backendId: String = KvStoreBackends.KOTTAGE
}

/**
 * Partition key used by backing storages (in-memory, persistent) to isolate data.
 */
@JsExportCompat
data class KvPartitionKey(
    val storeId: String,
    val tenantId: String? = null,
    val principalId: String? = null,
    val sessionId: String? = null,
)

@JsExportCompat
data class KvEntryMetadata(
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)

@JsExportCompat
data class KvEntry<V : Any>(
    val value: V,
    val metadata: KvEntryMetadata,
)

@JsExportCompat
data class KvPutResult(
    val metadata: KvEntryMetadata,
)
