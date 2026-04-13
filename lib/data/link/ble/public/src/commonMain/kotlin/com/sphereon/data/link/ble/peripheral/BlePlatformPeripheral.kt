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

package com.sphereon.data.link.ble.peripheral

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.core.api.IdkResult
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.CharacteristicWriteError
import com.sphereon.data.link.ble.client.BleEvent
import com.sphereon.data.link.ble.model.GattCharacteristic
import com.sphereon.data.link.ble.model.GattService
import com.sphereon.data.link.ble.model.HasUuidId
import kotlin.uuid.Uuid

/**
 * Platform-agnostic interface for BLE peripheral operations.
 * This is the counterpart to BlePlatformClient, used for peripheral server mode
 * where the device advertises and accepts connections from central devices.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlePlatformPeripheral", exact = true)
interface BlePlatformPeripheral : BleEvent.Handlers, AutoCloseable {

    /**
     * Start advertising the BLE peripheral with the specified service UUID
     */
    suspend fun startAdvertising(serviceUuid: Uuid): IdkResult<Unit, BleError>

    /**
     * Stop advertising the BLE peripheral
     */
    suspend fun stopAdvertising(): IdkResult<Unit, BleError>

    /**
     * Add a GATT service to the peripheral
     */
    suspend fun addService(service: GattService): IdkResult<GattService, BleError>

    /**
     * Remove a GATT service from the peripheral
     */
    suspend fun removeService(service: HasUuidId): IdkResult<Unit, BleError>

    /**
     * Update the value of a characteristic and notify subscribed central devices
     */
    suspend fun notifyCharacteristicChanged(
        service: HasUuidId,
        characteristic: HasUuidId,
        value: ByteArray
    ): IdkResult<Unit, CharacteristicWriteError>

    /**
     * Read the current value of a characteristic
     */
    suspend fun readCharacteristic(
        service: HasUuidId,
        characteristic: HasUuidId
    ): IdkResult<GattCharacteristic, BleError>

    /**
     * Wait for a central device to connect to this peripheral
     */
    suspend fun awaitConnection(): IdkResult<Unit, BleError>

    /**
     * Disconnect from the connected central device
     */
    suspend fun disconnect(): IdkResult<Unit, BleError>
}
