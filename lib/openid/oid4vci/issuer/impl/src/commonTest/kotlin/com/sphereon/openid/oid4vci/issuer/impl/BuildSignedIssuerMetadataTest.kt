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

package com.sphereon.openid.oid4vci.issuer.impl

import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildSignedIssuerMetadataTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    @Test
    fun metadataSerializesToJsonObjectPreservingAllFields() =
        runTest {
            val metadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example.com",
                    credentialEndpoint = "https://issuer.example.com/credential",
                    nonceEndpoint = "https://issuer.example.com/nonce",
                    deferredCredentialEndpoint = "https://issuer.example.com/deferred",
                    notificationEndpoint = "https://issuer.example.com/notification",
                    credentialConfigurationsSupported =
                        mapOf(
                            "UniversityDegree" to CredentialConfigurationSupported(format = "jwt_vc_json", scope = "degree"),
                        ),
                )

            val payloadJson = json.encodeToJsonElement(CredentialIssuerMetadata.serializer(), metadata)
            assertTrue(payloadJson is JsonObject, "Encoded metadata must be a JsonObject for use as JWT payload")

            val obj = payloadJson.jsonObject
            assertEquals("https://issuer.example.com", obj["credential_issuer"]?.jsonPrimitive?.content)
            assertEquals("https://issuer.example.com/credential", obj["credential_endpoint"]?.jsonPrimitive?.content)
            assertEquals("https://issuer.example.com/nonce", obj["nonce_endpoint"]?.jsonPrimitive?.content)
            assertNotNull(obj["credential_configurations_supported"])
        }

    @Test
    fun metadataRoundTripsThroughJsonObjectPreservingValues() =
        runTest {
            val metadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example.com",
                    credentialEndpoint = "https://issuer.example.com/credential",
                    credentialConfigurationsSupported =
                        mapOf(
                            "UniversityDegree" to CredentialConfigurationSupported(format = "jwt_vc_json", scope = "degree"),
                            "DriverLicense" to CredentialConfigurationSupported(format = "mso_mdoc"),
                        ),
                )

            val encoded = json.encodeToJsonElement(CredentialIssuerMetadata.serializer(), metadata)
            val decoded = json.decodeFromJsonElement(CredentialIssuerMetadata.serializer(), encoded)

            assertEquals(metadata.credentialIssuer, decoded.credentialIssuer)
            assertEquals(metadata.credentialEndpoint, decoded.credentialEndpoint)
            assertEquals(2, decoded.credentialConfigurationsSupported.size)
            assertEquals("jwt_vc_json", decoded.credentialConfigurationsSupported["UniversityDegree"]?.format)
            assertEquals("degree", decoded.credentialConfigurationsSupported["UniversityDegree"]?.scope)
            assertEquals("mso_mdoc", decoded.credentialConfigurationsSupported["DriverLicense"]?.format)
        }

    @Test
    fun signedMetadataFieldCanBeSetOnMetadata() =
        runTest {
            val metadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example.com",
                    credentialEndpoint = "https://issuer.example.com/credential",
                    credentialConfigurationsSupported =
                        mapOf(
                            "TestCred" to CredentialConfigurationSupported(format = "jwt_vc_json"),
                        ),
                )

            assertNull(metadata.signedMetadata, "signed_metadata should be null by default")

            val enriched = metadata.copy(signedMetadata = "eyJhbGciOiJFUzI1NiJ9.eyJjcmVkZW50aWFsX2lzc3VlciI6Imh0dHBzOi8vaXNzdWVyLmV4YW1wbGUuY29tIn0.sig")
            assertNotNull(enriched.signedMetadata)
            assertTrue(enriched.signedMetadata!!.startsWith("eyJ"))
            assertEquals(metadata.credentialIssuer, enriched.credentialIssuer)
            assertEquals(metadata.credentialEndpoint, enriched.credentialEndpoint)
        }

    @Test
    fun signedMetadataFieldSerializesWithCorrectWireKey() =
        runTest {
            val jwtValue = "eyJhbGciOiJFUzI1NiJ9.eyJjcmVkZW50aWFsX2lzc3VlciI6Imh0dHBzOi8vaXNzdWVyLmV4YW1wbGUuY29tIn0.sig"
            val metadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example.com",
                    credentialEndpoint = "https://issuer.example.com/credential",
                    credentialConfigurationsSupported =
                        mapOf(
                            "TestCred" to CredentialConfigurationSupported(format = "jwt_vc_json"),
                        ),
                    signedMetadata = jwtValue,
                )

            val encoded = json.encodeToJsonElement(CredentialIssuerMetadata.serializer(), metadata).jsonObject
            // The wire key must be "signed_metadata" per OID4VCI 1.1 Section 13.2
            assertEquals(jwtValue, encoded["signed_metadata"]?.jsonPrimitive?.content)
        }
}
