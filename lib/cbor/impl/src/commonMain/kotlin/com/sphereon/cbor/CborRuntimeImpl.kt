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

package com.sphereon.cbor

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.util.appendUInt16
import com.sphereon.util.appendUInt32
import com.sphereon.util.appendUInt64
import com.sphereon.util.appendUInt8
import com.sphereon.util.getUInt16
import com.sphereon.util.getUInt32
import com.sphereon.util.getUInt64
import com.sphereon.util.getUInt8
import kotlinx.io.bytestring.ByteStringBuilder
import kotlin.experimental.or
import kotlin.js.JsName
import kotlin.jvm.JvmStatic
import kotlin.math.pow

/**
 * CBOR support routines. Shamelessly copied and inspired by Google as we got fed up with having to jump through hoops with Kotlinx-serialization
 *
 * This package includes support for CBOR as specified in
 * [RFC 8849](https://www.rfc-editor.org/rfc/rfc8949.html).
 */
internal object CborRuntimeImpl {
    /** As defined in RFC 8949 3.2.1. The "break" stop code */
    const val BREAK: UByte = 0xffu
    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    internal fun encodeLength(
        builder: ByteStringBuilder,
        majorType: MajorType,
        length: Int,
    ) = encodeLength(builder, majorType, length.toULong())

    internal fun encodeLength(
        builder: ByteStringBuilder,
        majorType: MajorType,
        length: ULong,
    ) {
        val majorTypeShifted = (majorType.type shl 5).toUByte()
        builder.apply {
            when {
                length < 24U -> appendUInt8(majorTypeShifted.or(length.toUByte()))
                length < (1UL shl 8) -> appendUInt8(majorTypeShifted.or(24u)).appendUInt8(length.toUByte())
                length < (1UL shl 16) -> appendUInt8(majorTypeShifted.or(25u)).appendUInt16(length.toUInt())
                length < (1UL shl 32) -> appendUInt8(majorTypeShifted.or(26u)).appendUInt32(length.toUInt())
                else -> appendUInt8(majorTypeShifted.or(27u)).appendUInt64(length)
            }
        }
    }

    /**
     * Encodes a data item to CBOR.
     *
     * @param item the [DataItem] to encode.
     * @returns the bytes of the item.
     */
    @JvmStatic
    fun encode(item: CborItem<*>?): ByteArray {
        val input = item ?: CborNull()
        val builder = ByteStringBuilder()
        encodeItem(builder, input)
        return builder.toByteString().toByteArray()
    }

