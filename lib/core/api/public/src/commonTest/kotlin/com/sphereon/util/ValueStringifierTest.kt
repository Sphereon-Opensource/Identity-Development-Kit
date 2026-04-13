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
import kotlin.test.assertTrue

class StringifyBasicTest {

    @Test
    fun stringifyNullReturnsNull() {
        assertEquals("null", stringify(null))
    }

    @Test
    fun stringifyStringReturnsString() {
        assertEquals("Hello", stringify("Hello"))
    }

    @Test
    fun stringifyNumberReturnsString() {
        assertEquals("42", stringify(42))
        assertEquals("3.14", stringify(3.14))
    }

    @Test
    fun stringifyBooleanReturnsString() {
        assertEquals("true", stringify(true))
        assertEquals("false", stringify(false))
    }
}

class StringifyArrayTest {

    @Test
    fun stringifyBooleanArrayReturnsCorrectFormat() {
        val arr = booleanArrayOf(true, false, true)
        assertEquals("booleanArrayOf(true, false, true)", stringify(arr))
    }

    @Test
    fun stringifyByteArrayReturnsHex() {
        val arr = byteArrayOf(0x12, 0x34, 0xAB.toByte())
        val result = stringify(arr)
        assertTrue(result.startsWith("byteArrayOf("))
        assertTrue(result.contains("1234ab"))
    }

    @Test
    fun stringifyShortArrayReturnsCorrectFormat() {
        val arr = shortArrayOf(1, 2, 3)
        assertEquals("shortArrayOf(1, 2, 3)", stringify(arr))
    }

    @Test
    fun stringifyIntArrayReturnsCorrectFormat() {
        val arr = intArrayOf(10, 20, 30)
        assertEquals("intArrayOf(10, 20, 30)", stringify(arr))
    }

    @Test
    fun stringifyLongArrayReturnsCorrectFormat() {
        val arr = longArrayOf(100L, 200L, 300L)
        assertEquals("longArrayOf(100, 200, 300)", stringify(arr))
    }

    @Test
    fun stringifyFloatArrayReturnsCorrectFormat() {
        val arr = floatArrayOf(1.5f, 2.5f)
        val result = stringify(arr)
        assertTrue(result.startsWith("floatArrayOf("))
        assertTrue(result.contains("1.5"))
        assertTrue(result.contains("2.5"))
    }

    @Test
    fun stringifyDoubleArrayReturnsCorrectFormat() {
        val arr = doubleArrayOf(1.5, 2.5)
        val result = stringify(arr)
        assertTrue(result.startsWith("doubleArrayOf("))
        assertTrue(result.contains("1.5"))
        assertTrue(result.contains("2.5"))
    }

    @Test
    fun stringifyCharArrayReturnsCorrectFormat() {
        val arr = charArrayOf('a', 'b', 'c')
        assertEquals("charArrayOf(a, b, c)", stringify(arr))
    }

    @Test
    fun stringifyObjectArrayReturnsCorrectFormat() {
        val arr = arrayOf("one", "two", "three")
        assertEquals("[one, two, three]", stringify(arr))
    }

    @Test
    fun stringifyNestedObjectArrayReturnsCorrectFormat() {
        val arr = arrayOf(arrayOf(1, 2), arrayOf(3, 4))
        assertEquals("[[1, 2], [3, 4]]", stringify(arr))
    }
}

class StringifyIterableTest {

    @Test
    fun stringifyListReturnsCorrectFormat() {
        val list = listOf(1, 2, 3)
        assertEquals("[1, 2, 3]", stringify(list))
    }

    @Test
    fun stringifySetReturnsCorrectFormat() {
        val set = setOf("a")
        assertEquals("[a]", stringify(set))
    }

    @Test
    fun stringifyEmptyListReturnsEmptyBrackets() {
        assertEquals("[]", stringify(emptyList<Any>()))
    }

    @Test
    fun stringifyNestedListReturnsCorrectFormat() {
        val nested = listOf(listOf(1, 2), listOf(3, 4))
        assertEquals("[[1, 2], [3, 4]]", stringify(nested))
    }

    @Test
    fun stringifyListWithNullsHandlesCorrectly() {
        val list = listOf("a", null, "b")
        assertEquals("[a, null, b]", stringify(list))
    }
}

class StringifyMapTest {

    @Test
    fun stringifyMapReturnsCorrectFormat() {
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val result = stringify(map)
        assertTrue(result.startsWith("{"))
        assertTrue(result.endsWith("}"))
        assertTrue(result.contains("key1=value1"))
        assertTrue(result.contains("key2=value2"))
    }

    @Test
    fun stringifyEmptyMapReturnsEmptyBraces() {
        assertEquals("{}", stringify(emptyMap<String, Any>()))
    }

    @Test
    fun stringifyNestedMapReturnsCorrectFormat() {
        val nested = mapOf("outer" to mapOf("inner" to "value"))
        val result = stringify(nested)
        assertTrue(result.contains("outer={inner=value}"))
    }

    @Test
    fun stringifyMapWithNullValueHandlesCorrectly() {
        val map = mapOf("key" to null)
        assertEquals("{key=null}", stringify(map))
    }

    @Test
    fun stringifyMapWithListValueHandlesCorrectly() {
        val map = mapOf("key" to listOf(1, 2, 3))
        assertEquals("{key=[1, 2, 3]}", stringify(map))
    }

    @Test
    fun stringifyMapWithArrayValueHandlesCorrectly() {
        val map = mapOf("key" to arrayOf("a", "b"))
        val result = stringify(map)
        assertTrue(result.contains("key=[a, b]"))
    }
}

class StringifyMapFunctionTest {

    @Test
    fun stringifyMapFunctionDirectlyWorks() {
        val map = mapOf("a" to 1, "b" to 2)
        val result = stringifyMap(map)
        assertTrue(result.contains("a=1"))
        assertTrue(result.contains("b=2"))
    }
}

class ToHexStringTest {

    @Test
    fun toHexStringConvertsCorrectly() {
        val bytes = byteArrayOf(0x12, 0x34, 0xAB.toByte(), 0xCD.toByte())
        assertEquals("1234abcd", toHexString(bytes))
    }

    @Test
    fun toHexStringEmptyArrayReturnsEmpty() {
        assertEquals("", toHexString(byteArrayOf()))
    }

    @Test
    fun toHexStringPadsWithZeros() {
        val bytes = byteArrayOf(0x01, 0x0A)
        assertEquals("010a", toHexString(bytes))
    }

    @Test
    fun toHexStringHandlesSingleByte() {
        assertEquals("ff", toHexString(byteArrayOf(0xFF.toByte())))
    }
}
