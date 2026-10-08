/*
 * Copyright (c) 2026 Sphereon International B.V.
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

package com.sphereon.crypto.core.generic
import com.sphereon.core.compat.JsExportCompat
import kotlin.jvm.JvmStatic

/**
 * Multihash algorithm registry — maps DigestAlg to multicodec hash function codes.
 *
 * Note on HMAC: The multicodec table has no official HMAC codes. HMAC is a processing mode,
 * not a hash function. We store the *result* of HMAC-SHA256 using the sha2-256 code (0x12) —
 * the multihash describes the digest algorithm, not the keying mode.
 */
@JsExportCompat
enum class MultihashAlgorithm(
    val code: Int,
    val digestAlg: DigestAlg,
    val digestLength: Int,
) {
    SHA2_256(code = 0x12, digestAlg = DigestAlg.SHA256, digestLength = 32),
    SHA2_384(code = 0x20, digestAlg = DigestAlg.SHA384, digestLength = 48),
    SHA2_512(code = 0x13, digestAlg = DigestAlg.SHA512, digestLength = 64),
    SHA3_256(code = 0x16, digestAlg = DigestAlg.SHA3_256, digestLength = 32),
    SHA3_384(code = 0x15, digestAlg = DigestAlg.SHA3_384, digestLength = 48),
    SHA3_512(code = 0x14, digestAlg = DigestAlg.SHA3_512, digestLength = 64),
    ;

    companion object {
        @JvmStatic
        fun fromCode(code: Int): MultihashAlgorithm? = entries.find { it.code == code }

        @JvmStatic
        fun fromDigestAlg(alg: DigestAlg): MultihashAlgorithm? = entries.find { it.digestAlg == alg }
    }
}

/**
 * Multihash codec — encode/decode self-describing hash values.
 * Format: <varint hash-func-code> + <varint digest-length> + <raw digest bytes>
 */
object MultihashCodec {
    /**
     * Encode raw digest bytes into multihash binary format.
     */
    fun encode(
        digest: ByteArray,
        algorithm: MultihashAlgorithm,
    ): ByteArray {
        require(digest.size == algorithm.digestLength) {
            "Digest length ${digest.size} does not match expected ${algorithm.digestLength} for ${algorithm.name}"
        }
        val codeBytes = Varint.encode(algorithm.code)
        val lengthBytes = Varint.encode(digest.size)
        return codeBytes + lengthBytes + digest
    }

    /**
     * Decode multihash binary format into (algorithm, raw digest bytes).
     */
    fun decode(multihash: ByteArray): Pair<MultihashAlgorithm, ByteArray> {
        require(multihash.size >= 2) { "Multihash too short" }

        val (code, codeLen) = Varint.decode(multihash, 0)
        val algorithm =
            MultihashAlgorithm.fromCode(code)
                ?: throw IllegalArgumentException("Unknown multihash function code: 0x${code.toString(radix = 16)}")

        val (length, lengthLen) = Varint.decode(multihash, codeLen)
        val digestOffset = codeLen + lengthLen
        require(multihash.size >= digestOffset + length) {
            "Multihash truncated: expected $length digest bytes at offset $digestOffset, got ${multihash.size - digestOffset}"
        }

        val digest = multihash.copyOfRange(digestOffset, digestOffset + length)
        return algorithm to digest
    }

    /**
     * Check if bytes are valid multihash format.
     */
    fun isMultihash(bytes: ByteArray): Boolean =
        try {
            decode(bytes)
            true
        } catch (_: Exception) {
            false
        }

    /**
     * Encode raw digest as multibase-encoded multihash text.
     */
    fun encodeToMultibase(
        digest: ByteArray,
        algorithm: MultihashAlgorithm,
        base: MultibaseEncoding = MultibaseEncoding.BASE58BTC,
    ): String {
        val multihash = encode(digest, algorithm)
        return Multibase.encode(multihash, base)
    }

    /**
     * Decode multibase-encoded multihash text.
     */
    fun decodeFromMultibase(encoded: String): Pair<MultihashAlgorithm, ByteArray> {
        val multihash = Multibase.decode(encoded)
        return decode(multihash)
    }

    /**
     * Convert a plain hex hash to multihash binary format.
     */
    fun fromPlainHex(
        hashHex: String,
        algorithm: MultihashAlgorithm,
    ): ByteArray {
        val digest = hashHex.hexToBytes()
        return encode(digest, algorithm)
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(length / 2) { i ->
            substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
