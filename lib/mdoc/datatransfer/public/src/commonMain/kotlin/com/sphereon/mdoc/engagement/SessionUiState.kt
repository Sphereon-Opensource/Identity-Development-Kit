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

package com.sphereon.mdoc.engagement

import com.sphereon.core.api.encodeToHex
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.mdoc.data.device.DeviceRequestCborCodec
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * High-level UI phases for the session lifecycle.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("UiPhase", exact = true)
@JsExportCompat
enum class UiPhase {
    /** Engagement phase: QR display, NFC handover preparation */
    ENGAGEMENT,

    /** Transfer phase: connecting, data exchange, in-progress */
    TRANSFER,

    /** Terminal phase: session finished (success, error, canceled, etc.) */
    TERMINAL,
}

/**
 * NFC engagement mode for UI display logic.
 * Helps UIs decide whether to show NFC prompts, icons, or nothing.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("NfcMode", exact = true)
@JsExportCompat
enum class NfcMode {
    /** No NFC engagement exists - NFC not available or not enabled */
    DISABLED,

    /** NFC engagement exists in background - show icon/indicator but not active prompt */
    BACKGROUND,

    /** NFC engagement is in foreground - show active "tap to share" prompt */
    FOREGROUND,
}

/**
 * QR display state for UI.
 *
 * IMPORTANT: SCAN mode is UI-managed, not engagement-managed.
 * The user scans a QR code to get the URI *before* creating the engagement.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("QrMode", exact = true)
@JsExportCompat
sealed class QrMode {
    /** No QR engagement active, no scanning in progress */
    data object NONE : QrMode()

    /** Regular QR engagement active - display QR code for reader to scan */
    data object DISPLAY : QrMode()

    /**
     * UI-initiated scanning mode - user is scanning reader's QR code.
     * This is NOT tied to an engagement - it's a pre-engagement UI state.
     *
     * Once scanned, the URI is used to create a TO_APP engagement.
     *
     * **IMPORTANT:** This state must be managed by the UI layer, not the projector,
     * because scanning happens BEFORE engagement creation.
     *
     * Example usage:
     * ```kotlin
     * var uiScanMode by remember { mutableStateOf(false) }
     *
     * if (uiScanMode) {
     *     QrScanner { uri ->
     *         // User scanned reader's QR code
     *         // Create TO_APP engagement with scanned URI
     *         manager.createEngagement {
     *             engagement { toApp { this.uri = uri } }
     *         }
     *         uiScanMode = false
     *     }
     * }
     * ```
     *
     * The engagement system will NEVER set qrMode to SCAN - your UI code controls this.
     */
    data object SCAN : QrMode()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other != null && this::class == other::class
    }

    override fun hashCode(): Int = this::class.hashCode()
}

/**
 * Terminal outcome types for distinguishing between different end states.
 * This helps UIs provide appropriate feedback (success message, error dialog, etc.)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TerminalOutcome", exact = true)
@JsExportCompat
enum class TerminalOutcome {
    /** Transfer completed successfully - data was shared */
    SUCCESS,

    /** User declined to share the requested data */
    DECLINED,

    /** Session was canceled (by user or system) */
    CANCELED,

    /** An error occurred during the session */
    ERROR,

    // Session was terminated (connection lost, timeout, etc.)
//    TERMINATED
}

