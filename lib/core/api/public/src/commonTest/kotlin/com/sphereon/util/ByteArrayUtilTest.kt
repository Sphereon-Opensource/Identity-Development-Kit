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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ByteArrayUtilInt8Test {

    @Test
    fun putInt8WritesCorrectValue() {
        val bytes = ByteArray(4)
        bytes.putInt8(0, 42)
        assertEquals(42.toByte(), bytes[0])
    }

    @Test
    fun putInt8WritesNegativeValue() {
        val bytes = ByteArray(4)
        bytes.putInt8(1, -100)
        assertEquals((-100).toByte(), bytes[1])
    }

    @Test
    fun putInt8ThrowsForValueOutOfRange() {
        val bytes = ByteArray(4)
        assertFailsWith<IllegalArgumentException> {
            bytes.putInt8(0, 200)
        }
    }

    @Test
    fun getInt8ReadsCorrectValue() {
        val bytes = byteArrayOf(42, -100, 0, 127)
        assertEquals(42.toByte(), bytes.getInt8(0))
        assertEquals((-100).toByte(), bytes.getInt8(1))
        assertEquals(0.toByte(), bytes.getInt8(2))
        assertEquals(127.toByte(), bytes.getInt8(3))
    }
}

class ByteArrayUtilInt16Test {

    @Test
    fun putInt16WritesCorrectBigEndianValue() {
        val bytes = ByteArray(4)
        bytes.putInt16(0, 0x1234)
        assertEquals(0x12.toByte(), bytes[0])
        assertEquals(0x34.toByte(), bytes[1])
    }

    @Test
    fun putInt16LeWritesCorrectLittleEndianValue() {
        val bytes = ByteArray(4)
        bytes.putInt16Le(0, 0x1234)
        assertEquals(0x34.toByte(), bytes[0])
        assertEquals(0x12.toByte(), bytes[1])
    }

    @Test
    fun getInt16ReadsBigEndianCorrectly() {
        val bytes = byteArrayOf(0x12.toByte(), 0x34.toByte(), 0, 0)
        assertEquals(0x1234.toShort(), bytes.getInt16(0))
    }

    @Test
    fun getInt16LeReadsLittleEndianCorrectly() {
        val bytes = byteArrayOf(0x34.toByte(), 0x12.toByte(), 0, 0)
        assertEquals(0x1234.toShort(), bytes.getInt16Le(0))
    }

    @Test
    fun int16RoundTrips() {
        val bytes = ByteArray(4)
        val value: Short = 12345
        bytes.putInt16(0, value.toInt())
        assertEquals(value, bytes.getInt16(0))
    }

    @Test
    fun int16LeRoundTrips() {
        val bytes = ByteArray(4)
        val value: Short = -5000
        bytes.putInt16Le(0, value.toInt())
        assertEquals(value, bytes.getInt16Le(0))
    }
}

class ByteArrayUtilInt32Test {

    @Test
    fun putInt32WritesCorrectBigEndianValue() {
        val bytes = ByteArray(8)
        bytes.putInt32(0, 0x12345678)
        assertEquals(0x12.toByte(), bytes[0])
        assertEquals(0x34.toByte(), bytes[1])
        assertEquals(0x56.toByte(), bytes[2])
        assertEquals(0x78.toByte(), bytes[3])
    }

    @Test
    fun putInt32LeWritesCorrectLittleEndianValue() {
        val bytes = ByteArray(8)
        bytes.putInt32Le(0, 0x12345678)
        assertEquals(0x78.toByte(), bytes[0])
        assertEquals(0x56.toByte(), bytes[1])
        assertEquals(0x34.toByte(), bytes[2])
        assertEquals(0x12.toByte(), bytes[3])
    }

