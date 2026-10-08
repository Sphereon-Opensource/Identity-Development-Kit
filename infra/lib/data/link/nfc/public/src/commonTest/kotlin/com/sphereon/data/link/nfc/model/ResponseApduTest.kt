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

package com.sphereon.data.link.nfc.model

import com.sphereon.core.api.decodeFromHex
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.toHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class ResponseApduTest {
    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun encodeDecode() {
        val noPayload = ByteString(byteArrayOf())
        val payload = ByteString(ByteArray(0x123) { 65 })
        val payloadAsString = payload.toHexString()

        val pairs: List<Pair<String, ResponseApdu>> =
            listOf(
                Pair(
                    "9000",
                    ResponseApdu(NfcConst.RESPONSE_STATUS_SUCCESS),
                ),
                Pair(
                    "6a82",
                    ResponseApdu(0x6a82),
                ),
                Pair(
                    payloadAsString + "9000",
                    ResponseApdu(NfcConst.RESPONSE_STATUS_SUCCESS, payload),
                ),
                Pair(
                    payloadAsString + "6a82",
                    ResponseApdu(0x6a82, payload),
                ),
            )

        for ((hexEncoding, responseApdu) in pairs) {
            assertEquals(
                hexEncoding,
                responseApdu.encode().toHexString(),
                "Encoding of $responseApdu is ${responseApdu.encode()} which wasn't expected",
            )
            assertEquals(
                responseApdu,
                ResponseApdu.decode(hexEncoding.decodeFromHex()),
                "Decoding of $hexEncoding is ${ResponseApdu.decode(hexEncoding.decodeFromHex())} which wasn't expected",
            )
        }

        assertEquals(0x6a, ResponseApdu.decode("6a82".decodeFromHex()).sw1)
        assertEquals(0x82, ResponseApdu.decode("6a82".decodeFromHex()).sw2)
    }
}
