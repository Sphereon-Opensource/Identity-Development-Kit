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

package com.sphereon.openid.oid4vci.holder.impl.store

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
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.impl.KvStoreManager
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.holder.DeferredPollingEntry
import com.sphereon.openid.oid4vci.holder.DeferredPollingStatus
import com.sphereon.openid.oid4vci.holder.DeferredPollingStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.days

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeferredPollingStore>())
class KvDeferredPollingStore(
    private val kvStoreManager: KvStoreManager,
    private val execution: SessionExecution,
) : DeferredPollingStore {
    private val namespace =
        KvNamespace(
            name = "oid4vci-holder-deferred-polling",
            codec = KotlinxSerializationJsonKvCodec(json = Json, serializer = DeferredPollingEntry.serializer()),
        )

    /**
     * Index namespace: stores a single key "_index" containing the list of all known transaction IDs.
     * This is required because [KvStore] has no scan/list capability without [KvStoreListing].
     */
    private val indexNamespace =
        KvNamespace(
            name = "oid4vci-holder-deferred-polling.index",
            codec = KotlinxSerializationJsonKvCodec(json = Json, serializer = ListSerializer(String.serializer())),
        )

    private val storeConfig: KvStoreConfigBase =
        InMemoryKvStoreConfig(
            id = "oid4vci-holder-deferred-polling",
            scopeBinding = KvStoreScopeBinding.APP,
        )

    private val kv: KvStore by lazy {
        kvStoreManager.createFromKvStoreConfig(storeConfig, execution)
    }

    override suspend fun schedule(entry: DeferredPollingEntry): IdkResult<Unit, IdkError> {
        kv.put(namespace, entry.transactionId, entry, ttl = 7.days).getOrElse { return Err(it) }
        val currentIds = kv.get(indexNamespace, INDEX_KEY).getOrElse { return Err(it) } ?: emptyList()
        if (entry.transactionId !in currentIds) {
            val updated = currentIds + entry.transactionId
            kv.put(indexNamespace, INDEX_KEY, updated, ttl = 7.days).getOrElse { return Err(it) }
        }
        return Ok(Unit)
    }

    override suspend fun getPending(): IdkResult<List<DeferredPollingEntry>, IdkError> {
        val ids = kv.get(indexNamespace, INDEX_KEY).getOrElse { return Err(it) } ?: return Ok(emptyList())
        val pending = mutableListOf<DeferredPollingEntry>()
        for (id in ids) {
            val entry = kv.get(namespace, id).getOrElse { return Err(it) } ?: continue
            if (entry.status == DeferredPollingStatus.PENDING) {
                pending.add(entry)
            }
        }
        return Ok(pending)
    }

    override suspend fun updateLastPolled(
        transactionId: String,
        timestamp: Long,
    ): IdkResult<Unit, IdkError> {
        val entry =
            kv.get(namespace, transactionId).getOrElse { return Err(it) }
                ?: return Ok(Unit)
        val updated = entry.copy(lastPolledAt = timestamp)
        kv.put(namespace, transactionId, updated, ttl = 7.days).getOrElse { return Err(it) }
        return Ok(Unit)
    }

    override suspend fun markCompleted(transactionId: String): IdkResult<Unit, IdkError> = updateStatus(transactionId, DeferredPollingStatus.COMPLETED)

    override suspend fun markFailed(transactionId: String): IdkResult<Unit, IdkError> = updateStatus(transactionId, DeferredPollingStatus.FAILED)

    private suspend fun updateStatus(
        transactionId: String,
        status: DeferredPollingStatus,
    ): IdkResult<Unit, IdkError> {
        val entry =
            kv.get(namespace, transactionId).getOrElse { return Err(it) }
                ?: return Ok(Unit)
        val updated = entry.copy(status = status)
        kv.put(namespace, transactionId, updated, ttl = 7.days).getOrElse { return Err(it) }
        return Ok(Unit)
    }

    private companion object {
        const val INDEX_KEY = "_index"
    }
}
