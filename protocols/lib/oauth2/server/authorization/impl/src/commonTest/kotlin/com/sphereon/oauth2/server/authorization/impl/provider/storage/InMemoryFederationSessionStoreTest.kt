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

import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.server.authorization.provider.FlowContext
import com.sphereon.oauth2.server.authorization.model.NormalizedAuthenticationEvidence
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRoute
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRouteBinding
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRouteDecision
import com.sphereon.oauth2.server.authorization.storage.CachedUserInfo
import com.sphereon.oauth2.server.authorization.storage.FederationSessionStoreError
import com.sphereon.oauth2.server.authorization.storage.PendingFederation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Behavioural coverage of [InMemoryFederationSessionStore]. These tests pin down the
 * semantics the Postgres overlay must match, so they focus on the store/retrieve round
 * trip, TTL eviction on read, atomic completion + claims caching, and preservation of
 * upstream ACR/AMR values across the round trip.
 */
class InMemoryFederationSessionStoreTest {
    @Test
    fun storeAndRetrievePending() =
        runTest {
            val clock = FakeClock()
            val store = InMemoryFederationSessionStore(clock)
            val record = pending(state = "s1", sessionId = "sess-1")

            val stored = store.storePendingFederation(record, 5.minutes)
            assertTrue(stored.isOk)

            val retrieved = store.retrievePendingFederation("s1")
            assertTrue(retrieved.isOk)
            val value = retrieved.value
            assertNotNull(value)
            assertEquals("s1", value.state)
            assertEquals("sess-1", value.sessionId)
            assertFalse(value.completed)
            assertNull(value.userId)
            assertNull(value.authenticatedAt)
        }

    @Test
    fun retrievePendingForUnknownStateReturnsNull() =
        runTest {
            val store = InMemoryFederationSessionStore(FakeClock())

            val retrieved = store.retrievePendingFederation("never-stored")
            assertTrue(retrieved.isOk)
            assertNull(retrieved.value)
        }

    @Test
    fun retrievePendingReturnsNullAfterTtlExpiry() =
        runTest {
            val clock = FakeClock()
            val store = InMemoryFederationSessionStore(clock)
            val stored = store.storePendingFederation(pending(state = "s-exp"), 1.minutes)
            assertTrue(stored.isOk)

            clock.advance(2.minutes)

            val firstRead = store.retrievePendingFederation("s-exp")
            assertTrue(firstRead.isOk)
            assertNull(firstRead.value)

            // Row was evicted on the first read: we can now store a fresh record under the
            // same state without hitting the StateCollision branch, proving the eviction.
            val reStored = store.storePendingFederation(pending(state = "s-exp"), 1.minutes)
            assertTrue(reStored.isOk)
        }

    @Test
    fun storePendingRejectsUnexpiredStateCollision() =
        runTest {
            val store = InMemoryFederationSessionStore(FakeClock())
            val first = store.storePendingFederation(pending(state = "dup"), 5.minutes)
            assertTrue(first.isOk)

            val second = store.storePendingFederation(pending(state = "dup"), 5.minutes)
            assertTrue(second.isErr)
            val err = second.error
            assertIs<FederationSessionStoreError.StateCollision>(err)
            assertEquals("dup", err.state)
        }

    @Test
    fun storePendingReplacesExpiredRowForSameState() =
        runTest {
            val clock = FakeClock()
            val store = InMemoryFederationSessionStore(clock)

            val first =
                store.storePendingFederation(
                    pending(state = "reuse", sessionId = "sess-old"),
                    1.minutes,
                )
            assertTrue(first.isOk)

            clock.advance(2.minutes)

            val second =
                store.storePendingFederation(
                    pending(state = "reuse", sessionId = "sess-new"),
                    5.minutes,
                )
            assertTrue(second.isOk)

            val retrieved = store.retrievePendingFederation("reuse")
            assertTrue(retrieved.isOk)
            val value = retrieved.value
            assertNotNull(value)
            assertEquals("sess-new", value.sessionId)
        }

