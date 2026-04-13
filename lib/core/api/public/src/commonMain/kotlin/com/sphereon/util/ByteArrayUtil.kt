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

package com.sphereon.util

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.buildByteString

/*
 * Extension functions for reading and writing integer values from/to [ByteArray].
 *
 * All multi-byte functions default to big-endian byte order. Little-endian variants are suffixed with `Le`.
 * Each `put*` function validates the value against a configurable range before writing.
 */

// Byte mask for extracting the low 8 bits of an integer
private const val BYTE_MASK = 0xFF

// Byte mask for extracting the low 8 bits of a long value
private const val BYTE_MASK_LONG = 0xFFL

// Bit-shift amounts for multi-byte serialization
private const val SHIFT_8 = 8
private const val SHIFT_16 = 16
private const val SHIFT_24 = 24
private const val SHIFT_32 = 32
private const val SHIFT_40 = 40
private const val SHIFT_48 = 48
private const val SHIFT_56 = 56

// Byte offsets within multi-byte integers
private const val OFFSET_1 = 1
private const val OFFSET_2 = 2
private const val OFFSET_3 = 3
private const val OFFSET_4 = 4
private const val OFFSET_5 = 5
private const val OFFSET_6 = 6
private const val OFFSET_7 = 7

//region Writers - Signed

/** Writes an Int8 [value] at [offset], validated against [validRange]. */
fun ByteArray.putInt8(
    offset: Int,
    value: Int,
    validRange: IntRange = Byte.MIN_VALUE..Byte.MAX_VALUE,
) {
    value.requireInRange(validRange)
    this[offset] = value.toByte()
}

/** Writes an Int16 [value] at [offset] in big-endian order, validated against [validRange]. */
fun ByteArray.putInt16(
    offset: Int,
    value: Int,
    validRange: IntRange = Short.MIN_VALUE..Short.MAX_VALUE,
) {
    value.requireInRange(validRange)
    writeInt16BE(offset, value)
}

/** Writes an Int16 [value] at [offset] in little-endian order, validated against [validRange]. */
fun ByteArray.putInt16Le(
    offset: Int,
    value: Int,
    validRange: IntRange = Short.MIN_VALUE..Short.MAX_VALUE,
) {
    value.requireInRange(validRange)
    writeInt16LE(offset, value)
}

/** Writes an Int32 [value] at [offset] in big-endian order, validated against [validRange]. */
fun ByteArray.putInt32(
    offset: Int,
    value: Int,
    validRange: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
) {
    value.requireInRange(validRange)
    writeInt32BE(offset, value)
}

/** Writes an Int32 [value] at [offset] in little-endian order, validated against [validRange]. */
fun ByteArray.putInt32Le(
    offset: Int,
    value: Int,
    validRange: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
) {
    value.requireInRange(validRange)
    writeInt32LE(offset, value)
}

/** Writes an Int64 [value] at [offset] in big-endian order, validated against [validRange]. */
fun ByteArray.putInt64(
    offset: Int,
    value: Long,
    validRange: LongRange = Long.MIN_VALUE..Long.MAX_VALUE,
) {
    value.requireInRange(validRange)
    writeInt64BE(offset, value)
}

/** Writes an Int64 [value] at [offset] in little-endian order, validated against [validRange]. */
fun ByteArray.putInt64Le(
    offset: Int,
    value: Long,
    validRange: LongRange = Long.MIN_VALUE..Long.MAX_VALUE,
) {
    value.requireInRange(validRange)
    writeInt64LE(offset, value)
}

//endregion

//region Writers - Unsigned

/** Writes a UInt8 [value] at [offset], validated against [validRange]. */
fun ByteArray.putUInt8(
    offset: Int,
    value: UInt,
    validRange: UIntRange = UByte.MIN_VALUE.toUInt()..UByte.MAX_VALUE.toUInt(),
) {
    value.requireInRange(validRange)
    this[offset] = value.toByte()
}

