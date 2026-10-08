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

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreBackends
import com.sphereon.data.store.blob.BlobStoreConfigBase
import com.sphereon.data.store.blob.BlobStoreFactory
import com.sphereon.data.store.blob.BlobStoreScopeBinding
import com.sphereon.data.store.blob.InMemoryBlobStoreConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<BlobStoreFactory>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryBlobStoreFactoryImpl", exact = true)
class InMemoryBlobStoreFactoryImpl(
    private val backingStorage: InMemoryBlobBackingStorage,
) : BlobStoreFactory {
    override val backendId: String = BlobStoreBackends.MEMORY

    override fun create(
        config: BlobStoreConfigBase,
        execution: SessionExecution?,
    ): BlobStore {
        require(config.backendId.equals(backendId, ignoreCase = true)) {
            "InMemoryBlobStoreFactory cannot create backend '${config.backendId}'. Supported: '$backendId'"
        }

        val typedConfig = config as? InMemoryBlobStoreConfig
        val maxEntries = typedConfig?.maxEntries ?: 0

        val partitionKey =
            when (config.scopeBinding) {
                BlobStoreScopeBinding.APP -> {
                    InMemoryBlobPartitionKey(storeId = config.id)
                }

                BlobStoreScopeBinding.TENANT -> {
                    val tenantId =
                        checkNotNull(
                            execution
                                ?.sessionContext
                                ?.context
                                ?.tenant
                                ?.tenantId,
                        ) { "Cannot create TENANT-scoped blob store without execution context with tenant information" }
                    InMemoryBlobPartitionKey(storeId = config.id, tenantId = tenantId)
                }
            }

        val partition = backingStorage.getPartition(partitionKey)
        return InMemoryBlobStore(partition = partition, maxEntries = maxEntries)
    }
}
