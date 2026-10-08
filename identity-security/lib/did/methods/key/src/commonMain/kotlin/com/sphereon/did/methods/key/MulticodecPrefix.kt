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

package com.sphereon.did.methods.key

import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyTypeMapping

private const val CODE_ED25519 = 0xed
private const val CODE_X25519 = 0xec
private const val CODE_SECP256K1 = 0xe7
private const val CODE_P256 = 0x1200
private const val CODE_P384 = 0x1201
private const val OKP_KEY_LENGTH = 32
private const val EC_COMPRESSED_KEY_LENGTH = 33
private const val P384_COMPRESSED_KEY_LENGTH = 49
private const val BYTE_MASK = 0xFF
private const val VARINT_DATA_MASK = 0x7F
private const val VARINT_CONTINUATION_BIT = 0x80
private const val VARINT_SHIFT = 7
private const val HEX_RADIX = 16

/**
 * Multicodec prefixes for cryptographic key types.
 *
 * These are varint-encoded prefixes used in multicodec to identify the type of data.
 * For keys, these identify the key type and curve.
 *
 * @see <a href="https://github.com/multiformats/multicodec">Multicodec</a>
 */
enum class MulticodecPrefix(
    val code: Int,
    val keyType: KeyTypeMapping,
    val curve: Curve?,
    val keyLength: Int, // Expected public key length in bytes
) {
    /**
     * Ed25519 public key (0xed)
     */
    ED25519_PUB(CODE_ED25519, KeyTypeMapping.OKP, Curve.Ed25519, OKP_KEY_LENGTH),

    /**
     * X25519 public key for key agreement (0xec)
     */
    X25519_PUB(CODE_X25519, KeyTypeMapping.OKP, Curve.X25519, OKP_KEY_LENGTH),

    /**
     * Secp256k1 public key (0xe7)
     */
    SECP256K1_PUB(CODE_SECP256K1, KeyTypeMapping.EC, Curve.Secp256k1, EC_COMPRESSED_KEY_LENGTH), // Compressed

    /**
     * P-256 public key (0x8024) - 2-byte varint
     */
    P256_PUB(CODE_P256, KeyTypeMapping.EC, Curve.P_256, EC_COMPRESSED_KEY_LENGTH), // Compressed

    /**
     * P-384 public key (0x8124) - 2-byte varint
     */
    P384_PUB(CODE_P384, KeyTypeMapping.EC, Curve.P_384, P384_COMPRESSED_KEY_LENGTH), // Compressed
    ;

    companion object {
        /**
         * Finds a multicodec prefix by its code.
         */
        fun fromCode(code: Int): MulticodecPrefix? = entries.find { it.code == code }

        /**
         * Finds a multicodec prefix by key type and curve.
         */
        fun fromKeyTypeAndCurve(
            keyType: KeyTypeMapping,
            curve: Curve,
        ): MulticodecPrefix? = entries.find { it.keyType == keyType && it.curve == curve }

        /**
         * Decodes a varint from the beginning of a byte array.
         *
         * @param bytes The byte array
         * @return Pair of (decoded value, bytes consumed)
         */
        fun decodeVarint(bytes: ByteArray): Pair<Int, Int> {
            var value = 0
            var shift = 0
            var bytesRead = 0

            for (byte in bytes) {
                val b = byte.toInt() and BYTE_MASK
                value = value or ((b and VARINT_DATA_MASK) shl shift)
                bytesRead++

                if ((b and VARINT_CONTINUATION_BIT) == 0) {
                    break
                }
                shift += VARINT_SHIFT
            }

            return Pair(value, bytesRead)
        }

        /**
         * Encodes an integer as a varint.
         *
         * @param value The value to encode
         * @return The varint bytes
         */
        fun encodeVarint(value: Int): ByteArray {
            val result = mutableListOf<Byte>()
            var remaining = value

            while (remaining >= VARINT_CONTINUATION_BIT) {
                result.add(((remaining and VARINT_DATA_MASK) or VARINT_CONTINUATION_BIT).toByte())
                remaining = remaining ushr VARINT_SHIFT
            }
            result.add(remaining.toByte())

            return result.toByteArray()
        }
    }

    /**
     * Gets the varint-encoded prefix bytes.
     */
    fun toVarintBytes(): ByteArray = encodeVarint(code)
}
