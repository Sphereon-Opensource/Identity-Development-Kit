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

package com.sphereon.oauth2.server.authorization.impl.storage.memory

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStoreError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Pins the [InMemorySigningKeyStore] contract that the OAuth2 AS sign / JWKS / rotate
 * paths depend on. The tests are SPI-contract tests, not implementation-detail tests:
 * the same suite should pass against the EDK Postgres impl with the in-memory store
 * swapped out.
 *
 * Critical invariants under test:
 *  - `getActive` returns the highest-priority ACTIVE key whose `notBefore` is in the past.
 *  - `listPublishable` returns ACTIVE+LEGACY priority-descending so JWKS publishes the
 *    current signer first.
 *  - `register` rejects duplicate kid within the same tenant (RFC 6749 §10.4 reuse-defense:
 *    a kid MUST never be reused for a fresh key).
 *  - `rotate` is atomic per algorithm: insert + demote-previous-active happens in one critical
 *    section without disabling active signers for other algorithms.
 *  - `setState` transitions LEGACY → DISABLED (and back-stops); idempotent on no-op.
 *  - The `init` block on [OAuth2SigningKey] enforces that `keyInfo.kid` and
 *    `keyInfo.signatureAlgorithm` are non-null so downstream sign paths can read them
 *    without nullability noise.
 */
class InMemorySigningKeyStoreTest {
    private val tenantA = "tenant-a"
    private val tenantB = "tenant-b"

    // Anchor an hour in the past so every `notBefore = baseInstant + N.milliseconds`
    // computed below stays in the past relative to the test's wall-clock `now()`.
    // Otherwise the InMemorySigningKeyStore.getActive filter `notBefore <= now` would
    // reject keys whose notBefore happens to land microseconds after the test runs.
    private val baseInstant: Instant = Clock.System.now() - 1.hours

    private fun newStore(): InMemorySigningKeyStore = InMemorySigningKeyStore(clock = Clock.System)

    private fun signingKey(
        kid: String,
        tenantId: String = tenantA,
        algorithm: SignatureAlgorithm = SignatureAlgorithm.RSA_SHA256,
        state: OAuth2SigningKeyState = OAuth2SigningKeyState.ACTIVE,
        priority: Int = 1,
        createdAt: Instant = baseInstant,
        notBefore: Instant = createdAt,
        alias: String = "oauth2.$tenantId.$kid",
        providerId: String = "software",
    ): OAuth2SigningKey =
        OAuth2SigningKey(
            tenantId = tenantId,
            keyInfo =
                KeyInfo<KeyType>(
                    kid = kid,
                    alias = alias,
                    providerId = providerId,
                    signatureAlgorithm = algorithm,
                ),
            state = state,
            priority = priority,
            createdAt = createdAt,
            notBefore = notBefore,
        )

    @Test
    fun signingKeyConstructorRejectsBlankKid() {
        // The wire-visible kid is the load-bearing identifier: JWS headers, JWKS entries,
        // and the SigningKeyStore's primary key all reference it. Construction with a
        // blank or null kid would let a downstream caller read `key.kid` and silently
        // produce a token whose JWKS lookup fails. The init block must catch this.
        assertFails {
            OAuth2SigningKey(
                tenantId = tenantA,
                keyInfo =
                    KeyInfo<KeyType>(
                        kid = "",
                        alias = "alias",
                        providerId = "software",
                        signatureAlgorithm = SignatureAlgorithm.RSA_SHA256,
                    ),
                state = OAuth2SigningKeyState.ACTIVE,
                priority = 1,
                createdAt = baseInstant,
                notBefore = baseInstant,
            )
        }
    }

    @Test
    fun signingKeyConstructorRejectsMissingAlgorithm() {
        // Without `signatureAlgorithm`, the JWS sign path cannot fill the `alg` header
        // and the JWKS endpoint cannot publish the `alg` parameter. Catching at the
        // store entry point moves the failure from per-request to per-deploy.
        assertFails {
            OAuth2SigningKey(
                tenantId = tenantA,
                keyInfo =
                    KeyInfo<KeyType>(
                        kid = "kid-1",
                        alias = "alias",
                        providerId = "software",
                        signatureAlgorithm = null,
                    ),
                state = OAuth2SigningKeyState.ACTIVE,
                priority = 1,
                createdAt = baseInstant,
                notBefore = baseInstant,
            )
        }
    }

