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

package com.sphereon.mdoc.data.device

import com.sphereon.cbor.CborString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for DataElementIdentifier value class.
 */
class DataElementIdentifierTest {

    @Test
    fun testValidDataElementIdentifier() {
        val id = DataElementIdentifier("given_name")
        assertEquals("given_name", id.toString())
    }

    @Test
    fun testToCborStructure() {
        val id = DataElementIdentifier("family_name")
        val cbor = id.toCborStructure()
        assertEquals("family_name", cbor.value)
    }

    @Test
    fun testFromCborStructure() {
        val cbor = CborString("birth_date")
        val id = DataElementIdentifier.Decoder.fromCborStructure(cbor)
        assertEquals("birth_date", id.toString())
    }

    @Test
    fun testEquality() {
        val id1 = DataElementIdentifier("portrait")
        val id2 = DataElementIdentifier("portrait")
        assertEquals(id1, id2)
    }

    @Test
    fun testHashCodeConsistency() {
        val id1 = DataElementIdentifier("document_number")
        val id2 = DataElementIdentifier("document_number")
        assertEquals(id1.hashCode(), id2.hashCode())
    }

    @Test
    fun testToCborAndBack() {
        val original = DataElementIdentifier("driving_privileges")
        val cbor = original.toCborStructure()
        val decoded = DataElementIdentifier.Decoder.fromCborStructure(cbor)
        assertEquals(original, decoded)
    }

    @Test
    fun testSpecialCharacters() {
        val id = DataElementIdentifier("name_with_underscore")
        assertEquals("name_with_underscore", id.toString())
    }

    @Test
    fun testNumericIdentifier() {
        val id = DataElementIdentifier("123")
        assertEquals("123", id.toString())
    }
}
