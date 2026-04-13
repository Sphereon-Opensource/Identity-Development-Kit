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

package com.sphereon.link.data.ble

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import com.sphereon.core.api.IdkResult
import com.sphereon.data.link.ble.CharacteristicReadError
import com.sphereon.data.link.ble.CharacteristicWriteError
import com.sphereon.data.link.ble.ConnectionFailedError
import com.sphereon.data.link.ble.IBleError
import com.sphereon.data.link.ble.MtuChangeError
import com.sphereon.data.link.ble.NotificationFailedError
import com.sphereon.data.link.ble.ScanError
import com.sphereon.data.link.ble.client.BleEvent
import com.sphereon.data.link.ble.client.BlePlatformClient
import com.sphereon.data.link.ble.client.cmd.ScanDevicesArgs
import com.sphereon.data.link.ble.model.BleDevice
import com.sphereon.data.link.ble.model.CharacteristicWriteMode
import com.sphereon.data.link.ble.model.GattCharacteristic
import com.sphereon.data.link.ble.model.GattDescriptor
import com.sphereon.data.link.ble.model.GattService
import com.sphereon.data.link.ble.model.IHasAddress
import com.sphereon.data.link.ble.model.IHasUuidId
import com.sphereon.di.app.IApp
import kotlin.uuid.ExperimentalUuidApi


@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class JsBlePlatformClient(
    val app: IApp,
) : BlePlatformClient {
//    override val eventPublisher: IBleClientEventPublisher
//        get() = TODO("Not yet implemented")

    override suspend fun scan(args: ScanDevicesArgs): IdkResult<List<BleDevice>, ScanError> = TODO("")


    override suspend fun connect(device: IHasAddress): IdkResult<BleDevice, ConnectionFailedError> = TODO("")


    override suspend fun disconnect(): IdkResult<Unit, IBleError> = TODO("")


    override suspend fun setMtu(mtu: Int): IdkResult<Int, MtuChangeError> = TODO("")


    override suspend fun discoverServices(): IdkResult<List<GattService>, IBleError> = TODO("")

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun readCharacteristic(service: IHasUuidId, characteristic: IHasUuidId): IdkResult<GattCharacteristic, CharacteristicReadError> = TODO("")

    @OptIn(ExperimentalUuidApi::class)

    override suspend fun writeCharacteristic(
        service: IHasUuidId,
        characteristic: IHasUuidId,
        value: ByteArray,
        writeType: CharacteristicWriteMode,
    ): IdkResult<Int, CharacteristicWriteError> = TODO("")


    override suspend fun getDevice(): IdkResult<BleDevice, ConnectionFailedError> = TODO("")
    override suspend fun enableNotifications(
        service: IHasUuidId,
        characteristic: IHasUuidId,
    ): IdkResult<GattDescriptor, NotificationFailedError> {
        TODO("Not yet implemented")
    }

    override suspend fun discoverServiceCharacteristics(
        service: IHasUuidId,
        characteristics: List<IHasUuidId>?,
    ): IdkResult<List<GattCharacteristic>, CharacteristicReadError> {
        TODO("Not yet implemented")
    }

    override fun getBleEventListeners(): Set<BleEvent.Listener> {
        TODO("Not yet implemented")
    }

    override fun addBleEventListener(vararg listener: BleEvent.Listener): BleEvent.Handlers {
        TODO("Not yet implemented")
    }

    override fun removeBleEventListener(listener: BleEvent.Listener): BleEvent.Handlers {
        TODO("Not yet implemented")
    }

    override fun clearBleEventListeners(): BleEvent.Handlers {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    @ContributesTo(AppScope::class)
    interface Component {
        val blePlatformClient: BlePlatformClient
    }
}
