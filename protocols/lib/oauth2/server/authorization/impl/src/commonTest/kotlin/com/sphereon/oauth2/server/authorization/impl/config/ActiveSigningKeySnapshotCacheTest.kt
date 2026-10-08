/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemorySigningKeyStore
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ActiveSigningKeySnapshotCacheTest {
    @Test
    fun reusesImmutableSnapshotUntilTenantRevisionChanges() =
        runTest {
            val clock = MutableClock(T0)
            val cache = ActiveSigningKeySnapshotCache(clock)
            val first = signingKey("acme", "acme-1", createdAt = T0)
            val second = signingKey("acme", "acme-2", createdAt = T0 + 1.seconds, notBefore = T0)
            var loads = 0
            var authoritative = listOf(first)
            var revision = 1L

            assertEquals("acme-1", cache.resolve("acme", { revision }) { loads += 1; authoritative }?.kid)
            assertEquals("acme-1", cache.resolve("acme", { revision }) { loads += 1; authoritative }?.kid)
            assertEquals(1, loads, "a new request/session must reuse the AppScope snapshot")

            authoritative = listOf(second)
            revision += 1

            assertEquals("acme-2", cache.resolve("acme", { revision }) { loads += 1; authoritative }?.kid)
            assertEquals(2, loads)
        }

    @Test
    fun separatesTenantKeyAndRevisionNamespaces() =
        runTest {
            val cache = ActiveSigningKeySnapshotCache(MutableClock(T0))
            val acmeOne = signingKey("acme", "acme-1", createdAt = T0)
            val acmeTwo = signingKey("acme", "acme-2", createdAt = T0 + 1.seconds, notBefore = T0)
            val betaOne = signingKey("beta", "beta-1", createdAt = T0)
            var acmeLoads = 0
            var betaLoads = 0
            var acmeAuthoritative = listOf(acmeOne)
            var acmeRevision = 1L
            val betaRevision = 7L

            assertEquals("acme-1", cache.resolve("acme", { acmeRevision }) { acmeLoads += 1; acmeAuthoritative }?.kid)
            assertEquals("beta-1", cache.resolve("beta", { betaRevision }) { betaLoads += 1; listOf(betaOne) }?.kid)

            acmeAuthoritative = listOf(acmeTwo)
            acmeRevision += 1

            assertEquals("acme-2", cache.resolve("acme", { acmeRevision }) { acmeLoads += 1; acmeAuthoritative }?.kid)
            assertEquals("beta-1", cache.resolve("beta", { betaRevision }) { betaLoads += 1; error("beta must remain cached") }?.kid)
            assertEquals(2, acmeLoads)
            assertEquals(1, betaLoads)
        }

    @Test
    fun rejectsCrossTenantDescriptorsEvenAtMatchingRevision() =
        runTest {
            val cache = ActiveSigningKeySnapshotCache(MutableClock(T0))
            val beta = signingKey("beta", "beta-key", createdAt = T0)
            var loads = 0

            assertNull(cache.resolve("acme", { 4L }) { loads += 1; listOf(beta) })
            assertNull(cache.resolve("acme", { 4L }) { loads += 1; listOf(beta) })

            assertEquals(2, loads, "a foreign-tenant descriptor must be rejected and never cached")
        }

    @Test
    fun storeRotationAndDisableExplicitlyInvalidateSelection() =
        runTest {
            val clock = MutableClock(T0)
            val cache = ActiveSigningKeySnapshotCache(clock)
            val store: SigningKeyStore = InMemorySigningKeyStore(clock)
            val first = signingKey("acme", "acme-1", priority = 1, createdAt = T0)
            val second = signingKey("acme", "acme-2", priority = 2, createdAt = T0 + 1.seconds, notBefore = T0)

            assertTrue(store.register(first).isOk)
            assertEquals("acme-1", resolve(cache, store, "acme")?.kid)
            val afterRegister = store.contentRevision("acme").value

            assertTrue(store.rotate(second).isOk)
            assertEquals(afterRegister + 1, store.contentRevision("acme").value)
            assertEquals("acme-2", resolve(cache, store, "acme")?.kid)
            val publishable = store.listPublishable("acme")
            assertTrue(publishable.isOk)
            assertEquals(
                mapOf("acme-1" to OAuth2SigningKeyState.LEGACY, "acme-2" to OAuth2SigningKeyState.ACTIVE),
                publishable.value.associate { it.kid to it.state },
            )

            assertTrue(store.setState("acme", "acme-2", OAuth2SigningKeyState.DISABLED).isOk)
            assertNull(resolve(cache, store, "acme"), "a disabled former ACTIVE key must never be reused")
        }

    @Test
    fun futureNotBeforeIsAnExactSemanticInvalidationBoundary() =
        runTest {
            val clock = MutableClock(T0)
            val cache = ActiveSigningKeySnapshotCache(clock)
            val current = signingKey("acme", "current", priority = 1, createdAt = T0)
            val scheduled =
                signingKey(
                    tenantId = "acme",
                    kid = "scheduled",
                    priority = 2,
                    createdAt = T0 + 30.seconds,
                    notBefore = T0 + 60.seconds,
                )
            var loads = 0

            assertEquals("current", cache.resolve("acme", { 1L }) { loads += 1; listOf(current, scheduled) }?.kid)
            clock.current = T0 + 59.seconds
            assertEquals("current", cache.resolve("acme", { 1L }) { loads += 1; listOf(current, scheduled) }?.kid)
            assertEquals(1, loads)

            clock.current = T0 + 60.seconds
            assertEquals("scheduled", cache.resolve("acme", { 1L }) { loads += 1; listOf(current, scheduled) }?.kid)
            assertEquals(2, loads, "the snapshot must reload exactly when the scheduled key becomes eligible")
        }

    @Test
    fun absenceAndLoaderFailureAreNeverCached() =
        runTest {
            val cache = ActiveSigningKeySnapshotCache(MutableClock(T0))
            var emptyLoads = 0
            assertNull(cache.resolve("empty", { 0L }) { emptyLoads += 1; emptyList() })
            assertNull(cache.resolve("empty", { 0L }) { emptyLoads += 1; emptyList() })
            assertEquals(2, emptyLoads, "an unprovisioned tenant must be checked again and remain fail closed")

            var failingLoads = 0
            val failure =
                runCatching {
                    cache.resolve("failing", { 0L }) {
                        failingLoads += 1
                        error("authoritative store unavailable")
                    }
                }
            assertTrue(failure.isFailure)
            assertIs<IllegalStateException>(failure.exceptionOrNull())

            val recovered = signingKey("failing", "recovered", createdAt = T0)
            assertEquals("recovered", cache.resolve("failing", { 0L }) { failingLoads += 1; listOf(recovered) }?.kid)
            assertEquals(2, failingLoads, "a failed load must not poison the next authoritative attempt")
        }

    @Test
    fun revisionChangeDuringLoadRejectsStaleSnapshotAndReloads() =
        runTest {
            val cache = ActiveSigningKeySnapshotCache(MutableClock(T0))
            val old = signingKey("acme", "old", createdAt = T0)
            val rotated = signingKey("acme", "rotated", createdAt = T0 + 1.seconds, notBefore = T0)
            var revision = 1L
            var loads = 0

            val resolved =
                cache.resolve("acme", { revision }) {
                    loads += 1
                    if (loads == 1) {
                        revision = 2L
                        listOf(old)
                    } else {
                        listOf(rotated)
                    }
                }

            assertEquals("rotated", resolved?.kid)
            assertEquals(2, loads, "a descriptor loaded across a revision change must never be accepted")
        }

    @Test
    fun revisionReadFailureNeverFallsBackToCachedDescriptor() =
        runTest {
            val cache = ActiveSigningKeySnapshotCache(MutableClock(T0))
            val key = signingKey("acme", "active", createdAt = T0)
            assertEquals("active", cache.resolve("acme", { 1L }) { listOf(key) }?.kid)

            val failed = runCatching { cache.resolve("acme", { error("revision store unavailable") }) { error("unused") } }

            assertTrue(failed.isFailure)
            assertIs<IllegalStateException>(failed.exceptionOrNull())
        }

    private suspend fun resolve(
        cache: ActiveSigningKeySnapshotCache,
        store: SigningKeyStore,
        tenantId: String,
    ): OAuth2SigningKey? =
        cache.resolve(
            tenantId = tenantId,
            readRevision = {
                val revision = store.contentRevision(tenantId)
                check(revision.isOk) { "SigningKeyStore.contentRevision failed" }
                revision.value
            },
        ) {
            val all = store.listAll(tenantId)
            check(all.isOk) { "SigningKeyStore.listAll failed" }
            all.value
        }

    private fun signingKey(
        tenantId: String,
        kid: String,
        state: OAuth2SigningKeyState = OAuth2SigningKeyState.ACTIVE,
        priority: Int = 1,
        createdAt: Instant,
        notBefore: Instant = createdAt,
    ): OAuth2SigningKey =
        OAuth2SigningKey(
            tenantId = tenantId,
            keyInfo =
                KeyInfo<KeyType>(
                    kid = kid,
                    alias = "oauth2.$tenantId.$kid",
                    providerId = "provider-$tenantId",
                    signatureAlgorithm = SignatureAlgorithm.RSA_SHA256,
                ),
            state = state,
            priority = priority,
            createdAt = createdAt,
            notBefore = notBefore,
        )

    private class MutableClock(
        var current: Instant,
    ) : Clock {
        override fun now(): Instant = current
    }

    private companion object {
        val T0: Instant = Instant.parse("2026-08-11T10:00:00Z")
    }
}
