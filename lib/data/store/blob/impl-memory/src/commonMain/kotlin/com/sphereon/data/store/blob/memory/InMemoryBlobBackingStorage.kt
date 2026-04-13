/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.blob.memory

import com.sphereon.data.store.blob.BlobMetadata
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

data class InMemoryBlobPartitionKey(
    val storeId: String,
    val tenantId: String? = null,
)

data class InMemoryStoredBlob(
    val data: ByteArray,
    val metadata: BlobMetadata,
    val createdAtEpochMillis: Long,
    val lastModifiedAtEpochMillis: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is InMemoryStoredBlob) {
            return false
        }
        return data.contentEquals(other.data) && metadata == other.metadata &&
            createdAtEpochMillis == other.createdAtEpochMillis &&
            lastModifiedAtEpochMillis == other.lastModifiedAtEpochMillis
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + createdAtEpochMillis.hashCode()
        result = 31 * result + lastModifiedAtEpochMillis.hashCode()
        return result
    }
}

data class InMemoryBlobPartition(
    internal val mutex: Mutex = Mutex(),
    internal val blobs: MutableMap<String, InMemoryStoredBlob> = mutableMapOf(),
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryBlobBackingStorage", exact = true)
interface InMemoryBlobBackingStorage {
    fun getPartition(partitionKey: InMemoryBlobPartitionKey): InMemoryBlobPartition

    fun removePartition(partitionKey: InMemoryBlobPartitionKey): Boolean

    fun clearAll()
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryBlobBackingStorageImpl", exact = true)
class InMemoryBlobBackingStorageImpl : InMemoryBlobBackingStorage {
    private val partitions = mutableMapOf<InMemoryBlobPartitionKey, InMemoryBlobPartition>()

    override fun getPartition(partitionKey: InMemoryBlobPartitionKey): InMemoryBlobPartition = partitions.getOrPut(partitionKey) { InMemoryBlobPartition() }

    override fun removePartition(partitionKey: InMemoryBlobPartitionKey): Boolean = partitions.remove(partitionKey) != null

    override fun clearAll() {
        partitions.clear()
    }
}
