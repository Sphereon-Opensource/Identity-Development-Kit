package com.sphereon.crypto.jose.jws

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Minimal test to verify that dev.whyoleg.cryptography RSA-PSS works correctly
 * with default parameters (salt size = digest length).
 */
class MinimalPssTest {

    @Test
    fun testDevWhyolegRsaPssWithDefaults() = runTest {
        val provider = CryptographyProvider.Default
        val rsaPss = provider.get(RSA.PSS)

        // Generate key pair
        println("Generating RSA-PSS key pair...")
        val keyPair = rsaPss.keyPairGenerator(keySize = 2048.bits, digest = SHA256).generateKey()

        // Sign data
        val data = "Hello, World!".encodeToByteArray()
        println("Signing data with default salt size (should be 32 bytes for SHA-256)...")
        val signature = keyPair.privateKey.signatureGenerator().generateSignature(data)
        println("Signature generated: ${signature.size} bytes")

        // Verify signature
        println("Verifying signature with default salt size...")
        val isValid = keyPair.publicKey.signatureVerifier().tryVerifySignature(data, signature)
        println("Signature valid: $isValid")

        assertTrue(isValid, "RSA-PSS signature verification should succeed with library defaults")
    }
}
