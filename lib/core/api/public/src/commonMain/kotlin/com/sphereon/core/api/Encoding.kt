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

package com.sphereon.core.api

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import com.sphereon.core.compat.JsExportCompat

@OptIn(ExperimentalObjCName::class)
@ObjCName("Encoding", exact = true)
@JsExportCompat
enum class Encoding {
    BASE64,
    BASE64URL,
    BASE58BTC,
    HEX,
    UTF8
}

@OptIn(ExperimentalStdlibApi::class)
@JsExportCompat
fun String.decodeFromHex(): ByteArray {
    return this.hexToByteArray(HexFormat.Default)
}

@OptIn(ExperimentalStdlibApi::class)
@JsExportCompat
fun ByteArray.encodeToHex(): String {
    return this.toHexString(HexFormat.Default)
}

/**
 * Base58btc alphabet (Bitcoin alphabet).
 * Excludes 0, O, I, l to avoid visual ambiguity.
 */
private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

/**
 * Encodes bytes to base58btc (Bitcoin alphabet).
 *
 * Uses byte array arithmetic to avoid BigInteger dependency (not available in Kotlin Common).
 *
 * @return The base58btc-encoded string
 */
@JsExportCompat
fun ByteArray.encodeToBase58Btc(): String {
    if (this.isEmpty()) return ""

    // Count leading zeros
    var leadingZeros = 0
    for (byte in this) {
        if (byte.toInt() == 0) leadingZeros++ else break
    }

    // Work with a copy that we'll modify
    val input = this.copyOf()
    val result = StringBuilder()

    // Convert using repeated division by 58
    var inputStart = 0
    while (inputStart < input.size) {
        var remainder = 0
        var allZero = true
        for (i in inputStart until input.size) {
            val value = (input[i].toInt() and 0xFF) + remainder * 256
            input[i] = (value / 58).toByte()
            remainder = value % 58
            if (input[i].toInt() != 0) allZero = false
        }
        result.append(BASE58_ALPHABET[remainder])
        if (allZero || input[inputStart].toInt() == 0) {
            // Skip leading zeros in the working array
            while (inputStart < input.size && input[inputStart].toInt() == 0) {
                inputStart++
            }
        }
    }

    // Add leading '1's for each leading zero byte
    repeat(leadingZeros) {
        result.append('1')
    }

    return result.reverse().toString()
}

/**
 * Decodes a base58btc string to bytes.
 *
 * Uses byte array arithmetic to avoid BigInteger dependency (not available in Kotlin Common).
 *
 * @return The decoded bytes
 * @throws IllegalArgumentException if the string contains invalid characters
 */
@JsExportCompat
fun String.decodeFromBase58Btc(): ByteArray {
    if (this.isEmpty()) return ByteArray(0)

    // Count leading '1's (represent leading zero bytes)
    var leadingOnes = 0
    for (char in this) {
        if (char == '1') leadingOnes++ else break
    }

    // Start with a byte array large enough to hold the result
    // Each base58 char represents about log2(58)/8 ≈ 0.73 bytes
    val size = (this.length * 733 / 1000) + 1
    val output = ByteArray(size)

    // Convert from base58
    for (char in this) {
        val index = BASE58_ALPHABET.indexOf(char)
        require(index >= 0) { "Invalid base58 character: $char" }

        var carry = index
        for (i in output.indices.reversed()) {
            carry += 58 * (output[i].toInt() and 0xFF)
            output[i] = (carry and 0xFF).toByte()
            carry = carry shr 8
        }
    }

    // Skip leading zeros in output
    var start = 0
    while (start < output.size && output[start].toInt() == 0) {
        start++
    }

    // Prepend leading zero bytes and return
    return ByteArray(leadingOnes) { 0 } + output.copyOfRange(start, output.size)
}

/**
 * Standard Base64 alphabet (RFC 4648)
 */
private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/**
 * URL-safe Base64 alphabet (RFC 4648 section 5)
 */
private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/**
 * Encodes a ByteArray to Base64 string.
 *
 * @param urlSafe If true, uses URL-safe alphabet (- and _ instead of + and /)
 * @param padding If true, adds padding characters (=) to make output length multiple of 4
 * @return The Base64 encoded string
 */
