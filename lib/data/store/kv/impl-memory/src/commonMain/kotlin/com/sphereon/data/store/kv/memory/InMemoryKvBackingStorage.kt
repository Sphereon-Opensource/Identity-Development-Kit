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

package com.sphereon.data.store.kv.memory

import com.sphereon.data.store.kv.KvPartitionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

data class InMemoryKvEntryKey(
    val namespace: String,
    val key: String,
)

data class InMemoryKvStoredBytes(
    val value: ByteArray,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)

data class InMemoryKvVersionedStoredBytes(
    val versionId: String,
    val previousVersionId: String?,
    val value: ByteArray,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)

data class InMemoryKvVersionChain(
    var headVersionId: String,
    val entries: MutableMap<String, InMemoryKvVersionedStoredBytes> = mutableMapOf(),
)

data class InMemoryKvPartition(
    internal val mutex: Mutex = Mutex(),
    internal val entries: MutableMap<InMemoryKvEntryKey, InMemoryKvStoredBytes> = mutableMapOf(),
    internal val versionChains: MutableMap<InMemoryKvEntryKey, InMemoryKvVersionChain> = mutableMapOf(),
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryKvBackingStorage", exact = true)
interface InMemoryKvBackingStorage {
    fun getPartition(partitionKey: KvPartitionKey): InMemoryKvPartition

    fun removePartition(partitionKey: KvPartitionKey): Boolean

    fun clearAll()
}

/**
 * App-scoped backing storage for in-memory KV.
 *
 * This ensures that tenant/principal/session-scoped KV data can survive the lifetime of request-scoped
 * objects in REST APIs (session-per-request), while still being correctly partitioned by [KvPartitionKey].
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryKvBackingStorageImpl", exact = true)
class InMemoryKvBackingStorageImpl : InMemoryKvBackingStorage {
    private val partitions = mutableMapOf<KvPartitionKey, InMemoryKvPartition>()

    override fun getPartition(partitionKey: KvPartitionKey): InMemoryKvPartition = partitions.getOrPut(partitionKey) { InMemoryKvPartition() }

    override fun removePartition(partitionKey: KvPartitionKey): Boolean = partitions.remove(partitionKey) != null

    override fun clearAll() {
        partitions.clear()
    }
}
