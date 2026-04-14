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

import com.sphereon.core.api.IdkErrorResult
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.mdoc.transfer.TransferInstance
import kotlinx.coroutines.flow.StateFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("for", exact = true)
 * Main manager interface for mdoc engagement and transfer.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocEngagementManager", exact = true)
@JsExportCompat
interface MdocEngagementManager : MdocEngagementFactory.Holder {
    /**
     * Centralized event hub for UI integration.
     * This is the primary integration point for UI layers to consume events from the manager.
     *
     * Provides:
     * - Access to all event streams (engagement, transfer, combined)
     * - Filtered event streams (by state order ranges)
     * - State tracking (latest events, current states)
     * - Event history/buffer for debugging and state reconstruction
     *
     * ## Usage Example
     * ```kotlin
     * // Kotlin/Android
     * manager.eventHub.allEvents.collect { event ->
     *     when (event) {
     *         is MdocEngagementEvent.QrShow -> showQrCode(event.qrCodeData)
     *         is MdocEngagementEvent.Connected -> onConnected()
     *     }
     * }
     *
     * // Get current states
     * val currentStates = manager.eventHub.getCurrentStateByEngagement().value
     *
     * // Get event history for debugging
     * val recentEvents = manager.eventHub.getRecentEvents(count = 20)
     * ```
     *
     * @see MdocEventHub For full API documentation
     */
    val eventHub: MdocEventHub

    /**
     * Shared parameters manager for BLE UUIDs and ephemeral keys.
     *
     * This object manages parameters that are automatically shared across engagements
     * when not explicitly provided. It provides:
     * - Separate BLE UUIDs for central client mode and peripheral server mode
     * - Shared ephemeral key across all engagements
     * - Manual regeneration for new verification sessions
     *
     * ## Usage Example
     * ```kotlin
     * // Check current shared UUIDs
     * val centralUuid = manager.sharedParameters.bleCentralClientUuid.value
     * val peripheralUuid = manager.sharedParameters.blePeripheralServerUuid.value
     *
     * // Regenerate for new session
     * manager.sharedParameters.regenerate()
     *
     * // Use same UUID for both modes (optional)
     * manager.sharedParameters.useSameUuidForBothModes()
     * ```
     *
     * @see SharedParameters For full API documentation
     */
    val sharedParameters: SharedParameters

    /**
     * Map of engagement instances keyed by engagement type.
     * Only ONE engagement can exist per type at a time.
     *
     * Supported types:
     * - NFC: ISO 18013-5 proximity-based engagement
     * - QR: ISO 18013-5 QR code-based engagement
     * - TO_APP: ISO 18013-7 app-to-app / reverse engagement
     *
     * The map only contains entries for currently active engagement types.
     * You can have multiple engagement types active simultaneously (e.g., both QR and NFC),
     * but only one instance per type.
     *
     * @see nfcEngagement Direct access to NFC engagement (null if not active)
     * @see qrEngagement Direct access to QR engagement (null if not active)
     * @see toAppEngagement Direct access to ToApp engagement (null if not active)
     */
    val engagementsByType: StateFlow<Map<EngagementType, EngagementInstance>>

    /**
     * Direct access to the NFC engagement instance if one exists.
     * Returns null if no NFC engagement is currently active.
     *
     * This is a convenience property that extracts the NFC engagement from engagementsByType.
     * Prefer using this over map access for cleaner, more type-safe code.
     *
     * ## Usage
     * ```kotlin
     * manager.nfcEngagement.value?.let { nfc ->
     *     // Work with NFC engagement
     * }
     * ```
     */
    val nfcEngagement: StateFlow<EngagementInstance?>

    /**
     * Direct access to the QR engagement instance if one exists.
     * Returns null if no QR engagement is currently active.
     *
     * This is a convenience property that extracts the QR engagement from engagementsByType.
     * Prefer using this over map access for cleaner, more type-safe code.
     *
     * ## Usage
     * ```kotlin
     * manager.qrEngagement.value?.let { qr ->
     *     val uri = qr.getEngagementUri()
     *     displayQrCode(uri)
     * }
     * ```
     */
    val qrEngagement: StateFlow<EngagementInstance?>

