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

package com.sphereon.crypto.kms.keystore.memory

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * App-scoped backing storage for MemoryKeyStore.
 *
 * This singleton maintains separate storage partitions based on StoragePartitionKey.
 * It enables multi-tenant support where keys and certificates are isolated by:
 * - tenant only (for REST APIs)
 * - tenant + principal (for user-specific data)
 * - tenant + principal + session (for session-specific data)
 * - or app-wide (single shared storage)
 *
 * Note: This is a simple in-memory implementation. For production use with high concurrency,
 * consider using platform-specific concurrent collections or add explicit locking.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("MemoryKeyStoreBackingStorageImpl", exact = true)
class MemoryKeyStoreBackingStorageImpl : MemoryKeyStoreBackingStorage {
    private val partitions = mutableMapOf<StoragePartitionKey, StoragePartition>()

    /**
     * Gets or creates a storage partition for the given key.
     * Note: getOrPut is not fully thread-safe in all KMP targets, but should be sufficient
     * for most use cases. For high-concurrency scenarios, consider adding platform-specific locking.
     */
    override fun getPartition(partitionKey: StoragePartitionKey): StoragePartition = partitions.getOrPut(partitionKey) { StoragePartition() }

    /**
     * Removes a storage partition, clearing all associated keys and certificates.
     * Useful for cleanup when a session ends or a tenant is removed.
     */
    override fun removePartition(partitionKey: StoragePartitionKey): Boolean = partitions.remove(partitionKey) != null

    /**
     * Clears all storage partitions. Use with caution - typically only for testing or shutdown.
     */
    override fun clearAll() {
        partitions.clear()
    }

    /**
     * Returns the number of active storage partitions.
     */
    override fun getPartitionCount(): Int = partitions.size

    /**
     * Returns all partition keys (useful for diagnostics/monitoring).
     */
    override fun getPartitionKeys(): Set<StoragePartitionKey> = partitions.keys.toSet()
}
