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

package com.sphereon.data.store.blob.kv

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreConfigBase
import com.sphereon.data.store.blob.BlobStoreFactory
import com.sphereon.data.store.kv.KvStore
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

/**
 * Factory for creating [KvBlobStore] instances backed by KvStore.
 *
 * Resolves the backing KvStore by ID from the set of available [KvStoreFactory] multibindings.
 * The [KvBlobStoreConfig.kvStoreId] references a configured KvStore (e.g., memory or kottage).
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<BlobStoreFactory>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("KvBlobStoreFactoryImpl", exact = true)
class KvBlobStoreFactoryImpl(
    private val kvStoreFactories: Set<KvStoreFactory>,
) : BlobStoreFactory {
    override val backendId: String = KvBlobStoreConfig.BACKEND_ID

    override fun create(
        config: BlobStoreConfigBase,
        execution: SessionExecution?,
    ): BlobStore {
        require(config.backendId.equals(backendId, ignoreCase = true)) {
            "KvBlobStoreFactory cannot create backend '${config.backendId}'. Supported: '$backendId'"
        }

        val typedConfig =
            config as? KvBlobStoreConfig
                ?: throw IllegalArgumentException(
                    "KvStore blob store requires KvBlobStoreConfig, got ${config::class.simpleName}. " +
                        "Ensure 'blob.stores.${config.id}.type=kvstore' is set in config.",
                )

        // Create a KvStore config for the backing store
        val kvConfig =
            com.sphereon.data.store.kv.InMemoryKvStoreConfig(
                id = typedConfig.kvStoreId,
                scopeBinding =
                    when (typedConfig.scopeBinding) {
                        com.sphereon.data.store.blob.BlobStoreScopeBinding.APP -> KvStoreScopeBinding.APP
                        com.sphereon.data.store.blob.BlobStoreScopeBinding.TENANT -> KvStoreScopeBinding.TENANT
                    },
            )

        // Find the matching KvStore factory
        val kvFactory =
            kvStoreFactories
                .filter { it.backendId != "___NO_OP___" }
                .firstOrNull { it.backendId.equals(kvConfig.backendId, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "No KvStoreFactory found for backendId '${kvConfig.backendId}'. " +
                        "Available: ${kvStoreFactories.map { it.backendId }.distinct().sorted()}",
                )

        val kvStore = kvFactory.create(kvConfig, execution)
        return KvBlobStore(kvStore = kvStore, maxBlobSizeBytes = typedConfig.maxBlobSizeBytes)
    }
}
