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

package com.sphereon.statuslist.impl.codec

import com.sphereon.compression.CompressionAlgorithm
import com.sphereon.compression.compress
import com.sphereon.compression.decompress
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.statuslist.StatusListSpec

/**
 * Encodes/decodes the packed status byte array to the on-the-wire string form for each spec:
 * - Token Status List `lst`: zlib-DEFLATE then unpadded base64url.
 * - W3C Bitstring `encodedList`: GZIP then unpadded base64url, multibase-prefixed `u`.
 */
object StatusListCodec {
    suspend fun encode(
        bitset: StatusBitset,
        spec: StatusListSpec,
    ): String =
        when (spec) {
            StatusListSpec.TOKEN_STATUS_LIST -> {
                compress(bitset.toByteArray(), CompressionAlgorithm.DEFLATE_ZLIB).encodeToBase64Url()
            }

            StatusListSpec.BITSTRING_STATUS_LIST -> {
                MULTIBASE_BASE64URL + compress(bitset.toByteArray(), CompressionAlgorithm.GZIP).encodeToBase64Url()
            }
        }

    /**
     * Decode an `lst`/`encodedList` string into a [StatusBitset]. The number of entries is inferred
     * from the decompressed byte length and [bitsPerStatus] (sufficient for verifier-side reads).
     */
    suspend fun decode(
        encoded: String,
        bitsPerStatus: Int,
        spec: StatusListSpec,
    ): StatusBitset {
        val (algorithm, payload) =
            when (spec) {
                StatusListSpec.TOKEN_STATUS_LIST -> {
                    CompressionAlgorithm.DEFLATE_ZLIB to encoded
                }

                StatusListSpec.BITSTRING_STATUS_LIST -> {
                    CompressionAlgorithm.GZIP to encoded.removePrefix(MULTIBASE_BASE64URL)
                }
            }
        val bytes = decompress(payload.decodeFromBase64Url(), algorithm)
        val length = bytes.size * 8 / bitsPerStatus
        return StatusBitset.fromBytes(bytes, length, bitsPerStatus, bitOrderFor(spec))
    }

    fun bitOrderFor(spec: StatusListSpec): BitOrder =
        when (spec) {
            StatusListSpec.TOKEN_STATUS_LIST -> BitOrder.LSB_FIRST
            StatusListSpec.BITSTRING_STATUS_LIST -> BitOrder.MSB_FIRST
        }

    private const val MULTIBASE_BASE64URL = "u"
}
