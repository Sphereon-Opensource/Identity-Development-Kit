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

package com.sphereon.oauth2.server.authorization.impl.provider.storage

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.server.authorization.storage.CachedUserInfo
import com.sphereon.oauth2.server.authorization.storage.FederationSessionStore
import com.sphereon.oauth2.server.authorization.storage.FederationSessionStoreError
import com.sphereon.oauth2.server.authorization.storage.PendingFederation
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * AppScope in-memory [FederationSessionStore]. The default binding in pure-IDK BYO
 * deployments. EDK swaps in `PostgresFederationSessionStore` via Metro
 * `replaces = [InMemoryFederationSessionStore::class]` when its module is on classpath.
 *
 * TTL enforcement is lazy: expired rows stay in the map until the next read touches them.
 * That keeps the hot path off a sweeper thread while preserving the A-8 invariant that
 * readers of `getAuthenticatedUser` never observe cached claims without the completion flag
 * (both writes happen in a single `synchronized` block in [completePendingFederation]).
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<FederationSessionStore>())
class InMemoryFederationSessionStore(
    private val clock: Clock,
) : SynchronizedObject(),
    FederationSessionStore {
    private data class PendingEntry(
        val value: PendingFederation,
        val expiresAt: Instant
    )

    private data class ClaimsEntry(
        val value: CachedUserInfo,
        val expiresAt: Instant
    )

    // state -> entry
    private val pending = mutableMapOf<String, PendingEntry>()

    // userId -> entry
    private val claims = mutableMapOf<String, ClaimsEntry>()

    override suspend fun storePendingFederation(
        pending: PendingFederation,
        ttl: Duration,
    ): IdkResult<Unit, FederationSessionStoreError> =
        synchronized(this) {
            val now = clock.now()
            val existing = this.pending[pending.state]
            // Treat an unexpired existing row under the same state as a collision. Expired rows
            // are evicted on write, so they do not block a new store with the same state.
            if (existing != null && existing.expiresAt > now) {
                return@synchronized Err(FederationSessionStoreError.StateCollision(pending.state))
            }
            this.pending[pending.state] = PendingEntry(pending, now + ttl)
            Ok(Unit)
        }

    override suspend fun retrievePendingFederation(state: String,): IdkResult<PendingFederation?, FederationSessionStoreError> =
        synchronized(this) {
            val entry = pending[state] ?: return@synchronized Ok(null)
            if (entry.expiresAt <= clock.now()) {
                pending.remove(state)
                Ok(null)
            } else {
                Ok(entry.value)
            }
        }

    override suspend fun consumePendingFederation(state: String): IdkResult<PendingFederation?, FederationSessionStoreError> =
        synchronized(this) {
            val entry = pending[state] ?: return@synchronized Ok(null)
            val now = clock.now()
            if (entry.expiresAt <= now) {
                pending.remove(state)
                return@synchronized Ok(null)
            }
            if (entry.value.callbackConsumedAt != null || entry.value.completed) return@synchronized Ok(null)
            val consumed = entry.value.copy(callbackConsumedAt = now)
            pending[state] = entry.copy(value = consumed)
            Ok(consumed)
        }

    override suspend fun findCompletedPendingBySession(sessionId: String,): IdkResult<PendingFederation?, FederationSessionStoreError> =
        synchronized(this) {
            val now = clock.now()
            val iter = pending.entries.iterator()
            var found: PendingFederation? = null
            while (iter.hasNext()) {
                val entry = iter.next().value
                if (entry.expiresAt <= now) {
                    iter.remove()
                } else if (found == null && entry.value.sessionId == sessionId && entry.value.completed) {
                    found = entry.value
                }
            }
            Ok(found)
        }

    override suspend fun completePendingFederation(
        state: String,
        userId: String,
        claims: CachedUserInfo,
        claimsTtl: Duration,
        upstreamAcr: String?,
        upstreamAmr: List<String>?,
        evidence: com.sphereon.oauth2.server.authorization.model.NormalizedAuthenticationEvidence,
    ): IdkResult<Unit, FederationSessionStoreError> =
        synchronized(this) {
            val now = clock.now()
            val entry = pending[state]
            if (entry == null || entry.expiresAt <= now) {
                if (entry != null) pending.remove(state)
                return@synchronized Err(
                    FederationSessionStoreError.StorageFailure(
                        reason = "no pending federation for state='$state'",
                    ),
                )
            }
            // Preserve any pre-set upstream acr/amr on the pending record when the caller passes
            // null explicitly. In practice the pending record starts with null for both fields, so
            // this collapses to the caller-provided value when present.
            val updated =
                entry.value.copy(
                    completed = true,
                    userId = userId,
                    authenticatedAt = now,
                    upstreamAcr = upstreamAcr ?: entry.value.upstreamAcr,
                    upstreamAmr = upstreamAmr ?: entry.value.upstreamAmr,
                    evidence = evidence,
                )
            pending[state] = entry.copy(value = updated)
            this.claims[userId] = ClaimsEntry(claims, now + claimsTtl)
            Ok(Unit)
        }

    override suspend fun removePendingFederation(state: String,): IdkResult<Boolean, FederationSessionStoreError> =
        synchronized(this) {
            Ok(pending.remove(state) != null)
        }

    override suspend fun retrieveCachedUserClaims(userId: String,): IdkResult<CachedUserInfo?, FederationSessionStoreError> =
        synchronized(this) {
            val entry = claims[userId] ?: return@synchronized Ok(null)
            if (entry.expiresAt <= clock.now()) {
                claims.remove(userId)
                Ok(null)
            } else {
                Ok(entry.value)
            }
        }

    override suspend fun removeCachedUserClaims(userId: String,): IdkResult<Boolean, FederationSessionStoreError> =
        synchronized(this) {
            Ok(claims.remove(userId) != null)
        }
}
