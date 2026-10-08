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

package com.sphereon.oauth2.server.authorization.impl.oidc

import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests at_hash and c_hash computation per OpenID Connect Core Section 3.1.3.3:
 *
 * 1. Hash the ASCII octets of the token with SHA-256
 * 2. Take the left-most half of the hash (128 bits = 16 bytes)
 * 3. base64url-encode it (no padding)
 *
 * Uses the same algorithm as CreateIdTokenCommandImpl.computeTokenHash()
 */
class AtHashComputationTest {
    /**
     * Computes token hash per OIDC Core Section 3.1.3.3,
     * matching CreateIdTokenCommandImpl.computeTokenHash().
     */
    private fun computeTokenHash(input: String): String {
        val bytes = input.encodeToByteArray()
        val hashBytes = hash(bytes, DigestAlg.SHA256)
        val leftHalf = hashBytes.copyOfRange(0, hashBytes.size / 2)
        return leftHalf.encodeToBase64Url()
    }

    @Test
    fun atHashProducesCorrectLength() {
        val token = "ya29.example-access-token-value"
        val atHash = computeTokenHash(token)

        assertNotNull(atHash)
        assertTrue(atHash.length in 22..24, "at_hash length should be 22-24 chars, got ${atHash.length}")
    }

    @Test
    fun atHashIsDeterministic() {
        val token = "test-access-token-12345"
        val hash1 = computeTokenHash(token)
        val hash2 = computeTokenHash(token)

        assertEquals(hash1, hash2, "Same input must produce same hash")
    }

    @Test
    fun differentTokensProduceDifferentHashes() {
        val hash1 = computeTokenHash("access-token-1")
        val hash2 = computeTokenHash("access-token-2")

        assertNotEquals(hash1, hash2, "Different tokens should produce different hashes")
    }

    @Test
    fun cHashUseSameAlgorithm() {
        val code = "SplxlOBeZQQYbYS6WxSbIA"
        val cHash = computeTokenHash(code)

        assertNotNull(cHash)
        assertTrue(cHash.length in 22..24, "c_hash length should be 22-24 chars, got ${cHash.length}")
    }

    @Test
    fun hashDoesNotContainPaddingOrUnsafeChars() {
        val token = "test-token-for-base64url-validation"
        val atHash = computeTokenHash(token)

        assertTrue('+' !in atHash, "base64url should not contain '+'")
        assertTrue('/' !in atHash, "base64url should not contain '/'")
    }

    @Test
    fun hashUsesLeftHalfOnly() {
        val token = "verify-left-half"
        val fullHash = hash(token.encodeToByteArray(), DigestAlg.SHA256)
        assertEquals(32, fullHash.size, "SHA-256 should produce 32 bytes")

        val leftHalf = fullHash.copyOfRange(0, 16)
        val rightHalf = fullHash.copyOfRange(16, 32)

        val atHash = computeTokenHash(token)
        val leftHalfEncoded = leftHalf.encodeToBase64Url()

        assertEquals(leftHalfEncoded, atHash, "at_hash must use left half of SHA-256")

        val rightHalfEncoded = rightHalf.encodeToBase64Url()
        assertNotEquals(atHash, rightHalfEncoded, "at_hash should differ from right half encoding")
    }
}