    @Test
    fun getActiveReturnsNullWhenStoreEmpty() =
        runTest {
            val result = newStore().getActive(tenantA)
            assertTrue(result.isOk)
            assertNull(result.value)
        }

    @Test
    fun getActiveReturnsTheOnlyActiveKey() =
        runTest {
            val store = newStore()
            store.register(signingKey(kid = "kid-1"))
            val result = store.getActive(tenantA)
            assertTrue(result.isOk)
            assertEquals("kid-1", result.value?.kid)
        }

    @Test
    fun getActivePicksHighestPriorityWithCreatedAtTiebreak() =
        runTest {
            // Three ACTIVE keys: two share the highest priority, the newer must win the tie
            // so a freshly-rotated key takes precedence over an equal-priority predecessor
            // that was somehow promoted simultaneously.
            val store = newStore()
            store.register(signingKey(kid = "low-priority", priority = 1, createdAt = baseInstant))
            store.register(signingKey(kid = "old-high-priority", priority = 5, createdAt = baseInstant))
            store.register(signingKey(kid = "new-high-priority", priority = 5, createdAt = baseInstant + 10.milliseconds))
            val result = store.getActive(tenantA)
            assertTrue(result.isOk)
            assertEquals("new-high-priority", result.value?.kid, "newest of the same-priority ACTIVE keys must win")
        }

    @Test
    fun getActiveSkipsKeysWithFutureNotBefore() =
        runTest {
            // Pre-staged keys with a future notBefore are eligible to be ACTIVE in the
            // store (operator pinned them ahead of cutover) but the sign path must skip
            // them until their notBefore has elapsed. This is the rotate-by-schedule path.
            val now = Clock.System.now()
            val store = InMemorySigningKeyStore(clock = TestClock(now))
            store.register(signingKey(kid = "current", priority = 1, createdAt = now, notBefore = now))
            store.register(signingKey(kid = "future", priority = 10, createdAt = now, notBefore = now + 60_000.milliseconds))
            val result = store.getActive(tenantA)
            assertTrue(result.isOk)
            assertEquals("current", result.value?.kid, "future-notBefore keys must NOT win, even at higher priority")
        }

    @Test
    fun getActiveIgnoresLegacyAndDisabledKeys() =
        runTest {
            val store = newStore()
            store.register(signingKey(kid = "legacy", state = OAuth2SigningKeyState.LEGACY, priority = 100))
            store.register(signingKey(kid = "disabled", state = OAuth2SigningKeyState.DISABLED, priority = 100))
            store.register(signingKey(kid = "active", state = OAuth2SigningKeyState.ACTIVE, priority = 1))
            val result = store.getActive(tenantA)
            assertTrue(result.isOk)
            assertEquals("active", result.value?.kid, "only ACTIVE-state keys may sign new tokens")
        }

    @Test
    fun listPublishableExcludesDisabledAndOrdersByPriorityDescending() =
        runTest {
            val store = newStore()
            store.register(signingKey(kid = "active-low", state = OAuth2SigningKeyState.ACTIVE, priority = 1))
            store.register(signingKey(kid = "legacy-mid", state = OAuth2SigningKeyState.LEGACY, priority = 5))
            store.register(signingKey(kid = "active-high", state = OAuth2SigningKeyState.ACTIVE, priority = 10))
            store.register(signingKey(kid = "disabled-x", state = OAuth2SigningKeyState.DISABLED, priority = 100))
            val result = store.listPublishable(tenantA)
            assertTrue(result.isOk)
            val kids = result.value.map { it.kid }
            assertEquals(listOf("active-high", "legacy-mid", "active-low"), kids, "JWKS publication order is priority-descending; DISABLED excluded")
        }

