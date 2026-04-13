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

package com.sphereon.openid.oid4vci.holder

import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [PollDeferredCredentialArgs], [PollDeferredCredentialResult], and
 * [PollDeferredCredentialCommand] contract.
 *
 * These are pure data-model and contract tests — no HTTP or coroutine mocking required.
 */
class PollDeferredCredentialTest {
    // ============================================================================
    // Args defaults
    // ============================================================================

    @Test
    fun argsIntervalDefaultsToNull() {
        val args =
            PollDeferredCredentialArgs(
                deferredCredentialEndpoint = "https://issuer.example.com/deferred",
                accessToken = "access-token-xyz",
                transactionId = "txn-001",
                sessionId = "session-001",
            )

        assertNull(args.interval)
    }

    @Test
    fun argsMaxAttemptsDefaultsToNull() {
        val args =
            PollDeferredCredentialArgs(
                deferredCredentialEndpoint = "https://issuer.example.com/deferred",
                accessToken = "access-token-xyz",
                transactionId = "txn-001",
                sessionId = "session-001",
            )

        assertNull(args.maxAttempts)
    }

    @Test
    fun argsWithExplicitIntervalAndMaxAttempts() {
        val args =
            PollDeferredCredentialArgs(
                deferredCredentialEndpoint = "https://issuer.example.com/deferred",
                accessToken = "access-token-xyz",
                transactionId = "txn-001",
                sessionId = "session-001",
                interval = 10,
                maxAttempts = 5,
            )

        assertEquals(10, args.interval)
        assertEquals(5, args.maxAttempts)
    }

    @Test
    fun argsEncryptionAndDecryptionKeyDefaultToNull() {
        val args =
            PollDeferredCredentialArgs(
                deferredCredentialEndpoint = "https://issuer.example.com/deferred",
                accessToken = "access-token-xyz",
                transactionId = "txn-001",
                sessionId = "session-001",
            )

        assertNull(args.credentialResponseEncryption)
        assertNull(args.decryptionKey)
    }

    // ============================================================================
    // PollDeferredCredentialResult — Ready
    // ============================================================================

    @Test
    fun readyResultContainsCredentialAndPollAttempts() {
        val credential =
            CredentialResponse(
                credential = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.payload.sig"),
                notificationId = "notif-001",
            )

        val result =
            PollDeferredCredentialResult.Ready(
                credential = credential,
                pollAttempts = 3,
            )

        assertIs<PollDeferredCredentialResult.Ready>(result)
        assertEquals(3, result.pollAttempts)
        assertEquals("notif-001", result.credential.notificationId)
    }

    @Test
    fun readyResultPollAttemptsReflectsActualCount() {
        val credential =
            CredentialResponse(
                credential = JsonPrimitive("eyJ.cred.sig"),
            )

        val result = PollDeferredCredentialResult.Ready(credential = credential, pollAttempts = 1)

        assertEquals(1, result.pollAttempts)
    }

    // ============================================================================
    // PollDeferredCredentialResult — Exhausted
    // ============================================================================

    @Test
    fun exhaustedResultIsValidOkNotError() {
        // Exhausted must be a data class (not an exception), validating that the
        // design treats it as a regular result rather than an error path.
        val result =
            PollDeferredCredentialResult.Exhausted(
                transactionId = "txn-exhausted",
                attemptsMade = 60,
                lastInterval = 5,
            )

        assertIs<PollDeferredCredentialResult.Exhausted>(result)
        assertEquals("txn-exhausted", result.transactionId)
        assertEquals(60, result.attemptsMade)
        assertEquals(5, result.lastInterval)
    }

    @Test
    fun exhaustedResultPreservesUpdatedTransactionId() {
        // Server may update transaction_id on each poll — the final ID must be preserved
        val result =
            PollDeferredCredentialResult.Exhausted(
                transactionId = "txn-server-updated-456",
                attemptsMade = 10,
                lastInterval = 15,
            )

        assertEquals("txn-server-updated-456", result.transactionId)
        assertEquals(15, result.lastInterval)
    }

    @Test
    fun exhaustedResultIntervalReflectsServerUpdate() {
        // Server may shorten or lengthen the interval mid-polling
        val result =
            PollDeferredCredentialResult.Exhausted(
                transactionId = "txn-001",
                attemptsMade = 20,
                lastInterval = 30,
            )

        assertEquals(30, result.lastInterval)
    }

    // ============================================================================
    // Command ID
    // ============================================================================

    @Test
    fun commandIdIsCorrect() {
        assertEquals("oid4vci.holder.flow.polldeferred", PollDeferredCredentialCommand.COMMAND_ID)
    }

    // ============================================================================
    // Sealed class exhaustiveness — both subtypes present
    // ============================================================================

    @Test
    fun sealedSubtypesAreDistinct() {
        val credential = CredentialResponse(credential = JsonPrimitive("eyJ.x.y"))
        val ready: PollDeferredCredentialResult = PollDeferredCredentialResult.Ready(credential, 2)
        val exhausted: PollDeferredCredentialResult = PollDeferredCredentialResult.Exhausted("txn", 2, 5)

        assertTrue(ready is PollDeferredCredentialResult.Ready)
        assertTrue(exhausted is PollDeferredCredentialResult.Exhausted)
    }

    @Test
    fun whenExpressionCoversAllSubtypes() {
        val credential = CredentialResponse(credential = JsonPrimitive("eyJ.x.y"))
        val results: List<PollDeferredCredentialResult> =
            listOf(
                PollDeferredCredentialResult.Ready(credential, 1),
                PollDeferredCredentialResult.Exhausted("txn", 1, 5),
            )

        var readyCount = 0
        var exhaustedCount = 0
        for (result in results) {
            when (result) {
                is PollDeferredCredentialResult.Ready -> readyCount++
                is PollDeferredCredentialResult.Exhausted -> exhaustedCount++
            }
        }

        assertEquals(1, readyCount)
        assertEquals(1, exhaustedCount)
    }
}
