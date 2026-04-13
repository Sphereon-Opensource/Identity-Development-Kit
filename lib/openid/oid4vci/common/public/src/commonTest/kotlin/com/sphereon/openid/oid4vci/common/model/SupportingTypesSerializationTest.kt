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
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SupportingTypesSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun deferredCredentialRequestRoundTrip() {
        val request =
            DeferredCredentialRequest(
                transactionId = "txn-8dfc3e6a-b5f1-4e7d-a3c0-9b2f1e4d6a8c",
            )

        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<DeferredCredentialRequest>(encoded)

        assertEquals(request, decoded)
        assertEquals("txn-8dfc3e6a-b5f1-4e7d-a3c0-9b2f1e4d6a8c", decoded.transactionId)
    }

    @Test
    fun credentialNotificationAcceptedRoundTrip() {
        val notification =
            CredentialNotification(
                notificationId = "notification-abc123",
                event = CredentialNotificationEvent.CREDENTIAL_ACCEPTED,
                eventDescription = "Credential was successfully accepted by the holder",
            )

        val encoded = json.encodeToString(notification)
        val decoded = json.decodeFromString<CredentialNotification>(encoded)

        assertEquals(notification, decoded)
        assertEquals("notification-abc123", decoded.notificationId)
        assertEquals(CredentialNotificationEvent.CREDENTIAL_ACCEPTED, decoded.event)
        assertEquals("Credential was successfully accepted by the holder", decoded.eventDescription)
    }

    @Test
    fun credentialNotificationFailureRoundTrip() {
        val notification =
            CredentialNotification(
                notificationId = "notification-fail456",
                event = CredentialNotificationEvent.CREDENTIAL_FAILURE,
                eventDescription = "Credential processing failed due to invalid signature",
            )

        val encoded = json.encodeToString(notification)
        val decoded = json.decodeFromString<CredentialNotification>(encoded)

        assertEquals(notification, decoded)
        assertEquals(CredentialNotificationEvent.CREDENTIAL_FAILURE, decoded.event)
    }

    @Test
    fun credentialNotificationDeletedRoundTrip() {
        val notification =
            CredentialNotification(
                notificationId = "notification-del789",
                event = CredentialNotificationEvent.CREDENTIAL_DELETED,
            )

        val encoded = json.encodeToString(notification)
        val decoded = json.decodeFromString<CredentialNotification>(encoded)

        assertEquals(notification, decoded)
        assertEquals(CredentialNotificationEvent.CREDENTIAL_DELETED, decoded.event)
        assertNull(decoded.eventDescription)
    }

    @Test
    fun nonceResponseRoundTrip() {
        val response =
            NonceResponse(
                cNonce = "fGFF7UkhLa-EWzN5znxBb",
                cNonceExpiresIn = 86400,
            )

        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<NonceResponse>(encoded)

        assertEquals(response, decoded)
        assertEquals("fGFF7UkhLa-EWzN5znxBb", decoded.cNonce)
        assertEquals(86400, decoded.cNonceExpiresIn)
    }

    @Test
    fun nonceResponseWithoutExpiresInRoundTrip() {
        val response =
            NonceResponse(
                cNonce = "nonce-value-abc123",
            )

        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<NonceResponse>(encoded)

        assertEquals(response, decoded)
        assertEquals("nonce-value-abc123", decoded.cNonce)
        assertNull(decoded.cNonceExpiresIn)
    }

    @Test
    fun authorizationDetailRoundTrip() {
        val detail =
            Oid4vciAuthorizationDetail(
                type = "openid_credential",
                credentialConfigurationId = "UniversityDegreeCredential",
                locations = listOf("https://credential-issuer.example.com"),
            )

        val encoded = json.encodeToString(detail)
        val decoded = json.decodeFromString<Oid4vciAuthorizationDetail>(encoded)

        assertEquals(detail, decoded)
        assertEquals("openid_credential", decoded.type)
        assertEquals("UniversityDegreeCredential", decoded.credentialConfigurationId)
        assertEquals(listOf("https://credential-issuer.example.com"), decoded.locations)
        assertNull(decoded.claims)
    }

    @Test
    fun authorizationDetailMinimalRoundTrip() {
        val detail =
            Oid4vciAuthorizationDetail(
                type = "openid_credential",
                credentialConfigurationId = "MinimalCredential",
            )

        val encoded = json.encodeToString(detail)
        val decoded = json.decodeFromString<Oid4vciAuthorizationDetail>(encoded)

        assertEquals(detail, decoded)
        assertEquals("openid_credential", decoded.type)
        assertEquals("MinimalCredential", decoded.credentialConfigurationId)
        assertNull(decoded.locations)
        assertNull(decoded.claims)
    }

    @Test
    fun errorResponseRoundTrip() {
        val error =
            Oid4vciErrorResponse(
                error = Oid4vciErrors.INVALID_PROOF,
                errorDescription = "The proof JWT signature is invalid",
                errorUri = "https://issuer.example.com/errors/invalid_proof",
                cNonce = "new-nonce-abc123",
                cNonceExpiresIn = 300,
                interval = 10,
            )

        val encoded = json.encodeToString(error)
        val decoded = json.decodeFromString<Oid4vciErrorResponse>(encoded)

        assertEquals(error, decoded)
        assertEquals("invalid_proof", decoded.error)
        assertEquals("The proof JWT signature is invalid", decoded.errorDescription)
        assertEquals("https://issuer.example.com/errors/invalid_proof", decoded.errorUri)
        assertEquals("new-nonce-abc123", decoded.cNonce)
        assertEquals(300, decoded.cNonceExpiresIn)
        assertEquals(10, decoded.interval)
    }

    @Test
    fun errorResponseMinimalRoundTrip() {
        val error =
            Oid4vciErrorResponse(
                error = Oid4vciErrors.CREDENTIAL_REQUEST_DENIED,
            )

        val encoded = json.encodeToString(error)
        val decoded = json.decodeFromString<Oid4vciErrorResponse>(encoded)

        assertEquals(error, decoded)
        assertEquals("credential_request_denied", decoded.error)
        assertNull(decoded.errorDescription)
        assertNull(decoded.errorUri)
        assertNull(decoded.cNonce)
        assertNull(decoded.cNonceExpiresIn)
        assertNull(decoded.interval)
    }

    @Test
    fun credentialNotificationEventSerializesToCorrectValues() {
        val acceptedJson = json.encodeToString(CredentialNotificationEvent.CREDENTIAL_ACCEPTED)
        assertEquals("\"credential_accepted\"", acceptedJson)

        val failureJson = json.encodeToString(CredentialNotificationEvent.CREDENTIAL_FAILURE)
        assertEquals("\"credential_failure\"", failureJson)

        val deletedJson = json.encodeToString(CredentialNotificationEvent.CREDENTIAL_DELETED)
        assertEquals("\"credential_deleted\"", deletedJson)

        // Deserialize back
        val decodedAccepted = json.decodeFromString<CredentialNotificationEvent>(acceptedJson)
        assertEquals(CredentialNotificationEvent.CREDENTIAL_ACCEPTED, decodedAccepted)

        val decodedFailure = json.decodeFromString<CredentialNotificationEvent>(failureJson)
        assertEquals(CredentialNotificationEvent.CREDENTIAL_FAILURE, decodedFailure)

        val decodedDeleted = json.decodeFromString<CredentialNotificationEvent>(deletedJson)
        assertEquals(CredentialNotificationEvent.CREDENTIAL_DELETED, decodedDeleted)
    }

    @Test
    fun errorResponseIssuancePendingWithIntervalRoundTrip() {
        val error =
            Oid4vciErrorResponse(
                error = Oid4vciErrors.ISSUANCE_PENDING,
                interval = 5,
            )

        val encoded = json.encodeToString(error)
        val decoded = json.decodeFromString<Oid4vciErrorResponse>(encoded)

        assertEquals(error, decoded)
        assertEquals("issuance_pending", decoded.error)
        assertEquals(5, decoded.interval)
        assertNull(decoded.errorDescription)
    }

    @Test
    fun authorizationDetailWithClaimsRoundTrip() {
        val claimsList =
            listOf(
                ClaimsDescriptionObject(path = listOf(JsonPrimitive("given_name"))),
                ClaimsDescriptionObject(
                    path = listOf(JsonPrimitive("address"), JsonPrimitive("street_address")),
                    values = listOf(JsonPrimitive("123 Main St")),
                ),
            )
        val detail =
            Oid4vciAuthorizationDetail(
                type = "openid_credential",
                credentialConfigurationId = "IdentityCredential",
                claims = json.encodeToJsonElement(claimsList),
            )

        val encoded = json.encodeToString(detail)
        val decoded = json.decodeFromString<Oid4vciAuthorizationDetail>(encoded)

        assertEquals(detail, decoded)
        assertEquals("IdentityCredential", decoded.credentialConfigurationId)
        assertNotNull(decoded.claims)
        val decodedClaims = decoded.claimsAsDescriptionObjects()
        assertNotNull(decodedClaims)
        assertEquals(2, decodedClaims.size)
        assertEquals(listOf(JsonPrimitive("given_name")), decodedClaims[0].path)
        assertNull(decodedClaims[0].values)
        assertEquals(listOf(JsonPrimitive("address"), JsonPrimitive("street_address")), decodedClaims[1].path)
        assertEquals(listOf(JsonPrimitive("123 Main St")), decodedClaims[1].values)
    }

    @Test
    fun claimsDescriptionObjectRoundTrip() {
        val obj =
            ClaimsDescriptionObject(
                path = listOf(JsonPrimitive("degree"), JsonPrimitive("type")),
                values = listOf(JsonPrimitive("BachelorDegree"), JsonPrimitive("MasterDegree")),
            )

        val encoded = json.encodeToString(obj)
        val decoded = json.decodeFromString<ClaimsDescriptionObject>(encoded)

        assertEquals(obj, decoded)
        assertEquals(listOf(JsonPrimitive("degree"), JsonPrimitive("type")), decoded.path)
        assertEquals(2, decoded.values?.size)
    }

    @Test
    fun claimsDescriptionObjectMinimalRoundTrip() {
        val obj =
            ClaimsDescriptionObject(
                path = listOf(JsonPrimitive("family_name")),
            )

        val encoded = json.encodeToString(obj)
        val decoded = json.decodeFromString<ClaimsDescriptionObject>(encoded)

        assertEquals(obj, decoded)
        assertEquals(listOf(JsonPrimitive("family_name")), decoded.path)
        assertNull(decoded.values)
    }

    @Test
    fun deferredCredentialRequestWithProofsRoundTrip() {
        val request =
            DeferredCredentialRequest(
                transactionId = "txn-with-proofs-001",
                proofs =
                    CredentialRequestProofs(
                        proofType = "jwt",
                        proofValues = listOf(JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.eyJub25jZSI6ImFiYzEyMyJ9.signature")),
                    ),
            )

        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<DeferredCredentialRequest>(encoded)

        assertEquals(request, decoded)
        assertEquals("txn-with-proofs-001", decoded.transactionId)
        assertNotNull(decoded.proofs)
        assertEquals("jwt", decoded.proofs?.proofType)
        assertEquals(1, decoded.proofs?.proofValues?.size)
    }

    @Test
    fun deferredCredentialRequestDeserializesFromJson() {
        val jsonString =
            """
            {
                "transaction_id": "deferred-txn-001"
            }
            """.trimIndent()

        val decoded = json.decodeFromString<DeferredCredentialRequest>(jsonString)
        assertEquals("deferred-txn-001", decoded.transactionId)
        assertNull(decoded.proofs)

        // Verify round-trip
        val reEncoded = json.encodeToString(decoded)
        val reDecoded = json.decodeFromString<DeferredCredentialRequest>(reEncoded)
        assertEquals(decoded, reDecoded)
    }

    @Test
    fun nonceResponseDeserializesFromJson() {
        val jsonString =
            """
            {
                "c_nonce": "PAPm5aVICjpg2zMK09ydRQ",
                "c_nonce_expires_in": 600
            }
            """.trimIndent()

        val decoded = json.decodeFromString<NonceResponse>(jsonString)
        assertEquals("PAPm5aVICjpg2zMK09ydRQ", decoded.cNonce)
        assertEquals(600, decoded.cNonceExpiresIn)
    }

    @Test
    fun errorResponseDeserializesFromJson() {
        val jsonString =
            """
            {
                "error": "invalid_token",
                "error_description": "The provided token is invalid or expired",
                "c_nonce": "new-nonce-xyz",
                "c_nonce_expires_in": 300
            }
            """.trimIndent()

        val decoded = json.decodeFromString<Oid4vciErrorResponse>(jsonString)
        assertEquals("invalid_token", decoded.error)
        assertEquals("The provided token is invalid or expired", decoded.errorDescription)
        assertNotNull(decoded.cNonce)
        assertEquals("new-nonce-xyz", decoded.cNonce)
        assertEquals(300, decoded.cNonceExpiresIn)
        assertNull(decoded.errorUri)
        assertNull(decoded.interval)
    }
}
