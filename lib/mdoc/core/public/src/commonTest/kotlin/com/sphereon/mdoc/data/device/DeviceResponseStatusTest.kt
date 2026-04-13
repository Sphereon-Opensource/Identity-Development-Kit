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

import com.sphereon.cbor.CborUInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for DeviceResponseStatus value class.
 */
class DeviceResponseStatusTest {

    @Test
    fun testValidStatus0() {
        val status = DeviceResponseStatus(0u)
        assertEquals(0u, status.value)
        assertEquals("0", status.toString())
    }

    @Test
    fun testValidStatus11() {
        val status = DeviceResponseStatus(11u)
        assertEquals(11u, status.value)
        assertEquals("11", status.toString())
    }

    @Test
    fun testValidStatus12() {
        val status = DeviceResponseStatus(12u)
        assertEquals(12u, status.value)
        assertEquals("12", status.toString())
    }

    @Test
    fun testValidStatus20() {
        val status = DeviceResponseStatus(20u)
        assertEquals(20u, status.value)
        assertEquals("20", status.toString())
    }

    @Test
    fun testToCborStructure() {
        val status = DeviceResponseStatus(0u)
        val cbor = status.toCborStructure()
        assertEquals(0L, cbor.value)
    }

    @Test
    fun testFromCborStructure() {
        val cbor = CborUInt(11L)
        val status = DeviceResponseStatus.Decoder.fromCborStructure(cbor)
        assertEquals(11u, status.value)
    }

    @Test
    fun testInvalidStatusThrows() {
        assertFailsWith<IllegalArgumentException> {
            DeviceResponseStatus(1u)
        }
    }

    @Test
    fun testInvalidStatus5Throws() {
        assertFailsWith<IllegalArgumentException> {
            DeviceResponseStatus(5u)
        }
    }

    @Test
    fun testInvalidStatus100Throws() {
        assertFailsWith<IllegalArgumentException> {
            DeviceResponseStatus(100u)
        }
    }

    @Test
    fun testEquality() {
        val status1 = DeviceResponseStatus(0u)
        val status2 = DeviceResponseStatus(0u)
        assertEquals(status1, status2)
    }

    @Test
    fun testHashCodeConsistency() {
        val status1 = DeviceResponseStatus(11u)
        val status2 = DeviceResponseStatus(11u)
        assertEquals(status1.hashCode(), status2.hashCode())
    }
}
