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

package com.sphereon.crypto.kms.keystore.memory

import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.di.context.UserContext
import com.sphereon.di.session.SessionContext
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Represents a storage partition key for MemoryKeyStore.
 * This key is used to isolate storage based on the configured scope binding and keystore ID.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("StoragePartitionKey", exact = true)
data class StoragePartitionKey(
    val keystoreId: String,
    val tenantId: String? = null,
    val principalId: String? = null,
    val sessionId: String? = null
) {
    companion object {
        fun appLevel(keystoreId: String) = StoragePartitionKey(keystoreId = keystoreId)

        fun forTenant(keystoreId: String, tenantId: String) =
            StoragePartitionKey(keystoreId = keystoreId, tenantId = tenantId)

        fun forTenantFromContext(keystoreId: String, context: UserContext) =
            forTenant(keystoreId, context.tenant.tenantId)

        fun forPrincipalTenant(keystoreId: String, tenantId: String, principalId: String) =
            StoragePartitionKey(keystoreId = keystoreId, tenantId = tenantId, principalId = principalId)

        fun forPrincipalTenantFromContext(keystoreId: String, context: UserContext) =
            forPrincipalTenant(keystoreId, context.tenant.tenantId, context.principal.toString())

        fun forSession(keystoreId: String, tenantId: String, principalId: String, sessionId: String) =
            StoragePartitionKey(keystoreId = keystoreId, tenantId = tenantId, principalId = principalId, sessionId = sessionId)

        fun forSessionFromContext(keystoreId: String, context: SessionContext) =
            forSession(keystoreId, context.context.tenant.tenantId, context.context.principal.toString(), context.sessionId)
    }
}

/**
 * Holds the actual storage for a single partition (keys, certificate chains, and certificates).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("StoragePartition", exact = true)
data class StoragePartition(
    val keys: MutableMap<String, ManagedKeyInfoType<*>> = mutableMapOf(),
    val certificateChains: MutableMap<String, Array<Certificate>> = mutableMapOf(),
    val certificates: MutableMap<String, Certificate> = mutableMapOf()
)


@OptIn(ExperimentalObjCName::class)
@ObjCName("MemoryKeyStoreBackingStorage", exact = true)
interface MemoryKeyStoreBackingStorage {

    /**
     * Gets or creates a storage partition for the given key.
     * Note: getOrPut is not fully thread-safe in all KMP targets, but should be sufficient
     * for most use cases. For high-concurrency scenarios, consider adding platform-specific locking.
     */
    fun getPartition(partitionKey: StoragePartitionKey): StoragePartition

    /**
     * Removes a storage partition, clearing all associated keys and certificates.
     * Useful for cleanup when a session ends or a tenant is removed.
     */
    fun removePartition(partitionKey: StoragePartitionKey): Boolean

    /**
     * Clears all storage partitions. Use with caution - typically only for testing or shutdown.
     */
    fun clearAll()

    /**
     * Returns the number of active storage partitions.
     */
    fun getPartitionCount(): Int

    /**
     * Returns all partition keys (useful for diagnostics/monitoring).
     */
    fun getPartitionKeys(): Set<StoragePartitionKey>
}
