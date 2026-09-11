/*
 * © 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.openid.oid4vci.issuer.attribute.CredentialAttributeContribution
import com.sphereon.openid.oid4vci.issuer.attribute.CredentialAttributeContributionWaiter
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CredentialContributionSyncWaitTest {
    @Test
    fun `waits for every pending source within one total window`() = runTest {
        val awaited = mutableSetOf<String>()
        val waiter = CredentialAttributeContributionWaiter { correlationId, contributorId ->
            assertEquals("correlation-1", correlationId)
            awaited += contributorId
        }

        val completed =
            awaitPendingCredentialContributions(
                waiter = waiter,
                correlationId = "correlation-1",
                contribution =
                    CredentialAttributeContribution(
                        attributes = emptyMap(),
                        pendingAsyncCallbackSources = setOf("source-b", "source-a"),
                        syncWaitWindow = 1.seconds,
                    ),
            )

        assertTrue(completed)
        assertEquals(setOf("source-a", "source-b"), awaited)
    }

    @Test
    fun `times out when any pending source does not arrive`() = runTest {
        val waiter = CredentialAttributeContributionWaiter { _, contributorId ->
            if (contributorId == "never-arrives") awaitCancellation()
        }

        val completed =
            awaitPendingCredentialContributions(
                waiter = waiter,
                correlationId = "correlation-1",
                contribution =
                    CredentialAttributeContribution(
                        attributes = emptyMap(),
                        pendingAsyncCallbackSources = setOf("arrives", "never-arrives"),
                        syncWaitWindow = 100.milliseconds,
                    ),
            )

        assertFalse(completed)
    }

    @Test
    fun `does not wait without every precondition`() = runTest {
        var calls = 0
        val waiter = CredentialAttributeContributionWaiter { _, _ -> calls++ }

        assertFalse(
            awaitPendingCredentialContributions(
                waiter = null,
                correlationId = "correlation-1",
                contribution = pendingContribution(),
            ),
        )
        assertFalse(
            awaitPendingCredentialContributions(
                waiter = waiter,
                correlationId = null,
                contribution = pendingContribution(),
            ),
        )
        assertFalse(
            awaitPendingCredentialContributions(
                waiter = waiter,
                correlationId = "correlation-1",
                contribution = CredentialAttributeContribution(attributes = emptyMap()),
            ),
        )
        assertFalse(
            awaitPendingCredentialContributions(
                waiter = waiter,
                correlationId = "correlation-1",
                contribution = pendingContribution().copy(syncWaitWindow = kotlin.time.Duration.ZERO),
            ),
        )
        assertEquals(0, calls)
    }

    private fun pendingContribution() =
        CredentialAttributeContribution(
            attributes = emptyMap(),
            pendingAsyncCallbackSources = setOf("source-a"),
            syncWaitWindow = 1.seconds,
        )
}
