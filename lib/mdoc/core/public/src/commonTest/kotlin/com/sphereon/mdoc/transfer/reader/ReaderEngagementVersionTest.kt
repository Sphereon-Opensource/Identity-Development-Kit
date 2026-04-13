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

package com.sphereon.mdoc.transfer.reader

import com.sphereon.cbor.CborString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for ReaderEngagementVersion value class.
 */
class ReaderEngagementVersionTest {
    @Test
    fun testDefaultVersion() {
        val version = ReaderEngagementVersion()
        assertEquals("1.1", version.toString())
    }

    @Test
    fun testVersion10() {
        val version = ReaderEngagementVersion("1.0")
        assertEquals("1.0", version.toString())
    }

    @Test
    fun testVersion11() {
        val version = ReaderEngagementVersion("1.1")
        assertEquals("1.1", version.toString())
    }

    @Test
    fun testInvalidVersionThrows() {
        assertFailsWith<IllegalArgumentException> {
            ReaderEngagementVersion("2.0")
        }
    }

    @Test
    fun testInvalidVersion09Throws() {
        assertFailsWith<IllegalArgumentException> {
            ReaderEngagementVersion("0.9")
        }
    }

    @Test
    fun testInvalidVersionEmptyThrows() {
        assertFailsWith<IllegalArgumentException> {
            ReaderEngagementVersion("")
        }
    }

    @Test
    fun testToCborStructure() {
        val version = ReaderEngagementVersion("1.0")
        val cbor = version.toCborItem()
        assertEquals("1.0", cbor.value)
    }

    @Test
    fun testToCborStructureDefault() {
        val version = ReaderEngagementVersion()
        val cbor = version.toCborItem()
        assertEquals("1.1", cbor.value)
    }

    @Test
    fun testFromCborStructure10() {
        val cbor = CborString("1.0")
        val version = ReaderEngagementVersion.fromCborItem(cbor)
        assertEquals("1.0", version.toString())
    }

    @Test
    fun testFromCborStructure11() {
        val cbor = CborString("1.1")
        val version = ReaderEngagementVersion.fromCborItem(cbor)
        assertEquals("1.1", version.toString())
    }

    @Test
    fun testFromCborStructureInvalidThrows() {
        val cbor = CborString("2.0")
        assertFailsWith<IllegalArgumentException> {
            ReaderEngagementVersion.fromCborItem(cbor)
        }
    }

    @Test
    fun testRoundTripConversion10() {
        val original = ReaderEngagementVersion("1.0")
        val cbor = original.toCborItem()
        val converted = ReaderEngagementVersion.fromCborItem(cbor)
        assertEquals(original, converted)
    }

    @Test
    fun testRoundTripConversion11() {
        val original = ReaderEngagementVersion("1.1")
        val cbor = original.toCborItem()
        val converted = ReaderEngagementVersion.fromCborItem(cbor)
        assertEquals(original, converted)
    }

    @Test
    fun testEquality() {
        val version1 = ReaderEngagementVersion("1.0")
        val version2 = ReaderEngagementVersion("1.0")
        assertEquals(version1, version2)
    }

    @Test
    fun testHashCodeConsistency() {
        val version1 = ReaderEngagementVersion("1.1")
        val version2 = ReaderEngagementVersion("1.1")
        assertEquals(version1.hashCode(), version2.hashCode())
    }
}
