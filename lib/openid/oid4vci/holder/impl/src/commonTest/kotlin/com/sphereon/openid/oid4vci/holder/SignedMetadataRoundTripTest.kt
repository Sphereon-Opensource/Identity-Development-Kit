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

import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.holder.impl.SignedMetadataVerifier
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignedMetadataRoundTripTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    @Test
    fun metadataPayloadPreservesAllFieldsThroughJsonObject() =
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
            assertTrue(payloadJson is JsonObject)

            val roundTripped = json.decodeFromJsonElement(CredentialIssuerMetadata.serializer(), payloadJson)
            assertEquals(metadata.credentialIssuer, roundTripped.credentialIssuer)
            assertEquals(metadata.credentialEndpoint, roundTripped.credentialEndpoint)
            assertEquals(metadata.nonceEndpoint, roundTripped.nonceEndpoint)
            assertEquals(metadata.credentialConfigurationsSupported.size, roundTripped.credentialConfigurationsSupported.size)
            assertEquals("jwt_vc_json", roundTripped.credentialConfigurationsSupported["UniversityDegree"]?.format)
        }

    @Test
    fun signedMetadataFieldRoundTrips() =
        runTest {
            val metadataJson =
                """
                {
                  "credential_issuer": "https://issuer.example.com",
                  "credential_endpoint": "https://issuer.example.com/credential",
                  "credential_configurations_supported": {
                    "test": {"format": "jwt_vc_json"}
                  },
                  "signed_metadata": "eyJhbGciOiJFUzI1NiIsInR5cCI6Im9wZW5pZHZjaS1pc3N1ZXItbWV0YWRhdGErand0In0.eyJjcmVkZW50aWFsX2lzc3VlciI6Imh0dHBzOi8vaXNzdWVyLmV4YW1wbGUuY29tIn0.signature"
                }
                """.trimIndent()

            val metadata = json.decodeFromString<CredentialIssuerMetadata>(metadataJson)
            assertNotNull(metadata.signedMetadata)
            assertTrue(metadata.signedMetadata!!.startsWith("eyJ"))

            val reEncoded = json.encodeToString(CredentialIssuerMetadata.serializer(), metadata)
            val reParsed = json.decodeFromString<CredentialIssuerMetadata>(reEncoded)
            assertEquals(metadata.signedMetadata, reParsed.signedMetadata)
        }

    @Test
    fun metadataWithSignedFieldCanBeSetProgrammatically() =
        runTest {
            val metadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example.com",
                    credentialEndpoint = "https://issuer.example.com/credential",
                    credentialConfigurationsSupported =
                        mapOf(
                            "test" to CredentialConfigurationSupported(format = "jwt_vc_json"),
                        ),
                )

            val enriched = metadata.copy(signedMetadata = "eyJ.eyJ.sig")
            assertEquals("eyJ.eyJ.sig", enriched.signedMetadata)
            assertEquals(metadata.credentialIssuer, enriched.credentialIssuer)
        }

    @Test
    fun jwtDetectionAcceptsValidThreePartFormat() {
        assertTrue(SignedMetadataVerifier.isJwtResponse("eyJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJ0ZXN0In0.signature"))
        assertTrue(SignedMetadataVerifier.isJwtResponse("  eyJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJ0ZXN0In0.sig  "))
    }

    @Test
    fun jwtDetectionRejectsJsonObjects() {
        val verifier = SignedMetadataVerifier::isJwtResponse
        assertTrue(!verifier("""{"credential_issuer": "https://issuer.example.com"}"""))
        assertTrue(!verifier("{}"))
    }

    @Test
    fun jwtDetectionRejectsMalformedInputs() {
        val verifier = SignedMetadataVerifier::isJwtResponse
        assertTrue(!verifier(""))
        assertTrue(!verifier("not.a.jwt.four.parts"))
        assertTrue(!verifier("onlyonepart"))
    }

    // ── Signed metadata policy tests ──────────────────────────────────────────

    @Test
    fun metadataWithSignedMetadataFieldIsDetectable() {
        val metadataJson =
            """
            {
              "credential_issuer": "https://issuer.example.com",
              "credential_endpoint": "https://issuer.example.com/credential",
              "credential_configurations_supported": {"test": {"format": "jwt_vc_json"}},
              "signed_metadata": "eyJ.eyJ.sig"
            }
            """.trimIndent()
        val metadata = json.decodeFromString<CredentialIssuerMetadata>(metadataJson)
        assertNotNull(metadata.signedMetadata, "signed_metadata field should be parsed")
    }

    @Test
    fun metadataWithoutSignedMetadataFieldHasNullSignedMetadata() {
        val metadataJson =
            """
            {
              "credential_issuer": "https://issuer.example.com",
              "credential_endpoint": "https://issuer.example.com/credential",
              "credential_configurations_supported": {"test": {"format": "jwt_vc_json"}}
            }
            """.trimIndent()
        val metadata = json.decodeFromString<CredentialIssuerMetadata>(metadataJson)
        assertNull(metadata.signedMetadata, "signed_metadata should be null when absent")
    }

    @Test
    fun holderConfigDefaultDoesNotRequireSignedMetadata() {
        val config =
            object : Oid4vciHolderConfig {
                override val clientId: String? = null
                override val preferredFormat: String? = null
            }
        assertFalse(config.requireVerifiedSignedMetadata, "Default should not require signed metadata")
    }

    @Test
    fun holderConfigCanRequireSignedMetadata() {
        val config =
            object : Oid4vciHolderConfig {
                override val clientId: String? = null
                override val preferredFormat: String? = null
                override val requireVerifiedSignedMetadata: Boolean = true
            }
        assertTrue(config.requireVerifiedSignedMetadata, "Config should be able to require signed metadata")
    }
}
