/*
 * © 2025 Sphereon International B.V.
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

import com.sphereon.core.api.decodeFromBase58Btc
import com.sphereon.core.api.encodeToBase58Btc

/**
 * Multibase encoding/decoding support.
 *
 * Multibase is a protocol for self-describing base encodings.
 * The first character indicates the encoding used.
 *
 * @see <a href="https://github.com/multiformats/multibase">Multibase</a>
 */
object MultibaseCodec {

    /**
     * Multibase prefix for base58btc encoding.
     */
    const val BASE58BTC_PREFIX: Char = 'z'

    /**
     * Encodes bytes to a multibase base58btc string.
     *
     * @param bytes The bytes to encode
     * @return The multibase-encoded string (starting with 'z')
     */
    fun encodeBase58Btc(bytes: ByteArray): String {
        return BASE58BTC_PREFIX + bytes.encodeToBase58Btc()
    }

    /**
     * Decodes a multibase string to bytes.
     *
     * @param multibase The multibase-encoded string
     * @return The decoded bytes
     * @throws IllegalArgumentException if the multibase prefix is not supported
     */
    fun decode(multibase: String): ByteArray {
        require(multibase.isNotEmpty()) { "Multibase string cannot be empty" }

        return when (multibase[0]) {
            BASE58BTC_PREFIX -> multibase.substring(1).decodeFromBase58Btc()
            else -> throw IllegalArgumentException("Unsupported multibase prefix: ${multibase[0]}")
        }
    }

    /**
     * Checks if a string is a valid multibase-encoded string.
     */
    fun isMultibase(value: String): Boolean {
        if (value.isEmpty()) return false
        return value[0] == BASE58BTC_PREFIX
    }
}
