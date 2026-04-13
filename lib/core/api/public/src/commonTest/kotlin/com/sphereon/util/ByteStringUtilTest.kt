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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ByteStringConcatTest {
    @Test
    fun concatJoinsTwoByteStrings() {
        val first = ByteString(1, 2, 3)
        val second = ByteString(4, 5, 6)
        val result = first.concat(second)

        assertEquals(6, result.size)
        assertEquals(1.toByte(), result[0])
        assertEquals(4.toByte(), result[3])
        assertEquals(6.toByte(), result[5])
    }

    @Test
    fun concatWithEmptySecondReturnsFirst() {
        val first = ByteString(1, 2, 3)
        val second = ByteString()
        val result = first.concat(second)

        assertEquals(3, result.size)
    }

    @Test
    fun concatWithEmptyFirstReturnsSecond() {
        val first = ByteString()
        val second = ByteString(1, 2, 3)
        val result = first.concat(second)

        assertEquals(3, result.size)
    }
}

class ByteStringBuilderAppendIntTest {
    @Test
    fun appendInt8AppendsCorrectByte() {
        val result =
            buildByteString {
                appendInt8(42)
            }
        assertEquals(1, result.size)
        assertEquals(42.toByte(), result[0])
    }

    @Test
    fun appendInt8WithByteAppendsCorrectByte() {
        val result =
            buildByteString {
                appendInt8((-100).toByte())
            }
        assertEquals(1, result.size)
        assertEquals((-100).toByte(), result[0])
    }

    @Test
    fun appendInt8ThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendInt8(256)
            }
        }
    }

    @Test
    fun appendInt16AppendsCorrectBytes() {
        val result =
            buildByteString {
                appendInt16(0x1234)
            }
        assertEquals(2, result.size)
        assertEquals(0x12.toByte(), result[0])
        assertEquals(0x34.toByte(), result[1])
    }

    @Test
    fun appendInt16LeAppendsLittleEndian() {
        val result =
            buildByteString {
                appendInt16Le(0x1234)
            }
        assertEquals(2, result.size)
        assertEquals(0x34.toByte(), result[0])
        assertEquals(0x12.toByte(), result[1])
    }

    @Test
    fun appendInt32AppendsCorrectBytes() {
        val result =
            buildByteString {
                appendInt32(0x12345678)
            }
        assertEquals(4, result.size)
        assertEquals(0x12.toByte(), result[0])
        assertEquals(0x34.toByte(), result[1])
        assertEquals(0x56.toByte(), result[2])
        assertEquals(0x78.toByte(), result[3])
    }

    @Test
    fun appendInt32LeAppendsLittleEndian() {
        val result =
            buildByteString {
                appendInt32Le(0x12345678)
            }
        assertEquals(4, result.size)
        assertEquals(0x78.toByte(), result[0])
        assertEquals(0x56.toByte(), result[1])
        assertEquals(0x34.toByte(), result[2])
        assertEquals(0x12.toByte(), result[3])
    }

    @Test
    fun appendInt64AppendsCorrectBytes() {
        val result =
            buildByteString {
                appendInt64(0x123456789ABCDEF0L)
            }
        assertEquals(8, result.size)
        assertEquals(0x12.toByte(), result[0])
        assertEquals(0xF0.toByte(), result[7])
    }

    @Test
    fun appendInt64LeAppendsLittleEndian() {
        val result =
            buildByteString {
                appendInt64Le(0x123456789ABCDEF0L)
            }
        assertEquals(8, result.size)
        assertEquals(0xF0.toByte(), result[0])
        assertEquals(0x12.toByte(), result[7])
    }
}

class ByteStringBuilderAppendUIntTest {
    @Test
    fun appendUInt8AppendsCorrectByte() {
        val result =
            buildByteString {
                appendUInt8(200.toUByte())
            }
        assertEquals(1, result.size)
        assertEquals(200.toUByte(), (result[0].toInt() and 0xFF).toUByte())
    }

