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

/**
 * Bit ordering within the packed status byte array. The two specs differ:
 * - [LSB_FIRST] — IETF Token Status List: status index 0 maps to the least-significant bit of
 *   byte 0; a multi-bit value's least-significant bit sits at the lowest bit position.
 * - [MSB_FIRST] — W3C Bitstring Status List: status index 0 maps to the most-significant bit of
 *   byte 0; a multi-bit value is laid out big-endian.
 */
enum class BitOrder {
    LSB_FIRST,
    MSB_FIRST,
}

/**
 * A packed array of fixed-width status values. Each status occupies [bitsPerStatus] consecutive
 * bits starting at bit position `index * bitsPerStatus`, ordered per [bitOrder].
 */
class StatusBitset private constructor(
    val length: Int,
    val bitsPerStatus: Int,
    val bitOrder: BitOrder,
    private val bytes: ByteArray,
) {
    fun get(index: Int): Int {
        requireIndex(index)
        val start = index * bitsPerStatus
        var value = 0
        for (j in 0 until bitsPerStatus) {
            val bit = readBit(start + j)
            value =
                when (bitOrder) {
                    BitOrder.LSB_FIRST -> value or (bit shl j)
                    BitOrder.MSB_FIRST -> value or (bit shl (bitsPerStatus - 1 - j))
                }
        }
        return value
    }

    fun set(
        index: Int,
        value: Int,
    ) {
        requireIndex(index)
        require(value in 0 until (1 shl bitsPerStatus)) {
            "status value $value does not fit in $bitsPerStatus bit(s)"
        }
        val start = index * bitsPerStatus
        for (j in 0 until bitsPerStatus) {
            val bit =
                when (bitOrder) {
                    BitOrder.LSB_FIRST -> (value shr j) and 1
                    BitOrder.MSB_FIRST -> (value shr (bitsPerStatus - 1 - j)) and 1
                }
            writeBit(start + j, bit)
        }
    }

    fun toByteArray(): ByteArray = bytes.copyOf()

    private fun readBit(position: Int): Int {
        val mask = bitMask(position % 8)
        return if (bytes[position / 8].toInt() and mask != 0) 1 else 0
    }

    private fun writeBit(
        position: Int,
        bit: Int,
    ) {
        val byteIdx = position / 8
        val mask = bitMask(position % 8)
        val current = bytes[byteIdx].toInt()
        bytes[byteIdx] = (if (bit != 0) current or mask else current and mask.inv()).toByte()
    }

    private fun bitMask(positionInByte: Int): Int =
        when (bitOrder) {
            BitOrder.LSB_FIRST -> 1 shl positionInByte
            BitOrder.MSB_FIRST -> 1 shl (7 - positionInByte)
        }

    private fun requireIndex(index: Int) {
        require(index in 0 until length) { "status index $index out of range [0, $length)" }
    }

    companion object {
        private fun byteCount(
            length: Int,
            bitsPerStatus: Int,
        ): Int = (length.toLong() * bitsPerStatus + 7).toInt() / 8

        fun create(
            length: Int,
            bitsPerStatus: Int,
            bitOrder: BitOrder,
        ): StatusBitset {
            require(length > 0) { "length must be > 0" }
            require(bitsPerStatus in intArrayOf(1, 2, 4, 8)) { "bitsPerStatus must be 1, 2, 4, or 8" }
            return StatusBitset(length, bitsPerStatus, bitOrder, ByteArray(byteCount(length, bitsPerStatus)))
        }

        fun fromBytes(
            bytes: ByteArray,
            length: Int,
            bitsPerStatus: Int,
            bitOrder: BitOrder,
        ): StatusBitset {
            require(bytes.size >= byteCount(length, bitsPerStatus)) {
                "byte array too small for $length entries of $bitsPerStatus bit(s)"
            }
            return StatusBitset(length, bitsPerStatus, bitOrder, bytes.copyOf())
        }
    }
}
