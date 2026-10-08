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

package com.sphereon.cbor

import com.sphereon.cbor.dsl.cborArray
import com.sphereon.cbor.dsl.cborMap
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.cbor.dsl.encode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the CBOR DSL builders.
 *
 * These tests verify that the DSL produces correct CBOR structures and
 * matches the output of the legacy builder API.
 */
class CborDslTest {
    // ========================================
    // Map DSL Tests
    // ========================================

    @Test
    fun testSimpleMap() {
        val map =
            cborMap {
                "name" to "John"
                "age" to 30
            }

        assertEquals(2, map.value.size)
        assertEquals("John", (map.value[StringLabel("name")] as CborString).value)
        assertEquals(30, (map.value[StringLabel("age")] as CborUInt).value.toInt())
    }

    @Test
    fun testMapWithNumericKeys() {
        val map =
            cborMap {
                1 to "algorithm"
                -7 to "ES256"
            }

        assertEquals(2, map.value.size)
        assertEquals("algorithm", (map.value[NumberLabel(1)] as CborString).value)
        assertEquals("ES256", (map.value[NumberLabel(-7)] as CborString).value)
    }

    @Test
    fun testMapWithOptionalValues() {
        val email: String? = null
        val phone: String? = "+1234567890"

        val map =
            cborMap {
                "name" to "John"
                optional("email", email)
                optional("phone", phone)
            }

        assertEquals(2, map.value.size) // email should be skipped
        assertEquals("John", (map.value[StringLabel("name")] as CborString).value)
        assertEquals("+1234567890", (map.value[StringLabel("phone")] as CborString).value)
    }

    @Test
    fun testMapWithNestedMap() {
        val map =
            cborMap {
                "person" map {
                    "name" to "John"
                    "age" to 30
                }
            }

        assertEquals(1, map.value.size)
        val nestedMap = map.value[StringLabel("person")] as CborMap<*, *>
        assertEquals(2, nestedMap.value.size)
    }

    @Test
    fun testMapWithNestedArray() {
        val map =
            cborMap {
                "tags" array {
                    +"kotlin"
                    +"cbor"
                    +"dsl"
                }
            }

        assertEquals(1, map.value.size)
        val array = map.value[StringLabel("tags")] as CborArray<*>
        assertEquals(3, array.value.size)
    }

    @Test
    fun testMapWithBooleans() {
        val map =
            cborMap {
                "active" to true
                "verified" to false
            }

        assertEquals(2, map.value.size)
        assertTrue((map.value[StringLabel("active")] as CborBool).value)
    }