    @Test
    fun completePendingMarksCompletedAndCachesClaimsAtomically() =
        runTest {
            val clock = FakeClock()
            val store = InMemoryFederationSessionStore(clock)
            val record = pending(state = "s-ok", sessionId = "sess-ok")

            assertTrue(store.storePendingFederation(record, 5.minutes).isOk)

            clock.advance(1.minutes)
            val completedAt = clock.now()

            val userClaims = claims(userId = "user-1", email = "user-1@example.com")
            val completed =
                store.completePendingFederation(
                    state = "s-ok",
                    userId = "user-1",
                    claims = userClaims,
                    claimsTtl = 1.minutes,
                    evidence = evidence("s-ok"),
                )
            assertTrue(completed.isOk)

            val retrieved = store.retrievePendingFederation("s-ok")
            assertTrue(retrieved.isOk)
            val pendingValue = retrieved.value
            assertNotNull(pendingValue)
            assertTrue(pendingValue.completed)
            assertEquals("user-1", pendingValue.userId)
            assertEquals(completedAt, pendingValue.authenticatedAt)

            val cached = store.retrieveCachedUserClaims("user-1")
            assertTrue(cached.isOk)
            val cachedValue = cached.value
            assertNotNull(cachedValue)
            assertEquals(userClaims, cachedValue)

            val bySession = store.findCompletedPendingBySession("sess-ok")
            assertTrue(bySession.isOk)
            val foundBySession = bySession.value
            assertNotNull(foundBySession)
            assertEquals("s-ok", foundBySession.state)
            assertTrue(foundBySession.completed)
            assertEquals("user-1", foundBySession.userId)
        }

    @Test
    fun completePendingPersistsUpstreamAcrAndAmr() =
        runTest {
            val store = InMemoryFederationSessionStore(FakeClock())
            assertTrue(store.storePendingFederation(pending(state = "s-acr"), 5.minutes).isOk)

            val completed =
                store.completePendingFederation(
                    state = "s-acr",
                    userId = "u-acr",
                    claims = claims(userId = "u-acr"),
                    claimsTtl = 1.minutes,
                    upstreamAcr = "urn:example:acr:2",
                    upstreamAmr = listOf("pwd", "mfa"),
                    evidence = evidence("s-acr"),
                )
            assertTrue(completed.isOk)

            val retrieved = store.retrievePendingFederation("s-acr")
            assertTrue(retrieved.isOk)
            val value = retrieved.value
            assertNotNull(value)
            assertEquals("urn:example:acr:2", value.upstreamAcr)
            assertEquals(listOf("pwd", "mfa"), value.upstreamAmr)
        }

    @Test
    fun completePendingFailsForUnknownState() =
        runTest {
            val store = InMemoryFederationSessionStore(FakeClock())

            val completed =
                store.completePendingFederation(
                    state = "ghost",
                    userId = "u",
                    claims = claims(userId = "u"),
                    claimsTtl = 1.minutes,
                    evidence = evidence("ghost"),
                )
            assertTrue(completed.isErr)
            val err = completed.error
            assertIs<FederationSessionStoreError.StorageFailure>(err)
            assertTrue(err.reason.contains("ghost"))
        }

    @Test
    fun completePendingFailsForExpiredState() =
        runTest {
            val clock = FakeClock()
            val store = InMemoryFederationSessionStore(clock)
            assertTrue(store.storePendingFederation(pending(state = "s-late"), 1.minutes).isOk)

            clock.advance(2.minutes)

            val completed =
                store.completePendingFederation(
                    state = "s-late",
                    userId = "u",
                    claims = claims(userId = "u"),
                    claimsTtl = 1.minutes,
                    evidence = evidence("s-late"),
                )
            assertTrue(completed.isErr)
            assertIs<FederationSessionStoreError.StorageFailure>(completed.error)

            // Complete eviction: a fresh store under the same state must not hit a collision.
            val reStored = store.storePendingFederation(pending(state = "s-late"), 1.minutes)
            assertTrue(reStored.isOk)
        }