/**
 * Minimal, opinionated UI state projection for simple integration.
 *
 * This state is derived from raw engagement and retrieval events and provides
 * a deterministic, UI-friendly view of the session lifecycle.
 *
 * Key rules:
 * - QR mode: DISPLAY when QR engagement exists, NONE otherwise
 *   - SCAN mode is UI-managed (not set by projector) for pre-engagement scanning
 * - NFC state tracks background/foreground/disabled status based on engagement presence
 * - Phase transitions are deterministic: ENGAGEMENT -> TRANSFER -> TERMINAL
 * - Transfer phase begins when connection starts
 * - Once transfer starts, engagement-final events don't bounce the UI
 * - User interaction required when DocumentsSelectionProcessStart event occurs
 *
 * IMPORTANT: For reverse/ToApp engagements:
 * - UI opens scanner (sets qrMode to SCAN manually)
 * - User scans reader's QR code to get URI
 * - UI creates TO_APP engagement with URI
 * - Engagement starts normally (qrMode returns to NONE)
 * - The SCAN state is purely UI-managed, not driven by engagement events
 *
 * @property phase Current high-level phase (ENGAGEMENT, TRANSFER, TERMINAL)
 * @property substateLabel Human-readable label for current substate (e.g., "Connecting…", "Transferring…")
 * @property nfcMode NFC engagement state (DISABLED, BACKGROUND, FOREGROUND)
 * @property qrMode QR engagement mode (NONE, DISPLAY for showing QR, SCAN for scanning QR)
 * @property isSuspended Whether the session is suspended (engagement inactive)
 * @property progressHint Optional progress fraction (0.0-1.0) for transfer progress, null if not applicable
 * @property terminalOutcome Terminal outcome classification (success, error, declined, etc.), null unless phase is TERMINAL
 * @property terminalMessage Optional message/reason for terminal state, null unless phase is TERMINAL
 * @property userInteractionRequired Whether user must accept/decline the request (review document selection)
 * @property deviceRequest Optional CBOR-encoded device request data when user interaction is required
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionUiState", exact = true)
@JsExportCompat
data class SessionUiState(
    val phase: UiPhase,
    val substateLabel: String,
    val nfcMode: NfcMode,
    val qrMode: QrMode,
    val isSuspended: Boolean,
    val progressHint: Float?,
    val terminalOutcome: TerminalOutcome?,
    val terminalMessage: String?,
    val userInteractionRequired: Boolean,
    val deviceRequest: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as SessionUiState

        if (phase != other.phase) {
            return false
        }
        if (substateLabel != other.substateLabel) {
            return false
        }
        if (nfcMode != other.nfcMode) {
            return false
        }
        if (qrMode != other.qrMode) {
            return false
        }
        if (isSuspended != other.isSuspended) {
            return false
        }
        if (progressHint != other.progressHint) {
            return false
        }
        if (terminalOutcome != other.terminalOutcome) {
            return false
        }
        if (terminalMessage != other.terminalMessage) {
            return false
        }
        if (userInteractionRequired != other.userInteractionRequired) {
            return false
        }
        if (deviceRequest != null) {
            if (other.deviceRequest == null) {
                return false
            }
            if (!deviceRequest.contentEquals(other.deviceRequest)) {
                return false
            }
        } else if (other.deviceRequest != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = phase.hashCode()
        result = 31 * result + substateLabel.hashCode()
        result = 31 * result + nfcMode.hashCode()
        result = 31 * result + qrMode.hashCode()
        result = 31 * result + isSuspended.hashCode()
        result = 31 * result + (progressHint?.hashCode() ?: 0)
        result = 31 * result + (terminalOutcome?.hashCode() ?: 0)
        result = 31 * result + (terminalMessage?.hashCode() ?: 0)
        result = 31 * result + userInteractionRequired.hashCode()
        result = 31 * result + (deviceRequest?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Parsed representation of a DeviceRequest, making it easier to work with
 * the requested data without manually decoding CBOR.
 *
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("removes", exact = true)
 * This helper class removes the need for developers to understand CBOR encoding
 * when handling user consent dialogs.
 *
 * Example usage:
 * ```kotlin
 * manager.eventHub.sessionState.collect { state ->
 *     if (state.userInteractionRequired) {
 *         val parsed = state.parseDeviceRequest(deviceRequestCborCodec)
 *         if (parsed != null) {
 *             showConsentDialog(parsed)
 *         } else {
 *             // Failed to parse - handle error
 *             showError("Invalid device request")
 *         }
 *     }
 * }
 * ```
 *
 * @property version The DeviceRequest version (typically "1.0")
 * @property documents List of document requests, each containing docType and requested attributes
 *                    (includes OID4VP-derived requests when docRequests is absent)
 * @property hasReaderAuth Whether reader authentication is present in any document request
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParsedDeviceRequest", exact = true)
@JsExportCompat
data class ParsedDeviceRequest(
    val version: String,
    val documents: List<ParsedDocumentRequest>,
    val hasReaderAuth: Boolean,
) {
    companion object {
        /**
         * Parse a CBOR-encoded DeviceRequest into a user-friendly structure.
         *
         * @param deviceRequestBytes CBOR-encoded DeviceRequest bytes
         * @param deviceRequestCborCodec codec used to decode the request bytes
         * @return Parsed request, or null if parsing fails
         */
        fun parse(
            deviceRequestBytes: ByteArray,
            deviceRequestCborCodec: DeviceRequestCborCodec,
        ): ParsedDeviceRequest? {
            return try {
                val deviceRequest = deviceRequestCborCodec.decode(deviceRequestBytes).getOrNull()?.value ?: return null

                val documents =
                    deviceRequest.effectiveDocRequests().map { docReq ->
                        ParsedDocumentRequest(
                            docType = docReq.itemsRequest.docType.toString(),
                            nameSpaces =
                                docReq.itemsRequest.nameSpaces.mapKeys { it.key.toString() }.mapValues { entry ->
                                    entry.value.keys
                                        .map { it.toString() }
                                        .toSet()
                                },
                            hasReaderAuth = docReq.readerAuth != null,
                        )
                    }

                val hasReaderAuth = documents.any { it.hasReaderAuth }

                ParsedDeviceRequest(
                    version = deviceRequest.version.toString(),
                    documents = documents,
                    hasReaderAuth = hasReaderAuth,
                )
            } catch (_: Exception) {
                // Failed to parse - return null
                null
            }
        }
    }
}

