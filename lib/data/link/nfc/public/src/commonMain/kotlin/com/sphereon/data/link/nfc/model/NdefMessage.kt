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

/**
 * An immutable NDEF message.
 *
 * @property records the records in the message.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("NdefMessage", exact = true)
data class NdefMessage(
    val records: List<NdefRecord>
) {
    /**
     * Encodes the NDEF message.
     *
     * @return the encoded message.
     */
    fun encode(): ByteArray {
        val bsb = ByteStringBuilder()
        for (idx in records.indices) {
            val record = records[idx]
            val mb = (idx == 0)                  // first record
            val me = (idx == records.size - 1)   // last record
            record.encode(bsb, mb, me)
        }
        return bsb.toByteString().toByteArray()
    }

    companion object {
        /**
         * Decodes a NDEF message.
         *
         * @param encoded the encoded messages.
         * @return the decoded message
         */
        fun fromEncoded(encoded: ByteArray): NdefMessage {
            return NdefMessage(NdefRecord.fromEncoded(encoded))
        }
    }
}

