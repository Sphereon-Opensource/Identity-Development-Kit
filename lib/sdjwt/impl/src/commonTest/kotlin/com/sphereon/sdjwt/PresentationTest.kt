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
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for SD-JWT presentation functionality
 * Based on RFC 9901 Section 7 - Holder Disclosure
 */
class PresentationTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var sdJwtService: com.sphereon.sdjwt.SdJwtService

    val app = createSdJwtTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("sdjwt-presentation-test")

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config =
            SoftwareKmsProviderConfig(
                id = "sdjwt-presentation-test-provider",
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

        sdJwtService = (session.graph as com.sphereon.sdjwt.SdJwtServiceImpl.Graph).sdJwtService
    }

    /**
     * Test selective disclosure - present only a subset of claims
     */
    @Test
    fun testSelectiveDisclosure() =
        runTest {
            // Generate issuer key
            val issuerKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val issuerKeyInfo: ManagedKeyInfoType<*> = issuerKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = issuerKeyInfo,
                    context =
                        IdentifierContext(
                            clientId = "selective-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://issuer.example.com",
                        ),
                )

            // Issue SD-JWT with multiple SD claims
            val payload =
                sdJwtPayload {
                    iss("https://issuer.example.com")
                    sub("user-selective")
                    claimSd("email", "user@example.com")
                    claimSd("phone", "+1234567890")
                    claimSd("age", 30)
                    claimSd("verified", true)
                }

            val issueResult = sdJwtService.issueSdJwt(com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload))
            assertTrue(issueResult.isOk)

            val sdJwtResult = issueResult.value
            kotlin.test.assertEquals(4, sdJwtResult.disclosures.size, "Should have 4 disclosures")

            // Create presentation with only email and age disclosed (using SdMap)
            val disclosureSelection =
                com.sphereon.sdjwt.SdMap(
                    mapOf(
                        "email" to com.sphereon.sdjwt.SdField(sd = true),
                        "age" to com.sphereon.sdjwt.SdField(sd = true),
                    ),
                )

            val presentArgs =
                com.sphereon.sdjwt.PresentSdJwtArgs(
                    sdJwt = sdJwtResult.sdJwt,
                    disclosureSelection = disclosureSelection,
                )

            val presentResult = sdJwtService.presentSdJwt(presentArgs)
            assertTrue(presentResult.isOk)

            val presentation = presentResult.value
            assertNotNull(presentation.presentation)

            // Should disclose only selected claims
            kotlin.test.assertEquals(2, presentation.disclosedClaims.size, "Should disclose 2 claims")
            assertTrue(presentation.disclosedClaims.contains("email"))
            assertTrue(presentation.disclosedClaims.contains("age"))

            println("PASS: Selective disclosure: disclosed ${presentation.disclosedClaims.joinToString(", ")}")

            // Verify the presentation
            val verifyResult =
                sdJwtService.verifySdJwt(
                    com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = presentation.presentation, identifier = issuer),
                )
            assertTrue(verifyResult.isOk)
            assertTrue(verifyResult.value.isValid)

            // Check that only selected claims are in the full payload
            val fullPayload = verifyResult.value.sdJwt.payload.fullPayload
            assertNotNull(fullPayload["email"], "Email should be disclosed")
            assertNotNull(fullPayload["age"], "Age should be disclosed")

            // Note: With partial disclosure, phone and verified are NOT in fullPayload
            // because their disclosures were not included in the presentation

            println("PASS: Presentation verified with selective disclosure")
        }

    /**
     * Test disclosing all claims (disclosureSelection = null)
     */
    @Test
    fun testDiscloseAllClaims() =
        runTest {
            val issuerKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val issuerKeyInfo: ManagedKeyInfoType<*> = issuerKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = issuerKeyInfo,
                    context =
                        IdentifierContext(
                            clientId = "all-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://issuer-all.example.com",
                        ),
                )

            // Issue SD-JWT
            val payload =
                sdJwtPayload {
                    iss("https://issuer-all.example.com")
                    sub("user-all")
                    claimSd("claim1", "value1")
                    claimSd("claim2", "value2")
                    claimSd("claim3", "value3")
                }

            val issueResult = sdJwtService.issueSdJwt(com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload))
            assertTrue(issueResult.isOk)

            val sdJwtResult = issueResult.value

            // Present with disclosureSelection = null (disclose all)
            val presentArgs =
                com.sphereon.sdjwt.PresentSdJwtArgs(
                    sdJwt = sdJwtResult.sdJwt,
                    disclosureSelection = null, // Disclose all
                )

            val presentResult = sdJwtService.presentSdJwt(presentArgs)
            assertTrue(presentResult.isOk)

            val presentation = presentResult.value

            // Should disclose all 3 claims
            kotlin.test.assertEquals(3, presentation.disclosedClaims.size, "Should disclose all 3 claims")

            println("PASS: Disclosed all claims: ${presentation.disclosedClaims.joinToString(", ")}")

            // Verify presentation
            val verifyResult =
                sdJwtService.verifySdJwt(
                    com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = presentation.presentation, identifier = issuer),
                )
            assertTrue(verifyResult.isOk)
            assertTrue(verifyResult.value.isValid)

            println("PASS: Full disclosure presentation verified")
        }

    /**
     * Test Key Binding JWT creation
     */
    @Test
    fun testKeyBindingJwtCreation() =
        runTest {
            // Generate issuer key
            val issuerKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val issuerKeyInfo: ManagedKeyInfoType<*> = issuerKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = issuerKeyInfo,
                    context =
                        IdentifierContext(
                            clientId = "kb-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://kb-issuer.example.com",
                        ),
                )

            // Generate holder key for Key Binding
            val holderKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            // Use PRIVATE visibility for signing operations
            val holderKeyInfo: ManagedKeyInfoType<*> = holderKeyPair.joseToManagedKeyInfo(com.sphereon.crypto.core.KeyVisibility.PRIVATE)

            val holderKey =
                ManagedOptsKeyInfo(
                    identifier = holderKeyInfo,
                    context =
                        IdentifierContext(
                            clientId = "kb-holder",
                            clientIdScheme = "jwt_vc_json",
                        ),
                )

            // Issue SD-JWT
            val payload =
                sdJwtPayload {
                    iss("https://kb-issuer.example.com")
                    sub("user-kb")
                    claimSd("email", "kb@example.com")
                }

            val issueResult = sdJwtService.issueSdJwt(com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload))
            assertTrue(issueResult.isOk)

            val sdJwtResult = issueResult.value

            // Create presentation with Key Binding JWT
            val presentArgs =
                com.sphereon.sdjwt.PresentSdJwtArgs(
                    sdJwt = sdJwtResult.sdJwt,
                    disclosureSelection = null, // Disclose all
                    holderKey = holderKey,
                    audience = "https://verifier.example.com",
                    nonce = "random-nonce-12345",
                )

            val presentResult = sdJwtService.presentSdJwt(presentArgs)
            assertTrue(presentResult.isOk)

            val presentation = presentResult.value

            // Presentation string should end with KB-JWT
            assertTrue(
                presentation.presentation.count { it == '~' } >= 2,
                "Presentation should have KB-JWT (multiple ~ separators)",
            )

            println("PASS: Key Binding JWT created in presentation")

            // Parse presentation to check KB-JWT is present
            val parsedSdJwt =
                com.sphereon.sdjwt.SdJwtCodec
                    .parse(presentation.presentation)
            assertTrue(parsedSdJwt.isOk)
            assertNotNull(parsedSdJwt.value.keyBindingJwt, "Key Binding JWT should be present")

            println("PASS: Presentation with Key Binding JWT verified")
        }

    /**
     * Test Key Binding JWT verification (aud, nonce, iat, sd_hash)
     */
    @Test
    fun testKeyBindingJwtVerification() =
        runTest {
            // Generate issuer and holder keys
            val issuerKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            // Use PRIVATE visibility for issuer to sign the main SD-JWT
            val issuerKeyInfo: ManagedKeyInfoType<*> = issuerKeyPair.joseToManagedKeyInfo(com.sphereon.crypto.core.KeyVisibility.PRIVATE)

            val holderKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            // Create holder key info with PRIVATE visibility for signing operations
            val holderKeyInfo: ManagedKeyInfoType<*> = holderKeyPair.joseToManagedKeyInfo(com.sphereon.crypto.core.KeyVisibility.PRIVATE)
            // Extract public key for cnf claim
            val holderPublicKeyInfo: ManagedKeyInfoType<*> = holderKeyPair.joseToManagedKeyInfo(com.sphereon.crypto.core.KeyVisibility.PUBLIC)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = issuerKeyInfo,
                    context =
                        IdentifierContext(
                            clientId = "kbverify-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://kbverify.example.com",
                        ),
                )

            val holderKey =
                ManagedOptsKeyInfo(
                    identifier = holderKeyInfo,
                    context =
                        IdentifierContext(
                            clientId = "kbverify-holder",
                            clientIdScheme = "jwt_vc_json",
                        ),
                )

            // Issue SD-JWT with cnf claim containing holder's public key
            // Per RFC 7800, cnf claim should contain minimal JWK (only kty, crv, x, y for EC)
            val holderPublicJwk = holderPublicKeyInfo.key as com.sphereon.crypto.core.jose.Jwk
            val minimalJwk = holderPublicJwk.toMinimalJwk()
            val cnfClaim =
                buildJsonObject {
                    put("jwk", minimalJwk.toJsonObject())
                }

            val payload =
                sdJwtPayload {
                    iss("https://kbverify.example.com")
                    sub("user-kbverify")
                    claimSd("data", "sensitive")
                    claim("cnf", cnfClaim)
                }

            val issueResult = sdJwtService.issueSdJwt(com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload))
            assertTrue(issueResult.isOk)

            val audience = "https://verifier-kb.example.com"
            val nonce = "verification-nonce-67890"

            // Create presentation with KB-JWT
            val presentArgs =
                com.sphereon.sdjwt.PresentSdJwtArgs(
                    sdJwt = issueResult.value.sdJwt,
                    disclosureSelection = null,
                    holderKey = holderKey,
                    audience = audience,
                    nonce = nonce,
                )

            val presentResult = sdJwtService.presentSdJwt(presentArgs)
            assertTrue(presentResult.isOk)

            val presentation = presentResult.value

            // Parse and verify KB-JWT content
            val parsedSdJwt =
                com.sphereon.sdjwt.SdJwtCodec
                    .parse(presentation.presentation)
            assertTrue(parsedSdJwt.isOk)

            val kbJwt = parsedSdJwt.value.keyBindingJwt
            assertNotNull(kbJwt, "KB-JWT should be present")

            // Verify KB-JWT claims (would need to parse the JWT payload)
            // For now, verify the presentation is valid
            val verifyResult =
                sdJwtService.verifySdJwt(
                    com.sphereon.sdjwt.VerifySdJwtArgs(
                        sdJwt = presentation.presentation,
                        identifier = issuer,
                        expectedAudience = audience,
                        expectedNonce = nonce,
                    ),
                )

            assertTrue(verifyResult.isOk, "Verification should succeed")
            if (!verifyResult.value.isValid) {
                println("Verification failed. Errors: ${verifyResult.value.errorMessages}")
                println("  signatureValid: ${verifyResult.value.signatureValid}")
                println("  disclosuresValid: ${verifyResult.value.disclosuresValid}")
                println("  keyBindingValid: ${verifyResult.value.keyBindingValid}")

                // Debug: Print the JWK from cnf claim
                println("\nDebug - CNF claim minimal JWK:")
                println(minimalJwk.toJsonString())

                // Debug: Print the KB-JWT to analyze
                val kbJwtValue = parsedSdJwt.value.keyBindingJwt?.jwt
                if (kbJwtValue != null) {
                    println("\nDebug - Complete KB-JWT:")
                    println(kbJwtValue)

                    val kbParts = kbJwtValue.split(".")
                    if (kbParts.size == 3) {
                        val kbHeader =
                            com.sphereon.crypto.jose.jws.JwsUtils
                                .decodeBase64UrlToJson(kbParts[0])
                        println("\nDebug - KB-JWT header:")
                        println(kbHeader.toString())
                        val kbHeaderJwk = kbHeader["jwk"]
                        if (kbHeaderJwk != null) {
                            println("\nDebug - KB-JWT header JWK:")
                            println(kbHeaderJwk.toString())
                        }

                        val kbPayload =
                            com.sphereon.crypto.jose.jws.JwsUtils
                                .decodeBase64UrlToJson(kbParts[1])
                        println("\nDebug - KB-JWT payload:")
                        println(kbPayload.toString())

                        println("\nDebug - KB-JWT signature (base64url):")
                        println(kbParts[2])
                    }
                }
            }
            assertTrue(verifyResult.value.isValid, "Presentation should be valid")

            println("PASS: Key Binding JWT verification successful")
        }

    /**
     * Test presentation format: JWT~disclosure1~disclosure2~...~kbJwt
     */
    @Test
    fun testPresentationFormat() =
        runTest {
            val issuerKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val issuerKeyInfo: ManagedKeyInfoType<*> = issuerKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = issuerKeyInfo,
                    context =
                        IdentifierContext(
                            clientId = "format-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://format.example.com",
                        ),
                )

            // Issue SD-JWT with 2 disclosures
            val payload =
                sdJwtPayload {
                    iss("https://format.example.com")
                    sub("user-format")
                    claimSd("field1", "value1")
                    claimSd("field2", "value2")
                }

            val issueResult = sdJwtService.issueSdJwt(com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload))
            assertTrue(issueResult.isOk)

            // Create presentation without KB-JWT
            val presentArgs =
                com.sphereon.sdjwt.PresentSdJwtArgs(
                    sdJwt = issueResult.value.sdJwt,
                    disclosureSelection = null,
                )

            val presentResult = sdJwtService.presentSdJwt(presentArgs)
            assertTrue(presentResult.isOk)

            val presentation = presentResult.value.presentation

            // Format should be: JWT~disclosure~disclosure~
            val parts = presentation.split("~")
            assertTrue(parts.size >= 3, "Presentation should have JWT and at least 2 disclosures")

            // First part should be JWT (3 base64url parts separated by dots)
            val jwtParts = parts[0].split(".")
            assertEquals(3, jwtParts.size, "JWT should have 3 parts (header.payload.signature)")

            println("PASS: Presentation format: ${parts.size} parts (1 JWT + ${parts.size - 2} disclosures + trailing ~)")
        }

    /**
     * Test sd_hash binding in KB-JWT
     * The sd_hash claim in KB-JWT binds the KB-JWT to the SD-JWT
     */
    @Test
    fun testSdHashBinding() =
        runTest {
            val issuerKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            // Use PRIVATE visibility for issuer to sign the main SD-JWT
            val issuerKeyInfo: ManagedKeyInfoType<*> = issuerKeyPair.joseToManagedKeyInfo(com.sphereon.crypto.core.KeyVisibility.PRIVATE)

            val holderKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            // Create holder key info with PRIVATE visibility for signing operations
            val holderKeyInfo: ManagedKeyInfoType<*> = holderKeyPair.joseToManagedKeyInfo(com.sphereon.crypto.core.KeyVisibility.PRIVATE)
            // Extract public key for cnf claim
            val holderPublicKeyInfo: ManagedKeyInfoType<*> = holderKeyPair.joseToManagedKeyInfo(com.sphereon.crypto.core.KeyVisibility.PUBLIC)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = issuerKeyInfo,
                    context =
                        IdentifierContext(
                            clientId = "sdhash-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://sdhash.example.com",
                        ),
                )

            val holderKey =
                ManagedOptsKeyInfo(
                    identifier = holderKeyInfo,
                    context =
                        IdentifierContext(
                            clientId = "sdhash-holder",
                            clientIdScheme = "jwt_vc_json",
                        ),
                )

            // Issue SD-JWT with cnf claim containing holder's public key
            // Per RFC 7800, cnf claim should contain minimal JWK (only kty, crv, x, y for EC)
            val holderPublicJwk = holderPublicKeyInfo.key as com.sphereon.crypto.core.jose.Jwk
            val minimalJwk = holderPublicJwk.toMinimalJwk()
            val cnfClaim =
                buildJsonObject {
                    put("jwk", minimalJwk.toJsonObject())
                }

            val payload =
                sdJwtPayload {
                    iss("https://sdhash.example.com")
                    sub("user-sdhash")
                    claimSd("bound-data", "important")
                    claim("cnf", cnfClaim)
                }

            val issueResult = sdJwtService.issueSdJwt(com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload))
            assertTrue(issueResult.isOk)

            // Create presentation with KB-JWT
            val presentArgs =
                com.sphereon.sdjwt.PresentSdJwtArgs(
                    sdJwt = issueResult.value.sdJwt,
                    disclosureSelection = null,
                    holderKey = holderKey,
                    audience = "https://verifier-binding.example.com",
                    nonce = "binding-nonce",
                )

            val presentResult = sdJwtService.presentSdJwt(presentArgs)
            assertTrue(presentResult.isOk)

            // Verify the presentation (which checks sd_hash binding)
            val verifyResult =
                sdJwtService.verifySdJwt(
                    com.sphereon.sdjwt.VerifySdJwtArgs(
                        sdJwt = presentResult.value.presentation,
                        identifier = issuer,
                        expectedAudience = "https://verifier-binding.example.com",
                        expectedNonce = "binding-nonce",
                    ),
                )

            assertTrue(verifyResult.isOk)
            assertTrue(verifyResult.value.isValid, "sd_hash should bind KB-JWT to SD-JWT")

            println("PASS: sd_hash binding verified successfully")
        }

    /**
     * Test that KB-JWT signed with a different key (not matching CNF claim) is rejected
     * This is a CRITICAL security test per RFC 9901 Â§4.3
     */
    @Test
    fun testKbJwtCnfMismatchRejected() =
        runTest {
            // Generate issuer key
            val issuerKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val issuerKeyInfo: ManagedKeyInfoType<*> = issuerKeyPair.joseToManagedKeyInfo(com.sphereon.crypto.core.KeyVisibility.PRIVATE)

            val issuer =
                ManagedOptsKeyInfo(
                    identifier = issuerKeyInfo,
                    context =
                        IdentifierContext(
                            clientId = "cnf-test-issuer",
                            clientIdScheme = "jwt_vc_json",
                            issuer = "https://cnf-test-issuer.example.com",
                        ),
                )

            // Generate holder key for CNF claim
            val holderKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val holderPublicKeyInfo: ManagedKeyInfoType<*> = holderKeyPair.joseToManagedKeyInfo(com.sphereon.crypto.core.KeyVisibility.PUBLIC)
            val holderPublicJwk = holderPublicKeyInfo.key as com.sphereon.crypto.core.jose.Jwk
            val minimalJwk = holderPublicJwk.toMinimalJwk()
            val cnfClaim =
                buildJsonObject {
                    put("jwk", minimalJwk.toJsonObject())
                }

            // Issue SD-JWT with holder's public key in CNF claim
            val payload =
                sdJwtPayload {
                    iss("https://cnf-test-issuer.example.com")
                    sub("user-cnf-test")
                    claimSd("email", "cnf-test@example.com")
                    claim("cnf", cnfClaim)
                }

            val issueResult = sdJwtService.issueSdJwt(com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload))
            assertTrue(issueResult.isOk)

            // Generate DIFFERENT key for signing KB-JWT (this should be rejected!)
            val attackerKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val attackerKeyInfo: ManagedKeyInfoType<*> = attackerKeyPair.joseToManagedKeyInfo(com.sphereon.crypto.core.KeyVisibility.PRIVATE)
            val attackerKey =
                ManagedOptsKeyInfo(
                    identifier = attackerKeyInfo,
                    context =
                        IdentifierContext(
                            clientId = "attacker",
                            clientIdScheme = "jwt_vc_json",
                        ),
                )

            // Try to create presentation with WRONG key (attacker's key instead of holder's key)
            val presentArgs =
                com.sphereon.sdjwt.PresentSdJwtArgs(
                    sdJwt = issueResult.value.sdJwt,
                    disclosureSelection = null,
                    holderKey = attackerKey, // WRONG KEY - should be rejected
                    audience = "https://verifier-cnf-test.example.com",
                    nonce = "cnf-test-nonce",
                )

            val presentResult = sdJwtService.presentSdJwt(presentArgs)
            assertTrue(presentResult.isOk, "Presentation should succeed (KB-JWT gets created)")

            // Verify the presentation - should FAIL because KB-JWT is signed with wrong key
            val verifyResult =
                sdJwtService.verifySdJwt(
                    com.sphereon.sdjwt.VerifySdJwtArgs(
                        sdJwt = presentResult.value.presentation,
                        identifier = issuer,
                        expectedAudience = "https://verifier-cnf-test.example.com",
                        expectedNonce = "cnf-test-nonce",
                    ),
                )

            // CRITICAL: Verification MUST fail when KB-JWT is signed with a key that doesn't match CNF claim
            if (verifyResult.isOk) {
                assertFalse(
                    verifyResult.value.isValid,
                    "KB-JWT signed with wrong key MUST be rejected - this is a critical security vulnerability if it passes!",
                )

                val errorMessages = verifyResult.value.errorMessages
                assertTrue(
                    errorMessages.any {
                        it.contains("KB-JWT signature is invalid") ||
                            it.contains("CNF claim JWK") ||
                            it.contains("does not match")
                    },
                    "Error message should indicate KB-JWT signature or key mismatch. Actual errors: $errorMessages",
                )

                println("PASS: KB-JWT with mismatched key correctly rejected")
            } else {
                // If verification fails at the command level, that's also acceptable
                println("PASS: KB-JWT with mismatched key rejected at verification level: ${verifyResult.error.message}")
            }
        }
}