/**
 * Represents a single document request within a DeviceRequest.
 *
 * @property docType The document type being requested (e.g., "org.iso.18013.5.1.mDL")
 * @property nameSpaces Map of namespace to set of requested attribute identifiers
 * @property hasReaderAuth Whether this document request includes reader authentication
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParsedDocumentRequest", exact = true)
@JsExportCompat
data class ParsedDocumentRequest(
    val docType: String,
    val nameSpaces: Map<String, Set<String>>,
    val hasReaderAuth: Boolean,
)

/**
 * Extension function to parse the deviceRequest from SessionUiState.
 *
 * This is a convenience method that handles the CBOR decoding automatically.
 * Returns null if no request exists or if parsing fails.
 *
 * Example usage:
 * ```kotlin
 * manager.eventHub.sessionState.collect { state ->
 *     if (state.userInteractionRequired) {
 *         val parsed = state.parseDeviceRequest(deviceRequestCborCodec)
 *         if (parsed != null) {
 *             // Display consent dialog with parsed request
 *             showConsentDialog(parsed.documents)
 *         } else {
 *             showError("Invalid device request")
 *         }
 *     }
 * }
 * ```
 *
 * @return Parsed device request, or null if no request exists or parsing fails
 */
fun SessionUiState.parseDeviceRequest(deviceRequestCborCodec: DeviceRequestCborCodec): ParsedDeviceRequest? = deviceRequest?.let { ParsedDeviceRequest.parse(it, deviceRequestCborCodec) }

/**
 * Alias for parseDeviceRequest().
 * @see parseDeviceRequest
 */
fun SessionUiState.getParsedDeviceRequest(deviceRequestCborCodec: DeviceRequestCborCodec): ParsedDeviceRequest? = parseDeviceRequest(deviceRequestCborCodec)

/**
 * Simplified, UI-relevant events derived from raw MdocEngagementEvent and MdocRetrievalEvent.
 *
 * These events drive the SessionUiState projection and are designed for UI consumption.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionEvent", exact = true)
@JsExportCompat
sealed interface SessionEvent {
    /** QR code is being shown - QR visibility is managed via engagement state, not separate hide events */
    data object QrShow : SessionEvent

    /** NFC prompt should be shown */
    data object NfcPromptShown : SessionEvent

    /** NFC handover succeeded */
    data object NfcHandoverSuccess : SessionEvent

    /** Transfer is connecting */
    data object TransferConnecting : SessionEvent

//    data object TransferConnectionSelected : SessionEvent

    /** Transfer connected successfully */
    data object TransferConnected : SessionEvent

    /**
     * User interaction required: must review and accept/decline the document request.
     * This event is triggered when DocumentsSelectionProcessStart is received.
     * The UI should display the request details and provide accept/decline options.
     *
     * @property deviceRequest CBOR-encoded DeviceRequest data for the UI to decode and display
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("UserInteractionRequired", exact = true)
    data class UserInteractionRequired(
        val deviceRequest: ByteArray,
    ) : SessionEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }

            other as UserInteractionRequired

            return deviceRequest.contentEquals(other.deviceRequest)
        }

        override fun hashCode(): Int = deviceRequest.contentHashCode()

        override fun toString(): String = "UserInteractionRequired(deviceRequest=${deviceRequest.encodeToHex()})"
    }

    /** User accepted the document request */
    data object UserAccepted : SessionEvent

    /** User declined the document request */
    data object UserDeclined : SessionEvent

    /** Transfer progress update */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("TransferProgress", exact = true)
    data class TransferProgress(
        val fraction: Float,
    ) : SessionEvent

    /**
     * Session reached terminal state.
     *
     * @property outcome The type of terminal outcome (success, error, declined, etc.)
     * @property message Optional human-readable message describing the outcome
     */
    data class Terminal(
        val outcome: TerminalOutcome,
        val message: String? = null,
    ) : SessionEvent
}
