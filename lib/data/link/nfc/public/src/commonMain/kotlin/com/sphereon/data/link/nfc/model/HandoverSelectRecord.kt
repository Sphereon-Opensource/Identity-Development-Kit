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
import kotlinx.io.bytestring.ByteStringBuilder
import com.sphereon.util.appendUInt8
import com.sphereon.util.getUInt8

/**
 * Handover Select record.
 *
 * Reference: NFC Forum Connection Handover section 6.1 Handover Select Record
 *
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("HandoverSelectRecord", exact = true)
data class HandoverSelectRecord(
    val version: Int,
    val embeddedMessage: NdefMessage
) {

    fun generateNdefRecord(): NdefRecord {
        check(version < 256) { "Version must fit in one byte" }
        val bsb = ByteStringBuilder()
        bsb.appendUInt8(version)
        bsb.append(embeddedMessage.encode())
        return NdefRecord(
            tnf = NdefRecord.Tnf.WELL_KNOWN,
            type = NfcConst.RTD_HANDOVER_SELECT,
            payload = bsb.toByteString()
        )
    }

    companion object {
        fun fromNdefRecord(record: NdefRecord): HandoverSelectRecord? {
            if (record.tnf != NdefRecord.Tnf.WELL_KNOWN ||
                record.type != NfcConst.RTD_HANDOVER_SELECT) {
                return null
            }
            val version = record.payload.getUInt8(0)
            val embeddedMessage = NdefMessage.fromEncoded(record.payload.substring(1).toByteArray())
            return HandoverSelectRecord(version.toInt(), embeddedMessage)
        }
    }
}