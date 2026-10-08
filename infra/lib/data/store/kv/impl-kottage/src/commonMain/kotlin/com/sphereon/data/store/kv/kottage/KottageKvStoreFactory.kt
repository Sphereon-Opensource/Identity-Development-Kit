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

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.data.store.kv.KvPartitionKey
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreBackends
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreFactory
import com.sphereon.data.store.kv.KvStoreScopeBinding
import io.github.irgaly.kottage.Kottage
import kotlinx.coroutines.sync.Mutex

/**
 * Creates Kottage-backed [KvStore] instances from a provided [Kottage] database handle.
 *
 * The caller controls:
 * - where the database lives (directoryPath)
 * - lifecycle (CoroutineScope) and when it is closed
 */
class KottageKvStoreFactory(
    private val kottage: Kottage,
    private val storageNamePrefix: String = "idk-kv",
) : KvStoreFactory {
    override val backendId: String = KvStoreBackends.KOTTAGE

    /** Serializes index/value mutations across every store view created by this app instance. */
    private val mutationMutex = Mutex()

    override fun create(
        config: KvStoreConfigBase,
        execution: SessionExecution?,
    ): KvStore {
        require(config.backendId.equals(backendId, ignoreCase = true)) {
            "KottageKvStoreFactory cannot create backend '${config.backendId}'. Supported: '$backendId'"
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

        val storageName = storageNameFor(partitionKey)
        val storage = kottage.storage(storageName)
        return KottageKvStore(config = config, storage = storage, mutationMutex = mutationMutex)
    }

    private fun storageNameFor(partitionKey: KvPartitionKey): String {
        val raw =
            buildString {
                append(storageNamePrefix)
                append("_")
                append(partitionKey.storeId)
                append("_")
                append(partitionKey.tenantId ?: "app")
                append("_")
                append(partitionKey.principalId ?: "all")
                append("_")
                append(partitionKey.sessionId ?: "shared")
            }

        val sanitized =
            raw
                .map { ch ->
                    when {
                        ch.isLetterOrDigit() -> ch
                        ch == '-' || ch == '_' -> ch
                        else -> '_'
                    }
                }.joinToString("")

        if (sanitized.length <= 120) {
            return sanitized
        }
        val suffix = "_h${raw.hashCode().toString(16)}"
        return sanitized.take(120 - suffix.length) + suffix
    }
}
