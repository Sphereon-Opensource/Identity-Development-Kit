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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ByteDataReaderBasicTest {
    @Test
    fun readerCreatedFromByteArrayWorks() {
        val data = byteArrayOf(1, 2, 3, 4)
        val reader = ByteDataReader(data)
        assertEquals(4, reader.numBytesRemaining())
    }

    @Test
    fun readerCreatedFromByteStringWorks() {
        val data = ByteString(1, 2, 3, 4)
        val reader = ByteDataReader(data)
        assertEquals(4, reader.numBytesRemaining())
    }

    @Test
    fun exhaustedReturnsFalseWhenDataRemains() {
        val reader = ByteDataReader(byteArrayOf(1, 2, 3))
        assertFalse(reader.exhausted())
    }

    @Test
    fun exhaustedReturnsTrueWhenAllDataRead() {
        val reader = ByteDataReader(byteArrayOf(1, 2))
        reader.getInt8()
        reader.getInt8()
        assertTrue(reader.exhausted())
    }

    @Test
    fun exhaustedReturnsTrueForEmptyArray() {
        val reader = ByteDataReader(byteArrayOf())
        assertTrue(reader.exhausted())
    }

    @Test
    fun numBytesRemainingDecreasesAsDataIsRead() {
        val reader = ByteDataReader(byteArrayOf(1, 2, 3, 4, 5))
        assertEquals(5, reader.numBytesRemaining())
        reader.getInt8()
        assertEquals(4, reader.numBytesRemaining())
        reader.getInt16()
        assertEquals(2, reader.numBytesRemaining())
    }
}

class ByteDataReaderSkipTest {
    @Test
    fun skipMovesCursorForward() {
        val reader = ByteDataReader(byteArrayOf(1, 2, 3, 4, 5))
        reader.skip(2)
        assertEquals(3, reader.numBytesRemaining())
        assertEquals(3.toByte(), reader.getInt8())
    }

    @Test
    fun skipReturnsReaderForChaining() {
        val reader = ByteDataReader(byteArrayOf(1, 2, 3, 4, 5))
        val result = reader.skip(2)
        assertEquals(reader, result)
    }

    @Test
    fun skipThrowsForNegativeValue() {
        val reader = ByteDataReader(byteArrayOf(1, 2, 3))
        assertFailsWith<IllegalArgumentException> {
            reader.skip(-1)
        }
    }

    @Test
    fun skipThrowsWhenSkippingPastEnd() {
        val reader = ByteDataReader(byteArrayOf(1, 2, 3))
        assertFailsWith<IllegalArgumentException> {
            reader.skip(10)
        }
    }

    @Test
    fun skipZeroBytesDoesNothing() {
        val reader = ByteDataReader(byteArrayOf(1, 2, 3))
        reader.skip(0)
        assertEquals(3, reader.numBytesRemaining())
    }
}

class ByteDataReaderPeekTest {
    @Test
    fun peekInt8DoesNotAdvanceCursor() {
        val reader = ByteDataReader(byteArrayOf(42, 43, 44))
        assertEquals(42.toByte(), reader.peekInt8())
        assertEquals(42.toByte(), reader.peekInt8())
        assertEquals(3, reader.numBytesRemaining())
    }

    @Test
    fun peekInt16DoesNotAdvanceCursor() {
        val data = ByteArray(4)
        data.putInt16(0, 0x1234)
        val reader = ByteDataReader(data)
        assertEquals(0x1234.toShort(), reader.peekInt16())
        assertEquals(4, reader.numBytesRemaining())
    }

    @Test
    fun peekInt16LeDoesNotAdvanceCursor() {
        val data = ByteArray(4)
        data.putInt16Le(0, 0x1234)
        val reader = ByteDataReader(data)
        assertEquals(0x1234.toShort(), reader.peekInt16Le())
        assertEquals(4, reader.numBytesRemaining())
    }

    @Test
    fun peekInt32DoesNotAdvanceCursor() {
        val data = ByteArray(8)
        data.putInt32(0, 0x12345678)
        val reader = ByteDataReader(data)
        assertEquals(0x12345678, reader.peekInt32())
        assertEquals(8, reader.numBytesRemaining())
    }

    @Test
    fun peekInt32LeDoesNotAdvanceCursor() {
        val data = ByteArray(8)
        data.putInt32Le(0, 0x12345678)
        val reader = ByteDataReader(data)
        assertEquals(0x12345678, reader.peekInt32Le())
        assertEquals(8, reader.numBytesRemaining())
    }

    @Test
    fun peekInt64DoesNotAdvanceCursor() {
        val data = ByteArray(16)
        data.putInt64(0, 0x123456789ABCDEF0L)
        val reader = ByteDataReader(data)
        assertEquals(0x123456789ABCDEF0L, reader.peekInt64())
        assertEquals(16, reader.numBytesRemaining())
    }

