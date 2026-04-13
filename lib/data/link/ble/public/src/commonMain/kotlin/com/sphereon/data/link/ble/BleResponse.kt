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

package com.sphereon.data.link.ble

import com.sphereon.data.link.ble.model.BleDevice
import com.sphereon.data.link.ble.model.GattCharacteristic
import com.sphereon.data.link.ble.model.GattService
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

sealed class BleResponse {
    object Success : BleResponse()

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Characteristic", exact = true)
    data class Characteristic(
        val characteristic: GattCharacteristic,
    ) : BleResponse()

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Data", exact = true)
    data class Data(
        val value: ByteArray,
    ) : BleResponse()

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Services", exact = true)
    data class Services(
        val services: List<GattService>,
    ) : BleResponse()

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Devices", exact = true)
    data class Devices(
        val devices: List<BleDevice>,
    ) : BleResponse()

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Connect", exact = true)
    data class Connect(
        val device: BleDevice,
    ) : BleResponse()
}
