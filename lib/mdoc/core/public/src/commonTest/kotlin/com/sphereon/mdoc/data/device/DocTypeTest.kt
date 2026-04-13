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
import kotlin.test.assertFailsWith

/**
 * Tests for DocType value class.
 */
class DocTypeTest {

    @Test
    fun testValidDocType() {
        val docType = DocType("org.iso.18013.5.1.mDL")
        assertEquals("org.iso.18013.5.1.mDL", docType.toString())
    }

    @Test
    fun testToCborStructure() {
        val docType = DocType("org.iso.18013.5.1.mDL")
        val cbor = docType.toCborStructure()
        assertEquals("org.iso.18013.5.1.mDL", cbor.value)
    }

    @Test
    fun testFromCborStructure() {
        val cbor = CborString("eu.europa.ec.eudi.pid.1")
        val docType = DocType.Decoder.fromCborStructure(cbor)
        assertEquals("eu.europa.ec.eudi.pid.1", docType.toString())
    }

    @Test
    fun testEmptyDocTypeThrows() {
        assertFailsWith<IllegalStateException> {
            DocType("")
        }
    }

    @Test
    fun testEquality() {
        val docType1 = DocType("org.iso.18013.5.1.mDL")
        val docType2 = DocType("org.iso.18013.5.1.mDL")
        assertEquals(docType1, docType2)
    }

    @Test
    fun testHashCodeConsistency() {
        val docType1 = DocType("org.iso.18013.5.1.mDL")
        val docType2 = DocType("org.iso.18013.5.1.mDL")
        assertEquals(docType1.hashCode(), docType2.hashCode())
    }

    @Test
    fun testToCborAndBack() {
        val original = DocType("custom.doctype.1")
        val cbor = original.toCborStructure()
        val decoded = DocType.Decoder.fromCborStructure(cbor)
        assertEquals(original, decoded)
    }
}