    @Test
    fun peekInt64LeDoesNotAdvanceCursor() {
        val data = ByteArray(16)
        data.putInt64Le(0, 0x123456789ABCDEF0L)
        val reader = ByteDataReader(data)
        assertEquals(0x123456789ABCDEF0L, reader.peekInt64Le())
        assertEquals(16, reader.numBytesRemaining())
    }

    @Test
    fun peekUInt8DoesNotAdvanceCursor() {
        val data = byteArrayOf(0xFF.toByte())
        val reader = ByteDataReader(data)
        assertEquals(255.toUByte(), reader.peekUInt8())
        assertEquals(1, reader.numBytesRemaining())
    }

    @Test
    fun peekUInt16DoesNotAdvanceCursor() {
        val data = ByteArray(4)
        data.putUInt16(0, 50000u)
        val reader = ByteDataReader(data)
        assertEquals(50000.toUShort(), reader.peekUInt16())
        assertEquals(4, reader.numBytesRemaining())
    }

    @Test
    fun peekUInt16LeDoesNotAdvanceCursor() {
        val data = ByteArray(4)
        data.putUInt16Le(0, 50000u)
        val reader = ByteDataReader(data)
        assertEquals(50000.toUShort(), reader.peekUInt16Le())
        assertEquals(4, reader.numBytesRemaining())
    }

    @Test
    fun peekUInt32DoesNotAdvanceCursor() {
        val data = ByteArray(8)
        data.putUInt32(0, 3000000000u)
        val reader = ByteDataReader(data)
        assertEquals(3000000000u, reader.peekUInt32())
        assertEquals(8, reader.numBytesRemaining())
    }

    @Test
    fun peekUInt32LeDoesNotAdvanceCursor() {
        val data = ByteArray(8)
        data.putUInt32Le(0, 3000000000u)
        val reader = ByteDataReader(data)
        assertEquals(3000000000u, reader.peekUInt32Le())
        assertEquals(8, reader.numBytesRemaining())
    }

    @Test
    fun peekUInt64DoesNotAdvanceCursor() {
        val data = ByteArray(16)
        data.putUInt64(0, 10000000000000000000UL)
        val reader = ByteDataReader(data)
        assertEquals(10000000000000000000UL, reader.peekUInt64())
        assertEquals(16, reader.numBytesRemaining())
    }

    @Test
    fun peekUInt64LeDoesNotAdvanceCursor() {
        val data = ByteArray(16)
        data.putUInt64Le(0, 10000000000000000000UL)
        val reader = ByteDataReader(data)
        assertEquals(10000000000000000000UL, reader.peekUInt64Le())
        assertEquals(16, reader.numBytesRemaining())
    }

    @Test
    fun peekByteStringDoesNotAdvanceCursor() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val reader = ByteDataReader(data)
        val byteString = reader.peekByteString(3)
        assertEquals(3, byteString.size)
        assertEquals(5, reader.numBytesRemaining())
    }

    @Test
    fun peekStringDoesNotAdvanceCursor() {
        val data = "Hello".encodeToByteArray()
        val reader = ByteDataReader(data)
        assertEquals("Hello", reader.peekString(5))
        assertEquals(5, reader.numBytesRemaining())
    }
}

class ByteDataReaderGetTest {
    @Test
    fun getInt8AdvancesCursor() {
        val reader = ByteDataReader(byteArrayOf(42, 43, 44))
        assertEquals(42.toByte(), reader.getInt8())
        assertEquals(43.toByte(), reader.getInt8())
        assertEquals(1, reader.numBytesRemaining())
    }

    @Test
    fun getInt16AdvancesCursorByTwo() {
        val data = ByteArray(6)
        data.putInt16(0, 0x1234)
        data.putInt16(2, 0x5678)
        val reader = ByteDataReader(data)
        assertEquals(0x1234.toShort(), reader.getInt16())
        assertEquals(4, reader.numBytesRemaining())
        assertEquals(0x5678.toShort(), reader.getInt16())
    }

    @Test
    fun getInt16LeAdvancesCursorByTwo() {
        val data = ByteArray(6)
        data.putInt16Le(0, 0x1234)
        data.putInt16Le(2, 0x5678)
        val reader = ByteDataReader(data)
        assertEquals(0x1234.toShort(), reader.getInt16Le())
        assertEquals(4, reader.numBytesRemaining())
    }

    @Test
    fun getInt32AdvancesCursorByFour() {
        val data = ByteArray(12)
        data.putInt32(0, 0x12345678)
        data.putInt32(4, 0x9ABCDEF0.toInt())
        val reader = ByteDataReader(data)
        assertEquals(0x12345678, reader.getInt32())
        assertEquals(8, reader.numBytesRemaining())
    }

