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
 * Unit tests for [RequestCredentialWithFlowArgs], [CredentialFlowResult], and
 * [RequestCredentialWithFlowCommand] contract.
 *
 * Tests cover:
 * - Args construction and defaults
 * - Nonce retry error code extraction contract
 * - All three [CredentialFlowResult] subtypes
 * - Sealed class exhaustiveness
 */
class RequestCredentialWithFlowTest {
    // ============================================================================
    // Args defaults
    // ============================================================================

    @Test
    fun argsSigningAlgorithmDefaultsToEs256() {
        val args =
            RequestCredentialWithFlowArgs(
                sessionId = "sess-001",
                credentialEndpoint = "https://issuer.example.com/credential",
                accessToken = "tok",
                issuerUrl = "https://issuer.example.com",
                signingKeyId = "key-1",
            )

        assertEquals("ES256", args.signingAlgorithm)
    }

    @Test
    fun argsOptionalFieldsDefaultToNull() {
        val args =
            RequestCredentialWithFlowArgs(
                sessionId = "sess-001",
                credentialEndpoint = "https://issuer.example.com/credential",
                accessToken = "tok",
                issuerUrl = "https://issuer.example.com",
                signingKeyId = "key-1",
            )

        assertNull(args.credentialConfigurationId)
        assertNull(args.credentialIdentifier)
        assertNull(args.nonceEndpoint)
        assertNull(args.deferredCredentialEndpoint)
        assertNull(args.notificationEndpoint)
        assertNull(args.credentialResponseEncryption)
        assertNull(args.decryptionKey)
    }

    @Test
    fun argsWithAllOptionalEndpoints() {
        val args =
            RequestCredentialWithFlowArgs(
                sessionId = "sess-002",
                credentialEndpoint = "https://issuer.example.com/credential",
                accessToken = "Bearer eyJ.tok.sig",
                issuerUrl = "https://issuer.example.com",
                signingKeyId = "key-wallet-1",
                signingAlgorithm = "EdDSA",
                credentialConfigurationId = "UniversityDegreeCredential",
                nonceEndpoint = "https://issuer.example.com/nonce",
                deferredCredentialEndpoint = "https://issuer.example.com/deferred",
                notificationEndpoint = "https://issuer.example.com/notification",
            )

        assertEquals("sess-002", args.sessionId)
        assertEquals("EdDSA", args.signingAlgorithm)
        assertEquals("UniversityDegreeCredential", args.credentialConfigurationId)
        assertNull(args.credentialIdentifier)
        assertEquals("https://issuer.example.com/nonce", args.nonceEndpoint)
        assertEquals("https://issuer.example.com/deferred", args.deferredCredentialEndpoint)
        assertEquals("https://issuer.example.com/notification", args.notificationEndpoint)
    }

    @Test
    fun argsWithCredentialIdentifierInsteadOfConfigurationId() {
        val args =
            RequestCredentialWithFlowArgs(
                sessionId = "sess-003",
                credentialEndpoint = "https://issuer.example.com/credential",
                accessToken = "tok",
                issuerUrl = "https://issuer.example.com",
                signingKeyId = "key-1",
                credentialIdentifier = "CivilEngineeringDegree-2024",
            )

        assertNull(args.credentialConfigurationId)
        assertEquals("CivilEngineeringDegree-2024", args.credentialIdentifier)
    }

    // ============================================================================
    // Nonce retry — error code extraction contract
    // ============================================================================

    @Test
    fun invalidNonceFreshNonceAvailableCodeContainsNonce() {
        // The RequestCredentialCommandImpl embeds the fresh nonce as:
        // INVALID_NONCE_FRESH_NONCE_AVAILABLE:<nonce>
        val errorCode = "INVALID_NONCE_FRESH_NONCE_AVAILABLE:tZignsnFbp"
        val prefix = "INVALID_NONCE_FRESH_NONCE_AVAILABLE:"

        assertTrue(errorCode.startsWith(prefix))
        val extracted = errorCode.removePrefix(prefix)
        assertEquals("tZignsnFbp", extracted)
    }

