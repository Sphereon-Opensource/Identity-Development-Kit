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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CborFloatTest {

    // RFC 8949 test vectors for floating point numbers

    // Half-precision (float16) tests
    @Test
    fun testFloat16Zero() {
        // 0.0 in half-precision
        val decoded = cborSerializer.decode<CborFloat16>("f90000".decodeFromHex())
        assertEquals(0.0f, decoded.value)
    }

    @Test
    fun testFloat16NegativeZero() {
        // -0.0 in half-precision
        val decoded = cborSerializer.decode<CborFloat16>("f98000".decodeFromHex())
        assertEquals(-0.0f, decoded.value)
    }

    @Test
    fun testFloat16One() {
        // 1.0 in half-precision
        val decoded = cborSerializer.decode<CborFloat16>("f93c00".decodeFromHex())
        assertEquals(1.0f, decoded.value)
    }

    @Test
    fun testFloat16OnePointFive() {
        // 1.5 in half-precision
        val decoded = cborSerializer.decode<CborFloat16>("f93e00".decodeFromHex())
        assertEquals(1.5f, decoded.value)
    }

    @Test
    fun testFloat16LargeValue() {
        // 65504.0 in half-precision (largest representable)
        val decoded = cborSerializer.decode<CborFloat16>("f97bff".decodeFromHex())
        assertEquals(65504.0f, decoded.value)
    }

    @Test
    fun testFloat16SmallValue() {
        // 5.960464477539063e-8 (smallest subnormal)
        val decoded = cborSerializer.decode<CborFloat16>("f90001".decodeFromHex())
        assertTrue(decoded.value > 0.0f)
        assertTrue(decoded.value < 0.0001f)
    }

    @Test
    fun testFloat16PositiveInfinity() {
        val decoded = cborSerializer.decode<CborFloat16>("f97c00".decodeFromHex())
        assertEquals(Float.POSITIVE_INFINITY, decoded.value)
    }

    @Test
    fun testFloat16NegativeInfinity() {
        val decoded = cborSerializer.decode<CborFloat16>("f9fc00".decodeFromHex())
        assertEquals(Float.NEGATIVE_INFINITY, decoded.value)
    }

    @Test
    fun testFloat16NaN() {
        val decoded = cborSerializer.decode<CborFloat16>("f97e00".decodeFromHex())
        assertTrue(decoded.value.isNaN())
    }

    // Single-precision (float32) tests
    @Test
    fun testFloat32Zero() {
        val encoded = cborSerializer.encode(CborFloat32(0.0f))
        assertEquals("fa00000000", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborFloat>(encoded)
        assertEquals(0.0f, decoded.value)
    }

    @Test
    fun testFloat32One() {
        val encoded = cborSerializer.encode(CborFloat32(1.0f))
        assertEquals("fa3f800000", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborFloat>(encoded)
        assertEquals(1.0f, decoded.value)
    }

    @Test
    fun testFloat32OnePointFive() {
        val encoded = cborSerializer.encode(CborFloat32(1.5f))
        assertEquals("fa3fc00000", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborFloat>(encoded)
        assertEquals(1.5f, decoded.value)
    }

    @Test
    fun testFloat32Pi() {
        val pi = 3.14159265f
        val encoded = cborSerializer.encode(CborFloat32(pi))
        val decoded = cborSerializer.decode<CborFloat>(encoded)
        assertEquals(pi, decoded.value, 0.0001f)
    }

    @Test
    fun testFloat32Negative() {
        val encoded = cborSerializer.encode(CborFloat32(-4.0f))
        val decoded = cborSerializer.decode<CborFloat>(encoded)
        assertEquals(-4.0f, decoded.value)
    }

    @Test
    fun testFloat32LargeNumber() {
        val large = 100000.0f
        val encoded = cborSerializer.encode(CborFloat32(large))
        val decoded = cborSerializer.decode<CborFloat>(encoded)
        assertEquals(large, decoded.value)
    }

    @Test
    fun testFloat32SmallNumber() {
        val small = 0.00001f
        val encoded = cborSerializer.encode(CborFloat32(small))
        val decoded = cborSerializer.decode<CborFloat>(encoded)
        assertEquals(small, decoded.value, 0.000001f)
    }

    @Test
    fun testFloat32PositiveInfinity() {
        val encoded = cborSerializer.encode(CborFloat32(Float.POSITIVE_INFINITY))
        val decoded = cborSerializer.decode<CborFloat>(encoded)
        assertEquals(Float.POSITIVE_INFINITY, decoded.value)
    }

    @Test
    fun testFloat32NegativeInfinity() {
        val encoded = cborSerializer.encode(CborFloat32(Float.NEGATIVE_INFINITY))
        val decoded = cborSerializer.decode<CborFloat>(encoded)
        assertEquals(Float.NEGATIVE_INFINITY, decoded.value)
    }

    @Test
    fun testFloat32NaN() {
        val encoded = cborSerializer.encode(CborFloat32(Float.NaN))
        val decoded = cborSerializer.decode<CborFloat>(encoded)
        assertTrue(decoded.value.isNaN())
    }

    // Double-precision (float64) tests
    @Test
    fun testDoubleZero() {
        val encoded = cborSerializer.encode(CborDouble(0.0))
        assertEquals("fb0000000000000000", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborDouble>(encoded)
        assertEquals(0.0, decoded.value)
    }

    @Test
    fun testDoubleOne() {
        val encoded = cborSerializer.encode(CborDouble(1.0))
        assertEquals("fb3ff0000000000000", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborDouble>(encoded)
        assertEquals(1.0, decoded.value)
    }

    @Test
    fun testDoubleOnePointOne() {
        val encoded = cborSerializer.encode(CborDouble(1.1))
        assertEquals("fb3ff199999999999a", encoded.encodeToHex())
        val decoded = cborSerializer.decode<CborDouble>(encoded)
        assertEquals(1.1, decoded.value, 0.0000001)
    }

    @Test
    fun testDoublePi() {
        val pi = 3.141592653589793
        val encoded = cborSerializer.encode(CborDouble(pi))
        val decoded = cborSerializer.decode<CborDouble>(encoded)
        assertEquals(pi, decoded.value, 0.0000000001)
    }

    @Test
    fun testDoubleNegative() {
        val encoded = cborSerializer.encode(CborDouble(-4.1))
        val decoded = cborSerializer.decode<CborDouble>(encoded)
        assertEquals(-4.1, decoded.value, 0.0000001)
    }

    @Test
    fun testDoubleLargeNumber() {
        val large = 1.0e+300
        val encoded = cborSerializer.encode(CborDouble(large))
        val decoded = cborSerializer.decode<CborDouble>(encoded)
        assertEquals(large, decoded.value)
    }

    @Test
    fun testDoubleSmallNumber() {
        val small = 5.960464477539063e-8
        val encoded = cborSerializer.encode(CborDouble(small))
        val decoded = cborSerializer.decode<CborDouble>(encoded)
        assertEquals(small, decoded.value, 1e-15)
    }

    @Test
    fun testDoublePositiveInfinity() {
        val encoded = cborSerializer.encode(CborDouble(Double.POSITIVE_INFINITY))
        val decoded = cborSerializer.decode<CborDouble>(encoded)
        assertEquals(Double.POSITIVE_INFINITY, decoded.value)
    }

    @Test
    fun testDoubleNegativeInfinity() {
        val encoded = cborSerializer.encode(CborDouble(Double.NEGATIVE_INFINITY))
        val decoded = cborSerializer.decode<CborDouble>(encoded)
        assertEquals(Double.NEGATIVE_INFINITY, decoded.value)
    }

    @Test
    fun testDoubleNaN() {
        val encoded = cborSerializer.encode(CborDouble(Double.NaN))
        val decoded = cborSerializer.decode<CborDouble>(encoded)
        assertTrue(decoded.value.isNaN())
    }

    // Major type tests
    @Test
    fun testFloatMajorType() {
        val float = CborFloat32(1.0f)
        assertEquals(MajorType.SPECIAL, float.majorType)
    }

    @Test
    fun testDoubleMajorType() {
        val double = CborDouble(1.0)
        assertEquals(MajorType.SPECIAL, double.majorType)
    }

    // Extension function tests
    @Test
    fun testToCborFloat() {
        val float = 3.14f.toCborFloat()
        assertIs<CborFloat32>(float)
        assertEquals(3.14f, float.value, 0.001f)
    }

    @Test
    fun testToCborFloat64() {
        val double = 3.14159265358979.toCborFloat64()
        assertIs<CborDouble>(double)
        assertEquals(3.14159265358979, double.value, 0.0000000001)
    }

    // CDDL type tests
    @Test
    fun testCDDLFloat() {
        val item = CDDL.float.newFloat(3.14f)
        assertIs<CborFloat32>(item)
        assertEquals(3.14f, item.value, 0.001f)
    }

    @Test
    fun testCDDLFloat16() {
        val item = CDDL.float16.newFloat16(1.5f)
        assertIs<CborFloat16>(item)
        assertEquals(1.5f, item.value)
    }

    @Test
    fun testCDDLFloat32() {
        val item = CDDL.float32.newFloat32(3.14f)
        assertIs<CborFloat32>(item)
        assertEquals(3.14f, item.value, 0.001f)
    }

    @Test
    fun testCDDLFloat64() {
        val item = CDDL.float64.newFloat64(3.141592653589793)
        assertIs<CborDouble>(item)
        assertEquals(3.141592653589793, item.value, 0.0000000001)
    }

    // toCborItem conversion tests
    @Test
    fun testFloatToCborItem() {
        val item = 3.14f.toCborItem()
        assertIsCborFloat(item)
        assertEquals(3.14, (item.value as Number).toDouble(), 0.01)
    }

    @Test
    fun testDoubleToCborItem() {
        val item = 3.141592653589793.toCborItem()
        assertIsCborDouble(item)
        assertEquals(3.141592653589793, (item as CborDouble).value, 0.0000000001)
    }

    // JSON conversion tests
    @Test
    fun testDoubleToJsonSimple() {
        val double = CborDouble(3.14)
        val json = double.toJsonSimple()
        assertTrue(json.toString().contains("3.14"))
    }

    // Round-trip tests
    @Test
    fun testRoundTripFloat32() {
        val testValues = listOf(
            0.0f, 1.0f, -1.0f, 3.14f, -3.14f,
            Float.MAX_VALUE, Float.MIN_VALUE,
            Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY
        )

        for (value in testValues) {
            val item = CborFloat32(value)
            val encoded = cborSerializer.encode(item)
            val decoded = cborSerializer.decode<CborFloat>(encoded)
            if (value.isNaN()) {
                assertTrue(decoded.value.isNaN())
            } else {
                // Use relative tolerance for Float32 round-trip (JS has reduced precision for Float32 values)
                val tolerance = if (value == 0.0f) 0.001 else maxOf(0.001, kotlin.math.abs(value.toDouble()) * 1e-6)
                assertEquals(value.toDouble(), decoded.value.toDouble(), tolerance, "Round-trip failed for value: $value")
            }
        }
    }

    @Test
    fun testRoundTripFloat64() {
        val testValues = listOf(
            0.0, 1.0, -1.0, 3.141592653589793, -3.141592653589793,
            Double.MAX_VALUE, Double.MIN_VALUE,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY
        )

        for (value in testValues) {
            val item = CborDouble(value)
            val encoded = cborSerializer.encode(item)
            val decoded = cborSerializer.decode<CborDouble>(encoded)
            if (value.isNaN()) {
                assertTrue(decoded.value.isNaN())
            } else {
                assertEquals(value, decoded.value, "Round-trip failed for value: $value")
            }
        }
    }

    // Decode from known test vectors (RFC 8949)
    @Test
    fun testDecodeFloat32KnownVector() {
        // 100000.0f
        val decoded = cborSerializer.decode<CborFloat>("fa47c35000".decodeFromHex())
        assertEquals(100000.0f, decoded.value)
    }

    @Test
    fun testDecodeFloat64KnownVector() {
        // 1.0e+300
        val decoded = cborSerializer.decode<CborDouble>("fb7e37e43c8800759c".decodeFromHex())
        assertEquals(1.0e+300, decoded.value, 1e+290)
    }

    @Test
    fun testDecodeNegativeFloat32() {
        // -4.0f
        val decoded = cborSerializer.decode<CborFloat>("fac0800000".decodeFromHex())
        assertEquals(-4.0f, decoded.value)
    }
}
