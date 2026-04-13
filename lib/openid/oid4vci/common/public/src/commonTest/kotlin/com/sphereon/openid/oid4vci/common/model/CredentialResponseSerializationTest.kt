/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.common.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialResponseSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun singleCredentialRoundTrip() {
        val response =
            CredentialResponse(
                credential = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.eyJ2Y3QiOiJodHRwczovL2NyZWRlbnRpYWxzLmV4YW1wbGUuY29tL2lkZW50aXR5X2NyZWRlbnRpYWwifQ.signature"),
            )

        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<CredentialResponse>(encoded)

        assertEquals(response, decoded)
        assertNotNull(decoded.credential)
        assertTrue(decoded.credential is JsonPrimitive)
        assertNull(decoded.credentials)
        assertNull(decoded.transactionId)
        assertNull(decoded.cNonce)
        assertNull(decoded.cNonceExpiresIn)
        assertNull(decoded.notificationId)
        assertTrue(decoded.additionalParameters.isEmpty())
    }

    @Test
    fun batchCredentialRoundTrip() {
        val response =
            CredentialResponse(
                credentials =
                    listOf(
                        CredentialResponseItem(credential = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.credential1.sig1")),
                        CredentialResponseItem(credential = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.credential2.sig2")),
                        CredentialResponseItem(credential = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.credential3.sig3")),
                    ),
                notificationId = "notification-abc123",
            )

        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<CredentialResponse>(encoded)

        assertEquals(response, decoded)
        assertNull(decoded.credential)
        assertNotNull(decoded.credentials)
        assertEquals(3, decoded.credentials?.size)
        assertEquals("notification-abc123", decoded.notificationId)
    }

    @Test
    fun deferredResponseRoundTrip() {
        val response =
            CredentialResponse(
                transactionId = "txn-8dfc3e6a-b5f1-4e7d-a3c0-9b2f1e4d6a8c",
            )

        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<CredentialResponse>(encoded)

        assertEquals(response, decoded)
        assertNull(decoded.credential)
        assertNull(decoded.credentials)
        assertEquals("txn-8dfc3e6a-b5f1-4e7d-a3c0-9b2f1e4d6a8c", decoded.transactionId)
    }

    @Test
    fun responseWithNonceRoundTrip() {
        val response =
            CredentialResponse(
                credential = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.credential.sig"),
                cNonce = "fGFF7UkhLa",
                cNonceExpiresIn = 86400,
                notificationId = "notification-xyz789",
            )

        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<CredentialResponse>(encoded)

        assertEquals(response, decoded)
        assertNotNull(decoded.credential)
        assertEquals("fGFF7UkhLa", decoded.cNonce)
        assertEquals(86400, decoded.cNonceExpiresIn)
        assertEquals("notification-xyz789", decoded.notificationId)
    }

    @Test
    fun version10ImmediateResponseWithSingleCredentialWireFormat() {
        val jsonString =
            """
            {
                "credential": "eyJhbGciOiJFUzI1NiJ9.eyJ2Y3QiOiJodHRwczovL2NyZWRlbnRpYWxzLmV4YW1wbGUuY29tL2lkZW50aXR5X2NyZWRlbnRpYWwifQ.signature",
                "c_nonce": "fGFF7UkhLa",
                "c_nonce_expires_in": 86400
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialResponse>(jsonString)

        assertNotNull(decoded.credential)
        assertTrue(decoded.credential is JsonPrimitive, "1.0 credential is a single string")
        assertEquals("fGFF7UkhLa", decoded.cNonce)
        assertEquals(86400, decoded.cNonceExpiresIn)
        assertNull(decoded.credentials, "1.0 does not use credentials array")
        assertNull(decoded.transactionId)

        // Re-serialize and verify wire format
        val reEncoded = json.encodeToString(decoded)
        val reObj = json.parseToJsonElement(reEncoded).jsonObject
        assertTrue(reObj.containsKey("credential"), "must have 'credential' key")
        assertFalse(reObj.containsKey("credentials"), "1.0 must not have 'credentials' key")
        assertEquals("fGFF7UkhLa", reObj["c_nonce"]?.jsonPrimitive?.content)
        assertEquals(86400, reObj["c_nonce_expires_in"]?.jsonPrimitive?.intOrNull)

        val reDecoded = json.decodeFromString<CredentialResponse>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun version11ImmediateResponseWithCredentialsArrayWireFormat() {
        // OID4VCI 1.1: credentials is an array of objects each with a "credential" field
        val jsonString =
            """
            {
                "credentials": [
                    { "credential": "eyJhbGciOiJFUzI1NiJ9.credential1.sig1" },
                    { "credential": "eyJhbGciOiJFUzI1NiJ9.credential2.sig2" }
                ],
                "c_nonce": "new-nonce-123",
                "c_nonce_expires_in": 3600
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialResponse>(jsonString)

        assertNull(decoded.credential, "1.1 batch does not use singular credential")
        assertNotNull(decoded.credentials)
        assertEquals(2, decoded.credentials?.size)
        assertEquals("new-nonce-123", decoded.cNonce)
        assertEquals(3600, decoded.cNonceExpiresIn)

        // Verify each item has a credential field
        decoded.credentials?.forEach { item ->
            assertTrue(item.credential is JsonPrimitive, "each item.credential should be a primitive string")
        }

        // Re-serialize and verify wire format
        val reEncoded = json.encodeToString(decoded)
        val reObj = json.parseToJsonElement(reEncoded).jsonObject
        assertFalse(reObj.containsKey("credential"), "1.1 must not have singular 'credential' key")
        assertTrue(reObj.containsKey("credentials"), "1.1 must have 'credentials' key")

        val credentialsArray = reObj["credentials"]!!.jsonArray
        assertEquals(2, credentialsArray.size)
        // Each element must be an object with a "credential" key
        credentialsArray.forEach { element ->
            val obj = element.jsonObject
            assertTrue(obj.containsKey("credential"), "each credentials array element must have 'credential' key")
        }

        val reDecoded = json.decodeFromString<CredentialResponse>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun version11DeferredResponseWithTransactionIdAndInterval() {
        val jsonString =
            """
            {
                "transaction_id": "txn-8dfc3e6a-b5f1-4e7d-a3c0-9b2f1e4d6a8c",
                "interval": 5,
                "c_nonce": "deferred-nonce",
                "c_nonce_expires_in": 600
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialResponse>(jsonString)

        assertNull(decoded.credential)
        assertNull(decoded.credentials)
        assertEquals("txn-8dfc3e6a-b5f1-4e7d-a3c0-9b2f1e4d6a8c", decoded.transactionId)
        assertEquals(5, decoded.interval)
        assertEquals("deferred-nonce", decoded.cNonce)
        assertEquals(600, decoded.cNonceExpiresIn)

        // Re-serialize and verify wire format
        val reEncoded = json.encodeToString(decoded)
        val reObj = json.parseToJsonElement(reEncoded).jsonObject
        assertEquals("txn-8dfc3e6a-b5f1-4e7d-a3c0-9b2f1e4d6a8c", reObj["transaction_id"]?.jsonPrimitive?.content)
        assertEquals(5, reObj["interval"]?.jsonPrimitive?.intOrNull)
        assertFalse(reObj.containsKey("credential"), "deferred response must not have credential")
        assertFalse(reObj.containsKey("credentials"), "deferred response must not have credentials")

        val reDecoded = json.decodeFromString<CredentialResponse>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun version10DeferredResponseWithTransactionIdNoInterval() {
        val jsonString =
            """
            {
                "transaction_id": "txn-pending-001"
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialResponse>(jsonString)

        assertNull(decoded.credential)
        assertNull(decoded.credentials)
        assertEquals("txn-pending-001", decoded.transactionId)
        assertNull(decoded.interval, "1.0 deferred may omit interval")
        assertNull(decoded.cNonce)

        // Re-serialize and verify wire format
        val reEncoded = json.encodeToString(decoded)
        val reObj = json.parseToJsonElement(reEncoded).jsonObject
        assertEquals("txn-pending-001", reObj["transaction_id"]?.jsonPrimitive?.content)
        assertFalse(reObj.containsKey("interval"), "should not serialize null interval")
        assertFalse(reObj.containsKey("credential"))
        assertFalse(reObj.containsKey("credentials"))

        val reDecoded = json.decodeFromString<CredentialResponse>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun unknownFieldsCapturedInAdditionalParameters() {
        val jsonString =
            """
            {
                "credential": "eyJhbGciOiJFUzI1NiJ9.payload.sig",
                "c_nonce": "abc123",
                "c_nonce_expires_in": 3600,
                "custom_status": "active",
                "issuer_state": "state-ref-456"
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialResponse>(jsonString)

        assertEquals("abc123", decoded.cNonce)
        assertEquals(3600, decoded.cNonceExpiresIn)
        assertEquals(2, decoded.additionalParameters.size)
        assertEquals("active", decoded.additionalParameters["custom_status"]?.jsonPrimitive?.content)
        assertEquals("state-ref-456", decoded.additionalParameters["issuer_state"]?.jsonPrimitive?.content)

        // Round-trip preserves additional parameters
        val reEncoded = json.encodeToString(decoded)
        val reDecoded = json.decodeFromString<CredentialResponse>(reEncoded)
        assertEquals(decoded, reDecoded)
        assertEquals("active", reDecoded.additionalParameters["custom_status"]?.jsonPrimitive?.content)
    }
}
