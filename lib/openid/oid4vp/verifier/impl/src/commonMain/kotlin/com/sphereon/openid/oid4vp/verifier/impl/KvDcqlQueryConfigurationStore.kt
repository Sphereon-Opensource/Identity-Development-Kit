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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreListing
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.impl.KvStoreManager
import com.sphereon.data.store.kv.impl.KvStoreService
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.verifier.model.DcqlQueryConfiguration
import com.sphereon.openid.oid4vp.verifier.store.DcqlQueryConfigurationStore
import com.sphereon.openid.oid4vp.verifier.store.StoreMetadata
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val MILLIS_PER_SECOND = 1000

/**
 * KV-backed implementation of [DcqlQueryConfigurationStore].
 *
 * Configurations are persistent by default. This implementation:
 * - Stores configurations under their `queryId`
 * - Uses KV native key listing when available
 * - Falls back to maintaining an internal index when listing is not supported
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DcqlQueryConfigurationStore>())
class KvDcqlQueryConfigurationStore(
    private val kvStoreManager: KvStoreManager,
    private val kvStoreService: KvStoreService,
    private val execution: SessionExecution,
    private val clock: Clock = Clock.System,
) : DcqlQueryConfigurationStore {
    private val json = Json
    private val mutex = Mutex()

    private val namespace =
        KvNamespace(
            name = "oid4vp.dcql.query_config",
            codec = KotlinxSerializationJsonKvCodec(json = json, serializer = DcqlQueryConfigEntry.serializer()),
        )

    private val indexNamespace =
        KvNamespace(
            name = "oid4vp.dcql.query_config.index",
            codec =
                KotlinxSerializationJsonKvCodec(
                    json = json,
                    serializer = SetSerializer(String.serializer()),
                ),
        )

    private val indexKey = "__ids__"

    private val storeConfig: KvStoreConfigBase =
        InMemoryKvStoreConfig(
            id = "oid4vp.dcql.query_config",
            scopeBinding = KvStoreScopeBinding.TENANT,
        )

    private val kv: KvStore by lazy {
        kvStoreManager.createFromKvStoreConfig(resolveEffectiveStoreConfig(), execution)
    }

    private val listing: KvStoreListing?
        get() = kv as? KvStoreListing

    /**
     * Resolve KV store configuration from the config abstraction (if present), otherwise fall back to [storeConfig].
     *
     * This store is tenant-bound by design; configuration is validated to prevent scope drift.
     */
    private fun resolveEffectiveStoreConfig(): KvStoreConfigBase {
        val configured = runCatching { kvStoreService.getStoreConfig(storeConfig.id) }.getOrNull()
        val effective = configured ?: storeConfig
        require(effective.scopeBinding == storeConfig.scopeBinding) {
            "KV store '${storeConfig.id}' must use scopeBinding=${storeConfig.scopeBinding}, but was ${effective.scopeBinding}"
        }
        return effective
    }

    @Serializable
    internal data class DcqlQueryConfigEntry(
        val queryId: String,
        val name: String,
        val description: String? = null,
        val dcqlQuery: DcqlQuery,
        val enabled: Boolean = true,
        val createdAt: Long,
        val updatedAt: Long,
    ) {
        fun toPublic(): DcqlQueryConfiguration =
            DcqlQueryConfiguration(
                queryId = queryId,
                name = name,
                description = description,
                dcqlQuery = dcqlQuery,
                enabled = enabled,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
    }

    override suspend fun putPersistent(
        key: String,
        value: DcqlQueryConfiguration,
    ): IdkResult<StoreMetadata, IdkError> {
        val now = clock.now().toEpochMilliseconds()
        val entry =
            DcqlQueryConfigEntry(
                queryId = value.queryId,
                name = value.name,
                description = value.description,
                dcqlQuery = value.dcqlQuery,
                enabled = value.enabled,
                createdAt =
                    if (value.createdAt > 0) {
                        value.createdAt
                    } else {
                        now
                    },
                updatedAt = now,
            )

        return mutex.withLock {
            val putResult =
                kv.put(namespace, key, entry, Duration.INFINITE).getOrElse { e ->
                    return@withLock Err(
                        IdkError.fromString(
                            message = "Failed to store DCQL query configuration: ${e.message}",
                            exception = IllegalStateException(e.toString()),
                            code = "OID4VP_DCQL_STORE_ERROR",
                        ),
                    )
                }

            // Maintain index only when listing is not supported.
            if (listing == null) {
                addToIndex(key).getOrElse { err -> return@withLock Err(err) }
            }

            Ok(StoreMetadata(createdAt = putResult.metadata.createdAtEpochMillis, expiresAt = putResult.metadata.expiresAtEpochMillis))
        }
    }

    override suspend fun put(
        key: String,
        value: DcqlQueryConfiguration,
        ttlSeconds: Long,
    ): IdkResult<StoreMetadata, IdkError> {
        val now = clock.now().toEpochMilliseconds()
        val expiresAt = now + (ttlSeconds * MILLIS_PER_SECOND)
        val entry =
            DcqlQueryConfigEntry(
                queryId = value.queryId,
                name = value.name,
                description = value.description,
                dcqlQuery = value.dcqlQuery,
                enabled = value.enabled,
                createdAt =
                    if (value.createdAt > 0) {
                        value.createdAt
                    } else {
                        now
                    },
                updatedAt = now,
            )

        return mutex.withLock {
            val putResult =
                kv.put(namespace, key, entry, ttlSeconds.seconds).getOrElse { e ->
                    return@withLock Err(
                        IdkError.fromString(
                            message = "Failed to store DCQL query configuration: ${e.message}",
                            exception = IllegalStateException(e.toString()),
                            code = "OID4VP_DCQL_STORE_ERROR",
                        ),
                    )
                }

            if (listing == null) {
                addToIndex(key).getOrElse { err -> return@withLock Err(err) }
            }

            Ok(StoreMetadata(createdAt = putResult.metadata.createdAtEpochMillis, expiresAt = putResult.metadata.expiresAtEpochMillis))
        }
    }

    override suspend fun get(key: String): IdkResult<DcqlQueryConfiguration?, IdkError> =
        kv.get(namespace, key).map { it?.toPublic() }.mapError { e ->
            IdkError.fromString(message = "Failed to read DCQL query configuration: ${e.message}", exception = IllegalStateException(e.toString()), code = "OID4VP_DCQL_STORE_ERROR")
        }

    override suspend fun getEntry(key: String): IdkResult<com.sphereon.openid.oid4vp.verifier.store.StoredEntry<DcqlQueryConfiguration>?, IdkError> =
        kv
            .getEntry(namespace, key)
            .map { entry ->
                entry?.let {
                    com.sphereon.openid.oid4vp.verifier.store.StoredEntry(
                        value = it.value.toPublic(),
                        createdAt = it.metadata.createdAtEpochMillis,
                        expiresAt = it.metadata.expiresAtEpochMillis,
                    )
                }
            }.mapError { e ->
                IdkError.fromString(message = "Failed to read DCQL query configuration entry: ${e.message}", exception = IllegalStateException(e.toString()), code = "OID4VP_DCQL_STORE_ERROR")
            }

    override suspend fun delete(key: String): IdkResult<Boolean, IdkError> {
        return mutex.withLock {
            val deleted =
                kv.delete(namespace, key).getOrElse { e ->
                    return@withLock Err(
                        IdkError.fromString(
                            message = "Failed to delete DCQL query configuration: ${e.message}",
                            exception = IllegalStateException(e.toString()),
                            code = "OID4VP_DCQL_STORE_ERROR",
                        ),
                    )
                }
            if (deleted && listing == null) {
                removeFromIndex(key).getOrElse { err -> return@withLock Err(err) }
            }
            Ok(deleted)
        }
    }

    override suspend fun exists(key: String): IdkResult<Boolean, IdkError> =
        kv.exists(namespace, key).mapError { e ->
            IdkError.fromString(message = "Failed to check DCQL query configuration existence: ${e.message}", exception = IllegalStateException(e.toString()), code = "OID4VP_DCQL_STORE_ERROR")
        }

    override suspend fun touch(
        key: String,
        ttlSeconds: Long,
    ): IdkResult<Boolean, IdkError> =
        kv.touch(namespace, key, ttlSeconds.seconds).mapError { e ->
            IdkError.fromString(message = "Failed to touch DCQL query configuration: ${e.message}", exception = IllegalStateException(e.toString()), code = "OID4VP_DCQL_STORE_ERROR")
        }

    override suspend fun cleanupExpired(): IdkResult<Int, IdkError> =
        kv.cleanupExpired(namespace).mapError { e ->
            IdkError.fromString(message = "Failed to cleanup expired DCQL query configurations: ${e.message}", exception = IllegalStateException(e.toString()), code = "OID4VP_DCQL_STORE_ERROR")
        }

    override suspend fun listIds(): IdkResult<List<String>, IdkError> {
        val native = listing
        if (native != null) {
            return native.listKeys(namespace).map { it.sorted() }
        }
        return mutex.withLock {
            getIndex().map { it.toList().sorted() }
        }
    }

    override suspend fun getAll(): IdkResult<Map<String, DcqlQueryConfiguration>, IdkError> {
        val native = listing
        if (native != null) {
            return native.getAll(namespace).map { map -> map.mapValues { it.value.toPublic() } }
        }

        return mutex.withLock {
            val ids = getIndex().getOrElse { return@withLock Err(it) }
            val result = LinkedHashMap<String, DcqlQueryConfiguration>(ids.size)
            for (id in ids) {
                val value = get(id).getOrElse { return@withLock Err(it) } ?: continue
                result[id] = value
            }
            Ok(result)
        }
    }

    override suspend fun getByQueryId(queryId: String): IdkResult<DcqlQueryConfiguration?, IdkError> = get(queryId)

    private suspend fun getIndex(): IdkResult<Set<String>, IdkError> =
        kv.get(indexNamespace, indexKey).map { it ?: emptySet() }.mapError { e ->
            IdkError.fromString(message = "Failed to read DCQL query configuration index: ${e.message}", exception = IllegalStateException(e.toString()), code = "OID4VP_DCQL_INDEX_ERROR")
        }

    private suspend fun putIndex(ids: Set<String>): IdkResult<Unit, IdkError> =
        kv.put(indexNamespace, indexKey, ids, Duration.INFINITE).map { }.mapError { e ->
            IdkError.fromString(message = "Failed to write DCQL query configuration index: ${e.message}", exception = IllegalStateException(e.toString()), code = "OID4VP_DCQL_INDEX_ERROR")
        }

    private suspend fun addToIndex(id: String): IdkResult<Unit, IdkError> {
        val ids = getIndex().getOrElse { return Err(it) }.toMutableSet()
        if (ids.add(id)) {
            return putIndex(ids)
        }
        return Ok(Unit)
    }

    private suspend fun removeFromIndex(id: String): IdkResult<Unit, IdkError> {
        val ids = getIndex().getOrElse { return Err(it) }.toMutableSet()
        if (ids.remove(id)) {
            return putIndex(ids)
        }
        return Ok(Unit)
    }
}