private fun ByteArray.encodeToBase64Internal(urlSafe: Boolean, padding: Boolean): String {
    if (this.isEmpty()) return ""

    val alphabet = if (urlSafe) BASE64_URL_ALPHABET else BASE64_ALPHABET
    val output = StringBuilder()

    var i = 0
    while (i < this.size) {
        val b0 = this[i].toInt() and 0xFF
        output.append(alphabet[b0 shr 2])

        if (i + 1 < this.size) {
            val b1 = this[i + 1].toInt() and 0xFF
            output.append(alphabet[(b0 and 0x03) shl 4 or (b1 shr 4)])

            if (i + 2 < this.size) {
                val b2 = this[i + 2].toInt() and 0xFF
                output.append(alphabet[(b1 and 0x0F) shl 2 or (b2 shr 6)])
                output.append(alphabet[b2 and 0x3F])
            } else {
                output.append(alphabet[(b1 and 0x0F) shl 2])
                if (padding) output.append('=')
            }
        } else {
            output.append(alphabet[(b0 and 0x03) shl 4])
            if (padding) {
                output.append("==")
            }
        }
        i += 3
    }

    return output.toString()
}

/**
 * Decodes a Base64 string to ByteArray.
 *
 * @param urlSafe If true, expects URL-safe alphabet (- and _ instead of + and /)
 * @return The decoded bytes
 * @throws IllegalArgumentException if the string contains invalid characters
 */
private fun String.decodeFromBase64Internal(urlSafe: Boolean): ByteArray {
    if (this.isEmpty()) return ByteArray(0)

    // Remove whitespace first, then strip padding — ordering matters because
    // trimEnd('=') won't reach padding when whitespace follows it (e.g. "abc==\n")
    val input = this.filter { !it.isWhitespace() }.trimEnd('=')
    if (input.isEmpty()) return ByteArray(0)

    val alphabet = if (urlSafe) BASE64_URL_ALPHABET else BASE64_ALPHABET

    // Build reverse lookup - handle both alphabets for lenient decoding
    val lookup = IntArray(128) { -1 }
    BASE64_ALPHABET.forEachIndexed { index, c -> lookup[c.code] = index }
    BASE64_URL_ALPHABET.forEachIndexed { index, c -> lookup[c.code] = index }

    // Calculate output size
    val outputSize = (input.length * 3) / 4
    val output = ByteArray(outputSize)
    var outputIndex = 0

    var i = 0
    while (i < input.length) {
        val c0 = input[i]
        val c1 = if (i + 1 < input.length) input[i + 1] else 'A'
        val c2 = if (i + 2 < input.length) input[i + 2] else 'A'
        val c3 = if (i + 3 < input.length) input[i + 3] else 'A'

        require(c0.code < 128 && lookup[c0.code] >= 0) { "Invalid base64 character: $c0" }
        require(i + 1 >= input.length || (c1.code < 128 && lookup[c1.code] >= 0)) { "Invalid base64 character: $c1" }

        val v0 = lookup[c0.code]
        val v1 = if (i + 1 < input.length) lookup[c1.code] else 0
        val v2 = if (i + 2 < input.length) lookup[c2.code] else 0
        val v3 = if (i + 3 < input.length) lookup[c3.code] else 0

        if (outputIndex < output.size) {
            output[outputIndex++] = ((v0 shl 2) or (v1 shr 4)).toByte()
        }
        if (i + 2 < input.length && outputIndex < output.size) {
            output[outputIndex++] = ((v1 shl 4) or (v2 shr 2)).toByte()
        }
        if (i + 3 < input.length && outputIndex < output.size) {
            output[outputIndex++] = ((v2 shl 6) or v3).toByte()
        }

        i += 4
    }

    return if (outputIndex == output.size) output else output.copyOf(outputIndex)
}

@JsExportCompat
fun String.decodeFromBase64Url(): ByteArray = this.decodeFromBase64Internal(urlSafe = true)

@JsExportCompat
fun ByteArray.encodeToBase64Url(): String = this.encodeToBase64Internal(urlSafe = true, padding = false)

@JsExportCompat
fun ByteArray.encodeToBase64(urlSafe: Boolean = false): String =
    this.encodeToBase64Internal(urlSafe = urlSafe, padding = true)

@JsExportCompat
fun String.decodeFromBase64(urlSafe: Boolean = false): ByteArray =
    this.decodeFromBase64Internal(urlSafe = urlSafe)

@JsExportCompat
fun String.decodeFrom(encoding: Encoding): ByteArray = when (encoding) {
    Encoding.BASE64URL -> this.decodeFromBase64Url()
    Encoding.BASE64 -> this.decodeFromBase64(urlSafe = false)
    Encoding.BASE58BTC -> this.decodeFromBase58Btc()
    Encoding.HEX -> this.decodeFromHex()
    Encoding.UTF8 -> this.encodeToByteArray()
}

@JsExportCompat
fun ByteArray.encodeTo(encoding: Encoding): String = when (encoding) {
    Encoding.BASE64URL -> this.encodeToBase64Url()
    Encoding.BASE64 -> this.encodeToBase64(urlSafe = false)
    Encoding.BASE58BTC -> this.encodeToBase58Btc()
    Encoding.HEX -> this.encodeToHex()
    Encoding.UTF8 -> this.decodeToString()
}

