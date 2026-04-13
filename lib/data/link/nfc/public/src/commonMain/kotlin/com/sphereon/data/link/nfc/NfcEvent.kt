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

package com.sphereon.data.link.nfc

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.data.link.ble.BleErrors
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.ScanError
import com.sphereon.data.link.ble.model.BleDevice

import com.sphereon.data.link.ble.model.GattCharacteristic
import com.sphereon.data.link.ble.model.GattDescriptor
import com.sphereon.data.link.ble.model.GattService
import com.sphereon.data.link.ble.model.HasUuidId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
sealed interface NfcEvent {
    val requestId: Uuid
    val deviceAddress: String
    val time: LocalDateTimeKMP

    val operationId: String get() = this::class.simpleName ?: "Unknown"


    fun toError(reason: String, exception: Throwable? = null): BleError


    abstract class AbstractNfcEvent() : NfcEvent {
        override fun toError(reason: String, exception: Throwable?): BleError = BleErrors.unknown(reason = reason, throwable = exception)
        override fun toString(): String = "BleEvent(requestId=$requestId, deviceAddress='$deviceAddress', operationId='$operationId', time=$time)"
    }

    @OptIn(ExperimentalObjCName::class)

    @ObjCName("ScanStarted", exact = true)

    data class ScanStarted(
        override val requestId: Uuid, override val deviceAddress: String,
    ) : AbstractNfcEvent() {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override fun toError(reason: String, exception: Throwable?): ScanError = BleErrors.scanFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)

    @ObjCName("ScanStopped", exact = true)

    data class ScanStopped(
        override val requestId: Uuid, override val deviceAddress: String,
    ) : AbstractNfcEvent() {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override fun toError(reason: String, exception: Throwable?): ScanError = BleErrors.scanFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)

    @ObjCName("ScanResult", exact = true)

    data class ScanResult(
        override val requestId: Uuid, override val deviceAddress: String,
        val devices: Set<BleDevice>,
    ) : AbstractNfcEvent() {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override fun toError(reason: String, exception: Throwable?): ScanError = BleErrors.scanFailed(reason = reason, throwable = exception)
    }


    @OptIn(ExperimentalObjCName::class)


    @ObjCName("DeviceFound", exact = true)


    data class DeviceFound(
        override val requestId: Uuid, override val deviceAddress: String, val device: BleDevice,
    ) : AbstractNfcEvent() {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override fun toError(reason: String, exception: Throwable?): ScanError = BleErrors.scanFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)

    @ObjCName("ConnectionStateChanged", exact = true)

    data class ConnectionStateChanged(
        override val requestId: Uuid,
        override val deviceAddress: String,
        val newState: Int,
        val status: Int,
    ) : AbstractNfcEvent() {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override fun toError(reason: String, exception: Throwable?): BleError = BleErrors.connectionFailed(reason = reason, throwable = exception)

    }

    @OptIn(ExperimentalObjCName::class)

    @ObjCName("ServicesDiscovered", exact = true)

    data class ServicesDiscovered(
        override val requestId: Uuid,
        override val deviceAddress: String,
        val services: List<GattService>,
        val status: Int,
    ) : AbstractNfcEvent() {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()

        override fun toError(reason: String, exception: Throwable?): BleError = BleErrors.serviceDiscoveryFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)

    @ObjCName("MtuChanged", exact = true)

    data class MtuChanged(
        override val requestId: Uuid,
        override val deviceAddress: String,
        val mtu: Int,
        val status: Int,
    ) : AbstractNfcEvent() {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()

        override fun toError(reason: String, exception: Throwable?): BleError = BleErrors.writeCharacteristicFailed(reason = reason, throwable = exception)


    }

    sealed interface WithChars : NfcEvent {
        val service: HasUuidId
        val characteristic: HasUuidId
    }

    @OptIn(ExperimentalObjCName::class)

    @ObjCName("CharacteristicRead", exact = true)

    data class CharacteristicRead(
        override val requestId: Uuid,
        override val deviceAddress: String,
        override val service: GattService,
        override val characteristic: GattCharacteristic,
        val value: ByteArray,
        val status: Int,
    ) : AbstractNfcEvent(), WithChars {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()

        override fun toError(reason: String, exception: Throwable?): BleError = BleErrors.readCharacteristicFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)

    @ObjCName("CharacteristicWrite", exact = true)

    data class CharacteristicWrite(
        override val requestId: Uuid,
        override val deviceAddress: String,
        override val service: HasUuidId,
        override val characteristic: HasUuidId,
        val status: Int,
    ) : AbstractNfcEvent(), WithChars {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()

        override fun toError(reason: String, exception: Throwable?): BleError = BleErrors.writeCharacteristicFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)

    @ObjCName("CharacteristicChanged", exact = true)

    data class CharacteristicChanged(
        override val requestId: Uuid,
        override val deviceAddress: String,
        override val service: HasUuidId,
        override val characteristic: HasUuidId,
        val value: ByteArray,
    ) : AbstractNfcEvent(), WithChars {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()

        override fun toError(reason: String, exception: Throwable?): BleError = BleErrors.readCharacteristicFailed(reason = reason, throwable = exception)
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as CharacteristicChanged

            if (requestId != other.requestId) return false
            if (deviceAddress != other.deviceAddress) return false
            if (service != other.service) return false
            if (characteristic != other.characteristic) return false
            if (!value.contentEquals(other.value)) return false
            if (time != other.time) return false

            return true
        }

