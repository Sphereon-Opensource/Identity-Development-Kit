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

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.data.store.kv.KvPartitionKey
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreBackends
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreFactory
import com.sphereon.data.store.kv.KvStoreScopeBinding
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
@ContributesIntoSet(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryKvStoreFactoryImpl", exact = true)
class InMemoryKvStoreFactoryImpl(
    private val backingStorage: InMemoryKvBackingStorage,
) : KvStoreFactory {
    override val backendId: String = KvStoreBackends.MEMORY

    override fun create(
        config: KvStoreConfigBase,
        execution: SessionExecution?,
    ): KvStore {
        require(config.backendId.equals(backendId, ignoreCase = true)) {
            "InMemoryKvStoreFactory cannot create backend '${config.backendId}'. Supported: '$backendId'"
        }

        val partitionKey =
            when (config.scopeBinding) {
                KvStoreScopeBinding.APP -> {
                    KvPartitionKey(storeId = config.id)
                }

                KvStoreScopeBinding.TENANT -> {
                    val tenantId =
                        checkNotNull(
                            execution
                                ?.sessionContext
                                ?.context
                                ?.tenant
                                ?.tenantId,
                        ) { "Cannot create TENANT-scoped KV store without execution context with tenant information" }
                    KvPartitionKey(storeId = config.id, tenantId = tenantId)
                }

                KvStoreScopeBinding.PRINCIPAL_TENANT -> {
                    val context =
                        checkNotNull(execution?.sessionContext?.context) {
                            "Cannot create PRINCIPAL_TENANT-scoped KV store without execution context"
                        }
                    val tenantId = context.tenant.tenantId
                    val principalId =
                        checkNotNull(context.principal?.toString()) {
                            "Cannot create PRINCIPAL_TENANT-scoped KV store without principal information"
                        }
                    KvPartitionKey(storeId = config.id, tenantId = tenantId, principalId = principalId)
                }

                KvStoreScopeBinding.SESSION -> {
                    val sessionContext =
                        checkNotNull(execution?.sessionContext) {
                            "Cannot create SESSION-scoped KV store without execution context with session information"
                        }
                    val tenantId = sessionContext.context.tenant.tenantId
                    val principalId =
                        checkNotNull(sessionContext.context.principal?.toString()) {
                            "Cannot create SESSION-scoped KV store without principal information"
                        }
                    val sessionId = sessionContext.sessionId
                    KvPartitionKey(storeId = config.id, tenantId = tenantId, principalId = principalId, sessionId = sessionId)
                }
            }

        val partition = backingStorage.getPartition(partitionKey)
        return InMemoryKvStore(config = config, partition = partition)
    }
}
