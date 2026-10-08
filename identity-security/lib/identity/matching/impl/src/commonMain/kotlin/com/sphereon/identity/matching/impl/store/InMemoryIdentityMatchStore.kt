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

package com.sphereon.identity.matching.impl.store

import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.matching.model.IdentityMatch
import com.sphereon.identity.matching.store.IdentityMatchStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<IdentityMatchStore>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryIdentityMatchStoreImpl", exact = true)
class InMemoryIdentityMatchStore : IdentityMatchStore {
    private data class HashKey(
        val tenantId: String,
        val identifierHash: String,
        val identifierType: IdentifierType,
    )

    // Primary storage: tenantId+matchId -> IdentityMatch
    private val byId = mutableMapOf<String, IdentityMatch>()

    // Hash index: HashKey -> matchId
    private val byHash = mutableMapOf<HashKey, String>()

    private fun compositeId(
        tenantId: String,
        matchId: String,
    ) = "$tenantId:$matchId"

    override suspend fun findByIdentifierHash(
        tenantId: String,
        identifierHash: String,
        identifierType: IdentifierType,
    ): IdentityMatch? {
        val key = HashKey(tenantId, identifierHash, identifierType)
        val matchId = byHash[key] ?: return null
        return byId[compositeId(tenantId, matchId)]
    }

    override suspend fun findById(
        tenantId: String,
        matchId: String,
    ): IdentityMatch? = byId[compositeId(tenantId, matchId)]

    override suspend fun findByInternalIdentityId(
        tenantId: String,
        internalIdentityId: String,
    ): List<IdentityMatch> =
        byId.values.filter {
            it.tenantId == tenantId && it.internalIdentityId == internalIdentityId
        }

    override suspend fun create(match: IdentityMatch): IdentityMatch {
        val cid = compositeId(match.tenantId, match.id)
        byId[cid] = match
        byHash[HashKey(match.tenantId, match.identifierHash, match.identifierType)] = match.id
        return match
    }

    override suspend fun update(match: IdentityMatch): IdentityMatch {
        val cid = compositeId(match.tenantId, match.id)
        require(byId.containsKey(cid)) { "Match not found: ${match.id}" }
        byId[cid] = match
        return match
    }

    override suspend fun delete(
        tenantId: String,
        matchId: String,
    ): Boolean {
        val cid = compositeId(tenantId, matchId)
        val match = byId.remove(cid) ?: return false
        byHash.remove(HashKey(match.tenantId, match.identifierHash, match.identifierType))
        return true
    }
}