    @Test
    fun testMapWithByteArray() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)

        val map =
            cborMap {
                "data" to bytes
            }

        assertEquals(1, map.value.size)
        val bstr = map.value[StringLabel("data")] as CborByteString
        assertEquals(5, bstr.value.size)
    }

    @Test
    fun testMapWithFloats() {
        val map =
            cborMap {
                "pi" to 3.14159
                "e" to 2.71828f
            }

        assertEquals(2, map.value.size)
    }

    @Test
    fun testMapNonEmptyArray() {
        val items = listOf("a", "b", "c")
        val emptyItems: List<String>? = null

        val map =
            cborMap {
                nonEmptyArray("items", items)
                nonEmptyArray("empty", emptyItems)
            }

        assertEquals(1, map.value.size) // empty should be skipped
        val array = map.value[StringLabel("items")] as CborArray<*>
        assertEquals(3, array.value.size)
    }

    @Test
    fun testMapNonEmptyArrayWithMapper() {
        data class Item(
            val name: String,
            val value: Int,
        )
        val items = listOf(Item("a", 1), Item("b", 2))

        val map =
            cborMap {
                nonEmptyArray("names", items) { it.name }
            }

        val array = map.value[StringLabel("names")] as CborArray<*>
        assertEquals(2, array.value.size)
        assertEquals("a", (array.value[0] as CborString).value)
    }

    // ========================================
    // Array DSL Tests
    // ========================================

    @Test
    fun testSimpleArray() {
        val array =
            cborArray {
                +"hello"
                add(42) // Use add() for primitives since unaryPlus is shadowed by built-in
                add(true)
            }

        assertEquals(3, array.value.size)
        assertEquals("hello", (array.value[0] as CborString).value)
        assertEquals(42, (array.value[1] as CborUInt).value.toInt())
    }

    @Test
    fun testArrayWithExplicitAdd() {
        val array =
            cborArray {
                add("first")
                add(123)
                add(null) // Should be skipped
                add(false)
            }

        assertEquals(3, array.value.size)
    }

    @Test
    fun testArrayWithAddAll() {
        val array =
            cborArray {
                addAll(listOf("a", "b", "c"))
            }

        assertEquals(3, array.value.size)
    }

    @Test
    fun testArrayWithNestedMap() {
        val array =
            cborArray {
                map {
                    "key" to "value"
                }
            }

        assertEquals(1, array.value.size)
        assertTrue(array.value[0] is CborMap<*, *>)
    }

    @Test
    fun testArrayWithNestedArray() {
        val array =
            cborArray {
                array {
                    add(1)
                    add(2)
                }
                array {
                    add(3)
                    add(4)
                }
            }

        assertEquals(2, array.value.size)
        assertTrue(array.value[0] is CborArray<*>)
        assertTrue(array.value[1] is CborArray<*>)
    }

    @Test
    fun testArrayWithByteArray() {
        val array =
            cborArray {
                +byteArrayOf(1, 2, 3)
            }

        assertEquals(1, array.value.size)
        assertTrue(array.value[0] is CborByteString)
    }

    @Test
    fun testArrayWithNull() {
        val array =
            cborArray {
                +"before"
                addNull()
                +"after"
            }

        assertEquals(3, array.value.size)
        assertTrue(array.value[1] is CborNull)
    }

    // ========================================
    // Builder with Subject Tests
    // ========================================

    @Test
    fun testMapBuilderWithSubject() {
        data class Person(
            val name: String,
            val age: Int,
        )
        val person = Person("John", 30)

        val builder =
            cborMapBuilder(person) {
                "name" to person.name
                "age" to person.age
            }

        val map = builder.build() as CborMap<*, *>
        assertEquals(2, map.value.size)
        assertEquals(person, builder.subject())
    }

    // ========================================
    // Encoding Tests
    // ========================================

    @Test
    fun testMapEncoding() {
        val map =
            cborMap {
                "a" to 1
            }

        val encoded = map.encode()
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())

        // Decode and verify
        val decoded = Cbor.tryDecode(encoded)
        assertTrue(decoded.isOk)
    }

    @Test
    fun testArrayEncoding() {
        val array =
            cborArray {
                add(1)
                add(2)
                add(3)
            }

        val encoded = array.encode()
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())

        // Decode and verify
        val decoded = Cbor.tryDecode(encoded)
        assertTrue(decoded.isOk)
    }

    // ========================================
    // Complex Structure Tests
    // ========================================

    @Test
    fun testComplexStructure() {
        val map =
            cborMap {
                "version" to 1
                "header" map {
                    1 to -7 // alg
                    4 to "key-id" // kid
                }
                "payload" to byteArrayOf(1, 2, 3, 4, 5)
                "signatures" array {
                    map {
                        "protected" to byteArrayOf(0xa1.toByte())
                        "signature" to byteArrayOf(0x01, 0x02)
                    }
                }
            }

        assertEquals(4, map.value.size)

        // Verify header
        val header = map.value[StringLabel("header")] as CborMap<*, *>
        assertEquals(2, header.value.size)

        // Verify signatures array
        val signatures = map.value[StringLabel("signatures")] as CborArray<*>
        assertEquals(1, signatures.value.size)
    }

    @Test
    fun testDeepNesting() {
        val map =
            cborMap {
                "level1" map {
                    "level2" map {
                        "level3" map {
                            "value" to 42
                        }
                    }
                }
            }

        val level1 = map.value[StringLabel("level1")] as CborMap<*, *>
        val level2 = level1.value[StringLabel("level2")] as CborMap<*, *>
        val level3 = level2.value[StringLabel("level3")] as CborMap<*, *>
        assertEquals(42, (level3.value[StringLabel("value")] as CborUInt).value.toInt())
    }

    // ========================================
    // Enum Support Tests
    // ========================================

    enum class TestEnum {
        VALUE_A,
        VALUE_B,
        VALUE_C,
    }

    @Test
    fun testEnumInMap() {
        val map =
            cborMap {
                "status" to TestEnum.VALUE_A
            }

        assertEquals(1, map.value.size)
        assertEquals("VALUE_A", (map.value[StringLabel("status")] as CborString).value)
    }

    @Test
    fun testEnumInArray() {
        val array =
            cborArray {
                add(TestEnum.VALUE_B)
            }

        assertEquals(1, array.value.size)
        assertEquals("VALUE_B", (array.value[0] as CborString).value)
    }
}
