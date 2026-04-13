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
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.cborSerializer
import com.sphereon.cbor.dsl.cborArrayBuilder
import com.sphereon.core.compat.JsExportCompat

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceEngagementMessage", exact = true)
data class DeviceEngagementMessage(val deviceEngagementBytes: CborEncodedItem<DeviceEngagement>) :
    CborStructure<DeviceEngagementMessage, CborMap<StringLabel, CborItem<*>>>(CDDL.list) {
    override fun cborBuilder(): CborBuilder<DeviceEngagementMessage> = cborArrayBuilder(this) {
        add(deviceEngagementBytes)
    }


    companion object {
        val DEVICE_ENGAGEMENT_BYTES = StringLabel("deviceEngagementBytes")

        fun fromCborItem(m: CborMap<StringLabel, CborItem<*>>): DeviceEngagementMessage {
            return DeviceEngagementMessage(
                deviceEngagementBytes = DEVICE_ENGAGEMENT_BYTES.required(m)
            )
        }

        fun decodeCbor(deviceEngagementSecurity: ByteArray): DeviceEngagementMessage = fromCborItem(cborSerializer.decode(deviceEngagementSecurity))
    }
}