    private fun encodeItem(
        builder: ByteStringBuilder,
        item: CborItem<*>,
    ) {
        when (item) {
            is RawCbor -> {
                builder.append(item.value)
            }

            is CborEncodedItem<*> -> {
                encodeTaggedByteString(builder, item.value.taggedItem.value)
            }

            is CborTagged<*> -> {
                encodeLength(builder, MajorType.TAG, item.tagNumber)
                encodeItem(builder, item.taggedItem)
            }

            is CborByteStringIndefLength -> {
                builder.append(((MajorType.BYTE_STRING.type shl 5) + 31).toByte())
                item.value.forEach { chunk ->
                    encodeLength(builder, MajorType.BYTE_STRING, chunk.size)
                    builder.append(chunk)
                }
                builder.append(BREAK.toByte())
            }

            is CborByteString -> {
                encodeLength(builder, MajorType.BYTE_STRING, item.value.size)
                builder.append(item.value)
            }

            is CborStringIndefLength -> {
                builder.append(((MajorType.UNICODE_STRING.type shl 5) + 31).toByte())
                item.value.forEach { chunk ->
                    val encodedChunk = chunk.encodeToByteArray()
                    encodeLength(builder, MajorType.UNICODE_STRING, encodedChunk.size)
                    builder.append(encodedChunk)
                }
                builder.append(BREAK.toByte())
            }

            is CborString -> {
                val encodedValue = item.value.encodeToByteArray()
                encodeLength(builder, MajorType.UNICODE_STRING, encodedValue.size)
                builder.append(encodedValue)
            }

            is StringLabel -> {
                val encodedValue = item.value.encodeToByteArray()
                encodeLength(builder, MajorType.UNICODE_STRING, encodedValue.size)
                builder.append(encodedValue)
            }

            is NumberLabel -> {
                encodeItem(builder, item.toCborItem())
            }

            is CborUInt -> {
                encodeLength(builder, MajorType.UNSIGNED_INTEGER, item.value.toULong())
            }

            is CborNInt -> {
                encodeLength(builder, MajorType.NEGATIVE_INTEGER, (item.value - 1L).toULong())
            }

            is CborInt -> {
                if (item.majorType == MajorType.NEGATIVE_INTEGER) {
                    encodeLength(builder, MajorType.NEGATIVE_INTEGER, (item.value.toLong() - 1L).toULong())
                } else {
                    encodeLength(builder, MajorType.UNSIGNED_INTEGER, item.value.toLong().toULong())
                }
            }

            is CborArray<*> -> {
                if (item.indefiniteLength) {
                    builder.append(((MajorType.ARRAY.type shl 5) + 31).toByte())
                } else {
                    encodeLength(builder, MajorType.ARRAY, item.value.size)
                }
                item.value.forEach { element -> encodeItem(builder, element) }
                if (item.indefiniteLength) {
                    builder.append(BREAK.toByte())
                }
            }

            is CborMap<*, *> -> {
                if (item.indefiniteLength) {
                    builder.append(((MajorType.MAP.type shl 5) + 31).toByte())
                } else {
                    encodeLength(builder, MajorType.MAP, item.value.size)
                }
                item.value.forEach { (key, value) ->
                    encodeItem(builder, key)
                    value?.let { encodeItem(builder, it) }
                }
                if (item.indefiniteLength) {
                    builder.append(BREAK.toByte())
                }
            }

            is CborSimple<*> -> {
                val majorTypeShifted = (MajorType.SPECIAL.type shl 5).toByte()
                builder.append(majorTypeShifted.or(item.info!!.toByte()))
            }

            is CborDouble -> {
                val raw = item.value.toRawBits()
                builder.append(((MajorType.SPECIAL.type shl 5).toByte()).or(27.toByte()))
                builder.append((raw shr 56).and(0xff).toByte())
                builder.append((raw shr 48).and(0xff).toByte())
                builder.append((raw shr 40).and(0xff).toByte())
                builder.append((raw shr 32).and(0xff).toByte())
                builder.append((raw shr 24).and(0xff).toByte())
                builder.append((raw shr 16).and(0xff).toByte())
                builder.append((raw shr 8).and(0xff).toByte())
                builder.append((raw shr 0).and(0xff).toByte())
            }

            is CborFloat -> {
                val raw = item.value.toRawBits()
                builder.append(((MajorType.SPECIAL.type shl 5).toByte()).or(26.toByte()))
                builder.append((raw shr 24).and(0xff).toByte())
                builder.append((raw shr 16).and(0xff).toByte())
                builder.append((raw shr 8).and(0xff).toByte())
                builder.append((raw shr 0).and(0xff).toByte())
            }

            is CborAny<*> -> {
                encodeItem(builder, (item.cddl as CDDL).newCborItem(item.value))
            }

            else -> {
                throw IllegalArgumentException("Unsupported CBOR item type ${item::class.simpleName}")
            }
        }
    }

    private fun encodeTaggedByteString(
        builder: ByteStringBuilder,
        bytes: ByteArray,
    ) {
        encodeLength(builder, MajorType.TAG, CborTagged.ENCODED_CBOR)
        encodeLength(builder, MajorType.BYTE_STRING, bytes.size)
        builder.append(bytes)
    }

    // returns the new offset, then the length/value encoded in the decoded content
    //
    // throws IllegalArgumentException if not enough data or if additionalInformation
    // field is invalid
    //
    internal fun decodeLength(
        encodedCbor: ByteArray,
        offset: Int,
    ): Pair<Int, ULong> {
        require(offset < encodedCbor.size) { "Out of data at offset $offset" }
        val firstByte = encodedCbor[offset].toInt()
        val additionalInformation = firstByte and 0x1f
        if (additionalInformation < 24) {
            return Pair(offset + 1, additionalInformation.toULong())
        }
        // Check we have enough bytes for the additional data
        val requiredBytes =
            when (additionalInformation) {
                24 -> 2

                25 -> 3

                26 -> 5

                27 -> 9

                31 -> 1

                else -> throw IllegalArgumentException(
                    "Illegal additional information value $additionalInformation at offset $offset",
                )
            }
        require(offset + requiredBytes <= encodedCbor.size) {
            "Out of data at offset $offset: need $requiredBytes bytes but only ${encodedCbor.size - offset} available"
        }
        return when (additionalInformation) {
            24 -> Pair(offset + 2, encodedCbor.getUInt8(offset + 1).toULong())

            25 -> Pair(offset + 3, encodedCbor.getUInt16(offset + 1).toULong())

            26 -> Pair(offset + 5, encodedCbor.getUInt32(offset + 1).toULong())

            27 -> Pair(offset + 9, encodedCbor.getUInt64(offset + 1))

            31 -> Pair(offset + 1, 0UL)

            // indefinite length
            else -> throw IllegalArgumentException(
                "Illegal additional information value $additionalInformation at offset $offset",
            )
        }
    }

