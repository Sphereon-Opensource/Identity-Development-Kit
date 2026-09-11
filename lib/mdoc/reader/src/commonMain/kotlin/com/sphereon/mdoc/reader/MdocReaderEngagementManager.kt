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

package com.sphereon.mdoc.reader

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.VerifyResultsType
import com.sphereon.crypto.core.generic.VerifySignatureResultType
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DeviceResponse
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.engagement.EngagementInstance
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import kotlinx.coroutines.flow.StateFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Manager for reader-side mdoc operations following ISO/IEC 18013-5:2021.
 *
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("provides", exact = true)
 * This interface provides a high-level API for mdoc readers (verifiers) to:
 * - **Forward Engagement**: Parse device engagements from holder's QR codes, NFC, or app URIs and connect
 * - **Reverse Engagement**: Create reader engagement, show QR/NFC, and wait for holder to connect
 * - Send device requests and receive responses using various transport methods (BLE, NFC, REST API)
 * - Validate issuer and device authentication
 *
 * ## Architecture
 * ```
 * MdocReaderEngagementManager (High-level API)
 *     ↓
 * MdocTransferFactory (Creates transport-specific transfers)
 *     ↓
 * IMdocTransfer implementations (BLE, NFC, REST, etc.)
 *     ↓
 * Transport services and data channels
 * ```
 *
 * ## Usage Examples
 *
 * ### Forward Engagement (Reader scans holder's QR)
 * ```kotlin
 * @Inject
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("ReaderViewModel", exact = true)
 * class ReaderViewModel(
 *     private val readerManager: MdocReaderEngagementManager
 * ) {
 *     suspend fun scanHolderQR(qrUri: String) {
 *         // Parse holder's engagement URI
 *         val engagement = readerManager.parseEngagementUri(qrUri).value
 *
 *         // Connect to holder (auto-determines transport)
 *         readerManager.connect(engagement)
 *
 *         // Send request and receive response
 *         val request = buildDeviceRequest()
 *         readerManager.sendRequest(request)
 *         val response = readerManager.receiveResponse().value
 *
 *         // Validate
 *         response.documents?.forEach { doc ->
 *             readerManager.validateIssuerAuthentication(doc)
 *             readerManager.validateDeviceAuthentication(doc)
 *         }
 *     }
 * }
 * ```
 *
 * ### Reverse Engagement (Reader shows QR for holder to scan)
 * ```kotlin
 * suspend fun showReaderQR() {
 *     // Create reader engagement
 *     val engagement = readerManager.createReaderEngagement {
 *         ble {
 *             peripheralServerMode = true  // Reader advertises
 *         }
 *     }.value
 *
 *     // Get QR code URI
 *     val qrUri = engagement.getEngagementUri()
 *     displayQRCode(qrUri)  // Show to user
 *
 *     // Start transfer (wait for holder to scan and connect)
 *     val transferManager = engagement.start()
 *
 *     // Continue with request/response...
 * }
 * ```
 *
 * ## Dependency Injection
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("is", exact = true)
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocReaderEngagementManager", exact = true)
 * This interface is designed to be injected via DI (e.g., @Inject constructor).
 * All dependencies (KeyManagerService, LogManager, BLE services, etc.) are
 * managed by the DI framework and scoped appropriately (typically SessionScope).
 *
 * ## Transport Agnostic
 * The manager abstracts away transport details:
 * - BLE: Automatically determines central/peripheral mode based on device engagement
 * - NFC: Handles NFC tap and handover
 * - REST API: Handles HTTP-based exchanges
 * - Future: WiFi Aware, additional transport channels.
 *
 * @see MdocReaderEngagementManagerImpl for the implementation
 * @see DeviceEngagement for the parsed engagement structure
 * @see DeviceRequest for request structure
 * @see DeviceResponse for response structure
 */
interface MdocReaderEngagementManager : AutoCloseable {
    /**
     * Parsed device engagement from holder.
     * Updated when [parseEngagementUri] is called.
     *
     * Contains:
     * - Holder's ephemeral public key
     * - Supported retrieval methods (BLE, NFC, etc.)
     * - Security parameters (cipher suite)
     */
    val deviceEngagement: StateFlow<DeviceEngagement?>

