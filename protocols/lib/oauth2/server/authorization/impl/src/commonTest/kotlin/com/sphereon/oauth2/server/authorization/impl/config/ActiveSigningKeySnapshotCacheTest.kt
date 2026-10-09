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
    fun resolveAllReturnsEveryEligibleDescriptorInPriorityOrderForExactTenant() =
        runTest {
            val cache = ActiveSigningKeySnapshotCache(MutableClock(T0))
            val keys = listOf(
                signingKey("acme", "lower", priority = 1, createdAt = T0),
                signingKey("acme", "newer-equal", priority = 2, createdAt = T0 + 2.seconds, notBefore = T0),
                signingKey("acme", "older-equal", priority = 2, createdAt = T0 + 1.seconds, notBefore = T0),
                signingKey("beta", "foreign-higher", priority = 99, createdAt = T0),
                signingKey("acme", "disabled", state = OAuth2SigningKeyState.DISABLED, priority = 99, createdAt = T0),
                signingKey("acme", "scheduled", priority = 99, createdAt = T0, notBefore = T0 + 60.seconds),
            )

            val resolved = cache.resolveAll("acme", { 1L }) { keys }

            assertEquals(listOf("newer-equal", "older-equal", "lower"), resolved.map { it.kid })
        }

    @Test
    fun resolveAllCachesBothAlgorithmDescriptorsFromOneAcceptedLoad() =
        runTest {
            val cache = ActiveSigningKeySnapshotCache(MutableClock(T0))
            val rsa = signingKey("acme", "rsa", createdAt = T0, algorithm = SignatureAlgorithm.RSA_SHA256)
            val ec = signingKey("acme", "ec", createdAt = T0 + 1.seconds, notBefore = T0, algorithm = SignatureAlgorithm.ECDSA_SHA256)
            var loads = 0

            val first = cache.resolveAll("acme", { 7L }) { loads += 1; listOf(rsa, ec) }
            val second = cache.resolveAll("acme", { 7L }) { loads += 1; error("accepted collection must be cached") }

            assertEquals(listOf("ec", "rsa"), first.map { it.kid })
            assertEquals(listOf("ec", "rsa"), second.map { it.kid })
            assertEquals(1, loads)
        }

    @Test
    fun resolveAllRetriesWholeCollectionWhenRevisionChangesDuringLoad() =
        runTest {
            val cache = ActiveSigningKeySnapshotCache(MutableClock(T0))
            var revision = 1L
            var loads = 0
            val old = signingKey("acme", "old", createdAt = T0)
            val current = signingKey("acme", "current", priority = 2, createdAt = T0)
            val alternate = signingKey("acme", "alternate", priority = 1, createdAt = T0)

            val resolved = cache.resolveAll("acme", { revision }) {
                loads += 1
                if (loads == 1) {
                    revision = 2L
                    listOf(old)
                } else {
                    listOf(alternate, current)
                }
            }

            assertEquals(listOf("current", "alternate"), resolved.map { it.kid })
            assertEquals(2, loads, "a rotation during load must reject the entire old collection")
        }

    @Test
    fun resolveAllAddsLowerPriorityKeyAtItsScheduledActivation() =
        runTest {
            val clock = MutableClock(T0)
            val cache = ActiveSigningKeySnapshotCache(clock)
            val current = signingKey("acme", "current", priority = 9, createdAt = T0)
            val scheduled = signingKey("acme", "scheduled", priority = 1, createdAt = T0, notBefore = T0 + 60.seconds)
            var loads = 0
            val load = suspend { loads += 1; listOf(current, scheduled) }

            assertEquals(listOf("current"), cache.resolveAll("acme", { 1L }, load).map { it.kid })
            clock.current = T0 + 59.seconds
            assertEquals(listOf("current"), cache.resolveAll("acme", { 1L }, load).map { it.kid })
            assertEquals(1, loads)

            clock.current = T0 + 60.seconds
            assertEquals(listOf("current", "scheduled"), cache.resolveAll("acme", { 1L }, load).map { it.kid })
            assertEquals(2, loads, "even a lower-priority activation changes the accepted collection")
        }

    @Test
    fun resolveAllRemovesNonselectedDescriptorAfterDisableRevision() =
        runTest {
            val cache = ActiveSigningKeySnapshotCache(MutableClock(T0))
            val preferred = signingKey("acme", "preferred", priority = 9, createdAt = T0)
            val alternate = signingKey("acme", "alternate", priority = 1, createdAt = T0)
            var revision = 1L
            var authoritative = listOf(preferred, alternate)
            var loads = 0
            val load = suspend { loads += 1; authoritative }

            assertEquals(listOf("preferred", "alternate"), cache.resolveAll("acme", { revision }, load).map { it.kid })
            authoritative = listOf(preferred, alternate.copy(state = OAuth2SigningKeyState.DISABLED))
            revision += 1

            assertEquals(listOf("preferred"), cache.resolveAll("acme", { revision }, load).map { it.kid })
            assertEquals(2, loads)
        }

    @Test
    fun resolveAllDoesNotRetainCallerMutableLoaderList() =
        runTest {
            val cache = ActiveSigningKeySnapshotCache(MutableClock(T0))
            val supplied = mutableListOf(
                signingKey("acme", "preferred", priority = 2, createdAt = T0),
                signingKey("acme", "alternate", priority = 1, createdAt = T0),
            )
            var loads = 0
            val first = cache.resolveAll("acme", { 1L }) { loads += 1; supplied }
            supplied.clear()

            val reused = cache.resolveAll("acme", { 1L }) { loads += 1; error("cached collection must not use cleared loader list") }

            assertEquals(listOf("preferred", "alternate"), first.map { it.kid })
            assertEquals(listOf("preferred", "alternate"), reused.map { it.kid })
            assertEquals(1, loads)
        }

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

            assertEquals("acme-1", cache.resolveAll("acme", { revision }) { loads += 1; authoritative }.firstOrNull()?.kid)
            assertEquals("acme-1", cache.resolveAll("acme", { revision }) { loads += 1; authoritative }.firstOrNull()?.kid)
            assertEquals(1, loads, "a new request/session must reuse the AppScope snapshot")

            authoritative = listOf(second)
            revision += 1

            assertEquals("acme-2", cache.resolveAll("acme", { revision }) { loads += 1; authoritative }.firstOrNull()?.kid)
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

            assertEquals("acme-1", cache.resolveAll("acme", { acmeRevision }) { acmeLoads += 1; acmeAuthoritative }.firstOrNull()?.kid)
            assertEquals("beta-1", cache.resolveAll("beta", { betaRevision }) { betaLoads += 1; listOf(betaOne) }.firstOrNull()?.kid)

            acmeAuthoritative = listOf(acmeTwo)
            acmeRevision += 1

            assertEquals("acme-2", cache.resolveAll("acme", { acmeRevision }) { acmeLoads += 1; acmeAuthoritative }.firstOrNull()?.kid)
            assertEquals("beta-1", cache.resolveAll("beta", { betaRevision }) { betaLoads += 1; error("beta must remain cached") }.firstOrNull()?.kid)
            assertEquals(2, acmeLoads)
            assertEquals(1, betaLoads)
        }

    @Test
    fun rejectsCrossTenantDescriptorsEvenAtMatchingRevision() =
        runTest {
            val cache = ActiveSigningKeySnapshotCache(MutableClock(T0))
            val beta = signingKey("beta", "beta-key", createdAt = T0)
            var loads = 0

            assertTrue(cache.resolveAll("acme", { 4L }) { loads += 1; listOf(beta) }.isEmpty())
            assertTrue(cache.resolveAll("acme", { 4L }) { loads += 1; listOf(beta) }.isEmpty())

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

            assertEquals("current", cache.resolveAll("acme", { 1L }) { loads += 1; listOf(current, scheduled) }.firstOrNull()?.kid)
            clock.current = T0 + 59.seconds
            assertEquals("current", cache.resolveAll("acme", { 1L }) { loads += 1; listOf(current, scheduled) }.firstOrNull()?.kid)
            assertEquals(1, loads)

            clock.current = T0 + 60.seconds
            assertEquals("scheduled", cache.resolveAll("acme", { 1L }) { loads += 1; listOf(current, scheduled) }.firstOrNull()?.kid)
            assertEquals(2, loads, "the snapshot must reload exactly when the scheduled key becomes eligible")
        }

    @Test
    fun absenceAndLoaderFailureAreNeverCached() =
        runTest {
            val cache = ActiveSigningKeySnapshotCache(MutableClock(T0))
            var emptyLoads = 0
            assertTrue(cache.resolveAll("empty", { 0L }) { emptyLoads += 1; emptyList() }.isEmpty())
            assertTrue(cache.resolveAll("empty", { 0L }) { emptyLoads += 1; emptyList() }.isEmpty())
            assertEquals(2, emptyLoads, "an unprovisioned tenant must be checked again and remain fail closed")

            var failingLoads = 0
            val failure =
                runCatching {
                    cache.resolveAll("failing", { 0L }) {
                        failingLoads += 1
                        error("authoritative store unavailable")
                    }
                }
            assertTrue(failure.isFailure)
            assertIs<IllegalStateException>(failure.exceptionOrNull())

            val recovered = signingKey("failing", "recovered", createdAt = T0)
            assertEquals("recovered", cache.resolveAll("failing", { 0L }) { failingLoads += 1; listOf(recovered) }.firstOrNull()?.kid)
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
                cache.resolveAll("acme", { revision }) {
                    loads += 1
                    if (loads == 1) {
                        revision = 2L
                        listOf(old)
                    } else {
                        listOf(rotated)
                    }
                }

            assertEquals("rotated", resolved.firstOrNull()?.kid)
            assertEquals(2, loads, "a descriptor loaded across a revision change must never be accepted")
        }

    @Test
    fun revisionReadFailureNeverFallsBackToCachedDescriptor() =
        runTest {
            val cache = ActiveSigningKeySnapshotCache(MutableClock(T0))
            val key = signingKey("acme", "active", createdAt = T0)
            assertEquals("active", cache.resolveAll("acme", { 1L }) { listOf(key) }.firstOrNull()?.kid)

            val failed = runCatching { cache.resolveAll("acme", { error("revision store unavailable") }) { error("unused") } }

            assertTrue(failed.isFailure)
            assertIs<IllegalStateException>(failed.exceptionOrNull())
        }

    private suspend fun resolve(
        cache: ActiveSigningKeySnapshotCache,
        store: SigningKeyStore,
        tenantId: String,
    ): OAuth2SigningKey? =
        cache.resolveAll(
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
        }.firstOrNull()

    private fun signingKey(
        tenantId: String,
        kid: String,
        state: OAuth2SigningKeyState = OAuth2SigningKeyState.ACTIVE,
        priority: Int = 1,
        createdAt: Instant,
        notBefore: Instant = createdAt,
        algorithm: SignatureAlgorithm = SignatureAlgorithm.RSA_SHA256,
    ): OAuth2SigningKey =
        OAuth2SigningKey(
            tenantId = tenantId,
            keyInfo =
                KeyInfo<KeyType>(
                    kid = kid,
                    alias = "oauth2.$tenantId.$kid",
                    providerId = "provider-$tenantId",
                    signatureAlgorithm = algorithm,
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