    // This is based on the C code in https://www.rfc-editor.org/rfc/rfc8949.html#section-appendix.d
    private fun fromRawHalfFloat(raw: Int): Float {
        val exp = (raw shr 10) and 0x1f
        val mant = raw and 0x3ff
        val sign = (raw and 0x8000) != 0
        val value: Float
        if (exp == 0) {
            value = mant * 2f.pow(-24)
        } else if (exp != 31) {
            value = (mant + 1024) * 2f.pow(exp - 25)
        } else {
            value = (
                if (mant == 0) {
                    Float.POSITIVE_INFINITY
                } else {
                    Float.NaN
                }
            )
        }
        return if (sign) {
            -value
        } else {
            value
        }
    }

    // ========================================
    // Result-based decoding methods (preferred)
    // ========================================

    /**
     * Decodes a complete CBOR data item from a byte array, returning a result.
     *
     * This is the preferred method for decoding CBOR as it returns structured errors
     * via [IdkResult] instead of throwing exceptions.
     *
     * @param encodedCbor the bytes of the CBOR to decode.
     * @param config configuration with security limits (default: standard limits)
     * @return [IdkResult] containing either the decoded item or an error
     */
    @JvmStatic
    fun tryDecode(
        encodedCbor: ByteArray,
        config: CborDecoderConfig = CborDecoderConfig.DEFAULT,
    ): IdkResult<CborItem<*>, IdkError> = CborDecoderRuntimeImpl.decode(encodedCbor, config)

    /**
     * Decodes a CBOR data item starting at the given offset, returning a result.
     *
     * This is the preferred method for decoding CBOR with offset as it returns
     * structured errors via [IdkResult] instead of throwing exceptions.
     *
     * @param encodedCbor the bytes of the CBOR to decode.
     * @param offset the offset into the byte array to start decoding.
     * @param config configuration with security limits (default: standard limits)
     * @return [IdkResult] containing either the (new offset, decoded item) pair or an error
     */
    @JvmStatic
    @JsName("tryDecodeWithOffset")
    fun tryDecodeWithOffset(
        encodedCbor: ByteArray,
        offset: Int,
        config: CborDecoderConfig = CborDecoderConfig.DEFAULT,
    ): IdkResult<CborDecodedItem, IdkError> =
        CborDecoderRuntimeImpl.decodeWithOffset(encodedCbor, offset, config).map { (newOffset, item) ->
            CborDecodedItem(newOffset, item)
        }

    // ========================================
    // Legacy exception-based decoding methods
    // ========================================

    /**
     * Low-level function to decode CBOR.
     *
     * This decodes a single CBOR data item and also returns the offset of the given byte array
     * of the data that was consumed.
     *
     * @param encodedCbor the bytes of the CBOR to decode.
     * @param offset the offset into the byte array to start decoding.
     * @return a pair where the first value is the ending offset and the second value is the
     * decoded data item.
     * @throws IllegalArgumentException if the data isn't valid CBOR.
     * @see tryDecodeWithOffset for the result-based alternative
     */
    @JvmStatic
    @JsName("decodeWithOffset")
    @Deprecated(
        message = "Use tryDecodeWithOffset() which returns IdkResult instead of throwing exceptions",
        replaceWith = ReplaceWith("tryDecodeWithOffset(encodedCbor, offset)"),
    )
    fun decode(
        encodedCbor: ByteArray,
        offset: Int,
    ): CborDecodedItem =
        tryDecodeWithOffset(encodedCbor, offset).getOrElse { error ->
            throw IllegalArgumentException(error.message.defaultMessage, error.exception)
        }

    /**
     * Decodes CBOR.
     *
     * The given [ByteArray] should contain the bytes of a single CBOR data item.
     *
     * @param encodedCbor the bytes of the CBOR to decode.
     * @return a [DataItem] with the decoded data.
     * @throws IllegalArgumentException if bytes are left over or the data isn't valid CBOR.
     * @see tryDecode for the result-based alternative
     */
    @JvmStatic
    @Deprecated(
        message = "Use tryDecode() which returns IdkResult instead of throwing exceptions",
        replaceWith = ReplaceWith("tryDecode(encodedCbor)"),
    )
    @Suppress("UNCHECKED_CAST")
    fun <T : CborItem<*>> decode(encodedCbor: ByteArray): T =
        tryDecode(encodedCbor).getOrElse { error ->
            throw IllegalArgumentException(error.message.defaultMessage, error.exception)
        } as T

