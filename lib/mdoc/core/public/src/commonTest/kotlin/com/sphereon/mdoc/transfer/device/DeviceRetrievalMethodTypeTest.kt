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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for DeviceRetrievalMethodType enum.
 */
class DeviceRetrievalMethodTypeTest {

    @Test
    fun testNfcType() {
        val type = DeviceRetrievalMethodType.NFC
        assertEquals(1u, type.type)
        assertEquals("NFC", type.name)
    }

    @Test
    fun testBleType() {
        val type = DeviceRetrievalMethodType.BLE
        assertEquals(2u, type.type)
        assertEquals("BLE", type.name)
    }

    @Test
    fun testWifiWareType() {
        val type = DeviceRetrievalMethodType.WIFI_WARE
        assertEquals(3u, type.type)
        assertEquals("WIFI_WARE", type.name)
    }

    @Test
    fun testWebsiteType() {
        val type = DeviceRetrievalMethodType.WEBSITE
        assertEquals(4u, type.type)
        assertEquals("WEBSITE", type.name)
    }

    @Test
    fun testOid4vpType() {
        val type = DeviceRetrievalMethodType.OID4VP
        assertEquals(5u, type.type)
        assertEquals("OID4VP", type.name)
    }

    @Test
    fun testAllEntriesCount() {
        assertEquals(5, DeviceRetrievalMethodType.entries.size)
    }

    @Test
    fun testToCborItemNfc() {
        val cborItem = DeviceRetrievalMethodType.NFC.toCborItem()
        assertEquals(1L, cborItem.value)
    }

    @Test
    fun testToCborItemBle() {
        val cborItem = DeviceRetrievalMethodType.BLE.toCborItem()
        assertEquals(2L, cborItem.value)
    }

    @Test
    fun testToCborItemWifiWare() {
        val cborItem = DeviceRetrievalMethodType.WIFI_WARE.toCborItem()
        assertEquals(3L, cborItem.value)
    }

    @Test
    fun testToCborItemWebsite() {
        val cborItem = DeviceRetrievalMethodType.WEBSITE.toCborItem()
        assertEquals(4L, cborItem.value)
    }

    @Test
    fun testToCborItemOid4vp() {
        val cborItem = DeviceRetrievalMethodType.OID4VP.toCborItem()
        assertEquals(5L, cborItem.value)
    }

    @Test
    fun testFromCborStructureNfc() {
        val cbor = CborUInt(1L)
        val type = DeviceRetrievalMethodType.fromCborStructure(cbor)
        assertEquals(DeviceRetrievalMethodType.NFC, type)
    }

    @Test
    fun testFromCborStructureBle() {
        val cbor = CborUInt(2L)
        val type = DeviceRetrievalMethodType.fromCborStructure(cbor)
        assertEquals(DeviceRetrievalMethodType.BLE, type)
    }

    @Test
    fun testFromCborStructureWifiWare() {
        val cbor = CborUInt(3L)
        val type = DeviceRetrievalMethodType.fromCborStructure(cbor)
        assertEquals(DeviceRetrievalMethodType.WIFI_WARE, type)
    }

    @Test
    fun testFromCborStructureWebsite() {
        val cbor = CborUInt(4L)
        val type = DeviceRetrievalMethodType.fromCborStructure(cbor)
        assertEquals(DeviceRetrievalMethodType.WEBSITE, type)
    }

    @Test
    fun testFromCborStructureOid4vp() {
        val cbor = CborUInt(5L)
        val type = DeviceRetrievalMethodType.fromCborStructure(cbor)
        assertEquals(DeviceRetrievalMethodType.OID4VP, type)
    }

    @Test
    fun testFromCborStructureUnknownThrows() {
        val cbor = CborUInt(99L)
        assertFailsWith<NoSuchElementException> {
            DeviceRetrievalMethodType.fromCborStructure(cbor)
        }
    }

    @Test
    fun testRoundTripConversion() {
        DeviceRetrievalMethodType.entries.forEach { type ->
            val cborItem = type.toCborItem()
            val converted = DeviceRetrievalMethodType.fromCborStructure(cborItem)
            assertEquals(type, converted)
        }
    }

    @Test
    fun testValueOf() {
        assertEquals(DeviceRetrievalMethodType.NFC, DeviceRetrievalMethodType.valueOf("NFC"))
        assertEquals(DeviceRetrievalMethodType.BLE, DeviceRetrievalMethodType.valueOf("BLE"))
        assertEquals(DeviceRetrievalMethodType.WIFI_WARE, DeviceRetrievalMethodType.valueOf("WIFI_WARE"))
        assertEquals(DeviceRetrievalMethodType.WEBSITE, DeviceRetrievalMethodType.valueOf("WEBSITE"))
        assertEquals(DeviceRetrievalMethodType.OID4VP, DeviceRetrievalMethodType.valueOf("OID4VP"))
    }

    @Test
    fun testTypeValuesAreUnique() {
        val types = DeviceRetrievalMethodType.entries.map { it.type }
        assertEquals(types.size, types.toSet().size)
    }
}