    /**
     * Direct access to the ToApp (reverse) engagement instance if one exists.
     * Returns null if no ToApp engagement is currently active.
     *
     * This is a convenience property that extracts the ToApp engagement from engagementsByType.
     * Prefer using this over map access for cleaner, more type-safe code.
     *
     * ## Usage
     * ```kotlin
     * manager.toAppEngagement.value?.let { toApp ->
     *     // Work with ToApp engagement
     * }
     * ```
     */
    val toAppEngagement: StateFlow<EngagementInstance?>

    /**
     * Track which engagement is currently active (has an active transfer).
     * Only ONE engagement can have an active transfer at a time.
     * Value is null when no engagement is active.
     *
     * An engagement becomes active when its state transitions to CONNECTING.
     * Other engagements are automatically suspended when one becomes active.
     */
    val activeEngagement: StateFlow<EngagementInstance?>

    /**
     * Map of active transfer instances.
     * Transfer starts at different times depending on engagement method:
     * - QR: Immediately when engagement created
     * - NFC: After NFC tap completes
     */
    @OptIn(ExperimentalUuidApi::class)
    val transferInstances: StateFlow<Map<Uuid, TransferInstance>>

    /**
     * Enable automatic cleanup and background NFC restart.
     * When any transfer completes:
     * 1. All engagements are closed
     * 2. Background NFC (if configured) is recreated
     *
     * @param backgroundNfcConfig Config for background NFC, or null to disable
     * @return Result with Unit on success or error on failure
     */
    suspend fun enableAutoRestart(backgroundNfcConfig: (EngagementConfiguration.() -> Unit)? = null): IdkResult<Unit, IdkError>

    /**
     * Create a TO_APP engagement from a reader's engagement URI.
     * This is a unified convenience method for all reverse engagement protocols.
     *
     * **Use Case:**
     * When the holder scans a QR code or receives a deep link from a reader/verifier,
     * use this method to create the engagement. It automatically detects the protocol type.
     *
     * ## Supported URI Schemes
     *
     * ### 1. Classic Reverse Engagement (ISO 18013-5)
     * - **Scheme**: `mdoc:` (opaque URI, **NO** slashes)
     * - **Format**: `mdoc:<base64url-of-ReaderEngagement>`
     * - **Protocol**: BLE/NFC data transfer with CBOR
     * - **Use Case**: Reader displays QR, holder scans and connects via BLE/NFC
     *
     * ### 2. REST API / Website Retrieval (ISO 18013-7 Annex A)
     * - **Scheme**: `mdoc://` (hierarchical URI, **WITH** slashes)
     * - **Format**: `mdoc://<base64url-of-ReaderEngagement>`
     * - **Protocol**: HTTPS POST with CBOR
     * - **Use Case**: Reader displays QR, holder POSTs to HTTPS endpoint
     *
     * ### 3. OID4VP (ISO 18013-7 Annex B)
     * - **Scheme**: `mdoc-openid4vp://` (custom scheme for wallet invocation)
     * - **Format**: `mdoc-openid4vp://?client_id=...&request_uri=...`
     * - **Protocol**: OAuth 2.0 / OpenID4VP with JWT
     * - **Use Case**: Verifier displays QR, wallet performs OAuth flow
     * - **Note**: `response_uri`, `nonce`, and `dcql_query` are fetched from `request_uri`
     *   (legacy `presentation_definition` may appear; use it only for legacy mdoc and keep PE optional)
     *
     * ## Scheme Disambiguation
     *
     * **IMPORTANT**: `mdoc:` and `mdoc://` are **different** schemes:
     * - `mdoc:` (no slashes) = ISO 18013-5 reverse engagement with BLE/NFC
     * - `mdoc://` (with slashes) = ISO 18013-7 website retrieval with HTTPS
     *
     * The method automatically:
     * - Detects the protocol from the URI scheme
     * - Parses protocol-specific parameters
     * - Configures appropriate retrieval methods
     * - Sets up the engagement for the detected protocol
     * - **For OID4VP**: Automatically starts the engagement to fetch Authorization Request
     *
     * ## Examples
     *
     * ### Classic Reverse Engagement (BLE/NFC)
     * ```kotlin
     * val mdocUri = "mdoc:o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOB..." // Note: NO slashes
     * val engagement = manager.toApp(mdocUri).value
     * val transferManager = engagement.start().value
     * // Uses BLE or NFC for data transfer
     * ```
     *
     * ### REST API / Website
     * ```kotlin
     * val mdocUri = "mdoc://o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOB..." // Note: WITH slashes
     * val engagement = manager.toApp(mdocUri).value
     * val transferManager = engagement.start().value
     * // Uses HTTPS POST for data transfer
     * ```
     *
     * ### OID4VP
     * ```kotlin
     * val oid4vpUri = "mdoc-openid4vp://?client_id=verifier.com&request_uri=https://..."
     * val engagement = manager.toApp(oid4vpUri).value
     * // Engagement is automatically started and Authorization Request is fetched
     * // Access the transfer to work with the request:
     * val transfer = engagement.transferInstance.transfer as Oid4vpTransfer
     * val authRequest = transfer.getAuthorizationRequest()
     * ```
     *
     * @param mdocUri The URI from the reader's QR code or deep link
     * @param autoStart If true (default for OID4VP), automatically starts the engagement.
     *                  For OID4VP, starting is required to fetch the Authorization Request Object.
     * @return Result with EngagementInstance for TO_APP engagement
     */
    suspend fun toApp(
        mdocUri: String,
        autoStart: Boolean = true,
    ): IdkResult<EngagementInstance, IdkError> {
        fun parseReaderEngagementOrError(): IdkResult<com.sphereon.mdoc.transfer.reader.ReaderEngagement, IdkError> =
            IdkErrorResult(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Default MdocEngagementManager.toApp(...) no longer parses ReaderEngagement URIs. Use a codec-aware implementation.",
                    arg = mdocUri,
                ),
            )

