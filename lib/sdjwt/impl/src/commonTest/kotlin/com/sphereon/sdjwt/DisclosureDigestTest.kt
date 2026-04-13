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

import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.sdjwt.DefaultSaltProvider
import com.sphereon.sdjwt.DeterministicSaltProvider
import com.sphereon.sdjwt.Disclosure
import com.sphereon.sdjwt.DisclosureDigest
import com.sphereon.sdjwt.DisclosureDigestUtil
import com.sphereon.sdjwt.SdJwtPayload
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for disclosure digest calculation and verification
 * Based on RFC 9901 Section 5.1.2 - Hashing Disclosures
 */
class DisclosureDigestTest {
    @Test
    fun testCalculateDigest() {
        // Create a disclosure with known salt
        val saltProvider = com.sphereon.sdjwt.DeterministicSaltProvider("test-salt-123")
        val disclosure =
            com.sphereon.sdjwt.Disclosure.Companion
                .objectProperty(saltProvider, "email", JsonPrimitive("user@example.com"))

        // Calculate digest using SHA-256 (RFC default)
        val digest =
            com.sphereon.sdjwt.DisclosureDigest.Companion
                .calculate(DigestAlg.SHA256, disclosure)

        // Verify digest is not empty and is base64url encoded
        assertTrue(digest.value.isNotEmpty(), "Digest should not be empty")
        assertTrue(isBase64Url(digest.value), "Digest should be base64url encoded")

        // Calculate again - should be deterministic
        val digest2 =
            com.sphereon.sdjwt.DisclosureDigest.Companion
                .calculate(DigestAlg.SHA256, disclosure)
        kotlin.test.assertEquals(digest.value, digest2.value, "Same disclosure should produce same digest")
    }

    @Test
    fun testVerifyDigest() {
        // Create a disclosure
        val saltProvider = com.sphereon.sdjwt.DeterministicSaltProvider("verify-salt")
        val disclosure =
            com.sphereon.sdjwt.Disclosure.Companion
                .objectProperty(saltProvider, "name", JsonPrimitive("Alice"))

        // Calculate the expected digest
        val expectedDigest =
            com.sphereon.sdjwt.DisclosureDigest.Companion
                .calculateDigest(disclosure.encoded, DigestAlg.SHA256)

        // Verify the disclosure matches the expected digest
        assertTrue(
            com.sphereon.sdjwt.DisclosureDigestUtil
                .verifyDisclosure(disclosure, expectedDigest, DigestAlg.SHA256),
            "Disclosure should verify against its digest",
        )

        // Verify with wrong digest
        val wrongDigest = "wrong-digest-value"
        assertFalse(
            com.sphereon.sdjwt.DisclosureDigestUtil
                .verifyDisclosure(disclosure, wrongDigest, DigestAlg.SHA256),
            "Disclosure should not verify against wrong digest",
        )
    }

