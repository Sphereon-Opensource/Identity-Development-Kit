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

package com.sphereon.mdoc.types

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Tests for Bytes value classes.
 */
class BytesTest {

    // RawBytes tests

    @Test
    fun testRawBytesCreation() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val rawBytes = RawBytes(bytes)
        assertContentEquals(bytes, rawBytes.value)
    }

    @Test
    fun testRawBytesEmpty() {
        val rawBytes = RawBytes(byteArrayOf())
        assertEquals(0, rawBytes.value.size)
    }

    @Test
    fun testRawBytesSingleByte() {
        val rawBytes = RawBytes(byteArrayOf(42))
        assertEquals(1, rawBytes.value.size)
        assertEquals(42.toByte(), rawBytes.value[0])
    }

    @Test
    fun testRawBytesLarge() {
        val bytes = ByteArray(1000) { it.toByte() }
        val rawBytes = RawBytes(bytes)
        assertEquals(1000, rawBytes.value.size)
    }

    // CborBytes tests

    @Test
    fun testCborBytesCreation() {
        val bytes = byteArrayOf(0xA1.toByte(), 0x01, 0x02)
        val cborBytes = CborBytes(bytes)
        assertContentEquals(bytes, cborBytes.value)
    }

    @Test
    fun testCborBytesEmpty() {
        val cborBytes = CborBytes(byteArrayOf())
        assertEquals(0, cborBytes.value.size)
    }

    @Test
    fun testCborBytesSingleByte() {
        val cborBytes = CborBytes(byteArrayOf(0xF5.toByte()))
        assertEquals(1, cborBytes.value.size)
    }

    // CborLabelString tests

    @Test
    fun testCborLabelStringCreation() {
        val label = CborLabelString("myLabel")
        assertEquals("myLabel", label.value)
    }

    @Test
    fun testCborLabelStringToString() {
        val label = CborLabelString("testLabel")
        assertEquals("testLabel", label.toString())
    }

    @Test
    fun testCborLabelStringEmpty() {
        val label = CborLabelString("")
        assertEquals("", label.value)
        assertEquals("", label.toString())
    }

    @Test
    fun testCborLabelStringSpecialChars() {
        val label = CborLabelString("label-with_special.chars")
        assertEquals("label-with_special.chars", label.toString())
    }

    // CborLabelInt tests

    @Test
    fun testCborLabelIntCreation() {
        val label = CborLabelInt(42)
        assertEquals(42, label.value)
    }

    @Test
    fun testCborLabelIntToString() {
        val label = CborLabelInt(123)
        assertEquals("123", label.toString())
    }

    @Test
    fun testCborLabelIntZero() {
        val label = CborLabelInt(0)
        assertEquals(0, label.value)
        assertEquals("0", label.toString())
    }

    @Test
    fun testCborLabelIntNegative() {
        val label = CborLabelInt(-5)
        assertEquals(-5, label.value)
        assertEquals("-5", label.toString())
    }

    @Test
    fun testCborLabelIntMaxValue() {
        val label = CborLabelInt(Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, label.value)
    }

    @Test
    fun testCborLabelIntMinValue() {
        val label = CborLabelInt(Int.MIN_VALUE)
        assertEquals(Int.MIN_VALUE, label.value)
    }

    // Equality tests

    @Test
    fun testCborLabelStringEquality() {
        val label1 = CborLabelString("test")
        val label2 = CborLabelString("test")
        assertEquals(label1, label2)
    }

    @Test
    fun testCborLabelStringHashCode() {
        val label1 = CborLabelString("test")
        val label2 = CborLabelString("test")
        assertEquals(label1.hashCode(), label2.hashCode())
    }

    @Test
    fun testCborLabelIntEquality() {
        val label1 = CborLabelInt(42)
        val label2 = CborLabelInt(42)
        assertEquals(label1, label2)
    }

    @Test
    fun testCborLabelIntHashCode() {
        val label1 = CborLabelInt(42)
        val label2 = CborLabelInt(42)
        assertEquals(label1.hashCode(), label2.hashCode())
    }
}
