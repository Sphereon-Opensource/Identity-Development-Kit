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

package com.sphereon.mdoc.engagement

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.cborSerializer
import com.sphereon.cbor.dsl.cborArrayBuilder
import com.sphereon.cbor.toCborUIntFromUint
import com.sphereon.cbor.toUInt
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.core.compat.JsExportCompat

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceEngagementSecurity", exact = true)
data class DeviceEngagementSecurity(val cipherSuite: UInt = 1u, val eDeviceKeyBytes: CborEncodedItem<CoseKeyType>) :
    CborStructure<DeviceEngagementSecurity, CborArray<CborItem<*>>>(CDDL.list) {
    override fun cborBuilder(): CborBuilder<DeviceEngagementSecurity> = cborArrayBuilder(this) {
        add(cipherSuite.toCborUIntFromUint())
        add(eDeviceKeyBytes.value)
    }


    companion object {
        fun fromCborItem(a: CborArray<CborItem<*>>): DeviceEngagementSecurity {
            val eDeviceKeyBytes: CborEncodedItem<CborMap<NumberLabel, CborItem<*>>> = a.required(1)
            val eDeviceKey: CoseKeyType = CoseKey.fromCborStructure(eDeviceKeyBytes.data())
            return DeviceEngagementSecurity(
                (a.required(0) as CborUInt).toUInt(), eDeviceKeyBytes.copy(eDeviceKey)
            )
        }

        fun decodeCbor(deviceEngagementSecurity: ByteArray): DeviceEngagementSecurity = fromCborItem(cborSerializer.decode(deviceEngagementSecurity))
    }
}