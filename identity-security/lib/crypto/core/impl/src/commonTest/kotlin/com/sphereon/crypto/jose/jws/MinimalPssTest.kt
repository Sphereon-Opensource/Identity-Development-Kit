/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.crypto.jose.jws

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Minimal test to verify that dev.whyoleg.cryptography RSA-PSS works correctly
 * with default parameters (salt size = digest length).
 */
class MinimalPssTest {
    @Test
    fun testDevWhyolegRsaPssWithDefaults() =
        runTest {
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
