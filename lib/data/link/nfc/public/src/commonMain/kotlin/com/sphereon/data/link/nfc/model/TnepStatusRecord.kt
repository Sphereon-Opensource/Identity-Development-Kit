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

import com.sphereon.util.getUInt8
import kotlinx.io.bytestring.ByteString
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("TnepStatusRecord", exact = true)
data class TnepStatusRecord(
    val status: Int,
) {
    fun toNdefRecord(): NdefRecord {
        check(status < MAX_BYTE_VALUE) { "Status must fit in a byte" }
        return NdefRecord(
            tnf = NdefRecord.Tnf.WELL_KNOWN,
            type = NfcConst.RTD_TNEP_STATUS,
            payload = ByteString(status.toByte()),
        )
    }

    companion object {
        private const val MAX_BYTE_VALUE = 255

        fun fromNdefRecord(record: NdefRecord): TnepStatusRecord? {
            if (record.tnf != NdefRecord.Tnf.WELL_KNOWN ||
                record.type != NfcConst.RTD_TNEP_STATUS
            ) {
                return null
            }
            require(record.payload.size == 1)
            return TnepStatusRecord(record.payload.getUInt8(0).toInt())
        }
    }
}
