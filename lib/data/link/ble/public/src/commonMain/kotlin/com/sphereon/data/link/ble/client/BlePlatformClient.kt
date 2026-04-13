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

package com.sphereon.data.link.ble.client

import com.sphereon.core.api.IdkResult
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.CharacteristicReadError
import com.sphereon.data.link.ble.CharacteristicWriteError
import com.sphereon.data.link.ble.ConnectionFailedError
import com.sphereon.data.link.ble.MtuChangeError
import com.sphereon.data.link.ble.NotificationFailedError
import com.sphereon.data.link.ble.ScanError
import com.sphereon.data.link.ble.client.cmd.ScanDevicesArgs
import com.sphereon.data.link.ble.model.BleDevice
import com.sphereon.data.link.ble.model.CharacteristicWriteMode
import com.sphereon.data.link.ble.model.GattCharacteristic
import com.sphereon.data.link.ble.model.GattDescriptor
import com.sphereon.data.link.ble.model.GattService
import com.sphereon.data.link.ble.model.HasUuidId
import com.sphereon.data.link.ble.model.IHasAddress
import kotlinx.coroutines.flow.SharedFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalObjCName::class)
@ObjCName("BlePlatformClient", exact = true)
interface BlePlatformClient : AutoCloseable {
    val bleEvents: SharedFlow<BleEvent>

    suspend fun scan(args: ScanDevicesArgs = ScanDevicesArgs()): IdkResult<List<BleDevice>, ScanError>

    suspend fun connect(device: IHasAddress): IdkResult<BleDevice, ConnectionFailedError>

    suspend fun disconnect(): IdkResult<Unit, BleError>

    suspend fun setMtu(mtu: Int = 515): IdkResult<Int, MtuChangeError>

    suspend fun discoverServices(): IdkResult<List<GattService>, BleError>

    suspend fun readCharacteristic(
        service: HasUuidId,
        characteristic: HasUuidId,
    ): IdkResult<GattCharacteristic, CharacteristicReadError>

    suspend fun writeCharacteristic(
        service: HasUuidId,
        characteristic: HasUuidId,
        value: ByteArray,
        writeType: CharacteristicWriteMode = CharacteristicWriteMode.WRITE_TYPE_NO_RESPONSE,
    ): IdkResult<Int, CharacteristicWriteError>

    suspend fun getDevice(): IdkResult<BleDevice, ConnectionFailedError>

    @OptIn(ExperimentalUuidApi::class)
    suspend fun enableNotifications(
        service: HasUuidId,
        characteristic: HasUuidId,
    ): IdkResult<GattDescriptor, NotificationFailedError>

    suspend fun discoverServiceCharacteristics(
        service: HasUuidId,
        characteristics: List<HasUuidId>? = null,
    ): IdkResult<List<GattCharacteristic>, CharacteristicReadError>
}
