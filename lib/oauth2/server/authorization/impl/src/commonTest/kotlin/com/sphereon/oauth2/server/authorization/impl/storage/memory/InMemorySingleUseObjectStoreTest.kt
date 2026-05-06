/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.oauth2.server.authorization.impl.storage.memory

import com.sphereon.core.api.IdkResult
import com.sphereon.oauth2.server.authorization.storage.SingleUseObjectNamespaces
import com.sphereon.oauth2.server.authorization.storage.SingleUseObjectStoreError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Behaviour contract for the in-memory replay-prevention store. The Postgres impl
 * (`PostgresSingleUseObjectStoreTest` in EDK) runs the same scenarios so the SPI
 * stays single-source-of-truth: drop-in interchangeable across deployments.
 */
class InMemorySingleUseObjectStoreTest {
    private val baseInstant: Instant = Instant.fromEpochSeconds(1_700_000_000)

    @Test
    fun recordIfNew_firstCall_returnsTrue() =
        runTest {
            val store = InMemorySingleUseObjectStore(MutableClock(baseInstant))
            val recorded =
                store
                    .recordIfNew(
                        namespace = SingleUseObjectNamespaces.DPOP_JTI,
                        key = "first-jti",
                        expiresAt = baseInstant + 60.seconds,
                    ).expectOk()
            assertTrue(recorded)
        }

    @Test
    fun recordIfNew_secondCallWithinTtl_returnsFalse() =
        runTest {
            val store = InMemorySingleUseObjectStore(MutableClock(baseInstant))
            store.recordIfNew(SingleUseObjectNamespaces.DPOP_JTI, "x", baseInstant + 60.seconds).expectOk()

            val replay =
                store
                    .recordIfNew(
                        SingleUseObjectNamespaces.DPOP_JTI,
                        "x",
                        baseInstant + 60.seconds,
                    ).expectOk()
            assertFalse(replay, "second record within TTL must be rejected as replay")
        }

    @Test
    fun recordIfNew_afterExpiry_returnsTrueAgain() =
        runTest {
            val clock = MutableClock(baseInstant)
            val store = InMemorySingleUseObjectStore(clock)
            store.recordIfNew(SingleUseObjectNamespaces.DPOP_JTI, "x", baseInstant + 30.seconds).expectOk()

            clock.set(baseInstant + 60.seconds)
            val reaccept =
                store
                    .recordIfNew(
                        SingleUseObjectNamespaces.DPOP_JTI,
                        "x",
                        baseInstant + 120.seconds,
                    ).expectOk()
            assertTrue(reaccept, "expired entry must allow re-record")
        }

    @Test
    fun namespaces_areIndependent() =
        runTest {
            val store = InMemorySingleUseObjectStore(MutableClock(baseInstant))
            val dpop = store.recordIfNew(SingleUseObjectNamespaces.DPOP_JTI, "shared", baseInstant + 60.seconds).expectOk()
            val nonce = store.recordIfNew(SingleUseObjectNamespaces.OIDC_NONCE, "shared", baseInstant + 60.seconds).expectOk()
            assertTrue(dpop)
            assertTrue(nonce, "namespacing must keep keyspaces independent")
        }

    @Test
    fun isRecorded_filtersOnExpiry() =
        runTest {
            val clock = MutableClock(baseInstant)
            val store = InMemorySingleUseObjectStore(clock)
            store.recordIfNew(SingleUseObjectNamespaces.DPOP_JTI, "q", baseInstant + 60.seconds).expectOk()

            assertTrue(store.isRecorded(SingleUseObjectNamespaces.DPOP_JTI, "q").expectOk())

            clock.set(baseInstant + 120.seconds)
            assertFalse(
                store.isRecorded(SingleUseObjectNamespaces.DPOP_JTI, "q").expectOk(),
                "expired entry must not be reported as recorded even before pruning runs",
            )
        }

    @Test
    fun isRecorded_returnsFalseForUnknownKey() =
        runTest {
            val store = InMemorySingleUseObjectStore(MutableClock(baseInstant))
            assertFalse(store.isRecorded(SingleUseObjectNamespaces.DPOP_JTI, "absent").expectOk())
        }

    @Test
    fun prune_dropsExpiredOnly() =
        runTest {
            val store = InMemorySingleUseObjectStore(MutableClock(baseInstant))
            store.recordIfNew(SingleUseObjectNamespaces.DPOP_JTI, "doomed-1", baseInstant + 30.seconds).expectOk()
            store.recordIfNew(SingleUseObjectNamespaces.DPOP_JTI, "doomed-2", baseInstant + 30.seconds).expectOk()
            store.recordIfNew(SingleUseObjectNamespaces.DPOP_JTI, "survivor", baseInstant + 600.seconds).expectOk()

            val pruned = store.prune(baseInstant + 60.seconds).expectOk()
            assertEquals(2, pruned)
            assertTrue(store.isRecorded(SingleUseObjectNamespaces.DPOP_JTI, "survivor").expectOk())
        }

    @Test
    fun clear_removesEverything() =
        runTest {
            val store = InMemorySingleUseObjectStore(MutableClock(baseInstant))
            store.recordIfNew(SingleUseObjectNamespaces.DPOP_JTI, "x", baseInstant + 60.seconds).expectOk()
            store.recordIfNew(SingleUseObjectNamespaces.OIDC_NONCE, "y", baseInstant + 60.seconds).expectOk()

            store.clear().expectOk()
            assertFalse(store.isRecorded(SingleUseObjectNamespaces.DPOP_JTI, "x").expectOk())
            assertFalse(store.isRecorded(SingleUseObjectNamespaces.OIDC_NONCE, "y").expectOk())
        }

    private fun <V> IdkResult<V, SingleUseObjectStoreError>.expectOk(): V {
        check(isOk) { "expected Ok, got $error" }
        return value
    }
}

/** Test-only [Clock] whose "now" can be advanced explicitly. */
@OptIn(kotlin.time.ExperimentalTime::class)
private class MutableClock(
    private var now: Instant
) : Clock {
    override fun now(): Instant = now

    fun set(instant: Instant) {
        now = instant
    }
}
