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

package com.sphereon.mdoc.transport.ble

import com.sphereon.core.api.IdkResult
import com.sphereon.data.link.ble.CharacteristicWriteError
import com.sphereon.data.link.ble.client.BlePlatformClient
import com.sphereon.data.link.ble.model.HasUuidId
import com.sphereon.data.link.ble.peripheral.BlePlatformPeripheral
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Central (client) mode implementation of [BleCharacteristicWriter].
 *
 * Writes to characteristics on a remote GATT server (peripheral). This is used when the
 * device is operating as a BLE central connecting to a peripheral device.
 *
 * ## Usage Example
 * ```kotlin
 * val writer = CentralCharacteristicWriter(blePlatformClient)
 * val outgoingChannel = BleOutgoingDataChannelImpl(
 *     logManager = logManager,
 *     role = MdocRole.MDOC_READER,
 *     instanceId = instanceId,
 *     characteristicWriter = writer,  // Central mode
 *     outgoingCharacteristicId = characteristics.client2Server,
 *     stateCharacteristicId = characteristics.state,
 *     serviceUuid = serviceUuid
 * )
 * ```
 *
 * @param blePlatformClient The platform-specific BLE client for performing GATT operations
 * @see PeripheralCharacteristicWriter for server mode implementation
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CentralCharacteristicWriter", exact = true)
class CentralCharacteristicWriter(
    private val blePlatformClient: BlePlatformClient,
) : BleCharacteristicWriter {
    /**
     * Writes data to a remote peripheral's characteristic using GATT write operation.
     *
     * @param service The GATT service on the remote peripheral
     * @param characteristic The characteristic to write to
     * @param value The data to write
     * @return The number of bytes written, or an error
     */
    override suspend fun writeCharacteristic(
        service: HasUuidId,
        characteristic: HasUuidId,
        value: ByteArray,
    ): IdkResult<Int, CharacteristicWriteError> = blePlatformClient.writeCharacteristic(service, characteristic, value)
}

/**
 * Peripheral (server) mode implementation of [BleCharacteristicWriter].
 *
 * Sends notifications to connected central devices. This is used when the device is
 * operating as a BLE peripheral (GATT server) that accepts connections from centrals.
 *
 * In BLE terminology, a peripheral cannot "write" to a central's characteristic. Instead,
 * it notifies centrals when its own characteristic values change. This implementation
 * adapts that notification mechanism to match the [BleCharacteristicWriter] interface.
 *
 * ## Usage Example
 * ```kotlin
 * val writer = PeripheralCharacteristicWriter(blePlatformPeripheral)
 * val outgoingChannel = BleOutgoingDataChannelImpl(
 *     logManager = logManager,
 *     role = MdocRole.MDOC,
 *     instanceId = instanceId,
 *     characteristicWriter = writer,  // Peripheral mode
 *     outgoingCharacteristicId = characteristics.server2Client,
 *     stateCharacteristicId = characteristics.state,
 *     serviceUuid = serviceUuid
 * )
 * ```
 *
 * @param blePlatformPeripheral The platform-specific BLE peripheral for managing GATT server operations
 * @see CentralCharacteristicWriter for client mode implementation
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PeripheralCharacteristicWriter", exact = true)
class PeripheralCharacteristicWriter(
    private val blePlatformPeripheral: BlePlatformPeripheral,
) : BleCharacteristicWriter {
    /**
     * Sends a notification to connected central devices about a characteristic value change.
     *
     * In peripheral mode, "writing" means notifying connected centrals that the
     * characteristic value has changed. The central devices will receive this as
     * a characteristic change notification.
     *
     * @param service The GATT service containing the characteristic
     * @param characteristic The characteristic to notify on
     * @param value The new characteristic value
     * @return 0 on success (mapped from Unit), or an error
     */
    override suspend fun writeCharacteristic(
        service: HasUuidId,
        characteristic: HasUuidId,
        value: ByteArray,
    ): IdkResult<Int, CharacteristicWriteError> {
        // In peripheral mode, "writing" means notifying the central device
        return blePlatformPeripheral
            .notifyCharacteristicChanged(service, characteristic, value)
            .map { 0 } // Map Unit to Int (0 = success) to match the interface
    }
}
