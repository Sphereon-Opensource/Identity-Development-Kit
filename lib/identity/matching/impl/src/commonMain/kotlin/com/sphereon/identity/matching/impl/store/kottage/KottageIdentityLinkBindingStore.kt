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

package com.sphereon.identity.matching.impl.store.kottage

import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.identity.matching.model.IdentityLinkBinding
import com.sphereon.identity.matching.store.IdentityLinkBindingStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * KvStore-backed implementation of [IdentityLinkBindingStore] providing persistent
 * storage for encrypted identity link binding records.
 *
 * Uses four KV namespaces for efficient lookups:
 * - Primary: `identity-link-binding` namespace -> IdentityLinkBinding
 * - Match index: `identity-link-binding-match-idx` namespace -> bindingId
 * - Holder hash index: `identity-link-binding-holder-idx` namespace -> bindingId
 * - Tenant index: `identity-link-binding-tenant-idx` namespace -> list of bindingIds
 *
 * @param store The KvStore instance
 */
class KottageIdentityLinkBindingStore(
    private val store: KvStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : IdentityLinkBindingStore {
    private val bindingNs =
        KvNamespace(
            name = "identity-link-binding",
            codec = KotlinxSerializationJsonKvCodec(json, IdentityLinkBinding.serializer()),
        )
    private val matchIndexNs =
        KvNamespace(
            name = "identity-link-binding-match-idx",
            codec = KotlinxSerializationJsonKvCodec(json, String.serializer()),
        )
    private val holderIndexNs =
        KvNamespace(
            name = "identity-link-binding-holder-idx",
            codec = KotlinxSerializationJsonKvCodec(json, String.serializer()),
        )
    private val tenantIndexNs =
        KvNamespace(
            name = "identity-link-binding-tenant-idx",
            codec = KotlinxSerializationJsonKvCodec(json, ListSerializer(String.serializer())),
        )

    private fun bindingKey(
        tenantId: String,
        bindingId: String,
    ) = "$tenantId:$bindingId"

    private fun matchIndexKey(
        tenantId: String,
        matchId: String,
    ) = "$tenantId:$matchId"

    private fun holderIndexKey(
        tenantId: String,
        holderHash: String,
    ) = "$tenantId:$holderHash"

    private fun tenantIndexKey(tenantId: String) = tenantId

    override suspend fun create(binding: IdentityLinkBinding): IdentityLinkBinding {
        // Store the binding record
        store.put(bindingNs, bindingKey(binding.tenantId, binding.id), binding, Duration.INFINITE)

        // Store match index
        store.put(matchIndexNs, matchIndexKey(binding.tenantId, binding.matchId), binding.id, Duration.INFINITE)

        // Store holder hash index
        store.put(holderIndexNs, holderIndexKey(binding.tenantId, binding.holderIdentifierHash), binding.id, Duration.INFINITE)

        // Update tenant index for expiry scanning
        addToTenantIndex(binding.tenantId, binding.id)

        return binding
    }

    override suspend fun findByMatchId(
        tenantId: String,
        matchId: String,
    ): IdentityLinkBinding? {
        val bindingId =
            store.get(matchIndexNs, matchIndexKey(tenantId, matchId)).getOrNull()
                ?: return null
        return findByIdInternal(tenantId, bindingId)
    }

    override suspend fun findByHolderHash(
        tenantId: String,
        holderHash: String,
    ): IdentityLinkBinding? {
        val bindingId =
            store.get(holderIndexNs, holderIndexKey(tenantId, holderHash)).getOrNull()
                ?: return null
        return findByIdInternal(tenantId, bindingId)
    }

    override suspend fun update(binding: IdentityLinkBinding): IdentityLinkBinding {
        // Read existing to clean up old indexes if hashes changed
        val existing = findByIdInternal(binding.tenantId, binding.id)
        if (existing != null) {
            if (existing.matchId != binding.matchId) {
                store.delete(matchIndexNs, matchIndexKey(existing.tenantId, existing.matchId))
            }
            if (existing.holderIdentifierHash != binding.holderIdentifierHash) {
                store.delete(holderIndexNs, holderIndexKey(existing.tenantId, existing.holderIdentifierHash))
            }
        }

        store.put(bindingNs, bindingKey(binding.tenantId, binding.id), binding, Duration.INFINITE)
        store.put(matchIndexNs, matchIndexKey(binding.tenantId, binding.matchId), binding.id, Duration.INFINITE)
        store.put(holderIndexNs, holderIndexKey(binding.tenantId, binding.holderIdentifierHash), binding.id, Duration.INFINITE)

        return binding
    }

    override suspend fun delete(
        tenantId: String,
        bindingId: String,
    ): Boolean {
        val binding = findByIdInternal(tenantId, bindingId) ?: return false

        // Remove all indexes
        store.delete(matchIndexNs, matchIndexKey(tenantId, binding.matchId))
        store.delete(holderIndexNs, holderIndexKey(tenantId, binding.holderIdentifierHash))
        store.delete(bindingNs, bindingKey(tenantId, bindingId))

        // Remove from tenant index
        removeFromTenantIndex(tenantId, bindingId)

        return true
    }

    override suspend fun findExpired(
        tenantId: String,
        inactiveSince: Instant,
    ): List<IdentityLinkBinding> {
        val bindingIds = getTenantIndex(tenantId)
        return bindingIds
            .mapNotNull { bindingId ->
                findByIdInternal(tenantId, bindingId)
            }.filter { binding ->
                val lastActivity = binding.lastUsedAt ?: binding.updatedAt ?: binding.createdAt
                lastActivity < inactiveSince
            }
    }

    private suspend fun findByIdInternal(
        tenantId: String,
        bindingId: String,
    ): IdentityLinkBinding? = store.get(bindingNs, bindingKey(tenantId, bindingId)).getOrNull()

    private suspend fun addToTenantIndex(
        tenantId: String,
        bindingId: String,
    ) {
        val ids = getTenantIndex(tenantId).toMutableList()
        if (bindingId !in ids) {
            ids.add(bindingId)
        }
        store.put(tenantIndexNs, tenantIndexKey(tenantId), ids, Duration.INFINITE)
    }

    private suspend fun removeFromTenantIndex(
        tenantId: String,
        bindingId: String,
    ) {
        val ids = getTenantIndex(tenantId).toMutableList()
        ids.remove(bindingId)
        if (ids.isEmpty()) {
            store.delete(tenantIndexNs, tenantIndexKey(tenantId))
        } else {
            store.put(tenantIndexNs, tenantIndexKey(tenantId), ids, Duration.INFINITE)
        }
    }

    private suspend fun getTenantIndex(tenantId: String): List<String> = store.get(tenantIndexNs, tenantIndexKey(tenantId)).getOrNull() ?: emptyList()
}
