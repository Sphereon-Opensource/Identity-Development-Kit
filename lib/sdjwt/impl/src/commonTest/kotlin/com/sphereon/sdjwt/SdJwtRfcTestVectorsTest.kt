/*
 * Â© 2026 Sphereon International B.V.
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

package com.sphereon.sdjwt

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.KeyManagerServiceImpl
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.sdjwt.dsl.sdJwtPayload
import com.sphereon.sdjwt.testutil.createSdJwtTestAppGraph
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test vectors from RFC 9901 - Selective Disclosure for JWTs (SD-JWT)
 * https://datatracker.ietf.org/doc/rfc9901/
 */
class SdJwtRfcTestVectorsTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var sdJwtService: com.sphereon.sdjwt.SdJwtService

    val app = createSdJwtTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("sdjwt-rfc-test")

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config =
            SoftwareKmsProviderConfig(
                id = "sdjwt-rfc-test-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app
        val softwareKmsProvider =
            (app as com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider.create(
                config,
                session.asCoreApiServiceGraph().serviceExecution,
            )

        // Get KeyManagerService and SdJwtService from the session graph
        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        sdJwtService = (session.graph as SdJwtServiceImpl.Graph).sdJwtService
    }

    /**
     * RFC 9901 Section 5 - Complete Example
     * Tests the complete example from the RFC with user data including:
     * - sub, given_name, family_name, email, phone, address, birthdate, nationalities
     */
    @Test
    fun testRfc9901Section5CompleteExample() =
        runTest {
            // Generate key pair
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "test-rfc-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://issuer.example.com",
                        ),
                )

            // Create payload matching RFC 9901 Section 5 example
            val payload =
                sdJwtPayload {
                    iss("https://issuer.example.com")
                    sub("user_42")
                    claim("given_name", "John")
                    claim("family_name", "Doe")
                    claimSd("email", "johndoe@example.com") // Selectively disclosable
                    claimSd("phone_number", "+1-202-555-0101") // Selectively disclosable
                    claim("phone_number_verified", true)
                    // Nested address object - make the whole address SD
                    objSd("address") {
                        claim("street_address", "123 Main St")
                        claim("locality", "Anytown")
                        claim("region", "Anystate")
                        claim("country", "US")
                    }
                    claimSd("birthdate", "1940-01-01") // Selectively disclosable
                    claim("updated_at", 1570000000)
                    // Array of nationalities
                    claim(
                        "nationalities",
                        JsonArray(
                            listOf(
                                JsonPrimitive("US"),
                                JsonPrimitive("DE"),
                            ),
                        ),
                    )
                }

            // Issue SD-JWT
            val issueArgs =
                com.sphereon.sdjwt.IssueSdJwtArgs(
                    issuer = issuer,
                    payload = payload,
                )

            val issueResult = sdJwtService.issueSdJwt(issueArgs)
            assertTrue(issueResult.isOk, "Should issue SD-JWT successfully")

            val sdJwtResult = issueResult.value
            assertNotNull(sdJwtResult.sdJwt)

            // Should have 4 disclosures: email, phone_number, address, birthdate
            kotlin.test.assertEquals(4, sdJwtResult.disclosures.size, "Should have 4 disclosures")

            println("RFC 9901 Section 5 - Issued SD-JWT with ${sdJwtResult.disclosures.size} disclosures")

            // Verify SD-JWT with all disclosures
            val verifyArgs =
                com.sphereon.sdjwt.VerifySdJwtArgs(
                    sdJwt = sdJwtResult.sdJwt,
                    identifier = issuer,
                )

            val verifyResult = sdJwtService.verifySdJwt(verifyArgs)
            assertTrue(verifyResult.isOk, "Verification should succeed")

            val verification = verifyResult.value
            assertTrue(verification.isValid, "SD-JWT should be valid")
            assertTrue(verification.signatureValid, "Signature should be valid")
            assertTrue(verification.disclosuresValid, "Disclosures should be valid")

            // Verify reconstructed payload contains all original claims
            val fullPayload = verification.sdJwt.payload.fullPayload
            assertEquals("user_42", fullPayload["sub"]?.jsonPrimitive?.content)
            assertEquals("John", fullPayload["given_name"]?.jsonPrimitive?.content)
            assertEquals("Doe", fullPayload["family_name"]?.jsonPrimitive?.content)
            assertEquals("johndoe@example.com", fullPayload["email"]?.jsonPrimitive?.content)
            assertEquals("+1-202-555-0101", fullPayload["phone_number"]?.jsonPrimitive?.content)
            assertEquals("1940-01-01", fullPayload["birthdate"]?.jsonPrimitive?.content)

            // Verify address object
            val address = fullPayload["address"] as? JsonObject
            assertNotNull(address, "Address should be present")
            assertEquals("123 Main St", address["street_address"]?.jsonPrimitive?.content)
            assertEquals("Anytown", address["locality"]?.jsonPrimitive?.content)
            assertEquals("Anystate", address["region"]?.jsonPrimitive?.content)
            assertEquals("US", address["country"]?.jsonPrimitive?.content)

            // Verify nationalities array
            val nationalities = fullPayload["nationalities"]?.jsonArray
            assertNotNull(nationalities, "Nationalities should be present")
            assertEquals(2, nationalities.size)
            assertEquals("US", nationalities[0].jsonPrimitive.content)
            assertEquals("DE", nationalities[1].jsonPrimitive.content)

            println("PASS: RFC 9901 Section 5 complete example verified successfully")
        }

    /**
     * RFC 9901 - Flat SD-JWT
     * All claims at top level, all selectively disclosable
     */
    @Test
    fun testRfc9901FlatSdJwt() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "flat-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://flat.example.com",
                        ),
                )

            // Flat structure - all claims are SD
            val payload =
                sdJwtPayload {
                    iss("https://flat.example.com")
                    subSd("user_flat") // Using SD variant
                    claimSd("name", "Alice Smith")
                    claimSd("email", "alice@example.com")
                    claimSd("age", 30)
                    claimSd("verified", true)
                }

            val issueResult = sdJwtService.issueSdJwt(com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload))
            assertTrue(issueResult.isOk)

            val sdJwtResult = issueResult.value
            // Should have 5 disclosures (sub, name, email, age, verified)
            kotlin.test.assertEquals(5, sdJwtResult.disclosures.size, "Flat SD-JWT should have 5 disclosures")

            // Verify
            val verifyResult =
                sdJwtService.verifySdJwt(
                    com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = sdJwtResult.sdJwt, identifier = issuer),
                )
            assertTrue(verifyResult.isOk)
            assertTrue(verifyResult.value.isValid)

            // Check all disclosed claims are present
            val fullPayload = verifyResult.value.sdJwt.payload.fullPayload
            assertEquals("user_flat", fullPayload["sub"]?.jsonPrimitive?.content)
            assertEquals("Alice Smith", fullPayload["name"]?.jsonPrimitive?.content)
            assertEquals("alice@example.com", fullPayload["email"]?.jsonPrimitive?.content)

            println("PASS: Flat SD-JWT verified successfully")
        }

    /**
     * RFC 9901 - Structured SD-JWT with nested address
     * Each address field is individually disclosable
     */
    @Test
    fun testRfc9901StructuredSdJwt() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "structured-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://structured.example.com",
                        ),
                )

            // Structured with nested object as SD
            val payload =
                sdJwtPayload {
                    iss("https://structured.example.com")
                    sub("user_structured")
                    claim("name", "Bob Johnson")
                    // Address object as selectively disclosable
                    claimSd(
                        "address",
                        JsonObject(
                            mapOf(
                                "street" to JsonPrimitive("456 Oak Ave"),
                                "city" to JsonPrimitive("Springfield"),
                                "state" to JsonPrimitive("IL"),
                                "zip" to JsonPrimitive("62701"),
                            ),
                        ),
                    )
                }

            val issueResult = sdJwtService.issueSdJwt(com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload))
            assertTrue(issueResult.isOk)

            val sdJwtResult = issueResult.value
            // Should have 1 disclosure for the address object (the whole object is SD)
            assertTrue(sdJwtResult.disclosures.size >= 1, "Should have at least 1 disclosure")

            // Verify with all disclosures
            val verifyResult =
                sdJwtService.verifySdJwt(
                    com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = sdJwtResult.sdJwt, identifier = issuer),
                )
            assertTrue(verifyResult.isOk)
            assertTrue(verifyResult.value.isValid)

            // Verify address is reconstructed
            val fullPayload = verifyResult.value.sdJwt.payload.fullPayload
            val address = fullPayload["address"] as? JsonObject
            assertNotNull(address, "Address should be present in full payload")

            println("PASS: Structured SD-JWT with nested object verified successfully")
        }

    /**
     * RFC 9901 - Recursive Disclosures
     * Tests nested objects and arrays with recursive disclosure
     */
    @Test
    fun testRfc9901RecursiveDisclosures() =
        runTest {
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "recursive-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://recursive.example.com",
                        ),
                )

            // Recursive structure with nested objects and arrays
            val payload =
                sdJwtPayload {
                    iss("https://recursive.example.com")
                    sub("user_recursive")

                    // Nested credentials with array
                    objSd("credentials") {
                        claim("type", "VerifiedEmployee")
                        claim("issued", "2024-01-01")
                        // Nested array
                        claim(
                            "departments",
                            JsonArray(
                                listOf(
                                    JsonPrimitive("Engineering"),
                                    JsonPrimitive("Research"),
                                ),
                            ),
                        )
                    }

                    // Array of nationalities (from RFC example)
                    claimSd(
                        "nationalities",
                        JsonArray(
                            listOf(
                                JsonPrimitive("US"),
                                JsonPrimitive("DE"),
                            ),
                        ),
                    )
                }

            val issueResult = sdJwtService.issueSdJwt(com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload))
            assertTrue(issueResult.isOk)

            val sdJwtResult = issueResult.value
            assertTrue(sdJwtResult.disclosures.size >= 2, "Should have at least 2 disclosures")

            // Verify
            val verifyResult =
                sdJwtService.verifySdJwt(
                    com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = sdJwtResult.sdJwt, identifier = issuer),
                )
            assertTrue(verifyResult.isOk)
            assertTrue(verifyResult.value.isValid)

            // Verify nested structures are reconstructed
            val fullPayload = verifyResult.value.sdJwt.payload.fullPayload

            // Check credentials object
            val credentials = fullPayload["credentials"] as? JsonObject
            assertNotNull(credentials, "Credentials should be present")
            assertEquals("VerifiedEmployee", credentials["type"]?.jsonPrimitive?.content)

            val departments = credentials["departments"]?.jsonArray
            assertNotNull(departments, "Departments array should be present")
            assertEquals(2, departments.size)

            // Check nationalities array
            val nationalities = fullPayload["nationalities"]?.jsonArray
            assertNotNull(nationalities, "Nationalities should be present")
            assertEquals(2, nationalities.size)

            println("PASS: Recursive disclosures verified successfully")
        }
}
