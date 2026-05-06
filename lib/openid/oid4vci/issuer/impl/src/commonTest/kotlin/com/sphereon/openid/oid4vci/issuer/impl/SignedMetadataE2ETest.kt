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

import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vc.common.LogoProperties
import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.common.model.BatchCredentialIssuance
import com.sphereon.openid.oid4vci.common.model.ClaimDisplay
import com.sphereon.openid.oid4vci.common.model.CredentialClaim
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialDefinition
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialResponseEncryption
import com.sphereon.openid.oid4vci.common.model.ProofTypeSupported
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E2E test for the signed issuer metadata flow.
 *
 * Real crypto signing requires a full DI component (AppGraph with SoftwareKmsProvider),
 * which is only available in lib/crypto/core/impl tests. This module's test classpath
 * has no DI or KMS infrastructure, so these tests exercise the serialization path that
 * BuildSignedIssuerMetadataCommandImpl uses to produce the JWT payload:
 *
 *   CredentialIssuerMetadata  →  Oid4vciJson.lenientNoDefaults.encodeToJsonElement(...)
 *     .jsonObject  →  [passed as JWT payload]  →  deserialize back  →  assert equality
 *
 * This validates that every field survives the JWT payload encoding path intact,
 * including wire-format key names, optional fields, display properties, and additional metadata.
 */
class SignedMetadataE2ETest {
    // Same Json instance used by BuildSignedIssuerMetadataCommandImpl
    private val json: Json = Oid4vciJson.lenientNoDefaults

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun encodeToJsonObject(metadata: CredentialIssuerMetadata): JsonObject = json.encodeToJsonElement(CredentialIssuerMetadata.serializer(), metadata).jsonObject

    private fun decodeFromJsonObject(obj: JsonObject): CredentialIssuerMetadata = json.decodeFromJsonElement(CredentialIssuerMetadata.serializer(), obj)

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun minimalMetadataRoundTripsThroughJwtPayloadEncoding() =
        runTest {
            val metadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example.com",
                    credentialEndpoint = "https://issuer.example.com/credential",
                    credentialConfigurationsSupported =
                        mapOf(
                            "UniversityDegree" to CredentialConfigurationSupported(format = "jwt_vc_json"),
                        ),
                )

            val payload = encodeToJsonObject(metadata)
            val decoded = decodeFromJsonObject(payload)