    @Test
    fun findCompletedPendingBySessionSkipsIncompleteAndExpired() =
        runTest {
            val clock = FakeClock()
            val store = InMemoryFederationSessionStore(clock)

            // X will expire before the lookup. Y is completed. Z stays pending-but-not-completed.
            assertTrue(store.storePendingFederation(pending(state = "X", sessionId = "S"), 1.minutes).isOk)
            assertTrue(store.storePendingFederation(pending(state = "Y", sessionId = "S"), 10.minutes).isOk)
            assertTrue(store.storePendingFederation(pending(state = "Z", sessionId = "S"), 10.minutes).isOk)

            val completed =
                store.completePendingFederation(
                    state = "Y",
                    userId = "u-y",
                    claims = claims(userId = "u-y"),
                    claimsTtl = 5.minutes,
                    evidence = evidence("Y"),
                )
            assertTrue(completed.isOk)

            clock.advance(2.minutes)

            val found = store.findCompletedPendingBySession("S")
            assertTrue(found.isOk)
            val value = found.value
            assertNotNull(value)
            assertEquals("Y", value.state)
            assertEquals("u-y", value.userId)
            assertTrue(value.completed)
        }

    @Test
    fun findCompletedPendingBySessionReturnsNullWhenNoCompletion() =
        runTest {
            val store = InMemoryFederationSessionStore(FakeClock())
            assertTrue(store.storePendingFederation(pending(state = "s-nc", sessionId = "sess-nc"), 5.minutes).isOk)

            val found = store.findCompletedPendingBySession("sess-nc")
            assertTrue(found.isOk)
            assertNull(found.value)
        }

    @Test
    fun removePendingReturnsTrueOnFirstCallFalseOnSecond() =
        runTest {
            val store = InMemoryFederationSessionStore(FakeClock())
            assertTrue(store.storePendingFederation(pending(state = "rm"), 5.minutes).isOk)

            val first = store.removePendingFederation("rm")
            assertTrue(first.isOk)
            assertEquals(true, first.value)

            val second = store.removePendingFederation("rm")
            assertTrue(second.isOk)
            assertEquals(false, second.value)
        }

    @Test
    fun removeCachedUserClaimsReturnsTrueOnFirstCallFalseOnSecond() =
        runTest {
            val store = InMemoryFederationSessionStore(FakeClock())
            assertTrue(store.storePendingFederation(pending(state = "s-rm"), 5.minutes).isOk)
            assertTrue(
                store
                    .completePendingFederation(
                        state = "s-rm",
                        userId = "u-rm",
                        claims = claims(userId = "u-rm"),
                        claimsTtl = 5.minutes,
                        evidence = evidence("s-rm"),
                    ).isOk,
            )

            val first = store.removeCachedUserClaims("u-rm")
            assertTrue(first.isOk)
            assertEquals(true, first.value)

            val second = store.removeCachedUserClaims("u-rm")
            assertTrue(second.isOk)
            assertEquals(false, second.value)
        }

    @Test
    fun retrieveCachedUserClaimsReturnsNullAfterTtlExpiry() =
        runTest {
            val clock = FakeClock()
            val store = InMemoryFederationSessionStore(clock)
            assertTrue(store.storePendingFederation(pending(state = "s-cc"), 10.minutes).isOk)
            assertTrue(
                store
                    .completePendingFederation(
                        state = "s-cc",
                        userId = "u-cc",
                        claims = claims(userId = "u-cc"),
                        claimsTtl = 1.minutes,
                        evidence = evidence("s-cc"),
                    ).isOk,
            )

            clock.advance(2.minutes)

            val firstRead = store.retrieveCachedUserClaims("u-cc")
            assertTrue(firstRead.isOk)
            assertNull(firstRead.value)

            // Entry was evicted on read: removeCachedUserClaims now reports false.
            val removed = store.removeCachedUserClaims("u-cc")
            assertTrue(removed.isOk)
            assertEquals(false, removed.value)
        }

    // --- Helpers ---------------------------------------------------------------------------

    @Test
    fun callbackStateCanBeConsumedExactlyOnce() =
        runTest {
            val store = InMemoryFederationSessionStore(FakeClock())
            assertTrue(store.storePendingFederation(pending("one-time"), 5.minutes).isOk)

            val first = store.consumePendingFederation("one-time")
            val replay = store.consumePendingFederation("one-time")

            assertTrue(first.isOk)
            assertNotNull(first.value)
            assertTrue(replay.isOk)
            assertNull(replay.value)
        }

    @Test
    fun pendingTransactionRejectsUpstreamResourceSubstitution() {
        val original = pending("resource-pin")

        assertFailsWith<IllegalArgumentException> {
            original.copy(upstreamAuthorizationServerId = "44444444-4444-4444-8444-444444444444")
        }
    }