    /**
     * Connection status.
     * True when connected to holder and ready to exchange messages.
     */
    val isConnected: StateFlow<Boolean>

    /**
     * Parse engagement URI from holder (forward engagement).
     *
     * This method parses Device Engagement URIs from various sources:
     * - **QR codes**: Scanned QR code containing `mdoc:` URI (ISO 18013-5)
     * - **NFC**: Handover URI from NFC tap
     * - **App-to-app**: Deep link URI (context-dependent)
     *
     * ## URI Formats by Source
     *
     * ### QR Code (ISO 18013-5)
     * Format: `mdoc:<base64url-of-DeviceEngagement>` (opaque URI, no slashes)
     *
     * Example:
     * ```
     * mdoc:o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOB...
     * ```
     *
     * The URI contains the holder's:
     * - Device engagement (ephemeral key, security params)
     * - Retrieval methods (BLE UUIDs, NFC config, etc.)
     *
     * @param uri The engagement URI (format: `mdoc:<base64url>` for QR codes)
     * @return Result with parsed DeviceEngagement or error
     *
     * Parsing is codec-backed and must preserve the original engagement bytes.
     */
    suspend fun parseEngagementUri(uri: String): IdkResult<DeviceEngagement, IdkError>

    /**
     * Connect to holder using device engagement (forward engagement).
     *
     * This method:
     * 1. Extracts connection method from device engagement (BLE, NFC, etc.)
     * 2. Determines reader's role (for BLE: central or peripheral)
     * 3. Establishes connection (scan/advertise for BLE, wait for tap for NFC)
     * 4. Sets up data channels for encrypted communication
     *
     * ## BLE Role Determination
     * Reader does the OPPOSITE of what holder announces:
     * - Holder announces `peripheralServerMode` → Reader acts as **central** (scans and connects)
     * - Holder announces `centralClientMode` → Reader acts as **peripheral** (advertises and waits)
     *
     * ## Connection Methods
     * - **BLE Central**: Reader scans for holder's advertisement UUID
     * - **BLE Peripheral**: Reader advertises and holder connects
     * - **NFC**: Reader waits for NFC tap from holder
     * - **REST API**: Reader establishes HTTP connection
     *
     * @param deviceEngagement The parsed device engagement from [parseEngagementUri]
     * @return Result with Unit on success, or connection error
     *
     * @see parseEngagementUri to get the device engagement
     */
    suspend fun connect(deviceEngagement: DeviceEngagement): IdkResult<Unit, IdkError>

    /**
     * Create reader engagement for reverse engagement flow (reader shows QR/NFC).
     *
     * **Note**: This method is for reverse engagement scenarios. For forward engagement
     * (reader scans holder's QR), use [parseEngagementUri] + [connect] instead.
     *
     * This method:
     * 1. Creates reader engagement with specified connection methods
     * 2. Generates reader ephemeral key pair
     * 3. Returns EngagementInstance that can generate QR/NFC
     *
     * ## Reverse Engagement Flow
     * ```kotlin
     * // Create engagement with custom configuration
     * val engagement = readerManager.createReaderEngagement {
     *     retrieval {
     *         ble {
     *             peripheralServerMode = true  // Reader advertises
     *             peripheralServerUuid = Uuid.random()
     *         }
     *     }
     * }.value
     *
     * // Get QR code URI
     * val qrUri = engagement.getEngagementUri()
     * displayQRCode(qrUri)
     *
     * // Wait for holder to scan and connect
     * val transferManager = engagement.start().value
     * ```
     *
     * ## Configuration Options
     * The `init` builder allows customization of:
     * - **BLE**: Central client mode, peripheral server mode, custom UUIDs
     * - **NFC**: Command/response data field lengths
     * - Multiple retrieval methods can be specified
     *
     * ## Use Cases
     * - Fixed reader terminals (reader shows static QR)
     * - Kiosk scenarios
     * - When holder device has better connectivity
     *
     * @param init Configuration builder for retrieval methods (BLE, NFC, etc.)
     *             Defaults to BLE with both central and peripheral modes enabled
     * @return Result with EngagementInstance for generating QR/NFC and managing connection
     *
     * @see com.sphereon.mdoc.engagement.ReaderConfiguration for configuration options
     * @see EngagementInstance.getEngagementUri to get QR code URI
     * @see EngagementInstance.start to start waiting for holder connection
     */
    suspend fun createReaderEngagement(
        init: com.sphereon.mdoc.engagement.ReaderConfiguration.() -> Unit = {
            retrieval {
                ble {
                    peripheralServerMode = true
                    peripheralServerUuid = kotlin.uuid.Uuid.random()
                    centralClientMode = true
                    centralClientUuid = kotlin.uuid.Uuid.random()
                }
            }
        },
    ): IdkResult<EngagementInstance, IdkError> = throw NotImplementedError("Reverse engagement (reader shows QR) is not yet implemented. Use parseEngagementUri() + connect() for forward engagement.")

