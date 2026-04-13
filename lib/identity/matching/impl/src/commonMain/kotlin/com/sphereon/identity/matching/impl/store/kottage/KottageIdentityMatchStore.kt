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

package com.sphereon.identity.matching.impl.store.kottage

import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.matching.model.IdentityMatch
import com.sphereon.identity.matching.store.IdentityMatchStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Duration

/**
 * KvStore-backed implementation of [IdentityMatchStore] providing persistent
 * storage for identity match lookup records.
 *
 * Uses three KV namespaces for efficient lookups:
 * - Primary: `identity-match` namespace, key `{tenantId}:{matchId}` -> IdentityMatch
 * - Hash index: `identity-match-hash-idx` namespace, key `{tenantId}:{identifierHash}:{identifierType}` -> matchId
 * - Identity index: `identity-match-identity-idx` namespace, key `{tenantId}:{internalIdentityId}` -> list of matchIds
 *
 * @param store The KvStore instance
 */
class KottageIdentityMatchStore(
    private val store: KvStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : IdentityMatchStore {

    private val matchNs = KvNamespace(
        name = "identity-match",
        codec = KotlinxSerializationJsonKvCodec(json, IdentityMatch.serializer())
    )
    private val hashIndexNs = KvNamespace(
        name = "identity-match-hash-idx",
        codec = KotlinxSerializationJsonKvCodec(json, String.serializer())
    )
    private val identityIndexNs = KvNamespace(
        name = "identity-match-identity-idx",
        codec = KotlinxSerializationJsonKvCodec(json, ListSerializer(String.serializer()))
    )

    private fun matchKey(tenantId: String, matchId: String) = "$tenantId:$matchId"
    private fun hashKey(tenantId: String, identifierHash: String, identifierType: IdentifierType) =
        "$tenantId:$identifierHash:${identifierType.value}"
    private fun identityIndexKey(tenantId: String, internalIdentityId: String) =
        "$tenantId:$internalIdentityId"

    override suspend fun findByIdentifierHash(
        tenantId: String,
        identifierHash: String,
        identifierType: IdentifierType
    ): IdentityMatch? {
        val matchId = store.get(hashIndexNs, hashKey(tenantId, identifierHash, identifierType)).getOrNull()
            ?: return null
        return store.get(matchNs, matchKey(tenantId, matchId)).getOrNull()
    }

    override suspend fun findById(tenantId: String, matchId: String): IdentityMatch? {
        return store.get(matchNs, matchKey(tenantId, matchId)).getOrNull()
    }

    override suspend fun findByInternalIdentityId(tenantId: String, internalIdentityId: String): List<IdentityMatch> {
        val matchIds = store.get(identityIndexNs, identityIndexKey(tenantId, internalIdentityId)).getOrNull()
            ?: return emptyList()
        return matchIds.mapNotNull { matchId ->
            store.get(matchNs, matchKey(tenantId, matchId)).getOrNull()
        }
    }

    override suspend fun create(match: IdentityMatch): IdentityMatch {
        // Store the match record
        store.put(matchNs, matchKey(match.tenantId, match.id), match, Duration.INFINITE)

        // Store hash index
        store.put(hashIndexNs, hashKey(match.tenantId, match.identifierHash, match.identifierType), match.id, Duration.INFINITE)

        // Update identity index (append matchId to the list)
        val key = identityIndexKey(match.tenantId, match.internalIdentityId)
        val matchIds = store.get(identityIndexNs, key).getOrNull()?.toMutableList() ?: mutableListOf()
        if (match.id !in matchIds) {
            matchIds.add(match.id)
        }
        store.put(identityIndexNs, key, matchIds, Duration.INFINITE)

        return match
    }

    override suspend fun update(match: IdentityMatch): IdentityMatch {
        // Read existing record to detect indexed field changes
        val existing = findById(match.tenantId, match.id)
        if (existing != null) {
            // Clean up stale hash index if identifierHash or identifierType changed
            if (existing.identifierHash != match.identifierHash || existing.identifierType != match.identifierType) {
                store.delete(hashIndexNs, hashKey(existing.tenantId, existing.identifierHash, existing.identifierType))
            }
            // Clean up stale identity index if internalIdentityId changed
            if (existing.internalIdentityId != match.internalIdentityId) {
                val oldKey = identityIndexKey(existing.tenantId, existing.internalIdentityId)
                val matchIds = store.get(identityIndexNs, oldKey).getOrNull()?.toMutableList()
                if (matchIds != null) {
                    matchIds.remove(match.id)
                    if (matchIds.isEmpty()) {
                        store.delete(identityIndexNs, oldKey)
                    } else {
                        store.put(identityIndexNs, oldKey, matchIds, Duration.INFINITE)
                    }
                }
                // Add to new identity index
                val newKey = identityIndexKey(match.tenantId, match.internalIdentityId)
                val newMatchIds = store.get(identityIndexNs, newKey).getOrNull()?.toMutableList() ?: mutableListOf()
                if (match.id !in newMatchIds) {
                    newMatchIds.add(match.id)
                }
                store.put(identityIndexNs, newKey, newMatchIds, Duration.INFINITE)
            }
        }

        // Write primary record
        store.put(matchNs, matchKey(match.tenantId, match.id), match, Duration.INFINITE)

        // Write current hash index
        store.put(hashIndexNs, hashKey(match.tenantId, match.identifierHash, match.identifierType), match.id, Duration.INFINITE)

        return match
    }

    override suspend fun delete(tenantId: String, matchId: String): Boolean {
        val match = store.get(matchNs, matchKey(tenantId, matchId)).getOrNull()
            ?: return false

        // Remove hash index
        store.delete(hashIndexNs, hashKey(match.tenantId, match.identifierHash, match.identifierType))

        // Remove from identity index
        val key = identityIndexKey(match.tenantId, match.internalIdentityId)
        val matchIds = store.get(identityIndexNs, key).getOrNull()?.toMutableList()
        if (matchIds != null) {
            matchIds.remove(matchId)
            if (matchIds.isEmpty()) {
                store.delete(identityIndexNs, key)
            } else {
                store.put(identityIndexNs, key, matchIds, Duration.INFINITE)
            }
        }

        // Remove match record
        store.delete(matchNs, matchKey(tenantId, matchId))
        return true
    }
}
