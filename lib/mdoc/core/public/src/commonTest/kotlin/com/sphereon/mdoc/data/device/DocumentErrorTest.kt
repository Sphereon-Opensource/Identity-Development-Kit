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

import com.sphereon.cbor.CborInt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for DocumentError value class.
 */
class DocumentErrorTest {

    @Test
    fun testDocumentErrorCreation() {
        val error = DocumentError(10)
        assertEquals(10, error.errorCode)
    }

    @Test
    fun testDocumentErrorToString() {
        val error = DocumentError(42)
        assertEquals("42", error.toString())
    }

    @Test
    fun testToCborStructure() {
        val error = DocumentError(100)
        val cbor = error.toCborStructure()
        assertEquals(100L, cbor.value)
    }

    @Test
    fun testFromCborStructure() {
        val cbor = CborInt(55L)
        val error = DocumentError.Decoder.fromCborStructure(cbor)
        assertEquals(55, error.errorCode)
    }

    @Test
    fun testNegativeErrorCode() {
        val error = DocumentError(-1)
        assertEquals(-1, error.errorCode)
        assertEquals("-1", error.toString())
    }

    @Test
    fun testZeroErrorCode() {
        val error = DocumentError(0)
        assertEquals(0, error.errorCode)
        assertEquals("0", error.toString())
    }

    @Test
    fun testEquality() {
        val error1 = DocumentError(10)
        val error2 = DocumentError(10)
        assertEquals(error1, error2)
    }

    @Test
    fun testHashCodeConsistency() {
        val error1 = DocumentError(42)
        val error2 = DocumentError(42)
        assertEquals(error1.hashCode(), error2.hashCode())
    }

    @Test
    fun testToCborAndBack() {
        val original = DocumentError(99)
        val cbor = original.toCborStructure()
        val decoded = DocumentError.Decoder.fromCborStructure(cbor)
        assertEquals(original, decoded)
    }
}
