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
import com.sphereon.openid.oid4vp.common.ClientMetadata
import com.sphereon.openid.oid4vp.verifier.model.ClientMetadataConfiguration
import com.sphereon.openid.oid4vp.verifier.store.ClientMetadataConfigurationStore
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

/**
 * KV-backed implementation of [ClientMetadataConfigurationStore].
 *
 * Configurations are persistent by default. This implementation:
 * - Stores configurations under `clientMetadataId`
 * - Maintains a `clientId -> clientMetadataId` index for efficient lookup
 * - Uses KV native key listing when available, otherwise maintains an internal id index
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ClientMetadataConfigurationStore>())
class KvClientMetadataConfigurationStore(
    private val kvStoreManager: KvStoreManager,
    private val kvStoreService: KvStoreService,
    private val execution: SessionExecution,
    private val clock: Clock = Clock.System,
) : ClientMetadataConfigurationStore {
    private val json = Json
    private val mutex = Mutex()

    private val namespace =
        KvNamespace(
            name = "oid4vp.client_metadata.config",
            codec = KotlinxSerializationJsonKvCodec(json = json, serializer = ClientMetadataConfigEntry.serializer()),
        )

    private val indexNamespace =
        KvNamespace(
            name = "oid4vp.client_metadata.config.index",
            codec =
                KotlinxSerializationJsonKvCodec(
                    json = json,
                    serializer = SetSerializer(String.serializer()),
                ),
        )
    private val indexKey = "__ids__"

    private val clientIdIndexNamespace =
        KvNamespace(
            name = "oid4vp.client_metadata.config.by_client_id",
            codec = KotlinxSerializationJsonKvCodec(json = json, serializer = String.serializer()),
        )

    private val storeConfig: KvStoreConfigBase =
        InMemoryKvStoreConfig(
            id = "oid4vp.client_metadata.config",
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
    internal data class ClientMetadataConfigEntry(
        val clientMetadataId: String,
        val clientId: String,
        val name: String,
        val clientMetadata: ClientMetadata,
        val clientMetadataUri: String? = null,
        val enabled: Boolean = true,
        val createdAt: Long,
        val updatedAt: Long,
    ) {
        fun toPublic(): ClientMetadataConfiguration =
            ClientMetadataConfiguration(
                clientMetadataId = clientMetadataId,
                clientId = clientId,
                name = name,
                clientMetadata = clientMetadata,
                clientMetadataUri = clientMetadataUri,
                enabled = enabled,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
    }

    override suspend fun putPersistent(
        key: String,
        value: ClientMetadataConfiguration,
    ): IdkResult<StoreMetadata, IdkError> {
        val now = clock.now().toEpochMilliseconds()
        val entry =
            ClientMetadataConfigEntry(
                clientMetadataId = value.clientMetadataId,
                clientId = value.clientId,
                name = value.name,
                clientMetadata = value.clientMetadata,
                clientMetadataUri = value.clientMetadataUri,
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
                            message = "Failed to store client metadata configuration: ${e.message}",
                            exception = IllegalStateException(e.toString()),
                            code = "OID4VP_CLIENT_METADATA_STORE_ERROR",
                        ),
                    )
                }

            // Always maintain clientId -> id mapping (this is a real lookup requirement).
            kv.put(clientIdIndexNamespace, entry.clientId, entry.clientMetadataId, Duration.INFINITE).getOrElse { e ->
                return@withLock Err(
                    IdkError.fromString(
                        message = "Failed to store clientId index: ${e.message}",
                        exception = IllegalStateException(e.toString()),
                        code = "OID4VP_CLIENT_METADATA_INDEX_ERROR",
                    ),
                )
            }

            // Maintain ids index only when listing is not supported.
            if (listing == null) {
                addToIndex(key).getOrElse { err -> return@withLock Err(err) }
            }

            Ok(StoreMetadata(createdAt = putResult.metadata.createdAtEpochMillis, expiresAt = putResult.metadata.expiresAtEpochMillis))
        }
    }

    override suspend fun put(
        key: String,
        value: ClientMetadataConfiguration,
        ttlSeconds: Long,
    ): IdkResult<StoreMetadata, IdkError> {
        val now = clock.now().toEpochMilliseconds()
        val entry =
            ClientMetadataConfigEntry(
                clientMetadataId = value.clientMetadataId,
                clientId = value.clientId,
                name = value.name,
                clientMetadata = value.clientMetadata,
                clientMetadataUri = value.clientMetadataUri,
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
                            message = "Failed to store client metadata configuration: ${e.message}",
                            exception = IllegalStateException(e.toString()),
                            code = "OID4VP_CLIENT_METADATA_STORE_ERROR",
                        ),
                    )
                }

            kv.put(clientIdIndexNamespace, entry.clientId, entry.clientMetadataId, ttlSeconds.seconds).getOrElse { e ->
                return@withLock Err(
                    IdkError.fromString(
                        message = "Failed to store clientId index: ${e.message}",
                        exception = IllegalStateException(e.toString()),
                        code = "OID4VP_CLIENT_METADATA_INDEX_ERROR",
                    ),
                )
            }

            if (listing == null) {
                addToIndex(key).getOrElse { err -> return@withLock Err(err) }
            }

            Ok(StoreMetadata(createdAt = putResult.metadata.createdAtEpochMillis, expiresAt = putResult.metadata.expiresAtEpochMillis))
        }
    }

    override suspend fun get(key: String): IdkResult<ClientMetadataConfiguration?, IdkError> =
        kv.get(namespace, key).map { it?.toPublic() }.mapError { e ->
            IdkError.fromString(message = "Failed to read client metadata configuration: ${e.message}", exception = IllegalStateException(e.toString()), code = "OID4VP_CLIENT_METADATA_STORE_ERROR")
        }

    override suspend fun getEntry(key: String): IdkResult<com.sphereon.openid.oid4vp.verifier.store.StoredEntry<ClientMetadataConfiguration>?, IdkError> =
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
                IdkError.fromString(
                    message = "Failed to read client metadata configuration entry: ${e.message}",
                    exception = IllegalStateException(e.toString()),
                    code = "OID4VP_CLIENT_METADATA_STORE_ERROR",
                )
            }

    override suspend fun delete(key: String): IdkResult<Boolean, IdkError> {
        return mutex.withLock {
            val existing =
                kv.get(namespace, key).getOrElse { e ->
                    return@withLock Err(
                        IdkError.fromString(
                            message = "Failed to read client metadata configuration before delete: ${e.message}",
                            exception = IllegalStateException(e.toString()),
                            code = "OID4VP_CLIENT_METADATA_STORE_ERROR",
                        ),
                    )
                }

            val deleted =
                kv.delete(namespace, key).getOrElse { e ->
                    return@withLock Err(
                        IdkError.fromString(
                            message = "Failed to delete client metadata configuration: ${e.message}",
                            exception = IllegalStateException(e.toString()),
                            code = "OID4VP_CLIENT_METADATA_STORE_ERROR",
                        ),
                    )
                }

            if (deleted && existing != null) {
                kv.delete(clientIdIndexNamespace, existing.clientId).getOrElse { e ->
                    return@withLock Err(
                        IdkError.fromString(
                            message = "Failed to delete clientId index: ${e.message}",
                            exception = IllegalStateException(e.toString()),
                            code = "OID4VP_CLIENT_METADATA_INDEX_ERROR",
                        ),
                    )
                }
            }

            if (deleted && listing == null) {
                removeFromIndex(key).getOrElse { err -> return@withLock Err(err) }
            }

            Ok(deleted)
        }
    }

    override suspend fun exists(key: String): IdkResult<Boolean, IdkError> =
        kv.exists(namespace, key).mapError { e ->
            IdkError.fromString(
                message = "Failed to check client metadata configuration existence: ${e.message}",
                exception = IllegalStateException(e.toString()),
                code = "OID4VP_CLIENT_METADATA_STORE_ERROR",
            )
        }

    override suspend fun touch(
        key: String,
        ttlSeconds: Long,
    ): IdkResult<Boolean, IdkError> =
        kv.touch(namespace, key, ttlSeconds.seconds).mapError { e ->
            IdkError.fromString(message = "Failed to touch client metadata configuration: ${e.message}", exception = IllegalStateException(e.toString()), code = "OID4VP_CLIENT_METADATA_STORE_ERROR")
        }

    override suspend fun cleanupExpired(): IdkResult<Int, IdkError> =
        kv.cleanupExpired(namespace).mapError { e ->
            IdkError.fromString(
                message = "Failed to cleanup expired client metadata configurations: ${e.message}",
                exception = IllegalStateException(e.toString()),
                code = "OID4VP_CLIENT_METADATA_STORE_ERROR",
            )
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

    override suspend fun getAll(): IdkResult<Map<String, ClientMetadataConfiguration>, IdkError> {
        val native = listing
        if (native != null) {
            return native.getAll(namespace).map { map -> map.mapValues { it.value.toPublic() } }
        }

        return mutex.withLock {
            val ids = getIndex().getOrElse { return@withLock Err(it) }
            val result = LinkedHashMap<String, ClientMetadataConfiguration>(ids.size)
            for (id in ids) {
                val value = get(id).getOrElse { return@withLock Err(it) } ?: continue
                result[id] = value
            }
            Ok(result)
        }
    }

    override suspend fun getByClientMetadataId(clientMetadataId: String): IdkResult<ClientMetadataConfiguration?, IdkError> = get(clientMetadataId)

    override suspend fun getByClientId(clientId: String): IdkResult<ClientMetadataConfiguration?, IdkError> {
        return kv
            .get(clientIdIndexNamespace, clientId)
            .flatMap { clientMetadataId ->
                if (clientMetadataId == null) {
                    return@flatMap Ok(null)
                }
                get(clientMetadataId)
            }.mapError { e ->
                IdkError.fromString(
                    message = "Failed to resolve client metadata by clientId: ${e.message}",
                    exception = IllegalStateException(e.toString()),
                    code = "OID4VP_CLIENT_METADATA_INDEX_ERROR",
                )
            }
    }

    private suspend fun getIndex(): IdkResult<Set<String>, IdkError> =
        kv.get(indexNamespace, indexKey).map { it ?: emptySet() }.mapError { e ->
            IdkError.fromString(
                message = "Failed to read client metadata configuration index: ${e.message}",
                exception = IllegalStateException(e.toString()),
                code = "OID4VP_CLIENT_METADATA_INDEX_ERROR",
            )
        }

    private suspend fun putIndex(ids: Set<String>): IdkResult<Unit, IdkError> =
        kv.put(indexNamespace, indexKey, ids, Duration.INFINITE).map { }.mapError { e ->
            IdkError.fromString(
                message = "Failed to write client metadata configuration index: ${e.message}",
                exception = IllegalStateException(e.toString()),
                code = "OID4VP_CLIENT_METADATA_INDEX_ERROR",
            )
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