/** Writes a UInt16 [value] at [offset] in big-endian order, validated against [validRange]. */
fun ByteArray.putUInt16(
    offset: Int,
    value: UInt,
    validRange: UIntRange = UShort.MIN_VALUE.toUInt()..UShort.MAX_VALUE.toUInt(),
) {
    value.requireInRange(validRange)
    writeInt16BE(offset, value.toInt())
}

/** Writes a UInt16 [value] at [offset] in little-endian order, validated against [validRange]. */
fun ByteArray.putUInt16Le(
    offset: Int,
    value: UInt,
    validRange: UIntRange = UShort.MIN_VALUE.toUInt()..UShort.MAX_VALUE.toUInt(),
) {
    value.requireInRange(validRange)
    writeInt16LE(offset, value.toInt())
}

/** Writes a UInt32 [value] at [offset] in big-endian order, validated against [validRange]. */
fun ByteArray.putUInt32(
    offset: Int,
    value: UInt,
    validRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE,
) {
    value.requireInRange(validRange)
    writeInt32BE(offset, value.toInt())
}

/** Writes a UInt32 [value] at [offset] in little-endian order, validated against [validRange]. */
fun ByteArray.putUInt32Le(
    offset: Int,
    value: UInt,
    validRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE,
) {
    value.requireInRange(validRange)
    writeInt32LE(offset, value.toInt())
}

/** Writes a UInt64 [value] at [offset] in big-endian order, validated against [validRange]. */
fun ByteArray.putUInt64(
    offset: Int,
    value: ULong,
    validRange: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE,
) {
    value.requireInRange(validRange)
    writeInt64BE(offset, value.toLong())
}

/** Writes a UInt64 [value] at [offset] in little-endian order, validated against [validRange]. */
fun ByteArray.putUInt64Le(
    offset: Int,
    value: ULong,
    validRange: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE,
) {
    value.requireInRange(validRange)
    writeInt64LE(offset, value.toLong())
}

//endregion

//region Readers - Signed

/** Reads a signed Int8 from [offset]. */
fun ByteArray.getInt8(offset: Int): Byte = this[offset]

/** Reads a signed Int16 from [offset] in big-endian order. */
fun ByteArray.getInt16(offset: Int): Short {
    val higher = this[offset].toInt() and BYTE_MASK
    val lower = this[offset + OFFSET_1].toInt() and BYTE_MASK
    return ((higher shl SHIFT_8) or lower).toShort()
}

/** Reads a signed Int16 from [offset] in little-endian order. */
fun ByteArray.getInt16Le(offset: Int): Short {
    val lower = this[offset].toInt() and BYTE_MASK
    val higher = this[offset + OFFSET_1].toInt() and BYTE_MASK
    return ((higher shl SHIFT_8) or lower).toShort()
}

/** Reads a signed Int32 from [offset] in big-endian order. */
fun ByteArray.getInt32(offset: Int): Int {
    val b1 = this[offset].toInt() and BYTE_MASK
    val b2 = this[offset + OFFSET_1].toInt() and BYTE_MASK
    val b3 = this[offset + OFFSET_2].toInt() and BYTE_MASK
    val b4 = this[offset + OFFSET_3].toInt() and BYTE_MASK
    return (b1 shl SHIFT_24) or (b2 shl SHIFT_16) or (b3 shl SHIFT_8) or b4
}

/** Reads a signed Int32 from [offset] in little-endian order. */
fun ByteArray.getInt32Le(offset: Int): Int {
    val b1 = this[offset].toInt() and BYTE_MASK
    val b2 = this[offset + OFFSET_1].toInt() and BYTE_MASK
    val b3 = this[offset + OFFSET_2].toInt() and BYTE_MASK
    val b4 = this[offset + OFFSET_3].toInt() and BYTE_MASK
    return (b4 shl SHIFT_24) or (b3 shl SHIFT_16) or (b2 shl SHIFT_8) or b1
}

