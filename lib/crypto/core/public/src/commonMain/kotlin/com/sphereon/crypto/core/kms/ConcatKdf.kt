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

import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash

/**
 * Concat KDF implementation per RFC 7518 Section 4.6.2 and NIST SP 800-56A.
 *
 * This Key Derivation Function is used with ECDH-ES key agreement algorithms
 * to derive the Content Encryption Key (CEK) or a key-wrapping key.
 *
 * The Concat KDF concatenates the following data and hashes it:
 * - counter (4 bytes, big-endian)
 * - Z (shared secret from ECDH)
 * - OtherInfo:
 *   - AlgorithmID: len(algorithm) || algorithm
 *   - PartyUInfo: len(apu) || apu
 *   - PartyVInfo: len(apv) || apv
 *   - SuppPubInfo: keydatalen (4 bytes, big-endian)
 *   - SuppPrivInfo: (empty)
 *
 * References:
 * - RFC 7518 Section 4.6.2: Key Derivation for ECDH Key Agreement
 * - NIST SP 800-56A Rev. 3: Recommendation for Pair-Wise Key Establishment
 */
private const val BITS_PER_BYTE = 8
private const val INT32_SIZE = 4
private const val SHA256_DIGEST_SIZE = 32
private const val BYTE_MASK = 0xFF
private const val KEY_LENGTH_128 = 128
private const val KEY_LENGTH_192 = 192
private const val KEY_LENGTH_256 = 256
private const val KEY_LENGTH_384 = 384
private const val KEY_LENGTH_512 = 512
private const val SHIFT_8 = 8
private const val SHIFT_16 = 16
private const val SHIFT_24 = 24

object ConcatKdf {
    /**
     * Derives a key using Concat KDF.
     *
     * @param sharedSecret The shared secret Z from ECDH key agreement
     * @param keyDataLen The desired key length in bits
     * @param algorithmId The algorithm identifier (e.g., "A256GCM" or "A256KW")
     * @param apu Agreement PartyUInfo (optional, empty array if not provided)
     * @param apv Agreement PartyVInfo (optional, empty array if not provided)
     * @return The derived key of the requested length in bytes
     */
    fun deriveKey(
        sharedSecret: ByteArray,
        keyDataLen: Int,
        algorithmId: String,
        apu: ByteArray = ByteArray(0),
        apv: ByteArray = ByteArray(0),
    ): ByteArray {
        require(keyDataLen > 0) { "keyDataLen must be positive" }
        require(keyDataLen % BITS_PER_BYTE == 0) { "keyDataLen must be a multiple of 8 bits" }

        val keyDataLenBytes = keyDataLen / BITS_PER_BYTE
        val hashLen = SHA256_DIGEST_SIZE

        // Build OtherInfo per RFC 7518 Section 4.6.2
        val otherInfo = buildOtherInfo(algorithmId, apu, apv, keyDataLen)

        // Number of hash iterations needed
        val reps = (keyDataLenBytes + hashLen - 1) / hashLen

        // Accumulate derived keying material
        val derivedKeyingMaterial = ByteArray(reps * hashLen)
        var offset = 0

        for (counter in 1..reps) {
            // Build hash input: counter || Z || OtherInfo
            val hashInput = buildHashInput(counter, sharedSecret, otherInfo)

            // Hash with SHA-256
            val hashOutput = hash(hashInput, DigestAlg.SHA256)

            // Copy to derived keying material
            hashOutput.copyInto(derivedKeyingMaterial, offset)
            offset += hashLen
        }

        // Return first keyDataLenBytes
        return derivedKeyingMaterial.copyOfRange(0, keyDataLenBytes)
    }

    /**
     * Builds the OtherInfo structure per RFC 7518 Section 4.6.2.
     *
     * OtherInfo = AlgorithmID || PartyUInfo || PartyVInfo || SuppPubInfo || SuppPrivInfo
     * where:
     * - AlgorithmID = length_of(algorithm) || algorithm (using 4-byte big-endian length)
     * - PartyUInfo = length_of(apu) || apu
     * - PartyVInfo = length_of(apv) || apv
     * - SuppPubInfo = keydatalen (4 bytes big-endian)
     * - SuppPrivInfo = empty
     */
    private fun buildOtherInfo(
        algorithmId: String,
        apu: ByteArray,
        apv: ByteArray,
        keyDataLen: Int,
    ): ByteArray {
        val algorithmBytes = algorithmId.encodeToByteArray()

        // Calculate total size
        val size =
            INT32_SIZE + algorithmBytes.size + // AlgorithmID
                INT32_SIZE + apu.size + // PartyUInfo
                INT32_SIZE + apv.size + // PartyVInfo
                INT32_SIZE // SuppPubInfo (keyDataLen)

        val result = ByteArray(size)
        var offset = 0

        // AlgorithmID: len || algorithm
        writeInt32BE(result, offset, algorithmBytes.size)
        offset += INT32_SIZE
        algorithmBytes.copyInto(result, offset)
        offset += algorithmBytes.size

        // PartyUInfo: len || apu
        writeInt32BE(result, offset, apu.size)
        offset += INT32_SIZE
        apu.copyInto(result, offset)
        offset += apu.size

        // PartyVInfo: len || apv
        writeInt32BE(result, offset, apv.size)
        offset += INT32_SIZE
        apv.copyInto(result, offset)
        offset += apv.size

        // SuppPubInfo: keydatalen in bits
        writeInt32BE(result, offset, keyDataLen)

        return result
    }

