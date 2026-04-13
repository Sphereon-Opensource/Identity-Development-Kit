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
 * Tests for NameSpace value class.
 */
class NameSpaceTest {

    @Test
    fun testValidNameSpace() {
        val ns = NameSpace("org.iso.18013.5.1")
        assertEquals("org.iso.18013.5.1", ns.toString())
    }

    @Test
    fun testToCborStructure() {
        val ns = NameSpace("org.iso.18013.5.1")
        val cbor = ns.toCborStructure()
        assertEquals("org.iso.18013.5.1", cbor.value)
    }

    @Test
    fun testFromCborStructure() {
        val cbor = CborString("eu.europa.ec.eudi.pid.1")
        val ns = NameSpace.Decoder.fromCborStructure(cbor)
        assertEquals("eu.europa.ec.eudi.pid.1", ns.toString())
    }

    @Test
    fun testEmptyNameSpaceThrows() {
        assertFailsWith<IllegalStateException> {
            NameSpace("")
        }
    }

    @Test
    fun testEquality() {
        val ns1 = NameSpace("org.iso.18013.5.1")
        val ns2 = NameSpace("org.iso.18013.5.1")
        assertEquals(ns1, ns2)
    }

    @Test
    fun testHashCodeConsistency() {
        val ns1 = NameSpace("org.iso.18013.5.1")
        val ns2 = NameSpace("org.iso.18013.5.1")
        assertEquals(ns1.hashCode(), ns2.hashCode())
    }

    @Test
    fun testToCborAndBack() {
        val original = NameSpace("custom.namespace.1")
        val cbor = original.toCborStructure()
        val decoded = NameSpace.Decoder.fromCborStructure(cbor)
        assertEquals(original, decoded)
    }
}