/** Reads a signed Int64 from [offset] in big-endian order. */
fun ByteArray.getInt64(offset: Int): Long {
    val b1 = this[offset].toLong() and BYTE_MASK_LONG
    val b2 = this[offset + OFFSET_1].toLong() and BYTE_MASK_LONG
    val b3 = this[offset + OFFSET_2].toLong() and BYTE_MASK_LONG
    val b4 = this[offset + OFFSET_3].toLong() and BYTE_MASK_LONG
    val b5 = this[offset + OFFSET_4].toLong() and BYTE_MASK_LONG
    val b6 = this[offset + OFFSET_5].toLong() and BYTE_MASK_LONG
    val b7 = this[offset + OFFSET_6].toLong() and BYTE_MASK_LONG
    val b8 = this[offset + OFFSET_7].toLong() and BYTE_MASK_LONG
    return (b1 shl SHIFT_56) or (b2 shl SHIFT_48) or (b3 shl SHIFT_40) or (b4 shl SHIFT_32) or (b5 shl SHIFT_24) or (b6 shl SHIFT_16) or (b7 shl SHIFT_8) or b8
}

/** Reads a signed Int64 from [offset] in little-endian order. */
fun ByteArray.getInt64Le(offset: Int): Long {
    val b1 = this[offset].toLong() and BYTE_MASK_LONG
    val b2 = this[offset + OFFSET_1].toLong() and BYTE_MASK_LONG
    val b3 = this[offset + OFFSET_2].toLong() and BYTE_MASK_LONG
    val b4 = this[offset + OFFSET_3].toLong() and BYTE_MASK_LONG
    val b5 = this[offset + OFFSET_4].toLong() and BYTE_MASK_LONG
    val b6 = this[offset + OFFSET_5].toLong() and BYTE_MASK_LONG
    val b7 = this[offset + OFFSET_6].toLong() and BYTE_MASK_LONG
    val b8 = this[offset + OFFSET_7].toLong() and BYTE_MASK_LONG
    return (b8 shl SHIFT_56) or (b7 shl SHIFT_48) or (b6 shl SHIFT_40) or (b5 shl SHIFT_32) or (b4 shl SHIFT_24) or (b3 shl SHIFT_16) or (b2 shl SHIFT_8) or b1
}

//endregion

//region Readers - Unsigned (delegate to signed readers)

/** Reads an unsigned UInt8 from [offset]. */
fun ByteArray.getUInt8(offset: Int): UByte = getInt8(offset).toUByte()

/** Reads an unsigned UInt16 from [offset] in big-endian order. */
fun ByteArray.getUInt16(offset: Int): UShort = getInt16(offset).toUShort()

/** Reads an unsigned UInt16 from [offset] in little-endian order. */
fun ByteArray.getUInt16Le(offset: Int): UShort = getInt16Le(offset).toUShort()

/** Reads an unsigned UInt32 from [offset] in big-endian order. */
fun ByteArray.getUInt32(offset: Int): UInt = getInt32(offset).toUInt()

/** Reads an unsigned UInt32 from [offset] in little-endian order. */
fun ByteArray.getUInt32Le(offset: Int): UInt = getInt32Le(offset).toUInt()

/** Reads an unsigned UInt64 from [offset] in big-endian order. */
fun ByteArray.getUInt64(offset: Int): ULong = getInt64(offset).toULong()

/** Reads an unsigned UInt64 from [offset] in little-endian order. */
fun ByteArray.getUInt64Le(offset: Int): ULong = getInt64Le(offset).toULong()

//endregion

//region ByteString / String extraction

/**
 * Extracts a [ByteString] of [numBytes] starting at [offset].
 *
 * @throws IllegalArgumentException If [offset] or [numBytes] is negative, or if the range exceeds the array bounds.
 */
fun ByteArray.getByteString(
    offset: Int,
    numBytes: Int,
): ByteString {
    require(offset >= 0) { "Offset must be non-negative" }
    require(numBytes >= 0) { "Number of bytes must be non-negative" }
    require(offset + numBytes <= size) { "Offset and number of bytes must be within the bounds of the array" }
    return buildByteString { append(this@getByteString.copyOfRange(offset, offset + numBytes)) }
}