        // Detect protocol by URI scheme
        val engagementResult =
            when {
                // OID4VP protocol (ISO 18013-7 Annex B)
                mdocUri.startsWith("mdoc-openid4vp://") -> {
                    val engagementResult =
                        createEngagement {
                            engagement {
                                oid4vp {
                                    fromUri(mdocUri)
                                }
                            }
                            // Configure OID4VP retrieval method
                            // The retrieval options are auto-extracted from the URI
                            // by the Oid4vpRetrievalBuilder via fromOid4vpEngagement()
                            retrieval {
                                oid4vp {
                                    // Parameters will be auto-extracted from engagement method
                                }
                            }
                        }

                    engagementResult
                }

                // Classic reverse engagement (ISO 18013-5)
                // Uses opaque scheme without slashes: mdoc:
                mdocUri.startsWith("mdoc:") && !mdocUri.startsWith("mdoc://") -> {
                    // Parse the ReaderEngagement to extract retrieval methods
                    val readerEngagement = parseReaderEngagementOrError().getOrElse { return IdkErrorResult(it) }
                    val readerBleOptions = readerEngagement.getBleRetrievalOptions()
                    val readerNfcOptions = readerEngagement.getNfcRetrievalOptions()

                    val engagementResult =
                        createEngagement {
                            engagement {
                                reader {
                                    withReaderEngagement(readerEngagement)
                                }
                            }

                            // Configure BLE if the reader supports BLE
                            // If reader is peripheral server (advertises), holder should be central client (connects)
                            // If reader is central client (connects), holder should be peripheral server (advertises)
                            readerBleOptions?.let { bleOptions ->
                                retrieval {
                                    ble {
                                        // Reader is peripheral server -> holder becomes central client
                                        if (bleOptions.peripheralServerMode == true) {
                                            centralClientMode = true
                                            peripheralServerMode = false
                                            // Use the same UUID that the reader advertised
                                            centralClientUuid = bleOptions.peripheralServerModeUuid
                                        } else if (bleOptions.centralClientMode == true) {
                                            // Reader is central client -> holder becomes peripheral server
                                            peripheralServerMode = true
                                            centralClientMode = false
                                            // Use the same UUID that the reader will connect to
                                            peripheralServerUuid = bleOptions.centralClientModeUuid
                                        }
                                    }
                                }
                            }

                            // Configure NFC if the reader supports NFC
                            readerNfcOptions?.let { nfcOptions ->
                                retrieval {
                                    nfc {
                                        maxCommandDataFieldLength = nfcOptions.maxCommandDataFieldLength
                                        maxResponseDataFieldLength = nfcOptions.maxResponseDataFieldLength
                                    }
                                }
                            }
                        }

                    engagementResult
                }

                // REST API / Website protocol (ISO 18013-7 Annex A)
                // Uses hierarchical scheme with slashes: mdoc://
                mdocUri.startsWith("mdoc://") -> {
                    // Parse the ReaderEngagement to extract retrieval methods
                    val readerEngagement = parseReaderEngagementOrError().getOrElse { return IdkErrorResult(it) }
//                val readerBleOptions = readerEngagement.getBleRetrievalOptions()
                    val readerRestApiOptions = readerEngagement.getWebsiteRetrievalOptions()

                    val engagementResult =
                        createEngagement {
                            engagement {
                                reader {
                                    withReaderEngagement(readerEngagement)
                                }
                            }

                            /*
                            // Configure holder's retrieval methods based on what the reader advertised
                            // The holder needs to specify how they will communicate back to the reader
                    // Configure BLE if the reader supports BLE
                    // If reader is peripheral server (advertises), holder should be central client (connects)
                    // If reader is central client (connects), holder should be peripheral server (advertises)
                    readerBleOptions?.let { bleOptions ->
                        retrieval {
                            ble {
                                // Reader is peripheral server → holder becomes central client
                                if (bleOptions.peripheralServerMode == true) {
                                    centralClientMode = true
                                    peripheralServerMode = false
                                    // Use the same UUID that the reader advertised
                                    centralClientUuid = bleOptions.peripheralServerModeUuid
                                }
                                // Reader is central client → holder becomes peripheral server
                                else if (bleOptions.centralClientMode == true) {
                                    peripheralServerMode = true
                                    centralClientMode = false
                                    // Use the same UUID that the reader will connect to
                                    peripheralServerUuid = bleOptions.centralClientModeUuid
                                }
                            }
                        }
                    }*/

                            // Configure REST API if the reader supports REST API
                            // For REST API, the holder POSTs directly to the reader's endpoint
                            readerRestApiOptions?.let { restApiOpts ->
                                retrieval {
                                    website {
                                        uri = restApiOpts.uri
                                    }
                                }
                            }
                        }

                    engagementResult
                }

                // Unknown protocol
                else -> {
                    val scheme =
                        when {
                            mdocUri.contains("://") -> mdocUri.substringBefore("://")
                            mdocUri.contains(":") -> mdocUri.substringBefore(":")
                            else -> "unknown"
                        }

                    com.sphereon.core.api.IdkErrorResult(
                        com.sphereon.core.api.error.IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message =
                                "Unsupported URI scheme for toApp. Expected:\n" +
                                    "  - 'mdoc:' (ISO 18013-5 reverse engagement)\n" +
                                    "  - 'mdoc://' (ISO 18013-7 website retrieval)\n" +
                                    "  - 'mdoc-openid4vp://' (ISO 18013-7 OID4VP with OAuth)\n" +
                                    "Got: $scheme\n\n",
                            arg = mdocUri,
                        ),
                    )
                }
            }

        engagementResult.onSuccess {
            // For OID4VP, automatically start the engagement if requested
            // This fetches the Authorization Request Object from request_uri
            if (autoStart && engagementResult.isOk) {
                val engagement = engagementResult.value
                val startResult = engagement.tryOps().start()
                if (!startResult.isOk) {
                    // If starting failed, close the engagement and return the error
                    closeEngagementByInstance(engagement)
                    return IdkErrorResult(
                        IdkError.fromDTO(startResult.error),
                    )
                }
            }
        }

        return engagementResult
    }

    /**
     * Close NFC engagement if it exists.
     * @return Result with Unit on success, or error if no NFC engagement exists
     */
    suspend fun closeNfcEngagement(): IdkResult<Unit, IdkError>

    /**
     * Close QR engagement if it exists.
     * @return Result with Unit on success, or error if no QR engagement exists
     */
    suspend fun closeQrEngagement(): IdkResult<Unit, IdkError>

    /**
     * Close ToApp engagement if it exists.
     * @return Result with Unit on success, or error if no ToApp engagement exists
     */
    suspend fun closeToAppEngagement(): IdkResult<Unit, IdkError>

    /**
     * Close specific engagement by instance reference.
     * @return Result with Unit on success, or error if engagement not found
     */
    suspend fun closeEngagementByInstance(engagement: EngagementInstance): IdkResult<Unit, IdkError>

    /**
     * Close specific engagement by ID.
     * @return Result with Unit or error if engagement not found
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun closeEngagementById(id: Uuid): IdkResult<Unit, IdkError>

    /**
     * Close all engagements.
     * @return Result with Unit or error
     */
    suspend fun closeAll(): IdkResult<Unit, IdkError>

    /**
     * Closes the engagement manager and cleans up resources.
     */
    fun close()
}
