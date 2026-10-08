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

/**
 * OID4VCI 1.0 final §8.3 Credential Response shape:
 *  - `credentials`: REQUIRED for synchronous issuance — array of `{ credential: <jwt> }`.
 *  - `transaction_id`: REQUIRED for deferred issuance.
 *  - `notification_id`: OPTIONAL.
 *  - `interval`: OPTIONAL polling hint with `transaction_id`.
 *
 * Nonces are not on this response — wallets fetch them from the dedicated `/nonce` endpoint
 * (§7.2). Anything else in the body is captured into [CredentialResponse.additionalParameters]
 * for forward-compat / extension fields.
 */
class CredentialResponseSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun singleCredentialRoundTrip() {
        val response =
            CredentialResponse(
                credentials =
                    listOf(
                        CredentialResponseItem(
                            credential = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.eyJ2Y3QiOiJodHRwczovL2NyZWRlbnRpYWxzLmV4YW1wbGUuY29tL2lkZW50aXR5X2NyZWRlbnRpYWwifQ.signature"),
                        ),
                    ),
            )

        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<CredentialResponse>(encoded)

        assertEquals(response, decoded)
        assertNotNull(decoded.credentials)
        assertEquals(1, decoded.credentials?.size)
        assertNull(decoded.transactionId)
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
        assertNotNull(decoded.credentials)
        assertEquals(3, decoded.credentials?.size)
        assertEquals("notification-abc123", decoded.notificationId)
    }

    @Test
    fun deferredResponseRoundTrip() {
        val response =
            CredentialResponse(
                transactionId = "txn-8dfc3e6a-b5f1-4e7d-a3c0-9b2f1e4d6a8c",
                interval = 5,
            )

        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<CredentialResponse>(encoded)

        assertEquals(response, decoded)
        assertNull(decoded.credentials)
        assertEquals("txn-8dfc3e6a-b5f1-4e7d-a3c0-9b2f1e4d6a8c", decoded.transactionId)
        assertEquals(5, decoded.interval)
    }

    @Test
    fun immediateResponseWireFormatUsesCredentialsArray() {
        val jsonString =
            """
            {
                "credentials": [
                    { "credential": "eyJhbGciOiJFUzI1NiJ9.credential1.sig1" },
                    { "credential": "eyJhbGciOiJFUzI1NiJ9.credential2.sig2" }
                ],
                "notification_id": "notify-1"
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialResponse>(jsonString)

        assertNotNull(decoded.credentials)
        assertEquals(2, decoded.credentials?.size)
        assertEquals("notify-1", decoded.notificationId)

        decoded.credentials?.forEach { item ->
            assertTrue(item.credential is JsonPrimitive, "each item.credential should be a primitive string")
        }

        val reEncoded = json.encodeToString(decoded)
        val reObj = json.parseToJsonElement(reEncoded).jsonObject
        assertTrue(reObj.containsKey("credentials"), "must have 'credentials' key")
        assertFalse(reObj.containsKey("credential"), "must not emit singular 'credential' key (dropped in 1.0 final)")

        val credentialsArray = reObj["credentials"]!!.jsonArray
        assertEquals(2, credentialsArray.size)
        credentialsArray.forEach { element ->
            assertTrue(element.jsonObject.containsKey("credential"), "each credentials array element must have 'credential' key")
        }

        val reDecoded = json.decodeFromString<CredentialResponse>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun deferredResponseWireFormat() {
        val jsonString =
            """
            {
                "transaction_id": "txn-8dfc3e6a-b5f1-4e7d-a3c0-9b2f1e4d6a8c",
                "interval": 5
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialResponse>(jsonString)

        assertNull(decoded.credentials)
        assertEquals("txn-8dfc3e6a-b5f1-4e7d-a3c0-9b2f1e4d6a8c", decoded.transactionId)
        assertEquals(5, decoded.interval)

        val reEncoded = json.encodeToString(decoded)
        val reObj = json.parseToJsonElement(reEncoded).jsonObject
        assertEquals("txn-8dfc3e6a-b5f1-4e7d-a3c0-9b2f1e4d6a8c", reObj["transaction_id"]?.jsonPrimitive?.content)
        assertEquals(5, reObj["interval"]?.jsonPrimitive?.intOrNull)
        assertFalse(reObj.containsKey("credentials"), "deferred response must not have credentials")

        val reDecoded = json.decodeFromString<CredentialResponse>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun deferredResponseWithoutIntervalRoundTrip() {
        val jsonString =
            """
            {
                "transaction_id": "txn-pending-001"
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialResponse>(jsonString)

        assertNull(decoded.credentials)
        assertEquals("txn-pending-001", decoded.transactionId)
        assertNull(decoded.interval)

        val reEncoded = json.encodeToString(decoded)
        val reObj = json.parseToJsonElement(reEncoded).jsonObject
        assertEquals("txn-pending-001", reObj["transaction_id"]?.jsonPrimitive?.content)
        assertFalse(reObj.containsKey("interval"), "should not serialize null interval")
        assertFalse(reObj.containsKey("credentials"))
    }

    @Test
    fun unknownFieldsCapturedInAdditionalParameters() {
        // Legacy `c_nonce` / `c_nonce_expires_in` from a pre-1.0-final issuer flow into
        // additionalParameters — they're no longer first-class fields on the model.
        val jsonString =
            """
            {
                "credentials": [
                    { "credential": "eyJhbGciOiJFUzI1NiJ9.payload.sig" }
                ],
                "c_nonce": "abc123",
                "c_nonce_expires_in": 3600,
                "custom_status": "active",
                "issuer_state": "state-ref-456"
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialResponse>(jsonString)

        assertNotNull(decoded.credentials)
        assertEquals(4, decoded.additionalParameters.size)
        assertEquals("abc123", decoded.additionalParameters["c_nonce"]?.jsonPrimitive?.content)
        assertEquals(3600, decoded.additionalParameters["c_nonce_expires_in"]?.jsonPrimitive?.intOrNull)
        assertEquals("active", decoded.additionalParameters["custom_status"]?.jsonPrimitive?.content)
        assertEquals("state-ref-456", decoded.additionalParameters["issuer_state"]?.jsonPrimitive?.content)

        val reEncoded = json.encodeToString(decoded)
        val reDecoded = json.decodeFromString<CredentialResponse>(reEncoded)
        assertEquals(decoded, reDecoded)
        assertEquals("active", reDecoded.additionalParameters["custom_status"]?.jsonPrimitive?.content)
    }
}
