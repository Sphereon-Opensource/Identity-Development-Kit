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

package com.sphereon.mdoc.transfer.device

import com.sphereon.cbor.CborUInt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for DeviceRetrievalMethodVersion value class.
 */
class DeviceRetrievalMethodVersionTest {

    @Test
    fun testCreateVersion1() {
        val version = DeviceRetrievalMethodVersion(1u)
        assertEquals(1u, version.version)
    }

    @Test
    fun testCreateVersion2() {
        val version = DeviceRetrievalMethodVersion(2u)
        assertEquals(2u, version.version)
    }

    @Test
    fun testToCborItem() {
        val version = DeviceRetrievalMethodVersion(1u)
        val cborItem = version.toCborItem()
        assertEquals(1L, cborItem.value)
    }

    @Test
    fun testToCborItemHigherVersion() {
        val version = DeviceRetrievalMethodVersion(5u)
        val cborItem = version.toCborItem()
        assertEquals(5L, cborItem.value)
    }

    @Test
    fun testFromCborStructure() {
        val cbor = CborUInt(1L)
        val version = DeviceRetrievalMethodVersion.fromCborStructure(cbor)
        assertEquals(1u, version.version)
    }

    @Test
    fun testFromCborStructureHigherVersion() {
        val cbor = CborUInt(10L)
        val version = DeviceRetrievalMethodVersion.fromCborStructure(cbor)
        assertEquals(10u, version.version)
    }

    @Test
    fun testToString() {
        val version = DeviceRetrievalMethodVersion(1u)
        assertEquals("1", version.toString())
    }

    @Test
    fun testToStringHigherVersion() {
        val version = DeviceRetrievalMethodVersion(99u)
        assertEquals("99", version.toString())
    }

    @Test
    fun testRoundTripConversion() {
        val original = DeviceRetrievalMethodVersion(3u)
        val cborItem = original.toCborItem()
        val converted = DeviceRetrievalMethodVersion.fromCborStructure(cborItem)
        assertEquals(original, converted)
    }

    @Test
    fun testEquality() {
        val version1 = DeviceRetrievalMethodVersion(1u)
        val version2 = DeviceRetrievalMethodVersion(1u)
        assertEquals(version1, version2)
    }

    @Test
    fun testHashCodeConsistency() {
        val version1 = DeviceRetrievalMethodVersion(1u)
        val version2 = DeviceRetrievalMethodVersion(1u)
        assertEquals(version1.hashCode(), version2.hashCode())
    }

    @Test
    fun testVersionZero() {
        val version = DeviceRetrievalMethodVersion(0u)
        assertEquals(0u, version.version)
        assertEquals("0", version.toString())
    }

    @Test
    fun testLargeVersion() {
        val version = DeviceRetrievalMethodVersion(UInt.MAX_VALUE)
        assertEquals(UInt.MAX_VALUE, version.version)
    }
}
