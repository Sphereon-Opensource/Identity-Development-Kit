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

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.StringLabel
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceEngagementMessage", exact = true)
data class DeviceEngagementMessage(
    val deviceEngagementBytes: CborEncodedItem<DeviceEngagement>,
) {
    companion object {
        val DEVICE_ENGAGEMENT_BYTES = StringLabel("deviceEngagementBytes")
    }
}

internal fun DeviceEngagementMessage.toCborItem(): CborMap<StringLabel, CborItem<*>> =
    CborMap(
        mutableMapOf(
            DeviceEngagementMessage.DEVICE_ENGAGEMENT_BYTES to deviceEngagementBytes,
        ),
    )