    @Test
    fun pendingTransactionRejectsUpstreamRevisionSubstitution() {
        val original = pending("revision-pin")

        assertFailsWith<IllegalArgumentException> {
            original.copy(upstreamAuthorizationServerRevision = original.upstreamAuthorizationServerRevision + 1)
        }
    }

    @Test
    fun pendingTransactionRejectsIssuerSubstitution() {
        val original = pending("issuer-pin")

        assertFailsWith<IllegalArgumentException> {
            original.copy(upstreamIssuer = "https://attacker.example")
        }
    }

    private fun pending(
        state: String,
        sessionId: String = "sess-$state",
    ): PendingFederation =
        PendingFederation(
            tenantId = "tenant-1",
            hostedAuthorizationServerId = "11111111-1111-4111-8111-111111111111",
            hostedAuthorizationServerRevision = 3,
            federationBindingId = "22222222-2222-4222-8222-222222222222",
            federationBindingRevision = 5,
            upstreamAuthorizationServerId = "33333333-3333-4333-8333-333333333333",
            upstreamAuthorizationServerRevision = 7,
            upstreamIssuer = "https://idp.example",
            downstreamClientId = "downstream-client",
            authenticationRoute = route(),
            sessionId = sessionId,
            state = state,
            nonce = "nonce-$state",
            pkceData = PkceData(
                codeVerifier = "v".repeat(64),
                codeChallenge = "c".repeat(43),
            ),
            metadata = metadata(),
            returnUrl = "https://rp.example/return",
            callbackRedirectUri = "https://rp.example/callback",
            providerId = "22222222-2222-4222-8222-222222222222",
            flowContext = FlowContext(flow = "federation"),
            createdAt = Instant.fromEpochSeconds(0),
            expiresAt = Instant.fromEpochSeconds(300),
        )

    private fun route() = AuthenticationRouteDecision(
        route = AuthenticationRoute.UPSTREAM_REDIRECT,
        hostedAuthorizationServerId = "11111111-1111-4111-8111-111111111111",
        hostedAuthorizationServerRevision = 3,
        localLoginAllowed = false,
        eligibleBindings = listOf(
            AuthenticationRouteBinding(
                bindingId = "22222222-2222-4222-8222-222222222222",
                upstreamResourceId = "33333333-3333-4333-8333-333333333333",
                displayName = "Upstream",
                upstreamIssuer = "https://idp.example",
                bindingRevision = 5,
                upstreamResourceRevision = 7,
                claimsMapping = emptyMap(),
            ),
        ),
        selectedBindingId = "22222222-2222-4222-8222-222222222222",
    )

    private fun evidence(state: String) = NormalizedAuthenticationEvidence(
        hostedAuthorizationServerId = "11111111-1111-4111-8111-111111111111",
        federationBindingId = "22222222-2222-4222-8222-222222222222",
        upstreamIssuer = "https://idp.example",
        upstreamSubject = "user-$state",
        localSubject = "user-$state",
        acr = null,
        amr = emptyList(),
        authTime = Instant.fromEpochSeconds(0),
        governedClaims = emptyMap(),
        downstreamTransactionId = "sess-$state",
        upstreamTransactionId = state,
        validatedAt = Instant.fromEpochSeconds(0),
        hostedAuthorizationServerRevision = 3,
        federationBindingRevision = 5,
        upstreamResourceRevision = 7,
    )

    private fun claims(
        userId: String,
        email: String = "user@example.com",
    ): CachedUserInfo =
        CachedUserInfo(
            userId = userId,
            claims = mapOf<String, JsonElement>("email" to JsonPrimitive(email)),
            cachedAt = Instant.fromEpochSeconds(0),
        )

    private fun metadata(): AuthorizationServerMetadata =
        AuthorizationServerMetadata(
            issuer = "https://idp.example",
            tokenEndpoint = "https://idp.example/token",
            authorizationEndpoint = "https://idp.example/authorize",
        )

    private class FakeClock(
        start: Instant = Instant.fromEpochSeconds(0)
    ) : Clock {
        private var current: Instant = start

        override fun now(): Instant = current

        fun advance(by: Duration) {
            current = current + by
        }
    }
}
