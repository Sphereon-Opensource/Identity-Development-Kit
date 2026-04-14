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

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.util.appendUInt8
import com.sphereon.util.getUInt8
import kotlinx.io.bytestring.ByteStringBuilder
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * Handover Request record.
 *
 * Reference: NFC Forum Connection Handover section 6.1 Handover Request Record
 *
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("HandoverRequestRecord", exact = true)
data class HandoverRequestRecord(
    val version: Int,
    val embeddedMessage: NdefMessage,
) {
    fun generateNdefRecord(): NdefRecord {
        check(version < MAX_BYTE_VALUE) { "Version must fit in one byte" }
        val bsb = ByteStringBuilder()
        bsb.appendUInt8(version)
        bsb.append(embeddedMessage.encode())
        return NdefRecord(
            tnf = NdefRecord.Tnf.WELL_KNOWN,
            type = NfcConst.RTD_HANDOVER_REQUEST,
            payload = bsb.toByteString(),
        )
    }

    companion object {
        private const val MAX_BYTE_VALUE = 256

        @JvmStatic
        fun fromNdefRecord(record: NdefRecord): HandoverRequestRecord? {
            if (record.tnf != NdefRecord.Tnf.WELL_KNOWN ||
                record.type != NfcConst.RTD_HANDOVER_REQUEST
            ) {
                return null
            }
            val version = record.payload.getUInt8(0)
            val embeddedMessage = NdefMessage.fromEncoded(record.payload.substring(1).toByteArray())
            return HandoverRequestRecord(version.toInt(), embeddedMessage)
        }
    }
}
