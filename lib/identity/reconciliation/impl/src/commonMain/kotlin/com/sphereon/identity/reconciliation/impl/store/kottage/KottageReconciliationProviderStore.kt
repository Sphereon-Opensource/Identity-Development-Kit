/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.identity.reconciliation.impl.store.kottage

import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.identity.reconciliation.model.ReconciliationProvider
import com.sphereon.identity.reconciliation.store.ReconciliationProviderStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Duration

/**
 * KvStore-backed implementation of [ReconciliationProviderStore] providing persistent
 * storage for reconciliation provider configurations.
 *
 * Uses two KV namespaces:
 * - Primary: `recon-provider` namespace -> ReconciliationProvider
 * - All-providers index: `recon-provider-idx` namespace, key `__all` -> list of providerIds
 *
 * @param store The KvStore instance
 */
class KottageReconciliationProviderStore(
    private val store: KvStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ReconciliationProviderStore {

    private val providerNs = KvNamespace(
        name = "recon-provider",
        codec = KotlinxSerializationJsonKvCodec(json, ReconciliationProvider.serializer())
    )
    private val indexNs = KvNamespace(
        name = "recon-provider-idx",
        codec = KotlinxSerializationJsonKvCodec(json, ListSerializer(String.serializer()))
    )

    companion object {
        private const val INDEX_KEY = "__all"
    }

    override suspend fun findById(providerId: String): ReconciliationProvider? {
        return store.get(providerNs, providerId).getOrNull()
    }

    override suspend fun findAll(): List<ReconciliationProvider> {
        val providerIds = getIndex()
        return providerIds.mapNotNull { providerId ->
            findById(providerId)
        }
    }

    override suspend fun save(provider: ReconciliationProvider): ReconciliationProvider {
        store.put(providerNs, provider.id, provider, Duration.INFINITE)

        // Update index
        addToIndex(provider.id)

        return provider
    }

    override suspend fun delete(providerId: String): Boolean {
        val exists = store.exists(providerNs, providerId).getOrNull() ?: false
        if (!exists) return false

        store.delete(providerNs, providerId)
        removeFromIndex(providerId)

        return true
    }

    private suspend fun addToIndex(providerId: String) {
        val ids = getIndex().toMutableList()
        if (providerId !in ids) {
            ids.add(providerId)
        }
        store.put(indexNs, INDEX_KEY, ids, Duration.INFINITE)
    }

    private suspend fun removeFromIndex(providerId: String) {
        val ids = getIndex().toMutableList()
        ids.remove(providerId)
        if (ids.isEmpty()) {
            store.delete(indexNs, INDEX_KEY)
        } else {
            store.put(indexNs, INDEX_KEY, ids, Duration.INFINITE)
        }
    }

    private suspend fun getIndex(): List<String> {
        return store.get(indexNs, INDEX_KEY).getOrNull() ?: emptyList()
    }
}