    @Test
    fun appendUInt8WithUIntAppendsCorrectByte() {
        val result =
            buildByteString {
                appendUInt8(200u)
            }
        assertEquals(1, result.size)
    }

    @Test
    fun appendUInt8WithIntAppendsCorrectByte() {
        val result =
            buildByteString {
                appendUInt8(200)
            }
        assertEquals(1, result.size)
    }

    @Test
    fun appendUInt16AppendsCorrectBytes() {
        val result =
            buildByteString {
                appendUInt16(50000u)
            }
        assertEquals(2, result.size)
        assertEquals(50000.toUShort(), result.getUInt16(0))
    }

    @Test
    fun appendUInt16WithIntAppendsCorrectBytes() {
        val result =
            buildByteString {
                appendUInt16(50000)
            }
        assertEquals(2, result.size)
    }

    @Test
    fun appendUInt16LeAppendsLittleEndian() {
        val result =
            buildByteString {
                appendUInt16Le(50000u)
            }
        assertEquals(2, result.size)
        assertEquals(50000.toUShort(), result.getUInt16Le(0))
    }

    @Test
    fun appendUInt16LeWithIntAppendsCorrectly() {
        val result =
            buildByteString {
                appendUInt16Le(50000)
            }
        assertEquals(2, result.size)
    }

    @Test
    fun appendUInt32AppendsCorrectBytes() {
        val result =
            buildByteString {
                appendUInt32(3000000000u)
            }
        assertEquals(4, result.size)
        assertEquals(3000000000u, result.getUInt32(0))
    }

    @Test
    fun appendUInt32WithIntAppendsCorrectly() {
        val result =
            buildByteString {
                appendUInt32(1000000)
            }
        assertEquals(4, result.size)
    }

    @Test
    fun appendUInt32LeAppendsLittleEndian() {
        val result =
            buildByteString {
                appendUInt32Le(3000000000u)
            }
        assertEquals(4, result.size)
        assertEquals(3000000000u, result.getUInt32Le(0))
    }

    @Test
    fun appendUInt32LeWithIntAppendsCorrectly() {
        val result =
            buildByteString {
                appendUInt32Le(1000000)
            }
        assertEquals(4, result.size)
    }

    @Test
    fun appendUInt64AppendsCorrectBytes() {
        val result =
            buildByteString {
                appendUInt64(10000000000000000000UL)
            }
        assertEquals(8, result.size)
        assertEquals(10000000000000000000UL, result.getUInt64(0))
    }

    @Test
    fun appendUInt64WithLongAppendsCorrectly() {
        val result =
            buildByteString {
                appendUInt64(1000000000000L)
            }
        assertEquals(8, result.size)
    }

    @Test
    fun appendUInt64LeAppendsLittleEndian() {
        val result =
            buildByteString {
                appendUInt64Le(10000000000000000000UL)
            }
        assertEquals(8, result.size)
        assertEquals(10000000000000000000UL, result.getUInt64Le(0))
    }

    @Test
    fun appendUInt64LeWithLongAppendsCorrectly() {
        val result =
            buildByteString {
                appendUInt64Le(1000000000000L)
            }
        assertEquals(8, result.size)
    }
}

class ByteStringBuilderAppendOtherTest {
    @Test
    fun appendStringAppendsUtf8Bytes() {
        val result =
            buildByteString {
                appendString("Hello")
            }
        assertEquals(5, result.size)
        assertEquals("Hello".encodeToByteArray().toList(), result.toByteArray().toList())
    }

    @Test
    fun appendByteArrayAppendsAllBytes() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val result =
            buildByteString {
                appendByteArray(data)
            }
        assertEquals(5, result.size)
        assertEquals(data.toList(), result.toByteArray().toList())
    }

    @Test
    fun appendByteArrayWithEmptyArrayDoesNothing() {
        val result =
            buildByteString {
                appendInt8(42)
                appendByteArray(byteArrayOf())
            }
        assertEquals(1, result.size)
    }

    @Test
    fun appendByteStringAppendsAllBytes() {
        val data = ByteString(1, 2, 3, 4, 5)
        val result =
            buildByteString {
                appendByteString(data)
            }
        assertEquals(5, result.size)
    }
}

