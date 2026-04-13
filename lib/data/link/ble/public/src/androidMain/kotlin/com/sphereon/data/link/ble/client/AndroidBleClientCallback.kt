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

package com.sphereon.data.link.ble.client

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import kotlinx.coroutines.flow.MutableSharedFlow
import com.sphereon.core.api.log.LogService
import com.sphereon.data.link.ble.BleErrors
import com.sphereon.data.link.ble.model.toUuidId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AndroidBleClientCallback(
    private val requestId: Uuid,
    val log: LogService,
    val internalBleEvents: MutableSharedFlow<BleEvent>
) : BluetoothGattCallback() {

    private fun internalDispatch(event: BleEvent): Boolean {
        // Check if this event's requestId matches our callback's requestId
        if (event is BleEvent.Error) {
            log.error("internalDispatch(): dispatching error event: ${event.error.message.defaultMessage}")
        } else {
            log.info("internalDispatch(): dispatching event, id: ${event.requestId}, time: ${event.time}, operationId:${event.operationId}")
        }
        return internalBleEvents.tryEmit(event)
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        val stateString = when (newState) {
            BluetoothGatt.STATE_CONNECTED -> "connected"
            BluetoothGatt.STATE_DISCONNECTED -> "disconnected"
            BluetoothGatt.STATE_CONNECTING -> "connecting"
            else -> "$newState"
        }
        log.info("onConnectionStateChange($requestId) start: $status, new state: $stateString")
        if (gatt == null) {
            internalDispatch(
                BleEvent.Error(
                    requestId = requestId, deviceAddress = "<unknown>", error = BleErrors.Companion.connectionFailed("Disconnected"), originalOperationId = "ConnectionState"
                )
            )
            return
        }
        val device = gatt.device
        val event = BleEvent.ConnectionStateChanged(
            requestId = requestId, deviceAddress = device.address, status = status, newState = newState
        )
        if (status == BluetoothGatt.GATT_SUCCESS) {
            log.info("onConnectionStateChange($requestId) complete: new state: $stateString")
            internalDispatch(event)
        } else {
            log.error("onConnectionStateChange($requestId) failed: status: $status, newState: $stateString")
            internalDispatch(BleEvent.Error.from(event = event, message = "MTU change failed"))
        }

        internalDispatch(event)
    }


    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        log.info("#########################################################")
        log.info("#########################################################")
        log.info("onMtuChange($requestId) start mtu: $mtu, status: $status")
        val addr = gatt.device.address
        val event = BleEvent.MtuChanged(requestId, addr, mtu, status)


        if (status == BluetoothGatt.GATT_SUCCESS) {
            log.info("onMtuChanged($requestId) complete: mtu $mtu")
            internalDispatch(event)
        } else {
            log.error("onMtuChanged($requestId) failed: $mtu, $status")
            internalDispatch(BleEvent.Error.from(event = event, message = "MTU change failed"))
        }
        log.info("#########################################################")
        log.info("#########################################################")
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        log.info("onServicesDiscovered($requestId) start: $status")
        val addr = gatt.device.address
        val services = gatt.services.map { it.toGattService() }
        val event = BleEvent.ServicesDiscovered(requestId, addr, services = services, status = status)
        if (status == BluetoothGatt.GATT_SUCCESS) {
            log.info("onServicesDiscovered($requestId) complete: ${services.joinToString { it.id.toString() }}}")
            internalDispatch(event)
        } else {
            log.error("onServicesDiscovered($requestId) failed: $status")
            internalDispatch(BleEvent.Error.from(event = event, message = "Service discovery failed"))
        }
    }


    @Deprecated("Deprecated in Java")
    override fun onCharacteristicRead(gatt: BluetoothGatt?, bluetoothGattCharacteristic: BluetoothGattCharacteristic?, status: Int) {
        log.info("onCharacteristicRead($requestId) start: $status")
        if (gatt == null || bluetoothGattCharacteristic == null) {
            internalDispatch(
                BleEvent.Error(
                    requestId = requestId, deviceAddress = "<unknown>", error = BleErrors.Companion.connectionFailed("Disconnected"), originalOperationId = "CharacteristicRead"
                )
            )
            return
        }
        val address = gatt.device.address
        val characteristic = bluetoothGattCharacteristic.toGattCharacteristic()
        val service = bluetoothGattCharacteristic.service.toGattService()
        val event = BleEvent.CharacteristicRead(
            requestId = requestId, deviceAddress = address, service = service, characteristic = characteristic, value = bluetoothGattCharacteristic.value, status = status
        )
        if (status == BluetoothGatt.GATT_SUCCESS) {
            log.info("onCharacteristicRead($requestId) complete: $characteristic")
            internalDispatch(event)
        } else {
            log.error("onCharacteristicRead($requestId) failed: $status")
            internalDispatch(BleEvent.Error.from(event = event, message = "Characteristic read failed"))
        }
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        log.info("onCharacteristicWrite($requestId) start: $status, characteristic size: ${characteristic?.value?.size ?: 0}")
        if (gatt == null || characteristic == null) {
            internalDispatch(
                BleEvent.Error(
                    requestId = requestId,
                    deviceAddress = "<unknown>",
                    error = BleErrors.Companion.writeCharacteristicFailed("No gatt or characteristic found"),
                    originalOperationId = "CharacteristicWrite"
                )
            )
            return
        }

        val address = gatt.device.address
        val service = characteristic.service.toGattService()
        val event = BleEvent.CharacteristicWrite(
            requestId = requestId, deviceAddress = address, service = service, characteristic = characteristic.toGattCharacteristic(), status = status
        )
        if (status == BluetoothGatt.GATT_SUCCESS) {
            log.info("onCharacteristicWrite($requestId) complete: $characteristic")
            internalDispatch(event)
        } else {
            log.error("onCharacteristicWrite($requestId) failed: $status")
            internalDispatch(BleEvent.Error.from(event = event, message = "Characteristic write failed"))
        }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
        log.info("onDescriptorWrite($requestId) descriptor: ${descriptor?.uuid}, status $status")

        if (gatt == null || descriptor == null) {
            internalDispatch(
                BleEvent.Error(
                    requestId = requestId,
                    deviceAddress = "<unknown>",
                    error = BleErrors.Companion.writeDescriptorFailed("No gatt or descriptor found"),
                    originalOperationId = "DescriptorWrite"
                )
            )
            return
        }
        val address = gatt.device.address
        val characteristic = descriptor.characteristic.toGattCharacteristic()
        val service = characteristic.service?.id?.toUuidId() ?: Uuid.Companion.NIL.toUuidId()
        val event = BleEvent.DescriptorWrite(
            requestId = requestId, deviceAddress = address, service = service, characteristic = characteristic, descriptor = descriptor.toGattDescriptor(), status = status
        )


        if (status == BluetoothGatt.GATT_SUCCESS) {
            log.info("onDescriptorWrite($requestId) complete, $status")
            internalDispatch(event)
        } else {
            log.error("onDescriptorWrite($requestId) failed, $status")
            internalDispatch(BleEvent.Error.from(event = event, message = "MTU change failed"))
        }
    }

    override fun onServiceChanged(gatt: BluetoothGatt) {
        log.info("onServiceChanged($requestId) NO-OP: ${gatt}")
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        return onCharacteristicChanged(gatt, characteristic, characteristic.value)
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        log.info("onCharacteristicChanged($requestId) start: $characteristic")
        internalDispatch(
            BleEvent.CharacteristicChanged(
                requestId = requestId,
                deviceAddress = gatt.device.address,
                service = characteristic.service.toGattService(),
                characteristic = characteristic.toGattCharacteristic(),
                value = value
            )
        )
        log.info("onCharacteristicChanged($requestId) complete: $characteristic")
    }

    override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int, value: ByteArray) {
        log.info("onDescriptorRead($requestId)  status: $status, value: size(${value.size})")
    }


    override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
        log.info("onReliableWriteCompleted($requestId) status: $status")
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
        log.info("onReadRemoteRssi($requestId) rssi: $rssi, status: $status")
    }

    override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        log.info("onPhyRead($requestId) txPhy: $txPhy, rxPhy: $rxPhy, status: $status")
    }

    override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        log.info("onPhyUpdate($requestId) txPhy: $txPhy, rxPhy: $rxPhy, status: $status")
    }
}