    @Test
    fun listAllReturnsEverythingIncludingDisabled() =
        runTest {
            // Operator views (admin console, audit reports) want to see every entry,
            // including DISABLED tombstones, so they can pick a kid to prune past
            // retention. Sign / verify paths use getActive / listPublishable instead.
            val store = newStore()
            store.register(signingKey(kid = "active", state = OAuth2SigningKeyState.ACTIVE))
            store.register(signingKey(kid = "legacy", state = OAuth2SigningKeyState.LEGACY))
            store.register(signingKey(kid = "disabled", state = OAuth2SigningKeyState.DISABLED))
            val result = store.listAll(tenantA)
            assertTrue(result.isOk)
            assertEquals(setOf("active", "legacy", "disabled"), result.value.map { it.kid }.toSet())
        }

    @Test
    fun rotateDemotesOnlyActiveKeysUsingTheSameAlgorithm() =
        runTest {
            val store = newStore()
            store.register(signingKey(kid = "rsa-old", algorithm = SignatureAlgorithm.RSA_SHA256))
            store.register(signingKey(kid = "ec-active", algorithm = SignatureAlgorithm.ECDSA_SHA256))

            val rotation = store.rotate(signingKey(kid = "rsa-new", algorithm = SignatureAlgorithm.RSA_SHA256))

            assertTrue(rotation.isOk)
            assertEquals(listOf("rsa-old"), rotation.value.demotedToLegacy.map { it.kid })
            val all = store.listAll(tenantA).value.associateBy { it.kid }
            assertEquals(OAuth2SigningKeyState.LEGACY, all.getValue("rsa-old").state)
            assertEquals(OAuth2SigningKeyState.ACTIVE, all.getValue("rsa-new").state)
            assertEquals(OAuth2SigningKeyState.ACTIVE, all.getValue("ec-active").state)
        }

    @Test
    fun findByKidReturnsTheRightEntry() =
        runTest {
            val store = newStore()
            store.register(signingKey(kid = "kid-A"))
            store.register(signingKey(kid = "kid-B"))
            val a = store.findByKid(tenantA, "kid-A")
            val b = store.findByKid(tenantA, "kid-B")
            val absent = store.findByKid(tenantA, "kid-NOPE")
            assertTrue(a.isOk && a.value?.kid == "kid-A")
            assertTrue(b.isOk && b.value?.kid == "kid-B")
            assertTrue(absent.isOk && absent.value == null)
        }

    @Test
    fun registerRejectsDuplicateKidInSameTenant() =
        runTest {
            // Reusing a kid would let an attacker who captured an old token replay it
            // against a fresh signer with the same id. The store enforces uniqueness
            // including against DISABLED tombstones (i.e. once a kid is used, ever, it
            // cannot be reused for the same tenant).
            val store = newStore()
            assertTrue(store.register(signingKey(kid = "shared")).isOk)
            val second = store.register(signingKey(kid = "shared"))
            assertTrue(second.isErr)
            val error = second.error
            assertTrue(error is SigningKeyStoreError.DuplicateKid)
            assertEquals(tenantA, error.tenantId)
            assertEquals("shared", error.kid)
        }

    @Test
    fun registerAllowsSameKidAcrossDifferentTenants() =
        runTest {
            // Tenancy partitions the kid namespace: two tenants may legitimately have a
            // key with the same kid (operator convenience, backup-restore from a per-
            // tenant export, etc.). The DuplicateKid error fires only within a tenant.
            val store = newStore()
            assertTrue(store.register(signingKey(kid = "cross-tenant", tenantId = tenantA)).isOk)
            assertTrue(store.register(signingKey(kid = "cross-tenant", tenantId = tenantB)).isOk)
            assertEquals("cross-tenant", store.getActive(tenantA).value?.kid)
            assertEquals("cross-tenant", store.getActive(tenantB).value?.kid)
        }

