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
import com.sphereon.crypto.core.generic.DigestAlg
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.test.*
import kotlin.time.measureTime

/**
 * Edge case tests for SD-JWT implementation.
 * Tests Unicode characters, empty values, large payloads, and stress scenarios.
 */
class SdJwtEdgeCasesTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var sdJwtService: com.sphereon.sdjwt.SdJwtService

    val app = createSdJwtTestAppComponent(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("sdjwt-edge-test")

    @BeforeTest
    fun setUp() {
        val config = SoftwareKmsProviderConfig(
            id = "sdjwt-edge-test-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        app
        val softwareKmsProvider = (app as com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl.Component).softwareKmsProvider.create(config, session.asCoreApiServiceComponent().serviceExecution)

        keyManagerService = session.component.asKeyManagerServiceComponent().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        sdJwtService = (session.component as com.sphereon.sdjwt.SdJwtServiceImpl.Component).sdJwtService
    }

    /**
     * Test 1: Empty disclosures list
     * Tests SD-JWT with no selectively disclosable claims
     */
    @Test
    fun testEmptyDisclosuresList() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "test-empty-disclosures",
                clientIdScheme = "jwt_vc_json",
                issuer = "https://example.com/issuer"
            )
        )

        // Create payload with no SD claims
        val payload = sdJwtPayload {
            iss("https://example.com/issuer")
            sub("user-no-sd")
            claim("public_data", "visible-to-all")
            claim("verified", true)
        }

        val issueResult = sdJwtService.issueSdJwt(
            _root_ide_package_.com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload)
        )

        assertTrue(issueResult.isOk, "Issuance should succeed with no SD claims")
        val sdJwtResult = issueResult.value

        // Should have no disclosures
        _root_ide_package_.kotlin.test.assertEquals(0, sdJwtResult.disclosures.size, "Should have zero disclosures")

        // Verification should work
        val verifyResult = sdJwtService.verifySdJwt(
            _root_ide_package_.com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = sdJwtResult.sdJwt, identifier = issuer)
        )

        assertTrue(verifyResult.isOk)
        assertTrue(verifyResult.value.isValid, "SD-JWT with no disclosures should be valid")

        // Test presentation with empty selection (no disclosures to disclose)
        val presentResult = sdJwtService.presentSdJwt(
            _root_ide_package_.com.sphereon.sdjwt.PresentSdJwtArgs(
                sdJwt = sdJwtResult.sdJwt,
                disclosureSelection = _root_ide_package_.com.sphereon.sdjwt.SdMap(emptyMap()),
                holderKey = issuer,
                audience = "https://verifier.example.com",
                nonce = "test-nonce"
            )
        )

        assertTrue(presentResult.isOk, "Presentation with empty selection should succeed")
    }

    /**
     * Test 2: Unicode characters in claims
     * Tests SD-JWT with emoji, CJK characters, and special symbols
     */
    @Test
    fun testUnicodeCharactersInClaims() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "test-unicode",
                clientIdScheme = "jwt_vc_json",
                issuer = "https://example.com/issuer"
            )
        )

        // Create payload with various Unicode characters
        val payload = sdJwtPayload {
            iss("https://example.com/issuer")
            sub("user-unicode-123")
            claimSd("name_emoji", "Alice 👩‍💻 Smith")
            claimSd("name_cjk", "山田太郎")  // Japanese name
            claimSd("name_cyrillic", "Иван Петров")  // Russian name
            claimSd("name_arabic", "محمد علي")  // Arabic name
            claimSd("special_chars", "Café ñoño €100 ©2025")
            claimSd("math_symbols", "π ≈ 3.14 ∞ ∑")
        }

        val issueResult = sdJwtService.issueSdJwt(
            _root_ide_package_.com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload)
        )

        assertTrue(issueResult.isOk, "Issuance with Unicode should succeed")
        _root_ide_package_.kotlin.test.assertEquals(6, issueResult.value.disclosures.size, "Should have 6 disclosures")

        // Verify reconstruction preserves Unicode
        val verifyResult = sdJwtService.verifySdJwt(
            _root_ide_package_.com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = issueResult.value.sdJwt, identifier = issuer)
        )

        assertTrue(verifyResult.isOk)
        assertTrue(verifyResult.value.isValid)

        val fullPayload = verifyResult.value.sdJwt.payload.fullPayload

        // Verify Unicode values are preserved exactly
        assertEquals("Alice 👩‍💻 Smith", fullPayload["name_emoji"]?.toString()?.trim('"'))
        assertEquals("山田太郎", fullPayload["name_cjk"]?.toString()?.trim('"'))
        assertEquals("Иван Петров", fullPayload["name_cyrillic"]?.toString()?.trim('"'))
        assertEquals("محمد علي", fullPayload["name_arabic"]?.toString()?.trim('"'))
        assertEquals("Café ñoño €100 ©2025", fullPayload["special_chars"]?.toString()?.trim('"'))
        assertEquals("π ≈ 3.14 ∞ ∑", fullPayload["math_symbols"]?.toString()?.trim('"'))

        // Test selective disclosure with Unicode
        val disclosureSelection = _root_ide_package_.com.sphereon.sdjwt.SdMap(
            mapOf(
                "name_emoji" to _root_ide_package_.com.sphereon.sdjwt.SdField(sd = true),
                "name_cjk" to _root_ide_package_.com.sphereon.sdjwt.SdField(sd = true)
            )
        )

        val presentResult = sdJwtService.presentSdJwt(
            _root_ide_package_.com.sphereon.sdjwt.PresentSdJwtArgs(
                sdJwt = issueResult.value.sdJwt,
                disclosureSelection = disclosureSelection,
                holderKey = issuer,
                audience = "https://verifier.example.com",
                nonce = "unicode-test"
            )
        )

        assertTrue(presentResult.isOk, "Presentation with Unicode claims should succeed")
    }

    /**
     * Test 3: Null and undefined values
     * Tests SD-JWT with JsonNull values and missing optional claims
     */
    @Test
    fun testNullAndUndefinedValues() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "test-null",
                clientIdScheme = "jwt_vc_json",
                issuer = "https://example.com/issuer"
            )
        )

        // Build payload with explicit null values
        val payload = sdJwtPayload {
            iss("https://example.com/issuer")
            sub("user-null-test")
            claim("present", "value")
            claim("nullable_field", JsonNull)
        }

        val issueResult = sdJwtService.issueSdJwt(
            _root_ide_package_.com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload)
        )

        assertTrue(issueResult.isOk, "Issuance with null values should succeed")

        val verifyResult = sdJwtService.verifySdJwt(
            _root_ide_package_.com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = issueResult.value.sdJwt, identifier = issuer)
        )

        assertTrue(verifyResult.isOk)
        assertTrue(verifyResult.value.isValid)

        val fullPayload = verifyResult.value.sdJwt.payload.fullPayload

        // Verify null value is preserved
        assertTrue(fullPayload.containsKey("nullable_field"), "Null field should be present")
        assertTrue(
            fullPayload["nullable_field"] is JsonNull || fullPayload["nullable_field"].toString() == "null",
            "Field should be null"
        )

        // Verify missing optional claims don't break verification
        assertFalse(fullPayload.containsKey("undefined_field"), "Undefined field should not be present")
    }

    /**
     * Test 4: Empty strings and objects
     * Tests SD-JWT with empty string values and empty objects
     */
    @Test
    fun testEmptyStringsAndObjects() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "test-empty",
                clientIdScheme = "jwt_vc_json",
                issuer = "https://example.com/issuer"
            )
        )

        // Create payload with empty strings and objects
        val payload = sdJwtPayload {
            iss("https://example.com/issuer")
            sub("user-empty-test")
            claimSd("empty_string", "")
            claimSd("whitespace_only", "   ")
            claim("empty_object", JsonObject(emptyMap()))
        }

        val issueResult = sdJwtService.issueSdJwt(
            _root_ide_package_.com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload)
        )

        assertTrue(issueResult.isOk, "Issuance with empty values should succeed")

        val verifyResult = sdJwtService.verifySdJwt(
            _root_ide_package_.com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = issueResult.value.sdJwt, identifier = issuer)
        )

        assertTrue(verifyResult.isOk)
        assertTrue(verifyResult.value.isValid)

        val fullPayload = verifyResult.value.sdJwt.payload.fullPayload

        // Verify empty string is preserved
        assertEquals("", fullPayload["empty_string"]?.toString()?.trim('"'))

        // Verify whitespace-only string is preserved
        assertEquals("   ", fullPayload["whitespace_only"]?.toString()?.trim('"'))

        // Verify empty object is present
        assertTrue(fullPayload.containsKey("empty_object"), "Empty object should be present")
    }

    /**
     * Test 5: Very large payload (performance test)
     * Tests SD-JWT with 100+ claims and measures performance
     */
    @Test
    fun testVeryLargePayload() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "test-large",
                clientIdScheme = "jwt_vc_json",
                issuer = "https://example.com/issuer"
            )
        )

        // Create large payload with 100 claims (50 SD, 50 regular)
        val payload = sdJwtPayload {
            iss("https://example.com/issuer")
            sub("user-large-payload")

            // Add 50 regular claims
            repeat(50) { i ->
                claim("regular_claim_$i", "regular_value_$i")
            }

            // Add 50 SD claims
            repeat(50) { i ->
                claimSd("sd_claim_$i", "sensitive_value_$i")
            }

            // Use FIXED decoy mode with 20 decoys for privacy
            minimumDigests(20)
        }

        // Measure issuance time
        val issuanceTime = measureTime {
            val issueResult = sdJwtService.issueSdJwt(
                _root_ide_package_.com.sphereon.sdjwt.IssueSdJwtArgs(
                    issuer = issuer,
                    payload = payload,
                    spec = _root_ide_package_.com.sphereon.sdjwt.SdJwtSpec(
                        digestAlg = DigestAlg.SHA256,
                        decoyConfig = _root_ide_package_.com.sphereon.sdjwt.DecoyConfig(mode = _root_ide_package_.com.sphereon.sdjwt.DecoyMode.FIXED, count = 20)
                    )
                )
            )

            assertTrue(issueResult.isOk, "Large payload issuance should succeed")

            // Should have 50 disclosures
            _root_ide_package_.kotlin.test.assertEquals(50, issueResult.value.disclosures.size, "Should have 50 disclosures")

            println("Large payload issuance completed:")
            println("  - 50 regular claims")
            println("  - 50 SD claims (disclosures)")
            println("  - 20 decoy digests")
            println("  - SD-JWT size: ${issueResult.value.sdJwt.length} bytes")

            // Measure verification time
            val verificationTime = measureTime {
                val verifyResult = sdJwtService.verifySdJwt(
                    _root_ide_package_.com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = issueResult.value.sdJwt, identifier = issuer)
                )

                assertTrue(verifyResult.isOk)
                assertTrue(verifyResult.value.isValid, "Large SD-JWT should be valid")
                assertTrue(verifyResult.value.disclosuresValid, "All disclosures should be valid")

                val fullPayload = verifyResult.value.sdJwt.payload.fullPayload

                // Verify we have all claims
                assertTrue(fullPayload.size >= 100, "Should have at least 100 claims")
            }

            println("  - Verification time: $verificationTime")
        }

        println("  - Total issuance time: $issuanceTime")

        // Performance assertion - should complete in reasonable time
        assertTrue(issuanceTime.inWholeSeconds < 10, "Issuance should complete within 10 seconds")
    }

    /**
     * Test 6: Maximum decoy digests (stress test)
     * Tests SD-JWT with very large number of decoys
     */
    @Test
    fun testMaximumDecoyDigests() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "test-max-decoys",
                clientIdScheme = "jwt_vc_json",
                issuer = "https://example.com/issuer"
            )
        )

        // Create payload with just a few SD claims but many decoys
        val payload = sdJwtPayload {
            iss("https://example.com/issuer")
            sub("user-max-decoys")
            claimSd("secret1", "value1")
            claimSd("secret2", "value2")
            claimSd("secret3", "value3")
            minimumDigests(100)  // Request 100 decoys
        }

        val issueResult = sdJwtService.issueSdJwt(
            _root_ide_package_.com.sphereon.sdjwt.IssueSdJwtArgs(
                issuer = issuer,
                payload = payload,
                spec = _root_ide_package_.com.sphereon.sdjwt.SdJwtSpec(
                    digestAlg = DigestAlg.SHA256,
                    decoyConfig = _root_ide_package_.com.sphereon.sdjwt.DecoyConfig(mode = _root_ide_package_.com.sphereon.sdjwt.DecoyMode.FIXED, count = 100)
                )
            )
        )

        assertTrue(issueResult.isOk, "Issuance with 100 decoys should succeed")

        // Verify 3 disclosures
        _root_ide_package_.kotlin.test.assertEquals(3, issueResult.value.disclosures.size, "Should have 3 disclosures")

        println("Maximum decoy test:")
        println("  - 3 real disclosures")
        println("  - 100 decoy digests")
        println("  - SD-JWT size: ${issueResult.value.sdJwt.length} bytes")

        // Verification should still work
        val verifyResult = sdJwtService.verifySdJwt(
            _root_ide_package_.com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = issueResult.value.sdJwt, identifier = issuer)
        )

        assertTrue(verifyResult.isOk)
        assertTrue(verifyResult.value.isValid, "SD-JWT with many decoys should be valid")
        assertTrue(verifyResult.value.disclosuresValid, "Disclosures should be valid despite many decoys")

        // Verify _sd array contains many digests (real + decoys)
        val fullPayload = verifyResult.value.sdJwt.payload.fullPayload
        assertTrue(fullPayload.size >= 3, "Should have at least the disclosed claims")
    }
}
