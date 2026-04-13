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

package com.sphereon.openid.oid4vci.issuer.impl.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KvEntry
import com.sphereon.data.store.kv.KvEntryMetadata
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvNamespaceId
import com.sphereon.data.store.kv.KvPutResult
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.impl.KvStoreManager
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Minimal in-memory KV store for unit testing store implementations.
 */
internal class SimpleTestKvStore : KvStore {
    override val config: KvStoreConfigBase = InMemoryKvStoreConfig(id = "test", scopeBinding = KvStoreScopeBinding.APP)

    private data class Stored(
        val bytes: ByteArray,
        val createdAt: Long,
        val expiresAt: Long,
    )

    private val entries = mutableMapOf<String, Stored>()

    private fun k(
        namespace: KvNamespaceId,
        key: String,
    ) = "${namespace.name}:$key"

    override suspend fun <V : Any> put(
        namespace: KvNamespace<V>,
        key: String,
        value: V,
        ttl: Duration,
    ): IdkResult<KvPutResult, IdkError> {
        val now = Clock.System.now().toEpochMilliseconds()
        val expiresAt = if (ttl.isInfinite()) Long.MAX_VALUE else now + ttl.inWholeMilliseconds
        entries[k(namespace, key)] = Stored(bytes = namespace.codec.encode(value), createdAt = now, expiresAt = expiresAt)
        return Ok(KvPutResult(metadata = KvEntryMetadata(createdAtEpochMillis = now, expiresAtEpochMillis = expiresAt)))
    }

    override suspend fun <V : Any> get(
        namespace: KvNamespace<V>,
        key: String,
    ): IdkResult<V?, IdkError> = getEntry(namespace, key).map { it?.value }

    override suspend fun <V : Any> getEntry(
        namespace: KvNamespace<V>,
        key: String,
    ): IdkResult<KvEntry<V>?, IdkError> {
        val now = Clock.System.now().toEpochMilliseconds()
        val stored = entries[k(namespace, key)] ?: return Ok(null)
        if (stored.expiresAt <= now) {
            entries.remove(k(namespace, key))
            return Ok(null)
        }
        val value = namespace.codec.decode(stored.bytes)
        return Ok(KvEntry(value = value, metadata = KvEntryMetadata(createdAtEpochMillis = stored.createdAt, expiresAtEpochMillis = stored.expiresAt)))
    }

    override suspend fun delete(
        namespace: KvNamespaceId,
        key: String,
    ): IdkResult<Boolean, IdkError> = Ok(entries.remove(k(namespace, key)) != null)

    override suspend fun exists(
        namespace: KvNamespaceId,
        key: String,
    ): IdkResult<Boolean, IdkError> {
        val now = Clock.System.now().toEpochMilliseconds()
        val stored = entries[k(namespace, key)] ?: return Ok(false)
        if (stored.expiresAt <= now) {
            entries.remove(k(namespace, key))
            return Ok(false)
        }
        return Ok(true)
    }

    override suspend fun touch(
        namespace: KvNamespaceId,
        key: String,
        ttl: Duration,
    ): IdkResult<Boolean, IdkError> {
        val now = Clock.System.now().toEpochMilliseconds()
        val stored = entries[k(namespace, key)] ?: return Ok(false)
        if (stored.expiresAt <= now) {
            entries.remove(k(namespace, key))
            return Ok(false)
        }
        val expiresAt = if (ttl.isInfinite()) Long.MAX_VALUE else now + ttl.inWholeMilliseconds
        entries[k(namespace, key)] = stored.copy(expiresAt = expiresAt)
        return Ok(true)
    }

    override suspend fun cleanupExpired(namespace: KvNamespaceId?): IdkResult<Int, IdkError> {
        val now = Clock.System.now().toEpochMilliseconds()
        val keysToRemove =
            entries
                .filter { (fullKey, stored) ->
                    (namespace == null || fullKey.startsWith("${namespace.name}:")) && stored.expiresAt <= now
                }.keys
                .toList()
        keysToRemove.forEach { entries.remove(it) }
        return Ok(keysToRemove.size)
    }
}

/**
 * Test KvStoreManager that always returns the same in-memory store.
 */
internal class InMemoryTestKvStoreManager : KvStoreManager {
    private val store: KvStore = SimpleTestKvStore()

    override fun createFromKvStoreConfig(config: KvStoreConfigBase): KvStore = store

    override fun createFromKvStoreConfig(
        config: KvStoreConfigBase,
        execution: SessionExecution?,
    ): KvStore = store

    override fun createFromProperties(
        configService: ConfigService,
        execution: SessionExecution?,
    ): Set<KvStore> = setOf(store)
}

/**
 * No-op SessionExecution for tests that only need to pass it through to KvStoreManager.
 */
internal class NoOpSessionExecution : SessionExecution {
    override val sessionContextManager: SessionContextManager get() = throw UnsupportedOperationException("not used in store tests")
    override val sessionContext: SessionContext get() = throw UnsupportedOperationException("not used in store tests")
    override val log: SessionLogService get() = throw UnsupportedOperationException("not used in store tests")
    override val conf: ContextConfig get() = throw UnsupportedOperationException("not used in store tests")
}