/**
 * Decode URL-encoded (percent-encoded) string.
 *
 * Handles common percent-encoded characters according to RFC 3986.
 *
 * Example: "Hello%20World" -> "Hello World"
 */
@JsExportCompat
fun String.decodeUrlComponent(): String {
    return this
        .replace("+", " ")
        .replace("%20", " ")
        .replace("%21", "!")
        .replace("%22", "\"")
        .replace("%23", "#")
        .replace("%24", "$")
        .replace("%26", "&")
        .replace("%27", "'")
        .replace("%28", "(")
        .replace("%29", ")")
        .replace("%2A", "*")
        .replace("%2B", "+")
        .replace("%2C", ",")
        .replace("%2F", "/")
        .replace("%3A", ":")
        .replace("%3B", ";")
        .replace("%3D", "=")
        .replace("%3F", "?")
        .replace("%40", "@")
        .replace("%5B", "[")
        .replace("%5D", "]")
        .replace("%7B", "{")
        .replace("%7D", "}")
}

/**
 * Encode string to URL-encoded (percent-encoded) format.
 *
 * Encodes characters according to RFC 3986.
 * Note: The percent character (%) must be encoded first to avoid double-encoding.
 */
@JsExportCompat
fun String.encodeUrlComponent(): String {
    return this
        .replace("%", "%25")  // MUST be first to avoid double-encoding
        .replace(" ", "%20")
        .replace("!", "%21")
        .replace("\"", "%22")
        .replace("#", "%23")
        .replace("$", "%24")
        .replace("&", "%26")
        .replace("'", "%27")
        .replace("(", "%28")
        .replace(")", "%29")
        .replace("*", "%2A")
        .replace("+", "%2B")
        .replace(",", "%2C")
        .replace("/", "%2F")
        .replace(":", "%3A")
        .replace(";", "%3B")
        .replace("=", "%3D")
        .replace("?", "%3F")
        .replace("@", "%40")
        .replace("[", "%5B")
        .replace("]", "%5D")
        .replace("{", "%7B")
        .replace("}", "%7D")
}


/**
 * Serializer that can be used in Kotlin's serialization support to convert byte arrays into base64 strings and vice versa.
 */
object Base64Serializer : KSerializer<ByteArray> {

    override val descriptor = PrimitiveSerialDescriptor("Base64", PrimitiveKind.STRING)

    /**
     * Serializes the given byte array into a base64 string.
     */
    override fun serialize(encoder: Encoder, value: ByteArray) {
        return encoder.encodeString(value.encodeToBase64(urlSafe = false))
    }

    /**
     * Deserializes the given base64 string into a byte array.
     */
    override fun deserialize(decoder: Decoder): ByteArray {
        val str = decoder.decodeString()
        return str.decodeFromBase64(urlSafe = false)
    }
}


/**
 * Serializer that can be used in Kotlin's serialization support to convert byte arrays into base64url strings and vice versa.
 */
object Base64UrlSerializer : KSerializer<ByteArray> {

    override val descriptor = PrimitiveSerialDescriptor("Base64Url", PrimitiveKind.STRING)

    /**
     * Serializes the given byte array into a base64url string.
     */
    override fun serialize(encoder: Encoder, value: ByteArray) {
        return encoder.encodeString(value.encodeToBase64(urlSafe = true))
    }

    /**
     * Deserializes the given base64url string into a byte array.
     */
    override fun deserialize(decoder: Decoder): ByteArray {
        val str = decoder.decodeString()
        return str.decodeFromBase64(urlSafe = true)
    }
}


/**
 * Serializer that can be used in Kotlin's serialization support to convert byte arrays into base58btc strings and vice versa.
 *
 * Base58btc uses the Bitcoin alphabet which excludes 0, O, I, l to avoid visual ambiguity.
 */
object Base58BtcSerializer : KSerializer<ByteArray> {

    override val descriptor = PrimitiveSerialDescriptor("Base58Btc", PrimitiveKind.STRING)

    /**
     * Serializes the given byte array into a base58btc string.
     */
    override fun serialize(encoder: Encoder, value: ByteArray) {
        return encoder.encodeString(value.encodeToBase58Btc())
    }

    /**
     * Deserializes the given base58btc string into a byte array.
     */
    override fun deserialize(decoder: Decoder): ByteArray {
        val str = decoder.decodeString()
        return str.decodeFromBase58Btc()
    }
}


/**
 * A serializer for [Instant] that uses the ISO 8601 representation.
 *
 * JSON example: `"2020-12-09T09:16:56.000124Z"`
 *
 * @see Instant.toString
 * @see Instant.parse
 */
object InstantIso8601Serializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("kotlinx.datetime.Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant =
        Instant.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

}