class ByteStringBuilderChainingTest {
    @Test
    fun methodsChainingWorks() {
        val result =
            buildByteString {
                appendInt8(1)
                    .appendInt16(0x0203)
                    .appendInt32(0x04050607)
                    .appendString("Hi")
            }
        assertEquals(9, result.size)
    }
}

class ByteStringReadTest {
    @Test
    fun getInt8ReadsCorrectValue() {
        val data = ByteString(42, -100, 0, 127)
        assertEquals(42.toByte(), data.getInt8(0))
        assertEquals((-100).toByte(), data.getInt8(1))
    }

    @Test
    fun getInt8ThrowsForInvalidOffset() {
        val data = ByteString(1)
        assertFailsWith<IllegalArgumentException> {
            data.getInt8(5)
        }
    }

    @Test
    fun getInt16ReadsCorrectValue() {
        val data = buildByteString { appendInt16(0x1234) }
        assertEquals(0x1234.toShort(), data.getInt16(0))
    }

    @Test
    fun getInt16LeReadsCorrectValue() {
        val data = buildByteString { appendInt16Le(0x1234) }
        assertEquals(0x1234.toShort(), data.getInt16Le(0))
    }

    @Test
    fun getInt32ReadsCorrectValue() {
        val data = buildByteString { appendInt32(0x12345678) }
        assertEquals(0x12345678, data.getInt32(0))
    }

    @Test
    fun getInt32LeReadsCorrectValue() {
        val data = buildByteString { appendInt32Le(0x12345678) }
        assertEquals(0x12345678, data.getInt32Le(0))
    }

    @Test
    fun getInt64ReadsCorrectValue() {
        val data = buildByteString { appendInt64(0x123456789ABCDEF0L) }
        assertEquals(0x123456789ABCDEF0L, data.getInt64(0))
    }

    @Test
    fun getInt64LeReadsCorrectValue() {
        val data = buildByteString { appendInt64Le(0x123456789ABCDEF0L) }
        assertEquals(0x123456789ABCDEF0L, data.getInt64Le(0))
    }

    @Test
    fun getUInt8ReadsCorrectValue() {
        val data = buildByteString { appendUInt8(200.toUByte()) }
        assertEquals(200.toUByte(), data.getUInt8(0))
    }

    @Test
    fun getUInt16ReadsCorrectValue() {
        val data = buildByteString { appendUInt16(50000u) }
        assertEquals(50000.toUShort(), data.getUInt16(0))
    }

    @Test
    fun getUInt16LeReadsCorrectValue() {
        val data = buildByteString { appendUInt16Le(50000u) }
        assertEquals(50000.toUShort(), data.getUInt16Le(0))
    }

    @Test
    fun getUInt32ReadsCorrectValue() {
        val data = buildByteString { appendUInt32(3000000000u) }
        assertEquals(3000000000u, data.getUInt32(0))
    }

    @Test
    fun getUInt32LeReadsCorrectValue() {
        val data = buildByteString { appendUInt32Le(3000000000u) }
        assertEquals(3000000000u, data.getUInt32Le(0))
    }

    @Test
    fun getUInt64ReadsCorrectValue() {
        val data = buildByteString { appendUInt64(10000000000000000000UL) }
        assertEquals(10000000000000000000UL, data.getUInt64(0))
    }

    @Test
    fun getUInt64LeReadsCorrectValue() {
        val data = buildByteString { appendUInt64Le(10000000000000000000UL) }
        assertEquals(10000000000000000000UL, data.getUInt64Le(0))
    }
}

class ByteStringReadBoundsTest {
    @Test
    fun getInt16ThrowsForTooSmallByteString() {
        val data = ByteString(1)
        assertFailsWith<IllegalArgumentException> {
            data.getInt16(0)
        }
    }

