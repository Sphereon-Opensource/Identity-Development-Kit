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

import com.sphereon.core.api.events.EventType
import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.BleErrors
import com.sphereon.data.link.ble.BleEventTypes
import com.sphereon.data.link.ble.ScanError
import com.sphereon.data.link.ble.model.BleDevice
import com.sphereon.data.link.ble.model.GattCharacteristic
import com.sphereon.data.link.ble.model.GattDescriptor
import com.sphereon.data.link.ble.model.GattService
import com.sphereon.data.link.ble.model.HasUuidId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Sealed interface for typed BLE event payloads.
 *
 * Events are distributed via [kotlinx.coroutines.flow.SharedFlow] from
 * [BlePlatformClient.bleEvents] and [com.sphereon.data.link.ble.peripheral.BlePlatformPeripheral.bleEvents].
 * Each variant can be serialized into a [JsonObject] via [toPayload] for the
 * core event system ([com.sphereon.core.events.Event.payload]).
 *
 * Subscribe to BLE events:
 * ```kotlin
 * blePlatformClient.bleEvents.collect { event ->
 *     when (event) {
 *         is BleEvent.CharacteristicChanged -> handleData(event)
 *         is BleEvent.ConnectionStateChanged -> handleConnection(event)
 *         else -> {}
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalUuidApi::class)
sealed interface BleEvent {
    val requestId: Uuid
    val deviceAddress: String
    val time: LocalDateTimeKMP

    val operationId: String get() = this::class.simpleName ?: "Unknown"

    /** The core event type corresponding to this BLE event. */
    val eventType: EventType

    /** Serialize key fields to a [JsonObject] for the core event payload. */
    fun toPayload(): JsonObject

    fun toError(
        reason: String,
        exception: Throwable? = null,
    ): BleError

    abstract class AbstractBleEvent : BleEvent {
        override fun toError(
            reason: String,
            exception: Throwable?,
        ): BleError = BleErrors.unknown(reason = reason, throwable = exception)

        override fun toString(): String = "BleEvent(requestId=$requestId, deviceAddress='$deviceAddress', operationId='$operationId', time=$time)"
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("ScanStarted", exact = true)
    data class ScanStarted(
        override val requestId: Uuid,
        override val deviceAddress: String,
    ) : AbstractBleEvent() {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override val eventType: EventType get() = BleEventTypes.SCAN_STARTED

        override fun toPayload() =
            buildJsonObject {
                put("requestId", requestId.toString())
                put("deviceAddress", deviceAddress)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ): ScanError = BleErrors.scanFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("ScanStopped", exact = true)
    data class ScanStopped(
        override val requestId: Uuid,
        override val deviceAddress: String,
    ) : AbstractBleEvent() {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override val eventType: EventType get() = BleEventTypes.SCAN_STOPPED

        override fun toPayload() =
            buildJsonObject {
                put("requestId", requestId.toString())
                put("deviceAddress", deviceAddress)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ): ScanError = BleErrors.scanFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("ScanResult", exact = true)
    data class ScanResult(
        override val requestId: Uuid,
        override val deviceAddress: String,
        val devices: Set<BleDevice>,
    ) : AbstractBleEvent() {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override val eventType: EventType get() = BleEventTypes.SCAN_RESULT

        override fun toPayload() =
            buildJsonObject {
                put("requestId", requestId.toString())
                put("deviceAddress", deviceAddress)
                put("deviceCount", devices.size)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ): ScanError = BleErrors.scanFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("DeviceFound", exact = true)
    data class DeviceFound(
        override val requestId: Uuid,
        override val deviceAddress: String,
        val device: BleDevice,
    ) : AbstractBleEvent() {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override val eventType: EventType get() = BleEventTypes.DEVICE_FOUND

        override fun toPayload() =
            buildJsonObject {
                put("requestId", requestId.toString())
                put("deviceAddress", deviceAddress)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ): ScanError = BleErrors.scanFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("ConnectionStateChanged", exact = true)
    data class ConnectionStateChanged(
        override val requestId: Uuid,
        override val deviceAddress: String,
        val newState: Int,
        val status: Int,
    ) : AbstractBleEvent() {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override val eventType: EventType get() = BleEventTypes.CONNECTION_STATE_CHANGED

        override fun toPayload() =
            buildJsonObject {
                put("requestId", requestId.toString())
                put("deviceAddress", deviceAddress)
                put("newState", newState)
                put("status", status)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ): BleError = BleErrors.connectionFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("ServicesDiscovered", exact = true)
    data class ServicesDiscovered(
        override val requestId: Uuid,
        override val deviceAddress: String,
        val services: List<GattService>,
        val status: Int,
    ) : AbstractBleEvent() {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override val eventType: EventType get() = BleEventTypes.SERVICES_DISCOVERED

        override fun toPayload() =
            buildJsonObject {
                put("requestId", requestId.toString())
                put("deviceAddress", deviceAddress)
                put("serviceCount", services.size)
                put("status", status)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ): BleError = BleErrors.serviceDiscoveryFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("MtuChanged", exact = true)
    data class MtuChanged(
        override val requestId: Uuid,
        override val deviceAddress: String,
        val mtu: Int,
        val status: Int,
    ) : AbstractBleEvent() {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override val eventType: EventType get() = BleEventTypes.MTU_CHANGED

        override fun toPayload() =
            buildJsonObject {
                put("requestId", requestId.toString())
                put("deviceAddress", deviceAddress)
                put("mtu", mtu)
                put("status", status)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ): BleError = BleErrors.mtuChangeFailed(reason = reason, throwable = exception)
    }

    sealed interface WithChars : BleEvent {
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
    ) : AbstractBleEvent(),
        WithChars {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override val eventType: EventType get() = BleEventTypes.CHARACTERISTIC_READ

        override fun toPayload() =
            buildJsonObject {
                put("requestId", requestId.toString())
                put("deviceAddress", deviceAddress)
                put("serviceId", service.id.toString())
                put("characteristicId", characteristic.id.toString())
                put("valueSize", value.size)
                put("status", status)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ): BleError = BleErrors.readCharacteristicFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("CharacteristicWrite", exact = true)
    data class CharacteristicWrite(
        override val requestId: Uuid,
        override val deviceAddress: String,
        override val service: HasUuidId,
        override val characteristic: HasUuidId,
        val status: Int,
    ) : AbstractBleEvent(),
        WithChars {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override val eventType: EventType get() = BleEventTypes.CHARACTERISTIC_WRITE

        override fun toPayload() =
            buildJsonObject {
                put("requestId", requestId.toString())
                put("deviceAddress", deviceAddress)
                put("serviceId", service.id.toString())
                put("characteristicId", characteristic.id.toString())
                put("status", status)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ): BleError = BleErrors.writeCharacteristicFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("CharacteristicChanged", exact = true)
    data class CharacteristicChanged(
        override val requestId: Uuid,
        override val deviceAddress: String,
        override val service: HasUuidId,
        override val characteristic: HasUuidId,
        val value: ByteArray,
    ) : AbstractBleEvent(),
        WithChars {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override val eventType: EventType get() = BleEventTypes.CHARACTERISTIC_CHANGED

        override fun toPayload() =
            buildJsonObject {
                put("requestId", requestId.toString())
                put("deviceAddress", deviceAddress)
                put("serviceId", service.id.toString())
                put("characteristicId", characteristic.id.toString())
                put("valueSize", value.size)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ): BleError = BleErrors.readCharacteristicFailed(reason = reason, throwable = exception)

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }

            other as CharacteristicChanged

            if (requestId != other.requestId) {
                return false
            }
            if (deviceAddress != other.deviceAddress) {
                return false
            }
            if (service != other.service) {
                return false
            }
            if (characteristic != other.characteristic) {
                return false
            }
            if (!value.contentEquals(other.value)) {
                return false
            }
            if (time != other.time) {
                return false
            }

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
    ) : AbstractBleEvent(),
        WithChars {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override val eventType: EventType get() = BleEventTypes.DESCRIPTOR_READ

        override fun toPayload() =
            buildJsonObject {
                put("requestId", requestId.toString())
                put("deviceAddress", deviceAddress)
                put("serviceId", service.id.toString())
                put("characteristicId", characteristic.id.toString())
                put("valueSize", value.size)
                put("status", status)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ): BleError = BleErrors.readDescriptorFailed(reason = reason, throwable = exception)
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
    ) : AbstractBleEvent(),
        WithChars {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override val eventType: EventType get() = BleEventTypes.DESCRIPTOR_WRITE

        override fun toPayload() =
            buildJsonObject {
                put("requestId", requestId.toString())
                put("deviceAddress", deviceAddress)
                put("serviceId", service.id.toString())
                put("characteristicId", characteristic.id.toString())
                put("descriptorId", descriptor.id.toString())
                put("status", status)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ): BleError = BleErrors.writeDescriptorFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Notification", exact = true)
    data class Notification(
        override val requestId: Uuid,
        override val deviceAddress: String,
        override val service: HasUuidId,
        override val characteristic: HasUuidId,
        val value: ByteArray,
    ) : AbstractBleEvent(),
        WithChars {
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override val eventType: EventType get() = BleEventTypes.NOTIFICATION

        override fun toPayload() =
            buildJsonObject {
                put("requestId", requestId.toString())
                put("deviceAddress", deviceAddress)
                put("serviceId", service.id.toString())
                put("characteristicId", characteristic.id.toString())
                put("valueSize", value.size)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ): BleError = BleErrors.notificationFailed(reason = reason, throwable = exception)
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Error", exact = true)
    data class Error(
        override val requestId: Uuid,
        override val deviceAddress: String,
        val message: String? = null,
        val error: BleError,
        val originalOperationId: String,
        val originalEvent: BleEvent? = null,
    ) : AbstractBleEvent(),
        BleEvent {
        override val time = DateTimeUtils.DEFAULTS.dateTimeLocal()
        override val eventType: EventType get() = originalEvent?.eventType ?: BleEventTypes.CONNECTION_STATE_CHANGED

        override fun toPayload() =
            buildJsonObject {
                put("requestId", requestId.toString())
                put("deviceAddress", deviceAddress)
                put("errorCode", error.code)
                message?.let { put("errorMessage", it) }
                put("originalOperationId", originalOperationId)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ): BleError {
            if (originalEvent != null) {
                return originalEvent.toError(reason, exception)
            }
            return BleErrors.unknown(reason = message ?: error.message.defaultMessage, throwable = error.exception)
        }

        override fun toString(): String =
            "BleErrorEvent(requestId=$requestId, deviceAddress='$deviceAddress', message='$message' operationId='$operationId', time=$time, error=$error, original=$originalEvent)"

        companion object {
            fun from(
                event: BleEvent,
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
}
