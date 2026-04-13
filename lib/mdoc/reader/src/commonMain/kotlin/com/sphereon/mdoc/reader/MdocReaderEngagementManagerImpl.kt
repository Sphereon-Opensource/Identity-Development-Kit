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

import com.sphereon.cbor.CborItem
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.toException
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.cose.CoseKeyCborCodec
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.SessionDataCborCodec
import com.sphereon.mdoc.SessionEstablishmentCborCodec
import com.sphereon.mdoc.SessionTranscriptCborCodec
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DeviceRequestCborCodec
import com.sphereon.mdoc.data.device.DeviceResponse
import com.sphereon.mdoc.data.device.DeviceResponseCborCodec
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.engagement.DeviceEngagementCborCodec
import com.sphereon.mdoc.engagement.EngagementInstance
import com.sphereon.mdoc.engagement.MdocEngagementFactory
import com.sphereon.mdoc.transfer.MdocTransferFactory
import com.sphereon.mdoc.transfer.reader.Handover
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.mdoc.transport.IncomingDataChannel
import com.sphereon.mdoc.transport.MdocTransportRegistry
import com.sphereon.mdoc.transport.OutgoingDataChannel
import dev.whyoleg.cryptography.CryptographyProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import software.amazon.app.platform.scope.coroutine.CoroutineScopeScoped
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Implementation of [MdocReaderEngagementManager] using dependency injection.
 *
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("provides", exact = true)
 * This class provides the reader-side functionality for mDoc engagement and data exchange.
 * It manages:
 * - Forward engagement (reader scans holder's QR/NFC)
 * - Connection establishment (BLE, NFC, REST API)
 * - Session encryption and data transfer
 * - Request/response exchange
 *
 * All dependencies are injected via DI as SessionScope singletons:
 * - KeyManagerService: For ephemeral key generation and session encryption
 * - SessionLogService: For logging
 * - MdocEngagementFactory: For creating engagement instances
 * - MdocTransferFactory: For creating transport-specific transfers (BLE, NFC, etc.)
 *
 * ## Architecture
 * ```
 * MdocReaderEngagementManagerImpl (this class)
 *     ↓ uses
 * MdocEngagementFactory (creates EngagementInstance)
 *     ↓ creates
 * TransferManager (handles request/response)
 *     ↓ uses
 * MdocTransferFactory (creates transport-specific transfers)
 *     ↓ creates
 * IMdocTransfer implementations (BLE central/peripheral, NFC, REST)
 * ```
 *
 * @param kms KeyManagerService for cryptographic operations (SessionScope singleton)
 * @param logService SessionLogService for logging (SessionScope singleton)
 * @param engagementFactory MdocEngagementFactory for creating engagements (SessionScope singleton)
 * @param transferFactory MdocTransferFactory for creating transfers (SessionScope singleton)
 * @param scopeScoped Optional CoroutineScopeScoped for testing
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalObjCName::class)
@Inject
@ContributesBinding(SessionScope::class, binding = binding<MdocReaderEngagementManager>())
@SingleIn(SessionScope::class)
@ObjCName("MdocReaderEngagementManagerImpl", exact = true)
class MdocReaderEngagementManagerImpl(
    private val kms: KeyManagerService,
    private val logService: SessionLogService,
    private val engagementFactory: MdocEngagementFactory,
    private val transferFactory: MdocTransferFactory,
    private val transportRegistry: MdocTransportRegistry, // Transport registry for modular transports
    private val deviceRequestCborCodec: DeviceRequestCborCodec,
    private val deviceResponseCborCodec: DeviceResponseCborCodec,
    private val deviceEngagementCborCodec: DeviceEngagementCborCodec,
    private val sessionDataCborCodec: SessionDataCborCodec,
    private val sessionEstablishmentCborCodec: SessionEstablishmentCborCodec,
    private val sessionTranscriptCborCodec: SessionTranscriptCborCodec,
    private val coseKeyCborCodec: CoseKeyCborCodec,
    private val execution: com.sphereon.core.api.context.SessionExecution, // Session execution context
    private val cryptoProvider: CryptographyProvider = CryptographyProvider.Default,
    scopeScoped: CoroutineScopeScoped? = null,
) : MdocReaderEngagementManager,
    com.sphereon.mdoc.transfer.MdocRetrievalEvent.Handlers,
    com.sphereon.mdoc.transfer.MdocRetrievalEvent.Dispatcher {
    private val scope: CoroutineScope =
        scopeScoped?.createChild()
            ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val log = logService.logManager.withTag("MdocReaderManager")

    // Transport helper for creating modular transports (replaces old BLE connection helper)
    private val transportHelper =
        ReaderTransportHelper(
            transportRegistry = transportRegistry,
            execution = execution,
            logService = logService,
        )

    init {
        log.info("Reader engagement manager initialized with modular transports")
        log.info("Available transports: ${transportRegistry.supportedTransports}")
        log.info("Registered factories: ${transportRegistry.getAllFactories().map { it.transportType }}")
    }

    // State management
    private val _deviceEngagement = MutableStateFlow<DeviceEngagement?>(null)
    override val deviceEngagement: StateFlow<DeviceEngagement?> = _deviceEngagement.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Reader-specific state (not exposed in interface)
    private val readerId = Uuid.random()
    private var sessionEncryption: com.sphereon.mdoc.SessionEncryption? = null
    private var readerEphemeralKey: ResolvedKeyInfoType<CoseKeyType>? = null
    private var incomingDataChannel: IncomingDataChannel? = null
    private var outgoingDataChannel: OutgoingDataChannel? = null

    // Connection abstraction for unified forward/reverse handling
    private var connection: ReaderConnection? = null

    /**
     * Parse engagement URI from holder (forward engagement).
     *
     * This parses `mdoc:` URIs from:
     * - QR codes scanned from holder
     * - NFC handover URIs
     * - App-to-app deep links
     *
     * The parsed [DeviceEngagement] contains the holder's ephemeral key and retrieval methods.
     */
    override suspend fun parseEngagementUri(uri: String): IdkResult<DeviceEngagement, IdkError> =
        try {
            log.info("Parsing device engagement URI")
            check(uri.startsWith("mdoc:")) { "Device Engagement URI must start with 'mdoc:' per ISO 18013-5. We got: $uri" }
            val engagement = deviceEngagementCborCodec.decode(uri.substring(5).decodeFromBase64Url()).getOrThrow().value
            _deviceEngagement.value = engagement
            log.info("Successfully parsed device engagement from URI")
            engagement.asOkResult()
        } catch (expected: Exception) {
            log.error("Failed to parse engagement URI", exception = expected)
            IdkError
                .UNKNOWN_ERROR(
                    message = "Failed to parse engagement URI: ${expected.message}",
                    exception = expected,
                ).asErrorResult()
        }

    /**
     * Connect to holder using device engagement (forward engagement).
     *
     * This method:
     * 1. Extracts connection method from device engagement
     * 2. Determines reader's BLE role (opposite of holder's announcement)
     * 3. Creates data channels for encrypted communication
     * 4. Establishes BLE connection (scan/advertise based on role)
     *
     * ## BLE Role Determination
     * Reader does the OPPOSITE of what holder announces:
     * - Holder announces `peripheralServerMode` → Reader acts as CENTRAL (scans)
     * - Holder announces `centralClientMode` → Reader acts as PERIPHERAL (advertises)
     *
     * @param deviceEngagement The device engagement containing connection info
     * @return Result with Unit on success
     */
    override suspend fun connect(deviceEngagement: DeviceEngagement): IdkResult<Unit, IdkError> {
        return try {
            _deviceEngagement.value = deviceEngagement

            // Create forward connection using modular transports
            val forwardConnection =
                ForwardReaderConnection(
                    deviceEngagement = deviceEngagement,
                    readerId = readerId,
                    transportHelper = transportHelper,
                    logService = logService,
                )

            // Establish connection
            val establishResult = forwardConnection.establish()
            if (establishResult.isErr) {
                return establishResult.error.asErrorResult()
            }

            // Store connection and extract channels
            connection = forwardConnection
            incomingDataChannel = forwardConnection.incomingChannel
            outgoingDataChannel = forwardConnection.outgoingChannel

            log.info("Connected to holder via forward engagement")
            _isConnected.value = true

            Unit.asOkResult()
        } catch (expected: Exception) {
            log.error("Failed to connect to holder", exception = expected)
            IdkError.UNKNOWN_ERROR(message = "Failed to connect to holder: ${expected.message}", exception = expected).asErrorResult()
        }
    }

    /**
     * Send device request to holder.
     *
     * This method:
     * 1. Generates ephemeral key pair for session encryption
     * 2. Derives session keys using ECDH + HKDF
     * 3. Encrypts the DeviceRequest
     * 4. Sends SessionEstablishment over connection
     *
     * Per ISO 18013-5:2021 §12.2.4 step 2
     *
     * @param deviceRequest The request to send
     * @return Result with Unit on success
     */
    override suspend fun sendRequest(deviceRequest: DeviceRequest): IdkResult<Unit, IdkError> {
        return try {
            if (!_isConnected.value) {
                return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Not connected to holder").asErrorResult()
            }

            // Determine if this is forward or reverse engagement
            val isReverseEngagement = connection is ReverseReaderConnection

            if (isReverseEngagement) {
                // Reverse engagement: Reader ephemeral key already exists in the EngagementInstance
                return sendRequestReverseEngagement(deviceRequest)
            }

            // Forward engagement: Generate reader ephemeral key now
            val engagement =
                _deviceEngagement.value
                    ?: return IdkError.NOT_FOUND_ERROR(message = "No device engagement available").asErrorResult()

            // 1. Generate ephemeral key pair (reader side) using the same curve as device
            // Extract curve from device engagement security
            val deviceCurve =
                com.sphereon.crypto.core.generic.Curve.fromCose(
                    com.sphereon.crypto.core.cose.CoseCurve
                        .fromValue(engagement.security.cipherSuite.toInt()),
                )

            // Get appropriate signature algorithm for the curve
            val signatureAlgorithm =
                when (deviceCurve) {
                    com.sphereon.crypto.core.generic.Curve.P_256 -> SignatureAlgorithm.ECDSA_SHA256
                    com.sphereon.crypto.core.generic.Curve.P_384 -> SignatureAlgorithm.ECDSA_SHA384
                    com.sphereon.crypto.core.generic.Curve.P_521 -> SignatureAlgorithm.ECDSA_SHA512
                    else -> SignatureAlgorithm.ECDSA_SHA256 // Default fallback
                }

            log.debug("Generating reader ephemeral key pair with curve: $deviceCurve, algorithm: $signatureAlgorithm")
            val managedKeyPair =
                kms.generateKeyAsync(
                    providerId = null,
                    alg = signatureAlgorithm,
                )

            // Convert to COSE key format
            readerEphemeralKey =
                com.sphereon.crypto.core.CoseJoseKeyMappingService.toResolvedCoseKeyInfo(
                    managedKeyPair.toManagedKeyInfo<CoseKeyType>(
                        visibility = KeyVisibility.PRIVATE,
                        keyEncoding = KeyEncoding.COSE,
                    ),
                )

            // 2. Create SessionTranscript (§12.6.1)
            // CRITICAL: Use the original CBOR bytes from the engagement, not re-encoded bytes!
            val deviceEngagementCbor =
                engagement.original
                    ?: return IdkError
                        .ILLEGAL_ARGUMENT_ERROR(
                            message = "DeviceEngagement original CBOR bytes not available - cannot create session transcript",
                        ).asErrorResult()

            // CRITICAL: Only include the PUBLIC key in SessionTranscript, not the private key!
            val readerPublicCoseKey = readerEphemeralKey!!.key.toPublicKey()

            // CRITICAL: Encode the eReaderKey once and use those EXACT bytes in both SessionTranscript and SessionEstablishment!
            val readerPublicCoseKeyCbor =
                com.sphereon.crypto.core.cose.CoseKey
                    .fromDTO(readerPublicCoseKey)
            val encodedReaderKey =
                com.sphereon.cbor.CborEncodedItem(
                    coseKeyCborCodec.encode(readerPublicCoseKeyCbor).getOrElse { throw it.toException() },
                    readerPublicCoseKeyCbor,
                )

            // Create SessionTranscript and encode it ONCE to get deterministic bytes
            @Suppress("UNCHECKED_CAST")
            val sessionTranscriptTemp =
                com.sphereon.mdoc.transfer.reader.SessionTranscript(
                    deviceEngagement = com.sphereon.cbor.CborEncodedItem(deviceEngagementCbor, engagement),
                    eReaderKey = encodedReaderKey,
                    handover =
                        com.sphereon.mdoc.transfer.reader
                            .QrHandover() as Handover<*, CborItem<*>>,
                    // QR code handover (null in CBOR)
                    original = null,
                )

            // Encode the SessionTranscript once and store those bytes as original
            val sessionTranscriptBytes =
                sessionTranscriptCborCodec
                    .encode(sessionTranscriptTemp)
                    .getOrElse { throw it.toException() }
            val sessionTranscript = sessionTranscriptTemp.copy(original = sessionTranscriptBytes)

            // Store as CborEncodedItem for later use (e.g., in ReaderAuthentication)
            val sessionTranscriptEncoded =
                sessionTranscriptCborCodec
                    .encodeItem(sessionTranscript)
                    .getOrElse { throw it.toException() }
            // Per ISO 18013-5: SessionTranscriptBytes = #6.24(bstr .cbor SessionTranscript)
            // The HKDF salt is SHA-256(SessionTranscriptBytes), which IS Tag 24 wrapped
            val sessionTranscriptBytesForHKDF =
                sessionTranscriptCborCodec
                    .encodeTag24(sessionTranscript)
                    .getOrElse { throw it.toException() }

            log.debug("Created SessionTranscript with device engagement and reader ephemeral key")

            // 3. Derive session keys using ECDH + HKDF (§12.2.5)
            // Extract the device's ephemeral public key from the engagement
            val devicePublicKeyInfo =
                com.sphereon.crypto.core.ResolvedKeyInfo(
                    key = engagement.security.eDeviceKeyBytes.data(),
                )

            sessionEncryption =
                com.sphereon.mdoc.SessionEncryption
                    .Builder()
                    .withCborCodecs(
                        sessionEstablishmentCborCodec = sessionEstablishmentCborCodec,
                        sessionDataCborCodec = sessionDataCborCodec,
                        sessionTranscriptCborCodec = sessionTranscriptCborCodec,
                        coseKeyCborCodec = coseKeyCborCodec,
                    ).withSelfRole(MdocRole.MDOC_READER)
                    .withSelfPrivateEphemeralKey(readerEphemeralKey!!)
                    .withRemotePublicEphemeralKey(devicePublicKeyInfo)
                    .withSessionTranscriptBytes(sessionTranscriptBytesForHKDF) // Tag24 wrapped per ISO 18013-5
                    .withProvider(cryptoProvider)
                    .build()

            // CRITICAL: Set the encodedReaderKey on SessionEncryption to match what we used in SessionTranscript!
            sessionEncryption!!.encodedReaderKey = encodedReaderKey

            log.debug("Session encryption initialized with derived keys")

            // Store session encryption in connection if using connection abstraction
            if (connection is ForwardReaderConnection) {
                (connection as ForwardReaderConnection).setSessionEncryption(sessionEncryption!!)
            }

            // 4. Encrypt DeviceRequest and create SessionEstablishment (§12.2.4 step 2)
            val requestBytes =
                deviceRequestCborCodec
                    .encode(deviceRequest)
                    .getOrElse { throw it.toException() }
            log.debug("Encrypting DeviceRequest (${requestBytes.size} bytes)")

            val sessionEstablishment = sessionEncryption!!.encryptAsSessionEstablishment(requestBytes)

            log.debug("Created SessionEstablishment with encrypted request")

            // 5. Send over BLE
            requireNotNull(outgoingDataChannel) { "Outgoing data channel not initialized" }

            val sessionEstablishmentBytes =
                sessionEstablishmentCborCodec
                    .encode(sessionEstablishment)
                    .getOrElse { throw it.toException() }
            val status = outgoingDataChannel!!.sendRaw(sessionEstablishmentBytes)

            // Status 0 indicates success for BLE (not an error)
            // A negative value or exception indicates failure
            if (status < 0) {
                return IdkError.UNKNOWN_ERROR(message = "Failed to send SessionEstablishment: status=$status").asErrorResult()
            }

            log.info("Sent SessionEstablishment to holder successfully")
            Unit.asOkResult()
        } catch (expected: Exception) {
            log.error("Failed to send request", exception = expected)
            IdkError.UNKNOWN_ERROR(message = "Failed to send request: ${expected.message}", exception = expected).asErrorResult()
        }
    }

    /**
     * Create reader engagement for reverse engagement flow (reader shows QR/NFC).
     *
     * This method creates a ReaderEngagement where:
     * 1. Reader generates ephemeral key pair
     * 2. Reader creates engagement with specified transport methods (BLE, NFC, etc.)
     * 3. Reader can generate QR code URI or NFC data
     * 4. Holder scans/taps and initiates connection
     * 5. Communication proceeds (holder typically sends first in reverse flow)
     *
     * ## Reverse Engagement Flow
     * ```kotlin
     * // Example 1: Use default configuration (BLE both modes)
     * val engagement = readerManager.createReaderEngagement().value
     * val qrUri = engagement.getEngagementUri()
     * displayQRCode(qrUri)
     *
     * // Example 2: Custom configuration (peripheral only)
     * val engagement = readerManager.createReaderEngagement {
     *     retrieval {
     *         ble {
     *             peripheralServerMode = true
     *             peripheralServerUuid = customUuid
     *             centralClientMode = false  // Only advertise
     *         }
     *     }
     * }.value
     *
     * // 3. Wait for holder to scan and connect
     * val transferManager = engagement.start().value
     * ```
     *
     * ## Configuration Options
     * The `init` builder allows customization of:
     * - **BLE modes**: Choose central, peripheral, or both
     * - **UUIDs**: Specify custom UUIDs or let system generate
     * - **NFC**: Add NFC retrieval method with custom parameters
     * - **Multiple transports**: Combine BLE + NFC for flexibility
     *
     * ## Default Configuration
     * If no configuration is provided, creates engagement with:
     * - BLE peripheral server mode (reader advertises) - typical for reverse engagement
     * - BLE central client mode (reader can scan) - for flexibility
     * - Random UUIDs for both modes
     *
     * ## Use Cases
     * - **Fixed reader terminals/kiosks**: Reader shows QR on screen
     * - **Age verification at point of sale**: Reader shows QR, holder scans
     * - **Access control terminals**: Reader shows QR, holder scans to enter
     * - **Better holder connectivity**: When holder device has better network/BLE
     *
     * @param init Configuration builder for retrieval methods (BLE, NFC, etc.)
     * @return Result with EngagementInstance for generating QR/NFC and managing connection
     *
     * @see com.sphereon.mdoc.engagement.ReaderConfiguration for configuration options
     * @see EngagementInstance.getEngagementUri to get QR code URI
     * @see EngagementInstance.start to start waiting for holder connection
     */
    override suspend fun createReaderEngagement(init: com.sphereon.mdoc.engagement.ReaderConfiguration.() -> Unit): IdkResult<EngagementInstance, IdkError> {
        return try {
            log.info("Creating reader engagement for reverse flow with custom configuration")

            // Use the factory's reader.createEngagement() with provided configuration
            val engagementResult = engagementFactory.reader.createEngagement(init)

            if (engagementResult.isErr) {
                log.error("Failed to create reader engagement via factory: ${engagementResult.error.message}")
                return engagementResult.error.asErrorResult()
            }

            val engagement = engagementResult.value

            log.info("Created reader engagement for reverse flow: ${engagement.id}")

            // Note: In reverse engagement, we don't have a device engagement yet
            // The holder will parse our reader engagement and connect to us
            _deviceEngagement.value = null

            engagement.asOkResult()
        } catch (expected: Exception) {
            log.error("Failed to create reader engagement", exception = expected)
            IdkError
                .UNKNOWN_ERROR(
                    message = "Failed to create reader engagement: ${expected.message}",
                    exception = expected,
                ).asErrorResult()
        }
    }

    /**
     * Receive device response from holder.
     *
     * This method:
     * 1. Receives SessionData over connection
     * 2. Decrypts using session keys
     * 3. Parses DeviceResponse
     *
     * Per ISO 18013-5:2021 §12.2.4 step 3
     *
     * @return Result with DeviceResponse
     */
    override suspend fun receiveResponse(): IdkResult<DeviceResponse, IdkError> {
        return try {
            if (sessionEncryption == null) {
                return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Session encryption not initialized - must call sendRequest first").asErrorResult()
            }

            requireNotNull(incomingDataChannel) { "Incoming data channel not initialized" }

            // 1. Receive SessionData over BLE
            log.debug("Waiting to receive SessionData from holder...")
            val sessionDataBytes = incomingDataChannel!!.receiveRaw()
            val sessionData =
                sessionDataCborCodec
                    .decode(sessionDataBytes)
                    .getOrElse { throw it.toException() }
                    .value
            log.debug("Received SessionData with ${sessionData.data?.value?.size ?: 0} bytes from holder")

            // 2. Decrypt SessionData using session keys (§12.2.5)
            val decryptedSessionData = sessionEncryption!!.decryptSessionData(sessionData.original ?: sessionDataBytes)
            log.debug("Decrypted SessionData successfully")

            // 3. Parse DeviceResponse from decrypted data
            val responseData =
                decryptedSessionData.data
                    ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "SessionData contains no data").asErrorResult()

            val deviceResponse =
                deviceResponseCborCodec
                    .decode(responseData.value)
                    .getOrElse { throw it.toException() }
                    .value

            log.info("Received DeviceResponse from holder - status: ${deviceResponse.status}, documents: ${deviceResponse.documents?.size ?: 0}")

            deviceResponse.asOkResult()
        } catch (expected: Exception) {
            log.error("Failed to receive response", exception = expected)
            IdkError.UNKNOWN_ERROR(message = "Failed to receive response: ${expected.message}", exception = expected).asErrorResult()
        }
    }

    /**
     * Send request in reverse engagement flow.
     *
     * In reverse engagement, the reader's ephemeral key was already generated
     * when createReaderEngagement() was called. We need to:
     * 1. Get the reader's ephemeral key from the engagement instance
     * 2. Get the holder's ephemeral key from the session transcript
     * 3. Create session encryption using these keys
     * 4. Encrypt and send the request
     */
    private suspend fun sendRequestReverseEngagement(deviceRequest: DeviceRequest): IdkResult<Unit, IdkError> {
        val reverseConnection = connection as ReverseReaderConnection

        // Get the TransferManager which has access to engagement data
        val transferManager = reverseConnection.transferManager
        val engagement = transferManager.engagement

        log.debug("Setting up session encryption for reverse engagement request")

        // Get session transcript from transfer manager - this contains holder's ephemeral key
        val sessionTranscriptEncoded = transferManager.getSessionTranscript()
        val sessionTranscript = sessionTranscriptEncoded.data()
        // Per ISO 18013-5: SessionTranscriptBytes = #6.24(bstr .cbor SessionTranscript)
        val sessionTranscriptBytes =
            sessionTranscriptCborCodec
                .encodeTag24(sessionTranscript)
                .getOrElse { throw it.toException() }

        // Get reader's ephemeral key from the engagement instance (created during createReaderEngagement)
        val readerEphemeralKeyResult = engagement.tryOps().getEphemeralKey()
        if (readerEphemeralKeyResult.isErr) {
            return IdkError
                .UNKNOWN_ERROR(
                    message = "Failed to get reader ephemeral key: ${readerEphemeralKeyResult.error.message}",
                    exception = readerEphemeralKeyResult.error.exception,
                ).asErrorResult()
        }

        val readerPublicCoseKey = readerEphemeralKeyResult.value

        // Get the reader's private key from engagement data
        val readerPrivateKey = engagement.data.getEphemeralKey()

        // Extract holder's public key from session transcript
        // In reverse engagement, the eReaderKey in SessionTranscript is actually the READER's key
        // and the device engagement contains the HOLDER's key
        val deviceEngagementFromTranscript =
            sessionTranscript.deviceEngagement
                ?: return IdkError
                    .ILLEGAL_ARGUMENT_ERROR(
                        message = "Session transcript does not contain device engagement",
                    ).asErrorResult()

        val holderPublicKeyInfo =
            com.sphereon.crypto.core.ResolvedKeyInfo<com.sphereon.crypto.core.cose.CoseKeyType>(
                key =
                    deviceEngagementFromTranscript
                        .data()
                        .security.eDeviceKeyBytes
                        .data(),
            )

        // Create session encryption for reader role
        sessionEncryption =
            com.sphereon.mdoc.SessionEncryption
                .Builder()
                .withCborCodecs(
                    sessionEstablishmentCborCodec = sessionEstablishmentCborCodec,
                    sessionDataCborCodec = sessionDataCborCodec,
                    sessionTranscriptCborCodec = sessionTranscriptCborCodec,
                    coseKeyCborCodec = coseKeyCborCodec,
                ).withSelfRole(MdocRole.MDOC_READER)
                .withSelfPrivateEphemeralKey(readerPrivateKey)
                .withRemotePublicEphemeralKey(holderPublicKeyInfo)
                .withSessionTranscriptBytes(sessionTranscriptBytes)
                .withProvider(cryptoProvider)
                .build()

        // Store in connection
        reverseConnection.setSessionEncryption(sessionEncryption!!)

        log.debug("Session encryption created for reverse engagement")

        // Encrypt DeviceRequest and create SessionEstablishment
        val requestBytes =
            deviceRequestCborCodec
                .encode(deviceRequest)
                .getOrElse { throw it.toException() }
        log.debug("Encrypting DeviceRequest (${requestBytes.size} bytes)")

        val sessionEstablishment = sessionEncryption!!.encryptAsSessionEstablishment(requestBytes)
        log.debug("Created SessionEstablishment with encrypted request")

        // Send over BLE
        requireNotNull(outgoingDataChannel) { "Outgoing data channel not initialized" }

        val sessionEstablishmentBytes =
            sessionEstablishmentCborCodec
                .encode(sessionEstablishment)
                .getOrElse { throw it.toException() }
        val status = outgoingDataChannel!!.sendRaw(sessionEstablishmentBytes)

        // Status 0 indicates success for BLE (not an error)
        // A negative value or exception indicates failure
        if (status < 0) {
            return IdkError.UNKNOWN_ERROR(message = "Failed to send SessionEstablishment: status=$status").asErrorResult()
        }

        log.info("Sent SessionEstablishment to holder successfully (reverse engagement)")
        return Unit.asOkResult()
    }

    /**
     * Use a reverse engagement for request/response operations.
     *
     * This connects the reader manager to an EngagementInstance created via
     * createReaderEngagement() for reverse engagement flows.
     */
    override suspend fun useReverseEngagement(engagement: EngagementInstance): IdkResult<Unit, IdkError> =
        try {
            log.info("Using reverse engagement: ${engagement.id}")

            // Create reverse connection strategy
            val reverseConnection =
                ReverseReaderConnection(
                    readerEngagement = engagement,
                    logService = logService,
                )

            // Store connection (will be established in waitForHolderConnection)
            connection = reverseConnection

            // Clear device engagement (not applicable for reverse)
            _deviceEngagement.value = null

            log.info("Reverse engagement configured, call waitForHolderConnection() to establish connection")
            Unit.asOkResult()
        } catch (expected: Exception) {
            log.error("Failed to use reverse engagement", exception = expected)
            IdkError
                .UNKNOWN_ERROR(
                    message = "Failed to use reverse engagement: ${expected.message}",
                    exception = expected,
                ).asErrorResult()
        }

    /**
     * Wait for holder to connect in reverse engagement.
     *
     * This blocks until the holder scans the reader's QR code and establishes a connection.
     */
    override suspend fun waitForHolderConnection(): IdkResult<Unit, IdkError> {
        return try {
            val reverseConnection =
                connection as? ReverseReaderConnection
                    ?: return IdkError
                        .ILLEGAL_ARGUMENT_ERROR(
                            message = "No reverse engagement configured. Call useReverseEngagement() first.",
                        ).asErrorResult()

            log.info("Waiting for holder to scan QR and connect...")

            // Establish connection (blocks until holder connects)
            val establishResult = reverseConnection.establish()
            if (establishResult.isErr) {
                return establishResult.error.asErrorResult()
            }

            // Extract channels from connection
            incomingDataChannel = reverseConnection.incomingChannel
            outgoingDataChannel = reverseConnection.outgoingChannel

            log.info("Holder connected via reverse engagement")
            _isConnected.value = true

            Unit.asOkResult()
        } catch (expected: Exception) {
            log.error("Failed waiting for holder connection", exception = expected)
            IdkError
                .UNKNOWN_ERROR(
                    message = "Failed waiting for holder connection: ${expected.message}",
                    exception = expected,
                ).asErrorResult()
        }
    }

    /**
     * Validate issuer authentication (MSO).
     *
     * **Not yet implemented** - This is a placeholder that always returns true.
     * Full implementation requires certificate validation, COSE signature verification,
     * and digest validation.
     */
    override fun validateIssuerAuthentication(document: Document): Boolean {
        log.warn("Issuer authentication validation not yet implemented - returning true")
        // TODO: Phase N - Implement full MSO validation
        return true
    }

    /**
     * Validate device authentication (MAC or signature).
     *
     * **Not yet implemented** - This is a placeholder that always returns true.
     * Full implementation requires MAC/signature verification using session keys
     * or device public key from MSO.
     */
    override fun validateDeviceAuthentication(document: Document): Boolean {
        log.warn("Device authentication validation not yet implemented - returning true")
        // TODO: Phase N - Implement full device authentication validation
        return true
    }

    // ====================================================================
    // Event Handling Implementation
    // ====================================================================

    private val _retrievalEventListeners = mutableSetOf<com.sphereon.mdoc.transfer.MdocRetrievalEvent.Listener>()

    override fun getRetrievalEventListeners(): Set<com.sphereon.mdoc.transfer.MdocRetrievalEvent.Listener> = _retrievalEventListeners.toSet()

    override fun addRetrievalEventListener(vararg listener: com.sphereon.mdoc.transfer.MdocRetrievalEvent.Listener) {
        _retrievalEventListeners.addAll(listener)
        log.debug("Added ${listener.size} retrieval event listener(s). Total: ${_retrievalEventListeners.size}")
    }

    override fun removeRetrievalEventListener(listener: com.sphereon.mdoc.transfer.MdocRetrievalEvent.Listener) {
        _retrievalEventListeners.remove(listener)
        log.debug("Removed retrieval event listener. Remaining: ${_retrievalEventListeners.size}")
    }

    override fun clearRetrievalEventListeners() {
        _retrievalEventListeners.clear()
        log.debug("Cleared all retrieval event listeners")
    }

    override fun dispatch(event: com.sphereon.mdoc.transfer.MdocRetrievalEvent) {
        log.debug("Dispatching reader event: ${event::class.simpleName} for engagement ${event.engagementId}")
        // Note: We don't use coroutines here to keep dispatch synchronous
        // Listeners can launch coroutines internally if needed
        kotlinx.coroutines.runBlocking {
            _retrievalEventListeners.toList().forEach { listener ->
                try {
                    when (event) {
                        is com.sphereon.mdoc.transfer.MdocRetrievalEvent.SessionDataReceived -> listener.onSessionDataReceived(event)
                        is com.sphereon.mdoc.transfer.MdocRetrievalEvent.SessionTerminationReceived -> listener.onSessionTerminationReceived(event)
                        is com.sphereon.mdoc.transfer.MdocRetrievalEvent.Error -> listener.onError(event)
                        else -> log.warn("Unhandled event type for reader: ${event::class.simpleName}")
                    }
                } catch (expected: Exception) {
                    log.error("Error in retrieval event listener", exception = expected)
                }
            }
        }
    }

    // ====================================================================
    // Cleanup
    // ====================================================================

    /**
     * Close the reader manager and clean up resources.
     *
     * This closes:
     * - Any active engagement instance
     * - Data channels
     * - Event listeners
     */
    override fun close() {
        log.info("Closing reader engagement manager")
        try {
            // Close data channels
            incomingDataChannel = null
            outgoingDataChannel = null

            // Clear session state
            sessionEncryption = null
            readerEphemeralKey = null

            // Reset connection state
            _deviceEngagement.value = null
            _isConnected.value = false

            // Clear event listeners
            clearRetrievalEventListeners()

            log.info("Reader engagement manager closed successfully")
        } catch (expected: Exception) {
            log.error("Error closing reader engagement manager", exception = expected)
        }
    }

    /**
     * Extract connection method from device engagement.
     * Uses transport registry to discover and parse retrieval methods.
     */
    private fun extractConnectionMethod(deviceEngagement: DeviceEngagement): ConnectionMethod? =
        deviceEngagement.deviceRetrievalMethods
            ?.firstNotNullOfOrNull { retrievalMethod ->
                // Use registry to get connection method factory
                val cmFactory = transportRegistry.getConnectionMethodFactory(retrievalMethod)
                if (cmFactory != null) {
                    val connectionMethod = cmFactory.create(retrievalMethod)
                    if (connectionMethod != null) {
                        log.info("Found supported connection method: ${connectionMethod.transportType}")
                    }
                    connectionMethod
                } else {
                    log.debug("No factory found for retrieval method type: ${retrievalMethod.type}")
                    null
                }
            }

    /**
     * Generate ephemeral key for reader.
     * Uses KeyManagerService to generate EC key.
     */
    private suspend fun resolveEphemeralKey(alias: String): ResolvedKeyInfoType<CoseKeyType> {
        val key =
            kms
                .getKmsBySignatureAlgorithm(SignatureAlgorithm.ECDSA_SHA256)
                .generateKeyAsync(alias = alias)
        return key.toManagedKeyInfo<CoseKeyType>(
            visibility = KeyVisibility.PRIVATE,
            keyEncoding = KeyEncoding.COSE,
        )
    }

    /**
     * Get the active reverse reader connection.
     *
     * Returns the ReverseReaderConnection instance if reverse engagement is active,
     * or null if not in reverse engagement mode.
     */
    override fun getReverseConnection(): ReverseReaderConnection? = connection as? ReverseReaderConnection

    /**
     @OptIn(ExperimentalObjCName::class)
     @ObjCName("for", exact = true)
     * Graph interface for accessing the reader engagement manager from DI.
     */
    @ContributesTo(SessionScope::class)
    interface Graph {
        val mdocReaderEngagementManager: MdocReaderEngagementManager
    }
}
