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

package com.sphereon.data.link.nfc.model

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.buildByteString
import com.sphereon.core.api.encodeToHex
import com.sphereon.util.ByteDataReader
import com.sphereon.util.appendByteString
import com.sphereon.util.appendUInt8

/**
 * A response APDU according to ISO/IEC 7816-4.
 *
 * @property status the status word.
 * @property payload the payload.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResponseApdu", exact = true)
data class ResponseApdu(
    val status: Int,
    val payload: ByteString = ByteString(),
) {
    /**
     * The upper byte of [status].
     */
    val sw1: Int
        get() = status.and(0xff00).shr(8)

    /**
     * The lower byte of [status].
     */
    val sw2: Int
        get() = status.and(0xff)

    /**
     * Gets the status as a hexadecimal string.
     */
    val statusHexString: String
        get() = byteArrayOf(sw1.toByte(), sw2.toByte()).encodeToHex()

    /**
     * Encodes the APDU as bytes.
     *
     * @return the bytes of the APDU.
     */
    fun encode(): ByteArray {
        return buildByteString {
            appendByteString(payload)
            appendUInt8(sw1)
            appendUInt8(sw2)
        }.toByteArray()
    }

    companion object {
        /**
         * Decodes an APDU.
         *
         * @param encoded the bytes of the APDU
         * @return an object with the decoded fields.
         */
        fun decode(encoded: ByteArray): ResponseApdu {
            require(encoded.size >= 2)
            val reader = ByteDataReader(encoded)
            val payload = reader.getByteString(encoded.size - 2)
            val sw1 = reader.getUInt8().toInt()
            val sw2 = reader.getUInt8().toInt()
            val status = sw1.shl(8) + sw2
            return ResponseApdu(status, payload)
        }
    }
}