    /**
     * Send device request to holder.
     *
     * This method:
     * 1. Generates reader ephemeral key pair
     * 2. Performs ECDH with holder's ephemeral key
     * 3. Derives session keys via HKDF (SKDevice, SKReader, etc.)
     * 4. Creates SessionTranscript
     * 5. Encrypts DeviceRequest
     * 6. Sends SessionEstablishment over established connection
     *
     * The DeviceRequest specifies:
     * - Document types to request (e.g., "org.iso.18013.5.1.mDL")
     * - Namespaces and data elements to retrieve
     * - Intent to retain flags
     * - Optional reader authentication
     *
     * @param deviceRequest The request to send
     * @return Result with Unit on success, or encryption/transmission error
     *
     * @see DeviceRequestBuilder for building requests
     * @see receiveResponse to get the holder's response
     */
    suspend fun sendRequest(deviceRequest: DeviceRequest): IdkResult<Unit, IdkError>

    /**
     * Receive device response from holder.
     *
     * This method:
     * 1. Receives encrypted SessionData over connection
     * 2. Decrypts using session keys
     * 3. Parses DeviceResponse
     * 4. Returns response for validation
     *
     * The DeviceResponse contains:
     * - Requested documents
     * - IssuerSigned data (MSO + signed elements)
     * - DeviceSigned data (MAC or signature)
     * - Status code
     * - Optional errors
     *
     * @return Result with DeviceResponse, or decryption/parsing error
     *
     * @see sendRequest to send the request first
     * @see validateIssuerAuthentication to validate MSO
     * @see validateDeviceAuthentication to validate device signature/MAC
     */
    suspend fun receiveResponse(): IdkResult<DeviceResponse, IdkError>

    /**
     * Validate issuer authentication (Mobile Security Object - MSO).
     *
     * Per ISO 18013-5:2021 §9.1.2.4, this validates:
     * 1. Certificate chain validity
     * 2. COSE_Sign1 signature over MSO
     * 3. Digest values for IssuerSignedItems
     * 4. DocType matches
     * 5. ValidityInfo (issued, expiry dates)
     *
     * ## Compatibility behavior
     * The legacy synchronous reader-engagement API fails closed when the asynchronous
     * validator is not available; callers must use the validated transfer path.
     * Full validation requires:
     * - Certificate validation service
     * - COSE signature verification
     * - Digest calculation and comparison
     *
     * @param document The document to validate
     * @return true if issuer authentication is valid, false otherwise
     */
    fun validateIssuerAuthentication(document: Document): Boolean

    /**
     * Validate issuer authentication using the real asynchronous MSO validation pipeline.
     *
     * The legacy boolean method above is retained for source compatibility and cannot execute a
     * suspend validator. New reader integrations should use this method so certificate-chain,
     * COSE, digest, document-type, and validity failures remain observable as an [IdkResult].
     */
    suspend fun validateIssuerAuthenticationAsync(
        document: Document,
        trustedCerts: Array<String>? = null,
    ): IdkResult<VerifyResultsType<CoseKeyType>, IdkError>

