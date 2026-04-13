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
 */

package com.sphereon.mdoc.transfer.device

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceRetrievalMethodTypeTest {
    @Test
    fun retrievalMethodTypes_keep_expected_domain_values() {
        assertEquals(1u, DeviceRetrievalMethodType.NFC.type)
        assertEquals(2u, DeviceRetrievalMethodType.BLE.type)
        assertEquals(3u, DeviceRetrievalMethodType.WIFI_WARE.type)
        assertEquals(4u, DeviceRetrievalMethodType.WEBSITE.type)
        assertEquals(5u, DeviceRetrievalMethodType.OID4VP.type)
        assertEquals(5, DeviceRetrievalMethodType.entries.size)
    }

    @Test
    fun retrievalMethodType_names_and_values_are_stable() {
        assertEquals(DeviceRetrievalMethodType.BLE, DeviceRetrievalMethodType.valueOf("BLE"))
        assertEquals(
            DeviceRetrievalMethodType.entries.size,
            DeviceRetrievalMethodType.entries
                .map { it.type }
                .toSet()
                .size,
        )
    }
}