        override fun hashCode(): Int {
            var result = requestId.hashCode()
            result = 31 * result + deviceAddress.hashCode()
            result = 31 * result + service.hashCode()
            result = 31 * result + characteristic.hashCode()
            result = 31 * result + value.contentHashCode()
            result = 31 * result + time.hashCode()
            return result
        }


    }

    @OptIn(ExperimentalObjCName::class)

    @ObjCName("DescriptorRead", exact = true)

    data class DescriptorRead(
        override val requestId: Uuid,
        override val deviceAddress: String,
        override val service: GattService,
        override val characteristic: GattCharacteristic,
        val value: ByteArray,
        val status: Int,
    ) : AbstractNfcEvent(), WithChars {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()

        override fun toError(reason: String, exception: Throwable?): BleError = BleErrors.readDescriptorFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)

    @ObjCName("DescriptorWrite", exact = true)

    data class DescriptorWrite(
        override val requestId: Uuid,
        override val deviceAddress: String,
        override val service: HasUuidId,
        override val characteristic: HasUuidId,
        val descriptor: GattDescriptor,
        val status: Int,
    ) : AbstractNfcEvent(), WithChars {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()

        override fun toError(reason: String, exception: Throwable?): BleError = BleErrors.writeDescriptorFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)

    @ObjCName("Notification", exact = true)

    data class Notification(
        override val requestId: Uuid,
        override val deviceAddress: String,
        override val service: HasUuidId,
        override val characteristic: HasUuidId,
        val value: ByteArray,
    ) : AbstractNfcEvent(), WithChars {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override fun toError(reason: String, exception: Throwable?): BleError = BleErrors.notificationFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)

    @ObjCName("Error", exact = true)

    data class Error(
        override val requestId: Uuid,
        override val deviceAddress: String,
        val message: String? = null,
        val error: BleError,
        val originalOperationId: String,
        val originalEvent: NfcEvent? = null,
    ) : AbstractNfcEvent(), NfcEvent {
        override val time = DateTimeUtils.DEFAULTS.dateTimeLocal()

        override fun toError(reason: String, exception: Throwable?): BleError {
            if (originalEvent != null) {
                return originalEvent.toError(reason, exception)
            }
            return BleErrors.unknown(reason = message ?: error.message.defaultMessage, throwable = error.exception)
        }

        override fun toString(): String =
            "BleErrorEvent(requestId=$requestId, deviceAddress='$deviceAddress', message='$message' operationId='$operationId', time=$time, error=$error, original=$originalEvent)"

        companion object {
            fun from(
                event: NfcEvent,
                exception: Throwable? = null,
                error: BleError = BleErrors.unknown(reason = "Unknown error event", throwable = exception),
                message: String? = error.message.defaultMessage,
            ): Error {
                if (event is Error) {
                    return event.copy(error = error, message = message)
                }
                return Error(
                    requestId = event.requestId,
                    deviceAddress = event.deviceAddress,
                    message = message,
                    error = error,
                    originalOperationId = event.operationId,
                    originalEvent = event,
                )
            }
        }
    }

    abstract class ListenerAdapter : Listener {
        override fun onConnectionStateChanged(event: ConnectionStateChanged) {}
        override fun onServicesDiscovered(event: ServicesDiscovered) {}
        override fun onMtuChanged(event: MtuChanged) {}
        override fun onCharacteristicRead(event: CharacteristicRead) {}
        override fun onCharacteristicWrite(event: CharacteristicWrite) {}
        override fun onCharacteristicChanged(event: CharacteristicChanged) {}
        override fun onDescriptorRead(event: DescriptorRead) {}
        override fun onDescriptorWrite(event: DescriptorWrite) {}
        override fun onNotification(event: Notification) {}
        override fun onScanStarted(event: ScanStarted) {}
        override fun onScanStopped(event: ScanStopped) {}
        override fun onScanResult(event: ScanResult) {}
        override fun onDeviceFound(event: DeviceFound) {}
        override fun onBleError(event: Error) {}
    }

    @OptIn(ExperimentalObjCName::class)

    @ObjCName("Listener", exact = true)

    interface Listener {
        fun onConnectionStateChanged(event: ConnectionStateChanged)
        fun onServicesDiscovered(event: ServicesDiscovered)
        fun onMtuChanged(event: MtuChanged)
        fun onCharacteristicRead(event: CharacteristicRead)
        fun onCharacteristicWrite(event: CharacteristicWrite)

        fun onCharacteristicChanged(event: CharacteristicChanged)

        fun onDescriptorRead(event: DescriptorRead)
        fun onDescriptorWrite(event: DescriptorWrite)
        fun onNotification(event: Notification)

        fun onScanStarted(event: ScanStarted)
        fun onScanStopped(event: ScanStopped)

        fun onScanResult(event: ScanResult)
        fun onDeviceFound(event: DeviceFound)

        //        fun onBleConnectionError(event: INfcError)
        fun onBleError(event: Error)
    }


    @OptIn(ExperimentalObjCName::class)


    @ObjCName("Handlers", exact = true)


    interface Handlers {
        fun getBleEventListeners(): Set<Listener>
        fun addBleEventListener(vararg listener: Listener): Handlers
        fun removeBleEventListener(listener: Listener): Handlers
        fun clearBleEventListeners(): Handlers
    }
}
