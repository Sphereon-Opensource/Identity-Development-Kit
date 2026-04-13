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

@OptIn(ExperimentalObjCName::class)

@ObjCName("ServiceSelectRecord", exact = true)

data class ServiceSelectRecord(
    val serviceName: String
) {

    fun toNdefRecord(): NdefRecord {
        require(serviceName.length < 256) { "Service Name length must be shorter than 256" }
        val bsb = ByteStringBuilder()
        bsb.appendUInt8(serviceName.length)
        bsb.append(serviceName.encodeToByteArray())
        return NdefRecord(
            tnf = NdefRecord.Tnf.WELL_KNOWN,
            type = NfcConst.RTD_SERVICE_SELECT,
            payload = bsb.toByteString()
        )
    }

    companion object {
        fun fromNdefRecord(record: NdefRecord): ServiceSelectRecord? {
            if (record.tnf != NdefRecord.Tnf.WELL_KNOWN ||
                record.type != NfcConst.RTD_SERVICE_SELECT) {
                return null
            }

            require(record.payload.size >= 1) { "Unexpected length of Service Select Record" }
            val serviceNameLen = record.payload.getUInt8(0).toInt()
            require(record.payload.size == serviceNameLen + 1) { "Unexpected length of body in Service Select Record" }
            return ServiceSelectRecord(record.payload.toByteArray().decodeToString(1))
        }
    }
}