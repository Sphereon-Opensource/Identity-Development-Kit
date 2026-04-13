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

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.link.nfc.model

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.error.IdkError
import kotlin.js.JsExport
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalObjCName::class)

@ObjCName("NfcError", exact = true)

interface NfcError : IdkErrorType

@JsExport
sealed class NfcErrors(
    override val code: String,
    override val severity: Severity,
    override val message: Message,
    override val causes: List<IdkErrorType> = mutableListOf(),
    override val meta: Map<String, Any?> = mutableMapOf(),
    override val exception: Throwable? = null,
) : IdkError(code = code, severity = severity, message = message, causes = causes, meta = meta, exception = exception), NfcError {


//    fun asErrorResult() : IdkResult<Nothing, INfcError> = Err(this) as IdkResult<Nothing, INfcError>

    companion object {
        fun unknown(
            reason: String = "An unknown NFC error occurred", throwable: Throwable? = null, causes: List<IdkErrorType> = emptyList(),
        ) = UnknownNfcError(
            message = Message(
                i18nKey = "nfc.error.unknown", defaultMessage = reason
            ), exception = throwable, causes = causes
        )

        fun scanFailed(
            reason: String = "Scanning failed", throwable: Throwable? = null, causes: List<IdkErrorType> = emptyList(),
        ) = ScanError(
            message = Message(
                i18nKey = "nfc.error.scan-failed", defaultMessage = reason
            ), exception = throwable, causes = causes
        )

        fun deviceNotFound(deviceAddress: String) = DeviceNotFoundError(
            message = Message(
                i18nKey = "nfc.error.device-not-found", defaultMessage = "Device with id $deviceAddress not found"
            )
        )

        fun notificationFailed(reason: String, throwable: Throwable? = null) = NotificationFailedError(
            message = Message(
                i18nKey = "nfc.error.notification-failed", defaultMessage = reason
            ), exception = throwable
        )

        fun serviceDiscoveryFailed(reason: String, throwable: Throwable? = null) = ServiceDiscoveryError(
            message = Message(
                i18nKey = "nfc.error.service-discovery-failed", defaultMessage = reason
            ), exception = throwable
        )

        fun notSupported(
            reason: String, code: String = "BLE_NOT_SUPPORTED",
        ) = NotSupportedNfcError(
            message = Message(
                i18nKey = "nfc.error.not-supported", defaultMessage = reason
            )
        )

        fun connectionFailed(reason: String, throwable: Throwable? = null) = ConnectionFailedError(
            message = Message(
                i18nKey = "nfc.error.connection-failed", defaultMessage = reason
            ), exception = throwable
        )

        fun readCharacteristicFailed(reason: String, throwable: Throwable? = null) = CharacteristicReadError(
            message = Message(
                i18nKey = "nfc.error.read-characteristic", defaultMessage = reason
            ), exception = throwable
        )

        fun writeCharacteristicFailed(reason: String, throwable: Throwable? = null) = CharacteristicWriteError(
            message = Message(
                i18nKey = "nfc.error.write-characteristic", defaultMessage = reason
            ), exception = throwable
        )

        fun readDescriptorFailed(reason: String, throwable: Throwable? = null) = DescriptorReadError(
            message = Message(
                i18nKey = "nfc.error.read-descriptor", defaultMessage = reason
            ), exception = throwable
        )

        fun writeDescriptorFailed(reason: String, throwable: Throwable? = null) = DescriptorWriteError(
            message = Message(
                i18nKey = "nfc.error.write-descriptor", defaultMessage = reason
            ), exception = throwable
        )

        fun advertiseFailed(reason: String) = AdvertisingError(
            message = Message(
                i18nKey = "nfc.error.advertise", defaultMessage = reason
            )
        )

        fun mtuChangeFailed(reason: String, throwable: Throwable? = null) = MtuChangeError(
            message = Message(
                i18nKey = "nfc.error.mtu-change", defaultMessage = reason
            ), exception = throwable
        )
    }

    override fun toString(): String {
        return "NfcError(code='$code', severity=$severity, message=$message, causes=$causes, meta=$meta, exception=${exception?.stackTraceToString()})"
    }

}

@JsExport

@OptIn(ExperimentalObjCName::class)

@ObjCName("UnknownNfcError", exact = true)

