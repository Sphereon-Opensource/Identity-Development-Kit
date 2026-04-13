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

package com.sphereon.openid.oid4vci.common.impl.serialization

import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.CredentialResponseItem
import com.sphereon.openid.oid4vci.common.model.DeferredCredentialRequest
import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Serialization tests for deferred credential request/response wire formats.
 *
 * Covers DeferredCredentialRequest round-trip, CredentialResponse in 1.0 and 1.1
 * formats, deferred pending responses, and notification ID preservation.
 */
class DeferredSerializationTest {
    private val json = Oid4vciJson.lenient
    private val jsonNoDefaults = Oid4vciJson.lenientNoDefaults

    // ========================================================================
    // DeferredCredentialRequest round-trip with proof and encryption
    // ========================================================================

    @Test
    fun deferredRequestRoundTripWithProofsAndEncryption() {
        val jwk = JsonObject(mapOf("kty" to JsonPrimitive("EC"), "crv" to JsonPrimitive("P-256")))
        val original =
            DeferredCredentialRequest(
                transactionId = "txn-deferred-001",
                proofs = CredentialRequestProofs(proofType = "jwt", proofValues = listOf(JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.proof.sig"))),
                credentialResponseEncryption =
                    RequestedCredentialResponseEncryption(
                        jwk = jwk,
                        alg = "ECDH-ES+A256KW",
                        enc = "A256GCM",
                    ),
            )

        val encoded = json.encodeToString(DeferredCredentialRequest.serializer(), original)
        val decoded = json.decodeFromString(DeferredCredentialRequest.serializer(), encoded)

        assertEquals(original, decoded)
        assertEquals("txn-deferred-001", decoded.transactionId)
        assertNotNull(decoded.proofs)
        assertEquals("jwt", decoded.proofs!!.proofType)
        assertEquals(1, decoded.proofs!!.proofValues.size)
        assertNotNull(decoded.credentialResponseEncryption)
        assertEquals("A256GCM", decoded.credentialResponseEncryption!!.enc)
        assertEquals("ECDH-ES+A256KW", decoded.credentialResponseEncryption!!.alg)

        // Verify wire format keys
        val wireObj = json.parseToJsonElement(encoded).jsonObject
        assertTrue(wireObj.containsKey("transaction_id"), "must use snake_case 'transaction_id'")
        assertTrue(wireObj.containsKey("credential_response_encryption"), "must have 'credential_response_encryption'")
        assertTrue(wireObj.containsKey("proofs"), "must have 'proofs'")
    }

    @Test
    fun deferredRequestMinimalRoundTrip() {
        val original = DeferredCredentialRequest(transactionId = "txn-simple")

        val encoded = jsonNoDefaults.encodeToString(DeferredCredentialRequest.serializer(), original)
        val decoded = json.decodeFromString(DeferredCredentialRequest.serializer(), encoded)

        assertEquals("txn-simple", decoded.transactionId)
        assertNull(decoded.proofs)
        assertNull(decoded.credentialResponseEncryption)

        // Verify wire format omits null optional fields
        val wireObj = jsonNoDefaults.parseToJsonElement(encoded).jsonObject
        assertTrue(wireObj.containsKey("transaction_id"), "must have required field")
        assertFalse(wireObj.containsKey("proofs"), "null proofs must not appear")
        assertFalse(wireObj.containsKey("credential_response_encryption"), "null encryption must not appear")
    }

    // ========================================================================
    // CredentialResponse with credentials array (1.1 batch ready response)
    // ========================================================================

    @Test
    fun credentialResponseWithCredentialsArray() {
        val raw =
            """
            {
              "credentials": [
                {"credential": "eyJ.cred1.sig1"},
                {"credential": "eyJ.cred2.sig2"},
                {"credential": "eyJ.cred3.sig3"}
              ],
              "c_nonce": "fresh-nonce",
              "notification_id": "notif-batch-001"
            }
            """.trimIndent()

        val decoded = json.decodeFromString(CredentialResponse.serializer(), raw)

        assertNull(decoded.credential, "1.1 batch must not populate singular credential")
        assertNotNull(decoded.credentials)
        assertEquals(3, decoded.credentials!!.size)
        assertEquals("eyJ.cred1.sig1", (decoded.credentials!![0].credential as? JsonPrimitive)?.content)
        assertEquals("eyJ.cred2.sig2", (decoded.credentials!![1].credential as? JsonPrimitive)?.content)
        assertEquals("eyJ.cred3.sig3", (decoded.credentials!![2].credential as? JsonPrimitive)?.content)
        assertEquals("fresh-nonce", decoded.cNonce)
        assertEquals("notif-batch-001", decoded.notificationId)

        // Round-trip
        val reEncoded = json.encodeToString(CredentialResponse.serializer(), decoded)
        val reDecoded = json.decodeFromString(CredentialResponse.serializer(), reEncoded)
        assertEquals(decoded, reDecoded)
    }

    // ========================================================================
    // CredentialResponse with single credential (1.0 ready response)
    // ========================================================================

    @Test
    fun credentialResponseWithSingleCredential() {
        val raw =
            """
            {
              "credential": "eyJhbGciOiJFUzI1NiJ9.single-credential.sig",
              "c_nonce": "single-nonce",
              "c_nonce_expires_in": 3600,
              "notification_id": "notif-single-001"
            }
            """.trimIndent()

        val decoded = json.decodeFromString(CredentialResponse.serializer(), raw)

        assertNotNull(decoded.credential)
        assertEquals(
            "eyJhbGciOiJFUzI1NiJ9.single-credential.sig",
            (decoded.credential as? JsonPrimitive)?.content,
        )
        assertNull(decoded.credentials, "1.0 single must not have credentials array")
        assertNull(decoded.transactionId)
        assertEquals("single-nonce", decoded.cNonce)
        assertEquals(3600, decoded.cNonceExpiresIn)
        assertEquals("notif-single-001", decoded.notificationId)

        // Round-trip
        val reEncoded = json.encodeToString(CredentialResponse.serializer(), decoded)
        val reDecoded = json.decodeFromString(CredentialResponse.serializer(), reEncoded)
        assertEquals(decoded, reDecoded)
    }

    // ========================================================================
    // Deferred pending response: has transactionId but no credential(s)
    // ========================================================================

    @Test
    fun deferredPendingResponseHasTransactionIdOnly() {
        val raw =
            """
            {
              "transaction_id": "txn-pending-xyz",
              "interval": 10
            }
            """.trimIndent()

        val decoded = json.decodeFromString(CredentialResponse.serializer(), raw)

        assertNull(decoded.credential, "deferred pending must not have credential")
        assertNull(decoded.credentials, "deferred pending must not have credentials")
        assertEquals("txn-pending-xyz", decoded.transactionId)
        assertEquals(10, decoded.interval)
        assertNull(decoded.notificationId)
        assertNull(decoded.cNonce)

        // Round-trip
        val reEncoded = json.encodeToString(CredentialResponse.serializer(), decoded)
        val reDecoded = json.decodeFromString(CredentialResponse.serializer(), reEncoded)
        assertEquals(decoded, reDecoded)

        // Wire format verification
        val wireObj = json.parseToJsonElement(reEncoded).jsonObject
        assertTrue(wireObj.containsKey("transaction_id"), "must have 'transaction_id'")
        assertFalse(wireObj.containsKey("credential"), "pending must not serialize 'credential'")
        assertFalse(wireObj.containsKey("credentials"), "pending must not serialize 'credentials'")
    }

    // ========================================================================
    // Response with notificationId preserved
    // ========================================================================

    @Test
    fun responseWithNotificationIdPreserved() {
        val raw =
            """
            {
              "credential": "eyJ.cred.sig",
              "notification_id": "notif-preserved-123",
              "c_nonce": "abc"
            }
            """.trimIndent()

        val decoded = json.decodeFromString(CredentialResponse.serializer(), raw)

        assertEquals("notif-preserved-123", decoded.notificationId)

        // Verify it survives round-trip
        val reEncoded = json.encodeToString(CredentialResponse.serializer(), decoded)
        val reDecoded = json.decodeFromString(CredentialResponse.serializer(), reEncoded)

        assertEquals("notif-preserved-123", reDecoded.notificationId)

        // Verify wire key is snake_case
        val wireObj = json.parseToJsonElement(reEncoded).jsonObject
        assertTrue(wireObj.containsKey("notification_id"), "must use snake_case 'notification_id'")
        assertFalse(wireObj.containsKey("notificationId"), "must NOT use camelCase 'notificationId'")
        assertEquals("notif-preserved-123", wireObj["notification_id"]?.jsonPrimitive?.content)
    }

    // ========================================================================
    // Deserialization from spec-like deferred request JSON
    // ========================================================================

    @Test
    fun deserializeDeferredRequestFromSpecJson() {
        val raw =
            """
            {
              "transaction_id": "8xLOxBtZp8"
            }
            """.trimIndent()

        val decoded = json.decodeFromString(DeferredCredentialRequest.serializer(), raw)

        assertEquals("8xLOxBtZp8", decoded.transactionId)
        assertNull(decoded.proofs)
        assertNull(decoded.credentialResponseEncryption)
    }
}