    @Test
    fun testVerifyAllDisclosures() {
        // Create multiple disclosures
        val saltProvider = com.sphereon.sdjwt.DeterministicSaltProvider("salt")
        val disclosure1Base =
            com.sphereon.sdjwt.Disclosure.Companion
                .objectProperty(saltProvider, "email", JsonPrimitive("user@example.com"))
        val disclosure2Base =
            com.sphereon.sdjwt.Disclosure.Companion
                .objectProperty(saltProvider, "phone", JsonPrimitive("+1234567890"))
        val disclosure3Base =
            com.sphereon.sdjwt.Disclosure.Companion
                .objectProperty(saltProvider, "age", JsonPrimitive(25))

        // Calculate digests and create disclosures with digest field set
        val digest1 =
            com.sphereon.sdjwt.DisclosureDigest.Companion
                .calculate(DigestAlg.SHA256, disclosure1Base)
                .value
        val digest2 =
            com.sphereon.sdjwt.DisclosureDigest.Companion
                .calculate(DigestAlg.SHA256, disclosure2Base)
                .value
        val digest3 =
            com.sphereon.sdjwt.DisclosureDigest.Companion
                .calculate(DigestAlg.SHA256, disclosure3Base)
                .value

        val disclosure1 = disclosure1Base.copy(digest = digest1)
        val disclosure2 = disclosure2Base.copy(digest = digest2)
        val disclosure3 = disclosure3Base.copy(digest = digest3)

        val disclosures = listOf(disclosure1, disclosure2, disclosure3)
        val digests = listOf(digest1, digest2, digest3)

        // Create a payload with _sd array containing the digests
        val payload =
            buildJsonObject {
                put("iss", JsonPrimitive("https://issuer.example.com"))
                put("sub", JsonPrimitive("user123"))
                putJsonArray("_sd") {
                    digests.forEach { add(JsonPrimitive(it)) }
                }
                put("_sd_alg", JsonPrimitive("sha-256"))
            }

        // Create SdJwtPayload with digest mapping
        val digestedDisclosures =
            disclosures.associate {
                com.sphereon.sdjwt.DisclosureDigest.Companion
                    .calculate(DigestAlg.SHA256, it)
                    .value to it
            }
        val sdJwtPayload =
            com.sphereon.sdjwt.SdJwtPayload(
                undisclosedPayload = payload,
                fullPayload = payload,
                digestedDisclosures = digestedDisclosures,
            )

        // Verify all disclosures
        assertTrue(
            com.sphereon.sdjwt.DisclosureDigestUtil
                .verifyAllDisclosures(sdJwtPayload, disclosures, DigestAlg.SHA256),
            "All disclosures should verify successfully",
        )
    }

    @Test
    fun testExtractDigestsFromPayload() {
        // Create a payload with multiple _sd arrays at different levels
        val payload =
            buildJsonObject {
                put("iss", JsonPrimitive("https://issuer.example.com"))
                putJsonArray("_sd") {
                    add(JsonPrimitive("digest1"))
                    add(JsonPrimitive("digest2"))
                }
                put(
                    "address",
                    buildJsonObject {
                        put("country", JsonPrimitive("US"))
                        putJsonArray("_sd") {
                            add(JsonPrimitive("digest3"))
                            add(JsonPrimitive("digest4"))
                        }
                    },
                )
                put("_sd_alg", JsonPrimitive("sha-256"))
            }

        // Extract all digests
        val digests =
            com.sphereon.sdjwt.DisclosureDigestUtil
                .extractDigests(payload)

        // Should find all 4 digests
        kotlin.test.assertEquals(4, digests.size, "Should find all digests from nested _sd arrays")
        assertTrue(digests.contains("digest1"))
        assertTrue(digests.contains("digest2"))
        assertTrue(digests.contains("digest3"))
        assertTrue(digests.contains("digest4"))
    }

    @Test
    fun testDigestAlgorithmSHA256() {
        val saltProvider = com.sphereon.sdjwt.DeterministicSaltProvider("sha256-salt")
        val disclosure =
            com.sphereon.sdjwt.Disclosure.Companion
                .objectProperty(saltProvider, "claim", JsonPrimitive("value"))

        // Calculate digest with SHA-256
        val digestSha256 =
            com.sphereon.sdjwt.DisclosureDigest.Companion
                .calculate(DigestAlg.SHA256, disclosure)

        assertTrue(digestSha256.value.isNotEmpty())
        assertTrue(isBase64Url(digestSha256.value))
    }

    @Test
    fun testDigestAlgorithmSHA384() {
        val saltProvider = com.sphereon.sdjwt.DeterministicSaltProvider("sha384-salt")
        val disclosure =
            com.sphereon.sdjwt.Disclosure.Companion
                .objectProperty(saltProvider, "claim", JsonPrimitive("value"))

        // Calculate digest with SHA-384
        val digestSha384 =
            com.sphereon.sdjwt.DisclosureDigest.Companion
                .calculate(DigestAlg.SHA384, disclosure)

        assertTrue(digestSha384.value.isNotEmpty())
        assertTrue(isBase64Url(digestSha384.value))

        // SHA-384 produces longer digests than SHA-256
        val digestSha256 =
            com.sphereon.sdjwt.DisclosureDigest.Companion
                .calculate(DigestAlg.SHA256, disclosure)
        assertTrue(digestSha384.value.length > digestSha256.value.length)
    }

