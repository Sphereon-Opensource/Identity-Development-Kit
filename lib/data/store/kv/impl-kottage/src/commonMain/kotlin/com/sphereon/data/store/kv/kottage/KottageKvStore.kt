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

package com.sphereon.data.store.kv.kottage

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.Base64UrlSerializer
import com.sphereon.data.store.kv.KvEntry
import com.sphereon.data.store.kv.KvEntryMetadata
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvNamespaceId
import com.sphereon.data.store.kv.KvPutResult
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreConfigBase
import io.github.irgaly.kottage.KottageStorage
import io.github.irgaly.kottage.getOrNull
import io.github.irgaly.kottage.put
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
internal data class KottageKvEnvelope(
    @Serializable(with = Base64UrlSerializer::class)
    val value: ByteArray,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long
)

internal class KottageKvStore(
    override val config: KvStoreConfigBase,
    private val storage: KottageStorage
) : KvStore {

    private fun compositeKey(namespace: KvNamespaceId, key: String): String {
        // Use a stable, unambiguous separator. ':' is safe for Kottage keys.
        return "${namespace.name}:$key"
    }

    override suspend fun <V : Any> put(
        namespace: KvNamespace<V>,
        key: String,
        value: V,
        ttl: Duration
    ): IdkResult<KvPutResult, IdkError> {
        return try {
            val now = Clock.System.now().toEpochMilliseconds()
            val expiresAt = if (ttl.isInfinite()) Long.MAX_VALUE else now + ttl.inWholeMilliseconds
            val bytes = namespace.codec.encode(value)
            val envelope = KottageKvEnvelope(value = bytes, createdAtEpochMillis = now, expiresAtEpochMillis = expiresAt)

            storage.put(
                key = compositeKey(namespace, key),
                value = envelope,
                expireTime = ttl.takeUnless { it.isInfinite() }
            )

            Ok(KvPutResult(metadata = KvEntryMetadata(createdAtEpochMillis = now, expiresAtEpochMillis = expiresAt)))
        } catch (e: Exception) {
            Err(IdkError.fromString(message = "Failed to put KV entry '${namespace.name}:$key' to Kottage: ${e.message}", exception = e, code = "KV_KOTTAGE_PUT_FAILED"))
        }
    }

    override suspend fun <V : Any> get(namespace: KvNamespace<V>, key: String): IdkResult<V?, IdkError> {
        return getEntry(namespace, key).map { it?.value }
    }

    override suspend fun <V : Any> getEntry(namespace: KvNamespace<V>, key: String): IdkResult<KvEntry<V>?, IdkError> {
        return try {
            val compositeKey = compositeKey(namespace, key)
            val stored = storage.getOrNull<KottageKvEnvelope>(compositeKey) ?: return Ok(null)

            val now = Clock.System.now().toEpochMilliseconds()
            if (stored.expiresAtEpochMillis <= now) {
                storage.remove(compositeKey)
                return Ok(null)
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
            Err(IdkError.fromString(message = "Failed to get KV entry '${namespace.name}:$key' from Kottage: ${e.message}", exception = e, code = "KV_KOTTAGE_GET_FAILED"))
        }
    }

    override suspend fun delete(namespace: KvNamespaceId, key: String): IdkResult<Boolean, IdkError> {
        return try {
            Ok(storage.remove(compositeKey(namespace, key)))
        } catch (e: Exception) {
            Err(IdkError.fromString(message = "Failed to delete KV entry '${namespace.name}:$key' from Kottage: ${e.message}", exception = e, code = "KV_KOTTAGE_DELETE_FAILED"))
        }
    }

    override suspend fun exists(namespace: KvNamespaceId, key: String): IdkResult<Boolean, IdkError> {
        return try {
            Ok(storage.exists(compositeKey(namespace, key)))
        } catch (e: Exception) {
            Err(IdkError.fromString(message = "Failed to check existence of KV entry '${namespace.name}:$key' in Kottage: ${e.message}", exception = e, code = "KV_KOTTAGE_EXISTS_FAILED"))
        }
    }

    override suspend fun touch(namespace: KvNamespaceId, key: String, ttl: Duration): IdkResult<Boolean, IdkError> {
        return try {
            val compositeKey = compositeKey(namespace, key)
            val stored = storage.getOrNull<KottageKvEnvelope>(compositeKey) ?: return Ok(false)

            val now = Clock.System.now().toEpochMilliseconds()
            if (stored.expiresAtEpochMillis <= now) {
                storage.remove(compositeKey)
                return Ok(false)
            }

            val expiresAt = if (ttl.isInfinite()) Long.MAX_VALUE else now + ttl.inWholeMilliseconds
            val updated = stored.copy(expiresAtEpochMillis = expiresAt)

            storage.put(
                key = compositeKey,
                value = updated,
                expireTime = ttl.takeUnless { it.isInfinite() }
            )

            Ok(true)
        } catch (e: Exception) {
            Err(IdkError.fromString(message = "Failed to touch KV entry '${namespace.name}:$key' in Kottage: ${e.message}", exception = e, code = "KV_KOTTAGE_TOUCH_FAILED"))
        }
    }

    override suspend fun cleanupExpired(namespace: KvNamespaceId?): IdkResult<Int, IdkError> {
        return try {
            // Kottage compaction is storage-wide. Namespace-specific cleanup is not supported.
            storage.compact()
            Ok(0)
        } catch (e: Exception) {
            Err(IdkError.fromString(message = "Failed to compact Kottage storage for KV cleanup: ${e.message}", exception = e, code = "KV_KOTTAGE_CLEANUP_FAILED"))
        }
    }
}
