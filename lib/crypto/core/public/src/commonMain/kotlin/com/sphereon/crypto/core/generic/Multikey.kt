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

package com.sphereon.crypto.core.generic

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk

/**
 * Encodes a public key (as a JWK) into its **Multikey** form: a base58btc multibase string of the
 * multicodec-prefixed public key (e.g. `z6Mk…` for Ed25519). This is the representation used by
 * `publicKeyMultibase` (W3C Data Integrity), did:key identifiers, and did:webvh `updateKeys`.
 *
 * Generic crypto/encoding — independent of any DID method. The multicodec prefix is a LEB128 varint
 * of the key type's code; EC keys are encoded as their compressed SEC1 point.
 */
object Multikey {
    // Multicodec public-key codes (https://github.com/multiformats/multicodec).
    private const val CODE_ED25519 = 0xed
    private const val CODE_X25519 = 0xec
    private const val CODE_SECP256K1 = 0xe7
    private const val CODE_P256 = 0x1200
    private const val CODE_P384 = 0x1201

    private const val EC_POINT_EVEN_Y_PREFIX = 0x02
    private const val EC_POINT_ODD_Y_PREFIX = 0x03

    /** The base58btc Multikey (multicodec-prefixed public key) for [publicKeyJwk]. */
    fun fromPublicKey(publicKeyJwk: Jwk): IdkResult<String, IdkError> {
        val prefixed = multicodecPublicKey(publicKeyJwk).getOrElse { return Err(it) }
        return Ok(Multibase.encode(prefixed, MultibaseEncoding.BASE58BTC))
    }

    /** Multicodec-prefixed raw/compressed public-key bytes for [jwk]. */
    fun multicodecPublicKey(jwk: Jwk): IdkResult<ByteArray, IdkError> =
        when (jwk.kty) {
            JwaKeyType.OKP -> {
                val x = jwk.x ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OKP JWK is missing the 'x' coordinate"))
                val code =
                    when (jwk.crv?.value) {
                        "Ed25519" -> CODE_ED25519
                        "X25519" -> CODE_X25519
                        else -> return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported OKP curve for multikey: ${jwk.crv?.value}"))
                    }
                Ok(Varint.encode(code) + x.decodeFromBase64Url())
            }

            JwaKeyType.EC -> {
                val x = jwk.x ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "EC JWK is missing the 'x' coordinate"))
                val y = jwk.y ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "EC JWK is missing the 'y' coordinate"))
                val code =
                    when (jwk.crv?.value) {
                        "secp256k1" -> CODE_SECP256K1
                        "P-256" -> CODE_P256
                        "P-384" -> CODE_P384
                        else -> return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported EC curve for multikey: ${jwk.crv?.value}"))
                    }
                Ok(Varint.encode(code) + compressEcPoint(x.decodeFromBase64Url(), y.decodeFromBase64Url()))
            }

            else -> {
                Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported JWK key type for multikey: ${jwk.kty}"))
            }
        }

    /** Compressed SEC1 point: `0x02` (even y) / `0x03` (odd y) prefix followed by the x coordinate. */
    private fun compressEcPoint(
        x: ByteArray,
        y: ByteArray,
    ): ByteArray {
        val prefix =
            if (y.isNotEmpty() && (y.last().toInt() and 1) == 0) {
                EC_POINT_EVEN_Y_PREFIX.toByte()
            } else {
                EC_POINT_ODD_Y_PREFIX.toByte()
            }
        return byteArrayOf(prefix) + x
    }
}
