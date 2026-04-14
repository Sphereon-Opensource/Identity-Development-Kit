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

package com.sphereon.mdoc.engagement

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
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceEngagementSecurity", exact = true)
data class DeviceEngagementSecurity(
    val cipherSuite: UInt = 1u,
    val eDeviceKeyBytes: CborEncodedItem<CoseKeyType>,
) {
    companion object {
        @JvmStatic
        fun fromCborItem(
            a: CborArray<CborItem<*>>,
            coseKeyCborCodec: CoseKeyCborCodec,
        ): DeviceEngagementSecurity {
            val eDeviceKeyBytes: CborEncodedItem<CoseKeyType> = a.required(1)
            val eDeviceKey = coseKeyCborCodec.decode(eDeviceKeyBytes.value.taggedItem.value).getOrThrow().value
            return DeviceEngagementSecurity(
                (a.required(0) as CborUInt).toUInt(),
                eDeviceKeyBytes.copy(eDeviceKey),
            )
        }
    }
}

internal fun DeviceEngagementSecurity.toCborItem(): CborArray<CborItem<*>> =
    CborArray(
        mutableListOf(
            cipherSuite.toCborUIntFromUint(),
            eDeviceKeyBytes.value,
        ),
    )