    @Test
    fun getInt32ReadsBigEndianCorrectly() {
        val bytes = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0, 0, 0, 0)
        assertEquals(0x12345678, bytes.getInt32(0))
    }

    @Test
    fun getInt32LeReadsLittleEndianCorrectly() {
        val bytes = byteArrayOf(0x78, 0x56, 0x34, 0x12, 0, 0, 0, 0)
        assertEquals(0x12345678, bytes.getInt32Le(0))
    }

    @Test
    fun int32RoundTrips() {
        val bytes = ByteArray(8)
        val value = 123456789
        bytes.putInt32(0, value)
        assertEquals(value, bytes.getInt32(0))
    }

    @Test
    fun int32LeRoundTrips() {
        val bytes = ByteArray(8)
        val value = -987654321
        bytes.putInt32Le(0, value)
        assertEquals(value, bytes.getInt32Le(0))
    }
}

class ByteArrayUtilInt64Test {

    @Test
    fun putInt64WritesCorrectBigEndianValue() {
        val bytes = ByteArray(16)
        bytes.putInt64(0, 0x123456789ABCDEF0L)
        assertEquals(0x12.toByte(), bytes[0])
        assertEquals(0x34.toByte(), bytes[1])
        assertEquals(0x56.toByte(), bytes[2])
        assertEquals(0x78.toByte(), bytes[3])
        assertEquals(0x9A.toByte(), bytes[4])
        assertEquals(0xBC.toByte(), bytes[5])
        assertEquals(0xDE.toByte(), bytes[6])
        assertEquals(0xF0.toByte(), bytes[7])
    }

    @Test
    fun putInt64LeWritesCorrectLittleEndianValue() {
        val bytes = ByteArray(16)
        bytes.putInt64Le(0, 0x123456789ABCDEF0L)
        assertEquals(0xF0.toByte(), bytes[0])
        assertEquals(0xDE.toByte(), bytes[1])
        assertEquals(0xBC.toByte(), bytes[2])
        assertEquals(0x9A.toByte(), bytes[3])
        assertEquals(0x78.toByte(), bytes[4])
        assertEquals(0x56.toByte(), bytes[5])
        assertEquals(0x34.toByte(), bytes[6])
        assertEquals(0x12.toByte(), bytes[7])
    }

    @Test
    fun getInt64ReadsBigEndianCorrectly() {
        val bytes = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9A.toByte(), 0xBC.toByte(), 0xDE.toByte(), 0xF0.toByte())
        assertEquals(0x123456789ABCDEF0L, bytes.getInt64(0))
    }

    @Test
    fun getInt64LeReadsLittleEndianCorrectly() {
        val bytes = byteArrayOf(0xF0.toByte(), 0xDE.toByte(), 0xBC.toByte(), 0x9A.toByte(), 0x78, 0x56, 0x34, 0x12)
        assertEquals(0x123456789ABCDEF0L, bytes.getInt64Le(0))
    }

    @Test
    fun int64RoundTrips() {
        val bytes = ByteArray(16)
        val value = 1234567890123456789L
        bytes.putInt64(0, value)
        assertEquals(value, bytes.getInt64(0))
    }

    @Test
    fun int64LeRoundTrips() {
        val bytes = ByteArray(16)
        val value = -1234567890123456789L
        bytes.putInt64Le(0, value)
        assertEquals(value, bytes.getInt64Le(0))
    }
}

class ByteArrayUtilUnsignedTest {

    @Test
    fun putUInt8WritesCorrectValue() {
        val bytes = ByteArray(4)
        bytes.putUInt8(0, 200u)
        assertEquals(200.toUByte(), bytes.getUInt8(0))
    }

    @Test
    fun putUInt16WritesCorrectValue() {
        val bytes = ByteArray(4)
        bytes.putUInt16(0, 50000u)
        assertEquals(50000.toUShort(), bytes.getUInt16(0))
    }

    @Test
    fun putUInt16LeWritesCorrectValue() {
        val bytes = ByteArray(4)
        bytes.putUInt16Le(0, 50000u)
        assertEquals(50000.toUShort(), bytes.getUInt16Le(0))
    }

