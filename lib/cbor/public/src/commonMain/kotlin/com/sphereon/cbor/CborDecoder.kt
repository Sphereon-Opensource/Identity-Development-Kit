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

package com.sphereon.cbor

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.util.getUInt16
import com.sphereon.util.getUInt32
import com.sphereon.util.getUInt64
import com.sphereon.util.getUInt8
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName
import kotlin.jvm.JvmStatic
import kotlin.math.pow

/**
 * Result-based CBOR decoder with configurable security limits.
 *
 * This decoder provides structured error handling via [IdkResult] instead of
 * throwing exceptions. It also enforces security limits to protect against
 * denial-of-service attacks.
 *
 * Security limits:
 * - Maximum nesting depth (prevents stack overflow)
 * - Maximum item count (prevents memory exhaustion)
 * - Maximum string length (prevents memory exhaustion)
 *
 * @see CborDecoderConfig for configuring security limits
 * @see Cbor.tryDecode for the main entry point
 */
@JsExportCompat
object CborDecoder {

    /**
     * Cached exponent table for half-float decoding.
     * This avoids repeated calls to 2f.pow() during decode.
     */
    private val HALF_FLOAT_EXP_TABLE: FloatArray by lazy {
        FloatArray(32) { exp ->
            if (exp == 0) 0f else 2f.pow(exp - 25)
        }
    }

    /**
     * Decodes a complete CBOR data item from a byte array.
     *
     * @param encodedCbor the bytes of the CBOR to decode
     * @param config configuration with security limits (default: standard limits)
     * @return [IdkResult] containing either the decoded item or an error
     */
    @JvmStatic
    fun decode(
        encodedCbor: ByteArray,
        config: CborDecoderConfig = CborDecoderConfig.DEFAULT
    ): IdkResult<CborItem<*>, IdkError> {
        val state = CborDecodeState(config)
        return decodeWithState(encodedCbor, 0, state).flatMap { (newOffset, item) ->
            if (newOffset != encodedCbor.size) {
                Err(CborError.LEFTOVER_BYTES(encodedCbor.size - newOffset))
            } else {
                Ok(item)
            }
        }
    }

    /**
     * Decodes a CBOR data item starting at the given offset.
     *
     * This is useful when processing CBOR sequences or embedded CBOR.
     *
     * @param encodedCbor the bytes of the CBOR to decode
     * @param offset the offset into the byte array to start decoding
     * @param config configuration with security limits (default: standard limits)
     * @return [IdkResult] containing either the (new offset, decoded item) pair or an error
     */
    @JvmStatic
    @JsName("decodeWithOffset")
    fun decodeWithOffset(
        encodedCbor: ByteArray,
        offset: Int,
        config: CborDecoderConfig = CborDecoderConfig.DEFAULT
    ): IdkResult<Pair<Int, CborItem<*>>, IdkError> {
        val state = CborDecodeState(config)
        return decodeWithState(encodedCbor, offset, state)
    }

