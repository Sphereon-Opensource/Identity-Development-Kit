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
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.decodeFromHex
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.compat.JsExportCompat
import kotlin.jvm.JvmStatic

/**
 * Multibase encoding types per the multibase spec.
 * Each encoding is identified by a single-character prefix.
 */
@JsExportCompat
enum class MultibaseEncoding(
    val prefix: Char,
) {
    BASE16('f'),
    BASE58BTC('z'),
    BASE64URL('u'),
    ;

    companion object {
        @JvmStatic
        fun fromPrefix(prefix: Char): MultibaseEncoding? = entries.find { it.prefix == prefix }
    }
}

/**
 * Multibase codec — encode/decode binary data with a self-describing encoding prefix.
 */
object Multibase {
    fun encode(
        data: ByteArray,
        encoding: MultibaseEncoding = MultibaseEncoding.BASE58BTC,
    ): String {
        val encoded =
            when (encoding) {
                MultibaseEncoding.BASE16 -> data.encodeToHex()
                MultibaseEncoding.BASE58BTC -> Base58Btc.encode(data)
                MultibaseEncoding.BASE64URL -> encodeBase64Url(data)
            }
        return "${encoding.prefix}$encoded"
    }

    fun decode(encoded: String): ByteArray {
        require(encoded.isNotEmpty()) { "Multibase string must not be empty" }
        val prefix = encoded[0]
        val data = encoded.substring(1)
        val encoding =
            MultibaseEncoding.fromPrefix(prefix)
                ?: throw IllegalArgumentException("Unknown multibase prefix: '$prefix'")
        return when (encoding) {
            MultibaseEncoding.BASE16 -> data.decodeFromHex()
            MultibaseEncoding.BASE58BTC -> Base58Btc.decode(data)
            MultibaseEncoding.BASE64URL -> decodeBase64Url(data)
        }
    }

    fun getEncoding(encoded: String): MultibaseEncoding? {
        if (encoded.isEmpty()) {
            return null
        }
        return MultibaseEncoding.fromPrefix(encoded[0])
    }

    private fun encodeBase64Url(data: ByteArray): String = data.encodeToBase64Url()

    private fun decodeBase64Url(data: String): ByteArray = data.decodeFromBase64Url()
}

/**
 * Base58btc encoding/decoding (Bitcoin alphabet).
 */
private const val BYTE_MASK = 0xFF
private const val SHIFT_8 = 8
private const val BYTE_UNSIGNED_RANGE = 256

object Base58Btc {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val BASE = ALPHABET.length

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) {
            return ""
        }

        // Count leading zeros
        var leadingZeros = 0
        for (b in input) {
            if (b.toInt() == 0) {
                leadingZeros++
            } else {
                break
            }
        }

        // Convert to base58
        val encoded = StringBuilder()
        var num = input.fold(0L) { acc, byte -> acc * BYTE_UNSIGNED_RANGE + (byte.toInt() and BYTE_MASK) }

        // For large inputs, use BigInteger-style arithmetic
        // Simple approach using byte-level conversion for reasonable sizes
        val bytes = input.map { it.toInt() and BYTE_MASK }
        val digits = mutableListOf<Int>()

        for (byte in bytes) {
            var carry = byte
            for (i in digits.indices) {
                carry += digits[i] shl SHIFT_8
                digits[i] = carry % BASE
                carry /= BASE
            }
            while (carry > 0) {
                digits.add(carry % BASE)
                carry /= BASE
            }
        }

        val result = StringBuilder()
        repeat(leadingZeros) { result.append(ALPHABET[0]) }
        for (i in digits.indices.reversed()) {
            result.append(ALPHABET[digits[i]])
        }

        return result.toString()
    }

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) {
            return byteArrayOf()
        }

        // Count leading '1's (zeros in base58)
        var leadingOnes = 0
        for (c in input) {
            if (c == ALPHABET[0]) {
                leadingOnes++
            } else {
                break
            }
        }

        // Convert from base58
        val digits = mutableListOf<Int>()
        for (c in input) {
            val index = ALPHABET.indexOf(c)
            require(index >= 0) { "Invalid Base58 character: '$c'" }

            var carry = index
            for (i in digits.indices) {
                carry += digits[i] * BASE
                digits[i] = carry and BYTE_MASK
                carry = carry shr SHIFT_8
            }
            while (carry > 0) {
                digits.add(carry and BYTE_MASK)
                carry = carry shr SHIFT_8
            }
        }

        val result = ByteArray(leadingOnes) { 0 } + digits.reversed().map { it.toByte() }.toByteArray()
        return result
    }
}
