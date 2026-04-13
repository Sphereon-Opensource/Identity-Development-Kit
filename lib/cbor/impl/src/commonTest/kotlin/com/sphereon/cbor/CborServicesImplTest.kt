/*
 * Â© 2026 Sphereon International B.V.
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

package com.sphereon.cbor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CborServicesImplTest {
    private val encoder = CborEncoderImpl()
    private val parser = CborParserImpl()
    private val diagnostics = CborDiagnosticsImpl()

    @Test
    fun encoderAndParserRoundTrip() {
        val item = CborString("phase-1")
        val encoded = encoder.encode(item)
        val decoded = parser.parse(encoded)

        assertTrue(decoded.isOk)
        assertEquals(item, decoded.value)
    }

    @Test
    fun parserCanDecodeWithOffset() {
        val first = encoder.encode(CborString("a"))
        val second = encoder.encode(CborUInt(7))
        val combined = first + second

        val decoded = parser.parseWithOffset(combined, first.size)

        assertTrue(decoded.isOk)
        assertEquals(combined.size, decoded.value.first)
        assertEquals(CborUInt(7), decoded.value.second)
    }

    @Test
    fun diagnosticsRenderEncodedBytes() {
        val encoded = encoder.encode(CborArray(mutableListOf(CborString("x"), CborUInt(1))))

        assertEquals("[\"x\", 1]", diagnostics.renderEncoded(encoded))
    }
}
