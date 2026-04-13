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

import com.sphereon.util.ByteDataReader
import com.sphereon.util.appendUInt16
import com.sphereon.util.appendUInt8
import com.sphereon.util.getUInt16
import com.sphereon.util.getUInt8
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.append
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * A Command APDU according to ISO/IEC 7816.
 *
 * @property cla Command class byte.
 * @property ins Instruction byte.
 * @property p1 Parameter byte 1.
 * @property p2 Parameter byte 2.
 * @property payload Payload.
 * @property le Maximum length of response data field.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CommandApdu", exact = true)
data class CommandApdu(
    val cla: Int,
    val ins: Int,
    val p1: Int,
    val p2: Int,
    val payload: ByteString,
    val le: Int,
) {
    /**
     * Encodes the APDU as bytes.
     *
     * @return the bytes of the APDU.
     */
    fun encode(): ByteArray {
        check(payload.size < MAX_EXTENDED_LENGTH)

        val bsb = ByteStringBuilder()
        bsb.appendUInt8(cla)
        bsb.appendUInt8(ins)
        bsb.appendUInt8(p1)
        bsb.appendUInt8(p2)
        var lcPresent = false

        // Lc and Le must use the same encoding, either short or long
        val useShortEncoding = payload.size < SHORT_LENGTH_LIMIT && le <= SHORT_LENGTH_LIMIT

        if (payload.size > 0) {
            lcPresent = true
            if (useShortEncoding) {
                bsb.appendUInt8(payload.size)
            } else {
                bsb.appendUInt8(0u)
                bsb.appendUInt16(payload.size)
            }
            bsb.append(payload)
        }
        if (le > 0) {
            if (useShortEncoding) {
                if (le == SHORT_LENGTH_LIMIT) {
                    bsb.appendUInt8(0u)
                } else {
                    bsb.appendUInt8(le)
                }
            } else {
                if (!lcPresent) {
                    bsb.appendUInt8(0u)
                }
                if (le < MAX_EXTENDED_LENGTH) {
                    bsb.appendUInt16(le)
                } else if (le == MAX_EXTENDED_LENGTH) {
                    bsb.appendUInt16(0u)
                } else {
                    error("invalid LE size $le")
                }
            }
        }
        return bsb.toByteString().toByteArray()
    }

    companion object {
        private const val SHORT_LENGTH_LIMIT = 0x100
        private const val MAX_EXTENDED_LENGTH = 0x10000
        private const val APDU_HEADER_SIZE = 4
        private const val APDU_SHORT_HEADER_SIZE = 5
        private const val APDU_EXTENDED_LC_END = 7

        /**
         * Decodes an APDU.
         *
         * @param encoded the bytes of the APDU
         * @return an object with the decoded fields.
         */
        @OptIn(ExperimentalStdlibApi::class)
        fun decode(encoded: ByteArray): CommandApdu {
            require(encoded.size >= APDU_HEADER_SIZE)
            val reader = ByteDataReader(encoded)
            val cla = reader.getUInt8()
            val ins = reader.getUInt8()
            val p1 = reader.getUInt8()
            val p2 = reader.getUInt8()
            var payload = ByteString(byteArrayOf())
            var lc = 0
            var le = 0
            if (encoded.size == APDU_SHORT_HEADER_SIZE) {
                val encLe = reader.getUInt8().toInt()
                le =
                    if (encLe == 0) {
                        SHORT_LENGTH_LIMIT
                    } else {
                        encLe
                    }
            } else if (encoded.size > APDU_SHORT_HEADER_SIZE) {
                lc = reader.getUInt8().toInt()
                var lcEndsAt = APDU_SHORT_HEADER_SIZE
                if (lc == 0) {
                    lc = reader.getUInt16().toInt()
                    lcEndsAt = APDU_EXTENDED_LC_END
                }
                if (lc > 0 && lcEndsAt + lc <= encoded.size) {
                    val payloadArray = ByteArray(lc)
                    encoded.copyInto(payloadArray, 0, lcEndsAt, lcEndsAt + lc)
                    payload = ByteString(payloadArray)
                } else {
                    lc = 0
                    lcEndsAt = APDU_HEADER_SIZE
                }
                val leLen = encoded.size - lcEndsAt - lc
                le =
                    when (leLen) {
                        0 -> {
                            0
                        }

                        1 -> {
                            val encLe = encoded.getUInt8(encoded.size - 1).toInt()
                            if (encLe == 0x00) {
                                SHORT_LENGTH_LIMIT
                            } else {
                                encLe
                            }
                        }

                        2, 3 -> {
                            val encLe = encoded.getUInt16(encoded.size - 2).toInt()
                            if (encLe == 0x00) {
                                MAX_EXTENDED_LENGTH
                            } else {
                                encLe
                            }
                        }

                        else -> {
                            error("Invalid LE len $leLen")
                        }
                    }
            }
            return CommandApdu(
                cla = cla.toInt(),
                ins = ins.toInt(),
                p1 = p1.toInt(),
                p2 = p2.toInt(),
                payload = payload,
                le = le,
            )
        }
    }
}
