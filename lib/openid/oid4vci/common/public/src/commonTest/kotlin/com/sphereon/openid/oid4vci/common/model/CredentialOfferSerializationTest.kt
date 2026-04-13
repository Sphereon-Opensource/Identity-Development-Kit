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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CredentialOfferSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun authorizationCodeGrantRoundTrip() {
        val offer =
            CredentialOffer(
                credentialIssuer = "https://issuer.example.com",
                credentialConfigurationIds = listOf("UniversityDegree", "EmployeeID"),
                grants =
                    CredentialOfferGrants(
                        authorizationCode =
                            AuthorizationCodeOfferGrant(
                                issuerState = "eyJhbGciOiJSU0EtOkFQI",
                                authorizationServer = "https://auth.example.com",
                            ),
                    ),
            )

        val encoded = json.encodeToString(offer)
        val decoded = json.decodeFromString<CredentialOffer>(encoded)

        assertEquals(offer, decoded)
        assertEquals("https://issuer.example.com", decoded.credentialIssuer)
        assertEquals(2, decoded.credentialConfigurationIds.size)
        assertNotNull(decoded.grants?.authorizationCode)
        assertEquals("eyJhbGciOiJSU0EtOkFQI", decoded.grants?.authorizationCode?.issuerState)
        assertEquals("https://auth.example.com", decoded.grants?.authorizationCode?.authorizationServer)
        assertNull(decoded.grants?.preAuthorizedCode)
    }

    @Test
    fun preAuthorizedCodeGrantRoundTrip() {
        val offer =
            CredentialOffer(
                credentialIssuer = "https://issuer.example.com",
                credentialConfigurationIds = listOf("UniversityDegree"),
                grants =
                    CredentialOfferGrants(
                        preAuthorizedCode =
                            PreAuthorizedCodeOfferGrant(
                                preAuthorizedCode = "adhjhdjajkdkhjhdj",
                                txCode =
                                    TxCodeConfig(
                                        inputMode = "numeric",
                                        length = 6,
                                        description = "Please enter the 6-digit code sent to your email",
                                    ),
                                interval = 5,
                                authorizationServer = "https://auth.example.com",
                            ),
                    ),
            )

        val encoded = json.encodeToString(offer)
        val decoded = json.decodeFromString<CredentialOffer>(encoded)

        assertEquals(offer, decoded)
        assertNull(decoded.grants?.authorizationCode)
        assertNotNull(decoded.grants?.preAuthorizedCode)
        assertEquals("adhjhdjajkdkhjhdj", decoded.grants?.preAuthorizedCode?.preAuthorizedCode)
        assertEquals(
            "numeric",
            decoded.grants
                ?.preAuthorizedCode
                ?.txCode
                ?.inputMode,
        )
        assertEquals(
            6,
            decoded.grants
                ?.preAuthorizedCode
                ?.txCode
                ?.length,
        )
        assertEquals(
            "Please enter the 6-digit code sent to your email",
            decoded.grants
                ?.preAuthorizedCode
                ?.txCode
                ?.description,
        )
        assertEquals(5, decoded.grants?.preAuthorizedCode?.interval)
        assertEquals("https://auth.example.com", decoded.grants?.preAuthorizedCode?.authorizationServer)
    }

    @Test
    fun bothGrantsRoundTrip() {
        val offer =
            CredentialOffer(
                credentialIssuer = "https://issuer.example.com",
                credentialConfigurationIds = listOf("UniversityDegree"),
                grants =
                    CredentialOfferGrants(
                        authorizationCode =
                            AuthorizationCodeOfferGrant(
                                issuerState = "state123",
                            ),
                        preAuthorizedCode =
                            PreAuthorizedCodeOfferGrant(
                                preAuthorizedCode = "code456",
                            ),
                    ),
            )

        val encoded = json.encodeToString(offer)
        val decoded = json.decodeFromString<CredentialOffer>(encoded)

        assertEquals(offer, decoded)
        assertNotNull(decoded.grants?.authorizationCode)
        assertNotNull(decoded.grants?.preAuthorizedCode)
        assertEquals("state123", decoded.grants?.authorizationCode?.issuerState)
        assertEquals("code456", decoded.grants?.preAuthorizedCode?.preAuthorizedCode)
    }

    @Test
    fun minimalOfferRoundTrip() {
        val offer =
            CredentialOffer(
                credentialIssuer = "https://issuer.example.com",
                credentialConfigurationIds = listOf("TestCredential"),
            )

        val encoded = json.encodeToString(offer)
        val decoded = json.decodeFromString<CredentialOffer>(encoded)

        assertEquals(offer, decoded)
        assertNull(decoded.grants)
    }

    @Test
    fun deserializesPreAuthWithTxCode() {
        val jsonString =
            """
            {
                "credential_issuer": "https://credential-issuer.example.com",
                "credential_configuration_ids": ["UniversityDegreeCredential"],
                "grants": {
                    "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                        "pre-authorized_code": "oaKazRN8I0IbtZ0C7JuMn5",
                        "tx_code": {
                            "input_mode": "text",
                            "length": 8,
                            "description": "Please enter the code from your invitation letter"
                        },
                        "interval": 10
                    }
                }
            }
            """.trimIndent()

        val decoded = json.decodeFromString<CredentialOffer>(jsonString)

        assertEquals("https://credential-issuer.example.com", decoded.credentialIssuer)
        assertEquals(listOf("UniversityDegreeCredential"), decoded.credentialConfigurationIds)
        assertNull(decoded.grants?.authorizationCode)

        val preAuth = decoded.grants?.preAuthorizedCode
        assertNotNull(preAuth)
        assertEquals("oaKazRN8I0IbtZ0C7JuMn5", preAuth.preAuthorizedCode)
        assertEquals("text", preAuth.txCode?.inputMode)
        assertEquals(8, preAuth.txCode?.length)
        assertEquals("Please enter the code from your invitation letter", preAuth.txCode?.description)
        assertEquals(10, preAuth.interval)

        // Verify round-trip
        val reEncoded = json.encodeToString(decoded)
        val reDecoded = json.decodeFromString<CredentialOffer>(reEncoded)
        assertEquals(decoded, reDecoded)
    }
}
