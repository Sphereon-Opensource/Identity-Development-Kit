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

package com.sphereon.oauth2.server.authorization.storage

import com.sphereon.core.api.IdkResult
import kotlin.time.Duration

/**
 * Persistent store for pre-authenticated federation flow state. Carries two concerns:
 *
 * 1. The pending-federation record keyed by OAuth2 `state`, between initiate-time redirect and
 *    callback-time token exchange.
 * 2. The upstream-userinfo claims cache keyed by local userId, populated on successful
 *    completion and surfaced to downstream `getUserInfo` calls within a caller-supplied TTL.
 *
 * Implementations:
 * - IDK ships an in-memory AppScope default. Adequate for single-node pure-IDK BYO deployments.
 * - EDK ships a Postgres-backed impl that replaces the default via Metro `replaces` when on
 *   classpath. Required for multi-replica deployments where initiate and callback may land on
 *   different nodes.
 *
 * Atomicity contract: [completePendingFederation] writes the completion flag on the pending
 * record AND the claims cache entry in a single transaction / critical section. Readers of
 * [retrievePendingFederation] must filter on `completed = true` if they need the claims-cache
 * invariant.
 *
 * TTL contract: every write takes an explicit TTL. Reads MUST filter out expired rows so the
 * caller never observes a ghost of a timed-out session.
 */
interface FederationSessionStore {
    suspend fun storePendingFederation(
        pending: PendingFederation,
        ttl: Duration,
    ): IdkResult<Unit, FederationSessionStoreError>

    suspend fun retrievePendingFederation(state: String,): IdkResult<PendingFederation?, FederationSessionStoreError>

    /** Atomically claims an unexpired callback state. Exactly one concurrent caller can succeed. */
    suspend fun consumePendingFederation(state: String): IdkResult<PendingFederation?, FederationSessionStoreError>

    suspend fun findCompletedPendingBySession(sessionId: String,): IdkResult<PendingFederation?, FederationSessionStoreError>

    /**
     * Flip the pending-federation record to completed and atomically write the claims-cache
     * entry for the resolved [userId]. The [upstreamAcr] and [upstreamAmr] values captured from
     * the upstream id_token exchange are persisted onto the PendingFederation record alongside
     * the completion flag so downstream readers (e.g. [com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider.getAuthenticatedUser])
     * can surface the ground-truth OIDC `acr` / `amr` claims. Both default to null so legacy
     * callers that don't yet thread upstream authentication metadata compile unchanged.
     */
    suspend fun completePendingFederation(
        state: String,
        userId: String,
        claims: CachedUserInfo,
        claimsTtl: Duration,
        upstreamAcr: String? = null,
        upstreamAmr: List<String>? = null,
        evidence: com.sphereon.oauth2.server.authorization.model.NormalizedAuthenticationEvidence,
    ): IdkResult<Unit, FederationSessionStoreError>

    suspend fun removePendingFederation(state: String,): IdkResult<Boolean, FederationSessionStoreError>

    suspend fun retrieveCachedUserClaims(userId: String,): IdkResult<CachedUserInfo?, FederationSessionStoreError>

    suspend fun removeCachedUserClaims(userId: String,): IdkResult<Boolean, FederationSessionStoreError>
}
