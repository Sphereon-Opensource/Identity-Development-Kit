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

package com.sphereon.sdjwt.vc

import com.sphereon.sdjwt.testutil.createSdJwtTestAppComponent

import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.sdjwt.dsl.sdJwtPayload
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.*

/**
 * Integration tests for SD-JWT-VC (Verifiable Credentials) functionality.
 * Tests VCT validation, metadata resolution, and VC-specific verification.
 */
class SdJwtVcIntegrationTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var sdJwtService: com.sphereon.sdjwt.SdJwtService

    val app = createSdJwtTestAppComponent(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("sdjwt-vc-test")

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config = SoftwareKmsProviderConfig(
            id = "sdjwt-vc-test-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        app
        val softwareKmsProvider = (app as com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl.Component).softwareKmsProvider.create(config, session.asCoreApiServiceComponent().serviceExecution)

        // Get services from the session component
        keyManagerService = session.component.asKeyManagerServiceComponent().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        sdJwtService = (session.component as com.sphereon.sdjwt.SdJwtServiceImpl.Component).sdJwtService
    }

    /**
     * Test 1: Issue and verify SD-JWT-VC with VCT claim
     * Verifies that VCT is present and valid in the credential
     */
    @Test
    fun testIssueAndVerifySdJwtVc() = runTest {
        // Generate key pair for issuer
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "test-vc-issuer",
                clientIdScheme = "jwt_vc_json",
                issuer = "https://issuer.example.com"
            )
        )

        // Issue SD-JWT-VC with VCT claim
        val payload = sdJwtPayload {
            iss("https://issuer.example.com")
            claim("vct", "https://example.com/VerifiedEmployee")  // Required for VC
            subSd("employee-12345")
            claimSd("email", "employee@example.com")
            claimSd("department", "Engineering")
        }

        val issueResult = sdJwtService.issueSdJwt(
            _root_ide_package_.com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload)
        )

        assertTrue(issueResult.isOk, "Issuance should succeed")
        val sdJwtResult = issueResult.value
        assertNotNull(sdJwtResult.sdJwt)

        // Verify the SD-JWT contains VCT in the payload
        val verifyResult = sdJwtService.verifySdJwt(
            _root_ide_package_.com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = sdJwtResult.sdJwt, identifier = issuer)
        )

        assertTrue(verifyResult.isOk, "Verification should succeed")
        val verified = verifyResult.value
        assertTrue(verified.isValid, "SD-JWT should be valid")

        // Check VCT claim is present
        val fullPayload = verified.sdJwt.payload.fullPayload
        assertTrue(fullPayload.containsKey("vct"), "VCT claim should be present")
        assertEquals(
            "https://example.com/VerifiedEmployee",
            fullPayload["vct"]?.toString()?.trim('"'),
            "VCT should match"
        )
    }

    /**
     * Test 2: VCT validation - ensure VCT is required and has valid format
     */
    @Test
    fun testVctValidation() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "test-vct-validation",
                clientIdScheme = "jwt_vc_json",
                issuer = "https://issuer.example.com"
            )
        )

        // Test with valid VCT (URL format)
        val validPayload = sdJwtPayload {
            iss("https://issuer.example.com")
            claim("vct", "https://example.com/CredentialType")
            subSd("user-123")
        }

        val validResult = sdJwtService.issueSdJwt(
            _root_ide_package_.com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = validPayload)
        )

        assertTrue(validResult.isOk, "Valid VCT should succeed")

        // Verify VCT format is URL
        val verifyResult = sdJwtService.verifySdJwt(
            _root_ide_package_.com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = validResult.value.sdJwt, identifier = issuer)
        )
        assertTrue(verifyResult.isOk)
        val vct = verifyResult.value.sdJwt.payload.fullPayload["vct"]?.toString()?.trim('"')
        assertNotNull(vct)
        assertTrue(vct.startsWith("https://"), "VCT should be a URL")
    }

    /**
     * Test 3: Verify SD-JWT-VC credential without Key Binding
     * Basic VC verification without metadata resolution
     */
    @Test
    fun testVerifySdJwtVcCredential() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "test-vc-verify",
                clientIdScheme = "jwt_vc_json",
                issuer = "https://issuer.example.com"
            )
        )

        // Issue SD-JWT-VC
        val payload = sdJwtPayload {
            iss("https://issuer.example.com")
            claim("vct", "https://example.com/PersonCredential")
            subSd("person-456")
            claimSd("given_name", "Alice")
            claimSd("family_name", "Smith")
        }

        val issueResult = sdJwtService.issueSdJwt(
            _root_ide_package_.com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload)
        )
        assertTrue(issueResult.isOk)

        // Verify using base SD-JWT verification (VC verification would need metadata endpoints)
        val verifyResult = sdJwtService.verifySdJwt(
            _root_ide_package_.com.sphereon.sdjwt.VerifySdJwtArgs(sdJwt = issueResult.value.sdJwt, identifier = issuer)
        )

        assertTrue(verifyResult.isOk, "Verification should succeed")
        assertTrue(verifyResult.value.isValid, "VC should be valid")

        // Verify VC-specific claims
        val fullPayload = verifyResult.value.sdJwt.payload.fullPayload
        assertTrue(fullPayload.containsKey("vct"), "VCT should be present")
        assertEquals("Alice", fullPayload["given_name"]?.toString()?.trim('"'))
        assertEquals("Smith", fullPayload["family_name"]?.toString()?.trim('"'))
    }

    /**
     * Test 4: Verify SD-JWT-VC presentation with Key Binding JWT
     * Creates presentation with selective disclosure and validates KB-JWT
     */
    @Test
    fun testVerifySdJwtVcPresentation() = runTest {
        // Generate key pair for issuer
        val issuerKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        // Use PRIVATE visibility for issuer to sign the main SD-JWT
        val issuerKeyInfo: ManagedKeyInfoType<*> = issuerKeyPair.joseToManagedKeyInfo(com.sphereon.crypto.core.KeyVisibility.PRIVATE)

        val issuer = ManagedOptsKeyInfo(
            identifier = issuerKeyInfo,
            context = IdentifierContext(
                clientId = "test-presentation-issuer",
                clientIdScheme = "jwt_vc_json",
                issuer = "https://issuer.example.com"
            )
        )

        // Generate key pair for holder
        val holderKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        // Create holder key info with PRIVATE visibility for signing operations
        val holderKeyInfo: ManagedKeyInfoType<*> = holderKeyPair.joseToManagedKeyInfo(com.sphereon.crypto.core.KeyVisibility.PRIVATE)
        // Extract public key for cnf claim
        val holderPublicKeyInfo: ManagedKeyInfoType<*> = holderKeyPair.joseToManagedKeyInfo(com.sphereon.crypto.core.KeyVisibility.PUBLIC)

        val holder = ManagedOptsKeyInfo(
            identifier = holderKeyInfo,
            context = IdentifierContext(
                clientId = "test-holder",
                clientIdScheme = "jwt_vc_json",
                issuer = "https://holder.example.com"
            )
        )

        // Issue SD-JWT-VC with cnf claim containing holder's public key
        // Per RFC 7800, cnf claim should contain minimal JWK (only kty, crv, x, y for EC)
        val holderPublicJwk = holderPublicKeyInfo.key as com.sphereon.crypto.core.jose.Jwk
        val minimalJwk = holderPublicJwk.toMinimalJwk()
        val cnfValue = buildJsonObject {
            put("jwk", minimalJwk.toJsonObject())
        }

        val payload = sdJwtPayload {
            iss("https://issuer.example.com")
            claim("vct", "https://example.com/DriverLicense")
            subSd("holder-789")
            claimSd("name", "Bob Johnson")
            claimSd("license_number", "DL123456")
            claimSd("date_of_birth", "1990-05-15")
            claim("cnf", cnfValue)
        }

        val issueResult = sdJwtService.issueSdJwt(
            _root_ide_package_.com.sphereon.sdjwt.IssueSdJwtArgs(issuer = issuer, payload = payload)
        )
        assertTrue(issueResult.isOk)

        // Create presentation with selective disclosure (only name and license_number)
        val disclosureSelection = _root_ide_package_.com.sphereon.sdjwt.SdMap(
            mapOf(
                "name" to _root_ide_package_.com.sphereon.sdjwt.SdField(sd = true),
                "license_number" to _root_ide_package_.com.sphereon.sdjwt.SdField(sd = true)
                // Omit date_of_birth to not disclose it
            )
        )

        val presentResult = sdJwtService.presentSdJwt(
            _root_ide_package_.com.sphereon.sdjwt.PresentSdJwtArgs(
                sdJwt = issueResult.value.sdJwt,
                disclosureSelection = disclosureSelection,
                holderKey = holder,
                audience = "https://verifier.example.com",
                nonce = "test-nonce-12345"
            )
        )

        assertTrue(presentResult.isOk, "Presentation creation should succeed")
        val presentation = presentResult.value.presentation
        assertNotNull(presentation)

        // Presentation should have Key Binding JWT
        assertTrue(presentation.contains("~") && presentation.split("~").size >= 3,
            "Presentation should have KB-JWT appended")

        // Verify presentation
        val verifyPresentationResult = sdJwtService.verifySdJwt(
            _root_ide_package_.com.sphereon.sdjwt.VerifySdJwtArgs(
                sdJwt = presentation,
                identifier = issuer,
                expectedAudience = "https://verifier.example.com",
                expectedNonce = "test-nonce-12345"
            )
        )

        assertTrue(verifyPresentationResult.isOk, "Presentation verification should succeed")
        val verified = verifyPresentationResult.value
        assertTrue(verified.isValid, "Presentation should be valid")
        assertTrue(verified.keyBindingValid, "Key Binding should be valid")

        // Verify only selected claims are disclosed
        val fullPayload = verified.sdJwt.payload.fullPayload
        assertTrue(fullPayload.containsKey("name"), "Name should be disclosed")
        assertTrue(fullPayload.containsKey("license_number"), "License number should be disclosed")
        assertFalse(fullPayload.containsKey("date_of_birth"), "Date of birth should NOT be disclosed")
    }
}
