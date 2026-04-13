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
import com.sphereon.crypto.core.generic.Curve
import kotlinx.serialization.Serializable
import kotlin.js.JsStatic

/**
 * Key Agreement Algorithms for JWE (alg header parameter when using ECDH).
 * These algorithms perform key agreement to derive a shared secret, which is then
 * used directly (ECDH-ES) or to wrap a CEK (ECDH-ES+AxxxKW).
 *
 * References:
 * - RFC 7518 Section 4.6 (Key Agreement with Elliptic Curve Diffie-Hellman Ephemeral Static)
 * - RFC 7516 (JSON Web Encryption)
 */
@JsExportCompat
@Serializable
enum class KeyAgreementAlgorithm(
    val identifier: String,
    val description: String,
    val requiresKeyWrap: Boolean,
) {
    /**
     * ECDH-ES: Direct Key Agreement.
     * The shared secret from ECDH is used directly as the CEK.
     * No separate key wrapping step is performed.
     */
    ECDH_ES("ECDH-ES", "Elliptic Curve Diffie-Hellman Ephemeral Static (direct key agreement)", requiresKeyWrap = false),

    /**
     * ECDH-ES+A128KW: ECDH key agreement with AES-128 Key Wrap.
     * The shared secret from ECDH is used to wrap the CEK using AES-128-KW.
     */
    ECDH_ES_A128KW("ECDH-ES+A128KW", "ECDH-ES with AES-128 Key Wrap", requiresKeyWrap = true),

    /**
     * ECDH-ES+A192KW: ECDH key agreement with AES-192 Key Wrap.
     * The shared secret from ECDH is used to wrap the CEK using AES-192-KW.
     */
    ECDH_ES_A192KW("ECDH-ES+A192KW", "ECDH-ES with AES-192 Key Wrap", requiresKeyWrap = true),

    /**
     * ECDH-ES+A256KW: ECDH key agreement with AES-256 Key Wrap.
     * The shared secret from ECDH is used to wrap the CEK using AES-256-KW.
     */
    ECDH_ES_A256KW("ECDH-ES+A256KW", "ECDH-ES with AES-256 Key Wrap", requiresKeyWrap = true),
    ;

    override fun toString() = identifier

    companion object {
        /**
         * Find a KeyAgreementAlgorithm by its RFC 7518 identifier.
         *
         * @param identifier The JWE "alg" header value (e.g., "ECDH-ES", "ECDH-ES+A256KW")
         * @return The matching KeyAgreementAlgorithm, or null if not found
         */
        @JsStatic
        fun fromIdentifier(identifier: String): KeyAgreementAlgorithm? = entries.firstOrNull { it.identifier == identifier }

        /**
         * Get all ECDH-ES variants that support the specified curve.
         * Note: All ECDH-ES algorithms support P-256, P-384, and P-521 curves.
         *
         * @param curve The elliptic curve to filter by (optional)
         * @return Array of compatible KeyAgreementAlgorithm values
         */
        @JsStatic
        fun supportedByCurve(curve: Curve?): Array<KeyAgreementAlgorithm> {
            // All ECDH-ES variants support P-256, P-384, P-521
            return when (curve) {
                Curve.P_256, Curve.P_384, Curve.P_521, null -> entries.toTypedArray()
                else -> emptyArray()
            }
        }
    }
}
