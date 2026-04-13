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

import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.CredentialResponseItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Serialization/deserialization tests for RequestCredential.
 *
 * Covers:
 * - CredentialRequest serialization with credential_configuration_id or credential_identifier
 * - CredentialRequest serialization with single proof (OID4VCI 1.0) and batch proofs (OID4VCI 1.1)
 * - CredentialResponse deserialization for immediate (OID4VCI 1.0 single credential)
 * - CredentialResponse deserialization for immediate (OID4VCI 1.1 credentials array)
 * - CredentialResponse deserialization for deferred issuance (transaction_id)
 *
 * Pure serialization tests — no HTTP mocking required.
 */
class RequestCredentialTest {
    private val json = Json { ignoreUnknownKeys = true }

    // ============================================================================
    // CredentialRequest serialization
    // ============================================================================

    @Test
    fun serializeCredentialRequestWithConfigurationId() {
        val request =
            CredentialRequest(
                credentialConfigurationId = "UniversityDegreeCredential",
            )

        val serialized = json.encodeToString(CredentialRequest.serializer(), request)
        val parsed = json.parseToJsonElement(serialized)
        val obj = parsed as kotlinx.serialization.json.JsonObject

        assertEquals("UniversityDegreeCredential", (obj["credential_configuration_id"] as? JsonPrimitive)?.content)
        assertNull(obj["credential_identifier"])
        assertNull(obj["proofs"])
    }

    @Test
    fun serializeCredentialRequestWithCredentialIdentifier() {
        val request =
            CredentialRequest(
                credentialIdentifier = "CivilEngineeringDegree-2023",
            )

        val serialized = json.encodeToString(CredentialRequest.serializer(), request)
        val obj = json.parseToJsonElement(serialized) as kotlinx.serialization.json.JsonObject

        assertEquals("CivilEngineeringDegree-2023", (obj["credential_identifier"] as? JsonPrimitive)?.content)
        assertNull(obj["credential_configuration_id"])
    }

    @Test
    fun serializeCredentialRequestWithProofs() {
        val request =
            CredentialRequest(
                credentialConfigurationId = "UniversityDegreeCredential",
                proofs =
                    CredentialRequestProofs(
                        proofType = "jwt",
                        proofValues =
                            listOf(
                                JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.payload1.sig1"),
                                JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.payload2.sig2"),
                            ),
                    ),
            )

        val serialized = json.encodeToString(CredentialRequest.serializer(), request)
        val obj = json.parseToJsonElement(serialized) as kotlinx.serialization.json.JsonObject

        assertNotNull(obj["proofs"])
        // Per OID4VCI spec: proofs wire format is {"proofs": {"jwt": ["eyJ...", "eyJ..."]}}
        val proofs = obj["proofs"] as kotlinx.serialization.json.JsonObject
        val jwtArray = proofs["jwt"] as? kotlinx.serialization.json.JsonArray
        assertNotNull(jwtArray)
        assertEquals(2, jwtArray.size)
    }

    // ============================================================================
    // CredentialResponse deserialization — OID4VCI 1.0 (single credential)
    // ============================================================================

    @Test
    fun deserializeImmediateResponseOid4vci10() {
        // OID4VCI 1.0 format: credential is a single value
        val raw =
            """
            {
              "credential": "eyJhbGciOiJFUzI1NiIsInR5cCI6InZjK3NkLWp3dCJ9.payload.sig",
              "c_nonce": "fGFF7UkhLa",
              "c_nonce_expires_in": 86400,
              "notification_id": "3fwe98js"
            }
            """.trimIndent()

        val response = json.decodeFromString(CredentialResponse.serializer(), raw)

        assertNotNull(response.credential)
        assertNull(response.credentials)
        assertNull(response.transactionId)
        assertEquals("fGFF7UkhLa", response.cNonce)
        assertEquals(86400, response.cNonceExpiresIn)
        assertEquals("3fwe98js", response.notificationId)
    }

    // ============================================================================
    // CredentialResponse deserialization — OID4VCI 1.1 (credentials array)
    // ============================================================================

    @Test
    fun deserializeImmediateResponseOid4vci11() {
        // OID4VCI 1.1 format: credentials is an array of objects with a "credential" field
        val raw =
            """
            {
              "credentials": [
                {
                  "credential": "eyJhbGciOiJFUzI1NiIsInR5cCI6InZjK3NkLWp3dCJ9.payload1.sig1"
                }
              ],
              "c_nonce": "tZignsnFbp",
              "notification_id": "xyz789"
            }
            """.trimIndent()

        val response = json.decodeFromString(CredentialResponse.serializer(), raw)

        assertNull(response.credential)
        assertNotNull(response.credentials)
        assertEquals(1, response.credentials!!.size)
        assertNotNull(response.credentials!![0].credential)
        assertEquals("tZignsnFbp", response.cNonce)
    }

    @Test
    fun deserializeImmediateResponseOid4vci11MultipleCreds() {
        val raw =
            """
            {
              "credentials": [
                {"credential": "eyJ.cred1.sig1"},
                {"credential": "eyJ.cred2.sig2"}
              ]
            }
            """.trimIndent()

        val response = json.decodeFromString(CredentialResponse.serializer(), raw)

        assertNotNull(response.credentials)
        assertEquals(2, response.credentials!!.size)
    }

    // ============================================================================
    // CredentialResponse deserialization — deferred issuance
    // ============================================================================

    @Test
    fun deserializeDeferredResponse() {
        // Deferred: transaction_id present, no credential(s)
        val raw =
            """
            {
              "transaction_id": "8xLOxBtZp8",
              "interval": 5
            }
            """.trimIndent()

        val response = json.decodeFromString(CredentialResponse.serializer(), raw)

        assertNull(response.credential)
        assertNull(response.credentials)
        assertEquals("8xLOxBtZp8", response.transactionId)
        assertEquals(5, response.interval)
    }

    // ============================================================================
    // CredentialResponse serialization round-trip
    // ============================================================================

    @Test
    fun roundTripDeferredResponse() {
        val original =
            CredentialResponse(
                transactionId = "8xLOxBtZp8",
                interval = 5,
            )

        val serialized = json.encodeToString(CredentialResponse.serializer(), original)
        val restored = json.decodeFromString(CredentialResponse.serializer(), serialized)

        assertEquals(original.transactionId, restored.transactionId)
        assertEquals(original.interval, restored.interval)
        assertNull(restored.credential)
        assertNull(restored.credentials)
    }
}
