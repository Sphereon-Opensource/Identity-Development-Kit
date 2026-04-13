/*
 * © 2026 Sphereon International B.V.
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

import com.sphereon.core.api.encodeToBase64Url
import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * Default implementation of SaltProvider using cryptographically secure random number generation
 *
 * This implementation uses the cryptography-kotlin library's CryptographyRandom
 * which provides platform-specific secure random generation
 */
class DefaultSaltProvider : SaltProvider {
    /**
     * Generate a cryptographically secure random salt
     *
     * @param length The desired length in bytes (default 16 bytes = 128 bits)
     * @return Base64url-encoded salt string
     */
    override fun generateSalt(length: Int): String {
        require(length > 0) { "Salt length must be positive, got $length" }
        require(length <= MAX_SALT_LENGTH) { "Salt length exceeds maximum of $MAX_SALT_LENGTH bytes, got $length" }

        val randomBytes = CryptographyRandom.nextBytes(length)
        return randomBytes.encodeToBase64Url()
    }

    companion object {
        /**
         * Maximum allowed salt length (1KB)
         * This is a reasonable upper bound to prevent memory issues
         */
        const val MAX_SALT_LENGTH = 1024

        /**
         * Recommended salt length per RFC 9901
         * 16 bytes (128 bits) provides sufficient entropy
         */
        const val RECOMMENDED_SALT_LENGTH = 16
    }
}

/**
 * Deterministic salt provider for testing purposes
 *
 * WARNING: This should NEVER be used in production!
 * It generates predictable salts based on a counter, which defeats the security purpose of salts
 *
 * @param prefix Optional prefix for generated salts (useful for distinguishing test scenarios)
 */
class DeterministicSaltProvider(
    private val prefix: String = "test",
) : SaltProvider {
    private var counter = 0

    /**
     * Generate a deterministic salt for testing
     *
     * @param length Ignored - salts are generated with consistent length
     * @return Base64url-encoded deterministic salt
     */
    override fun generateSalt(length: Int): String {
        val salt = "${prefix}_salt_${counter++}"
        return salt.encodeToByteArray().encodeToBase64Url()
    }

    /**
     * Reset the counter (useful between tests)
     */
    fun reset() {
        counter = 0
    }
}

/**
 * Salt provider that allows pre-configured salts
 *
 * Useful for testing or when specific salt values are required
 * Falls back to random generation when the configured salts are exhausted
 *
 * @param configuredSalts List of pre-configured base64url-encoded salts
 */
class ConfiguredSaltProvider(
    private val configuredSalts: List<String>,
) : SaltProvider {
    private var index = 0
    private val fallback = DefaultSaltProvider()

    /**
     * Generate a salt from the configured list or fall back to random generation
     *
     * @param length The desired length (only used for fallback generation)
     * @return Base64url-encoded salt string
     */
    override fun generateSalt(length: Int): String =
        if (index < configuredSalts.size) {
            configuredSalts[index++]
        } else {
            fallback.generateSalt(length)
        }

    /**
     * Reset to the beginning of the configured salts
     */
    fun reset() {
        index = 0
    }

    /**
     * Check if there are more configured salts available
     */
    fun hasConfiguredSalts(): Boolean = index < configuredSalts.size
}
