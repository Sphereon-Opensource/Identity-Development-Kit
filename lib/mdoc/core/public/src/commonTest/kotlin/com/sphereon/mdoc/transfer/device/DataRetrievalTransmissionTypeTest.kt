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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for DataRetrievalTransmissionType enum.
 */
class DataRetrievalTransmissionTypeTest {

    @Test
    fun testNfcType() {
        val type = DataRetrievalTransmissionType.NFC
        assertEquals("NFC", type.name)
    }

    @Test
    fun testBleType() {
        val type = DataRetrievalTransmissionType.BLE
        assertEquals("BLE", type.name)
    }

    @Test
    fun testWifiAwareType() {
        val type = DataRetrievalTransmissionType.WIFI_AWARE
        assertEquals("WIFI_AWARE", type.name)
    }

    @Test
    fun testWebsiteType() {
        val type = DataRetrievalTransmissionType.WEBSITE
        assertEquals("WEBSITE", type.name)
    }

    @Test
    fun testAllEntriesCount() {
        assertEquals(4, DataRetrievalTransmissionType.entries.size)
    }

    @Test
    fun testFromStringBle() {
        assertEquals(DataRetrievalTransmissionType.BLE, DataRetrievalTransmissionType.fromString("ble"))
        assertEquals(DataRetrievalTransmissionType.BLE, DataRetrievalTransmissionType.fromString("BLE"))
        assertEquals(DataRetrievalTransmissionType.BLE, DataRetrievalTransmissionType.fromString("Ble"))
    }

    @Test
    fun testFromStringNfc() {
        assertEquals(DataRetrievalTransmissionType.NFC, DataRetrievalTransmissionType.fromString("nfc"))
        assertEquals(DataRetrievalTransmissionType.NFC, DataRetrievalTransmissionType.fromString("NFC"))
        assertEquals(DataRetrievalTransmissionType.NFC, DataRetrievalTransmissionType.fromString("Nfc"))
    }

    @Test
    fun testFromStringWifiAware() {
        assertEquals(DataRetrievalTransmissionType.WIFI_AWARE, DataRetrievalTransmissionType.fromString("wifi_aware"))
        assertEquals(DataRetrievalTransmissionType.WIFI_AWARE, DataRetrievalTransmissionType.fromString("WIFI_AWARE"))
        assertEquals(DataRetrievalTransmissionType.WIFI_AWARE, DataRetrievalTransmissionType.fromString("Wifi_Aware"))
    }

    @Test
    fun testFromStringWebsite() {
        assertEquals(DataRetrievalTransmissionType.WEBSITE, DataRetrievalTransmissionType.fromString("website"))
        assertEquals(DataRetrievalTransmissionType.WEBSITE, DataRetrievalTransmissionType.fromString("WEBSITE"))
        assertEquals(DataRetrievalTransmissionType.WEBSITE, DataRetrievalTransmissionType.fromString("Website"))
    }

    @Test
    fun testFromStringRestApiMapsToWebsite() {
        assertEquals(DataRetrievalTransmissionType.WEBSITE, DataRetrievalTransmissionType.fromString("rest_api"))
        assertEquals(DataRetrievalTransmissionType.WEBSITE, DataRetrievalTransmissionType.fromString("REST_API"))
        assertEquals(DataRetrievalTransmissionType.WEBSITE, DataRetrievalTransmissionType.fromString("Rest_Api"))
    }

    @Test
    fun testFromStringRestapiMapsToWebsite() {
        assertEquals(DataRetrievalTransmissionType.WEBSITE, DataRetrievalTransmissionType.fromString("restapi"))
        assertEquals(DataRetrievalTransmissionType.WEBSITE, DataRetrievalTransmissionType.fromString("RESTAPI"))
        assertEquals(DataRetrievalTransmissionType.WEBSITE, DataRetrievalTransmissionType.fromString("RestApi"))
    }

    @Test
    fun testFromStringUnknownReturnsNull() {
        assertNull(DataRetrievalTransmissionType.fromString("unknown"))
        assertNull(DataRetrievalTransmissionType.fromString(""))
        assertNull(DataRetrievalTransmissionType.fromString("invalid"))
        assertNull(DataRetrievalTransmissionType.fromString("bluetooth"))
    }

    @Test
    fun testValueOf() {
        assertEquals(DataRetrievalTransmissionType.NFC, DataRetrievalTransmissionType.valueOf("NFC"))
        assertEquals(DataRetrievalTransmissionType.BLE, DataRetrievalTransmissionType.valueOf("BLE"))
        assertEquals(DataRetrievalTransmissionType.WIFI_AWARE, DataRetrievalTransmissionType.valueOf("WIFI_AWARE"))
        assertEquals(DataRetrievalTransmissionType.WEBSITE, DataRetrievalTransmissionType.valueOf("WEBSITE"))
    }

    @Test
    fun testOrdinalValues() {
        assertEquals(0, DataRetrievalTransmissionType.NFC.ordinal)
        assertEquals(1, DataRetrievalTransmissionType.BLE.ordinal)
        assertEquals(2, DataRetrievalTransmissionType.WIFI_AWARE.ordinal)
        assertEquals(3, DataRetrievalTransmissionType.WEBSITE.ordinal)
    }

    @Test
    fun testAllEntriesPresent() {
        val entries = DataRetrievalTransmissionType.entries
        assertTrue(entries.contains(DataRetrievalTransmissionType.NFC))
        assertTrue(entries.contains(DataRetrievalTransmissionType.BLE))
        assertTrue(entries.contains(DataRetrievalTransmissionType.WIFI_AWARE))
        assertTrue(entries.contains(DataRetrievalTransmissionType.WEBSITE))
    }
}