            assertEquals(metadata.credentialIssuer, decoded.credentialIssuer)
            assertEquals(metadata.credentialEndpoint, decoded.credentialEndpoint)
            assertNull(decoded.nonceEndpoint, "nonceEndpoint should be absent when null")
            assertNull(decoded.deferredCredentialEndpoint)
            assertEquals(1, decoded.credentialConfigurationsSupported.size)
            assertEquals("jwt_vc_json", decoded.credentialConfigurationsSupported["UniversityDegree"]?.format)
        }

    @Test
    fun allOptionalEndpointsRoundTripCorrectly() =
        runTest {
            val metadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example.com",
                    credentialEndpoint = "https://issuer.example.com/credential",
                    nonceEndpoint = "https://issuer.example.com/nonce",
                    deferredCredentialEndpoint = "https://issuer.example.com/deferred",
                    notificationEndpoint = "https://issuer.example.com/notification",
                    batchCredentialEndpoint = "https://issuer.example.com/batch",
                    authorizationServers = listOf("https://as.example.com"),
                    credentialConfigurationsSupported =
                        mapOf(
                            "SomeCredential" to CredentialConfigurationSupported(format = "dc+sd-jwt"),
                        ),
                )

            val payload = encodeToJsonObject(metadata)

            // Verify wire keys match OID4VCI spec (Section 12.2.4)
            assertEquals("https://issuer.example.com", payload["credential_issuer"]?.jsonPrimitive?.content)
            assertEquals("https://issuer.example.com/credential", payload["credential_endpoint"]?.jsonPrimitive?.content)
            assertEquals("https://issuer.example.com/nonce", payload["nonce_endpoint"]?.jsonPrimitive?.content)
            assertEquals("https://issuer.example.com/deferred", payload["deferred_credential_endpoint"]?.jsonPrimitive?.content)
            assertEquals("https://issuer.example.com/notification", payload["notification_endpoint"]?.jsonPrimitive?.content)
            assertEquals("https://issuer.example.com/batch", payload["batch_credential_endpoint"]?.jsonPrimitive?.content)
            val authServers = payload["authorization_servers"]?.jsonArray
            assertNotNull(authServers)
            assertEquals("https://as.example.com", authServers[0].jsonPrimitive.content)

            val decoded = decodeFromJsonObject(payload)
            assertEquals(metadata.credentialIssuer, decoded.credentialIssuer)
            assertEquals(metadata.nonceEndpoint, decoded.nonceEndpoint)
            assertEquals(metadata.deferredCredentialEndpoint, decoded.deferredCredentialEndpoint)
            assertEquals(metadata.notificationEndpoint, decoded.notificationEndpoint)
            assertEquals(metadata.batchCredentialEndpoint, decoded.batchCredentialEndpoint)
            assertEquals(listOf("https://as.example.com"), decoded.authorizationServers)
        }

    @Test
    fun multipleCredentialConfigurationsRoundTripCorrectly() =
        runTest {
            val metadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example.com",
                    credentialEndpoint = "https://issuer.example.com/credential",
                    credentialConfigurationsSupported =
                        mapOf(
                            "UniversityDegree" to
                                CredentialConfigurationSupported(
                                    format = "jwt_vc_json",
                                    scope = "degree",
                                    cryptographicBindingMethodsSupported = listOf("did:key", "did:jwk"),
                                    credentialSigningAlgValuesSupported = listOf(JsonPrimitive("ES256"), JsonPrimitive("ES384")),
                                    credentialDefinition =
                                        CredentialDefinition(
                                            type = listOf("VerifiableCredential", "UniversityDegreeCredential"),
                                            context = listOf("https://www.w3.org/2018/credentials/v1"),
                                        ),
                                    display =
                                        listOf(
                                            DisplayProperties(
                                                name = "University Degree",
                                                locale = "en-US",
                                                logo = LogoProperties(uri = "https://example.com/logo.png", altText = "Degree Logo"),
                                                backgroundColor = "#12107c",
                                                textColor = "#FFFFFF",
                                            ),
                                            DisplayProperties(name = "Hochschulabschluss", locale = "de-DE"),
                                        ),
                                    proofTypesSupported =
                                        mapOf(
                                            "jwt" to
                                                ProofTypeSupported(
                                                    proofSigningAlgValuesSupported = listOf("ES256"),
                                                ),
                                        ),
                                ),
                            "DriverLicense" to
                                CredentialConfigurationSupported(
                                    format = "mso_mdoc",
                                    doctype = "org.iso.18013.5.1.mDL",
                                    scope = "driving_license",
                                    claims =
                                        listOf(
                                            CredentialClaim(
                                                path = listOf("org.iso.18013.5.1", "given_name"),
                                                mandatory = true,
                                                valueType = "string",
                                                display = listOf(ClaimDisplay(name = "Given Name", locale = "en-US")),
                                            ),
                                            CredentialClaim(
                                                path = listOf("org.iso.18013.5.1", "birth_date"),
                                                mandatory = true,
                                                valueType = "full-date",
                                            ),
                                        ),
                                ),
                            "PID" to
                                CredentialConfigurationSupported(
                                    format = "dc+sd-jwt",
                                    vct = "https://credentials.example.com/identity_credential",
                                    scope = "pid",
                                ),
                        ),
                )

            val payload = encodeToJsonObject(metadata)
            val decoded = decodeFromJsonObject(payload)

            assertEquals(3, decoded.credentialConfigurationsSupported.size)

            // University Degree assertions
            val degree = decoded.credentialConfigurationsSupported["UniversityDegree"]
            assertNotNull(degree)
            assertEquals("jwt_vc_json", degree.format)
            assertEquals("degree", degree.scope)
            assertEquals(listOf("did:key", "did:jwk"), degree.cryptographicBindingMethodsSupported)
            assertEquals(
                listOf(JsonPrimitive("ES256"), JsonPrimitive("ES384")),
                degree.credentialSigningAlgValuesSupported,
            )
            val degreeDefinition = degree.credentialDefinition
            assertNotNull(degreeDefinition)
            assertEquals(listOf("VerifiableCredential", "UniversityDegreeCredential"), degreeDefinition.type)
            assertEquals(listOf("https://www.w3.org/2018/credentials/v1"), degreeDefinition.context)
            val degreeDisplay = degree.display
            assertNotNull(degreeDisplay)
            assertEquals(2, degreeDisplay.size)
            assertEquals("University Degree", degreeDisplay[0].name)
            assertEquals("en-US", degreeDisplay[0].locale)
            assertEquals("https://example.com/logo.png", degreeDisplay[0].logo?.uri)
            assertEquals("Degree Logo", degreeDisplay[0].logo?.altText)
            assertEquals("#12107c", degreeDisplay[0].backgroundColor)
            assertEquals("#FFFFFF", degreeDisplay[0].textColor)
            assertEquals("Hochschulabschluss", degreeDisplay[1].name)
            assertEquals("de-DE", degreeDisplay[1].locale)
            val jwtProof = degree.proofTypesSupported?.get("jwt")
            assertNotNull(jwtProof)
            assertEquals(listOf("ES256"), jwtProof.proofSigningAlgValuesSupported)

            // Driver License (mso_mdoc) assertions
            val license = decoded.credentialConfigurationsSupported["DriverLicense"]
            assertNotNull(license)
            assertEquals("mso_mdoc", license.format)
            assertEquals("org.iso.18013.5.1.mDL", license.doctype)
            assertEquals("driving_license", license.scope)
            val givenName = license.claims?.firstOrNull { it.path == listOf("org.iso.18013.5.1", "given_name") }
            assertNotNull(givenName)
            assertTrue(givenName.mandatory == true)
            assertEquals("string", givenName.valueType)
            assertEquals("Given Name", givenName.display?.firstOrNull()?.name)

            // PID (dc+sd-jwt) assertions
            val pid = decoded.credentialConfigurationsSupported["PID"]
            assertNotNull(pid)
            assertEquals("dc+sd-jwt", pid.format)
            assertEquals("https://credentials.example.com/identity_credential", pid.vct)
            assertEquals("pid", pid.scope)
        }

    @Test
    fun issuerDisplayPropertiesRoundTripCorrectly() =
        runTest {
            val metadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example.com",
                    credentialEndpoint = "https://issuer.example.com/credential",
                    credentialConfigurationsSupported =
                        mapOf(
                            "SomeCred" to CredentialConfigurationSupported(format = "jwt_vc_json"),
                        ),
                    display =
                        listOf(
                            DisplayProperties(
                                name = "Example University",
                                locale = "en-US",
                                logo = LogoProperties(uri = "https://university.example.com/logo.svg"),
                            ),
                            DisplayProperties(name = "Beispiel Universität", locale = "de-DE"),
                        ),
                )

            val payload = encodeToJsonObject(metadata)
            val displayArr = payload["display"]?.jsonArray
            assertNotNull(displayArr, "display must be present in the JWT payload JsonObject")
            assertEquals(2, displayArr.size)

            val decoded = decodeFromJsonObject(payload)
            assertNotNull(decoded.display)
            assertEquals(2, decoded.display!!.size)
            assertEquals("Example University", decoded.display!![0].name)
            assertEquals("en-US", decoded.display!![0].locale)
            assertEquals("https://university.example.com/logo.svg", decoded.display!![0].logo?.uri)
            assertEquals("Beispiel Universität", decoded.display!![1].name)
        }

    @Test
    fun signedMetadataFieldSurvivesJwtPayloadRoundTrip() =
        runTest {
            // Simulate the full BuildSignedIssuerMetadataCommandImpl flow:
            // 1. metadata → JsonObject (JWT payload)
            // 2. JWT compact is produced externally (mocked here as a placeholder)
            // 3. The resulting JWT is stored in signed_metadata
            // 4. The enriched metadata is serialized again → JsonObject → deserialized → assert

            val originalMetadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example.com",
                    credentialEndpoint = "https://issuer.example.com/credential",
                    credentialConfigurationsSupported =
                        mapOf(
                            "TestCred" to CredentialConfigurationSupported(format = "jwt_vc_json"),
                        ),
                )

            // Step 1: encode original metadata as JWT payload
            val jwtPayload = encodeToJsonObject(originalMetadata)
            assertNull(jwtPayload["signed_metadata"], "signed_metadata must not be present in the JWT payload itself")

            // Step 2: simulate external JWT production (real flow uses CreateJwsCompactCommand)
            val simulatedJwt =
                "eyJhbGciOiJFUzI1NiIsInR5cCI6Im9wZW5pZHZjaS1pc3N1ZXItbWV0YWRhdGErand0In0" +
                    ".eyJjcmVkZW50aWFsX2lzc3VlciI6Imh0dHBzOi8vaXNzdWVyLmV4YW1wbGUuY29tIn0" +
                    ".SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

            // Step 3: enrich the metadata with the signed_metadata JWT
            val enrichedMetadata = originalMetadata.copy(signedMetadata = simulatedJwt)

            // Step 4: encode enriched metadata and verify signed_metadata wire key
            val enrichedPayload = encodeToJsonObject(enrichedMetadata)
            assertEquals(
                simulatedJwt,
                enrichedPayload["signed_metadata"]?.jsonPrimitive?.content,
                "signed_metadata wire key must match OID4VCI 1.1 Section 13.2",
            )

            // Step 5: round-trip through the JsonObject
            val decoded = decodeFromJsonObject(enrichedPayload)
            assertEquals(enrichedMetadata.credentialIssuer, decoded.credentialIssuer)
            assertEquals(enrichedMetadata.credentialEndpoint, decoded.credentialEndpoint)
            assertEquals(simulatedJwt, decoded.signedMetadata)
            assertEquals(enrichedMetadata.credentialConfigurationsSupported.keys, decoded.credentialConfigurationsSupported.keys)
        }

    @Test
    fun additionalMetadataPassthroughSurvivesRoundTrip() =
        runTest {
            // Verify that unknown/extension metadata fields survive the encoding path
            val metadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example.com",
                    credentialEndpoint = "https://issuer.example.com/credential",
                    credentialConfigurationsSupported =
                        mapOf(
                            "SomeCred" to CredentialConfigurationSupported(format = "jwt_vc_json"),
                        ),
                    additionalMetadata =
                        mapOf(
                            "x_custom_feature" to JsonPrimitive("enabled"),
                            "x_issuer_type" to JsonPrimitive("university"),
                        ),
                )

            val payload = encodeToJsonObject(metadata)
            assertEquals("enabled", payload["x_custom_feature"]?.jsonPrimitive?.content)
            assertEquals("university", payload["x_issuer_type"]?.jsonPrimitive?.content)

            val decoded = decodeFromJsonObject(payload)
            assertEquals("enabled", (decoded.additionalMetadata["x_custom_feature"] as? JsonPrimitive)?.content)
            assertEquals("university", (decoded.additionalMetadata["x_issuer_type"] as? JsonPrimitive)?.content)
        }

    @Test
    fun credentialResponseEncryptionRoundTripsCorrectly() =
        runTest {
            val metadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example.com",
                    credentialEndpoint = "https://issuer.example.com/credential",
                    credentialConfigurationsSupported =
                        mapOf(
                            "EncryptedCred" to CredentialConfigurationSupported(format = "jwt_vc_json"),
                        ),
                    credentialResponseEncryption =
                        MetadataCredentialResponseEncryption(
                            algValuesSupported = listOf("RSA-OAEP", "ECDH-ES"),
                            encValuesSupported = listOf("A128GCM", "A256GCM"),
                            encryptionRequired = true,
                        ),
                )

            val payload = encodeToJsonObject(metadata)
            val encObj = payload["credential_response_encryption"]?.jsonObject
            assertNotNull(encObj, "credential_response_encryption must be present in JWT payload")

            val decoded = decodeFromJsonObject(payload)
            val enc = decoded.credentialResponseEncryption
            assertNotNull(enc)
            assertEquals(listOf("RSA-OAEP", "ECDH-ES"), enc.algValuesSupported)
            assertEquals(listOf("A128GCM", "A256GCM"), enc.encValuesSupported)
            assertTrue(enc.encryptionRequired)
        }

    @Test
    fun batchCredentialIssuanceRoundTripsCorrectly() =
        runTest {
            val metadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example.com",
                    credentialEndpoint = "https://issuer.example.com/credential",
                    credentialConfigurationsSupported =
                        mapOf(
                            "BatchCred" to CredentialConfigurationSupported(format = "jwt_vc_json"),
                        ),
                    batchCredentialIssuance = BatchCredentialIssuance(batchSize = 10),
                )

            val payload = encodeToJsonObject(metadata)
            val batchObj = payload["batch_credential_issuance"]?.jsonObject
            assertNotNull(batchObj)

            val decoded = decodeFromJsonObject(payload)
            assertEquals(10, decoded.batchCredentialIssuance?.batchSize)
        }

    @Test
    fun lenientNoDefaultsJsonDoesNotEncodeNullFields() =
        runTest {
            // BuildSignedIssuerMetadataCommandImpl uses Oid4vciJson.lenientNoDefaults.
            // Verify that null/default fields are absent from the JWT payload,
            // keeping it compact and spec-compliant.
            val metadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example.com",
                    credentialEndpoint = "https://issuer.example.com/credential",
                    credentialConfigurationsSupported =
                        mapOf(
                            "MinimalCred" to CredentialConfigurationSupported(format = "mso_mdoc"),
                        ),
                )

            val payload = encodeToJsonObject(metadata)

            // Optional null fields must be absent (compact JWT payload)
            assertNull(payload["nonce_endpoint"], "null fields must be omitted with lenientNoDefaults")
            assertNull(payload["deferred_credential_endpoint"])
            assertNull(payload["notification_endpoint"])
            assertNull(payload["batch_credential_endpoint"])
            assertNull(payload["authorization_servers"])
            assertNull(payload["signed_metadata"])
            assertNull(payload["display"])
            assertNull(payload["credential_response_encryption"])
            assertNull(payload["credential_request_encryption"])
            assertNull(payload["batch_credential_issuance"])

            // Required fields must always be present
            assertNotNull(payload["credential_issuer"])
            assertNotNull(payload["credential_endpoint"])
            assertNotNull(payload["credential_configurations_supported"])
        }
}
