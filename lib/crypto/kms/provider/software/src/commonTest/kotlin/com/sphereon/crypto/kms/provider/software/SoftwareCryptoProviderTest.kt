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

package com.sphereon.crypto.kms.provider.software

import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.kms.provider.software.testutil.SoftwareKmsTestContext
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SoftwareCryptoProviderTest {
    private lateinit var softwareKMSProvider: SoftwareKmsProvider

    val ctx = SoftwareKmsTestContext("test", this)

    @BeforeTest
    fun setUp() {
        val config = SoftwareKmsProviderConfig(id = "test-ecdsa", cryptographyProvider = CryptographyProvider.Default.name)
        softwareKMSProvider = ctx.softwareKmsProviderFactory.create(config, ctx.session.sessionExecution)
    }

    @Test
    fun testSupportedCurves() {
        val curves = softwareKMSProvider.supportedCurves()
        assertContentEquals(
            arrayOf(Curve.P_256, Curve.P_384, Curve.P_521),
            curves
        )
    }

    @Test
    fun testIsSupportedCurve() {
        assertTrue(softwareKMSProvider.isSupportedCurve(Curve.P_256))
        assertTrue(softwareKMSProvider.isSupportedCurve(Curve.P_384))
        assertTrue(softwareKMSProvider.isSupportedCurve(Curve.P_521))
        assertFalse(softwareKMSProvider.isSupportedCurve(Curve.X25519))
    }

    @Test
    fun testSupportedDigests() {
        val digests = softwareKMSProvider.supportedDigests()
        assertContentEquals(
            arrayOf(DigestAlg.SHA256, DigestAlg.SHA384, DigestAlg.SHA512),
            digests
        )
    }

    @Test
    fun testGenerateKeyAsync() = runTest {
        val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        assertNotNull(managedKeyPair)
        assertNotNull(managedKeyPair.cborToManagedKeyInfo().key.kid)
    }

    @Test
    fun testValidEcdsaRawSignatureAndVerification() = runTest {
        val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
        assertNotNull(keyInfo)
        val signature = softwareKMSProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
        assertNotNull(signature)
        val verification = softwareKMSProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray())
        assertTrue(verification)
    }

    @Test
    fun testInvalidEcdsaRawSignatureAndVerification() = runTest {
        val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
        assertNotNull(keyInfo)
        val signature = softwareKMSProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
        assertNotNull(signature)
        val verification = softwareKMSProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test2".encodeToByteArray())
        assertFalse(verification)
    }

    @Test
    fun testValidPSSRawSignatureAndVerification() = runTest {
        val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
        assertNotNull(keyInfo)
        val signature = softwareKMSProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
        assertNotNull(signature)
        val verification = softwareKMSProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray())
        assertTrue(verification)
    }

    @Test
    fun testInvalidPSSRawSignatureAndVerification() = runTest {
        val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
        assertNotNull(keyInfo)
        val signature = softwareKMSProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
        assertNotNull(signature)
        val verification = softwareKMSProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test#".encodeToByteArray())
        assertFalse(verification)
    }

    @Test
    fun testValidRsaPkcs1RawSignatureAndVerification() = runTest {
        val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
        assertNotNull(keyInfo)
        val signature = softwareKMSProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
        assertNotNull(signature)
        val verification = softwareKMSProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray())
        assertTrue(verification)
    }

    @Test
    fun testInvalidRsaPkcs1RawSignatureAndVerification() = runTest {
        val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
        assertNotNull(keyInfo)
        val signature = softwareKMSProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
        assertNotNull(signature)
        val verification = softwareKMSProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test#".encodeToByteArray())
        assertFalse(verification)
    }

    @Test
    fun testAllAlgSignaturesAndVerification() = runTest {
        softwareKMSProvider.supportedSignatureAlgorithms()
            .filter { it.cryptoAlgorithm != com.sphereon.crypto.core.generic.CryptoAlg.HMAC }
            .forEach { alg ->
                val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = alg)
                val keyInfo = managedKeyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
                assertNotNull(keyInfo)
                val signature = softwareKMSProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
                assertNotNull(signature)
                val verification = softwareKMSProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray())
                assertTrue(verification)
            }
    }

    @Test
    fun testGenerateKeyThrowsExceptionForUnsupportedCurve() = runTest {
        val unsupportedAlg = SignatureAlgorithm.ED25519
        val exception = assertFailsWith<IllegalArgumentException> {
            softwareKMSProvider.generateKeyAsync(alg = unsupportedAlg)
        }
        assertEquals("Curve ${unsupportedAlg.curve!!.jose.value} not supported for EcDSA", exception.message)
    }

    @Test
    fun testSupportedKeyTypes() {
        val keyTypes = softwareKMSProvider.supportedKeyTypes()
        assertContentEquals(arrayOf(KeyTypeMapping.EC, KeyTypeMapping.RSA, KeyTypeMapping.Symmetric), keyTypes)
    }

    @Test
    fun testSupportedAlg() {
        val algorithms = softwareKMSProvider.supportedSignatureAlgorithms()
        assertContentEquals(
            arrayOf(
                SignatureAlgorithm.ECDSA_SHA256,
                SignatureAlgorithm.ECDSA_SHA384,
                SignatureAlgorithm.ECDSA_SHA512,
                SignatureAlgorithm.RSA_RAW,
                SignatureAlgorithm.RSA_SHA256,
                SignatureAlgorithm.RSA_SHA384,
                SignatureAlgorithm.RSA_SHA512,
                SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
                SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
                SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
                SignatureAlgorithm.HMAC_SHA256,
                SignatureAlgorithm.HMAC_SHA384,
                SignatureAlgorithm.HMAC_SHA512
            ),
            algorithms
        )
    }

    @Test
    fun testGenerateHmacSha256Key() = runTest {
        val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.HMAC_SHA256)
        assertNotNull(managedKeyPair)
        assertNotNull(managedKeyPair.kid)
        assertNotNull(managedKeyPair.jose)
        // Public JWK should not contain secret material
        val publicJwk = managedKeyPair.jose.publicJwk
        assertNotNull(publicJwk)
        assertEquals(com.sphereon.crypto.core.jose.JwaKeyType.oct, publicJwk.kty)
        assertEquals(null, publicJwk.k, "Public JWK must not expose symmetric key material")
    }

    @Test
    fun testGenerateHmacSha384Key() = runTest {
        val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.HMAC_SHA384)
        assertNotNull(managedKeyPair)
        assertNotNull(managedKeyPair.kid)
    }

    @Test
    fun testGenerateHmacSha512Key() = runTest {
        val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.HMAC_SHA512)
        assertNotNull(managedKeyPair)
        assertNotNull(managedKeyPair.kid)
    }

    @Test
    fun testHmacGenerateMacAndVerify() = runTest {
        // Generate an HMAC key with persistence so generateMac/verifyMac can look it up
        val persistConfig = SoftwareKmsProviderConfig(
            id = "test-hmac",
            cryptographyProvider = CryptographyProvider.Default.name,
            persistKeysDuringGeneration = true,
            exposePrivateKeysDuringGeneration = true
        )
        val hmacProvider = ctx.softwareKmsProviderFactory.create(persistConfig, ctx.session.sessionExecution)

        val managedKeyPair = hmacProvider.generateKeyAsync(alg = SignatureAlgorithm.HMAC_SHA256)
        val kid = managedKeyPair.kid
        assertNotNull(kid)

        val message = "Hello HMAC".encodeToByteArray()
        val mac = hmacProvider.generateMac(keyId = kid, message = message, digestAlgorithm = DigestAlg.SHA256)
        assertNotNull(mac)
        assertTrue(mac.isNotEmpty())

        // Verify the MAC is valid
        val isValid = hmacProvider.verifyMac(keyId = kid, message = message, mac = mac, digestAlgorithm = DigestAlg.SHA256)
        assertTrue(isValid, "MAC verification should succeed for correct message")

        // Verify the MAC fails for tampered message
        val isInvalid = hmacProvider.verifyMac(keyId = kid, message = "Tampered".encodeToByteArray(), mac = mac, digestAlgorithm = DigestAlg.SHA256)
        assertFalse(isInvalid, "MAC verification should fail for tampered message")
    }

    @Test
    fun testHmacKeyRoundTripThroughKeyStore() = runTest {
        val persistConfig = SoftwareKmsProviderConfig(
            id = "test-hmac-store",
            cryptographyProvider = CryptographyProvider.Default.name,
            persistKeysDuringGeneration = true,
            exposePrivateKeysDuringGeneration = true
        )
        val hmacProvider = ctx.softwareKmsProviderFactory.create(persistConfig, ctx.session.sessionExecution)

        val managedKeyPair = hmacProvider.generateKeyAsync(
            alias = "my-hmac-key",
            alg = SignatureAlgorithm.HMAC_SHA256
        )
        assertEquals("my-hmac-key", managedKeyPair.alias)

        // Retrieve the key from the store
        val retrieved = hmacProvider.getKey(com.sphereon.crypto.core.KeyInfo<com.sphereon.crypto.core.jose.Jwk>(alias = "my-hmac-key"))
        assertNotNull(retrieved)
        assertEquals("my-hmac-key", retrieved.alias)
        assertEquals(KeyTypeMapping.Symmetric, retrieved.keyType)
    }

    @Test
    fun testCapabilitiesIncludeSymmetric() {
        val capabilities = softwareKMSProvider.getCapabilities()
        assertTrue(capabilities.supportedKeyTypes.contains(KeyTypeMapping.Symmetric))
        assertTrue(capabilities.supportedCryptoAlgorithms.contains(com.sphereon.crypto.core.generic.CryptoAlg.HMAC))

        val macGenOp = capabilities.operations.find { it.operation == com.sphereon.crypto.core.kms.KmsProviderOperation.GENERATE_MAC }
        assertNotNull(macGenOp)
        assertTrue(macGenOp.supported)
        assertNotNull(macGenOp.signatureAlgorithms)
        assertTrue(macGenOp.signatureAlgorithms!!.contains(SignatureAlgorithm.HMAC_SHA256))
    }

    @Test
    fun testKeyTypeMappingSymmetricFromJose() {
        val mapping = KeyTypeMapping.fromJose(com.sphereon.crypto.core.jose.JwaKeyType.oct)
        assertEquals(KeyTypeMapping.Symmetric, mapping)
    }
}