    @Test
    fun getInt32ThrowsForTooSmallByteString() {
        val data = ByteString(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            data.getInt32(0)
        }
    }

    @Test
    fun getInt64ThrowsForTooSmallByteString() {
        val data = ByteString(1, 2, 3, 4, 5, 6, 7)
        assertFailsWith<IllegalArgumentException> {
            data.getInt64(0)
        }
    }

    @Test
    fun getInt16ThrowsForInvalidOffset() {
        val data = ByteString(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            data.getInt16(2)
        }
    }

    @Test
    fun getInt32ThrowsForInvalidOffset() {
        val data = ByteString(1, 2, 3, 4, 5)
        assertFailsWith<IllegalArgumentException> {
            data.getInt32(2)
        }
    }

    @Test
    fun getInt64ThrowsForInvalidOffset() {
        val data = ByteString(1, 2, 3, 4, 5, 6, 7, 8, 9)
        assertFailsWith<IllegalArgumentException> {
            data.getInt64(2)
        }
    }

    // Additional size validation tests
    @Test
    fun getInt8ThrowsForEmptyByteString() {
        val data = ByteString()
        assertFailsWith<IllegalArgumentException> {
            data.getInt8(0)
        }
    }

    @Test
    fun getInt16LeThrowsForTooSmallByteString() {
        val data = ByteString(1)
        assertFailsWith<IllegalArgumentException> {
            data.getInt16Le(0)
        }
    }

    @Test
    fun getInt32LeThrowsForTooSmallByteString() {
        val data = ByteString(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            data.getInt32Le(0)
        }
    }

    @Test
    fun getInt64LeThrowsForTooSmallByteString() {
        val data = ByteString(1, 2, 3, 4, 5, 6, 7)
        assertFailsWith<IllegalArgumentException> {
            data.getInt64Le(0)
        }
    }

    @Test
    fun getUInt8ThrowsForEmptyByteString() {
        val data = ByteString()
        assertFailsWith<IllegalArgumentException> {
            data.getUInt8(0)
        }
    }

    @Test
    fun getUInt16ThrowsForTooSmallByteString() {
        val data = ByteString(1)
        assertFailsWith<IllegalArgumentException> {
            data.getUInt16(0)
        }
    }

    @Test
    fun getUInt16LeThrowsForTooSmallByteString() {
        val data = ByteString(1)
        assertFailsWith<IllegalArgumentException> {
            data.getUInt16Le(0)
        }
    }

    @Test
    fun getUInt32ThrowsForTooSmallByteString() {
        val data = ByteString(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            data.getUInt32(0)
        }
    }

    @Test
    fun getUInt32LeThrowsForTooSmallByteString() {
        val data = ByteString(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            data.getUInt32Le(0)
        }
    }

    @Test
    fun getUInt64ThrowsForTooSmallByteString() {
        val data = ByteString(1, 2, 3, 4, 5, 6, 7)
        assertFailsWith<IllegalArgumentException> {
            data.getUInt64(0)
        }
    }

    @Test
    fun getUInt64LeThrowsForTooSmallByteString() {
        val data = ByteString(1, 2, 3, 4, 5, 6, 7)
        assertFailsWith<IllegalArgumentException> {
            data.getUInt64Le(0)
        }
    }

    // Additional offset validation tests
    @Test
    fun getInt16LeThrowsForInvalidOffset() {
        val data = ByteString(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            data.getInt16Le(2)
        }
    }

    @Test
    fun getInt32LeThrowsForInvalidOffset() {
        val data = ByteString(1, 2, 3, 4, 5)
        assertFailsWith<IllegalArgumentException> {
            data.getInt32Le(2)
        }
    }

    @Test
    fun getInt64LeThrowsForInvalidOffset() {
        val data = ByteString(1, 2, 3, 4, 5, 6, 7, 8, 9)
        assertFailsWith<IllegalArgumentException> {
            data.getInt64Le(2)
        }
    }

    @Test
    fun getUInt8ThrowsForInvalidOffset() {
        val data = ByteString(1)
        assertFailsWith<IllegalArgumentException> {
            data.getUInt8(5)
        }
    }

