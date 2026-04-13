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

package com.sphereon.util

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.append
import kotlinx.io.bytestring.buildByteString

/**
 * Extension functions for [ByteStringBuilder] (append/write) and [ByteString] (read).
 *
 * All multi-byte functions default to big-endian byte order. Little-endian variants are suffixed with `Le`.
 * Each `append*` function validates the value against a configurable range before writing and returns the builder
 * for chaining.
 */

/** Concatenates this [ByteString] with [bStr], returning a new [ByteString]. */
fun ByteString.concat(bStr: ByteString) =
    buildByteString {
        append(this@concat)
        append(bStr)
    }

//region Writers - Signed

/** Appends an Int8 [value], validated against [validRange]. */
fun ByteStringBuilder.appendInt8(value: Int, validRange: IntRange = Byte.MIN_VALUE..Byte.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    append(value.toByte())
    return this
}

/** Appends an Int8 [value] (Byte overload), validated against [validRange]. */
fun ByteStringBuilder.appendInt8(value: Byte, validRange: IntRange = Byte.MIN_VALUE..Byte.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    append(value)
    return this
}

/** Appends an Int16 [value] in big-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendInt16(value: Int, validRange: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendInt16BEBytes(value)
    return this
}

/** Appends an Int16 [value] in little-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendInt16Le(value: Int, validRange: IntRange = Short.MIN_VALUE..Short.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendInt16LEBytes(value)
    return this
}

/** Appends an Int32 [value] in big-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendInt32(value: Int, validRange: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendInt32BEBytes(value)
    return this
}

/** Appends an Int32 [value] in little-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendInt32Le(value: Int, validRange: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendInt32LEBytes(value)
    return this
}

/** Appends an Int64 [value] in big-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendInt64(value: Long, validRange: LongRange = Long.MIN_VALUE..Long.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendInt64BEBytes(value)
    return this
}

/** Appends an Int64 [value] in little-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendInt64Le(value: Long, validRange: LongRange = Long.MIN_VALUE..Long.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendInt64LEBytes(value)
    return this
}

//endregion

//region Writers - Unsigned

/** Appends a UInt8 [value] (UByte overload), validated against [validRange]. */
fun ByteStringBuilder.appendUInt8(value: UByte, validRange: UIntRange = UByte.MIN_VALUE..UByte.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    append(value.toByte())
    return this
}

/** Appends a UInt8 [value] (UInt overload), validated against [validRange]. */
fun ByteStringBuilder.appendUInt8(value: UInt, validRange: UIntRange = UByte.MIN_VALUE..UByte.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    append(value.toByte())
    return this
}

/** Appends a UInt8 [value] (Int convenience overload), validated against [validRange]. */
fun ByteStringBuilder.appendUInt8(value: Int, validRange: UIntRange = UByte.MIN_VALUE..UByte.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendUInt8(value.toUInt(), validRange)
    return this
}

/** Appends a UInt16 [value] in big-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendUInt16(value: UInt, validRange: UIntRange = UShort.MIN_VALUE..UShort.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendInt16BEBytes(value.toInt())
    return this
}

/** Appends a UInt16 [value] (Int convenience overload) in big-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendUInt16(value: Int, validRange: UIntRange = UShort.MIN_VALUE..UShort.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendUInt16(value.toUInt(), validRange)
    return this
}

/** Appends a UInt16 [value] in little-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendUInt16Le(value: UInt, validRange: UIntRange = UShort.MIN_VALUE..UShort.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendInt16LEBytes(value.toInt())
    return this
}

/** Appends a UInt16 [value] (Int convenience overload) in little-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendUInt16Le(value: Int, validRange: UIntRange = UShort.MIN_VALUE..UShort.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendUInt16Le(value.toUInt(), validRange)
    return this
}

/** Appends a UInt32 [value] in big-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendUInt32(value: UInt, validRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendInt32BEBytes(value.toInt())
    return this
}

/** Appends a UInt32 [value] (Int convenience overload) in big-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendUInt32(value: Int, validRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendUInt32(value.toUInt(), validRange)
    return this
}

/** Appends a UInt32 [value] in little-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendUInt32Le(value: UInt, validRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendInt32LEBytes(value.toInt())
    return this
}

/** Appends a UInt32 [value] (Int convenience overload) in little-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendUInt32Le(value: Int, validRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendUInt32Le(value.toUInt(), validRange)
    return this
}

/** Appends a UInt64 [value] in big-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendUInt64(value: ULong, validRange: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendInt64BEBytes(value.toLong())
    return this
}

/** Appends a UInt64 [value] (Long convenience overload) in big-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendUInt64(value: Long, validRange: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendUInt64(value.toULong(), validRange)
    return this
}

/** Appends a UInt64 [value] in little-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendUInt64Le(value: ULong, validRange: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendInt64LEBytes(value.toLong())
    return this
}

/** Appends a UInt64 [value] (Long convenience overload) in little-endian order, validated against [validRange]. */
fun ByteStringBuilder.appendUInt64Le(value: Long, validRange: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    appendUInt64Le(value.toULong(), validRange)
    return this
}

