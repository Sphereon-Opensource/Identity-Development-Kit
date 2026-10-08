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

package com.sphereon.data.link.nfc.model

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
@ObjCName("NfcError", exact = true)
interface NfcError : IdkErrorType

@JsExportCompat
sealed class NfcErrors(
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
    NfcError {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun unknown(
            reason: String = "An unknown NFC error occurred",
            throwable: Throwable? = null,
            causes: List<IdkErrorType> = emptyList(),
        ) = UnknownNfcError(
            message = Message(i18nKey = "nfc.error.unknown", defaultMessage = reason),
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
            message = Message(i18nKey = "nfc.error.scan.failed", defaultMessage = reason),
            exception = throwable,
            causes = causes,
        )

        @JvmStatic
        fun tagNotFound(tagId: String) =
            TagNotFoundError(
                message = Message(i18nKey = "nfc.error.tag.not.found", defaultMessage = "Tag with id $tagId not found"),
            )

        @JvmStatic
        @JvmOverloads
        fun tagLost(
            reason: String = "NFC tag was removed from the field",
            throwable: Throwable? = null,
        ) = TagLostError(
            message = Message(i18nKey = "nfc.error.tag.lost", defaultMessage = reason),
            exception = throwable,
        )

        @JvmStatic
        fun notSupported(reason: String) =
            NotSupportedNfcError(
                message = Message(i18nKey = "nfc.error.not.supported", defaultMessage = reason),
            )

        @JvmStatic
        @JvmOverloads
        fun connectionFailed(
            reason: String,
            throwable: Throwable? = null,
        ) = ConnectionFailedError(
            message = Message(i18nKey = "nfc.error.connection.failed", defaultMessage = reason),
            exception = throwable,
        )

        @JvmStatic
        @JvmOverloads
        fun transceiveFailed(
            reason: String,
            throwable: Throwable? = null,
        ) = TransceiveError(
            message = Message(i18nKey = "nfc.error.transceive.failed", defaultMessage = reason),
            exception = throwable,
        )

        @JvmStatic
        @JvmOverloads
        fun commandFailed(
            reason: String,
            status: Int? = null,
            throwable: Throwable? = null,
        ) = CommandFailedError(
            message = Message(i18nKey = "nfc.error.command.failed", defaultMessage = reason),
            exception = throwable,
            meta =
                if (status != null) {
                    mapOf("status" to status)
                } else {
                    emptyMap()
                },
        )

        @JvmStatic
        @JvmOverloads
        fun sessionFailed(
            reason: String,
            throwable: Throwable? = null,
        ) = SessionError(
            message = Message(i18nKey = "nfc.error.session.failed", defaultMessage = reason),
            exception = throwable,
        )

        @JvmStatic
        @JvmOverloads
        fun ndefParseFailed(
            reason: String,
            throwable: Throwable? = null,
        ) = NdefParseError(
            message = Message(i18nKey = "nfc.error.ndef.parse.failed", defaultMessage = reason),
            exception = throwable,
        )
    }

    override fun toString(): String = "NfcError(code='$code', severity=$severity, message=$message, causes=$causes, meta=$meta, exception=${exception?.stackTraceToString()})"
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("UnknownNfcError", exact = true)
data class UnknownNfcError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "NFC_UNKNOWN_ERROR", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("NotSupportedNfcError", exact = true)
data class NotSupportedNfcError(
    override val message: Message,
    override val severity: Severity = Severity.ERROR,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "NFC_NOT_SUPPORTED", severity = severity)

@OptIn(ExperimentalObjCName::class)
@ObjCName("TagNotFoundError", exact = true)
data class TagNotFoundError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "NFC_TAG_NOT_FOUND", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("TagLostError", exact = true)
data class TagLostError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "NFC_TAG_LOST", severity = Severity.ERROR)

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
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "NFC_CONNECTION_FAILED", severity = Severity.ERROR)

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
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "NFC_SCAN_FAILED", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("TransceiveError", exact = true)
data class TransceiveError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "NFC_TRANSCEIVE_FAILED", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("CommandFailedError", exact = true)
data class CommandFailedError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "NFC_COMMAND_FAILED", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionError", exact = true)
data class SessionError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "NFC_SESSION_FAILED", severity = Severity.ERROR)

@OptIn(ExperimentalObjCName::class)
@ObjCName("NdefParseError", exact = true)
data class NdefParseError(
    override val message: Message,
    @JsExportIgnoreCompat
    override val exception: Throwable? = null,
    @JsExportIgnoreCompat
    override val causes: List<IdkErrorType> = emptyList(),
    @JsExportIgnoreCompat
    override val meta: Map<String, Any?> = emptyMap(),
) : NfcErrors(message = message, exception = exception, causes = causes, meta = meta, code = "NFC_NDEF_PARSE_FAILED", severity = Severity.ERROR)
