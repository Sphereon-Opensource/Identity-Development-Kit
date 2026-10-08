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

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.security.ConstantTime
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256

/**
 * PBKDF2-HMAC-SHA256 password hasher with deployment-wide pepper.
 *
 * Per-account salt = `sha256(deploymentSalt || ":" || username)`. The pepper-plus-username binding
 * keeps two accounts with the same password from sharing a derived hash even when no per-row salt
 * is stored, while still letting the hasher recompute the salt deterministically from the
 * `(deploymentSalt, username)` pair at verify time.
 *
 * Iteration count defaults to OWASP's 2024 floor of 210,000 for PBKDF2-HMAC-SHA256 in
 * [ConfigBackedUserAuthenticationProvider]; this class accepts whatever the caller passes so tests
 * can run with a smaller value.
 */
class PasswordHasher(
    private val deploymentSalt: ByteArray,
    private val iterations: Int,
    private val keyLengthBytes: Int = DEFAULT_KEY_LENGTH_BYTES,
) {
    private val provider = CryptographyProvider.Default
    private val sha256 = provider.get(SHA256)
    private val pbkdf2 = provider.get(PBKDF2)

    suspend fun hash(
        username: String,
        password: String,
    ): String = rawHash(username, password).encodeToBase64()

    /**
     * Constant-time verify against a stored base64 hash. Returns false on length mismatch or any
     * decoding failure rather than throwing, so a malformed config entry yields "wrong password"
     * not a 500.
     */
    suspend fun verify(
        username: String,
        password: String,
        expectedHashB64: String,
    ): Boolean {
        val expected = runCatching { expectedHashB64.decodeFromBase64() }.getOrNull() ?: return false
        val candidate = rawHash(username, password)
        return ConstantTime.equalsCT(expected, candidate)
    }

    private suspend fun rawHash(
        username: String,
        password: String,
    ): ByteArray {
        val perAccountSalt =
            sha256.hasher().hash(
                deploymentSalt + SALT_USERNAME_SEPARATOR + username.encodeToByteArray(),
            )
        val derivation =
            pbkdf2.secretDerivation(
                digest = SHA256,
                iterations = iterations,
                outputSize = keyLengthBytes.bytes,
                salt = perAccountSalt,
            )
        return derivation.deriveSecretToByteArray(password.encodeToByteArray())
    }

    companion object {
        const val DEFAULT_KEY_LENGTH_BYTES: Int = 32
        private val SALT_USERNAME_SEPARATOR: ByteArray = ":".encodeToByteArray()
    }
}