    @Test
    fun getUInt16ThrowsForInvalidOffset() {
        val data = ByteString(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            data.getUInt16(2)
        }
    }

    @Test
    fun getUInt16LeThrowsForInvalidOffset() {
        val data = ByteString(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            data.getUInt16Le(2)
        }
    }

    @Test
    fun getUInt32ThrowsForInvalidOffset() {
        val data = ByteString(1, 2, 3, 4, 5)
        assertFailsWith<IllegalArgumentException> {
            data.getUInt32(2)
        }
    }

    @Test
    fun getUInt32LeThrowsForInvalidOffset() {
        val data = ByteString(1, 2, 3, 4, 5)
        assertFailsWith<IllegalArgumentException> {
            data.getUInt32Le(2)
        }
    }

    @Test
    fun getUInt64ThrowsForInvalidOffset() {
        val data = ByteString(1, 2, 3, 4, 5, 6, 7, 8, 9)
        assertFailsWith<IllegalArgumentException> {
            data.getUInt64(2)
        }
    }

    @Test
    fun getUInt64LeThrowsForInvalidOffset() {
        val data = ByteString(1, 2, 3, 4, 5, 6, 7, 8, 9)
        assertFailsWith<IllegalArgumentException> {
            data.getUInt64Le(2)
        }
    }
}

class ByteStringAppendRangeValidationTest {
    @Test
    fun appendInt8WithByteThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendInt8(0.toByte(), 10..20)
            }
        }
    }

    @Test
    fun appendInt16ThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendInt16(100, 0..50)
            }
        }
    }

    @Test
    fun appendInt16LeThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendInt16Le(100, 0..50)
            }
        }
    }

    @Test
    fun appendInt32ThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendInt32(100, 0..50)
            }
        }
    }

    @Test
    fun appendInt32LeThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendInt32Le(100, 0..50)
            }
        }
    }

    @Test
    fun appendInt64ThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendInt64(100L, 0L..50L)
            }
        }
    }

    @Test
    fun appendInt64LeThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendInt64Le(100L, 0L..50L)
            }
        }
    }

    @Test
    fun appendUInt8WithUByteThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendUInt8(100.toUByte(), 10u..50u)
            }
        }
    }

    @Test
    fun appendUInt8WithUIntThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendUInt8(100u, 10u..50u)
            }
        }
    }

    @Test
    fun appendUInt8WithIntThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendUInt8(100, 10u..50u)
            }
        }
    }

    @Test
    fun appendUInt16ThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendUInt16(100u, 10u..50u)
            }
        }
    }

    @Test
    fun appendUInt16WithIntThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendUInt16(100, 10u..50u)
            }
        }
    }

    @Test
    fun appendUInt16LeThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendUInt16Le(100u, 10u..50u)
            }
        }
    }

    @Test
    fun appendUInt16LeWithIntThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendUInt16Le(100, 10u..50u)
            }
        }
    }

    @Test
    fun appendUInt32ThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendUInt32(100u, 10u..50u)
            }
        }
    }

    @Test
    fun appendUInt32WithIntThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendUInt32(100, 10u..50u)
            }
        }
    }

    @Test
    fun appendUInt32LeThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendUInt32Le(100u, 10u..50u)
            }
        }
    }

    @Test
    fun appendUInt32LeWithIntThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendUInt32Le(100, 10u..50u)
            }
        }
    }

    @Test
    fun appendUInt64ThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendUInt64(100UL, 10UL..50UL)
            }
        }
    }

    @Test
    fun appendUInt64WithLongThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendUInt64(100L, 10UL..50UL)
            }
        }
    }

    @Test
    fun appendUInt64LeThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendUInt64Le(100UL, 10UL..50UL)
            }
        }
    }

    @Test
    fun appendUInt64LeWithLongThrowsForValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            buildByteString {
                appendUInt64Le(100L, 10UL..50UL)
            }
        }
    }
}
