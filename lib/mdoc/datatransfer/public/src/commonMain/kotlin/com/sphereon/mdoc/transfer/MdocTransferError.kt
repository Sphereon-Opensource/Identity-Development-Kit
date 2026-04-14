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

package com.sphereon.mdoc.transfer

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Enhanced error types for mDoc transfer operations.
 *
 * These errors provide contextual information and recovery suggestions to help
 * developers quickly diagnose and fix issues.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocTransferError", exact = true)
@JsExportCompat
sealed class MdocTransferError(
    message: String,
) : Exception(message) {
    abstract val errorCode: String
    abstract val context: Map<String, Any>
    abstract val recoverySuggestions: List<String>

    /**
     * Format error as a detailed message including context and recovery suggestions.
     */
    fun formatDetailedMessage(): String {
        val parts = mutableListOf<String>()

        parts.add("Error: $message")
        parts.add("   Code: $errorCode")

        if (context.isNotEmpty()) {
            parts.add("\n Context:")
            context.forEach { (key, value) ->
                parts.add("   • $key: $value")
            }
        }

        if (recoverySuggestions.isNotEmpty()) {
            parts.add("\n Recovery Suggestions:")
            recoverySuggestions.forEachIndexed { index, suggestion ->
                parts.add("   ${index + 1}. $suggestion")
            }
        }

        return parts.joinToString("\n")
    }

    /**
     * Session transcript not initialized.
     *
     * This error occurs when attempting operations that require a session transcript
     * before the transcript has been established.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("SessionTranscriptNotInitialized", exact = true)
    data class SessionTranscriptNotInitialized(
        val transferState: String,
        @OptIn(ExperimentalUuidApi::class)
        val engagementId: Uuid,
        override val context: Map<String, Any> = emptyMap(),
    ) : MdocTransferError("Session transcript not initialized") {
        override val errorCode = "MDOC_TRANSFER_001"
        override val recoverySuggestions =
            listOf(
                "Call receiveDeviceRequest() before attempting to sign documents or access the transcript",
                "Ensure transfer session has been started with transferManager.start()",
                "Check that engagement is in the correct state (should be >= REQUEST_RECEIVED)",
                "Verify that the reader has sent a device request",
            )
    }

    /**
     * Invalid state transition attempted.
     *
     * This error occurs when attempting to transition to a state that is not
     * allowed from the current state.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("InvalidStateTransition", exact = true)
    data class InvalidStateTransition(
        val fromState: String,
        val toState: String,
        val allowedStates: List<String>,
        override val context: Map<String, Any> = emptyMap(),
    ) : MdocTransferError(
            "Invalid state transition from '$fromState' to '$toState'. " +
                "Allowed transitions: ${allowedStates.joinToString(", ")}",
        ) {
        override val errorCode = "MDOC_TRANSFER_002"
        override val recoverySuggestions =
            listOf(
                "Verify the order of operations matches the ISO 18013-5 flow",
                "Expected flow: INIT → START → REQUEST_RECEIVED → RESPONSE_READY → RESPONSE_SENT → COMPLETED",
                "Check if a previous operation failed and left the session in an error state",
                "Consider closing and recreating the engagement if state is corrupted",
                "Review the state machine documentation for valid transitions",
            )
    }

    /**
     * Timeout occurred during transfer operation.
     *
     * This error occurs when an operation takes longer than the configured timeout.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("TimeoutError", exact = true)
    data class TimeoutError(
        val operation: String,
        val timeoutDuration: Duration,
        override val context: Map<String, Any> = emptyMap(),
    ) : MdocTransferError("Operation '$operation' timed out after $timeoutDuration") {
        override val errorCode = "MDOC_TRANSFER_003"
        override val recoverySuggestions =
            listOf(
                "Check network connectivity between holder and reader devices",
                "Verify BLE is enabled and scanning/advertising is active",
                "Ensure devices are within BLE range (typically 10-30 meters)",
                "Increase timeout configuration if operations legitimately take longer",
                "Check that the remote device is still active and responding",
                "For NFC: Ensure devices remain in close proximity during transfer",
            )
    }

    /**
     * Document type mismatch between request and document.
     *
     * This error occurs when trying to sign a document with a request that
     * specifies a different document type.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("DocTypeMismatch", exact = true)
    data class DocTypeMismatch(
        val requestDocType: String,
        val documentDocType: String,
        val documentId: String? = null,
        override val context: Map<String, Any> = emptyMap(),
    ) : MdocTransferError(
            "DocType mismatch: Request expects '$requestDocType' but document has '$documentDocType'",
        ) {
        override val errorCode = "MDOC_TRANSFER_004"
        override val recoverySuggestions =
            listOf(
                "Ensure the DocRequest matches the Document you're signing",
                "Check that you're using the correct document for this request",
                "Verify document provider returns documents matching requested docTypes",
                "Common docTypes: 'org.iso.18013.5.1.mDL' (mobile driving license), 'eu.europa.ec.eudi.pid.1' (EU PID)",
            )
    }

    /**
     * Required namespace missing in document.
     *
     * This error occurs when a request asks for data from a namespace that
     * doesn't exist in the document.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("NamespaceMissing", exact = true)
    data class NamespaceMissing(
        val requestedNamespace: String,
        val availableNamespaces: Set<String>,
        val documentDocType: String,
        override val context: Map<String, Any> = emptyMap(),
    ) : MdocTransferError(
            "Requested namespace '$requestedNamespace' not found in document. " +
                "Available namespaces: ${availableNamespaces.joinToString(", ")}",
        ) {
        override val errorCode = "MDOC_TRANSFER_005"
        override val recoverySuggestions =
            listOf(
                "Verify the namespace name matches the document structure",
                "Check if document was issued with the expected namespaces",
                "Review the document's MSO (Mobile Security Object) for available namespaces",
                "For mDL: Common namespace is 'org.iso.18013.5.1'",
                "For PID: Check EU PID specification for valid namespaces",
            )
    }

    /**
     * Reader authentication failed or missing.
     *
     * This error occurs when reader authentication is required but missing,
     * or when validation of the reader's signature fails.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("ReaderAuthenticationFailed", exact = true)
    data class ReaderAuthenticationFailed(
        val reason: String,
        val requireReaderAuth: Boolean,
        val hasReaderAuth: Boolean,
        override val context: Map<String, Any> = emptyMap(),
    ) : MdocTransferError("Reader authentication failed: $reason") {
        override val errorCode = "MDOC_TRANSFER_006"
        override val recoverySuggestions =
            buildList {
                add("Verify reader's certificate chain is valid and trusted")
                add("Check that reader's signature over session transcript is correct")
                add("Ensure reader's certificate hasn't expired")
                if (!hasReaderAuth && requireReaderAuth) {
                    add("Reader must provide authentication when requireReaderAuthentication=true")
                }
                add("Review trust anchor configuration for reader certificates")
                add("Check certificate revocation status (CRL/OCSP)")
            }
    }

    /**
     * BLE connection failed.
     *
     * This error occurs when BLE connection establishment fails.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("BleConnectionFailed", exact = true)
    data class BleConnectionFailed(
        @OptIn(ExperimentalUuidApi::class)
        val bleUuid: Uuid?,
        val mode: String, // "central" or "peripheral"
        val reason: String?,
        override val context: Map<String, Any> = emptyMap(),
    ) : MdocTransferError("BLE connection failed in $mode mode: ${reason ?: "unknown reason"}") {
        override val errorCode = "MDOC_TRANSFER_007"
        override val recoverySuggestions =
            listOf(
                "Verify Bluetooth is enabled on both devices",
                "Check Bluetooth permissions are granted (BLUETOOTH_SCAN, BLUETOOTH_CONNECT on Android 12+)",
                "Ensure devices are within BLE range (typically 10-30 meters)",
                "Check for BLE interference from other devices",
                "Verify BLE UUID matches between holder and reader: ${bleUuid ?: "not available"}",
                "On iOS: Ensure CoreBluetooth permissions are configured in Info.plist",
                "Try restarting Bluetooth on both devices",
            )
    }

    /**
     * NFC operation failed.
     *
     * This error occurs when NFC communication fails.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("NfcOperationFailed", exact = true)
    data class NfcOperationFailed(
        val operation: String,
        val reason: String?,
        override val context: Map<String, Any> = emptyMap(),
    ) : MdocTransferError("NFC operation '$operation' failed: ${reason ?: "unknown reason"}") {
        override val errorCode = "MDOC_TRANSFER_008"
        override val recoverySuggestions =
            listOf(
                "Ensure NFC is enabled on the device",
                "Keep devices in close proximity (< 4cm) during entire operation",
                "Don't move devices during NFC communication",
                "Check NFC permissions are granted",
                "Verify device supports NFC HCE (Host Card Emulation) for holder apps",
                "On Android: Ensure NFC service is declared in AndroidManifest.xml",
                "Try restarting NFC on both devices",
            )
    }

    /**
     * Data encoding/decoding failed.
     *
     * This error occurs when CBOR encoding or decoding fails.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("EncodingError", exact = true)
    data class EncodingError(
        val dataType: String,
        val operation: String, // "encode" or "decode"
        val reason: String?,
        override val context: Map<String, Any> = emptyMap(),
    ) : MdocTransferError("Failed to $operation $dataType: ${reason ?: "unknown reason"}") {
        override val errorCode = "MDOC_TRANSFER_009"
        override val recoverySuggestions =
            listOf(
                "Verify data structure matches ISO 18013-5 specification",
                "Check for corrupted or incomplete data",
                "Ensure CBOR encoding follows CDDL schema",
                "Review raw bytes for debugging (may contain binary data)",
                "Check for version mismatches between sender and receiver",
                "Consult ISO 18013-5 Annex D for valid data structures",
            )
    }

    /**
     * Engagement already closed.
     *
     * This error occurs when attempting operations on a closed engagement.
     */
    @OptIn(ExperimentalUuidApi::class, ExperimentalObjCName::class)
    @ObjCName("EngagementClosed", exact = true)
    data class EngagementClosed(
        val engagementId: Uuid,
        val attemptedOperation: String,
        override val context: Map<String, Any> = emptyMap(),
    ) : MdocTransferError("Cannot perform '$attemptedOperation': Engagement $engagementId is already closed") {
        override val errorCode = "MDOC_TRANSFER_010"
        override val recoverySuggestions =
            listOf(
                "Check if engagement was closed prematurely",
                "Verify you're not using a stale engagement reference",
                "Create a new engagement instead of reusing closed one",
                "Review engagement lifecycle management in your application",
                "Ensure close() is only called when transfer is complete or canceled",
            )
    }
}

/**
 * Convert MdocTransferError to IdkError for use with IdkResult.
 */
fun MdocTransferError.toIdkError(): IdkError =
    IdkError(
        code = this.errorCode,
        severity = IdkError.Severity.ERROR,
        message =
            IdkError.Message(
                i18nKey = "com.sphereon.mdoc.transfer.error.${this.errorCode.lowercase()}",
                defaultMessage = this.formatDetailedMessage(),
            ),
        exception = this,
        meta = this.context,
    )
