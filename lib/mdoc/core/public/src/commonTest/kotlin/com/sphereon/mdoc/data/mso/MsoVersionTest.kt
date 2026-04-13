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

import com.sphereon.cbor.CborString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for MsoVersion value class.
 */
class MsoVersionTest {

    @Test
    fun testValidVersion() {
        val version = MsoVersion("1.0")
        assertEquals("1.0", version.toString())
    }

    @Test
    fun testToCborStructure() {
        val version = MsoVersion("1.0")
        val cbor = version.toCborStructure()
        assertEquals("1.0", cbor.value)
    }

    @Test
    fun testFromCborStructure() {
        val cbor = CborString("1.0")
        val version = MsoVersion.Decoder.fromCborStructure(cbor)
        assertEquals("1.0", version.toString())
    }

    @Test
    fun testInvalidVersionThrows() {
        assertFailsWith<IllegalArgumentException> {
            MsoVersion("2.0")
        }
    }

    @Test
    fun testInvalidVersionEmptyThrows() {
        assertFailsWith<IllegalArgumentException> {
            MsoVersion("")
        }
    }

    @Test
    fun testInvalidVersionFormatThrows() {
        assertFailsWith<IllegalArgumentException> {
            MsoVersion("1.1")
        }
    }

    @Test
    fun testEquality() {
        val version1 = MsoVersion("1.0")
        val version2 = MsoVersion("1.0")
        assertEquals(version1, version2)
    }

    @Test
    fun testHashCodeConsistency() {
        val version1 = MsoVersion("1.0")
        val version2 = MsoVersion("1.0")
        assertEquals(version1.hashCode(), version2.hashCode())
    }

    @Test
    fun testToCborAndBack() {
        val original = MsoVersion("1.0")
        val cbor = original.toCborStructure()
        val decoded = MsoVersion.Decoder.fromCborStructure(cbor)
        assertEquals(original, decoded)
    }
}