    /**
     * Builds the hash input: counter || Z || OtherInfo
     */
    private fun buildHashInput(
        counter: Int,
        z: ByteArray,
        otherInfo: ByteArray,
    ): ByteArray {
        val result = ByteArray(INT32_SIZE + z.size + otherInfo.size)

        // Write counter as 4-byte big-endian
        writeInt32BE(result, 0, counter)

        // Copy Z
        z.copyInto(result, INT32_SIZE)

        // Copy OtherInfo
        otherInfo.copyInto(result, INT32_SIZE + z.size)

        return result
    }

    /**
     * Writes a 32-bit integer in big-endian format.
     */
    private fun writeInt32BE(
        array: ByteArray,
        offset: Int,
        value: Int,
    ) {
        array[offset] = ((value shr SHIFT_24) and BYTE_MASK).toByte()
        array[offset + 1] = ((value shr SHIFT_16) and BYTE_MASK).toByte()
        array[offset + 2] = ((value shr SHIFT_8) and BYTE_MASK).toByte()
        array[offset + 3] = (value and BYTE_MASK).toByte()
    }

    /**
     * Determines the key length in bits for ECDH-ES key agreement.
     *
     * For ECDH-ES (direct key agreement), the derived key length matches
     * the content encryption algorithm requirements.
     *
     * For ECDH-ES+AxxxKW (key wrapping), the derived key length matches
     * the key wrapping algorithm requirements.
     *
     * @param algorithm The JWE "alg" value (ECDH-ES or ECDH-ES+AxxxKW)
     * @param encAlgorithm The JWE "enc" value (content encryption algorithm)
     * @return The key length in bits
     */
    fun getKeyLengthBits(
        algorithm: KeyAgreementAlgorithm,
        encAlgorithm: String,
    ): Int =
        when (algorithm) {
            // Direct key agreement - derive CEK directly
            KeyAgreementAlgorithm.ECDH_ES -> {
                when (encAlgorithm) {
                    "A128GCM" -> KEY_LENGTH_128

                    "A192GCM" -> KEY_LENGTH_192

                    "A256GCM" -> KEY_LENGTH_256

                    "A128CBC-HS256" -> KEY_LENGTH_256

                    // 128 enc + 128 mac
                    "A192CBC-HS384" -> KEY_LENGTH_384

                    // 192 enc + 192 mac
                    "A256CBC-HS512" -> KEY_LENGTH_512

                    // 256 enc + 256 mac
                    else -> throw IllegalArgumentException("Unsupported content encryption algorithm: $encAlgorithm")
                }
            }

            // Key wrapping - derive key-wrapping key
            KeyAgreementAlgorithm.ECDH_ES_A128KW -> {
                KEY_LENGTH_128
            }

            KeyAgreementAlgorithm.ECDH_ES_A192KW -> {
                KEY_LENGTH_192
            }

            KeyAgreementAlgorithm.ECDH_ES_A256KW -> {
                KEY_LENGTH_256
            }
        }

    /**
     * Gets the algorithm identifier to use in Concat KDF.
     *
     * For ECDH-ES (direct), the "enc" algorithm is used.
     * For ECDH-ES+AxxxKW, the AES key-wrap algorithm name is used.
     *
     * @param algorithm The JWE "alg" value
     * @param encAlgorithm The JWE "enc" value
     * @return The algorithm identifier for Concat KDF
     */
    fun getAlgorithmId(
        algorithm: KeyAgreementAlgorithm,
        encAlgorithm: String,
    ): String =
        when (algorithm) {
            KeyAgreementAlgorithm.ECDH_ES -> encAlgorithm
            KeyAgreementAlgorithm.ECDH_ES_A128KW -> "A128KW"
            KeyAgreementAlgorithm.ECDH_ES_A192KW -> "A192KW"
            KeyAgreementAlgorithm.ECDH_ES_A256KW -> "A256KW"
        }
}
