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

package com.sphereon.mdoc.transfer.reader

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.toCborUIntFromUint
import com.sphereon.cbor.toUInt
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.cose.CoseKeyCborCodec
import com.sphereon.crypto.core.cose.CoseKeyType
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ReaderEngagementSecurity", exact = true)
data class ReaderEngagementSecurity(
    val cipherSuite: UInt = 1u,
    val eReaderKeyBytes: CborEncodedItem<CoseKeyType>,
) {
    companion object {
        fun fromCborItem(
            a: CborArray<CborItem<*>>,
            coseKeyCborCodec: CoseKeyCborCodec,
        ): ReaderEngagementSecurity {
            val eReaderKeyBytes: CborEncodedItem<CoseKeyType> = a.required(1)
            val eReaderKey = coseKeyCborCodec.decode(eReaderKeyBytes.value.taggedItem.value).getOrThrow().value
            return ReaderEngagementSecurity(
                (a.required(0) as CborUInt).toUInt(),
                eReaderKeyBytes.copy(eReaderKey),
            )
        }
    }
}

internal fun ReaderEngagementSecurity.toCborItem(): CborArray<CborItem<*>> =
    CborArray(
        mutableListOf(
            cipherSuite.toCborUIntFromUint(),
            eReaderKeyBytes.value,
        ),
    )