/** Appends a [string] encoded as UTF-8 bytes. */
fun ByteStringBuilder.appendString(string: String): ByteStringBuilder {
    append(string.encodeToByteArray())
    return this
}

/** Appends a [bArray]. If empty, nothing is appended. */
fun ByteStringBuilder.appendByteArray(bArray: ByteArray): ByteStringBuilder {
    if (bArray.isNotEmpty()) append(bArray)
    return this
}

/** Appends a [bString]. */
fun ByteStringBuilder.appendByteString(bString: ByteString): ByteStringBuilder {
    append(bString)
    return this
}

//endregion

//region Readers - Signed

/** Reads a signed Int8 from [offset]. */
fun ByteString.getInt8(offset: Int): Byte {
    require(size >= Byte.SIZE_BYTES) { errSize(size, "Int8") }
    require(offset in 0..<size) { errOffset(offset, size, "Int8") }
    return get(offset)
}

/** Reads a signed Int16 from [offset] in big-endian order. */
fun ByteString.getInt16(offset: Int): Short {
    require(size >= Short.SIZE_BYTES) { errSize(size, "Int16") }
    require(offset in 0..(size - Short.SIZE_BYTES)) { errOffset(offset, size, "Int16") }
    val higher = (get(offset).toInt() and 0xFF)
    val lower = (get(offset + 1).toInt() and 0xFF)
    return ((higher shl 8) or lower).toShort()
}

/** Reads a signed Int16 from [offset] in little-endian order. */
fun ByteString.getInt16Le(offset: Int): Short {
    require(size >= Short.SIZE_BYTES) { errSize(size, "Int16") }
    require(offset in 0..(size - Short.SIZE_BYTES)) { errOffset(offset, size, "Int16") }
    val lower = (get(offset).toInt() and 0xFF)
    val higher = (get(offset + 1).toInt() and 0xFF)
    return ((higher shl 8) or lower).toShort()
}

/** Reads a signed Int32 from [offset] in big-endian order. */
fun ByteString.getInt32(offset: Int): Int {
    require(size >= Int.SIZE_BYTES) { errSize(size, "Int32") }
    require(offset in 0..(size - Int.SIZE_BYTES)) { errOffset(offset, size, "Int32") }
    val b1 = (get(offset).toInt() and 0xFF)
    val b2 = (get(offset + 1).toInt() and 0xFF)
    val b3 = (get(offset + 2).toInt() and 0xFF)
    val b4 = (get(offset + 3).toInt() and 0xFF)
    return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
}