    @Test
    fun rotateInsertsNewActiveAndDemotesPreviousActiveToLegacy() =
        runTest {
            val store = newStore()
            store.register(signingKey(kid = "old", priority = 5))
            val rotateResult = store.rotate(signingKey(kid = "new", priority = 6))
            assertTrue(rotateResult.isOk)
            val rotation = rotateResult.value
            assertEquals("new", rotation.newActive.kid)
            assertEquals(OAuth2SigningKeyState.ACTIVE, rotation.newActive.state)
            assertEquals(listOf("old"), rotation.demotedToLegacy.map { it.kid })
            assertEquals(OAuth2SigningKeyState.LEGACY, rotation.demotedToLegacy.single().state)
            // Subsequent reads see the new active and the demoted legacy together.
            val publishable = store.listPublishable(tenantA).value
            assertEquals(setOf("new", "old"), publishable.map { it.kid }.toSet())
            val active = store.getActive(tenantA).value
            assertEquals("new", active?.kid)
        }

    @Test
    fun rotateLeavesLegacyAndDisabledKeysUntouched() =
        runTest {
            // Rotate's atomicity guarantee says it demotes ALL currently-ACTIVE keys; LEGACY
            // and DISABLED entries from prior rotations stay in their lifecycle state. This
            // matters because a pre-existing LEGACY key still needs to verify in-flight
            // tokens — bumping it to DISABLED on rotation would invalidate them.
            val store = newStore()
            store.register(signingKey(kid = "ancient", state = OAuth2SigningKeyState.DISABLED, priority = 0))
            store.register(signingKey(kid = "previous", state = OAuth2SigningKeyState.LEGACY, priority = 4))
            store.register(signingKey(kid = "current", state = OAuth2SigningKeyState.ACTIVE, priority = 5))
            store.rotate(signingKey(kid = "fresh", priority = 6))
            val all = store.listAll(tenantA).value.associateBy { it.kid }
            assertEquals(OAuth2SigningKeyState.DISABLED, all.getValue("ancient").state)
            assertEquals(OAuth2SigningKeyState.LEGACY, all.getValue("previous").state)
            assertEquals(OAuth2SigningKeyState.LEGACY, all.getValue("current").state, "rotate must demote the previously-ACTIVE key only")
            assertEquals(OAuth2SigningKeyState.ACTIVE, all.getValue("fresh").state)
        }

    @Test
    fun rotateRejectsDuplicateKid() =
        runTest {
            // Rotation with a kid that already exists (in any state) is a programming error
            // by the caller; surfacing as DuplicateKid rather than silently overwriting
            // protects against accidentally clobbering a LEGACY key whose verification
            // duty is not yet over.
            val store = newStore()
            store.register(signingKey(kid = "repeat", state = OAuth2SigningKeyState.LEGACY))
            val result = store.rotate(signingKey(kid = "repeat"))
            assertTrue(result.isErr)
            assertTrue(result.error is SigningKeyStoreError.DuplicateKid)
        }

    @Test
    fun setStateTransitionsLegacyToDisabled() =
        runTest {
            val store = newStore()
            store.register(signingKey(kid = "to-disable", state = OAuth2SigningKeyState.LEGACY))
            val first = store.setState(tenantA, "to-disable", OAuth2SigningKeyState.DISABLED)
            assertTrue(first.isOk && first.value)
            assertEquals(OAuth2SigningKeyState.DISABLED, store.findByKid(tenantA, "to-disable").value?.state)
            // Idempotent on no-op transition: returning false signals the caller
            // (typically the cleanup job) that nothing changed without raising an error.
            val second = store.setState(tenantA, "to-disable", OAuth2SigningKeyState.DISABLED)
            assertTrue(second.isOk && !second.value)
        }

    @Test
    fun setStateOnUnknownKidReturnsFalse() =
        runTest {
            // Per the SPI doc on [SigningKeyStore.setState], a missing tenant returns false
            // (not error) so the cleanup job can safely operate on an empty tenant without
            // alarming.
            val store = newStore()
            val result = store.setState(tenantA, "ghost", OAuth2SigningKeyState.DISABLED)
            assertTrue(result.isOk)
            assertEquals(false, result.value)
        }

    /**
     * Test clock that returns a fixed instant, used by the future-notBefore test so that
     * `clock.now()` is deterministic relative to the test's `notBefore` arithmetic.
     */
    private class TestClock(
        private val fixed: Instant,
    ) : Clock {
        override fun now(): Instant = fixed
    }
}
