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
 *
 */

package com.sphereon.crypto.cose

import com.sphereon.cbor.CborByteString
import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.CoseCryptoService
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.CryptoServicesImpl
import com.sphereon.crypto.core.testutil.createCryptoTestAppComponent
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseSign1Input
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for COSE Sign1 operations with actual cryptographic signing and verification.
 *
 * These tests verify:
 * - Key generation via KeyManagerService
 * - COSE Sign1 message creation with various algorithms
 * - Signature verification
 * - Error cases (tampered signatures, mismatched keys)
 */
class CoseSigningIntegrationTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var coseCryptoService: CoseCryptoService

    val app = createCryptoTestAppComponent(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("cose-test")

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config = SoftwareKmsProviderConfig(
            id = "cose-test-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        app as SoftwareKmsProviderFactoryImpl.Component
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceComponent().serviceExecution)

        // Get KeyManagerService from the session component
        keyManagerService = session.component.asKeyManagerServiceComponent().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        // Get CoseCryptoService from the session component
        coseCryptoService = (session.component as CryptoServicesImpl.Component).cryptoServices.cose
    }

    @Test
    fun testCoseSign1WithES256() = runTest {
        // Generate EC P-256 key pair
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        // Create COSE Sign1 input
        val payload = CborByteString("Hello COSE World!".encodeToByteArray())
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)

        val sign1Input = CoseSign1Input(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = payload
        )

        // Convert JWK key info to COSE key info for signing
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Sign without requiring X.509 chain
        val result = coseCryptoService.sign1<Any>(
            input = sign1Input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(result)
        assertNotNull(result.coseSign1)
        assertNotNull(result.coseSign1.signature)
        assertEquals(CoseAlgorithm.ES256, result.coseSign1.protectedHeader.alg)

        // Verify the signature
        val verifyResult = coseCryptoService.verify1(
            input = result.coseSign1,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(verifyResult)
        assertFalse(verifyResult.error, "Signature verification should not have error")
        assertTrue(verifyResult.message?.contains("valid", ignoreCase = true) == true || !verifyResult.critical,
            "Signature should be valid: ${verifyResult.message}")
    }

    @Test
    fun testCoseSign1WithES384() = runTest {
        // Generate EC P-384 key pair
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA384)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        // Create COSE Sign1 input
        val payload = CborByteString("Test payload for ES384".encodeToByteArray())
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES384)

        val sign1Input = CoseSign1Input(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = payload
        )

        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val result = coseCryptoService.sign1<Any>(
            input = sign1Input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(result)
        assertEquals(CoseAlgorithm.ES384, result.coseSign1.protectedHeader.alg)

        // Verify
        val verifyResult = coseCryptoService.verify1(
            input = result.coseSign1,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertFalse(verifyResult.error, "ES384 signature verification should not have error")
    }

    @Test
    fun testCoseSign1WithES512() = runTest {
        // Generate EC P-521 key pair
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA512)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        // Create COSE Sign1 input
        val payload = CborByteString("Test payload for ES512".encodeToByteArray())
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES512)

        val sign1Input = CoseSign1Input(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = payload
        )

        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val result = coseCryptoService.sign1<Any>(
            input = sign1Input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(result)
        assertEquals(CoseAlgorithm.ES512, result.coseSign1.protectedHeader.alg)

        // Verify
        val verifyResult = coseCryptoService.verify1(
            input = result.coseSign1,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertFalse(verifyResult.error, "ES512 signature verification should not have error")
    }

    @Test
    fun testCoseSign1RoundTripEncoding() = runTest {
        // Generate key
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val payload = CborByteString("Round trip test payload".encodeToByteArray())
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)

        val sign1Input = CoseSign1Input(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = payload
        )

        // Sign
        val result = coseCryptoService.sign1<Any>(
            input = sign1Input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Encode to CBOR bytes
        val encodedCbor = result.coseSign1.encodeCbor()
        assertNotNull(encodedCbor)

        // Decode back from CBOR
        val decodedSign1 = com.sphereon.crypto.core.cose.CoseSign1.decodeCbor<Any>(encodedCbor)
        assertNotNull(decodedSign1)

        // Verify decoded structure matches original
        assertEquals(result.coseSign1.protectedHeader.alg, decodedSign1.protectedHeader.alg)
        assertEquals(result.coseSign1.signature.value.size, decodedSign1.signature.value.size)

        // Verify the decoded CoseSign1 still validates
        val verifyResult = coseCryptoService.verify1(
            input = decodedSign1,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertFalse(verifyResult.error, "Decoded COSE Sign1 should verify successfully")
    }

    @Test
    fun testCoseSign1TamperedSignatureFails() = runTest {
        // Generate key
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val payload = CborByteString("Tamper test payload".encodeToByteArray())
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)

        val sign1Input = CoseSign1Input(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = payload
        )

        // Sign
        val result = coseCryptoService.sign1<Any>(
            input = sign1Input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Tamper with the signature by modifying bytes
        val originalSignature = result.coseSign1.signature.value
        val tamperedSignature = originalSignature.copyOf()
        tamperedSignature[0] = (tamperedSignature[0].toInt() xor 0xFF).toByte()

        val tamperedSign1 = result.coseSign1.copy(signature = CborByteString(tamperedSignature))

        // Verify should fail with tampered signature
        val verifyResult = coseCryptoService.verify1(
            input = tamperedSign1,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertTrue(verifyResult.error || verifyResult.critical,
            "Tampered signature should fail verification: ${verifyResult.message}")
    }

    @Test
    fun testCoseSign1TamperedPayloadFails() = runTest {
        // Generate key
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val originalPayload = CborByteString("Original payload".encodeToByteArray())
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)

        val sign1Input = CoseSign1Input(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = originalPayload
        )

        // Sign
        val result = coseCryptoService.sign1<Any>(
            input = sign1Input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Tamper with the payload
        val tamperedPayload = CborByteString("Tampered payload".encodeToByteArray())
        val tamperedSign1 = result.coseSign1.copy(payload = tamperedPayload)

        // Verify should fail with tampered payload
        val verifyResult = coseCryptoService.verify1(
            input = tamperedSign1,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertTrue(verifyResult.error || verifyResult.critical,
            "Tampered payload should fail verification: ${verifyResult.message}")
    }

    @Test
    fun testCoseSign1WrongKeyFails() = runTest {
        // Generate two different keys
        val signingKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val signingKeyInfo = signingKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val signingCoseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(signingKeyInfo)

        val wrongKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val wrongKeyInfo = wrongKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val wrongCoseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(wrongKeyInfo)

        val payload = CborByteString("Test payload".encodeToByteArray())
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)

        val sign1Input = CoseSign1Input(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = payload
        )

        // Sign with first key
        val result = coseCryptoService.sign1<Any>(
            input = sign1Input,
            keyInfo = signingCoseKeyInfo,
            requireX5Chain = false
        )

        // Verify with wrong key should fail
        val verifyResult = coseCryptoService.verify1(
            input = result.coseSign1,
            keyInfo = wrongCoseKeyInfo,
            requireX5Chain = false
        )

        assertTrue(verifyResult.error || verifyResult.critical,
            "Verification with wrong key should fail: ${verifyResult.message}")
    }

    @Test
    fun testCoseSign1MultipleAlgorithms() = runTest {
        val algorithms = listOf(
            SignatureAlgorithm.ECDSA_SHA256 to CoseAlgorithm.ES256,
            SignatureAlgorithm.ECDSA_SHA384 to CoseAlgorithm.ES384,
            SignatureAlgorithm.ECDSA_SHA512 to CoseAlgorithm.ES512
        )

        algorithms.forEach { (signatureAlg, coseAlg) ->
            println("Testing COSE Sign1 with algorithm: $coseAlg")

            // Generate key for this algorithm
            val managedKeyPair = keyManagerService.generateKey(alg = signatureAlg)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

            val payload = CborByteString("Test payload for $coseAlg".encodeToByteArray())
            val protectedHeader = CoseHeaderCbor(alg = coseAlg)

            val sign1Input = CoseSign1Input(
                protectedHeader = protectedHeader,
                unprotectedHeader = null,
                payload = payload
            )

            // Sign
            val result = coseCryptoService.sign1<Any>(
                input = sign1Input,
                keyInfo = coseKeyInfo,
                requireX5Chain = false
            )

            assertNotNull(result, "Sign1 result should not be null for $coseAlg")
            assertEquals(coseAlg, result.coseSign1.protectedHeader.alg, "Algorithm should match for $coseAlg")

            // Verify
            val verifyResult = coseCryptoService.verify1(
                input = result.coseSign1,
                keyInfo = coseKeyInfo,
                requireX5Chain = false
            )

            assertFalse(verifyResult.error, "Verification should succeed for $coseAlg: ${verifyResult.message}")
            println("PASS: $coseAlg")
        }
    }

    @Test
    fun testCoseSign1DetachedPayload() = runTest {
        // Generate key
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val payload = CborByteString("Detached payload test".encodeToByteArray())
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)

        val sign1Input = CoseSign1Input(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = payload
        )

        // Sign
        val result = coseCryptoService.sign1<Any>(
            input = sign1Input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Create detached copy (no payload)
        val detachedSign1 = result.coseSign1.detachedPayloadCopy()
        assertNotNull(detachedSign1)
        assertEquals(null, detachedSign1.payload?.value, "Detached copy should have null payload")
        assertEquals(result.coseSign1.signature, detachedSign1.signature, "Signature should be preserved")
    }

    @Test
    fun testCoseSign1WithKeyFromJwk() = runTest {
        // Generate a JWK key
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val jwkKeyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        // Get the underlying JWK
        val jwk = jwkKeyInfo.key
        assertNotNull(jwk, "JWK should not be null")

        // Create KeyInfo from the JWK directly
        val keyInfoFromJwk = KeyInfo(
            key = jwk,
            kid = jwkKeyInfo.kid,
            alias = jwkKeyInfo.alias,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
        )

        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfoFromJwk)

        val payload = CborByteString("JWK to COSE key test".encodeToByteArray())
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)

        val sign1Input = CoseSign1Input(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = payload
        )

        // Sign using COSE key derived from JWK
        val result = coseCryptoService.sign1<Any>(
            input = sign1Input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(result)

        // Verify
        val verifyResult = coseCryptoService.verify1(
            input = result.coseSign1,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertFalse(verifyResult.error, "JWK-derived COSE key signature should verify")
    }

    @Test
    fun testCoseSign1EmptyPayload() = runTest {
        // Generate key
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Empty payload
        val payload = CborByteString(ByteArray(0))
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)

        val sign1Input = CoseSign1Input(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = payload
        )

        // Sign
        val result = coseCryptoService.sign1<Any>(
            input = sign1Input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(result)

        // Verify
        val verifyResult = coseCryptoService.verify1(
            input = result.coseSign1,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertFalse(verifyResult.error, "Empty payload should sign and verify: ${verifyResult.message}")
    }

    @Test
    fun testCoseSign1LargePayload() = runTest {
        // Generate key
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Large payload (1MB)
        val largePayload = CborByteString(ByteArray(1024 * 1024) { it.toByte() })
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)

        val sign1Input = CoseSign1Input(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = largePayload
        )

        // Sign
        val result = coseCryptoService.sign1<Any>(
            input = sign1Input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(result)

        // Verify
        val verifyResult = coseCryptoService.verify1(
            input = result.coseSign1,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertFalse(verifyResult.error, "Large payload should sign and verify: ${verifyResult.message}")
    }
}