/**
 * Decodes [numBytes] starting at [offset] to a UTF-8 String. Invalid characters are replaced with U+FFFD.
 */
fun ByteArray.getString(
    offset: Int,
    numBytes: Int,
): String = decodeToString(offset, offset + numBytes, true)

//endregion

//region Private write helpers

private fun ByteArray.writeInt16BE(
    offset: Int,
    value: Int,
) {
    this[offset] = ((value shr SHIFT_8) and BYTE_MASK).toByte()
    this[offset + OFFSET_1] = (value and BYTE_MASK).toByte()
}

private fun ByteArray.writeInt16LE(
    offset: Int,
    value: Int,
) {
    this[offset] = (value and BYTE_MASK).toByte()
    this[offset + OFFSET_1] = ((value shr SHIFT_8) and BYTE_MASK).toByte()
}

private fun ByteArray.writeInt32BE(
    offset: Int,
    value: Int,
) {
    this[offset] = ((value shr SHIFT_24) and BYTE_MASK).toByte()
    this[offset + OFFSET_1] = ((value shr SHIFT_16) and BYTE_MASK).toByte()
    this[offset + OFFSET_2] = ((value shr SHIFT_8) and BYTE_MASK).toByte()
    this[offset + OFFSET_3] = (value and BYTE_MASK).toByte()
}

private fun ByteArray.writeInt32LE(
    offset: Int,
    value: Int,
) {
    this[offset] = (value and BYTE_MASK).toByte()
    this[offset + OFFSET_1] = ((value shr SHIFT_8) and BYTE_MASK).toByte()
    this[offset + OFFSET_2] = ((value shr SHIFT_16) and BYTE_MASK).toByte()
    this[offset + OFFSET_3] = ((value shr SHIFT_24) and BYTE_MASK).toByte()
}

private fun ByteArray.writeInt64BE(
    offset: Int,
    value: Long,
) {
    this[offset] = ((value shr SHIFT_56) and BYTE_MASK_LONG).toByte()
    this[offset + OFFSET_1] = ((value shr SHIFT_48) and BYTE_MASK_LONG).toByte()
    this[offset + OFFSET_2] = ((value shr SHIFT_40) and BYTE_MASK_LONG).toByte()
    this[offset + OFFSET_3] = ((value shr SHIFT_32) and BYTE_MASK_LONG).toByte()
    this[offset + OFFSET_4] = ((value shr SHIFT_24) and BYTE_MASK_LONG).toByte()
    this[offset + OFFSET_5] = ((value shr SHIFT_16) and BYTE_MASK_LONG).toByte()
    this[offset + OFFSET_6] = ((value shr SHIFT_8) and BYTE_MASK_LONG).toByte()
    this[offset + OFFSET_7] = (value and BYTE_MASK_LONG).toByte()
}

private fun ByteArray.writeInt64LE(
    offset: Int,
    value: Long,
) {
    this[offset] = (value and BYTE_MASK_LONG).toByte()
    this[offset + OFFSET_1] = ((value shr SHIFT_8) and BYTE_MASK_LONG).toByte()
    this[offset + OFFSET_2] = ((value shr SHIFT_16) and BYTE_MASK_LONG).toByte()
    this[offset + OFFSET_3] = ((value shr SHIFT_24) and BYTE_MASK_LONG).toByte()
    this[offset + OFFSET_4] = ((value shr SHIFT_32) and BYTE_MASK_LONG).toByte()
    this[offset + OFFSET_5] = ((value shr SHIFT_40) and BYTE_MASK_LONG).toByte()
    this[offset + OFFSET_6] = ((value shr SHIFT_48) and BYTE_MASK_LONG).toByte()
    this[offset + OFFSET_7] = ((value shr SHIFT_56) and BYTE_MASK_LONG).toByte()
}

//endregion
