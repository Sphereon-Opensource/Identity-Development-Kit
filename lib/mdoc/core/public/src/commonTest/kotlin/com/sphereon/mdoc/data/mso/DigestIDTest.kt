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

package com.sphereon.mdoc.data.mso

import com.sphereon.cbor.CborUInt
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for DigestID value class.
 */
class DigestIDTest {

    @Test
    fun testDigestIDCreation() {
        val id = DigestID(0u)
        assertEquals("0", id.toString())
    }

    @Test
    fun testDigestIDWithValue() {
        val id = DigestID(42u)
        assertEquals("42", id.toString())
    }

    @Test
    fun testToCborStructure() {
        val id = DigestID(100u)
        val cbor = id.toCborStructure()
        assertEquals(100L, cbor.value)
    }

    @Test
    fun testFromCborStructure() {
        val cbor = CborUInt(55L)
        val id = DigestID.Decoder.fromCborStructure(cbor)
        assertEquals("55", id.toString())
    }

    @Test
    fun testLargeValue() {
        val id = DigestID(4294967295u) // Max UInt value
        val cbor = id.toCborStructure()
        assertEquals(4294967295L, cbor.value)
    }

    @Test
    fun testEquality() {
        val id1 = DigestID(10u)
        val id2 = DigestID(10u)
        assertEquals(id1, id2)
    }

    @Test
    fun testHashCodeConsistency() {
        val id1 = DigestID(42u)
        val id2 = DigestID(42u)
        assertEquals(id1.hashCode(), id2.hashCode())
    }

    @Test
    fun testToCborAndBack() {
        val original = DigestID(99u)
        val cbor = original.toCborStructure()
        val decoded = DigestID.Decoder.fromCborStructure(cbor)
        assertEquals(original, decoded)
    }

    @Test
    fun testJsonSerialization() {
        val id = DigestID(42u)
        val json = Json.encodeToString(DigestID.serializer(), id)
        assertTrue(json.contains("42"))
    }

    @Test
    fun testJsonDeserialization() {
        val json = "100"
        val id = Json.decodeFromString(DigestID.serializer(), json)
        assertEquals("100", id.toString())
    }

    @Test
    fun testJsonRoundTrip() {
        val original = DigestID(255u)
        val json = Json.encodeToString(DigestID.serializer(), original)
        val decoded = Json.decodeFromString(DigestID.serializer(), json)
        assertEquals(original, decoded)
    }

    @Test
    fun testInequality() {
        val id1 = DigestID(10u)
        val id2 = DigestID(20u)
        assertNotEquals(id1, id2)
    }
}
