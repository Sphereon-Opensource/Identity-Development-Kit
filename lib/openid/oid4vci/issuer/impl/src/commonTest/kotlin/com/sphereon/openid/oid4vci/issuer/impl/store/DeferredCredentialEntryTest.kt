/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.issuer.impl.store

import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialEntry
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStatus
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Serialization and model tests for [DeferredCredentialEntry].
 *
 * Covers single credentialResponse, batch credentialResponses,
 * notificationId preservation, and status transitions.
 */
class DeferredCredentialEntryTest {
    private val json = Oid4vciJson.lenient
    private val jsonNoDefaults = Oid4vciJson.lenientNoDefaults
    private val instanceId = "issuer-instance-deferred-entry-serialization"

    private val now = 1711900800L // fixed epoch seconds for deterministic tests
    private val expiresAt = now + 3600

    // ========================================================================
    // Entry with single credentialResponse serializes correctly
    // ========================================================================

    @Test
    fun entryWithSingleCredentialResponseRoundTrip() {
        val entry =
            DeferredCredentialEntry(
                transactionId = "txn-single-001",
                issuanceSessionId = "session-abc",
                instanceId = instanceId,
                credentialConfigurationId = "IdentityCredential",
                status = DeferredCredentialStatus.READY,
                credentialResponse = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.credential.sig"),
                createdAt = now,
                expiresAt = expiresAt,
            )

        val encoded = json.encodeToString(DeferredCredentialEntry.serializer(), entry)
        val decoded = json.decodeFromString(DeferredCredentialEntry.serializer(), encoded)

        assertEquals(entry, decoded)
        assertEquals("txn-single-001", decoded.transactionId)
        assertEquals("session-abc", decoded.issuanceSessionId)
        assertEquals(instanceId, decoded.instanceId)
        assertEquals("IdentityCredential", decoded.credentialConfigurationId)
        assertEquals(DeferredCredentialStatus.READY, decoded.status)
        assertNotNull(decoded.credentialResponse)
        assertEquals("eyJhbGciOiJFUzI1NiJ9.credential.sig", decoded.credentialResponse!!.jsonPrimitive.content)
        assertNull(decoded.credentialResponses, "single-credential entry must not have credentialResponses")
        assertNull(decoded.notificationId)
    }

    // ========================================================================
    // Entry with batch credentialResponses serializes correctly
    // ========================================================================

    @Test
    fun entryWithBatchCredentialResponsesRoundTrip() {
        val entry =
            DeferredCredentialEntry(
                transactionId = "txn-batch-001",
                issuanceSessionId = "session-def",
                instanceId = instanceId,
                credentialConfigurationId = "BatchCredential",
                status = DeferredCredentialStatus.READY,
                credentialResponses =
                    listOf(
                        JsonPrimitive("eyJ.cred1.sig1"),
                        JsonPrimitive("eyJ.cred2.sig2"),
                        JsonPrimitive("eyJ.cred3.sig3"),
                    ),
                createdAt = now,
                expiresAt = expiresAt,
            )

        val encoded = json.encodeToString(DeferredCredentialEntry.serializer(), entry)
        val decoded = json.decodeFromString(DeferredCredentialEntry.serializer(), encoded)

        assertEquals(entry, decoded)
        assertNull(decoded.credentialResponse, "batch entry must not have singular credentialResponse")
        assertNotNull(decoded.credentialResponses)
        assertEquals(3, decoded.credentialResponses!!.size)
        assertEquals("eyJ.cred1.sig1", decoded.credentialResponses!![0].jsonPrimitive.content)
        assertEquals("eyJ.cred2.sig2", decoded.credentialResponses!![1].jsonPrimitive.content)
        assertEquals("eyJ.cred3.sig3", decoded.credentialResponses!![2].jsonPrimitive.content)
    }

    // ========================================================================
    // Entry with notificationId preserved
    // ========================================================================

    @Test
    fun entryWithNotificationIdPreserved() {
        val entry =
            DeferredCredentialEntry(
                transactionId = "txn-notif-001",
                issuanceSessionId = "session-ghi",
                instanceId = instanceId,
                credentialConfigurationId = "IdentityCredential",
                status = DeferredCredentialStatus.READY,
                credentialResponse = JsonPrimitive("eyJ.cred.sig"),
                notificationId = "notif-abc-123",
                createdAt = now,
                expiresAt = expiresAt,
            )

        val encoded = json.encodeToString(DeferredCredentialEntry.serializer(), entry)
        val decoded = json.decodeFromString(DeferredCredentialEntry.serializer(), encoded)

        assertEquals("notif-abc-123", decoded.notificationId)
        assertEquals(entry, decoded)
    }

