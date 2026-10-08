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

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.link.ble

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalObjCName::class)
@ObjCName("BleError", exact = true)
interface BleError : IdkErrorType

@JsExportCompat
sealed class BleErrors(
    override val code: String,
    override val severity: Severity,
    override val message: Message,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = mutableListOf(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = mutableMapOf(),
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
) : IdkError(code = code, severity = severity, message = message, causes = causes, meta = meta, exception = exception),
    BleError {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun unknown(
            reason: String = "An unknown BLE error occurred",
            throwable: Throwable? = null,
            causes: List<IdkErrorType> = emptyList(),
        ) = UnknownBleError(
            message =
                Message(
                    i18nKey = "ble.error.unknown",
                    defaultMessage = reason,
                ),
            exception = throwable,
            causes = causes,
        )

        @JvmStatic
        @JvmOverloads
        fun scanFailed(
            reason: String = "Scanning failed",
            throwable: Throwable? = null,
            causes: List<IdkErrorType> = emptyList(),
        ) = ScanError(
            message =
                Message(
                    i18nKey = "ble.error.scan-failed",
                    defaultMessage = reason,
                ),
            exception = throwable,
            causes = causes,
        )

        @JvmStatic
        fun deviceNotFound(deviceAddress: String) =
            DeviceNotFoundError(
                message =
                    Message(
                        i18nKey = "ble.error.device-not-found",
                        defaultMessage = "Device with id $deviceAddress not found",
                    ),
            )

        @JvmStatic
        @JvmOverloads
        fun notificationFailed(
            reason: String,
            throwable: Throwable? = null,
        ) = NotificationFailedError(
            message =
                Message(
                    i18nKey = "ble.error.notification-failed",
                    defaultMessage = reason,
                ),
            exception = throwable,
        )

        @JvmStatic
        @JvmOverloads
        fun serviceDiscoveryFailed(
            reason: String,
            throwable: Throwable? = null,
        ) = ServiceDiscoveryError(
            message =
                Message(
                    i18nKey = "ble.error.service-discovery-failed",
                    defaultMessage = reason,
                ),
            exception = throwable,
        )

        @JvmStatic
        fun notSupported(reason: String) =
            NotSupportedBleError(
                message =
                    Message(
                        i18nKey = "ble.error.not-supported",
                        defaultMessage = reason,
                    ),
            )

        @JvmStatic
        @JvmOverloads
        fun connectionFailed(
            reason: String,
            throwable: Throwable? = null,
        ) = ConnectionFailedError(
            message =
                Message(
                    i18nKey = "ble.error.connection-failed",
                    defaultMessage = reason,
                ),
            exception = throwable,
        )

        @JvmStatic
        @JvmOverloads
        fun readCharacteristicFailed(
            reason: String,
            throwable: Throwable? = null,
        ) = CharacteristicReadError(
            message =
                Message(
                    i18nKey = "ble.error.read-characteristic",
                    defaultMessage = reason,
                ),
            exception = throwable,
        )

        @JvmStatic
        @JvmOverloads
        fun writeCharacteristicFailed(
            reason: String,
            throwable: Throwable? = null,
        ) = CharacteristicWriteError(
            message =
                Message(
                    i18nKey = "ble.error.write-characteristic",
                    defaultMessage = reason,
                ),
            exception = throwable,
        )

        @JvmStatic
        @JvmOverloads
        fun readDescriptorFailed(
            reason: String,
            throwable: Throwable? = null,
        ) = DescriptorReadError(
            message =
                Message(
                    i18nKey = "ble.error.read-descriptor",
                    defaultMessage = reason,
                ),
            exception = throwable,
        )

        @JvmStatic
        @JvmOverloads
        fun writeDescriptorFailed(
            reason: String,
            throwable: Throwable? = null,
        ) = DescriptorWriteError(
            message =
                Message(
                    i18nKey = "ble.error.write-descriptor",
                    defaultMessage = reason,
                ),
            exception = throwable,
        )

        @JvmStatic
        fun advertiseFailed(reason: String) =
            AdvertisingError(
                message =
                    Message(
                        i18nKey = "ble.error.advertise",
                        defaultMessage = reason,
                    ),
            )

        /**
         * Create a cancellation "error" - this is not really an error but an expected
         * outcome when a coroutine is cancelled (e.g., during connection racing).
         * Callers should check for this using `is CancelledError` and handle gracefully.
         */
        @JvmStatic
        @JvmOverloads
        fun cancelled(
            reason: String = "Operation was cancelled",
            throwable: Throwable? = null,
        ) = CancelledError(
            message =
                Message(
                    i18nKey = "ble.cancelled",
                    defaultMessage = reason,
                ),
            exception = throwable,
        )

        @JvmStatic
        @JvmOverloads
        fun mtuChangeFailed(
            reason: String,
            throwable: Throwable? = null,
        ) = MtuChangeError(
            message =
                Message(
                    i18nKey = "ble.error.mtu-change",
                    defaultMessage = reason,
                ),
            exception = throwable,
        )
    }

    override fun toString(): String = "BleError(code='$code', severity=$severity, message=$message, causes=$causes, meta=$meta, exception=${exception?.stackTraceToString()})"
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("UnknownBleError", exact = true)
data class UnknownBleError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : BleErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_UNKNOWN_ERROR", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("NotSupportedBleError", exact = true)
data class NotSupportedBleError(
    override val message: Message,
    override val severity: Severity = Severity.ERROR,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : BleErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_NOT_SUPPORTED", severity = severity)

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceNotFoundError", exact = true)
data class DeviceNotFoundError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : BleErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_DEVICE_NOT_FOUND", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("ConnectionFailedError", exact = true)
data class ConnectionFailedError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : BleErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_CONNECTION_FAILED", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("CharacteristicReadError", exact = true)
data class CharacteristicReadError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : BleErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_CHARACTERISTIC_READ_FAILED", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("CharacteristicWriteError", exact = true)
data class CharacteristicWriteError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : BleErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_CHARACTERISTIC_WRITE_FAILED", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("DescriptorReadError", exact = true)
data class DescriptorReadError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : BleErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_DESCRIPTOR_READ_FAILED", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("DescriptorWriteError", exact = true)
data class DescriptorWriteError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : BleErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_DESCRIPTOR_WRITE_FAILED", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("MtuChangeError", exact = true)
data class MtuChangeError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : BleErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_MTU_CHANGE_FAILED", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("ServiceDiscoveryError", exact = true)
data class ServiceDiscoveryError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : BleErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_SERVICE_DISCOVERY_FAILED", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("NotificationFailedError", exact = true)
data class NotificationFailedError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : BleErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_NOTIFICATION_FAILED", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("ScanError", exact = true)
data class ScanError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : BleErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_SCAN_FAILED", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("AdvertisingError", exact = true)
data class AdvertisingError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : BleErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_ADVERTISING_FAILED", severity = Severity.ERROR)

/**
 * Represents a cancelled operation - not a real error but an expected outcome
 * when racing connections or when a coroutine scope is cancelled.
 *
 * Callers should check for this type and handle gracefully (e.g., set state to
 * DISCONNECTED instead of ERROR).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CancelledError", exact = true)
data class CancelledError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : BleErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_CANCELLED", severity = Severity.INFO)
