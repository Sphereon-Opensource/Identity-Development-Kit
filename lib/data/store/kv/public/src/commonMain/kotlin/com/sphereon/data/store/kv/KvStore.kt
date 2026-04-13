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

package com.sphereon.data.store.kv

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import kotlin.time.Duration

/**
 * Cross-cutting key-value store abstraction.
 *
 * Notes:
 * - Namespaces are mandatory to avoid collisions across modules.
 * - TTL is a first-class concept. Use [Duration.INFINITE] to represent persistent entries.
 * - Scope binding (APP/TENANT/PRINCIPAL_TENANT/SESSION) is configured in [KvStoreConfig] and
 *   affects how data is partitioned in backing storage.
 */
interface KvStore {
    val config: KvStoreConfigBase

    suspend fun <V : Any> put(
        namespace: KvNamespace<V>,
        key: String,
        value: V,
        ttl: Duration
    ): IdkResult<KvPutResult, IdkError>

    suspend fun <V : Any> get(
        namespace: KvNamespace<V>,
        key: String
    ): IdkResult<V?, IdkError>

    suspend fun <V : Any> getEntry(
        namespace: KvNamespace<V>,
        key: String
    ): IdkResult<KvEntry<V>?, IdkError>

    suspend fun delete(namespace: KvNamespaceId, key: String): IdkResult<Boolean, IdkError>

    suspend fun exists(namespace: KvNamespaceId, key: String): IdkResult<Boolean, IdkError>

    suspend fun touch(namespace: KvNamespaceId, key: String, ttl: Duration): IdkResult<Boolean, IdkError>

    suspend fun cleanupExpired(namespace: KvNamespaceId? = null): IdkResult<Int, IdkError>
}

/**
 * Optional KV capability for enumerating keys/entries per namespace.
 *
 * Notes:
 * - Not all backends can efficiently list keys (or list keys at all).
 * - Protocol-layer stores should use this capability when present for better performance/integration,
 *   and fall back to maintaining their own indexes when absent.
 */
interface KvStoreListing : KvStore {
    /**
     * List all non-expired keys for [namespace] in the current partition.
     */
    suspend fun listKeys(namespace: KvNamespaceId): IdkResult<List<String>, IdkError>

    /**
     * Get all non-expired entries for [namespace] in the current partition.
     *
     * Default implementation uses [listKeys] and then reads values individually.
     * Backends may override this for better performance.
     */
    suspend fun <V : Any> getAll(namespace: KvNamespace<V>): IdkResult<Map<String, V>, IdkError> {
        val keys = listKeys(namespace).getOrElse { return Err(it) }
        val result = LinkedHashMap<String, V>(keys.size)
        for (key in keys) {
            val value = get(namespace, key).getOrElse { return Err(it) } ?: continue
            result[key] = value
        }
        return Ok(result)
    }

    /**
     * Get all non-expired entries including metadata for [namespace] in the current partition.
     *
     * Default implementation uses [listKeys] and then reads entries individually.
     * Backends may override this for better performance.
     */
    suspend fun <V : Any> getAllEntries(namespace: KvNamespace<V>): IdkResult<Map<String, KvEntry<V>>, IdkError> {
        val keys = listKeys(namespace).getOrElse { return Err(it) }
        val result = LinkedHashMap<String, KvEntry<V>>(keys.size)
        for (key in keys) {
            val entry = getEntry(namespace, key).getOrElse { return Err(it) } ?: continue
            result[key] = entry
        }
        return Ok(result)
    }
}
