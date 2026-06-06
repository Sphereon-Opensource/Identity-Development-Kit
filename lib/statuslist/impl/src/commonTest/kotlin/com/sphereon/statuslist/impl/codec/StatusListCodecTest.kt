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

package com.sphereon.statuslist.impl.codec

import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusValues
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusListCodecTest {
    @Test
    fun tokenStatusListPacksLeastSignificantBitFirst() {
        // IETF Token Status List: index 0 -> LSB (0x01) of byte 0.
        val bs = StatusBitset.create(16, 1, BitOrder.LSB_FIRST)
        bs.set(0, 1)
        assertEquals(0x01, bs.toByteArray()[0].toInt() and 0xFF)
        bs.set(2, 1)
        assertEquals(0x05, bs.toByteArray()[0].toInt() and 0xFF)
        assertEquals(1, bs.get(0))
        assertEquals(0, bs.get(1))
        assertEquals(1, bs.get(2))
    }

    @Test
    fun bitstringPacksMostSignificantBitFirst() {
        // W3C Bitstring Status List: index 0 -> MSB (0x80) of byte 0.
        val bs = StatusBitset.create(16, 1, BitOrder.MSB_FIRST)
        bs.set(0, 1)
        assertEquals(0x80, bs.toByteArray()[0].toInt() and 0xFF)
        bs.set(2, 1)
        assertEquals(0xA0, bs.toByteArray()[0].toInt() and 0xFF)
    }

    @Test
    fun multiBitValuesRoundTripInBothOrders() {
        for (order in BitOrder.entries) {
            val bs = StatusBitset.create(4, 2, order)
            bs.set(0, 1)
            bs.set(1, 2)
            bs.set(2, 3)
            bs.set(3, 0)
            assertEquals(1, bs.get(0))
            assertEquals(2, bs.get(1))
            assertEquals(3, bs.get(2))
            assertEquals(0, bs.get(3))
        }
    }

    @Test
    fun encodeDecodeRoundTripsBothSpecs() =
        runTest {
            for (spec in StatusListSpec.entries) {
                val order = StatusListCodec.bitOrderFor(spec)
                val bs = StatusBitset.create(131_072, 1, order)
                bs.set(42, 1)
                bs.set(99_999, 1)
                val encoded = StatusListCodec.encode(bs, spec)
                if (spec == StatusListSpec.BITSTRING_STATUS_LIST) {
                    assertTrue(encoded.startsWith("u"), "W3C encodedList must be multibase base64url ('u')")
                }
                val decoded = StatusListCodec.decode(encoded, 1, spec)
                assertEquals(1, decoded.get(42), "$spec")
                assertEquals(1, decoded.get(99_999), "$spec")
                assertEquals(0, decoded.get(0), "$spec")
            }
        }

    @Test
    fun tokenStatusListSuspendedMultiBitRoundTrips() =
        runTest {
            val bs = StatusBitset.create(100, 2, BitOrder.LSB_FIRST)
            bs.set(0, StatusValues.SUSPENDED)
            bs.set(5, StatusValues.INVALID)
            val encoded = StatusListCodec.encode(bs, StatusListSpec.TOKEN_STATUS_LIST)
            val decoded = StatusListCodec.decode(encoded, 2, StatusListSpec.TOKEN_STATUS_LIST)
            assertEquals(StatusValues.SUSPENDED, decoded.get(0))
            assertEquals(StatusValues.INVALID, decoded.get(5))
            assertEquals(StatusValues.VALID, decoded.get(1))
        }
}