    @Test
    fun testDigestAlgorithmSHA512() {
        val saltProvider = com.sphereon.sdjwt.DeterministicSaltProvider("sha512-salt")
        val disclosure =
            com.sphereon.sdjwt.Disclosure.Companion
                .objectProperty(saltProvider, "claim", JsonPrimitive("value"))

        // Calculate digest with SHA-512
        val digestSha512 =
            com.sphereon.sdjwt.DisclosureDigest.Companion
                .calculate(DigestAlg.SHA512, disclosure)

        assertTrue(digestSha512.value.isNotEmpty())
        assertTrue(isBase64Url(digestSha512.value))

        // SHA-512 produces the longest digests
        val digestSha256 =
            com.sphereon.sdjwt.DisclosureDigest.Companion
                .calculate(DigestAlg.SHA256, disclosure)
        assertTrue(digestSha512.value.length > digestSha256.value.length)
    }

    @Test
    fun testDifferentClaimsProduceDifferentDigests() {
        val saltProvider = com.sphereon.sdjwt.DeterministicSaltProvider("salt")

        val disclosure1 =
            com.sphereon.sdjwt.Disclosure.Companion
                .objectProperty(saltProvider, "email", JsonPrimitive("alice@example.com"))
        val disclosure2 =
            com.sphereon.sdjwt.Disclosure.Companion
                .objectProperty(saltProvider, "email", JsonPrimitive("bob@example.com"))

        val digest1 =
            com.sphereon.sdjwt.DisclosureDigest.Companion
                .calculate(DigestAlg.SHA256, disclosure1)
        val digest2 =
            com.sphereon.sdjwt.DisclosureDigest.Companion
                .calculate(DigestAlg.SHA256, disclosure2)

        kotlin.test.assertNotEquals(
            digest1.value,
            digest2.value,
            "Different claim values should produce different digests",
        )
    }

    @Test
    fun testGenerateDecoyDigest() {
        val saltProvider = com.sphereon.sdjwt.DefaultSaltProvider()

        // Generate decoy digest
        val decoyDigest =
            com.sphereon.sdjwt.DisclosureDigestUtil
                .generateDecoyDigest(saltProvider, DigestAlg.SHA256)

        assertTrue(decoyDigest.isNotEmpty(), "Decoy digest should not be empty")
        assertTrue(isBase64Url(decoyDigest), "Decoy digest should be base64url encoded")

        // Generate another - should be different (random salt)
        val decoyDigest2 =
            com.sphereon.sdjwt.DisclosureDigestUtil
                .generateDecoyDigest(saltProvider, DigestAlg.SHA256)
        kotlin.test.assertNotEquals(decoyDigest, decoyDigest2, "Different decoy digests should be generated")
    }

    @Test
    fun testGenerateMultipleDecoyDigests() {
        val saltProvider = com.sphereon.sdjwt.DefaultSaltProvider()
        val count = 5

        val decoyDigests =
            com.sphereon.sdjwt.DisclosureDigestUtil
                .generateDecoyDigests(count, saltProvider, DigestAlg.SHA256)

        kotlin.test.assertEquals(count, decoyDigests.size, "Should generate requested number of decoy digests")

        // All should be unique
        kotlin.test.assertEquals(
            count,
            decoyDigests.toSet().size,
            "All decoy digests should be unique",
        )

        // All should be valid base64url
        decoyDigests.forEach { digest ->
            assertTrue(isBase64Url(digest), "Each decoy digest should be base64url encoded")
        }
    }

    /**
     * Check if a string is valid base64url encoding
     */
    private fun isBase64Url(str: String): Boolean {
        // Base64url uses A-Z, a-z, 0-9, -, _ (no padding)
        return str.matches(Regex("^[A-Za-z0-9_-]+$"))
    }
}
