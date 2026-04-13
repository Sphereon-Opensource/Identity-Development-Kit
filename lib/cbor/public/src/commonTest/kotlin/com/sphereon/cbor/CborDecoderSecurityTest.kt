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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for CBOR decoder security limits.
 *
 * These tests verify that the decoder properly enforces limits on:
 * - Maximum nesting depth (prevents stack overflow)
 * - Maximum item count (prevents memory exhaustion)
 * - Maximum string length (prevents memory exhaustion)
 */
class CborDecoderSecurityTest {

    @Test
    fun testDecodeSimpleValue() {
        // Simple unsigned integer: 42
        val bytes = byteArrayOf(0x18, 0x2a)
        val result = CborDecoder.decode(bytes)

        assertTrue(result.isOk)
        assertEquals(42L, (result.value as CborUInt).value)
    }

    @Test
    fun testDecodeNegativeInteger() {
        // Negative integer: -1
        val bytes = byteArrayOf(0x20.toByte())
        val result = CborDecoder.decode(bytes)

        assertTrue(result.isOk)
        assertEquals(1L, (result.value as CborNInt).value)
    }

    @Test
    fun testDecodeString() {
        // Text string: "hello"
        val bytes = byteArrayOf(0x65, 0x68, 0x65, 0x6c, 0x6c, 0x6f)
        val result = CborDecoder.decode(bytes)

        assertTrue(result.isOk)
        assertEquals("hello", (result.value as CborString).value)
    }

    @Test
    fun testDecodeArray() {
        // Array [1, 2, 3]
        val bytes = byteArrayOf(0x83.toByte(), 0x01, 0x02, 0x03)
        val result = CborDecoder.decode(bytes)

        assertTrue(result.isOk)
        val array = result.value as CborArray<*>
        assertEquals(3, array.value.size)
    }

    @Test
    fun testDecodeMap() {
        // Map {"a": 1}
        val bytes = byteArrayOf(0xa1.toByte(), 0x61, 0x61, 0x01)
        val result = CborDecoder.decode(bytes)

        assertTrue(result.isOk)
        val map = result.value as CborMap<*, *>
        assertEquals(1, map.value.size)
    }

    @Test
    fun testMaxDepthExceeded() {
        // Create deeply nested arrays that exceed the depth limit
        // Array containing array containing array... (depth = 5)
        val config = CborDecoderConfig(maxDepth = 3, maxItems = 1000, maxStringLength = 1000)

        // Build nested arrays: [[[[1]]]]
        val bytes = byteArrayOf(
            0x81.toByte(), // array(1)
            0x81.toByte(), // array(1)
            0x81.toByte(), // array(1)
            0x81.toByte(), // array(1)
            0x81.toByte(), // array(1)
            0x01           // 1
        )

        val result = CborDecoder.decode(bytes, config)

        assertTrue(result.isErr)
        assertEquals("CBOR_MAX_DEPTH_EXCEEDED", result.error.code)
    }

    @Test
    fun testMaxDepthNotExceeded() {
        // Nested arrays within limit (depth = 2)
        val config = CborDecoderConfig(maxDepth = 3, maxItems = 1000, maxStringLength = 1000)

        // Build nested arrays: [[1]]
        val bytes = byteArrayOf(
            0x81.toByte(), // array(1)
            0x81.toByte(), // array(1)
            0x01           // 1
        )

        val result = CborDecoder.decode(bytes, config)

        assertTrue(result.isOk)
    }

    @Test
    fun testMaxItemsExceeded() {
        // Array with many items that exceed the limit
        val config = CborDecoderConfig(maxDepth = 100, maxItems = 5, maxStringLength = 1000)

        // Array [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
        val bytes = byteArrayOf(
            0x8a.toByte(), // array(10)
            0x01, 0x02, 0x03, 0x04, 0x05,
            0x06, 0x07, 0x08, 0x09, 0x0a
        )

        val result = CborDecoder.decode(bytes, config)

        assertTrue(result.isErr)
        assertEquals("CBOR_MAX_ITEMS_EXCEEDED", result.error.code)
    }

    @Test
    fun testMaxItemsNotExceeded() {
        // Array within item limit
        val config = CborDecoderConfig(maxDepth = 100, maxItems = 10, maxStringLength = 1000)

        // Array [1, 2, 3]
        val bytes = byteArrayOf(0x83.toByte(), 0x01, 0x02, 0x03)

        val result = CborDecoder.decode(bytes, config)

        assertTrue(result.isOk)
    }

    @Test
    fun testMaxStringLengthExceeded() {
        // String that exceeds the length limit
        val config = CborDecoderConfig(maxDepth = 100, maxItems = 1000, maxStringLength = 5)

        // Text string "hello world" (11 bytes)
        val text = "hello world"
        val bytes = ByteArray(1 + text.length)
        bytes[0] = (0x60 + text.length).toByte() // text string header
        text.encodeToByteArray().copyInto(bytes, 1)

        val result = CborDecoder.decode(bytes, config)

        assertTrue(result.isErr)
        assertEquals("CBOR_MAX_STRING_LENGTH_EXCEEDED", result.error.code)
    }

    @Test
    fun testMaxStringLengthNotExceeded() {
        // String within length limit
        val config = CborDecoderConfig(maxDepth = 100, maxItems = 1000, maxStringLength = 20)

        // Text string "hello"
        val bytes = byteArrayOf(0x65, 0x68, 0x65, 0x6c, 0x6c, 0x6f)

        val result = CborDecoder.decode(bytes, config)

        assertTrue(result.isOk)
    }

