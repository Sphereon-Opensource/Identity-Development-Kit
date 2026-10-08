/*
 * Â© 2026 Sphereon International B.V.
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

package com.sphereon.mdoc.transport.restapi

import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncoder
import com.sphereon.cbor.CborItem
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.toException
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseKeyCborCodec
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.SessionDataCborCodec
import com.sphereon.mdoc.SessionEncryption
import com.sphereon.mdoc.SessionEstablishmentCborCodec
import com.sphereon.mdoc.SessionTranscriptCborCodec
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DeviceRequestCborCodec
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.engagement.DeviceEngagementCborCodec
import com.sphereon.mdoc.engagement.EngagementData
import com.sphereon.mdoc.transfer.reader.Handover
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import com.sphereon.mdoc.transfer.reader.ReaderEngagementCborCodec
import com.sphereon.mdoc.transfer.reader.RestApiHandover
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import com.sphereon.mdoc.transport.AbstractMdocTransport
import dev.whyoleg.cryptography.CryptographyProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * REST API transport implementation for mdoc data exchange.
 *
 * This implements the "Device Retrieval to Website" mechanism as specified in
 * ISO 18013-7 Annex A. The mdoc holder POSTs the DeviceResponse to a reader's
 * web endpoint.
 *
 * ## Protocol Flow (Holder Side)
 *
 * 1. **Scan QR Code**: Get device engagement with REST API URI
 * 2. **Open Connection**: Prepare HTTP client, validate URI
 * 3. **Receive Request**: Not applicable (holder initiates)
 * 4. **Send Response**: POST DeviceResponse to URI
 * 5. **Close**: Clean up resources
 *
 * ## Protocol Flow (Reader Side)
 *
 * 1. **Create Engagement**: Include REST API URI in device engagement
 * 2. **Wait for POST**: Holder POSTs DeviceResponse
 * 3. **Process Response**: Parse and validate DeviceResponse
 *
 * ## Stateless Design
 *
 * Unlike BLE/NFC, REST API is stateless:
 * - No persistent connection
 * - Single request/response cycle
 * - No session establishment handshake
 * - Connection reused across requests
 *
 * ## Security
 *
 * - Uses HTTPS only (enforced by RestApiConnectionMethod)
 * - DeviceResponse is encrypted at session level
 * - Server certificate validation via Ktor
 * - Optional certificate pinning (TODO)
 *
 * @param connectionMethod REST API connection method with URI
 * @param execution Session execution context
 * @param httpClient Ktor HTTP client for making requests
 * @param role Device role (MDOC or MDOC_READER)
 * @param engagementData Engagement data containing device/reader engagements and ephemeral keys
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RestApiTransfer", exact = true)
class RestApiTransport(
    connectionMethod: RestApiConnectionMethod,
    execution: SessionExecution,
    private val httpClient: HttpClient,
    private val cborEncoder: CborEncoder,
    private val deviceRequestCborCodec: DeviceRequestCborCodec,
    private val deviceEngagementCborCodec: DeviceEngagementCborCodec,
    private val readerEngagementCborCodec: ReaderEngagementCborCodec,
    private val sessionEstablishmentCborCodec: SessionEstablishmentCborCodec,
    private val sessionDataCborCodec: SessionDataCborCodec,
    private val sessionTranscriptCborCodec: SessionTranscriptCborCodec,
    private val coseKeyCborCodec: CoseKeyCborCodec,
    override val role: MdocRole,
    engagementData: EngagementData?,
) : AbstractMdocTransport<String>(connectionMethod, execution) {
    private val log = execution.log
    private val uri = connectionMethod.options.uri

    // REST API is stateless, but we track if open() was called
    private val mutex = Mutex()
    private var deviceRequest: ByteArray? = null
    private var deviceResponse: ByteArray? = null
    private var sessionEncryption: SessionEncryption? = null
    private var sessionTranscript: SessionTranscript? = null

    init {
        setEngagementData(engagementData)
    }

    override suspend fun open(
        senderKey: CoseKeyType,
        id: String,
        engagementData: EngagementData?,
    ): IdkResult<String, IdkErrorType> {
        return mutex.withLock {
            log.info("Opening REST API transfer: role=$role, uri=$uri")

            // Validate URI format
            if (!uri.startsWith("https://", ignoreCase = true)) {
                return@withLock Err(RestApiError.InsecureUri(uri).toIdkError())
            }
            if (this.engagementData != null) {
                require(engagementData == this.engagementData) { "We encountered a mismatch in engagement data" }
            }
            setEngagementData(engagementData)

            when (role) {
                MdocRole.MDOC -> {
                    // Holder side: Implement full ISO 18013-7 flow
                    // Engagement data is REQUIRED for holder role in production
                    try {
                        // Validate engagement data
                        val data =
                            engagementData
                                ?: return@withLock Err(
                                    RestApiError
                                        .MissingEngagementData(
                                            "MdocEngagementData required for REST API holder",
                                        ).toIdkError(),
                                )

                        val deviceEngagementEncoded = data.getDeviceEngagement()
                        val deviceEngagement = deviceEngagementEncoded.data()
                        val readerEngagement =
                            data.getReaderEngagement()
                                ?: return@withLock Err(
                                    RestApiError
                                        .MissingEngagementData(
                                            "ReaderEngagement required for REST API toApp",
                                        ).toIdkError(),
                                )
                        val readerEngagementOriginal =
                            readerEngagement.original
                                ?: return@withLock Err(
                                    RestApiError
                                        .MissingEngagementData(
                                            "ReaderEngagement original bytes required for REST API handover hashing. Use a parsed engagement URI or retain the emitted engagement bytes.",
                                        ).toIdkError(),
                                )

                        log.debug("Reader engagement:")
                        log.debug(readerEngagementOriginal.encodeToHex())

                        val holderKeyInfo = data.getEphemeralKey()

                        val cipherResult = verifyCipherSuite(deviceEngagement, readerEngagement)
                        if (cipherResult.isErr) {
                            return cipherResult.error.asErrorResult()
                        }

                        val keyResult = verifyKeyTypesAndCurves(deviceEngagement, readerEngagement)
                        if (keyResult.isErr) {
                            return keyResult.error.asErrorResult()
                        }
                        log.info(deviceEngagement.toString())

                        log.info("Starting REST API session establishment")

                        // CRITICAL: Create MINIMAL DeviceEngagement for BOTH message AND SessionTranscript
                        // Per ISO 18013-7, only version + security are needed for SessionTranscript
                        // The reader will use the DeviceEngagement from the message in its SessionTranscript
                        // So we MUST send a minimal one to ensure both parties have identical bytes

                        log.debug("=== Creating Minimal DeviceEngagement ===")

                        // Log the ORIGINAL DeviceEngagement for comparison
                        val originalBytes = cborEncoder.encode(deviceEngagementEncoded)
                        log.debug("ORIGINAL DeviceEngagement: size=${originalBytes.size} bytes")
                        log.debug("ORIGINAL DeviceEngagement hex: ${originalBytes.encodeToHex()}")
                        log.debug("ORIGINAL has originInfos: ${(deviceEngagement as? com.sphereon.mdoc.engagement.DeviceEngagement.V1_1)?.originInfos != null}")

                        // Create minimal CoseKey with ONLY cryptographic fields (kty, crv, x, y)
                        // Remove metadata fields like kid, alg, key_ops that cause SessionTranscript mismatch
//                        val originalEDeviceKey = deviceEngagement.security.eDeviceKeyBytes.data() as CoseKey
//                        log.debug("ORIGINAL eDeviceKey has kid: ${originalEDeviceKey.kid != null}, alg: ${originalEDeviceKey.alg != null}, key_ops: ${originalEDeviceKey.key_ops != null}")

                        // Create the DeviceEngagementMessage with Tag 24 wrapped bytes
                        val deviceEngagementMessage =
                            deviceEngagementCborCodec
                                .encodeMessageItem(deviceEngagementEncoded)
                                .getOrElse { throw it.toException() }

                        // Step 1: POST DeviceEngagement to reader endpoint
                        log.info("Sending DeviceEngagement to reader: $uri")
                        log.debug("Device engagement message hex:")
                        log.debug(deviceEngagementMessage.encodeToHex())
                        val response: HttpResponse
                        try {
                            response =
                                httpClient.post(uri) {
                                    contentType(ContentType.Application.Cbor)
                                    header(HttpHeaders.Accept, ContentType.Application.Cbor.toString())
                                    header("User-Agent", "mdoc-holder/1.0")
                                    setBody(deviceEngagementMessage)
                                }

                            if (!response.status.isSuccess()) {
                                log.error("Reader returned error status: ${response.status}")
                                return@withLock Err(
                                    RestApiError
                                        .ServerError(
                                            uri = uri,
                                            statusCode = response.status.value,
                                            serverMessage = "Server returned ${response.status}",
                                    ).toIdkError(),
                                )
                            }
                            requireCborResponse(response, "SessionEstablishment")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            log.error("device engagement failed", e)
                            return@withLock Err(
                                RestApiError
                                    .ConnectionFailed(
                                        uri = uri,
                                        throwable = e,
                                    ).toIdkError(),
                            )
                        }

                        // Step 2: Receive SessionEstablishment response
                        val sessionDataBytes = readBoundedResponseBody(response)
                        log.info("Received SessionEstablishment from reader (${sessionDataBytes.size} bytes)")
                        log.debug(sessionDataBytes.encodeToHex())

                        // Step 3: Create session transcript (ISO 18013-7 Annex A.8)
                        // For REST API: SessionTranscript = [DeviceEngagementBytes, EReaderKeyBytes, ReaderEngagementBytesHash]
                        // The handover is the SHA-256 hash of the Tag 24 wrapped ReaderEngagementBytes
                        // Per ISO 18013-7 Annex A, ReaderEngagementBytes = #6.24(bstr .cbor ReaderEngagement)

                        // CRITICAL: For REST API handover, we need SHA-256 of Tag 24 wrapped ReaderEngagement
                        // Per ISO 18013-7 Annex A.8: Handover = ReaderEngagementBytesHash
                        // Per ISO 18013-5 section 6.4.3.2: ReaderEngagementBytes = #6.24(bstr .cbor ReaderEngagement)
                        //
                        // The QR code format (ISO 18013-7 Annex A.1) does NOT require Tag 24 wrapping.
                        // However, for the SessionTranscript hash, we MUST use the Tag 24 wrapped format
                        // as defined in ISO 18013-5 section 6.4.3.2.
                        //
                        // Some readers (like RDW) provide unwrapped ReaderEngagement in QR codes,
                        // so we need to manually wrap it with Tag 24 before hashing.
                        val readerEngagementBytesForHash =
                            readerEngagementCborCodec
                                .encodeTag24(readerEngagement)
                                .getOrElse { throw it.toException() }

                        val wasAlreadyTag24Wrapped = readerEngagementBytesForHash.contentEquals(readerEngagementOriginal)
                        if (wasAlreadyTag24Wrapped) {
                            log.debug("ReaderEngagement is already Tag 24 wrapped (${readerEngagementOriginal.size} bytes)")
                        } else {
                            log.debug("ReaderEngagement is not Tag 24 wrapped, codec wrapped retained original bytes (${readerEngagementBytesForHash.size} bytes)")
                        }

                        // Hash the Tag 24 wrapped bytes
                        val readerEngagementHash = hash(readerEngagementBytesForHash, DigestAlg.SHA256)

                        log.debug("ReaderEngagement bytes for hash (${readerEngagementBytesForHash.size}):")
                        log.debug(readerEngagementBytesForHash.encodeToHex())
                        log.debug("ReaderEngagement hash:")
                        log.debug(readerEngagementHash.encodeToHex())

                        // Create REST API handover with the hash
                        val restApiHandover = RestApiHandover(readerEngagementHash)

                        log.debug("restApiHandover: ")
                        log.debug(cborEncoder.encode(CborByteString(restApiHandover.readerEngagementHash)).encodeToHex())

                        // CRITICAL: Verify eReaderKey is correctly extracted and not null
                        val eReaderKey = readerEngagement.security.eReaderKeyBytes
                        log.debug("=== eReaderKey Verification ===")

                        // Create session transcript with proper handover
                        // Per ISO 18013-7 A.8: SessionTranscript = [DeviceEngagementBytes, EReaderKeyBytes, ReaderEngagementBytesHash]
                        @Suppress("UNCHECKED_CAST")
                        val sessionTranscript =
                            SessionTranscript(
                                deviceEngagement = deviceEngagementEncoded,
                                eReaderKey = eReaderKey,
                                handover = restApiHandover as Handover<*, CborItem<*>>,
                                original = null,
                            )
                        this.sessionTranscript = sessionTranscript

                        log.debug("SessionTranscript created successfully")
                        log.debug("SessionTranscript.deviceEngagement: ${sessionTranscript.deviceEngagement != null}")
                        log.debug("SessionTranscript.eReaderKey: ${sessionTranscript.eReaderKey != null}")
                        log.debug("SessionTranscript.handover type: ${sessionTranscript.handover::class.simpleName}")

                        val sessionTranscriptBytes =
                            sessionTranscriptCborCodec
                                .encodeTag24(sessionTranscript)
                                .getOrElse { throw it.toException() }

                        log.info("Session transcript created for REST API")
                        log.debug("=== SessionTranscript Encoding ===")
                        log.debug("SessionTranscript total length: ${sessionTranscriptBytes.size} bytes")
                        log.debug("SessionTranscript hex:")
                        log.debug(sessionTranscriptBytes.encodeToHex())

                        // Parse the SessionTranscript array to verify structure
                        log.debug("SessionTranscript structure verification:")
                        log.debug("- Element [0] = DeviceEngagement (should be CBOR bytes)")
                        log.debug("- Element [1] = EReaderKey (CborEncodedItem<CoseKey> - could be Tag 24 wrapped)")
                        log.debug("- Element [2] = Handover (RestApiHandover = hash bytes)")

                        // Verify the SessionTranscript starts with array tag
                        if (sessionTranscriptBytes.isNotEmpty()) {
                            val firstByte = sessionTranscriptBytes[0].toUByte().toInt()
                            val isArray = (firstByte and 0xE0) == 0x80 // CBOR array major type is 0b100xxxxx
                            log.debug("SessionTranscript starts with array: $isArray (first byte: 0x${firstByte.toString(16)})")
                            if (isArray) {
                                val arrayLength = firstByte and 0x1F
                                log.debug("Array length: $arrayLength (should be 3)")
                            }
                        }
                        log.debug("=== End SessionTranscript Encoding ===")

                        // Step 4: Set up session encryption
                        val sessionEncryptionInstance =
                            SessionEncryption
                                .Builder()
                                .withCborCodecs(
                                    sessionEstablishmentCborCodec = sessionEstablishmentCborCodec,
                                    sessionDataCborCodec = sessionDataCborCodec,
                                    sessionTranscriptCborCodec = sessionTranscriptCborCodec,
                                    coseKeyCborCodec = coseKeyCborCodec,
                                ).withProvider(CryptographyProvider.Default)
                                .withSessionTranscriptBytes(sessionTranscriptBytes)
                                .withSelfRole(MdocRole.MDOC) // We are the holder
                                .withSelfPrivateEphemeralKey(holderKeyInfo)
                                .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerEngagement.security.eReaderKeyBytes.data()))
                                .build()

                        log.info("Session encryption established")

                        // Step 5: Decrypt DeviceRequest
                        // CRITICAL: Use decrypt() which returns DecryptResult with decrypted sessionData
                        // decryptSessionEstablishment() returns SessionEstablishment with ENCRYPTED data
                        val decryptResult = sessionEncryptionInstance.decrypt(sessionDataBytes)
                        val deviceRequestData =
                            decryptResult.sessionData.data?.value
                                ?: throw IllegalStateException("No data in decrypted session data")

                        log.info("DeviceRequest decrypted (${deviceRequestData.size} bytes)")

                        // Step 6: Parse and cache DeviceRequest
                        val parsedDeviceRequest =
                            deviceRequestCborCodec
                                .decode(deviceRequestData)
                                .getOrElse { throw it.toException() }
                                .value
                        log.info("DeviceRequest parsed: ${parsedDeviceRequest.effectiveDocRequests().size} document requests")

                        deviceRequest = deviceRequestData // Cache raw bytes

                        // Store session encryption for later use in messageSendBlocking
                        this.sessionEncryption = sessionEncryptionInstance

                        markOpen()
                        Ok(id)
                    } catch (e: CancellationException) {
                        // Propagate cancellation without wrapping
                        log.info("REST API transfer opening was cancelled")
                        throw e
                    } catch (e: RestApiError) {
                        log.error("REST API error during open", exception = e)
                        Err(e.toIdkError())
                    } catch (expected: Exception) {
                        log.error("Failed to open REST API connection", exception = expected)
                        Err(RestApiError.ConnectionFailed(uri, expected).toIdkError())
                    }
                }

                MdocRole.MDOC_READER -> {
                    // Reader side: Just mark as open
                    // Reader doesn't initiate HTTP requests - it waits for holder POSTs
                    try {
                        markOpen()
                        Ok(id)
                    } catch (e: CancellationException) {
                        // Propagate cancellation without wrapping
                        log.info("REST API transfer opening was cancelled")
                        throw e
                    } catch (expected: Exception) {
                        log.error("Failed to open REST API connection", exception = expected)
                        Err(RestApiError.ConnectionFailed(uri, expected).toIdkError())
                    }
                }
            }
        }
    }

    private fun verifyKeyTypesAndCurves(
        deviceEngagement: DeviceEngagement,
        readerEngagement: ReaderEngagement,
    ): IdkResult<Unit, IdkErrorType> {
        // Verify key types and curves match cipher suite requirements
        log.debug("=== KEY TYPE & CURVE VERIFICATION ===")
        val deviceKey = deviceEngagement.security.eDeviceKeyBytes.data()
        val readerKey = readerEngagement.security.eReaderKeyBytes.data()

        log.debug("Device key type (kty): ${deviceKey.kty.value} (expected 2 = EC2)")
        log.debug("Device key curve (crv): ${deviceKey.crv?.value} (expected 1 = P-256)")
        log.debug("Reader key type (kty): ${readerKey.kty.value} (expected 2 = EC2)")
        log.debug("Reader key curve (crv): ${readerKey.crv?.value} (expected 1 = P-256)")

        // Per ISO 18013-5: Cipher Suite 1 requires EC2 keys on P-256 curve
        if (deviceKey.kty.value != 2L) {
            log.error("CRITICAL: Device key type mismatch! Expected kty=2 (EC2), got ${deviceKey.kty.value}")
            return Err(
                RestApiError
                    .ConnectionFailed(
                        uri = uri,
                        throwable = IllegalStateException("Device key type mismatch! Expected kty=2 (EC2), got ${deviceKey.kty.value}"),
                    ).toIdkError(),
            )
        }

        if (readerKey.kty.value != 2L) {
            log.error("CRITICAL: Reader key type mismatch! Expected kty=2 (EC2), got ${readerKey.kty.value}")
            return Err(
                RestApiError
                    .ConnectionFailed(
                        uri = uri,
                        throwable = IllegalStateException("Reader key type mismatch! Expected kty=2 (EC2), got ${readerKey.kty.value}"),
                    ).toIdkError(),
            )
        }

        if (deviceKey.crv?.value != 1L) {
            log.error("CRITICAL: Device curve mismatch! Expected crv=1 (P-256), got ${deviceKey.crv?.value}")
            return Err(
                RestApiError
                    .ConnectionFailed(
                        uri = uri,
                        throwable = IllegalStateException("Device curve mismatch! Expected crv=1 (P-256), got ${deviceKey.crv?.value}"),
                    ).toIdkError(),
            )
        }

        if (readerKey.crv?.value != 1L) {
            log.error("CRITICAL: Reader curve mismatch! Expected crv=1 (P-256), got ${readerKey.crv?.value}")
            return Err(
                RestApiError
                    .ConnectionFailed(
                        uri = uri,
                        throwable = IllegalStateException("Reader curve mismatch! Expected crv=1 (P-256), got ${readerKey.crv?.value}"),
                    ).toIdkError(),
            )
        }

        // Verify P-256 key coordinate sizes (32 bytes each for X and Y)
        log.debug("Device key X coordinate: ${deviceKey.x?.value?.size} bytes (expected 32 for P-256)")
        log.debug("Device key Y coordinate: ${deviceKey.y?.value?.size} bytes (expected 32 for P-256)")
        log.debug("Reader key X coordinate: ${readerKey.x?.value?.size} bytes (expected 32 for P-256)")
        log.debug("Reader key Y coordinate: ${readerKey.y?.value?.size} bytes (expected 32 for P-256)")

        if (deviceKey.x?.value?.size != 32 || deviceKey.y?.value?.size != 32) {
            log.error("CRITICAL: Device key has invalid P-256 coordinate size! X=${deviceKey.x?.value?.size}, Y=${deviceKey.y?.value?.size}")
            return Err(
                RestApiError
                    .ConnectionFailed(
                        uri = uri,
                        throwable = IllegalStateException("Device key has invalid P-256 coordinate size! X=${deviceKey.x?.value?.size}, Y=${deviceKey.y?.value?.size}"),
                    ).toIdkError(),
            )
        }

        if (readerKey.x?.value?.size != 32 || readerKey.y?.value?.size != 32) {
            log.error("CRITICAL: Reader key has invalid P-256 coordinate size! X=${readerKey.x?.value?.size}, Y=${readerKey.y?.value?.size}")
            return Err(
                RestApiError
                    .ConnectionFailed(
                        uri = uri,
                        throwable = IllegalStateException("Reader key has invalid P-256 coordinate size! X=${readerKey.x?.value?.size}, Y=${readerKey.y?.value?.size}"),
                    ).toIdkError(),
            )
        }

        log.info("Key types and curves match cipher suite requirements")
        log.debug("=== END KEY TYPE & CURVE VERIFICATION ===")
        return Unit.asOkResult()
    }

    override suspend fun messageReceiveBlocking(): ByteArray {
        requireOpen()

        return mutex.withLock {
            log.debug("Receiving message over REST API")

            when (role) {
                MdocRole.MDOC -> {
                    // Holder side: DeviceRequest comes from local storage
                    // (provided by reader via QR code or other out-of-band means)
                    val request = deviceRequest
                    if (request == null) {
                        log.error("No DeviceRequest available for holder")
                        throw IllegalStateException("DeviceRequest not set for REST API holder")
                    }
                    log.info("Returning cached DeviceRequest (${request.size} bytes)")
                    request
                }

                MdocRole.MDOC_READER -> {
                    // Reader side: Wait for holder to POST DeviceResponse
                    // This is typically handled by a web server, not the reader client
                    log.error("Reader should not call receive() - holder POSTs to reader's server")
                    throw UnsupportedOperationException(
                        "REST API reader receives via HTTP server, not client.receive()",
                    )
                }
            }
        }
    }

    override suspend fun messageSendBlocking(message: ByteArray) {
        requireOpen()

        mutex.withLock {
            log.debug("Sending ${message.size} bytes over REST API to $uri")

            when (role) {
                MdocRole.MDOC -> {
                    // Holder side: POST DeviceResponse to reader's URI
                    try {
                        val encryption =
                            sessionEncryption
                                ?: throw IllegalStateException("Session encryption not initialized. Call open() first.")
                        val sessionData = encryption.encryptAsSessionData(message)
                        val sessionDataBytes =
                            sessionDataCborCodec
                                .encode(sessionData)
                                .getOrElse { throw it.toException() }

                        val response =
                            httpClient.post(uri) {
                                contentType(ContentType.Application.Cbor)
                                header(HttpHeaders.Accept, ContentType.Application.Cbor.toString())
                                header("User-Agent", "mdoc-holder/1.0")
                                setBody(sessionDataBytes)
                            }

                        if (response.status.isSuccess()) {
                            log.info("Successfully sent DeviceResponse (${message.size} bytes), status: ${response.status}")
                            deviceResponse = message
                        } else {
                            log.error("Server returned error status: ${response.status}")
                            throw RestApiError.ServerError(
                                uri = uri,
                                statusCode = response.status.value,
                                serverMessage = "Server returned ${response.status}",
                            )
                        }
                        requireCborResponse(response, "SessionData")
                    } catch (e: CancellationException) {
                        // Propagate cancellation without wrapping
                        log.debug("Message send was cancelled")
                        throw e
                    } catch (e: RestApiError) {
                        throw e
                    } catch (expected: Exception) {
                        log.error("Failed to send DeviceResponse", exception = expected)
                        throw RestApiError.NetworkError(uri, expected)
                    }
                }

                MdocRole.MDOC_READER -> {
                    // Reader side: DeviceRequest is typically sent via QR code,
                    // not via HTTP POST. But we can cache it for the holder.
                    log.info("Caching DeviceRequest (${message.size} bytes) for holder")
                    deviceRequest = message
                }
            }
        }
    }

    private fun verifyCipherSuite(
        deviceEngagement: DeviceEngagement,
        readerEngagement: ReaderEngagement,
    ): IdkResult<Unit, IdkErrorType> {
        // Per ISO 18013-5 Table 1:
        // Cipher Suite 1 = ECDH-ES + HKDF-256 + AES-256-GCM
        // - Key agreement: ECDH (Elliptic Curve Diffie-Hellman) over P-256
        // - Key derivation: HKDF with SHA-256
        // - Authenticated encryption: AES-256-GCM with 128-bit authentication tag
        // - IV: 12 bytes (role identifier + 32-bit counter)
        // CRITICAL: Verify cipher suite alignment
        log.debug("=== CIPHER SUITE VERIFICATION ===")
        log.debug("DeviceEngagement version: ${deviceEngagement.version}")
        log.debug("DeviceEngagement cipher suite: ${deviceEngagement.security.cipherSuite}")
        log.debug("ReaderEngagement version: ${readerEngagement.version}")
        log.debug("ReaderEngagement cipher suite: ${readerEngagement.security.cipherSuite}")

        if (deviceEngagement.security.cipherSuite != 1u) {
            log.warn("WARNING: DeviceEngagement cipher suite is ${deviceEngagement.security.cipherSuite}, expected 1 (ECDH-ES + HKDF-256 + AES-256-GCM)")
            return Err(
                RestApiError
                    .ConnectionFailed(
                        uri = uri,
                        throwable =
                            IllegalStateException(
                                "DeviceEngagement cipher suite not supported: device=${deviceEngagement.security.cipherSuite}, reader=${readerEngagement.security.cipherSuite}",
                            ),
                    ).toIdkError(),
            )
        } else if (readerEngagement.security.cipherSuite != 1u) {
            log.warn("WARNING: ReaderEngagement cipher suite is ${readerEngagement.security.cipherSuite}, expected 1 (ECDH-ES + HKDF-256 + AES-256-GCM)")
            return Err(
                RestApiError
                    .ConnectionFailed(
                        uri = uri,
                        throwable =
                            IllegalStateException(
                                "ReaderEngagement cipher suite not supported: device=${deviceEngagement.security.cipherSuite}, reader=${readerEngagement.security.cipherSuite}",
                            ),
                    ).toIdkError(),
            )
        } else if (deviceEngagement.security.cipherSuite != readerEngagement.security.cipherSuite) {
            log.error("CRITICAL: Cipher suite mismatch! Device=${deviceEngagement.security.cipherSuite}, Reader=${readerEngagement.security.cipherSuite}")
            return Err(
                RestApiError
                    .ConnectionFailed(
                        uri = uri,
                        throwable = IllegalStateException("Cipher suite mismatch: device=${deviceEngagement.security.cipherSuite}, reader=${readerEngagement.security.cipherSuite}"),
                    ).toIdkError(),
            )
        }

        log.info("Cipher suite alignment verified: ${deviceEngagement.security.cipherSuite} (ECDH-ES + HKDF-256 + AES-256-GCM)")
        log.debug("=== END CIPHER SUITE VERIFICATION ===")
        return Unit.asOkResult()
    }

    override fun close() {
        log.info("Closing REST API transfer")

        // REST API is stateless, so just clean up local state
        deviceRequest = null
        deviceResponse = null
        sessionEncryption = null
        sessionTranscript = null

        super.close()
    }

    /**
     * Set the DeviceRequest that the holder should respond to.
     * This is typically obtained from the QR code or other out-of-band means.
     *
     * @param request The DeviceRequest bytes
     */
    suspend fun setDeviceRequest(request: ByteArray) {
        mutex.withLock {
            log.info("Setting DeviceRequest (${request.size} bytes)")
            deviceRequest = request
        }
    }

    override fun getContext(key: String): Any? =
        when (key) {
            "sessionTranscript" -> sessionTranscript
            else -> null
        }

    /**
     * Session-establishment data is supplied by a remote reader. Keep the
     * response bounded before it reaches the CBOR/session decryptors so an
     * otherwise valid HTTP response cannot become an unbounded allocation.
     */
    private suspend fun readBoundedResponseBody(response: HttpResponse): ByteArray {
        val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        require(declaredLength == null || declaredLength in 0..MAX_RESPONSE_BODY_BYTES) {
            "REST API response body exceeds the configured maximum"
        }

        val channel = response.bodyAsChannel()
        val chunks = mutableListOf<ByteArray>()
        val buffer = ByteArray(minOf(8 * 1024L, MAX_RESPONSE_BODY_BYTES).toInt())
        var total = 0L
        try {
            while (true) {
                val count = channel.readAvailable(buffer)
                if (count < 0) break
                if (count == 0) continue
                require(total <= MAX_RESPONSE_BODY_BYTES - count.toLong()) {
                    "REST API response body exceeds the configured maximum"
                }
                total += count
                chunks += buffer.copyOf(count)
            }
        } catch (expected: Exception) {
            try {
                channel.cancel(expected)
            } catch (_: Exception) {
                // Preserve the primary read/size failure.
            }
            throw expected
        }

        val result = ByteArray(total.toInt())
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(result, destinationOffset = offset)
            offset += chunk.size
        }
        return result
    }

    /**
     * Annex A exchanges CBOR data items. A successful HTTP status is not enough:
     * accepting a text or JSON response would pass a misrouted endpoint into the
     * session decryptor and make protocol errors indistinguishable from data.
     */
    private fun requireCborResponse(response: HttpResponse, messageType: String) {
        val responseType = response.contentType()?.withoutParameters()
        require(responseType == ContentType.Application.Cbor) {
            "REST API $messageType response must use Content-Type application/cbor, got ${response.contentType()}"
        }
    }

    private companion object {
        private const val MAX_RESPONSE_BODY_BYTES = 10L * 1024L * 1024L
    }
}
