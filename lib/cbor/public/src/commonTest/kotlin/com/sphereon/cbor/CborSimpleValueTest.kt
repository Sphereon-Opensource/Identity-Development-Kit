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

import com.sphereon.core.api.decodeFromHex
import com.sphereon.core.api.encodeToHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CborSimpleValueTest {

    // Boolean tests - RFC 8949 test vectors

    @Test
    fun testFalse() {
        val encoded = cborSerializer.encode(CborSimple.FALSE)
        assertEquals("f4", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborBool>(encoded)
        assertEquals(false, decoded.value)
        assertIs<CborFalse>(decoded)
    }

    @Test
    fun testTrue() {
        val encoded = cborSerializer.encode(CborSimple.TRUE)
        assertEquals("f5", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborBool>(encoded)
        assertEquals(true, decoded.value)
        assertIs<CborTrue>(decoded)
    }

    @Test
    fun testNull() {
        val encoded = cborSerializer.encode(CborSimple.NULL)
        assertEquals("f6", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborSimple<*>>(encoded)
        assertSame(CborSimple.NULL, decoded)
    }

    @Test
    fun testUndefined() {
        val encoded = cborSerializer.encode(CborSimple.UNDEFINED)
        assertEquals("f7", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborSimple<*>>(encoded)
        assertSame(CborSimple.UNDEFINED, decoded)
    }

    // CborNull specific tests
    @Test
    fun testCborNullClass() {
        val nullItem = CborNull()
        assertEquals(null, nullItem.value)
        assertEquals(MajorType.SPECIAL, nullItem.majorType)
    }

    @Test
    fun testCborNullToJsonSimple() {
        val nullItem = CborNull()
        val json = nullItem.toJsonSimple()
        assertEquals("null", json.toString())
    }

    // CborUndefined specific tests
    @Test
    fun testCborUndefinedClass() {
        val undefinedItem = CborUndefined()
        assertEquals(Unit, undefinedItem.value)
        assertEquals(MajorType.SPECIAL, undefinedItem.majorType)
    }

    @Test
    fun testCborUndefinedToJsonSimple() {
        val undefinedItem = CborUndefined()
        val json = undefinedItem.toJsonSimple()
        assertEquals("null", json.toString())
    }

    // CborBool tests
    @Test
    fun testCborTrueDirectInstantiation() {
        val trueItem = CborTrue()
        assertEquals(true, trueItem.value)
        assertEquals(MajorType.SPECIAL, trueItem.majorType)
    }

    @Test
    fun testCborFalseDirectInstantiation() {
        val falseItem = CborFalse()
        assertEquals(false, falseItem.value)
        assertEquals(MajorType.SPECIAL, falseItem.majorType)
    }

    @Test
    fun testCborBoolToJsonSimple() {
        val trueItem = CborTrue()
        assertEquals("true", trueItem.toJsonSimple().toString())

        val falseItem = CborFalse()
        assertEquals("false", falseItem.toJsonSimple().toString())
    }

    // asBool accessor
    @Test
    fun testAsBool() {
        val trueItem = CborSimple.TRUE
        assertEquals(true, trueItem.asBool)

        val falseItem = CborSimple.FALSE
        assertEquals(false, falseItem.asBool)
    }

    @Test
    fun testAsBoolOnNonBool() {
        val uint = CborUInt(42)
        assertFailsWith<IllegalArgumentException> {
            uint.asBool
        }
    }

    // CDDL type tests
    @Test
    fun testCDDLBoolTrue() {
        val item = CDDL.bool.newBool(true)
        assertSame(CborSimple.TRUE, item)
    }

    @Test
    fun testCDDLBoolFalse() {
        val item = CDDL.bool.newBool(false)
        assertSame(CborSimple.FALSE, item)
    }

    @Test
    fun testCDDLTrue() {
        val item = CDDL.True.newTrue()
        assertSame(CborSimple.TRUE, item)
    }

    @Test
    fun testCDDLFalse() {
        val item = CDDL.False.newFalse()
        assertSame(CborSimple.FALSE, item)
    }

    @Test
    fun testCDDLNil() {
        val item = CDDL.nil.newNil()
        assertSame(CborSimple.NULL, item)
    }

    @Test
    fun testCDDLNull() {
        val item = CDDL.Null.newNull()
        assertSame(CborSimple.NULL, item)
    }

    @Test
    fun testCDDLUndefined() {
        val item = CDDL.undefined.newUndefined()
        assertSame(CborSimple.UNDEFINED, item)
    }

    // Extension function tests
    @Test
    fun testToCborBoolTrue() {
        val item = true.toCborBool()
        assertSame(CborSimple.TRUE, item)
    }

    @Test
    fun testToCborBoolFalse() {
        val item = false.toCborBool()
        assertSame(CborSimple.FALSE, item)
    }

    // toCborItem conversion
    @Test
    fun testBooleanToCborItem() {
        val trueItem = true.toCborItem()
        assertSame(CborSimple.TRUE, trueItem)

        val falseItem = false.toCborItem()
        assertSame(CborSimple.FALSE, falseItem)
    }

    @Test
    fun testNullToCborItem() {
        val item = null.toCborItem()
        assertIs<CborNull>(item)
    }

    // Equality tests
    @Test
    fun testSimpleEquality() {
        assertEquals(CborSimple.TRUE, CborSimple.TRUE)
        assertEquals(CborSimple.FALSE, CborSimple.FALSE)
        assertEquals(CborSimple.NULL, CborSimple.NULL)
        assertEquals(CborSimple.UNDEFINED, CborSimple.UNDEFINED)

        assertTrue(CborSimple.TRUE != CborSimple.FALSE)
        assertTrue(CborSimple.NULL != CborSimple.UNDEFINED)
    }

    @Test
    fun testBoolHashCode() {
        assertEquals(CborSimple.TRUE.hashCode(), CborTrue().hashCode())
        assertEquals(CborSimple.FALSE.hashCode(), CborFalse().hashCode())
    }

    // toString tests
    @Test
    fun testSimpleToString() {
        assertTrue(CborSimple.TRUE.toString().contains("TRUE"))
        assertTrue(CborSimple.FALSE.toString().contains("FALSE"))
        assertTrue(CborSimple.NULL.toString().contains("NULL"))
        assertTrue(CborSimple.UNDEFINED.toString().contains("UNDEFINED"))
    }

    // Major type tests
    @Test
    fun testSimpleMajorType() {
        assertEquals(MajorType.SPECIAL, CborSimple.TRUE.majorType)
        assertEquals(MajorType.SPECIAL, CborSimple.FALSE.majorType)
        assertEquals(MajorType.SPECIAL, CborSimple.NULL.majorType)
        assertEquals(MajorType.SPECIAL, CborSimple.UNDEFINED.majorType)
    }

    // Decode from known test vectors
    @Test
    fun testDecodeKnownVectors() {
        // false
        val falseDecoded = cborSerializer.decode<CborBool>("f4".decodeFromHex())
        assertEquals(false, falseDecoded.value)

        // true
        val trueDecoded = cborSerializer.decode<CborBool>("f5".decodeFromHex())
        assertEquals(true, trueDecoded.value)

        // null
        val nullDecoded = cborSerializer.decode<CborSimple<*>>("f6".decodeFromHex())
        assertSame(CborSimple.NULL, nullDecoded)

        // undefined
        val undefinedDecoded = cborSerializer.decode<CborSimple<*>>("f7".decodeFromHex())
        assertSame(CborSimple.UNDEFINED, undefinedDecoded)
    }

    // Error cases
    @Test
    fun testInvalidSimpleValueTwoBytes() {
        // Two-byte simple value with value < 32 is invalid
        assertFailsWith<IllegalArgumentException> {
            cborSerializer.decode<CborSimple<*>>("f810".decodeFromHex()) // simple(16) in two bytes
        }
    }

    // Round-trip tests
    @Test
    fun testRoundTrip() {
        val values = listOf(CborSimple.TRUE, CborSimple.FALSE, CborSimple.NULL, CborSimple.UNDEFINED)

        for (value in values) {
            val encoded = cborSerializer.encode(value)
            val decoded = cborSerializer.decode<CborSimple<*>>(encoded)
            assertEquals(value, decoded, "Round-trip failed for: $value")
        }
    }

    // Singleton behavior tests
    @Test
    fun testSingletonBehavior() {
        // Multiple calls should return the same instance
        assertSame(CborSimple.TRUE, CDDL.bool.newBool(true))
        assertSame(CborSimple.FALSE, CDDL.bool.newBool(false))
        assertSame(CborSimple.NULL, CDDL.nil.newNil())
        assertSame(CborSimple.UNDEFINED, CDDL.undefined.newUndefined())
    }

    // JSON conversion with CDDL
    @Test
    fun testToJsonWithCDDL() {
        val trueItem = CborSimple.TRUE
        val json = trueItem.toJsonWithCDDL()
        assertTrue(json.toString().contains("true"))
    }

    // Info value tests
    @Test
    fun testInfoValues() {
        assertEquals(20, CDDL.False.info)
        assertEquals(21, CDDL.True.info)
        assertEquals(22, CDDL.Null.info)
        assertEquals(23, CDDL.undefined.info)
    }
}
