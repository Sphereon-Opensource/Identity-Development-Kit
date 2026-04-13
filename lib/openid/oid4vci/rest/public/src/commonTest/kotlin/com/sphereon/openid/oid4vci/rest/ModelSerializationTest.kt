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

package com.sphereon.openid.oid4vci.rest

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelSerializationTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun createCredentialOfferInputMinimal() {
        val input =
            json.decodeFromString(
                CreateCredentialOfferInput.serializer(),
                """{"credential_configuration_ids":["PensionSdJwt"]}""",
            )
        assertEquals(listOf("PensionSdJwt"), input.credentialConfigurationIds)
        assertNull(input.correlationId)
        assertNull(input.issuerId)
        assertNull(input.grants)
        assertNull(input.qrCodeOptions)
        assertNull(input.callback)
    }

    @Test
    fun createCredentialOfferInputFull() {
        val jsonStr =
            """
            {
                "credential_configuration_ids": ["PensionSdJwt", "DriverLicenseMdoc"],
                "credential_subject_data": {"givenName": "John", "age": 30},
                "correlation_id": "corr-123",
                "issuer_id": "issuer-abc",
                "grants": {
                    "pre_authorized_code": {
                        "tx_code": {
                            "input_mode": "numeric",
                            "length": 4,
                            "description": "Check your email"
                        }
                    }
                },
                "scheme": "openid-credential-offer://",
                "qr_code": {"size": 300, "color_dark": "#333333"},
                "callback": {
                    "url": "https://example.com/webhook",
                    "statuses": ["credential_issued", "error"],
                    "include_issuance_data": true
                },
                "state": "my-state",
                "ttl_seconds": 300
            }
            """.trimIndent()

        val input = json.decodeFromString(CreateCredentialOfferInput.serializer(), jsonStr)
        assertEquals(2, input.credentialConfigurationIds.size)
        assertEquals("corr-123", input.correlationId)
        assertEquals("issuer-abc", input.issuerId)
        assertNotNull(input.grants?.preAuthorizedCode?.txCode)
        assertEquals(
            "numeric",
            input.grants!!
                .preAuthorizedCode!!
                .txCode!!
                .inputMode,
        )
        assertEquals(
            4,
            input.grants!!
                .preAuthorizedCode!!
                .txCode!!
                .length,
        )
        assertNotNull(input.qrCodeOptions)
        assertEquals(300, input.qrCodeOptions!!.size)
        assertNotNull(input.callback)
        assertEquals("https://example.com/webhook", input.callback!!.url)
        assertEquals(2, input.callback!!.statuses.size)
        assertEquals(true, input.callback!!.includeIssuanceData)
        assertEquals("my-state", input.state)
        assertEquals(300L, input.ttlSeconds)

        val subjectData = input.credentialSubjectData
        assertNotNull(subjectData)
        assertEquals(JsonPrimitive("John"), subjectData["givenName"])
    }

    @Test
    fun createCredentialOfferOutputSerialization() {
        val output =
            CreateCredentialOfferOutput(
                correlationId = "corr-123",
                offerUri = "openid-credential-offer://?credential_offer=...",
                statusUri = "https://issuer.example.com/oid4vci/backend/credential/offers/corr-123",
                qrUri = "data:image/png;base64,abc123",
                txCode = "1234",
            )

        val serialized = json.encodeToString(CreateCredentialOfferOutput.serializer(), output)
        assertTrue(serialized.contains("\"correlation_id\":\"corr-123\""))
        assertTrue(serialized.contains("\"offer_uri\":"))
        assertTrue(serialized.contains("\"status_uri\":"))
        assertTrue(serialized.contains("\"qr_uri\":"))
        assertTrue(serialized.contains("\"tx_code\":\"1234\""))
    }

    @Test
    fun getCredentialOfferStatusOutputSerialization() {
        val output =
            GetCredentialOfferStatusOutput(
                correlationId = "corr-123",
                status = CredentialOfferSessionStatus.CREDENTIAL_ISSUED,
                lastUpdated = 1700000000000L,
                issuanceData =
                    IssuanceData(
                        credentialConfigurationIds = listOf("PensionSdJwt"),
                        credentialIdentifiers = listOf("cred-001"),
                    ),
            )

        val serialized = json.encodeToString(GetCredentialOfferStatusOutput.serializer(), output)
        assertTrue(serialized.contains("\"correlation_id\":\"corr-123\""))
        assertTrue(serialized.contains("\"status\":\"credential_issued\""))
        assertTrue(serialized.contains("\"last_updated\":1700000000000"))
        assertTrue(serialized.contains("\"issuance_data\""))
        assertTrue(serialized.contains("\"credential_configuration_ids\""))
    }

    @Test
    fun statusEnumSerializesToSnakeCase() {
        for (status in CredentialOfferSessionStatus.entries) {
            val serialized = json.encodeToString(CredentialOfferSessionStatus.serializer(), status)
            // All values should serialize to lowercase snake_case
            assertTrue(serialized.all { it == '"' || it.isLowerCase() || it == '_' })
        }
    }

    @Test
    fun callbackStatusUpdateSerialization() {
        val update =
            CredentialOfferSessionStatusUpdate(
                correlationId = "corr-123",
                status = CredentialOfferSessionStatus.CREDENTIAL_ISSUED,
                updatedAt = 1700000000000L,
            )

        val serialized = json.encodeToString(CredentialOfferSessionStatusUpdate.serializer(), update)
        assertTrue(serialized.contains("\"correlation_id\":\"corr-123\""))
        assertTrue(serialized.contains("\"status\":\"credential_issued\""))
        assertTrue(serialized.contains("\"updated_at\":1700000000000"))
    }

    @Test
    fun errorStatusOutputSerialization() {
        val output =
            GetCredentialOfferStatusOutput(
                correlationId = "corr-err",
                status = CredentialOfferSessionStatus.ERROR,
                lastUpdated = 1700000000000L,
                error =
                    com.sphereon.openid.oid4vc.common.SessionError(
                        code = "issuance_error",
                        message = "Credential issuance failed",
                    ),
            )

        val serialized = json.encodeToString(GetCredentialOfferStatusOutput.serializer(), output)
        assertTrue(serialized.contains("\"status\":\"error\""))
        assertTrue(serialized.contains("\"issuance_error\""))
    }

    @Test
    fun unknownFieldsIgnoredOnDeserialization() {
        val input =
            json.decodeFromString(
                CreateCredentialOfferInput.serializer(),
                """{"credential_configuration_ids":["Test"],"unknown_field":"ignored"}""",
            )
        assertEquals(listOf("Test"), input.credentialConfigurationIds)
    }
}
