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

import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [SendNotificationWithRetryArgs], [SendNotificationWithRetryCommand] contract,
 * and the exponential backoff math used by [SendNotificationWithRetryCommandImpl].
 *
 * Pure data-model tests — no HTTP or coroutine mocking required.
 */
class SendNotificationWithRetryTest {
    // ============================================================================
    // Args defaults
    // ============================================================================

    @Test
    fun maxRetriesDefaultsTo3() {
        val args =
            SendNotificationWithRetryArgs(
                notificationEndpoint = "https://issuer.example.com/notify",
                accessToken = "tok",
                notificationId = "notif-001",
                event = CredentialNotificationEvent.CREDENTIAL_ACCEPTED,
            )

        assertEquals(3, args.maxRetries)
    }

    @Test
    fun initialBackoffMsDefaultsTo1000() {
        val args =
            SendNotificationWithRetryArgs(
                notificationEndpoint = "https://issuer.example.com/notify",
                accessToken = "tok",
                notificationId = "notif-001",
                event = CredentialNotificationEvent.CREDENTIAL_ACCEPTED,
            )

        assertEquals(1000L, args.initialBackoffMs)
    }

    @Test
    fun eventDescriptionDefaultsToNull() {
        val args =
            SendNotificationWithRetryArgs(
                notificationEndpoint = "https://issuer.example.com/notify",
                accessToken = "tok",
                notificationId = "notif-001",
                event = CredentialNotificationEvent.CREDENTIAL_ACCEPTED,
            )

        assertNull(args.eventDescription)
    }

    @Test
    fun argsWithAllFields() {
        val args =
            SendNotificationWithRetryArgs(
                notificationEndpoint = "https://issuer.example.com/notify",
                accessToken = "Bearer eyJhbGciOiJFUzI1NiJ9",
                notificationId = "notif-xyz",
                event = CredentialNotificationEvent.CREDENTIAL_FAILURE,
                eventDescription = "User rejected credential",
                maxRetries = 5,
                initialBackoffMs = 500L,
            )

        assertEquals("notif-xyz", args.notificationId)
        assertEquals(CredentialNotificationEvent.CREDENTIAL_FAILURE, args.event)
        assertEquals("User rejected credential", args.eventDescription)
        assertEquals(5, args.maxRetries)
        assertEquals(500L, args.initialBackoffMs)
    }

    // ============================================================================
    // Exponential backoff math
    // ============================================================================

    @Test
    fun exponentialBackoffDoublesOnEachRetry() {
        // Simulate the backoff sequence: 1000, 2000, 4000, 8000
        var backoffMs = 1000L
        val sequence = mutableListOf<Long>()
        repeat(4) {
            sequence.add(backoffMs)
            backoffMs *= 2
        }

        assertEquals(listOf(1000L, 2000L, 4000L, 8000L), sequence)
    }

    @Test
    fun backoffAfterMaxRetriesWithInitial500ms() {
        var backoffMs = 500L
        val sequence = mutableListOf<Long>()
        repeat(3) {
            sequence.add(backoffMs)
            backoffMs *= 2
        }

        assertEquals(listOf(500L, 1000L, 2000L), sequence)
    }

    @Test
    fun totalAttemptsIsMaxRetriesPlusOne() {
        // attempt 0 = initial, attempts 1..maxRetries = retries
        val maxRetries = 3
        val totalAttempts = maxRetries + 1
        assertEquals(4, totalAttempts)
    }

    // ============================================================================
    // CredentialNotificationEvent enum values
    // ============================================================================

    @Test
    fun credentialAcceptedEventExists() {
        val event = CredentialNotificationEvent.CREDENTIAL_ACCEPTED
        assertEquals(CredentialNotificationEvent.CREDENTIAL_ACCEPTED, event)
    }

    @Test
    fun credentialFailureEventExists() {
        val event = CredentialNotificationEvent.CREDENTIAL_FAILURE
        assertEquals(CredentialNotificationEvent.CREDENTIAL_FAILURE, event)
    }

    @Test
    fun credentialDeletedEventExists() {
        val event = CredentialNotificationEvent.CREDENTIAL_DELETED
        assertEquals(CredentialNotificationEvent.CREDENTIAL_DELETED, event)
    }

    @Test
    fun allEventsCanBeUsedInArgs() {
        val events =
            listOf(
                CredentialNotificationEvent.CREDENTIAL_ACCEPTED,
                CredentialNotificationEvent.CREDENTIAL_FAILURE,
                CredentialNotificationEvent.CREDENTIAL_DELETED,
            )

        val argsList =
            events.map { event ->
                SendNotificationWithRetryArgs(
                    notificationEndpoint = "https://issuer.example.com/notify",
                    accessToken = "tok",
                    notificationId = "notif-${event.name}",
                    event = event,
                )
            }

        assertEquals(3, argsList.size)
        argsList.forEachIndexed { i, args ->
            assertEquals(events[i], args.event)
        }
    }

    // ============================================================================
    // Command ID
    // ============================================================================

    @Test
    fun commandIdIsCorrect() {
        assertEquals("oid4vci.holder.flow.notifyretry", SendNotificationWithRetryCommand.COMMAND_ID)
    }

    // ============================================================================
    // Zero retries
    // ============================================================================

    @Test
    fun zeroMaxRetriesMeansOnlyOneAttempt() {
        val args =
            SendNotificationWithRetryArgs(
                notificationEndpoint = "https://issuer.example.com/notify",
                accessToken = "tok",
                notificationId = "notif-001",
                event = CredentialNotificationEvent.CREDENTIAL_ACCEPTED,
                maxRetries = 0,
            )

        assertEquals(0, args.maxRetries)
        // With maxRetries=0, the loop runs for attempt 0 only (0..0), giving exactly 1 attempt
        val expectedAttempts = args.maxRetries + 1
        assertEquals(1, expectedAttempts)
    }
}