    // ========================================================================
    // Status transitions (PENDING -> READY -> DELIVERED)
    // ========================================================================

    @Test
    fun statusTransitionPendingToReady() {
        val pending =
            DeferredCredentialEntry(
                transactionId = "txn-transition-001",
                issuanceSessionId = "session-jkl",
                instanceId = instanceId,
                credentialConfigurationId = "IdentityCredential",
                status = DeferredCredentialStatus.PENDING,
                createdAt = now,
                expiresAt = expiresAt,
            )

        assertEquals(DeferredCredentialStatus.PENDING, pending.status)
        assertNull(pending.credentialResponse, "PENDING entry should have no credential")

        val ready =
            pending.copy(
                status = DeferredCredentialStatus.READY,
                credentialResponse = JsonPrimitive("eyJ.ready-cred.sig"),
            )

        assertEquals(DeferredCredentialStatus.READY, ready.status)
        assertNotNull(ready.credentialResponse)

        // Verify both serialize independently
        val pendingEncoded = json.encodeToString(DeferredCredentialEntry.serializer(), pending)
        val readyEncoded = json.encodeToString(DeferredCredentialEntry.serializer(), ready)

        val pendingDecoded = json.decodeFromString(DeferredCredentialEntry.serializer(), pendingEncoded)
        val readyDecoded = json.decodeFromString(DeferredCredentialEntry.serializer(), readyEncoded)

        assertEquals(DeferredCredentialStatus.PENDING, pendingDecoded.status)
        assertEquals(DeferredCredentialStatus.READY, readyDecoded.status)
    }

    @Test
    fun statusTransitionReadyToDelivered() {
        val ready =
            DeferredCredentialEntry(
                transactionId = "txn-transition-002",
                issuanceSessionId = "session-mno",
                instanceId = instanceId,
                credentialConfigurationId = "IdentityCredential",
                status = DeferredCredentialStatus.READY,
                credentialResponse = JsonPrimitive("eyJ.cred.sig"),
                notificationId = "notif-delivered",
                createdAt = now,
                expiresAt = expiresAt,
            )

        val delivered = ready.copy(status = DeferredCredentialStatus.DELIVERED)

        assertEquals(DeferredCredentialStatus.DELIVERED, delivered.status)
        // Credential and notification should be preserved through transition
        assertNotNull(delivered.credentialResponse)
        assertEquals("notif-delivered", delivered.notificationId)
        assertEquals(ready.transactionId, delivered.transactionId)

        // Round-trip the delivered entry
        val encoded = json.encodeToString(DeferredCredentialEntry.serializer(), delivered)
        val decoded = json.decodeFromString(DeferredCredentialEntry.serializer(), encoded)

        assertEquals(delivered, decoded)
    }

    @Test
    fun statusTransitionToFailed() {
        val pending =
            DeferredCredentialEntry(
                transactionId = "txn-failed-001",
                issuanceSessionId = "session-pqr",
                instanceId = instanceId,
                credentialConfigurationId = "IdentityCredential",
                status = DeferredCredentialStatus.PENDING,
                createdAt = now,
                expiresAt = expiresAt,
            )

        val failed = pending.copy(status = DeferredCredentialStatus.FAILED)

        assertEquals(DeferredCredentialStatus.FAILED, failed.status)
        assertNull(failed.credentialResponse)

        val encoded = json.encodeToString(DeferredCredentialEntry.serializer(), failed)
        val decoded = json.decodeFromString(DeferredCredentialEntry.serializer(), encoded)

        assertEquals(DeferredCredentialStatus.FAILED, decoded.status)
    }

    // ========================================================================
    // Default retryAfterSeconds
    // ========================================================================

    @Test
    fun defaultRetryAfterSeconds() {
        val entry =
            DeferredCredentialEntry(
                transactionId = "txn-defaults",
                issuanceSessionId = "session-stu",
                instanceId = instanceId,
                credentialConfigurationId = "IdentityCredential",
                status = DeferredCredentialStatus.PENDING,
                createdAt = now,
                expiresAt = expiresAt,
            )

        assertEquals(5, entry.retryAfterSeconds, "default retryAfterSeconds should be 5")
    }
}