    @Test
    fun testLeftoverBytesError() {
        // Valid CBOR followed by extra bytes
        val bytes = byteArrayOf(0x01, 0x02) // integer 1 followed by extra byte

        val result = CborDecoder.decode(bytes)

        assertTrue(result.isErr)
        assertEquals("CBOR_LEFTOVER_BYTES", result.error.code)
    }

    @Test
    fun testOutOfBoundsError() {
        // Truncated CBOR (declares 5-byte string but only has 2)
        val bytes = byteArrayOf(0x65, 0x68, 0x65) // text(5) but only "he"

        val result = CborDecoder.decode(bytes)

        assertTrue(result.isErr)
        assertEquals("CBOR_OUT_OF_BOUNDS", result.error.code)
    }

    @Test
    fun testDefaultConfig() {
        val config = CborDecoderConfig.DEFAULT

        assertEquals(64, config.maxDepth)
        assertEquals(1_000_000, config.maxItems)
        assertEquals(10_000_000, config.maxStringLength)
    }

    @Test
    fun testStrictConfig() {
        val config = CborDecoderConfig.STRICT

        assertEquals(32, config.maxDepth)
        assertEquals(100_000, config.maxItems)
        assertEquals(1_000_000, config.maxStringLength)
    }

    @Test
    fun testPermissiveConfig() {
        val config = CborDecoderConfig.PERMISSIVE

        assertEquals(Int.MAX_VALUE, config.maxDepth)
        assertEquals(Int.MAX_VALUE, config.maxItems)
        assertEquals(Int.MAX_VALUE, config.maxStringLength)
    }

    @Test
    fun testCborTryDecodeMethod() {
        // Test the tryDecode method on Cbor object
        val bytes = byteArrayOf(0x18, 0x2a) // 42

        val result = Cbor.tryDecode(bytes)

        assertTrue(result.isOk)
        assertEquals(42L, (result.value as CborUInt).value)
    }

    @Test
    fun testCborTryDecodeWithOffsetMethod() {
        // Test the tryDecodeWithOffset method
        val bytes = byteArrayOf(0x00, 0x18, 0x2a) // padding + 42

        val result = Cbor.tryDecodeWithOffset(bytes, 1)

        assertTrue(result.isOk)
        val (newOffset, item) = result.value
        assertEquals(3, newOffset)
        assertEquals(42L, (item as CborUInt).value)
    }

    @Test
    fun testBooleanValues() {
        // false
        val falseBytes = byteArrayOf(0xf4.toByte())
        val falseResult = CborDecoder.decode(falseBytes)
        assertTrue(falseResult.isOk)
        assertEquals(CborSimple.FALSE, falseResult.value)

        // true
        val trueBytes = byteArrayOf(0xf5.toByte())
        val trueResult = CborDecoder.decode(trueBytes)
        assertTrue(trueResult.isOk)
        assertEquals(CborSimple.TRUE, trueResult.value)
    }

    @Test
    fun testNullValue() {
        val bytes = byteArrayOf(0xf6.toByte())
        val result = CborDecoder.decode(bytes)

        assertTrue(result.isOk)
        assertEquals(CborSimple.NULL, result.value)
    }

    @Test
    fun testUndefinedValue() {
        val bytes = byteArrayOf(0xf7.toByte())
        val result = CborDecoder.decode(bytes)

        assertTrue(result.isOk)
        assertEquals(CborSimple.UNDEFINED, result.value)
    }

    @Test
    fun testByteString() {
        // Byte string h'0102030405'
        val bytes = byteArrayOf(0x45, 0x01, 0x02, 0x03, 0x04, 0x05)
        val result = CborDecoder.decode(bytes)

        assertTrue(result.isOk)
        val bstr = result.value as CborByteString
        assertEquals(5, bstr.value.size)
        assertEquals(1, bstr.value[0].toInt())
    }

    @Test
    fun testTaggedItem() {
        // Tag 24 (encoded CBOR) with byte string
        val innerCbor = byteArrayOf(0x01) // integer 1
        val bytes = byteArrayOf(
            0xd8.toByte(), 0x18, // tag(24)
            0x41, 0x01          // bstr with 1 byte containing 0x01
        )
        val result = CborDecoder.decode(bytes)

        assertTrue(result.isOk)
        assertTrue(result.value is CborEncodedItem<*>)
    }

    @Test
    fun testDecodeWithOffsetMultipleItems() {
        // Two concatenated CBOR items: 1 and 2
        val bytes = byteArrayOf(0x01, 0x02)

        // Decode first item
        val result1 = CborDecoder.decodeWithOffset(bytes, 0)
        assertTrue(result1.isOk)
        val (offset1, item1) = result1.value
        assertEquals(1, offset1)
        assertEquals(1L, (item1 as CborUInt).value)

        // Decode second item
        val result2 = CborDecoder.decodeWithOffset(bytes, offset1)
        assertTrue(result2.isOk)
        val (offset2, item2) = result2.value
        assertEquals(2, offset2)
        assertEquals(2L, (item2 as CborUInt).value)
    }

    @Test
    fun testMapDepthCounting() {
        // Nested maps should count toward depth
        val config = CborDecoderConfig(maxDepth = 2, maxItems = 100, maxStringLength = 1000)

        // { "a": { "b": { "c": 1 } } }
        val bytes = byteArrayOf(
            0xa1.toByte(), 0x61, 0x61, // {"a":
            0xa1.toByte(), 0x61, 0x62, // {"b":
            0xa1.toByte(), 0x61, 0x63, // {"c":
            0x01                       // 1}}}
        )

        val result = CborDecoder.decode(bytes, config)

        assertTrue(result.isErr)
        assertEquals("CBOR_MAX_DEPTH_EXCEEDED", result.error.code)
    }
}
