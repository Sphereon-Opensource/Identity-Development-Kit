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

class DeviceRetrievalMethodVersionTest {
    @Test
    fun retrievalMethodVersion_exposes_value_and_string_form() {
        val version = DeviceRetrievalMethodVersion(3u)

        assertEquals(3u, version.version)
        assertEquals("3", version.toString())
    }

    @Test
    fun retrievalMethodVersion_keeps_value_semantics() {
        val version1 = DeviceRetrievalMethodVersion(1u)
        val version2 = DeviceRetrievalMethodVersion(1u)

        assertEquals(version1, version2)
        assertEquals(version1.hashCode(), version2.hashCode())
        assertEquals(UInt.MAX_VALUE, DeviceRetrievalMethodVersion(UInt.MAX_VALUE).version)
    }
}