/** Reads a signed Int32 from [offset] in little-endian order. */
fun ByteString.getInt32Le(offset: Int): Int {
    require(size >= Int.SIZE_BYTES) { errSize(size, "Int32") }
    require(offset in 0..(size - Int.SIZE_BYTES)) { errOffset(offset, size, "Int32") }
    val b1 = (get(offset).toInt() and 0xFF)
    val b2 = (get(offset + 1).toInt() and 0xFF)
    val b3 = (get(offset + 2).toInt() and 0xFF)
    val b4 = (get(offset + 3).toInt() and 0xFF)
    return (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
}

/** Reads a signed Int64 from [offset] in big-endian order. */
fun ByteString.getInt64(offset: Int): Long {
    require(size >= Long.SIZE_BYTES) { errSize(size, "Int64") }
    require(offset in 0..(size - Long.SIZE_BYTES)) { errOffset(offset, size, "Int64") }
    val b1 = (get(offset).toLong() and 0xFF)
    val b2 = (get(offset + 1).toLong() and 0xFF)
    val b3 = (get(offset + 2).toLong() and 0xFF)
    val b4 = (get(offset + 3).toLong() and 0xFF)
    val b5 = (get(offset + 4).toLong() and 0xFF)
    val b6 = (get(offset + 5).toLong() and 0xFF)
    val b7 = (get(offset + 6).toLong() and 0xFF)
    val b8 = (get(offset + 7).toLong() and 0xFF)
    return (b1 shl 56) or (b2 shl 48) or (b3 shl 40) or (b4 shl 32) or (b5 shl 24) or (b6 shl 16) or (b7 shl 8) or b8
}

/** Reads a signed Int64 from [offset] in little-endian order. */
fun ByteString.getInt64Le(offset: Int): Long {
    require(size >= Long.SIZE_BYTES) { errSize(size, "Int64") }
    require(offset in 0..(size - Long.SIZE_BYTES)) { errOffset(offset, size, "Int64") }
    val b1 = (get(offset).toLong() and 0xFF)
    val b2 = (get(offset + 1).toLong() and 0xFF)
    val b3 = (get(offset + 2).toLong() and 0xFF)
    val b4 = (get(offset + 3).toLong() and 0xFF)
    val b5 = (get(offset + 4).toLong() and 0xFF)
    val b6 = (get(offset + 5).toLong() and 0xFF)
    val b7 = (get(offset + 6).toLong() and 0xFF)
    val b8 = (get(offset + 7).toLong() and 0xFF)
    return (b8 shl 56) or (b7 shl 48) or (b6 shl 40) or (b5 shl 32) or (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
}

//endregion

//region Readers - Unsigned (delegate to signed readers)

/** Reads an unsigned UInt8 from [offset]. */
fun ByteString.getUInt8(offset: Int): UByte = getInt8(offset).toUByte()

/** Reads an unsigned UInt16 from [offset] in big-endian order. */
fun ByteString.getUInt16(offset: Int): UShort = getInt16(offset).toUShort()

/** Reads an unsigned UInt16 from [offset] in little-endian order. */
fun ByteString.getUInt16Le(offset: Int): UShort = getInt16Le(offset).toUShort()

/** Reads an unsigned UInt32 from [offset] in big-endian order. */
fun ByteString.getUInt32(offset: Int): UInt = getInt32(offset).toUInt()

/** Reads an unsigned UInt32 from [offset] in little-endian order. */
fun ByteString.getUInt32Le(offset: Int): UInt = getInt32Le(offset).toUInt()

/** Reads an unsigned UInt64 from [offset] in big-endian order. */
fun ByteString.getUInt64(offset: Int): ULong = getInt64(offset).toULong()

/** Reads an unsigned UInt64 from [offset] in little-endian order. */
fun ByteString.getUInt64Le(offset: Int): ULong = getInt64Le(offset).toULong()

//endregion

//region Validation (internal, shared with ByteArrayUtil)

private fun Int.requireInRange() {
    require(this >= 0) { errPositive(this, "Int") }
}

private fun Long.requireInRange() {
    require(this >= 0) { errPositive(this, "Long") }
}

private fun Byte.requireInRange(validRange: IntRange) {
    require(this in validRange) { "Int ${errRange(this, validRange.first, validRange.last)}" }
}

private fun UByte.requireInRange(validRange: UIntRange) {
    require(this in validRange) { "UInt ${errRange(this, validRange.first, validRange.last)}" }
}

internal fun Int.requireInRange(validRange: IntRange) {
    require(this in validRange) { "Int ${errRange(this, validRange.first, validRange.last)}" }
}

internal fun Int.requireInRange(validRange: UIntRange) {
    require(this.toUInt() in validRange) { "Int ${errRange(this, validRange.first, validRange.last)}" }
}

internal fun UInt.requireInRange(validRange: UIntRange) {
    require(this in validRange) { "UInt ${errRange(this, validRange.first, validRange.last)}" }
}

internal fun Long.requireInRange(validRange: LongRange) {
    require(this in validRange) { "Long ${errRange(this, validRange.first, validRange.last)}" }
}

internal fun Long.requireInRange(validRange: ULongRange) {
    require(this.toULong() in validRange) { "Long ${errRange(this, validRange.first, validRange.last)}" }
}

internal fun ULong.requireInRange(validRange: ULongRange) {
    require(this in validRange) { "ULong ${errRange(this, validRange.first, validRange.last)}" }
}

private fun errRange(v: Any, min: Any, max: Any) = " value $v is out of valid range: $min..$max."

private fun errPositive(v: Any, type: String) = "Value $v is negative and cannot be converted to U$type"

private fun errOffset(offset: Int, size: Int, type: String) =
    "Offset $offset is out of bounds for reading $type from ByteString of size $size."

private fun errSize(size: Int, s: String) = "ByteString size $size is less than $s byte size"

//endregion

//region Private write helpers (ByteStringBuilder)

private fun ByteStringBuilder.appendInt16BEBytes(value: Int) {
    append((value shr 8).toByte())
    append(value.toByte())
}

private fun ByteStringBuilder.appendInt16LEBytes(value: Int) {
    append(value.toByte())
    append((value shr 8).toByte())
}

private fun ByteStringBuilder.appendInt32BEBytes(value: Int) {
    append((value shr 24).toByte())
    append((value shr 16).toByte())
    append((value shr 8).toByte())
    append(value.toByte())
}

private fun ByteStringBuilder.appendInt32LEBytes(value: Int) {
    append(value.toByte())
    append((value shr 8).toByte())
    append((value shr 16).toByte())
    append((value shr 24).toByte())
}

private fun ByteStringBuilder.appendInt64BEBytes(value: Long) {
    append((value shr 56).toByte())
    append((value shr 48).toByte())
    append((value shr 40).toByte())
    append((value shr 32).toByte())
    append((value shr 24).toByte())
    append((value shr 16).toByte())
    append((value shr 8).toByte())
    append(value.toByte())
}

private fun ByteStringBuilder.appendInt64LEBytes(value: Long) {
    append(value.toByte())
    append((value shr 8).toByte())
    append((value shr 16).toByte())
    append((value shr 24).toByte())
    append((value shr 32).toByte())
    append((value shr 40).toByte())
    append((value shr 48).toByte())
    append((value shr 56).toByte())
}

//endregion