    /**
     * Internal decode function that tracks state for security limits.
     */
    internal fun decodeWithState(
        encodedCbor: ByteArray,
        offset: Int,
        state: CborDecodeState
    ): IdkResult<Pair<Int, CborItem<*>>, IdkError> {
        // Check item count
        if (!state.incrementItemCount()) {
            return Err(CborError.MAX_ITEMS_EXCEEDED(state.itemCount, state.config.maxItems))
        }

        // Bounds check for first byte
        if (offset >= encodedCbor.size) {
            return Err(CborError.OUT_OF_BOUNDS(offset, 1, encodedCbor.size - offset))
        }

        return try {
            val first = encodedCbor[offset]
            val majorType = MajorType.fromInt(first.toInt().and(0xff) ushr 5)
            val additionalInformation = first.toInt().and(0x1f)

            when (majorType) {
                MajorType.UNSIGNED_INTEGER -> {
                    if (additionalInformation == 31) {
                        return Err(CborError.INDEFINITE_LENGTH_NOT_ALLOWED(majorType.type, offset))
                    }
                    decodeUnsignedInteger(encodedCbor, offset)
                }

                MajorType.NEGATIVE_INTEGER -> {
                    if (additionalInformation == 31) {
                        return Err(CborError.INDEFINITE_LENGTH_NOT_ALLOWED(majorType.type, offset))
                    }
                    decodeNegativeInteger(encodedCbor, offset)
                }

                MajorType.BYTE_STRING -> {
                    if (additionalInformation == 31) {
                        decodeIndefiniteByteString(encodedCbor, offset, state)
                    } else {
                        decodeByteString(encodedCbor, offset, state)
                    }
                }

                MajorType.UNICODE_STRING -> {
                    if (additionalInformation == 31) {
                        decodeIndefiniteString(encodedCbor, offset, state)
                    } else {
                        decodeTextString(encodedCbor, offset, state)
                    }
                }

                MajorType.ARRAY -> decodeArray(encodedCbor, offset, additionalInformation, state)
                MajorType.MAP -> decodeMap(encodedCbor, offset, additionalInformation, state)

                MajorType.TAG -> {
                    if (additionalInformation == 31) {
                        return Err(CborError.INDEFINITE_LENGTH_NOT_ALLOWED(majorType.type, offset))
                    }
                    decodeTagged(encodedCbor, offset, state)
                }

                MajorType.SPECIAL -> decodeSpecial(encodedCbor, offset, additionalInformation)
            }
        } catch (e: IndexOutOfBoundsException) {
            Err(CborError.OUT_OF_BOUNDS(offset, 1, encodedCbor.size - offset))
        } catch (e: Throwable) {
            Err(CborError.DECODE_ERROR(e.message ?: "Unknown error", e))
        }
    }

    // ========================================
    // Type-specific decode functions
    // ========================================