    /**
     * Validate device authentication (MAC or signature).
     *
     * Per ISO 18013-5:2021 §9.1.2.5, this validates:
     * - **For MAC**: Derive EMacKey and verify COSE_Mac0
     * - **For Signature**: Verify COSE_Sign1 with device public key from MSO
     *
     * Device authentication proves that the device possesses the private key
     * corresponding to the public key in the MSO.
     *
     * ## Compatibility behavior
     * The legacy synchronous reader-engagement API fails closed when the asynchronous
     * validator is not available; callers must use the validated transfer path.
     * Full validation requires:
     * - Key derivation (EMacKey for MAC)
     * - COSE_Mac0 verification
     * - COSE_Sign1 verification with device key
     *
     * @param document The document to validate
     * @return true if device authentication is valid, false otherwise
     */
    fun validateDeviceAuthentication(document: Document): Boolean

    /**
     * Validate holder device authentication against the active transfer transcript.
     *
     * Pass [expectedSessionTranscript] when validating a transcript supplied by the caller;
     * otherwise the transcript created by the current engagement is used. This method is the
     * suspend counterpart to the legacy boolean API and preserves verification failures.
     */
    suspend fun validateDeviceAuthenticationAsync(
        document: Document,
        expectedSessionTranscript: SessionTranscript? = null,
    ): IdkResult<VerifySignatureResultType<CoseKeyType>, IdkError>

    /**
     * Use a reverse engagement for request/response operations.
     *
     * This method connects the reader manager to an EngagementInstance created
     * via [createReaderEngagement] for reverse engagement flows.
     *
     * ## Reverse Engagement Flow
     * ```kotlin
     * // 1. Create reader engagement
     * val engagement = readerManager.createReaderEngagement {
     *     retrieval {
     *         ble {
     *             peripheralServerMode = true
     *             peripheralServerUuid = Uuid.random()
     *         }
     *     }
     * }.value
     *
     * // 2. Show QR code to holder
     * val qrUri = engagement.getEngagementUri()
     * displayQRCode(qrUri)
     *
     * // 3. Connect reader manager to reverse engagement
     * readerManager.useReverseEngagement(engagement).value
     *
     * // 4. Wait for holder to scan and connect
     * readerManager.waitForHolderConnection().value
     *
     * // 5. Send request and receive response (same as forward engagement)
     * readerManager.sendRequest(request).value
     * val response = readerManager.receiveResponse().value
     * ```
     *
     * @param engagement The EngagementInstance created via createReaderEngagement
     * @return Result with Unit on success, or error if engagement is invalid
     *
     * @see createReaderEngagement to create the reader engagement
     * @see waitForHolderConnection to wait for holder to connect
     */
    suspend fun useReverseEngagement(engagement: EngagementInstance): IdkResult<Unit, IdkError>

    /**
     * Wait for holder to connect in reverse engagement.
     *
     * This method blocks until the holder scans the reader's QR code and
     * establishes a connection. It must be called after [useReverseEngagement].
     *
     * ## Usage
     * ```kotlin
     * readerManager.useReverseEngagement(engagement).value
     * readerManager.waitForHolderConnection().value  // Blocks until holder connects
     * ```
     *
     * @return Result with Unit when holder connects, or error on timeout/failure
     *
     * @see useReverseEngagement to set up the reverse engagement
     */
    suspend fun waitForHolderConnection(): IdkResult<Unit, IdkError>

    /**
     * Get the active reverse reader connection.
     *
     * Returns the ReverseReaderConnection instance if reverse engagement is active,
     * or null if not in reverse engagement mode.
     *
     * This is used to access the transfer layer for operations like reading
     * DeviceEngagement from the State characteristic in ISO 18013-7 reverse engagement.
     *
     * ## Usage
     * ```kotlin
     * val reverseConnection = readerManager.getReverseConnection()
     * if (reverseConnection != null) {
     *     val transfer = reverseConnection.transferManager.instance.transfer as? BlePeripheralServerTransfer
     *     val deviceEngagement = transfer?.readDeviceEngagement()
     * }
     * ```
     *
     * @return The active ReverseReaderConnection, or null if not in reverse engagement
     */
    fun getReverseConnection(): ReverseReaderConnection?
}