    // Returns true iff all elements in |items| are not compound (e.g. an array or a map).
    private fun allDataItemsNonCompound(
        items: List<CborItem<*>>,
        options: Set<DiagnosticOption>,
    ): Boolean {
        for (item in items) {
            if (options.contains(DiagnosticOption.EMBEDDED_CBOR) &&
                item is CborTagged && item.tagNumber == CborTagged.ENCODED_CBOR
            ) {
                return false
            }
            when (item.majorType) {
                MajorType.ARRAY, MajorType.MAP -> {
                    return false
                }

                else -> {}
            }
        }
        return true
    }

    private fun fitsInASingleLine(
        items: List<CborItem<*>>,
        options: Set<DiagnosticOption>,
    ): Boolean =
        // For now just use this heuristic.
        allDataItemsNonCompound(items, options) && items.size < 8

    @Suppress("DEPRECATION")
    private fun toDiagnostics(
        sb: StringBuilder,
        indent: Int,
        item: CborItem<*>,
        tagNumberOfParent: Int?,
        options: Set<DiagnosticOption>,
    ) {
        val pretty = options.contains(DiagnosticOption.PRETTY_PRINT)
        val indentString =
            if (!pretty) {
                ""
            } else {
                val indentBuilder = StringBuilder()
                for (n in 0 until indent) {
                    indentBuilder.append(' ')
                }
                indentBuilder.toString()
            }

        if (item is RawCbor) {
            toDiagnostics(sb, indent, decode(item.value), tagNumberOfParent, options)
            return
        }

        when (item.majorType) {
            MajorType.UNSIGNED_INTEGER -> {
                when (item) {
                    is CborUInt -> sb.append(item.value)
                    is NumberLabel -> sb.append(item.value)
                    else -> error("Unexpected item type $item for UNSIGNED_INTEGER")
                }
            }

            MajorType.NEGATIVE_INTEGER -> {
                sb.append('-')
                when (item) {
                    is CborNInt -> sb.append(item.value)
                    is NumberLabel -> sb.append(item.value)
                    else -> error("Unexpected item type $item for NEGATIVE_INTEGER")
                }
            }

            MajorType.BYTE_STRING -> {
                when (item) {
                    is CborByteStringIndefLength -> {
                        if (DiagnosticOption.BSTR_PRINT_LENGTH in options) {
                            sb.append("indefinite-size byte-string")
                        } else {
                            sb.append("(_")
                            var count = 0
                            for (chunk in item.value) {
                                if (count++ == 0) {
                                    sb.append(" h'")
                                } else {
                                    sb.append(", h'")
                                }
                                for (b in chunk) {
                                    sb.append(HEX_DIGITS[b.toInt().and(0xff) shr 4])
                                    sb.append(HEX_DIGITS[b.toInt().and(0x0f)])
                                }
                                sb.append('\'')
                            }
                            sb.append(')')
                        }
                    }

                    is CborByteString -> {
                        if ((tagNumberOfParent != null && tagNumberOfParent == CborTagged.ENCODED_CBOR) || item is CborEncodedItem<*>) {
                            sb.append("<< ")
                            try {
                                val embeddedItem: CborItem<*> = decode(item.value)
                                toDiagnostics(sb, indent, embeddedItem, null, options)
                            } catch (_: Exception) {
                                // Never throw an exception
                                sb.append("Error Decoding CBOR")
                            }
                            sb.append(" >>")
                        } else {
                            if (DiagnosticOption.BSTR_PRINT_LENGTH in options) {
                                when (item.value.size) {
                                    1 -> sb.append("${item.value.size} byte")
                                    else -> sb.append("${item.value.size} bytes")
                                }
                            } else {
                                sb.append("h'")
                                for (b in item.value) {
                                    sb.append(HEX_DIGITS[b.toInt().and(0xff) shr 4])
                                    sb.append(HEX_DIGITS[b.toInt().and(0x0f)])
                                }
                                sb.append("'")
                            }
                        }
                    }

                    else -> {
                        error("Unexpected item type $item")
                    }
                }
            }

            MajorType.UNICODE_STRING -> {
                when (item) {
                    is CborStringIndefLength -> {
                        sb.append("(_")
                        var count = 0
                        for (chunk in item.value) {
                            if (count++ == 0) {
                                sb.append(" \"")
                            } else {
                                sb.append(", \"")
                            }
                            val escapedChunkValue =
                                chunk
                                    .replace("\\", "\\\\")
                                    .replace("\"", "\\\"")
                            sb.append("$escapedChunkValue\"")
                        }
                        sb.append(')')
                    }

                    is CborString -> {
                        val escapedTstrValue =
                            item.value
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                        sb.append("\"$escapedTstrValue\"")
                    }

                    is StringLabel -> {
                        val escapedTstrValue =
                            item.value
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                        sb.append("\"$escapedTstrValue\"")
                    }

                    else -> {
                        error("Unexpected item type $item")
                    }
                }
            }

            MajorType.ARRAY -> {
                val items = (item as CborArray<out CborItem<*>>).value
                if (!pretty || fitsInASingleLine(items, options)) {
                    sb.append(
                        if (item.indefiniteLength) {
                            "[_ "
                        } else {
                            "["
                        }
                    )
                    var count = 0
                    for (elementItem in items) {
                        toDiagnostics(sb, indent, elementItem, null, options)
                        if (++count < items.size) {
                            sb.append(", ")
                        }
                    }
                    sb.append("]")
                } else {
                    sb.append("[\n").append(indentString)
                    var count = 0
                    for (elementItem in items) {
                        sb.append("  ")
                        toDiagnostics(sb, indent + 2, elementItem, null, options)
                        if (++count < items.size) {
                            sb.append(",")
                        }
                        sb.append("\n").append(indentString)
                    }
                    sb.append("]")
                }
            }

            MajorType.MAP -> {
                val items = (item as CborMap<*, *>).value
                if (!pretty || items.isEmpty()) {
                    sb.append(
                        if (item.indefiniteLength) {
                            "{_ "
                        } else {
                            "{"
                        }
                    )
                    var count = 0
                    for ((key, value) in items) {
                        toDiagnostics(sb, indent, key, null, options)
                        sb.append(": ")
                        toDiagnostics(sb, indent + 2, value!!, null, options)
                        if (++count < items.size) {
                            sb.append(", ")
                        }
                    }
                    sb.append("}")
                } else {
                    sb.append(
                        if (item.indefiniteLength) {
                            "{_\n"
                        } else {
                            "{\n"
                        }
                    )
                    sb.append(indentString)
                    var count = 0
                    for ((key, value) in items) {
                        sb.append("  ")
                        toDiagnostics(sb, indent + 2, key, null, options)
                        sb.append(": ")
                        toDiagnostics(sb, indent + 2, value!!, null, options)
                        if (++count < items.size) {
                            sb.append(",")
                        }
                        sb.append("\n").append(indentString)
                    }
                    sb.append("}")
                }
            }

            MajorType.TAG -> {
                val tagNumber = (item as CborTagged).tagNumber
                sb.append("$tagNumber(")
                toDiagnostics(sb, indent, item.taggedItem, tagNumber, options)
                sb.append(")")
            }

            MajorType.SPECIAL -> {
                when (item) {
                    is CborSimple -> {
                        when (item) {
                            CborSimple.FALSE -> sb.append("false")
                            CborSimple.TRUE -> sb.append("true")
                            CborSimple.NULL -> sb.append("null")
                            CborSimple.UNDEFINED -> sb.append("undefined")
                            else -> sb.append("simple(${item.value})")
                        }
                    }

                    is CborFloat -> {
                        sb.append(item.value)
                    }

                    is CborDouble -> {
                        sb.append(item.value)
                    }

                    else -> {
                        throw IllegalArgumentException("Unexpected instance for MajorType.SPECIAL")
                    }
                }
            }

            null -> {
                TODO()
            }
        }
    }

    /**
     * Returns the diagnostics notation for a data item.
     *
     * @param item the CBOR data item.
     * @param options zero or more [DiagnosticOption].
     */
    @JvmStatic
    fun toDiagnostics(
        item: CborItem<*>,
        options: Set<DiagnosticOption> = emptySet(),
    ): String {
        val sb = StringBuilder()
        toDiagnostics(sb, 0, item, null, options)
        return sb.toString()
    }

    /**
     * Returns the diagnostics notation for an encoded data item.
     *
     * @param encodedItem the encoded CBOR data item.
     * @param options zero or more [DiagnosticOption].
     */
    @JvmStatic
    @Suppress("DEPRECATION")
    fun toDiagnosticsEncoded(
        encodedItem: ByteArray,
        options: Set<DiagnosticOption> = emptySet(),
    ): String {
        val sb = StringBuilder()
        toDiagnostics(sb, 0, decode(encodedItem), null, options)
        return sb.toString()
    }
}
