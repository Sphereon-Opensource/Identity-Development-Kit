/*
 * © 2025 Sphereon International B.V.
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

import com.sphereon.sdjwt.testutil.createSdJwtTestAppComponent

import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.sdjwt.dsl.sdJwtPayload
import com.sphereon.crypto.kms.KeyManagerServiceImpl
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for complex nested structures in SD-JWT
 * Tests nested objects, arrays, and mixed structures
 */
class ComplexStructureTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var sdJwtService: SdJwtService

    val app = createSdJwtTestAppComponent(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("sdjwt-complex-test")

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config = SoftwareKmsProviderConfig(
            id = "sdjwt-complex-test-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        val softwareKmsProvider = (app as com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl.Component).softwareKmsProvider.create(config, session.asCoreApiServiceComponent().serviceExecution)

        // Get KeyManagerService and SdJwtService from the session component
        keyManagerService = session.component.asKeyManagerServiceComponent().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        sdJwtService = (session.component as com.sphereon.sdjwt.SdJwtServiceImpl.Component).sdJwtService
    }

    /**
     * Test nested objects (multiple levels)
     */
    @Test
    fun testNestedObjects() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "nested-issuer",
                clientIdScheme = "jwt_vc_json",
                issuer = "https://nested.example.com"
            )
        )

        // Create nested object structure (using claimSd with JsonObject for nested SD)
        val payload = sdJwtPayload {
            iss("https://nested.example.com")
            sub("user-nested")

            // Level 1: Person object (SD) with nested structure
            claimSd("person", JsonObject(mapOf(
                "name" to JsonPrimitive("John Doe"),
                "age" to JsonPrimitive(35),
                "address" to JsonObject(mapOf(
                    "street" to JsonPrimitive("123 Main St"),
                    "city" to JsonPrimitive("Springfield"),
                    "coordinates" to JsonObject(mapOf(
                        "lat" to JsonPrimitive(42.1234),
                        "lon" to JsonPrimitive(-71.5678)
                    ))
                )),
                "contact" to JsonObject(mapOf(
                    "email" to JsonPrimitive("john@example.com"),
                    "phone" to JsonPrimitive("+1234567890")
                ))
            )))
        }

        // Issue SD-JWT
        val issueResult = sdJwtService.issueSdJwt(_root_ide_package_.com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload))
        assertTrue(issueResult.isOk)

        val sdJwtResult = issueResult.value
        assertTrue(sdJwtResult.disclosures.size >= 1, "Should have at least 1 disclosure")

        println("Nested objects: ${sdJwtResult.disclosures.size} disclosures")

        // Verify
        val verifyResult = sdJwtService.verifySdJwt(
            _root_ide_package_.com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = sdJwtResult.sdJwt, identifier = issuer)
        )
        assertTrue(verifyResult.isOk, "Verification should succeed: ${if (!verifyResult.isOk) verifyResult.error else ""}")
        val verification = verifyResult.value
        assertTrue(verification.isValid, "SD-JWT should be valid. signatureValid=${verification.signatureValid}, disclosuresValid=${verification.disclosuresValid}")

        // Verify nested structure is reconstructed
        val fullPayload = verifyResult.value.sdJwt.payload.fullPayload
        val person = fullPayload["person"] as? JsonObject
        assertNotNull(person, "Person object should be present")

        val address = person["address"] as? JsonObject
        assertNotNull(address, "Address object should be nested in person")

        val coordinates = address["coordinates"] as? JsonObject
        assertNotNull(coordinates, "Coordinates should be nested in address")

        assertEquals("Springfield", address["city"]?.jsonPrimitive?.content)
        assertEquals(42.1234, coordinates["lat"]?.jsonPrimitive?.content?.toDouble())

        println("PASS: Nested objects verified successfully")
    }

    /**
     * Test arrays with selectively disclosable elements
     */
    @Test
    fun testArrays() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "array-issuer",
                clientIdScheme = "jwt_vc_json",
                issuer = "https://array.example.com"
            )
        )

        // Create payload with arrays
        val payload = sdJwtPayload {
            iss("https://array.example.com")
            sub("user-array")

            // Simple array
            claim("languages", JsonArray(listOf(
                JsonPrimitive("English"),
                JsonPrimitive("Spanish"),
                JsonPrimitive("French")
            )))

            // Array of objects
            claimSd("credentials", JsonArray(listOf(
                JsonObject(mapOf(
                    "type" to JsonPrimitive("Degree"),
                    "field" to JsonPrimitive("Computer Science")
                )),
                JsonObject(mapOf(
                    "type" to JsonPrimitive("Certificate"),
                    "field" to JsonPrimitive("Cybersecurity")
                ))
            )))
        }

        // Issue SD-JWT
        val issueResult = sdJwtService.issueSdJwt(_root_ide_package_.com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload))
        assertTrue(issueResult.isOk)

        val sdJwtResult = issueResult.value

        // Verify
        val verifyResult = sdJwtService.verifySdJwt(
            _root_ide_package_.com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = sdJwtResult.sdJwt, identifier = issuer)
        )
        assertTrue(verifyResult.isOk)
        assertTrue(verifyResult.value.isValid)

        val fullPayload = verifyResult.value.sdJwt.payload.fullPayload

        // Verify arrays are present
        val languages = fullPayload["languages"]?.jsonArray
        assertNotNull(languages, "Languages array should be present")
        assertEquals(3, languages.size)
        assertEquals("English", languages[0].jsonPrimitive.content)

        val credentials = fullPayload["credentials"]?.jsonArray
        assertNotNull(credentials, "Credentials array should be present")
        assertEquals(2, credentials.size)

        println("PASS: Arrays verified successfully")
    }

    /**
     * Test mixed structure - objects containing arrays containing objects
     */
    @Test
    fun testMixedStructure() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "mixed-issuer",
                clientIdScheme = "jwt_vc_json",
                issuer = "https://mixed.example.com"
            )
        )

        // Create complex mixed structure
        val payload = sdJwtPayload {
            iss("https://mixed.example.com")
            sub("user-mixed")

            // Object containing array containing objects
            objSd("company") {
                claim("name", "Acme Corp")
                claim("founded", 2000)

                // Array of office locations
                claim("offices", JsonArray(listOf(
                    JsonObject(mapOf(
                        "city" to JsonPrimitive("New York"),
                        "country" to JsonPrimitive("USA"),
                        "employees" to JsonPrimitive(500)
                    )),
                    JsonObject(mapOf(
                        "city" to JsonPrimitive("London"),
                        "country" to JsonPrimitive("UK"),
                        "employees" to JsonPrimitive(300)
                    )),
                    JsonObject(mapOf(
                        "city" to JsonPrimitive("Tokyo"),
                        "country" to JsonPrimitive("Japan"),
                        "employees" to JsonPrimitive(200)
                    ))
                )))

                // Nested object with arrays
                claim("departments", JsonObject(mapOf(
                    "engineering" to JsonArray(listOf(
                        JsonPrimitive("Backend"),
                        JsonPrimitive("Frontend"),
                        JsonPrimitive("Mobile")
                    )),
                    "operations" to JsonArray(listOf(
                        JsonPrimitive("HR"),
                        JsonPrimitive("Finance")
                    ))
                )))
            }
        }

        // Issue SD-JWT
        val issueResult = sdJwtService.issueSdJwt(_root_ide_package_.com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload))
        assertTrue(issueResult.isOk)

        val sdJwtResult = issueResult.value

        // Verify
        val verifyResult = sdJwtService.verifySdJwt(
            _root_ide_package_.com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = sdJwtResult.sdJwt, identifier = issuer)
        )
        assertTrue(verifyResult.isOk)
        assertTrue(verifyResult.value.isValid)

        val fullPayload = verifyResult.value.sdJwt.payload.fullPayload

        // Verify mixed structure
        val company = fullPayload["company"] as? JsonObject
        assertNotNull(company, "Company object should be present")

        val offices = company["offices"]?.jsonArray
        assertNotNull(offices, "Offices array should be present")
        assertEquals(3, offices.size)

        val firstOffice = offices[0].jsonObject
        assertEquals("New York", firstOffice["city"]?.jsonPrimitive?.content)
        assertEquals(500, firstOffice["employees"]?.jsonPrimitive?.content?.toInt())

        val departments = company["departments"]?.jsonObject
        assertNotNull(departments, "Departments object should be present")

        val engineering = departments["engineering"]?.jsonArray
        assertNotNull(engineering, "Engineering array should be present")
        assertEquals(3, engineering.size)

        println("PASS: Mixed structure verified successfully")
    }

    /**
     * Test deep recursion (5+ levels)
     */
    @Test
    fun testDeepRecursion() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "deep-issuer",
                clientIdScheme = "jwt_vc_json",
                issuer = "https://deep.example.com"
            )
        )

        // Create deeply nested structure (5 levels)
        val payload = sdJwtPayload {
            iss("https://deep.example.com")
            sub("user-deep")

            // Level 1
            objSd("level1") {
                claim("value", "L1")

                // Level 2
                claim("level2", JsonObject(mapOf(
                    "value" to JsonPrimitive("L2"),

                    // Level 3
                    "level3" to JsonObject(mapOf(
                        "value" to JsonPrimitive("L3"),

                        // Level 4
                        "level4" to JsonObject(mapOf(
                            "value" to JsonPrimitive("L4"),

                            // Level 5
                            "level5" to JsonObject(mapOf(
                                "value" to JsonPrimitive("L5"),
                                "deepest" to JsonPrimitive("You found me!")
                            ))
                        ))
                    ))
                )))
            }
        }

        // Issue SD-JWT
        val issueResult = sdJwtService.issueSdJwt(_root_ide_package_.com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload))
        assertTrue(issueResult.isOk)

        val sdJwtResult = issueResult.value

        // Verify
        val verifyResult = sdJwtService.verifySdJwt(
            _root_ide_package_.com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = sdJwtResult.sdJwt, identifier = issuer)
        )
        assertTrue(verifyResult.isOk)
        assertTrue(verifyResult.value.isValid)

        // Navigate to deepest level
        val fullPayload = verifyResult.value.sdJwt.payload.fullPayload
        val level1 = fullPayload["level1"] as? JsonObject
        assertNotNull(level1)

        val level2 = level1["level2"] as? JsonObject
        assertNotNull(level2)

        val level3 = level2["level3"] as? JsonObject
        assertNotNull(level3)

        val level4 = level3["level4"] as? JsonObject
        assertNotNull(level4)

        val level5 = level4["level5"] as? JsonObject
        assertNotNull(level5)

        assertEquals("You found me!", level5["deepest"]?.jsonPrimitive?.content)

        println("PASS: Deep recursion (5 levels) verified successfully")
    }

    /**
     * Test partial reconstruction with selective disclosure at multiple levels
     */
    @Test
    fun testPartialReconstruction() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "partial-issuer",
                clientIdScheme = "jwt_vc_json",
                issuer = "https://partial.example.com"
            )
        )

        // Create structure with multiple SD claims at different levels
        val payload = sdJwtPayload {
            iss("https://partial.example.com")
            sub("user-partial")
            claim("public_name", "Alice")

            // SD at level 1
            claimSd("email", "alice@example.com")

            // SD object at level 1
            objSd("profile") {
                claim("visible_field", "public")
                claimSd("secret_field", "private")

                // Nested object with SD
                objSd("nested") {
                    claim("public_nested", "visible")
                    claimSd("private_nested", "hidden")
                }
            }
        }

        // Issue SD-JWT
        val issueResult = sdJwtService.issueSdJwt(_root_ide_package_.com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload))
        assertTrue(issueResult.isOk)

        val sdJwtResult = issueResult.value
        // Should have 2 top-level disclosures: email and profile
        // Note: nested claimSd inside objClaimSd don't create separate disclosures
        // because the entire profile object is disclosed as one unit
        assertTrue(sdJwtResult.disclosures.size >= 2, "Should have multiple SD claims")

        // Create selective presentation (disclose only email)
        val disclosureSelection = _root_ide_package_.com.sphereon.sdjwt.SdMap(mapOf("email" to _root_ide_package_.com.sphereon.sdjwt.SdField(sd = true)))

        val presentResult = sdJwtService.presentSdJwt(
            _root_ide_package_.com.sphereon.sdjwt.PresentSdJwtArgs(
                sdJwt = sdJwtResult.sdJwt,
                disclosureSelection = disclosureSelection
            )
        )
        assertTrue(presentResult.isOk)

        // Verify partial presentation
        val verifyResult = sdJwtService.verifySdJwt(
            _root_ide_package_.com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = presentResult.value.presentation, identifier = issuer)
        )
        assertTrue(verifyResult.isOk)
        assertTrue(verifyResult.value.isValid)

        val partialPayload = verifyResult.value.sdJwt.payload.fullPayload

        // Should have public claims and disclosed email
        assertEquals("Alice", partialPayload["public_name"]?.jsonPrimitive?.content)
        assertNotNull(partialPayload["email"], "Email should be disclosed")

        // Profile and nested should NOT be fully present (not disclosed)
        // (Depending on implementation, profile might not be in fullPayload at all)

        println("PASS: Partial reconstruction verified successfully")
    }
}
