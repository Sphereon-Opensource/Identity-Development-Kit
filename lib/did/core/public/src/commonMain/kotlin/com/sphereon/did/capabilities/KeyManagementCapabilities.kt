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

package com.sphereon.did.capabilities

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Key type capability with curve/algorithm details.
 *
 * Uses generic mapper types from crypto-core/generic which work with both COSE and JOSE.
 *
 * @property keyType The key type (OKP, EC, RSA)
 * @property curve The elliptic curve (if applicable)
 * @property algorithm The signature algorithm (if applicable)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidKeyTypeCapability", exact = true)
@JsExportCompat
@Serializable
data class KeyTypeCapability(
    val keyType: KeyTypeMapping,
    val curve: Curve? = null,
    val algorithm: SignatureAlgorithm? = null,
)

/**
 * Verification method (key) management capabilities.
 *
 * Describes which key management operations are supported by a DID method.
 *
 * @property addition Whether new keys can be added to existing DIDs
 * @property replacement Whether existing keys can be replaced
 * @property removal Whether keys can be removed from DIDs
 * @property supportedKeyTypes List of supported key types with their configurations
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidKeyManagementCapabilities", exact = true)
@JsExportCompat
@Serializable
data class KeyManagementCapabilities(
    val addition: Boolean = false,
    val replacement: Boolean = false,
    val removal: Boolean = false,
    val supportedKeyTypes: List<KeyTypeCapability> = emptyList(),
) {
    companion object {
        /**
         * Common Ed25519 key capability.
         */
        val ED25519: KeyTypeCapability =
            KeyTypeCapability(
                keyType = KeyTypeMapping.OKP,
                curve = Curve.Ed25519,
                algorithm = SignatureAlgorithm.ED25519,
            )

        /**
         * Common X25519 key capability (key agreement).
         */
        val X25519: KeyTypeCapability =
            KeyTypeCapability(
                keyType = KeyTypeMapping.OKP,
                curve = Curve.X25519,
                algorithm = null, // X25519 is for key agreement, not signing
            )

        /**
         * Common secp256k1 key capability.
         */
        val SECP256K1: KeyTypeCapability =
            KeyTypeCapability(
                keyType = KeyTypeMapping.EC,
                curve = Curve.Secp256k1,
                algorithm = SignatureAlgorithm.ES256K,
            )

        /**
         * Common P-256 key capability.
         */
        val P256: KeyTypeCapability =
            KeyTypeCapability(
                keyType = KeyTypeMapping.EC,
                curve = Curve.P_256,
                algorithm = SignatureAlgorithm.ECDSA_SHA256,
            )

        /**
         * Common P-384 key capability.
         */
        val P384: KeyTypeCapability =
            KeyTypeCapability(
                keyType = KeyTypeMapping.EC,
                curve = Curve.P_384,
                algorithm = SignatureAlgorithm.ECDSA_SHA384,
            )

        /**
         * Common P-521 key capability.
         */
        val P521: KeyTypeCapability =
            KeyTypeCapability(
                keyType = KeyTypeMapping.EC,
                curve = Curve.P_521,
                algorithm = SignatureAlgorithm.ECDSA_SHA512,
            )

        /**
         * Common RSA key capability.
         */
        val RSA: KeyTypeCapability =
            KeyTypeCapability(
                keyType = KeyTypeMapping.RSA,
                curve = null,
                algorithm = null,
            )

        /**
         * No key management capabilities.
         */
        val NONE: KeyManagementCapabilities = KeyManagementCapabilities()
    }

    /**
     * Checks if any key management operations are supported.
     */
    fun supportsKeyManagement(): Boolean = addition || replacement || removal

    /**
     * Checks if a specific key type and curve is supported.
     */
    fun supportsKeyType(
        keyType: KeyTypeMapping,
        curve: Curve?,
    ): Boolean =
        supportedKeyTypes.any { capability ->
            capability.keyType == keyType && (curve == null || capability.curve == curve)
        }
}
