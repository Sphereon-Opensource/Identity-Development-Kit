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

package com.sphereon.oauth2.server.authorization.storage

import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.security.ConstantTime
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash as digestBytes
import dev.zacsweers.metro.Inject

/**
 * Hashing for dynamically registered client secrets (RFC 7591 section 5.2.2 lifecycle).
 *
 * Format: `v1.<salt b64url>.<hash b64url>` where hash = SHA-256(salt || secret). A fast digest
 * is deliberate: dynamically registered secrets are server-generated high-entropy tokens, not
 * human passwords, so there is no offline brute-force value worth a PBKDF2 cost;
 * [PasswordHasher][com.sphereon.oauth2.server.authorization.impl.provider.PasswordHasher]
 * remains the tool for low-entropy user passwords. Salts draw from the injected [SecureRandom]
 * service so policy/telemetry hooks configured for that facade apply uniformly, exactly as they
 * do for device codes and authorization codes. Verification compares constant-time so a
 * malformed stored value yields "wrong secret", never a crash or an oracle.
 */
@Inject
class ClientSecretHasher(
    private val secureRandom: SecureRandom,
) {
    suspend fun hash(secret: String): String {
        val salt = secureRandom.randomBytes(SALT_BYTES)
        val digest = digestBytes(salt + secret.encodeToByteArray(), DigestAlg.SHA256)
        return "$VERSION.${salt.encodeToBase64Url()}.${digest.encodeToBase64Url()}"
    }

    fun verify(
        secret: String,
        storedHash: String?,
    ): Boolean {
        if (storedHash == null) return false
        val parts = storedHash.split('.')
        if (parts.size != 3 || parts[0] != VERSION) return false
        val salt = runCatching { parts[1].decodeFromBase64Url() }.getOrNull() ?: return false
        val expected = runCatching { parts[2].decodeFromBase64Url() }.getOrNull() ?: return false
        val candidate = digestBytes(salt + secret.encodeToByteArray(), DigestAlg.SHA256)
        return ConstantTime.equalsCT(expected, candidate)
    }

    private companion object {
        const val VERSION = "v1"
        const val SALT_BYTES = 16
    }
}