    private fun decodeLength(encodedCbor: ByteArray, offset: Int): IdkResult<Pair<Int, ULong>, IdkError> {
        return try {
            val firstByte = encodedCbor[offset].toInt()
            val additionalInformation = firstByte and 0x1f

            if (additionalInformation < 24) {
                Ok(Pair(offset + 1, additionalInformation.toULong()))
            } else {
                when (additionalInformation) {
                    24 -> Ok(Pair(offset + 2, encodedCbor.getUInt8(offset + 1).toULong()))
                    25 -> Ok(Pair(offset + 3, encodedCbor.getUInt16(offset + 1).toULong()))
                    26 -> Ok(Pair(offset + 5, encodedCbor.getUInt32(offset + 1).toULong()))
                    27 -> Ok(Pair(offset + 9, encodedCbor.getUInt64(offset + 1)))
                    31 -> Ok(Pair(offset + 1, 0UL)) // indefinite length marker
                    else -> Err(CborError.INVALID_ADDITIONAL_INFO(additionalInformation, offset))
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            Err(CborError.OUT_OF_BOUNDS(offset, 1, encodedCbor.size - offset))
        }
    }

    private fun decodeUnsignedInteger(
        encodedCbor: ByteArray,
        offset: Int
    ): IdkResult<Pair<Int, CborItem<*>>, IdkError> {
        return decodeLength(encodedCbor, offset).map { (newOffset, value) ->
            Pair(newOffset, CborUInt(value.toLong()))
        }
    }

    private fun decodeNegativeInteger(
        encodedCbor: ByteArray,
        offset: Int
    ): IdkResult<Pair<Int, CborItem<*>>, IdkError> {
        return decodeLength(encodedCbor, offset).map { (newOffset, value) ->
            // CBOR stores negative integers as -(1+n), so we store n+1 as the absolute value
            Pair(newOffset, CborNInt(value.toLong() + 1L))
        }
    }

    private fun decodeByteString(
        encodedCbor: ByteArray,
        offset: Int,
        state: CborDecodeState
    ): IdkResult<Pair<Int, CborItem<*>>, IdkError> {
        return decodeLength(encodedCbor, offset).flatMap { (newOffset, length) ->
            val len = length.toInt()
            if (!state.checkStringLength(len)) {
                return@flatMap Err(CborError.MAX_STRING_LENGTH_EXCEEDED(len, state.config.maxStringLength))
            }

            val endOffset = newOffset + len
            if (endOffset > encodedCbor.size) {
                return@flatMap Err(CborError.OUT_OF_BOUNDS(newOffset, len, encodedCbor.size - newOffset))
            }

            val bytes = encodedCbor.copyOfRange(newOffset, endOffset)
            Ok(Pair(endOffset, CborByteString(bytes)))
        }
    }

    private fun decodeIndefiniteByteString(
        encodedCbor: ByteArray,
        offset: Int,
        state: CborDecodeState
    ): IdkResult<Pair<Int, CborItem<*>>, IdkError> {
        var currentOffset = offset + 1 // skip the 0x5f byte
        val chunks = mutableListOf<ByteArray>()

        while (currentOffset < encodedCbor.size) {
            val byte = encodedCbor[currentOffset].toUByte()
            if (byte == Cbor.BREAK) {
                return Ok(Pair(currentOffset + 1, CborByteStringIndefLength(chunks)))
            }

            // Must be a definite-length byte string
            val majorType = (byte.toInt() ushr 5) and 0x07
            if (majorType != MajorType.BYTE_STRING.type) {
                return Err(CborError.DECODE_ERROR("Expected byte string chunk in indefinite-length byte string at offset $currentOffset"))
            }

            val result = decodeByteString(encodedCbor, currentOffset, state)
            if (result.isErr) return result.map { it }
            val (newOffset, item) = result.value
            chunks.add((item as CborByteString).value)
            currentOffset = newOffset

            if (!state.incrementItemCount()) {
                return Err(CborError.MAX_ITEMS_EXCEEDED(state.itemCount, state.config.maxItems))
            }
        }

        return Err(CborError.OUT_OF_BOUNDS(currentOffset, 1, 0))
    }

    private fun decodeTextString(
        encodedCbor: ByteArray,
        offset: Int,
        state: CborDecodeState
    ): IdkResult<Pair<Int, CborItem<*>>, IdkError> {
        return decodeLength(encodedCbor, offset).flatMap { (newOffset, length) ->
            val len = length.toInt()
            if (!state.checkStringLength(len)) {
                return@flatMap Err(CborError.MAX_STRING_LENGTH_EXCEEDED(len, state.config.maxStringLength))
            }

            val endOffset = newOffset + len
            if (endOffset > encodedCbor.size) {
                return@flatMap Err(CborError.OUT_OF_BOUNDS(newOffset, len, encodedCbor.size - newOffset))
            }

            val bytes = encodedCbor.copyOfRange(newOffset, endOffset)
            try {
                val str = bytes.decodeToString()
                Ok(Pair(endOffset, CborString(str)))
            } catch (e: Exception) {
                Err(CborError.INVALID_UTF8(newOffset))
            }
        }
    }

    private fun decodeIndefiniteString(
        encodedCbor: ByteArray,
        offset: Int,
        state: CborDecodeState
    ): IdkResult<Pair<Int, CborItem<*>>, IdkError> {
        var currentOffset = offset + 1 // skip the 0x7f byte
        val chunks = mutableListOf<String>()

        while (currentOffset < encodedCbor.size) {
            val byte = encodedCbor[currentOffset].toUByte()
            if (byte == Cbor.BREAK) {
                return Ok(Pair(currentOffset + 1, CborStringIndefLength(chunks)))
            }

            // Must be a definite-length text string
            val majorType = (byte.toInt() ushr 5) and 0x07
            if (majorType != MajorType.UNICODE_STRING.type) {
                return Err(CborError.DECODE_ERROR("Expected text string chunk in indefinite-length text string at offset $currentOffset"))
            }

            val result = decodeTextString(encodedCbor, currentOffset, state)
            if (result.isErr) return result.map { it }
            val (newOffset, item) = result.value
            chunks.add((item as CborString).value)
            currentOffset = newOffset

            if (!state.incrementItemCount()) {
                return Err(CborError.MAX_ITEMS_EXCEEDED(state.itemCount, state.config.maxItems))
            }
        }

        return Err(CborError.OUT_OF_BOUNDS(currentOffset, 1, 0))
    }

    private fun decodeArray(
        encodedCbor: ByteArray,
        offset: Int,
        additionalInformation: Int,
        state: CborDecodeState
    ): IdkResult<Pair<Int, CborItem<*>>, IdkError> {
        // Check depth limit
        if (!state.enterContainer()) {
            return Err(CborError.MAX_DEPTH_EXCEEDED(state.currentDepth, state.config.maxDepth))
        }

        try {
            val isIndefinite = additionalInformation == 31
            val items = mutableListOf<CborItem<*>>()

            return if (isIndefinite) {
                var currentOffset = offset + 1 // skip the 0x9f byte
                while (currentOffset < encodedCbor.size) {
                    if (encodedCbor[currentOffset].toUByte() == Cbor.BREAK) {
                        state.exitContainer()
                        return Ok(Pair(currentOffset + 1, CborArray(items, indefiniteLength = true)))
                    }

                    val result = decodeWithState(encodedCbor, currentOffset, state)
                    if (result.isErr) {
                        state.exitContainer()
                        return result
                    }
                    val (newOffset, item) = result.value
                    items.add(item)
                    currentOffset = newOffset
                }
                state.exitContainer()
                Err(CborError.OUT_OF_BOUNDS(currentOffset, 1, 0))
            } else {
                decodeLength(encodedCbor, offset).flatMap { (lengthOffset, length) ->
                    var currentOffset = lengthOffset
                    val count = length.toInt()

                    for (i in 0 until count) {
                        val result = decodeWithState(encodedCbor, currentOffset, state)
                        if (result.isErr) {
                            state.exitContainer()
                            return@flatMap result
                        }
                        val (newOffset, item) = result.value
                        items.add(item)
                        currentOffset = newOffset
                    }

                    state.exitContainer()
                    Ok(Pair(currentOffset, CborArray(items)))
                }
            }
        } catch (e: Exception) {
            state.exitContainer()
            throw e
        }
    }

    private fun decodeMap(
        encodedCbor: ByteArray,
        offset: Int,
        additionalInformation: Int,
        state: CborDecodeState
    ): IdkResult<Pair<Int, CborItem<*>>, IdkError> {
        // Check depth limit
        if (!state.enterContainer()) {
            return Err(CborError.MAX_DEPTH_EXCEEDED(state.currentDepth, state.config.maxDepth))
        }

        try {
            val isIndefinite = additionalInformation == 31
            val items = mutableMapOf<CborItem<*>, CborItem<*>?>()

            return if (isIndefinite) {
                var currentOffset = offset + 1 // skip the 0xbf byte
                while (currentOffset < encodedCbor.size) {
                    if (encodedCbor[currentOffset].toUByte() == Cbor.BREAK) {
                        state.exitContainer()
                        return Ok(Pair(currentOffset + 1, CborMap(items, indefiniteLength = true)))
                    }

                    // Decode key
                    val keyResult = decodeWithState(encodedCbor, currentOffset, state)
                    if (keyResult.isErr) {
                        state.exitContainer()
                        return keyResult
                    }
                    val (keyOffset, key) = keyResult.value

                    // Decode value
                    val valueResult = decodeWithState(encodedCbor, keyOffset, state)
                    if (valueResult.isErr) {
                        state.exitContainer()
                        return valueResult
                    }
                    val (valueOffset, value) = valueResult.value

                    items[key] = value
                    currentOffset = valueOffset
                }
                state.exitContainer()
                Err(CborError.OUT_OF_BOUNDS(currentOffset, 1, 0))
            } else {
                decodeLength(encodedCbor, offset).flatMap { (lengthOffset, length) ->
                    var currentOffset = lengthOffset
                    val count = length.toInt()

                    for (i in 0 until count) {
                        // Decode key
                        val keyResult = decodeWithState(encodedCbor, currentOffset, state)
                        if (keyResult.isErr) {
                            state.exitContainer()
                            return@flatMap keyResult
                        }
                        val (keyOffset, key) = keyResult.value

                        // Decode value
                        val valueResult = decodeWithState(encodedCbor, keyOffset, state)
                        if (valueResult.isErr) {
                            state.exitContainer()
                            return@flatMap valueResult
                        }
                        val (valueOffset, value) = valueResult.value

                        items[key] = value
                        currentOffset = valueOffset
                    }

                    state.exitContainer()
                    Ok(Pair(currentOffset, CborMap(items)))
                }
            }
        } catch (e: Exception) {
            state.exitContainer()
            throw e
        }
    }

    private fun decodeTagged(
        encodedCbor: ByteArray,
        offset: Int,
        state: CborDecodeState
    ): IdkResult<Pair<Int, CborItem<*>>, IdkError> {
        // Check depth limit
        if (!state.enterContainer()) {
            return Err(CborError.MAX_DEPTH_EXCEEDED(state.currentDepth, state.config.maxDepth))
        }

        return try {
            decodeLength(encodedCbor, offset).flatMap { (tagOffset, tagNumber) ->
                val tagNum = tagNumber.toInt()

                // Decode the tagged item
                val result = decodeWithState(encodedCbor, tagOffset, state)
                state.exitContainer()

                if (result.isErr) {
                    return@flatMap result
                }
                val (itemOffset, taggedItem) = result.value

                // Handle special tag for encoded CBOR
                val item = if (tagNum == CborTagged.ENCODED_CBOR && taggedItem is CborByteString) {
                    CborEncodedItem<CborBaseItem>(taggedItem)
                } else {
                    CborTagged(tagNum, taggedItem)
                }

                Ok(Pair(itemOffset, item))
            }
        } catch (e: Exception) {
            state.exitContainer()
            throw e
        }
    }

    private fun decodeSpecial(
        encodedCbor: ByteArray,
        offset: Int,
        additionalInformation: Int
    ): IdkResult<Pair<Int, CborItem<*>>, IdkError> {
        return when (additionalInformation) {
            // Simple values 0-19 are reserved/unassigned in CBOR
            in 0..19 -> Err(CborError.DECODE_ERROR("Reserved simple value $additionalInformation at offset $offset"))
            20 -> Ok(Pair(offset + 1, CborSimple.FALSE))
            21 -> Ok(Pair(offset + 1, CborSimple.TRUE))
            22 -> Ok(Pair(offset + 1, CborSimple.NULL))
            23 -> Ok(Pair(offset + 1, CborSimple.UNDEFINED))
            24 -> {
                // One-byte simple value follows
                if (offset + 1 >= encodedCbor.size) {
                    return Err(CborError.OUT_OF_BOUNDS(offset + 1, 1, encodedCbor.size - offset - 1))
                }
                val value = encodedCbor.getUInt8(offset + 1).toInt()
                // Simple values 0-31 should use direct encoding, values 32-255 use this form
                if (value < 32) {
                    return Err(CborError.DECODE_ERROR("Two-byte simple value must be >= 32, got $value at offset $offset"))
                }
                // For now, treat unrecognized simple values as an error since CborSimple is abstract
                Err(CborError.DECODE_ERROR("Unknown simple value $value at offset $offset"))
            }
            25 -> { // half-precision float (16-bit)
                if (offset + 2 >= encodedCbor.size) {
                    return Err(CborError.OUT_OF_BOUNDS(offset + 1, 2, encodedCbor.size - offset - 1))
                }
                val raw = encodedCbor.getUInt16(offset + 1).toInt()
                val value = fromRawHalfFloat(raw)
                Ok(Pair(offset + 3, CborFloat16(value)))
            }
            26 -> { // single-precision float (32-bit)
                if (offset + 4 >= encodedCbor.size) {
                    return Err(CborError.OUT_OF_BOUNDS(offset + 1, 4, encodedCbor.size - offset - 1))
                }
                val bits = encodedCbor.getUInt32(offset + 1).toInt()
                val value = Float.fromBits(bits)
                Ok(Pair(offset + 5, CborFloat(value, CDDL.float)))
            }
            27 -> { // double-precision float (64-bit)
                if (offset + 8 >= encodedCbor.size) {
                    return Err(CborError.OUT_OF_BOUNDS(offset + 1, 8, encodedCbor.size - offset - 1))
                }
                val bits = encodedCbor.getUInt64(offset + 1).toLong()
                val value = Double.fromBits(bits)
                Ok(Pair(offset + 9, CborDouble(value)))
            }
            31 -> Err(CborError.UNEXPECTED_BREAK(offset))
            else -> Err(CborError.INVALID_ADDITIONAL_INFO(additionalInformation, offset))
        }
    }

    /**
     * Converts raw half-float (16-bit float) to Float using cached exponent table.
     */
    private fun fromRawHalfFloat(raw: Int): Float {
        val exp = (raw shr 10) and 0x1f
        val mant = raw and 0x3ff
        val sign = (raw and 0x8000) != 0

        val value: Float = when {
            exp == 0 -> mant * 2f.pow(-24)
            exp == 31 -> if (mant == 0) Float.POSITIVE_INFINITY else Float.NaN
            else -> (mant + 1024) * HALF_FLOAT_EXP_TABLE[exp]
        }

        return if (sign) -value else value
    }
}
