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

package com.sphereon.data.store.kv.memory

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.kv.KvEntry
import com.sphereon.data.store.kv.KvEntryMetadata
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvNamespaceId
import com.sphereon.data.store.kv.KvPutResult
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreListing
import com.sphereon.data.store.kv.KvStoreConfigBase
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.time.Duration

internal class InMemoryKvStore(
    override val config: KvStoreConfigBase,
    private val partition: InMemoryKvPartition
) : KvStoreListing {
    override suspend fun <V : Any> put(
        namespace: KvNamespace<V>,
        key: String,
        value: V,
        ttl: Duration
    ): IdkResult<KvPutResult, IdkError> = partition.mutex.withLock {
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            val expiresAt = if (ttl.isInfinite()) Long.MAX_VALUE else now + ttl.inWholeMilliseconds
            val bytes = namespace.codec.encode(value)
            partition.entries[InMemoryKvEntryKey(namespace = namespace.name, key = key)] = InMemoryKvStoredBytes(
                value = bytes,
                createdAtEpochMillis = now,
                expiresAtEpochMillis = expiresAt
            )
            Ok(KvPutResult(metadata = KvEntryMetadata(createdAtEpochMillis = now, expiresAtEpochMillis = expiresAt)))
        } catch (e: Exception) {
            Err(IdkError.fromString(message = "Failed to put KV entry '${namespace.name}:$key': ${e.message}", exception = e, code = "KV_PUT_FAILED"))
        }
    }

    override suspend fun <V : Any> get(namespace: KvNamespace<V>, key: String): IdkResult<V?, IdkError> {
        return getEntry(namespace, key).map { it?.value }
    }

    override suspend fun <V : Any> getEntry(namespace: KvNamespace<V>, key: String): IdkResult<KvEntry<V>?, IdkError> = partition.mutex.withLock {
        try {
            val entryKey = InMemoryKvEntryKey(namespace = namespace.name, key = key)
            val stored = partition.entries[entryKey] ?: return@withLock Ok(null)

            val now = Clock.System.now().toEpochMilliseconds()
            if (stored.expiresAtEpochMillis <= now) {
                partition.entries.remove(entryKey)
                return@withLock Ok(null)
            }

            val value = namespace.codec.decode(stored.value)
            Ok(
                KvEntry(
                    value = value,
                    metadata = KvEntryMetadata(
                        createdAtEpochMillis = stored.createdAtEpochMillis,
                        expiresAtEpochMillis = stored.expiresAtEpochMillis
                    )
                )
            )
        } catch (e: Exception) {
            Err(IdkError.fromString(message = "Failed to get KV entry '${namespace.name}:$key': ${e.message}", exception = e, code = "KV_GET_FAILED"))
        }
    }

    override suspend fun delete(namespace: KvNamespaceId, key: String): IdkResult<Boolean, IdkError> = partition.mutex.withLock {
        try {
            Ok(partition.entries.remove(InMemoryKvEntryKey(namespace = namespace.name, key = key)) != null)
        } catch (e: Exception) {
            Err(IdkError.fromString(message = "Failed to delete KV entry '${namespace.name}:$key': ${e.message}", exception = e, code = "KV_DELETE_FAILED"))
        }
    }

    override suspend fun exists(namespace: KvNamespaceId, key: String): IdkResult<Boolean, IdkError> = partition.mutex.withLock {
        try {
            val entryKey = InMemoryKvEntryKey(namespace = namespace.name, key = key)
            val stored = partition.entries[entryKey] ?: return@withLock Ok(false)

            val now = Clock.System.now().toEpochMilliseconds()
            if (stored.expiresAtEpochMillis <= now) {
                partition.entries.remove(entryKey)
                return@withLock Ok(false)
            }

            Ok(true)
        } catch (e: Exception) {
            Err(IdkError.fromString(message = "Failed to check existence of KV entry '${namespace.name}:$key': ${e.message}", exception = e, code = "KV_EXISTS_FAILED"))
        }
    }

    override suspend fun touch(namespace: KvNamespaceId, key: String, ttl: Duration): IdkResult<Boolean, IdkError> = partition.mutex.withLock {
        try {
            val entryKey = InMemoryKvEntryKey(namespace = namespace.name, key = key)
            val stored = partition.entries[entryKey] ?: return@withLock Ok(false)

            val now = Clock.System.now().toEpochMilliseconds()
            if (stored.expiresAtEpochMillis <= now) {
                partition.entries.remove(entryKey)
                return@withLock Ok(false)
            }

            val expiresAt = if (ttl.isInfinite()) Long.MAX_VALUE else now + ttl.inWholeMilliseconds
            partition.entries[entryKey] = stored.copy(expiresAtEpochMillis = expiresAt)
            Ok(true)
        } catch (e: Exception) {
            Err(IdkError.fromString(message = "Failed to touch KV entry '${namespace.name}:$key': ${e.message}", exception = e, code = "KV_TOUCH_FAILED"))
        }
    }

    override suspend fun cleanupExpired(namespace: KvNamespaceId?): IdkResult<Int, IdkError> = partition.mutex.withLock {
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            val keysToRemove = partition.entries
                .filter { (entryKey, stored) ->
                    (namespace == null || entryKey.namespace == namespace.name) && stored.expiresAtEpochMillis <= now
                }
                .map { it.key }

            keysToRemove.forEach { partition.entries.remove(it) }
            Ok(keysToRemove.size)
        } catch (e: Exception) {
            Err(IdkError.fromString(message = "Failed to cleanup expired KV entries: ${e.message}", exception = e, code = "KV_CLEANUP_FAILED"))
        }
    }

    override suspend fun listKeys(namespace: KvNamespaceId): IdkResult<List<String>, IdkError> = partition.mutex.withLock {
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            val keys = mutableListOf<String>()

            val iterator = partition.entries.entries.iterator()
            while (iterator.hasNext()) {
                val (entryKey, stored) = iterator.next()
                if (entryKey.namespace != namespace.name) continue
                if (stored.expiresAtEpochMillis <= now) {
                    iterator.remove()
                    continue
                }
                keys.add(entryKey.key)
            }

            Ok(keys)
        } catch (e: Exception) {
            Err(IdkError.fromString(message = "Failed to list KV keys for namespace '${namespace.name}': ${e.message}", exception = e, code = "KV_LIST_KEYS_FAILED"))
        }
    }
}