    @Test
    fun getInt32LeAdvancesCursorByFour() {
        val data = ByteArray(12)
        data.putInt32Le(0, 0x12345678)
        val reader = ByteDataReader(data)
        assertEquals(0x12345678, reader.getInt32Le())
        assertEquals(8, reader.numBytesRemaining())
    }

    @Test
    fun getInt64AdvancesCursorByEight() {
        val data = ByteArray(16)
        data.putInt64(0, 0x123456789ABCDEF0L)
        val reader = ByteDataReader(data)
        assertEquals(0x123456789ABCDEF0L, reader.getInt64())
        assertEquals(8, reader.numBytesRemaining())
    }

    @Test
    fun getInt64LeAdvancesCursorByEight() {
        val data = ByteArray(16)
        data.putInt64Le(0, 0x123456789ABCDEF0L)
        val reader = ByteDataReader(data)
        assertEquals(0x123456789ABCDEF0L, reader.getInt64Le())
        assertEquals(8, reader.numBytesRemaining())
    }

    @Test
    fun getUInt8AdvancesCursor() {
        val data = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val reader = ByteDataReader(data)
        assertEquals(255.toUByte(), reader.getUInt8())
        assertEquals(1, reader.numBytesRemaining())
    }

    @Test
    fun getUInt16AdvancesCursorByTwo() {
        val data = ByteArray(6)
        data.putUInt16(0, 50000u)
        val reader = ByteDataReader(data)
        assertEquals(50000.toUShort(), reader.getUInt16())
        assertEquals(4, reader.numBytesRemaining())
    }

    @Test
    fun getUInt16LeAdvancesCursorByTwo() {
        val data = ByteArray(6)
        data.putUInt16Le(0, 50000u)
        val reader = ByteDataReader(data)
        assertEquals(50000.toUShort(), reader.getUInt16Le())
        assertEquals(4, reader.numBytesRemaining())
    }

    @Test
    fun getUInt32AdvancesCursorByFour() {
        val data = ByteArray(12)
        data.putUInt32(0, 3000000000u)
        val reader = ByteDataReader(data)
        assertEquals(3000000000u, reader.getUInt32())
        assertEquals(8, reader.numBytesRemaining())
    }

    @Test
    fun getUInt32LeAdvancesCursorByFour() {
        val data = ByteArray(12)
        data.putUInt32Le(0, 3000000000u)
        val reader = ByteDataReader(data)
        assertEquals(3000000000u, reader.getUInt32Le())
        assertEquals(8, reader.numBytesRemaining())
    }

    @Test
    fun getUInt64AdvancesCursorByEight() {
        val data = ByteArray(16)
        data.putUInt64(0, 10000000000000000000UL)
        val reader = ByteDataReader(data)
        assertEquals(10000000000000000000UL, reader.getUInt64())
        assertEquals(8, reader.numBytesRemaining())
    }

    @Test
    fun getUInt64LeAdvancesCursorByEight() {
        val data = ByteArray(16)
        data.putUInt64Le(0, 10000000000000000000UL)
        val reader = ByteDataReader(data)
        assertEquals(10000000000000000000UL, reader.getUInt64Le())
        assertEquals(8, reader.numBytesRemaining())
    }

    @Test
    fun getByteStringAdvancesCursor() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val reader = ByteDataReader(data)
        val byteString = reader.getByteString(3)
        assertEquals(3, byteString.size)
        assertEquals(2, reader.numBytesRemaining())
    }

    @Test
    fun getStringAdvancesCursor() {
        val data = "Hello, World!".encodeToByteArray()
        val reader = ByteDataReader(data)
        assertEquals("Hello", reader.getString(5))
        assertEquals(8, reader.numBytesRemaining())
    }
}

class ByteDataReaderMixedTest {
    @Test
    fun readMixedDataTypes() {
        val data = ByteArray(20)
        data.putInt8(0, 42)
        data.putInt16(1, 0x1234)
        data.putInt32(3, 0x12345678)
        data.putInt64(7, 0x123456789ABCDEF0L)

        val reader = ByteDataReader(data)
        assertEquals(42.toByte(), reader.getInt8())
        assertEquals(0x1234.toShort(), reader.getInt16())
        assertEquals(0x12345678, reader.getInt32())
        assertEquals(0x123456789ABCDEF0L, reader.getInt64())
    }

    @Test
    fun peekThenGetReturnsSameValue() {
        val data = ByteArray(8)
        data.putInt32(0, 0x12345678)
        val reader = ByteDataReader(data)

        val peeked = reader.peekInt32()
        val gotten = reader.getInt32()
        assertEquals(peeked, gotten)
    }
}