    @Test
    fun invalidNonceCodeWithEmptyNonceResultsInBlank() {
        val errorCode = "INVALID_NONCE_FRESH_NONCE_AVAILABLE:"
        val prefix = "INVALID_NONCE_FRESH_NONCE_AVAILABLE:"
        val extracted = errorCode.removePrefix(prefix)
        assertTrue(extracted.isBlank())
    }

    @Test
    fun unrelatedErrorCodeDoesNotMatchNoncePrefix() {
        val errorCode = "CREDENTIAL_REQUEST_FAILED"
        val prefix = "INVALID_NONCE_FRESH_NONCE_AVAILABLE:"
        assertTrue(!errorCode.startsWith(prefix))
    }

    // ============================================================================
    // CredentialFlowResult — Immediate
    // ============================================================================

    @Test
    fun immediateResultHasCredentialAndNotificationSent() {
        val credential =
            CredentialResponse(
                credential = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.payload.sig"),
                notificationId = "notif-001",
            )

        val result =
            CredentialFlowResult.Immediate(
                credential = credential,
                notificationSent = true,
            )

        assertIs<CredentialFlowResult.Immediate>(result)
        assertEquals("notif-001", result.credential.notificationId)
        assertTrue(result.notificationSent)
    }

    @Test
    fun immediateResultNotificationSentCanBeFalse() {
        val credential = CredentialResponse(credential = JsonPrimitive("eyJ.x.y"))

        val result = CredentialFlowResult.Immediate(credential = credential, notificationSent = false)

        assertTrue(!result.notificationSent)
    }

    // ============================================================================
    // CredentialFlowResult — DeferredCompleted
    // ============================================================================

    @Test
    fun deferredCompletedResultHasAllFields() {
        val credential =
            CredentialResponse(
                credential = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.deferred.sig"),
                notificationId = "notif-deferred",
            )

        val result =
            CredentialFlowResult.DeferredCompleted(
                credential = credential,
                pollAttempts = 7,
                notificationSent = true,
            )

        assertIs<CredentialFlowResult.DeferredCompleted>(result)
        assertEquals(7, result.pollAttempts)
        assertTrue(result.notificationSent)
        assertEquals("notif-deferred", result.credential.notificationId)
    }

    // ============================================================================
    // CredentialFlowResult — DeferredExhausted
    // ============================================================================

    @Test
    fun deferredExhaustedResultPreservesAllState() {
        val result =
            CredentialFlowResult.DeferredExhausted(
                transactionId = "txn-server-final",
                attemptsMade = 60,
                lastInterval = 10,
            )

        assertIs<CredentialFlowResult.DeferredExhausted>(result)
        assertEquals("txn-server-final", result.transactionId)
        assertEquals(60, result.attemptsMade)
        assertEquals(10, result.lastInterval)
    }

    // ============================================================================
    // Sealed class exhaustiveness
    // ============================================================================

    @Test
    fun whenExpressionCoversAllSubtypes() {
        val credential = CredentialResponse(credential = JsonPrimitive("eyJ.x.y"))
        val results: List<CredentialFlowResult> =
            listOf(
                CredentialFlowResult.Immediate(credential, false),
                CredentialFlowResult.DeferredCompleted(credential, 3, true),
                CredentialFlowResult.DeferredExhausted("txn", 60, 5),
            )

        var immediateCount = 0
        var deferredCompletedCount = 0
        var deferredExhaustedCount = 0

        for (result in results) {
            when (result) {
                is CredentialFlowResult.Immediate -> immediateCount++
                is CredentialFlowResult.DeferredCompleted -> deferredCompletedCount++
                is CredentialFlowResult.DeferredExhausted -> deferredExhaustedCount++
            }
        }

        assertEquals(1, immediateCount)
        assertEquals(1, deferredCompletedCount)
        assertEquals(1, deferredExhaustedCount)
    }

    // ============================================================================
    // Command ID
    // ============================================================================

    @Test
    fun commandIdIsCorrect() {
        assertEquals("oid4vci.holder.flow.credentialflow", RequestCredentialWithFlowCommand.COMMAND_ID)
    }
}
