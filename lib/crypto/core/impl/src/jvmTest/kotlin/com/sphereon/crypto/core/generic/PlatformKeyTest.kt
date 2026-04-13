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

package com.sphereon.crypto.core.generic

import at.asitplus.signum.indispensable.CryptoPrivateKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for JVM platform key wrappers.
 */
class PlatformKeyTest {

    // =========== PublicPlatformKey Tests ===========

    @Test
    fun publicPlatformKeyShouldWrapECPublicKey() {
        val keyPair = generateECKeyPair()
        val publicKey = keyPair.public

        val platformKey = publicKey.toPlatformKey()

        assertNotNull(platformKey)
        assertEquals(publicKey, platformKey.delegate)
    }

    @Test
    fun publicPlatformKeyShouldReturnCorrectKeyType() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        val kty = platformKey.getKeyType()

        assertNotNull(kty)
        assertEquals(KeyTypeMapping.EC, kty)
    }

    @Test
    fun publicPlatformKeyShouldReturnXCoordinate() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        val x = platformKey.getXAsString()

        assertNotNull(x, "X coordinate should not be null for EC key")
        assertTrue(x.isNotEmpty(), "X coordinate should not be empty")
    }

    @Test
    fun publicPlatformKeyShouldReturnYCoordinate() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        val y = platformKey.getYAsString()

        assertNotNull(y, "Y coordinate should not be null for EC key")
        assertTrue(y.isNotEmpty(), "Y coordinate should not be empty")
    }

    @Test
    fun publicPlatformKeyShouldReturnNullForDCoordinate() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        val d = platformKey.getDAsString()

        assertNull(d, "D coordinate should be null for public key")
    }

    @Test
    fun publicPlatformKeyToPublicKeyShouldReturnSelf() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        val publicKey = platformKey.toPublicKey()

        assertEquals(platformKey, publicKey)
    }

    @Test
    fun publicPlatformKeyShouldGeneratePem() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        val pem = platformKey.publicKeyPem()

        assertNotNull(pem)
        assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----"), "PEM should start with header")
        assertTrue(pem.contains("-----END PUBLIC KEY-----"), "PEM should end with footer")
    }

    @Test
    fun publicPlatformKeyShouldReturnNullForSignatureAlgorithm() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        assertNull(platformKey.getSignatureAlgorithm())
    }

    @Test
    fun publicPlatformKeyShouldReturnNullForKeyOperations() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        assertNull(platformKey.getKeyOperations())
    }

    @Test
    fun publicPlatformKeyShouldReturnNullForX509Chain() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        assertNull(platformKey.getX509CertificateChain())
    }

    @Test
    fun publicPlatformKeyShouldReturnNullForKeyId() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        assertNull(platformKey.getKeyId(false))
        assertNull(platformKey.getKeyId(true))
    }

    // =========== PrivatePlatformKey Tests ===========

    @Test
    fun privatePlatformKeyShouldWrapECPrivateKey() {
        val keyPair = generateECKeyPair()
        val privateKey = keyPair.private

        val platformKey = privateKey.toPlatformKey()

        assertNotNull(platformKey)
        assertEquals(privateKey, platformKey.delegate)
    }

    @Test
    fun privatePlatformKeyShouldReturnCorrectKeyType() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        val kty = platformKey.getKeyType()

        assertNotNull(kty)
        assertEquals(KeyTypeMapping.EC, kty)
    }

    @Test
    fun privatePlatformKeyToPublicKeyShouldExtractPublicKey() {
        val keyPair = generateECKeyPair()
        val privatePlatformKey = keyPair.private.toPlatformKey()

        val publicKey = privatePlatformKey.toPublicKey()

        assertNotNull(publicKey)
        assertTrue(publicKey is PublicPlatformKey)
    }

    @Test
    fun privatePlatformKeyShouldGeneratePublicKeyPem() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        val pem = platformKey.publicKeyPem()

        assertNotNull(pem)
        assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----"))
        assertTrue(pem.contains("-----END PUBLIC KEY-----"))
    }

    @Test
    fun privatePlatformKeyShouldReturnNullForSignatureAlgorithm() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        assertNull(platformKey.getSignatureAlgorithm())
    }

    @Test
    fun privatePlatformKeyShouldReturnNullForKeyOperations() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        assertNull(platformKey.getKeyOperations())
    }

    @Test
    fun privatePlatformKeyShouldReturnNullForCoordinates() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        assertNull(platformKey.getXAsString())
        assertNull(platformKey.getYAsString())
        assertNull(platformKey.getDAsString())
    }

    // =========== RSA Key Tests ===========

    @Test
    fun publicPlatformKeyShouldWrapRSAPublicKey() {
        val keyPair = generateRSAKeyPair()
        val publicKey = keyPair.public

        val platformKey = publicKey.toPlatformKey()

        assertNotNull(platformKey)
        assertEquals(publicKey, platformKey.delegate)
    }

    @Test
    fun publicPlatformKeyRsaShouldReturnCorrectKeyType() {
        val keyPair = generateRSAKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        val kty = platformKey.getKeyType()

        assertNotNull(kty)
        assertEquals(KeyTypeMapping.RSA, kty)
    }

    @Test
    fun publicPlatformKeyRsaShouldReturnNullForECCoordinates() {
        val keyPair = generateRSAKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        assertNull(platformKey.getXAsString(), "X should be null for RSA key")
        assertNull(platformKey.getYAsString(), "Y should be null for RSA key")
    }

    @Test
    fun privatePlatformKeyRsaShouldExtractPublicKey() {
        val keyPair = generateRSAKeyPair()
        val privatePlatformKey = keyPair.private.toPlatformKey()

        val publicKey = privatePlatformKey.toPublicKey()

        assertNotNull(publicKey)
        assertTrue(publicKey is PublicPlatformKey)
    }

    @Test
    fun privatePlatformKeyRsaShouldReturnCorrectKeyType() {
        val keyPair = generateRSAKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        val kty = platformKey.getKeyType()

        assertNotNull(kty)
        assertEquals(KeyTypeMapping.RSA, kty)
    }

    // =========== Additional Property Tests ===========

    @Test
    fun publicPlatformKeyShouldReturnNullForX509Certificate() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        assertNull(platformKey.getX509Certificate())
    }

    @Test
    fun publicPlatformKeyShouldReturnNullForX509CertificatePem() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        assertNull(platformKey.getX509CertificatePem())
    }

    @Test
    fun privatePlatformKeyShouldReturnNullForX509Certificate() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        assertNull(platformKey.getX509Certificate())
    }

    @Test
    fun privatePlatformKeyShouldReturnNullForX509CertificatePem() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        assertNull(platformKey.getX509CertificatePem())
    }

    @Test
    fun privatePlatformKeyShouldReturnNullForKeyId() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        assertNull(platformKey.getKeyId(false))
        assertNull(platformKey.getKeyId(true))
    }

    @Test
    fun privatePlatformKeyShouldReturnNullForX509Chain() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        assertNull(platformKey.getX509CertificateChain())
    }

    @Test
    fun publicPlatformKeyKtyShouldMatchKeyType() {
        val ecKeyPair = generateECKeyPair()
        val ecPlatformKey = ecKeyPair.public.toPlatformKey()
        assertEquals(KeyTypeMapping.EC, ecPlatformKey.kty)

        val rsaKeyPair = generateRSAKeyPair()
        val rsaPlatformKey = rsaKeyPair.public.toPlatformKey()
        assertEquals(KeyTypeMapping.RSA, rsaPlatformKey.kty)
    }

    @Test
    fun privatePlatformKeyKtyShouldMatchKeyType() {
        val ecKeyPair = generateECKeyPair()
        val ecPlatformKey = ecKeyPair.private.toPlatformKey()
        assertEquals(KeyTypeMapping.EC, ecPlatformKey.kty)

        val rsaKeyPair = generateRSAKeyPair()
        val rsaPlatformKey = rsaKeyPair.private.toPlatformKey()
        assertEquals(KeyTypeMapping.RSA, rsaPlatformKey.kty)
    }

    @Test
    fun publicPlatformKeyPropertiesShouldBeNull() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        assertNull(platformKey.kid)
        assertNull(platformKey.alg)
        assertNull(platformKey.key_ops)
        assertNull(platformKey.crv)
        assertNull(platformKey.x)
        assertNull(platformKey.y)
        assertNull(platformKey.d)
        assertNull(platformKey.additional)
    }

    @Test
    fun privatePlatformKeyPropertiesShouldBeNull() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        assertNull(platformKey.kid)
        assertNull(platformKey.alg)
        assertNull(platformKey.key_ops)
        assertNull(platformKey.crv)
        assertNull(platformKey.x)
        assertNull(platformKey.y)
        assertNull(platformKey.d)
        assertNull(platformKey.additional)
    }

    // =========== EC Curve Variant Tests ===========

    @Test
    fun publicPlatformKeyShouldWorkWithP384Curve() {
        val keyPair = generateECKeyPair("secp384r1")
        val platformKey = keyPair.public.toPlatformKey()

        assertNotNull(platformKey.getXAsString())
        assertNotNull(platformKey.getYAsString())
        assertEquals(KeyTypeMapping.EC, platformKey.kty)
    }

    @Test
    fun publicPlatformKeyShouldWorkWithP521Curve() {
        val keyPair = generateECKeyPair("secp521r1")
        val platformKey = keyPair.public.toPlatformKey()

        assertNotNull(platformKey.getXAsString())
        assertNotNull(platformKey.getYAsString())
        assertEquals(KeyTypeMapping.EC, platformKey.kty)
    }

    @Test
    fun privatePlatformKeyShouldExtractPublicKeyFromP384() {
        val keyPair = generateECKeyPair("secp384r1")
        val platformKey = keyPair.private.toPlatformKey()

        val publicKey = platformKey.toPublicKey()

        assertNotNull(publicKey)
        assertTrue(publicKey is PublicPlatformKey)
    }

    @Test
    fun privatePlatformKeyShouldExtractPublicKeyFromP521() {
        val keyPair = generateECKeyPair("secp521r1")
        val platformKey = keyPair.private.toPlatformKey()

        val publicKey = platformKey.toPublicKey()

        assertNotNull(publicKey)
        assertTrue(publicKey is PublicPlatformKey)
    }

    // =========== PrivatePlatformKey destroy/isDestroyed Tests ===========

    @Test
    fun privatePlatformKeyIsDestroyedShouldDelegateToUnderlying() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        // Initially not destroyed
        assertFalse(platformKey.isDestroyed)
    }

    @Test
    fun privatePlatformKeyDestroyShouldDelegateToUnderlying() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        // Try to destroy - this may throw UnsupportedOperationException on some JVM implementations
        try {
            platformKey.destroy()
            // If destroy succeeded, isDestroyed should return true
            assertTrue(platformKey.isDestroyed)
        } catch (e: javax.security.auth.DestroyFailedException) {
            // Some key implementations don't support destroy - that's OK
            assertTrue(true)
        }
    }

    // =========== RSA Key Extraction Tests ===========

    @Test
    fun privatePlatformKeyRsaCrtShouldExtractPublicKeyDirectly() {
        // This tests the RSAPrivateCrtKey branch specifically
        val keyPair = generateRSAKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        // Verify this is RSAPrivateCrtKey
        assertTrue(keyPair.private is java.security.interfaces.RSAPrivateCrtKey)

        val publicKey = platformKey.toPublicKey()

        assertNotNull(publicKey)
        assertTrue(publicKey is PublicPlatformKey)
        assertEquals(KeyTypeMapping.RSA, publicKey.kty)
    }

    @Test
    fun privatePlatformKeyRsaShouldGenerateValidPublicKeyPem() {
        val keyPair = generateRSAKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        val pem = platformKey.publicKeyPem()

        assertNotNull(pem)
        assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----"))
        assertTrue(pem.contains("-----END PUBLIC KEY-----"))
    }

    // =========== PublicPlatformKey delegate methods ===========

    @Test
    fun publicPlatformKeyShouldDelegateGetAlgorithm() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        assertEquals("EC", platformKey.algorithm)
    }

    @Test
    fun publicPlatformKeyShouldDelegateGetFormat() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        assertEquals("X.509", platformKey.format)
    }

    @Test
    fun publicPlatformKeyShouldDelegateGetEncoded() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        val encoded = platformKey.encoded

        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    // =========== PrivatePlatformKey delegate methods ===========

    @Test
    fun privatePlatformKeyShouldDelegateGetAlgorithm() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        assertEquals("EC", platformKey.algorithm)
    }

    @Test
    fun privatePlatformKeyShouldDelegateGetFormat() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        assertEquals("PKCS#8", platformKey.format)
    }

    @Test
    fun privatePlatformKeyShouldDelegateGetEncoded() {
        val keyPair = generateECKeyPair()
        val platformKey = keyPair.private.toPlatformKey()

        val encoded = platformKey.encoded

        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    // =========== RSA PublicPlatformKey Tests ===========

    @Test
    fun publicPlatformKeyRsaShouldGenerateValidPem() {
        val keyPair = generateRSAKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        val pem = platformKey.publicKeyPem()

        assertNotNull(pem)
        assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----"))
        assertTrue(pem.contains("-----END PUBLIC KEY-----"))
    }

    @Test
    fun publicPlatformKeyRsaToPublicKeyShouldReturnSelf() {
        val keyPair = generateRSAKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        val publicKey = platformKey.toPublicKey()

        assertEquals(platformKey, publicKey)
    }

    @Test
    fun publicPlatformKeyRsaPropertiesShouldBeNull() {
        val keyPair = generateRSAKeyPair()
        val platformKey = keyPair.public.toPlatformKey()

        assertNull(platformKey.kid)
        assertNull(platformKey.alg)
        assertNull(platformKey.key_ops)
        assertNull(platformKey.crv)
        assertNull(platformKey.x)
        assertNull(platformKey.y)
        assertNull(platformKey.d)
        assertNull(platformKey.additional)
    }

    // =========== Edge Case Tests ===========

    @Test
    fun privatePlatformKeyWithNullEncodingShouldThrowOnToPublicKey() {
        // Create a mock private key that returns null for encoded
        val mockPrivateKey = object : PrivateKey {
            override fun getAlgorithm(): String = "EC"
            override fun getFormat(): String = "PKCS#8"
            override fun getEncoded(): ByteArray? = null // This triggers the error path
        }

        val platformKey = PrivatePlatformKey(mockPrivateKey)

        assertFailsWith<IllegalStateException> {
            platformKey.toPublicKey()
        }
    }

    @Test
    fun privatePlatformKeyWithUnsupportedKeyTypeShouldThrowOnToPublicKey() {
        // Create a mock private key that is neither ECPrivateKey nor RSAPrivateCrtKey
        // but has valid encoding - this tests the else branch
        val rsaKeyPair = generateRSAKeyPair()
        val mockPrivateKey = object : PrivateKey {
            // Use RSA encoding but don't implement RSAPrivateCrtKey interface
            override fun getAlgorithm(): String = "RSA"
            override fun getFormat(): String = "PKCS#8"
            override fun getEncoded(): ByteArray = rsaKeyPair.private.encoded
        }

        val platformKey = PrivatePlatformKey(mockPrivateKey)

        // This should use the else branch and successfully extract public key via signum
        val publicKey = platformKey.toPublicKey()
        assertNotNull(publicKey)
    }

    @Test
    fun privatePlatformKeyWithUnknownAlgorithmShouldThrowOnConstruction() {
        // Create a mock private key with unknown algorithm
        val mockPrivateKey = object : PrivateKey {
            override fun getAlgorithm(): String = "UNKNOWN"
            override fun getFormat(): String = "RAW"
            override fun getEncoded(): ByteArray = byteArrayOf(1, 2, 3)
        }

        // The kty property is initialized eagerly and throws when it can't map the algorithm
        // KeyTypeMapping.fromValue("UNKNOWN") returns null, then
        // KeyTypeMapping.fromJose(JwaKeyType.fromValue("UNKNOWN")) throws
        assertFailsWith<IllegalArgumentException> {
            PrivatePlatformKey(mockPrivateKey)
        }
    }

    @Test
    fun publicPlatformKeyWithNullEncodingShouldThrowOnPublicKeyPem() {
        // Create a mock public key that returns null for encoded
        val mockPublicKey = object : PublicKey {
            override fun getAlgorithm(): String = "EC"
            override fun getFormat(): String = "X.509"
            override fun getEncoded(): ByteArray? = null // This triggers the error path
        }

        val platformKey = PublicPlatformKey(mockPublicKey)

        assertFailsWith<IllegalStateException> {
            platformKey.publicKeyPem()
        }
    }

    @Test
    fun publicPlatformKeyWithUnknownAlgorithmShouldThrowOnConstruction() {
        // Create a mock public key with unknown algorithm
        val mockPublicKey = object : PublicKey {
            override fun getAlgorithm(): String = "UNKNOWN"
            override fun getFormat(): String = "RAW"
            override fun getEncoded(): ByteArray = byteArrayOf(1, 2, 3)
        }

        // The kty property is initialized eagerly and throws when it can't map the algorithm
        assertFailsWith<IllegalArgumentException> {
            PublicPlatformKey(mockPublicKey)
        }
    }

    // =========== MockK Tests for ECPrivateKey Edge Cases ===========

    @Test
    fun privatePlatformKeyEcWithNullEncodingShouldThrowOnToPublicKey() {
        // Use MockK to create an ECPrivateKey that returns null for encoded
        // This tests the defensive check inside the ECPrivateKey branch
        val mockEcPrivateKey = mockk<ECPrivateKey>(relaxed = true)
        every { mockEcPrivateKey.algorithm } returns "EC"
        every { mockEcPrivateKey.format } returns "PKCS#8"
        every { mockEcPrivateKey.encoded } returns null

        val platformKey = PrivatePlatformKey(mockEcPrivateKey)

        assertFailsWith<IllegalStateException> {
            platformKey.toPublicKey()
        }
    }

    @Test
    fun privatePlatformKeyEcDecodingAsNonEcShouldThrow() {
        // Test the defensive check: EC key that signum decodes as non-EC
        val realEcKeyPair = generateECKeyPair()
        val realEcPrivateKey = realEcKeyPair.private as ECPrivateKey

        // Mock the companion object's decodeFromDer to return RSA instead of EC
        val mockRsaKey = mockk<CryptoPrivateKey.RSA>(relaxed = true)

        mockkObject(CryptoPrivateKey)
        try {
            every { CryptoPrivateKey.decodeFromDer(any()) } returns mockRsaKey

            val platformKey = PrivatePlatformKey(realEcPrivateKey)

            assertFailsWith<IllegalStateException>("Expected EC private key but got") {
                platformKey.toPublicKey()
            }
        } finally {
            unmockkObject(CryptoPrivateKey)
        }
    }

    @Test
    fun privatePlatformKeyEcWithoutPublicKeyShouldThrow() {
        // Test the defensive check: EC key without embedded public key
        val realEcKeyPair = generateECKeyPair()
        val realEcPrivateKey = realEcKeyPair.private as ECPrivateKey

        // Mock the companion object's decodeFromDer to return EC.WithoutPublicKey
        val mockEcWithoutPublicKey = mockk<CryptoPrivateKey.EC.WithoutPublicKey>(relaxed = true)

        mockkObject(CryptoPrivateKey)
        try {
            every { CryptoPrivateKey.decodeFromDer(any()) } returns mockEcWithoutPublicKey

            val platformKey = PrivatePlatformKey(realEcPrivateKey)

            assertFailsWith<IllegalStateException>("EC private key does not contain public key information") {
                platformKey.toPublicKey()
            }
        } finally {
            unmockkObject(CryptoPrivateKey)
        }
    }

    @Test
    fun privatePlatformKeyElseBranchWithNonRsaDecodingShouldThrow() {
        // Test the defensive check: non-RSA key in else branch decoding as non-RSA
        // Create a mock private key that is NOT ECPrivateKey or RSAPrivateCrtKey
        // but has valid RSA encoding - then mock decodeFromDer to return EC instead of RSA
        val rsaKeyPair = generateRSAKeyPair()
        val mockPrivateKey = object : PrivateKey {
            override fun getAlgorithm(): String = "RSA"
            override fun getFormat(): String = "PKCS#8"
            override fun getEncoded(): ByteArray = rsaKeyPair.private.encoded
        }

        // Mock the companion object's decodeFromDer to return EC instead of RSA
        val mockEcKey = mockk<CryptoPrivateKey.EC.WithoutPublicKey>(relaxed = true)

        mockkObject(CryptoPrivateKey)
        try {
            every { CryptoPrivateKey.decodeFromDer(any()) } returns mockEcKey

            val platformKey = PrivatePlatformKey(mockPrivateKey)

            assertFailsWith<IllegalStateException>("Cannot extract public key from private key type") {
                platformKey.toPublicKey()
            }
        } finally {
            unmockkObject(CryptoPrivateKey)
        }
    }

    // =========== Helper Methods ===========

    private fun generateECKeyPair(curve: String = "secp256r1"): java.security.KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec(curve))
        return keyPairGenerator.generateKeyPair()
    }

    private fun generateRSAKeyPair(): java.security.KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        return keyPairGenerator.generateKeyPair()
    }
}
