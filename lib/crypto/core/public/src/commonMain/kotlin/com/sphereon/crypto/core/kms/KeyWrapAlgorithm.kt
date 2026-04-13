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
 *
 */

package com.sphereon.crypto.core.kms

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.generic.CryptoAlg
import kotlinx.serialization.Serializable
import kotlin.js.JsStatic

/**
 * Key Wrap Algorithms for JWE (alg header parameter when used for key encryption).
 * These algorithms are used to encrypt the Content Encryption Key (CEK).
 *
 * References:
 * - RFC 7518 Section 4.1 (JWE "alg" Header Parameter Values)
 * - RFC 7516 (JSON Web Encryption)
 */
@JsExportCompat
@Serializable
enum class KeyWrapAlgorithm(
    val identifier: String,
    val description: String,
    val cryptoAlg: CryptoAlg?,
) {
    // RSA Key Encryption (PKCS#1 v1.5 and OAEP variants)
    RSA1_5("RSA1_5", "RSA-PKCS1-v1_5 (deprecated - use RSA-OAEP instead)", CryptoAlg.RSA),
    RSA_OAEP("RSA-OAEP", "RSA-OAEP with SHA-1 and MGF1", CryptoAlg.RSA),
    RSA_OAEP_256("RSA-OAEP-256", "RSA-OAEP with SHA-256 and MGF1", CryptoAlg.RSA),
    RSA_OAEP_384("RSA-OAEP-384", "RSA-OAEP with SHA-384 and MGF1", CryptoAlg.RSA),
    RSA_OAEP_512("RSA-OAEP-512", "RSA-OAEP with SHA-512 and MGF1", CryptoAlg.RSA),

    // AES Key Wrap (RFC 3394)
    A128KW("A128KW", "AES-128 Key Wrap", null),
    A192KW("A192KW", "AES-192 Key Wrap", null),
    A256KW("A256KW", "AES-256 Key Wrap", null),

    // AES-GCM Key Wrap
    A128GCMKW("A128GCMKW", "AES-128 GCM Key Wrap", null),
    A192GCMKW("A192GCMKW", "AES-192 GCM Key Wrap", null),
    A256GCMKW("A256GCMKW", "AES-256 GCM Key Wrap", null),

    // Direct Key Agreement - no key wrapping, CEK is the agreed-upon key
    DIR("dir", "Direct use of shared symmetric key (no key wrapping)", null),
    ;

    override fun toString() = identifier

    companion object {
        /**
         * Find a KeyWrapAlgorithm by its RFC 7518 identifier.
         *
         * @param identifier The JWE "alg" header value (e.g., "RSA-OAEP-256", "A256KW")
         * @return The matching KeyWrapAlgorithm, or null if not found
         */
        @JsStatic
        fun fromIdentifier(identifier: String): KeyWrapAlgorithm? = entries.firstOrNull { it.identifier == identifier }
    }
}
