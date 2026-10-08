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

package com.sphereon.mdoc.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for TransportType enum.
 */
class TransportTypeTest {
    @Test
    fun testBleTransportType() {
        val type = TransportType.BLE
        assertEquals("BLE", type.name)
    }

    @Test
    fun testNfcTransportType() {
        val type = TransportType.NFC
        assertEquals("NFC", type.name)
    }

    @Test
    fun testRestApiTransportType() {
        val type = TransportType.REST_API
        assertEquals("REST_API", type.name)
    }

    @Test
    fun testOid4vpTransportType() {
        val type = TransportType.OID4VP
        assertEquals("OID4VP", type.name)
    }

    @Test
    fun testWifiAwareTransportType() {
        val type = TransportType.WIFI_AWARE
        assertEquals("WIFI_AWARE", type.name)
    }

    @Test
    fun testAllEntriesCount() {
        assertEquals(5, TransportType.entries.size)
    }

    @Test
    fun testAllEntriesPresent() {
        val entries = TransportType.entries
        assertTrue(entries.contains(TransportType.BLE))
        assertTrue(entries.contains(TransportType.NFC))
        assertTrue(entries.contains(TransportType.REST_API))
        assertTrue(entries.contains(TransportType.OID4VP))
        assertTrue(entries.contains(TransportType.WIFI_AWARE))
    }

    @Test
    fun testValueOf() {
        assertEquals(TransportType.BLE, TransportType.valueOf("BLE"))
        assertEquals(TransportType.NFC, TransportType.valueOf("NFC"))
        assertEquals(TransportType.REST_API, TransportType.valueOf("REST_API"))
        assertEquals(TransportType.OID4VP, TransportType.valueOf("OID4VP"))
        assertEquals(TransportType.WIFI_AWARE, TransportType.valueOf("WIFI_AWARE"))
    }

    @Test
    fun testOrdinalValues() {
        assertEquals(0, TransportType.BLE.ordinal)
        assertEquals(1, TransportType.NFC.ordinal)
        assertEquals(2, TransportType.REST_API.ordinal)
        assertEquals(3, TransportType.OID4VP.ordinal)
        assertEquals(4, TransportType.WIFI_AWARE.ordinal)
    }
}