data class UnknownNfcError(
    override val message: Message,
    override val exception: Throwable? = null,
    override val causes: List<IdkErrorType> = emptyList(),
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_UNKNOWN_ERROR", severity = Severity.ERROR) {}

@JsExport

@OptIn(ExperimentalObjCName::class)

@ObjCName("NotSupportedNfcError", exact = true)

data class NotSupportedNfcError(
    override val message: Message,
    override val severity: Severity = Severity.ERROR,
    override val exception: Throwable? = null,
    override val causes: List<IdkErrorType> = emptyList(),
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_NOT_SUPPORTED", severity = severity)


@JsExport


@OptIn(ExperimentalObjCName::class)


@ObjCName("DeviceNotFoundError", exact = true)


data class DeviceNotFoundError(
    override val message: Message,
    override val exception: Throwable? = null,
    override val causes: List<IdkErrorType> = emptyList(),
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_DEVICE_NOT_FOUND", severity = Severity.ERROR) {}


@JsExport


@OptIn(ExperimentalObjCName::class)


@ObjCName("ConnectionFailedError", exact = true)


data class ConnectionFailedError(
    override val message: Message,
    override val exception: Throwable? = null,
    override val causes: List<IdkErrorType> = emptyList(),
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_CONNECTION_FAILED", severity = Severity.ERROR) {

}

@JsExport

@OptIn(ExperimentalObjCName::class)

@ObjCName("CharacteristicReadError", exact = true)

data class CharacteristicReadError(
    override val message: Message,
    override val exception: Throwable? = null,
    override val causes: List<IdkErrorType> = emptyList(),
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_CHARACTERISTIC_READ_FAILED", severity = Severity.ERROR) {}

@JsExport

@OptIn(ExperimentalObjCName::class)

@ObjCName("CharacteristicWriteError", exact = true)

data class CharacteristicWriteError(
    override val message: Message,
    override val exception: Throwable? = null,
    override val causes: List<IdkErrorType> = emptyList(),
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_CHARACTERISTIC_WRITE_FAILED", severity = Severity.ERROR)


@JsExport


@OptIn(ExperimentalObjCName::class)


@ObjCName("DescriptorReadError", exact = true)


data class DescriptorReadError(
    override val message: Message,
    override val exception: Throwable? = null,
    override val causes: List<IdkErrorType> = emptyList(),
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_DESCRIPTOR_READ_FAILED", severity = Severity.ERROR) {}

@JsExport

@OptIn(ExperimentalObjCName::class)

@ObjCName("DescriptorWriteError", exact = true)

data class DescriptorWriteError(
    override val message: Message,
    override val exception: Throwable? = null,
    override val causes: List<IdkErrorType> = emptyList(),
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_DESCRIPTOR_WRITE_FAILED", severity = Severity.ERROR)


@JsExport


@OptIn(ExperimentalObjCName::class)


@ObjCName("MtuChangeError", exact = true)


data class MtuChangeError(
    override val message: Message,
    override val exception: Throwable? = null,
    override val causes: List<IdkErrorType> = emptyList(),
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_MTU_CHANGE_FAILED", severity = Severity.ERROR)


@JsExport


@OptIn(ExperimentalObjCName::class)


@ObjCName("ServiceDiscoveryError", exact = true)


data class ServiceDiscoveryError(
    override val message: Message,
    override val exception: Throwable? = null,
    override val causes: List<IdkErrorType> = emptyList(),
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_SERVICE_DISCOVERY_FAILED", severity = Severity.ERROR)

@JsExport

@OptIn(ExperimentalObjCName::class)

@ObjCName("NotificationFailedError", exact = true)

data class NotificationFailedError(
    override val message: Message,
    override val exception: Throwable? = null,
    override val causes: List<IdkErrorType> = emptyList(),
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_NOTIFICATION_FAILED", severity = Severity.ERROR) {}


@JsExport


@OptIn(ExperimentalObjCName::class)


@ObjCName("ScanError", exact = true)


data class ScanError(
    override val message: Message,
    override val exception: Throwable? = null,
    override val causes: List<IdkErrorType> = emptyList(),
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_SCAN_FAILED", severity = Severity.ERROR) {

}

@JsExport

@OptIn(ExperimentalObjCName::class)

@ObjCName("AdvertisingError", exact = true)

data class AdvertisingError(
    override val message: Message,
    override val exception: Throwable? = null,
    override val causes: List<IdkErrorType> = emptyList(),
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "BLE_ADVERTISING_FAILED", severity = Severity.ERROR) {

}