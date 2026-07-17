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

package com.sphereon.data.store.kv.kottage

import com.sphereon.core.api.Base64UrlSerializer
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
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreListing
import com.sphereon.data.store.kv.KvStoreVersioning
import com.sphereon.data.store.kv.KvVersionAppendResult
import com.sphereon.data.store.kv.KvVersionedEntry
import io.github.irgaly.kottage.KottageStorage
import io.github.irgaly.kottage.getOrNull
import io.github.irgaly.kottage.put
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
internal data class KottageKvEnvelope(
    @Serializable(with = Base64UrlSerializer::class)
    val value: ByteArray,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)

@Serializable
private data class KottageKvKeyIndex(
    val keys: Set<String> = emptySet(),
)

@Serializable
private data class KottageKvVersionEnvelope(
    val versionId: String,
    val previousVersionId: String?,
    @Serializable(with = Base64UrlSerializer::class)
    val value: ByteArray,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)

@Serializable
private data class KottageKvVersionChainEnvelope(
    val headVersionId: String,
    val entries: List<KottageKvVersionEnvelope>,
)

internal class KottageKvStore(
    override val config: KvStoreConfigBase,
    private val storage: KottageStorage,
    private val mutationMutex: Mutex,
) : KvStoreListing,
    KvStoreVersioning {
    private fun compositeKey(
        namespace: KvNamespaceId,
        key: String,
    ): String {
        // Use a stable, unambiguous separator. ':' is safe for Kottage keys.
        return "${namespace.name}:$key"
    }

    override suspend fun <V : Any> put(
        namespace: KvNamespace<V>,
        key: String,
        value: V,
        ttl: Duration,
    ): IdkResult<KvPutResult, IdkError> =
        mutationMutex.withLock {
            val previousIndex =
                try {
                    readIndex(namespace)
                } catch (expected: Exception) {
                    return@withLock putError(namespace, key, expected)
                }
            try {
                if (key !in previousIndex.keys) writeIndex(namespace, previousIndex.copy(keys = previousIndex.keys + key))
                val now = Clock.System.now().toEpochMilliseconds()
                val expiresAt =
                    if (ttl.isInfinite()) {
                        Long.MAX_VALUE
                    } else {
                        now + ttl.inWholeMilliseconds
                    }
                val bytes = namespace.codec.encode(value)
                val envelope = KottageKvEnvelope(value = bytes, createdAtEpochMillis = now, expiresAtEpochMillis = expiresAt)

                storage.put(
                    key = compositeKey(namespace, key),
                    value = envelope,
                    expireTime = ttl.takeUnless { it.isInfinite() },
                )

                Ok(KvPutResult(metadata = KvEntryMetadata(createdAtEpochMillis = now, expiresAtEpochMillis = expiresAt)))
            } catch (expected: Exception) {
                if (key !in previousIndex.keys) runCatching { writeIndex(namespace, previousIndex) }
                putError(namespace, key, expected)
            }
        }

    override suspend fun <V : Any> get(
        namespace: KvNamespace<V>,
        key: String,
    ): IdkResult<V?, IdkError> = getEntry(namespace, key).map { it?.value }

    override suspend fun <V : Any> getEntry(
        namespace: KvNamespace<V>,
        key: String,
    ): IdkResult<KvEntry<V>?, IdkError> {
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
                    metadata =
                        KvEntryMetadata(
                            createdAtEpochMillis = stored.createdAtEpochMillis,
                            expiresAtEpochMillis = stored.expiresAtEpochMillis,
                        ),
                ),
            )
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "Failed to get KV entry '${namespace.name}:$key' from Kottage: ${expected.message}", exception = expected, code = "KV_KOTTAGE_GET_FAILED"))
        }
    }

    override suspend fun delete(
        namespace: KvNamespaceId,
        key: String,
    ): IdkResult<Boolean, IdkError> =
        mutationMutex.withLock {
            try {
                val removed = storage.remove(compositeKey(namespace, key))
                val index = readIndex(namespace)
                if (key in index.keys) writeIndex(namespace, index.copy(keys = index.keys - key))
                Ok(removed)
            } catch (expected: Exception) {
                Err(IdkError.fromString(message = "Failed to delete KV entry '${namespace.name}:$key' from Kottage: ${expected.message}", exception = expected, code = "KV_KOTTAGE_DELETE_FAILED"))
            }
        }

    override suspend fun listKeys(namespace: KvNamespaceId): IdkResult<List<String>, IdkError> =
        mutationMutex.withLock {
            try {
                val index = readIndex(namespace)
                val liveKeys =
                    index.keys.filter { key ->
                        storage.getOrNull<KottageKvEnvelope>(compositeKey(namespace, key)) != null
                    }
                if (liveKeys.size != index.keys.size) writeIndex(namespace, KottageKvKeyIndex(liveKeys.toSet()))
                Ok(liveKeys)
            } catch (expected: Exception) {
                Err(
                    IdkError.fromString(
                        message = "Failed to list KV entries for '${namespace.name}' from Kottage: ${expected.message}",
                        exception = expected,
                        code = "KV_KOTTAGE_LIST_FAILED",
                    ),
                )
            }
        }

    override suspend fun exists(
        namespace: KvNamespaceId,
        key: String,
    ): IdkResult<Boolean, IdkError> =
        try {
            Ok(storage.exists(compositeKey(namespace, key)))
        } catch (expected: Exception) {
            Err(
                IdkError.fromString(
                    message = "Failed to check existence of KV entry '${namespace.name}:$key' in Kottage: ${expected.message}",
                    exception = expected,
                    code = "KV_KOTTAGE_EXISTS_FAILED",
                ),
            )
        }

    override suspend fun touch(
        namespace: KvNamespaceId,
        key: String,
        ttl: Duration,
    ): IdkResult<Boolean, IdkError> {
        return try {
            val compositeKey = compositeKey(namespace, key)
            val stored = storage.getOrNull<KottageKvEnvelope>(compositeKey) ?: return Ok(false)

            val now = Clock.System.now().toEpochMilliseconds()
            if (stored.expiresAtEpochMillis <= now) {
                storage.remove(compositeKey)
                return Ok(false)
            }

            val expiresAt =
                if (ttl.isInfinite()) {
                    Long.MAX_VALUE
                } else {
                    now + ttl.inWholeMilliseconds
                }
            val updated = stored.copy(expiresAtEpochMillis = expiresAt)

            storage.put(
                key = compositeKey,
                value = updated,
                expireTime = ttl.takeUnless { it.isInfinite() },
            )

            Ok(true)
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "Failed to touch KV entry '${namespace.name}:$key' in Kottage: ${expected.message}", exception = expected, code = "KV_KOTTAGE_TOUCH_FAILED"))
        }
    }

    override suspend fun cleanupExpired(namespace: KvNamespaceId?): IdkResult<Int, IdkError> =
        try {
            // Kottage compaction is storage-wide. Namespace-specific cleanup is not supported.
            storage.compact()
            Ok(0)
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "Failed to compact Kottage storage for KV cleanup: ${expected.message}", exception = expected, code = "KV_KOTTAGE_CLEANUP_FAILED"))
        }

    override suspend fun <V : Any> getHead(
        namespace: KvNamespace<V>,
        key: String,
    ): IdkResult<KvVersionedEntry<V>?, IdkError> =
        mutationMutex.withLock {
            try {
                val chain = readLiveVersionChain(namespace, key, Clock.System.now().toEpochMilliseconds()) ?: return@withLock Ok(null)
                Ok(chain.entries.firstOrNull { it.versionId == chain.headVersionId }?.decode(namespace))
            } catch (expected: Exception) {
                versionError(namespace, key, "get head", "KV_KOTTAGE_VERSION_GET_HEAD_FAILED", expected)
            }
        }

    override suspend fun <V : Any> getVersion(
        namespace: KvNamespace<V>,
        key: String,
        versionId: String,
    ): IdkResult<KvVersionedEntry<V>?, IdkError> =
        mutationMutex.withLock {
            try {
                val now = Clock.System.now().toEpochMilliseconds()
                val chain = readLiveVersionChain(namespace, key, now) ?: return@withLock Ok(null)
                val entry = chain.entries.firstOrNull { it.versionId == versionId && it.expiresAtEpochMillis > now }
                Ok(entry?.decode(namespace))
            } catch (expected: Exception) {
                versionError(namespace, key, "get version", "KV_KOTTAGE_VERSION_GET_FAILED", expected)
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun <V : Any> append(
        namespace: KvNamespace<V>,
        key: String,
        expectedPreviousVersionId: String?,
        value: V,
        ttl: Duration,
    ): IdkResult<KvVersionAppendResult<V>, IdkError> =
        mutationMutex.withLock {
            try {
                val now = Clock.System.now().toEpochMilliseconds()
                val chain = readLiveVersionChain(namespace, key, now)
                val currentHead = chain?.entries?.firstOrNull { it.versionId == chain.headVersionId }
                if (currentHead?.versionId != expectedPreviousVersionId || (chain != null && expectedPreviousVersionId == null)) {
                    return@withLock Ok(KvVersionAppendResult.Conflict(currentHead = currentHead?.decode(namespace)))
                }

                val stored =
                    KottageKvVersionEnvelope(
                        versionId = Uuid.random().toString(),
                        previousVersionId = expectedPreviousVersionId,
                        value = namespace.codec.encode(value),
                        createdAtEpochMillis = now,
                        expiresAtEpochMillis = if (ttl.isInfinite()) Long.MAX_VALUE else now + ttl.inWholeMilliseconds,
                    )
                val updated =
                    KottageKvVersionChainEnvelope(
                        headVersionId = stored.versionId,
                        entries = (chain?.entries ?: emptyList()) + stored,
                    )
                // One Kottage value is one SQLite transaction, so the chain and its head advance atomically.
                storage.put(versionChainKey(namespace, key), updated)
                Ok(KvVersionAppendResult.Applied(entry = stored.decode(namespace)))
            } catch (expected: Exception) {
                versionError(namespace, key, "append", "KV_KOTTAGE_VERSION_APPEND_FAILED", expected)
            }
        }

    override suspend fun deleteVersioned(
        namespace: KvNamespaceId,
        key: String,
    ): IdkResult<Boolean, IdkError> =
        mutationMutex.withLock {
            try {
                Ok(storage.remove(versionChainKey(namespace, key)))
            } catch (expected: Exception) {
                versionError(namespace, key, "delete", "KV_KOTTAGE_VERSION_DELETE_FAILED", expected)
            }
        }

    private fun indexKey(namespace: KvNamespaceId): String = "$INDEX_PREFIX:${namespace.name}"

    private fun versionChainKey(
        namespace: KvNamespaceId,
        key: String,
    ): String = "$VERSION_CHAIN_PREFIX:${namespace.name.length}:${namespace.name}:$key"

    private suspend fun readLiveVersionChain(
        namespace: KvNamespaceId,
        key: String,
        now: Long,
    ): KottageKvVersionChainEnvelope? {
        val storageKey = versionChainKey(namespace, key)
        val chain = storage.getOrNull<KottageKvVersionChainEnvelope>(storageKey) ?: return null
        val head = chain.entries.firstOrNull { it.versionId == chain.headVersionId }
        if (head == null || head.expiresAtEpochMillis <= now) {
            storage.remove(storageKey)
            return null
        }
        return chain
    }

    private fun <V : Any> KottageKvVersionEnvelope.decode(namespace: KvNamespace<V>): KvVersionedEntry<V> =
        KvVersionedEntry(
            versionId = versionId,
            previousVersionId = previousVersionId,
            value = namespace.codec.decode(value),
            metadata = KvEntryMetadata(createdAtEpochMillis = createdAtEpochMillis, expiresAtEpochMillis = expiresAtEpochMillis),
        )

    private suspend fun readIndex(namespace: KvNamespaceId): KottageKvKeyIndex = storage.getOrNull<KottageKvKeyIndex>(indexKey(namespace)) ?: KottageKvKeyIndex()

    private suspend fun writeIndex(
        namespace: KvNamespaceId,
        index: KottageKvKeyIndex,
    ) {
        storage.put(indexKey(namespace), index)
    }

    private fun putError(
        namespace: KvNamespaceId,
        key: String,
        expected: Exception,
    ): IdkResult<KvPutResult, IdkError> =
        Err(
            IdkError.fromString(
                message = "Failed to put KV entry '${namespace.name}:$key' to Kottage: ${expected.message}",
                exception = expected,
                code = "KV_KOTTAGE_PUT_FAILED",
            ),
        )

    private fun <T> versionError(
        namespace: KvNamespaceId,
        key: String,
        operation: String,
        code: String,
        expected: Exception,
    ): IdkResult<T, IdkError> =
        Err(
            IdkError.fromString(
                message = "Failed to $operation versioned KV entry '${namespace.name}:$key' in Kottage: ${expected.message}",
                exception = expected,
                code = code,
            ),
        )

    private companion object {
        const val INDEX_PREFIX = "__idk_kv_index__"
        const val VERSION_CHAIN_PREFIX = "__idk_kv_version_chain__"
    }
}
