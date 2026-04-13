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

import com.sphereon.openid.oid4vci.common.model.BatchCredentialIssuance
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialDefinition
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialNotification
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialOfferGrants
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.CredentialResponseItem
import com.sphereon.openid.oid4vci.common.model.KeyAttestationsRequired
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrorResponse
import com.sphereon.openid.oid4vci.common.model.PreAuthorizedCodeOfferGrant
import com.sphereon.openid.oid4vci.common.model.ProofTypeSupported
import com.sphereon.openid.oid4vci.common.model.TxCodeConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
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
 * Spec-example round-trip serialization tests for OID4VCI wire formats.
 *
 * Each test validates that the data model can round-trip through JSON without data loss,
 * and that the wire format matches the spec examples from OID4VCI 1.1.
 *
 * Reference: OpenID for Verifiable Credential Issuance 1.0 / 1.1
 */
class CredentialOfferSerializationTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    // =========================================================================
    // Test 1: Credential Offer round-trip (OID4VCI 1.1 Section 4)
    // =========================================================================

    @Test
    fun credentialOfferWithPreAuthGrantRoundTrip() {
        val jsonString =
            """
            {
              "credential_issuer": "https://credential-issuer.example.com",
              "credential_configuration_ids": ["UniversityDegreeCredential", "org.iso.18013.5.1.mDL"],
              "grants": {
                "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                  "pre-authorized_code": "oaKazRN8I0IbtZ0C7JuMn5",
                  "tx_code": {"length": 4, "input_mode": "numeric", "description": "Please provide the one-time code that was sent via e-mail"}
                }
              }
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialOffer>(jsonString)

        assertEquals("https://credential-issuer.example.com", decoded.credentialIssuer)
        assertEquals(2, decoded.credentialConfigurationIds.size)
        assertEquals("UniversityDegreeCredential", decoded.credentialConfigurationIds[0])
        assertEquals("org.iso.18013.5.1.mDL", decoded.credentialConfigurationIds[1])

        val preAuth = decoded.grants?.preAuthorizedCode
        assertNotNull(preAuth)
        assertEquals("oaKazRN8I0IbtZ0C7JuMn5", preAuth.preAuthorizedCode)
        assertEquals(4, preAuth.txCode?.length)
        assertEquals("numeric", preAuth.txCode?.inputMode)
        assertEquals("Please provide the one-time code that was sent via e-mail", preAuth.txCode?.description)

        // Round-trip: re-serialize then re-deserialize
        val reEncoded = json.encodeToString(decoded)
        val reDecoded = json.decodeFromString<CredentialOffer>(reEncoded)
        assertEquals(decoded, reDecoded)

        // Verify wire key for pre-authorized_code grant
        val wireObj = json.parseToJsonElement(reEncoded).jsonObject
        assertTrue(wireObj.containsKey("credential_issuer"), "must have credential_issuer")
        assertTrue(wireObj.containsKey("credential_configuration_ids"), "must have credential_configuration_ids")
        val grantsObj = wireObj["grants"]?.jsonObject
        assertNotNull(grantsObj)
        assertTrue(
            grantsObj.containsKey("urn:ietf:params:oauth:grant-type:pre-authorized_code"),
            "must use full URN key for pre-authorized_code grant",
        )
        val preAuthObj = grantsObj["urn:ietf:params:oauth:grant-type:pre-authorized_code"]?.jsonObject
        assertNotNull(preAuthObj)
        assertEquals("oaKazRN8I0IbtZ0C7JuMn5", preAuthObj["pre-authorized_code"]?.jsonPrimitive?.content)
        val txCodeObj = preAuthObj["tx_code"]?.jsonObject
        assertNotNull(txCodeObj)
        assertEquals(4, txCodeObj["length"]?.jsonPrimitive?.int)
        // input_mode may be omitted in re-encoded output when encodeDefaults = false and it equals
        // the default "numeric". Verify via the deserialized model instead.
        assertEquals(
            "numeric",
            reDecoded.grants
                ?.preAuthorizedCode
                ?.txCode
                ?.inputMode,
        )
    }

    // =========================================================================
    // Test 2: Issuer Metadata with 1.1 features (OID4VCI 1.1 Section 13.2)
    // =========================================================================

    @Test
    fun issuerMetadataWith11FeaturesRoundTrip() {
        val jsonString =
            """
            {
              "credential_issuer": "https://credential-issuer.example.com",
              "credential_endpoint": "https://credential-issuer.example.com/credential",
              "nonce_endpoint": "https://credential-issuer.example.com/nonce",
              "batch_credential_issuance": {"batch_size": 5},
              "credential_configurations_supported": {
                "UniversityDegreeCredential": {
                  "format": "jwt_vc_json",
                  "credential_definition": {"type": ["VerifiableCredential", "UniversityDegreeCredential"]},
                  "proof_types_supported": {
                    "jwt": {
                      "proof_signing_alg_values_supported": ["ES256"],
                      "key_attestations_required": {"key_storage": ["iso_18045_high"]}
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialIssuerMetadata>(jsonString)

        assertEquals("https://credential-issuer.example.com", decoded.credentialIssuer)
        assertEquals("https://credential-issuer.example.com/credential", decoded.credentialEndpoint)
        assertEquals("https://credential-issuer.example.com/nonce", decoded.nonceEndpoint)
        assertEquals(5, decoded.batchCredentialIssuance?.batchSize)

        val univDegree = decoded.credentialConfigurationsSupported["UniversityDegreeCredential"]
        assertNotNull(univDegree)
        assertEquals("jwt_vc_json", univDegree.format)
        assertEquals(listOf("VerifiableCredential", "UniversityDegreeCredential"), univDegree.credentialDefinition?.type)

        val jwtProof = univDegree.proofTypesSupported?.get("jwt")
        assertNotNull(jwtProof)
        assertEquals(listOf("ES256"), jwtProof.proofSigningAlgValuesSupported)
        assertEquals(listOf("iso_18045_high"), jwtProof.keyAttestationsRequired?.keyStorage)

        // Round-trip
        val reEncoded = json.encodeToString(decoded)
        val reDecoded = json.decodeFromString<CredentialIssuerMetadata>(reEncoded)
        assertEquals(decoded, reDecoded)

        // Verify wire format keys
        val wireObj = json.parseToJsonElement(reEncoded).jsonObject
        assertTrue(wireObj.containsKey("nonce_endpoint"), "must serialize nonce_endpoint")
        assertTrue(wireObj.containsKey("batch_credential_issuance"), "must serialize batch_credential_issuance")
        assertEquals(
            5,
            wireObj["batch_credential_issuance"]
                ?.jsonObject
                ?.get("batch_size")
                ?.jsonPrimitive
                ?.int,
        )
        assertFalse(wireObj.containsKey("batch_credential_endpoint"), "optional fields must not appear when null")
    }

    // =========================================================================
    // Test 3: Credential Response 1.1 — credentials array (Section 9.3)
    // =========================================================================

    @Test
    fun credentialResponse11WithCredentialsArrayRoundTrip() {
        val jsonString =
            """
            {
              "credentials": [{"credential": "LUpixVCWJk0eOt4CXQe1NXK....WZwmhmn9OQp6YxX0a2L"}],
              "notification_id": "3fwe98js"
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialResponse>(jsonString)

        assertNull(decoded.credential, "1.1 credentials array must not populate singular credential field")
        assertNotNull(decoded.credentials)
        assertEquals(1, decoded.credentials?.size)
        assertEquals(
            "LUpixVCWJk0eOt4CXQe1NXK....WZwmhmn9OQp6YxX0a2L",
            (decoded.credentials?.first()?.credential as? JsonPrimitive)?.content,
        )
        assertEquals("3fwe98js", decoded.notificationId)

        // Round-trip
        val reEncoded = json.encodeToString(decoded)
        val reDecoded = json.decodeFromString<CredentialResponse>(reEncoded)
        assertEquals(decoded, reDecoded)

        // Verify wire format
        val wireObj = json.parseToJsonElement(reEncoded).jsonObject
        assertTrue(wireObj.containsKey("credentials"), "must have 'credentials' array key")
        assertFalse(wireObj.containsKey("credential"), "must NOT have singular 'credential' key")
        assertEquals("3fwe98js", wireObj["notification_id"]?.jsonPrimitive?.content)

        val credArr = wireObj["credentials"]?.jsonArray
        assertNotNull(credArr)
        assertEquals(1, credArr.size)
        assertTrue(credArr[0].jsonObject.containsKey("credential"), "each credentials array item must have 'credential' key")
    }

    // =========================================================================
    // Test 4: Credential Response 1.0 backward compat — singular credential
    // =========================================================================

    @Test
    fun credentialResponse10WithSingularCredentialRoundTrip() {
        val jsonString =
            """
            {
              "credential": "eyJhbGciOiJFUzI1NiJ9.eyJ2Y...",
              "c_nonce": "fGFF7UkhL",
              "c_nonce_expires_in": 86400
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialResponse>(jsonString)

        assertNotNull(decoded.credential)
        assertEquals("eyJhbGciOiJFUzI1NiJ9.eyJ2Y...", (decoded.credential as? JsonPrimitive)?.content)
        assertNull(decoded.credentials, "1.0 must not have credentials array")
        assertEquals("fGFF7UkhL", decoded.cNonce)
        assertEquals(86400, decoded.cNonceExpiresIn)

        // Round-trip
        val reEncoded = json.encodeToString(decoded)
        val reDecoded = json.decodeFromString<CredentialResponse>(reEncoded)
        assertEquals(decoded, reDecoded)

        // Verify wire format
        val wireObj = json.parseToJsonElement(reEncoded).jsonObject
        assertTrue(wireObj.containsKey("credential"), "must have singular 'credential' key")
        assertFalse(wireObj.containsKey("credentials"), "must NOT have 'credentials' array")
        assertEquals("fGFF7UkhL", wireObj["c_nonce"]?.jsonPrimitive?.content)
        assertEquals(86400, wireObj["c_nonce_expires_in"]?.jsonPrimitive?.int)
    }

    // =========================================================================
    // Test 5: Proofs wire format (OID4VCI 1.1 Section 9.2)
    // =========================================================================

    @Test
    fun credentialRequestProofsWireFormat() {
        val proofs =
            CredentialRequestProofs(
                proofType = "jwt",
                proofValues = listOf(JsonPrimitive("eyJ1"), JsonPrimitive("eyJ2")),
            )

        val encoded = json.encodeToString(proofs)
        val wireObj = json.parseToJsonElement(encoded).jsonObject

        // The proof type IS the JSON key; there must be no "proof_type" field
        assertFalse(wireObj.containsKey("proof_type"), "wire format must NOT have a 'proof_type' key")
        assertFalse(wireObj.containsKey("proofType"), "wire format must NOT have a 'proofType' key")
        assertTrue(wireObj.containsKey("jwt"), "the proof type 'jwt' must be the JSON key")

        val proofsArray = wireObj["jwt"]?.jsonArray
        assertNotNull(proofsArray)
        assertEquals(2, proofsArray.size)
        assertEquals("eyJ1", proofsArray[0].jsonPrimitive.content)
        assertEquals("eyJ2", proofsArray[1].jsonPrimitive.content)

        // Round-trip
        val decoded = json.decodeFromString<CredentialRequestProofs>(encoded)
        assertEquals("jwt", decoded.proofType)
        assertEquals(listOf(JsonPrimitive("eyJ1"), JsonPrimitive("eyJ2")), decoded.proofValues)
        assertEquals(proofs, decoded)
    }

    // =========================================================================
    // Test 6: Deferred response pending (OID4VCI 1.1 Section 10.2)
    // =========================================================================

    @Test
    fun deferredResponsePendingRoundTrip() {
        val jsonString =
            """
            {"transaction_id": "8xLOxBtZp8", "interval": 3600}
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialResponse>(jsonString)

        assertNull(decoded.credential, "deferred pending must not have credential")
        assertNull(decoded.credentials, "deferred pending must not have credentials array")
        assertEquals("8xLOxBtZp8", decoded.transactionId)
        assertEquals(3600, decoded.interval)

        // Round-trip
        val reEncoded = json.encodeToString(decoded)
        val reDecoded = json.decodeFromString<CredentialResponse>(reEncoded)
        assertEquals(decoded, reDecoded)

        // Verify wire format
        val wireObj = json.parseToJsonElement(reEncoded).jsonObject
        assertEquals("8xLOxBtZp8", wireObj["transaction_id"]?.jsonPrimitive?.content)
        assertEquals(3600, wireObj["interval"]?.jsonPrimitive?.int)
        assertFalse(wireObj.containsKey("credential"), "deferred pending must not serialize credential")
        assertFalse(wireObj.containsKey("credentials"), "deferred pending must not serialize credentials")
    }

    // =========================================================================
    // Test 7: Nonce response (OID4VCI 1.1 Section 8.2)
    // =========================================================================

    @Test
    fun nonceResponseRoundTrip() {
        val jsonString =
            """
            {"c_nonce": "wKI4LT17ac15ES9bw8ac4"}
            """.trimIndent()

        val decoded = json.decodeFromString<NonceResponse>(jsonString)

        assertEquals("wKI4LT17ac15ES9bw8ac4", decoded.cNonce)
        assertNull(decoded.cNonceExpiresIn)

        // Round-trip
        val reEncoded = json.encodeToString(decoded)
        val reDecoded = json.decodeFromString<NonceResponse>(reEncoded)
        assertEquals(decoded, reDecoded)

        // Verify wire format: no c_nonce_expires_in when absent
        val wireObj = json.parseToJsonElement(reEncoded).jsonObject
        assertEquals("wKI4LT17ac15ES9bw8ac4", wireObj["c_nonce"]?.jsonPrimitive?.content)
        assertFalse(wireObj.containsKey("c_nonce_expires_in"), "must not serialize null c_nonce_expires_in")
    }

    // =========================================================================
    // Test 8: Notification request wire names (OID4VCI 1.1 Section 12.1)
    // =========================================================================

    @Test
    fun notificationRequestWireNames() {
        val notification =
            CredentialNotification(
                notificationId = "3fwe98js",
                event = CredentialNotificationEvent.CREDENTIAL_ACCEPTED,
                eventDescription = "Credential has been stored",
            )

        val encoded = json.encodeToString(notification)
        val wireObj = json.parseToJsonElement(encoded).jsonObject

        // Verify spec wire names
        assertTrue(wireObj.containsKey("notification_id"), "must have 'notification_id' wire key")
        assertTrue(wireObj.containsKey("event"), "must have 'event' wire key")
        assertTrue(wireObj.containsKey("event_description"), "must have 'event_description' wire key")
        assertFalse(wireObj.containsKey("notificationId"), "must NOT use camelCase 'notificationId'")
        assertFalse(wireObj.containsKey("eventDescription"), "must NOT use camelCase 'eventDescription'")

        assertEquals("3fwe98js", wireObj["notification_id"]?.jsonPrimitive?.content)
        assertEquals("credential_accepted", wireObj["event"]?.jsonPrimitive?.content)
        assertEquals("Credential has been stored", wireObj["event_description"]?.jsonPrimitive?.content)

        // Round-trip
        val decoded = json.decodeFromString<CredentialNotification>(encoded)
        assertEquals(notification, decoded)

        // Verify all event types serialize correctly
        assertEquals(
            "credential_accepted",
            json
                .parseToJsonElement(json.encodeToString(CredentialNotification("id", CredentialNotificationEvent.CREDENTIAL_ACCEPTED)))
                .jsonObject["event"]
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "credential_failure",
            json
                .parseToJsonElement(json.encodeToString(CredentialNotification("id", CredentialNotificationEvent.CREDENTIAL_FAILURE)))
                .jsonObject["event"]
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "credential_deleted",
            json
                .parseToJsonElement(json.encodeToString(CredentialNotification("id", CredentialNotificationEvent.CREDENTIAL_DELETED)))
                .jsonObject["event"]
                ?.jsonPrimitive
                ?.content,
        )
    }

    // =========================================================================
    // Test 9: Error response with 1.1 error code
    // =========================================================================

    @Test
    fun errorResponseWith11ErrorCodeRoundTrip() {
        val jsonString =
            """
            {"error": "invalid_nonce", "error_description": "The nonce is invalid"}
            """.trimIndent()

        val decoded = json.decodeFromString<Oid4vciErrorResponse>(jsonString)

        assertEquals("invalid_nonce", decoded.error)
        assertEquals("The nonce is invalid", decoded.errorDescription)
        assertNull(decoded.errorUri)
        assertNull(decoded.cNonce)
        assertNull(decoded.cNonceExpiresIn)
        assertNull(decoded.interval)

        // Round-trip
        val reEncoded = json.encodeToString(decoded)
        val reDecoded = json.decodeFromString<Oid4vciErrorResponse>(reEncoded)
        assertEquals(decoded, reDecoded)

        // Verify wire format
        val wireObj = json.parseToJsonElement(reEncoded).jsonObject
        assertEquals("invalid_nonce", wireObj["error"]?.jsonPrimitive?.content)
        assertEquals("The nonce is invalid", wireObj["error_description"]?.jsonPrimitive?.content)
        // With encodeDefaults = false, optional null fields should not appear
        assertFalse(wireObj.containsKey("error_uri"), "must not serialize null error_uri")
        assertFalse(wireObj.containsKey("c_nonce"), "must not serialize null c_nonce")
        assertFalse(wireObj.containsKey("interval"), "must not serialize null interval")
    }
}