    @Test
    fun putUInt32WritesCorrectValue() {
        val bytes = ByteArray(8)
        bytes.putUInt32(0, 3000000000u)
        assertEquals(3000000000u, bytes.getUInt32(0))
    }

    @Test
    fun putUInt32LeWritesCorrectValue() {
        val bytes = ByteArray(8)
        bytes.putUInt32Le(0, 3000000000u)
        assertEquals(3000000000u, bytes.getUInt32Le(0))
    }

    @Test
    fun putUInt64WritesCorrectValue() {
        val bytes = ByteArray(16)
        bytes.putUInt64(0, 10000000000000000000UL)
        assertEquals(10000000000000000000UL, bytes.getUInt64(0))
    }

    @Test
    fun putUInt64LeWritesCorrectValue() {
        val bytes = ByteArray(16)
        bytes.putUInt64Le(0, 10000000000000000000UL)
        assertEquals(10000000000000000000UL, bytes.getUInt64Le(0))
    }
}

class ByteArrayUtilByteStringTest {

    @Test
    fun getByteStringExtractsCorrectBytes() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6)
        val byteString = bytes.getByteString(1, 3)
        assertEquals(3, byteString.size)
        assertEquals(2.toByte(), byteString[0])
        assertEquals(3.toByte(), byteString[1])
        assertEquals(4.toByte(), byteString[2])
    }

    @Test
    fun getByteStringWithZeroLengthReturnsEmpty() {
        val bytes = byteArrayOf(1, 2, 3)
        val byteString = bytes.getByteString(0, 0)
        assertEquals(0, byteString.size)
    }

    @Test
    fun getByteStringThrowsForNegativeOffset() {
        val bytes = byteArrayOf(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            bytes.getByteString(-1, 2)
        }
    }

    @Test
    fun getByteStringThrowsForNegativeNumBytes() {
        val bytes = byteArrayOf(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            bytes.getByteString(0, -1)
        }
    }

    @Test
    fun getByteStringThrowsForOutOfBounds() {
        val bytes = byteArrayOf(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            bytes.getByteString(2, 3)
        }
    }
}

class ByteArrayUtilStringTest {

    @Test
    fun getStringDecodesUtf8() {
        val bytes = "Hello, World!".encodeToByteArray()
        assertEquals("Hello, World!", bytes.getString(0, bytes.size))
    }

    @Test
    fun getStringDecodesSubstring() {
        val bytes = "Hello, World!".encodeToByteArray()
        assertEquals("Hello", bytes.getString(0, 5))
    }

    @Test
    fun getStringDecodesMiddlePortion() {
        val bytes = "Hello, World!".encodeToByteArray()
        assertEquals("World", bytes.getString(7, 5))
    }
}

class ByteArrayUtilOffsetTest {

    @Test
    fun putAndGetAtDifferentOffsets() {
        val bytes = ByteArray(20)
        bytes.putInt32(0, 0x11111111)
        bytes.putInt32(4, 0x22222222)
        bytes.putInt32(8, 0x33333333)

        assertEquals(0x11111111, bytes.getInt32(0))
        assertEquals(0x22222222, bytes.getInt32(4))
        assertEquals(0x33333333, bytes.getInt32(8))
    }

    @Test
    fun mixedEndiannessAtDifferentOffsets() {
        val bytes = ByteArray(8)
        bytes.putInt16(0, 0x1234)
        bytes.putInt16Le(2, 0x5678)
        bytes.putInt16(4, 0x1ABC)
        bytes.putInt16Le(6, 0x2EF0)

        assertEquals(0x1234.toShort(), bytes.getInt16(0))
        assertEquals(0x5678.toShort(), bytes.getInt16Le(2))
        assertEquals(0x1ABC.toShort(), bytes.getInt16(4))
        assertEquals(0x2EF0.toShort(), bytes.getInt16Le(6))
    }
}
