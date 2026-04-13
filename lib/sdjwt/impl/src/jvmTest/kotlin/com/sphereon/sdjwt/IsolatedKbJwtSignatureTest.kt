package com.sphereon.sdjwt

import com.sphereon.sdjwt.createJvmCryptoTestAppComponent

import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.jose.jws.*
import com.sphereon.crypto.kms.KeyManagerServiceImpl
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwkOpts
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Isolated test to capture KB-JWT signature and verify it separately
 * This helps debug the signature verification issue
 */
class IsolatedKbJwtSignatureTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var jwtService: JwtService

    val app = createJvmCryptoTestAppComponent(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("isolated-kb-jwt-test")

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config = SoftwareKmsProviderConfig(
            id = "isolated-kb-test-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        val appMerged = app as JvmCryptoTestAppComponent
        val softwareKmsProvider = appMerged.softwareKmsProvider.create(config, session.asCoreApiServiceComponent().serviceExecution)

        // Get KeyManagerService and JwtService from the session component
        keyManagerService = session.component.asKeyManagerServiceComponent().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        jwtService = (session.component as com.sphereon.crypto.jose.jws.JwtServiceImpl.Component).jwtService
    }

    /**
     * Step 1: Generate a key, sign a KB-JWT, and print all the details
     */
    @Test
    fun step1_GenerateAndSignKbJwt() = runTest {
        println("\n=== STEP 1: Generate Key and Sign KB-JWT ===\n")

        // Generate a key pair
        val keyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)

        // Get private key for signing
        val privateKeyInfo: ManagedKeyInfoType<*> = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val privateJwk = privateKeyInfo.key as com.sphereon.crypto.core.jose.Jwk

        // Get public key
        val publicKeyInfo: ManagedKeyInfoType<*> = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
        val publicJwk = publicKeyInfo.key as com.sphereon.crypto.core.jose.Jwk

        // Get minimal public key for CNF claim
        val minimalPublicJwk = publicJwk.toMinimalJwk()

        println("Private key JWK (full):")
        println(privateJwk.toJsonString())
        println("\nPublic key JWK (full):")
        println(publicJwk.toJsonString())
        println("\nMinimal public key JWK (for CNF):")
        println(minimalPublicJwk.toJsonString())

        // Create KB-JWT payload
        val kbPayload = buildJsonObject {
            put("aud", "https://verifier.example.com")
            put("nonce", "test-nonce-12345")
            put("iat", 1234567890)
            put("sd_hash", "test-hash-value")
        }

        println("\nKB-JWT payload:")
        println(kbPayload.toString())

        // Create managed key opts for signing
        val holderKey = ManagedOptsKeyInfo(
            identifier = privateKeyInfo,
            context = IdentifierContext()
        )

        // Create KB-JWT with proper options
        val kbOpts = CreateJwsOpts(
            noIssPayloadUpdate = true,  // Don't add iss/client_id
            noIdentifierInHeader = false // DO embed JWK in header
        )

        val kbJwsArgs = CreateJwsArgs(
            issuer = holderKey,
            payload = kbPayload,
            mode = JwsIdentifierMode.JWK,
            opts = kbOpts
        )

        val kbResult = jwtService.createJwsCompact(kbJwsArgs)
        if (!kbResult.isOk) {
            throw AssertionError("Failed to create KB-JWT: ${kbResult.error?.message}")
        }

        val kbJwt = kbResult.value.jwt

        println("\n=== Generated KB-JWT ===")
        println(kbJwt)

        // Parse and print parts
        val parts = kbJwt.split(".")
        require(parts.size == 3) { "KB-JWT should have 3 parts" }

        val header = JwsUtils.decodeBase64UrlToJson(parts[0])
        val payload = JwsUtils.decodeBase64UrlToJson(parts[1])
        val signature = parts[2]

        println("\n=== KB-JWT Parts ===")
        println("Header: $header")
        println("\nPayload: $payload")
        println("\nSignature (base64url): $signature")

        // Extract header JWK
        val headerJwkElement = header["jwk"]
        require(headerJwkElement is kotlinx.serialization.json.JsonObject) { "Header should contain JWK" }
        val headerJwk = com.sphereon.crypto.core.jose.Jwk.fromJsonObject(headerJwkElement)

        println("\n=== Header JWK ===")
        println(headerJwk.toJsonString())

        println("\n=== COPY THESE VALUES FOR STEP 2 ===")
        println("KB_JWT=\"$kbJwt\"")
        println("MINIMAL_JWK='${minimalPublicJwk.toJsonString()}'")
    }

    /**
     * Step 2: Verify a captured KB-JWT using the minimal public key
     *
     * INSTRUCTIONS:
     * 1. Run step1_GenerateAndSignKbJwt first
     * 2. Copy the KB_JWT and MINIMAL_JWK values from the output
     * 3. Paste them into the variables below
     * 4. Run this test to verify the signature
     */
    @Test
    fun step2_VerifyCapturedKbJwt() = runTest {
        println("\n=== STEP 2: Verify Captured KB-JWT ===\n")

        // PASTE THE VALUES FROM STEP 1 HERE:
        val capturedKbJwt = "PASTE_KB_JWT_HERE"
        val capturedMinimalJwkJson = """PASTE_MINIMAL_JWK_HERE"""

        // Skip if not yet pasted
        if (capturedKbJwt == "PASTE_KB_JWT_HERE") {
            println("WARNING: Run step1_GenerateAndSignKbJwt first and paste the values here")
            return@runTest
        }

        println("Captured KB-JWT: $capturedKbJwt")
        println("\nCapturing minimal JWK: $capturedMinimalJwkJson")

        // Parse the minimal JWK from JSON
        val minimalJwk = com.sphereon.crypto.core.jose.Jwk.fromJsonObject(
            Json.parseToJsonElement(capturedMinimalJwkJson).jsonObject
        )
        println("\nParsed minimal JWK:")
        println(minimalJwk.toJsonString())

        // Create external identifier from minimal JWK
        val externalIdentifier = ExternalIdentifierJwkOpts(identifier = minimalJwk)

        // Verify the KB-JWT signature
        val verifyArgs = VerifyJwsArgs(
            jws = JwsCompact(capturedKbJwt),
            identifier = externalIdentifier
        )

        val verifyResult = jwtService.verifyJws(verifyArgs)

        println("\n=== Verification Result ===")
        println("Result OK: ${verifyResult.isOk}")
        if (verifyResult.isErr) {
            println("Error: ${verifyResult.error.message}")
        } else {
            println("Is Valid: ${verifyResult.value.isValid}")
            if (!verifyResult.value.isValid) {
                println("Error Messages: ${verifyResult.value.errorMessages}")
            }
        }

        assertTrue(verifyResult.isOk, "Verification should complete: ${verifyResult.error?.message}")
        assertTrue(verifyResult.value.isValid, "Signature should be valid. Errors: ${verifyResult.value.errorMessages}")

        println("\nPASS: KB-JWT signature verified successfully!")
    }

    /**
     * Step 3: All-in-one test - sign and immediately verify
     */
    @Test
    fun step3_SignAndVerifyImmediately() = runTest {
        println("\n=== STEP 3: Sign and Verify Immediately ===\n")

        // Generate a key pair
        val keyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)

        // Get private key for signing
        val privateKeyInfo: ManagedKeyInfoType<*> = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        // Get public key
        val publicKeyInfo: ManagedKeyInfoType<*> = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
        val publicJwk = publicKeyInfo.key as com.sphereon.crypto.core.jose.Jwk
        val minimalPublicJwk = publicJwk.toMinimalJwk()

        println("Minimal public JWK: ${minimalPublicJwk.toJsonString()}")

        // Create KB-JWT payload
        val kbPayload = buildJsonObject {
            put("aud", "https://verifier.example.com")
            put("nonce", "test-nonce-67890")
            put("iat", 1234567890)
            put("sd_hash", "immediate-test-hash")
        }

        // Create managed key opts for signing
        val holderKey = ManagedOptsKeyInfo(
            identifier = privateKeyInfo,
            context = IdentifierContext()
        )

        // Sign the KB-JWT
        val kbOpts = CreateJwsOpts(
            noIssPayloadUpdate = true,
            noIdentifierInHeader = false
        )

        val kbJwsArgs = CreateJwsArgs(
            issuer = holderKey,
            payload = kbPayload,
            mode = JwsIdentifierMode.JWK,
            opts = kbOpts
        )

        val kbResult = jwtService.createJwsCompact(kbJwsArgs)
        assertTrue(kbResult.isOk, "Failed to create KB-JWT")

        val kbJwt = kbResult.value.jwt
        println("\nGenerated KB-JWT: $kbJwt")

        // NOW VERIFY using the minimal public key
        val externalIdentifier = ExternalIdentifierJwkOpts(identifier = minimalPublicJwk)

        val verifyArgs = VerifyJwsArgs(
            jws = JwsCompact(kbJwt),
            identifier = externalIdentifier
        )

        val verifyResult = jwtService.verifyJws(verifyArgs)

        println("\n=== Immediate Verification Result ===")
        println("Result OK: ${verifyResult.isOk}")
        if (verifyResult.isErr) {
            println("Error: ${verifyResult.error.message}")
        } else {
            println("Is Valid: ${verifyResult.value.isValid}")
            if (!verifyResult.value.isValid) {
                println("Error Messages: ${verifyResult.value.errorMessages}")
            }
        }

        assertTrue(verifyResult.isOk, "Verification should complete")
        assertTrue(verifyResult.value.isValid, "Signature should be valid. Errors: ${verifyResult.value.errorMessages}")

        println("\nPASS: Immediate sign and verify successful!")
    }
}
