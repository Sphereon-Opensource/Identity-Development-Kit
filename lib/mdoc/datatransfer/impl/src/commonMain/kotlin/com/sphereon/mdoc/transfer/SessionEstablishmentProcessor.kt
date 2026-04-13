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

package com.sphereon.mdoc.transfer

import dev.whyoleg.cryptography.CryptographyProvider
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.HasToCbor
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.LogService
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.SessionEncryption
import com.sphereon.mdoc.SessionEstablishment
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.engagement.EngagementInstance
import com.sphereon.mdoc.logging.IMdocDebugLogger
import com.sphereon.mdoc.transfer.reader.Handover
import com.sphereon.mdoc.transfer.reader.QrHandover
import com.sphereon.mdoc.transfer.reader.SessionTranscript

/**
 * Result of processing a SessionEstablishment message.
 *
 * Contains the extracted DeviceRequest, the session transcript used for
 * signature verification, and the initialized session encryption context.
 */
data class SessionEstablishmentResult(
    val deviceRequest: DeviceRequest,
    val sessionTranscript: CborEncodedItem<SessionTranscript>,
    val sessionEncryption: SessionEncryption
)

/**
 * Processes SessionEstablishment data to extract DeviceRequest.
 *
 * This class handles the complex process of:
 * 1. Decoding the CBOR-encoded SessionEstablishment message
 * 2. Building the SessionTranscript for HKDF salt derivation
 * 3. Resolving ephemeral keys and performing ECDH (standard or native keychain)
 * 4. Initializing SessionEncryption with derived keys
 * 5. Decrypting the DeviceRequest from the encrypted session data
 *
 * Per ISO 18013-5:
 * - SessionTranscript = [DeviceEngagementBytes, EReaderKeyBytes, Handover]
 * - SessionTranscriptBytes = #6.24(bstr .cbor SessionTranscript)
 * - HKDF salt = SHA-256(SessionTranscriptBytes)
 *
 * @param debugLogger Optional debug logger for ISO 18013-5 debugging
 * @param log Log service for standard logging
 * @param logId Identifier prefix for log messages
 */
internal class SessionEstablishmentProcessor(
    private val debugLogger: IMdocDebugLogger?,
    private val log: LogService,
    private val logId: String
) {
    /**
     * Process SessionEstablishment data to extract DeviceRequest.
     *
     * @param readerData Raw CBOR-encoded SessionEstablishment from reader
     * @param engagement The engagement instance containing device engagement and ephemeral keys
     * @return IdkResult containing SessionEstablishmentResult or an error
     */
    suspend fun process(
        readerData: ByteArray,
        engagement: EngagementInstance
    ): IdkResult<SessionEstablishmentResult, IdkError> {
        return try {
            // 1. Decode session establishment
            debugLogger?.logSessionEstablishment("Received SessionEstablishment from reader", readerData)
            log.info("$logId: Reader data received. Will decode session establishment")

            val sessionEstablishment = SessionEstablishment.decodeCbor(readerData)
            log.info("$logId: Session establishment decoded")

            logEReaderKey(sessionEstablishment)

            // 2. Build session transcript
            val (transcript, transcriptBytes) = buildSessionTranscript(sessionEstablishment, engagement)

            // 3. Resolve ephemeral key and perform ECDH
            val preComputedSharedSecret = resolveEphemeralKeyAndEcdh(engagement, sessionEstablishment)

            // 4. Initialize session encryption
            val sessionEncryption = initializeSessionEncryption(
                transcriptBytes = transcriptBytes,
                engagement = engagement,
                sessionEstablishment = sessionEstablishment,
                preComputedSharedSecret = preComputedSharedSecret
            )

            // 5. Decrypt device request
            val deviceRequest = decryptDeviceRequest(sessionEncryption, readerData)

            SessionEstablishmentResult(
                deviceRequest = deviceRequest,
                sessionTranscript = transcript,
                sessionEncryption = sessionEncryption
            ).asOkResult()
        } catch (e: Exception) {
            log.error("$logId: Error processing session establishment: ${e.message}", exception = e)
            IdkError.UNKNOWN_ERROR(
                message = "Failed to process session establishment: ${e.message}",
                exception = e
            ).asErrorResult()
        }
    }

    /**
     * Log EReaderKey details for debugging.
     */
    private fun logEReaderKey(sessionEstablishment: SessionEstablishment) {
        val eReaderKeyBytes = sessionEstablishment.encodedReaderKey.encodeCbor()
        debugLogger?.logEReaderKeyBytes("EReaderKeyBytes from SessionEstablishment (Tag 24 wrapped)", eReaderKeyBytes)

        val eReaderKey = sessionEstablishment.encodedReaderKey.data()
        debugLogger?.logEReaderKey("EReaderKey content (COSE_Key)", eReaderKey.encodeCbor())
    }

    /**
     * Build the SessionTranscript per ISO 18013-5.
     *
     * SessionTranscript = [DeviceEngagementBytes, EReaderKeyBytes, Handover]
     *
     * @return Pair of encoded transcript and raw bytes for HKDF
     */
    private fun buildSessionTranscript(
        sessionEstablishment: SessionEstablishment,
        engagement: EngagementInstance
    ): Pair<CborEncodedItem<SessionTranscript>, ByteArray> {
        // Log DeviceEngagementBytes
        val deviceEngagementEncoded = engagement.getDeviceEngagement()
        val deviceEngagementBytes = deviceEngagementEncoded.encodeCbor()
        debugLogger?.logDeviceEngagementBytes("DeviceEngagementBytes for SessionTranscript (Tag 24 wrapped)", deviceEngagementBytes)

        // Verify DeviceEngagement bytes consistency (critical for iOS interop)
        logDeviceEngagementBytesVerification(deviceEngagementEncoded)

        // Log EDeviceKey for ECDH comparison
        val eDeviceKeyFromEngagement = deviceEngagementEncoded.data().security.eDeviceKeyBytes.data()
        val eDeviceKeyBytes = CoseKey.fromDTO(eDeviceKeyFromEngagement).encodeCbor()
        debugLogger?.logEDeviceKey("EDeviceKey from DeviceEngagement (for ECDH comparison)", eDeviceKeyBytes)

        // Log Handover
        debugLogger?.logHandover("Handover for SessionTranscript", engagement.handover)

        log.info("$logId: Session establishment decoded, will generate session transcript")

        val transcript = SessionTranscript(
            deviceEngagement = deviceEngagementEncoded,
            eReaderKey = sessionEstablishment.encodedReaderKey,
            handover = engagement.handover?.let { Handover.decodeCbor(it) } ?: QrHandover() as Handover<*, CborItem<*>>,
            original = null
        )

        // Log raw SessionTranscript
        val rawTranscriptBytes = transcript.encodeCbor()
        debugLogger?.logSessionTranscript("Raw SessionTranscript (CBOR array, starts with 0x83)", rawTranscriptBytes)

        val encodedTranscript = CborEncodedItem.fromData(transcript)

        // Per ISO 18013-5: SessionTranscriptBytes = #6.24(bstr .cbor SessionTranscript)
        val transcriptBytes = encodedTranscript.encodeCbor()
        debugLogger?.logSessionTranscriptBytes("SessionTranscriptBytes for HKDF (Tag 24 wrapped, starts with 0xd818)", transcriptBytes)

        // Log iOS interoperability debug info
        logIosInteropDebugInfo(deviceEngagementBytes, sessionEstablishment, transcriptBytes, engagement)

        return encodedTranscript to transcriptBytes
    }

    /**
     * Verify DeviceEngagement bytes consistency - critical for iOS interoperability.
     */
    private fun logDeviceEngagementBytesVerification(deviceEngagementEncoded: CborEncodedItem<*>) {
        val rawDeviceEngagementInTag24 = deviceEngagementEncoded.value.taggedItem.value
        val data = deviceEngagementEncoded.data()
        @Suppress("UNCHECKED_CAST")
        val reEncodedDeviceEngagement = (data as? HasToCbor<CborItem<*>>)?.encodeCbor() ?: return
        val bytesMatch = rawDeviceEngagementInTag24.contentEquals(reEncodedDeviceEngagement)

        debugLogger?.logText(
            "DEVICE_ENGAGEMENT_BYTES_VERIFY",
            """
            |Verifying DeviceEngagement bytes consistency (CRITICAL for iOS interop):
            |  - Bytes in Tag 24 wrapper: ${rawDeviceEngagementInTag24.size} bytes
            |  - Re-encoded from object: ${reEncodedDeviceEngagement.size} bytes
            |  - BYTES MATCH: $bytesMatch
            |
            |If BYTES MATCH is false, there's a bug in Android's byte preservation.
            |If BYTES MATCH is true, iOS must be re-encoding instead of using original QR bytes.
            |
            |First 32 bytes comparison:
            |  - Tag 24 inner: ${rawDeviceEngagementInTag24.take(32).toByteArray().encodeToHex()}
            |  - Re-encoded:   ${reEncodedDeviceEngagement.take(32).toByteArray().encodeToHex()}
            """.trimMargin(),
            "bytesMatch=$bytesMatch"
        )
    }

    /**
     * Log iOS interoperability debug information.
     */
    private fun logIosInteropDebugInfo(
        deviceEngagementBytes: ByteArray,
        sessionEstablishment: SessionEstablishment,
        transcriptBytes: ByteArray,
        engagement: EngagementInstance
    ) {
        val eReaderKeyBytesForLog = sessionEstablishment.encodedReaderKey.encodeCbor()
        debugLogger?.logText(
            "IOS_INTEROP_DEBUG",
            """
            |iOS Interoperability Debug Info - Compare with iOS reader logs:
            |If decryption fails, iOS might be using different bytes for SessionTranscript
            |
            |Component sizes (iOS should match these exactly):
            |  - DeviceEngagementBytes: ${deviceEngagementBytes.size} bytes
            |  - EReaderKeyBytes: ${eReaderKeyBytesForLog.size} bytes
            |  - Handover: ${if (engagement.handover == null) "null (QR)" else "${engagement.handover!!.size} bytes"}
            |  - SessionTranscriptBytes (total): ${transcriptBytes.size} bytes
            |
            |First 16 bytes of each (for quick comparison):
            |  - DeviceEngagementBytes: ${deviceEngagementBytes.take(16).toByteArray().encodeToHex()}...
            |  - EReaderKeyBytes: ${eReaderKeyBytesForLog.take(16).toByteArray().encodeToHex()}...
            |  - SessionTranscriptBytes: ${transcriptBytes.take(16).toByteArray().encodeToHex()}...
            """.trimMargin(),
            "See values above"
        )
    }

    /**
     * Resolve ephemeral key and perform ECDH if needed.
     *
     * When the ephemeral key's 'd' parameter is missing (key stored in secure enclave),
     * we use native keychain ECDH via the key alias.
     *
     * @return Pre-computed shared secret if native ECDH was used, null otherwise
     */
    private suspend fun resolveEphemeralKeyAndEcdh(
        engagement: EngagementInstance,
        sessionEstablishment: SessionEstablishment
    ): ByteArray? {
        val ephemeralKeyInfo = engagement.data.getEphemeralKey()
        val ephemeralKey = ephemeralKeyInfo.key
        val readerKey = CoseKey.fromEncodedCborItem(sessionEstablishment.encodedReaderKey)
        val readerKeyInfo = ResolvedKeyInfo.fromKey(readerKey)

        // Check if we need to use native ECDH (when 'd' is missing)
        return if (ephemeralKey.d == null) {
            log.info("$logId: Ephemeral key missing 'd' parameter, attempting native ECDH")

            val alias = ephemeralKeyInfo.alias
            if (alias != null) {
                // Convert reader's COSE key to JWK for native ECDH
                val readerJwk = com.sphereon.crypto.core.CoseJoseKeyMappingService.toResolvedJwkKeyInfo(readerKeyInfo).key
                log.info("$logId: Attempting native keychain ECDH with alias: $alias")

                val result = com.sphereon.crypto.kms.performNativeKeychainEcdh(alias, readerJwk)
                if (result != null) {
                    log.info("$logId: Native keychain ECDH succeeded, shared secret size: ${result.size}")
                } else {
                    log.error("$logId: Native keychain ECDH failed or not available on this platform")
                }
                result
            } else {
                log.error("$logId: Ephemeral key missing 'd' and no alias available for native ECDH")
                null
            }
        } else {
            null // Standard ECDH will be used
        }
    }

    /**
     * Initialize SessionEncryption with derived keys.
     */
    private suspend fun initializeSessionEncryption(
        transcriptBytes: ByteArray,
        engagement: EngagementInstance,
        sessionEstablishment: SessionEstablishment,
        preComputedSharedSecret: ByteArray?
    ): SessionEncryption {
        val ephemeralKeyInfo = engagement.data.getEphemeralKey()
        val readerKey = CoseKey.fromEncodedCborItem(sessionEstablishment.encodedReaderKey)
        val readerKeyInfo = ResolvedKeyInfo.fromKey(readerKey)

        val builder = SessionEncryption.Builder()
            .withProvider(CryptographyProvider.Default)
            .withSessionTranscriptBytes(transcriptBytes)
            .withSelfRole(MdocRole.MDOC)
            .withSelfPrivateEphemeralKey(ephemeralKeyInfo)
            .withRemotePublicEphemeralKey(readerKeyInfo)
            .withDebugLogger(debugLogger)

        // Add pre-computed shared secret if we used native ECDH
        if (preComputedSharedSecret != null) {
            builder.withPreComputedSharedSecret(preComputedSharedSecret)
        }

        return builder.build()
    }

    /**
     * Decrypt the DeviceRequest from SessionEstablishment data.
     */
    private suspend fun decryptDeviceRequest(
        sessionEncryption: SessionEncryption,
        readerData: ByteArray
    ): DeviceRequest {
        debugLogger?.logEvent("SESSION_ENCRYPTION", "Session encryption initialized, attempting decryption")

        log.info("$logId: Will decrypt the reader data")
        val sessionData = sessionEncryption.decrypt(readerData)
        log.info("$logId: Reader data decrypted, will decode into device request")

        val deviceRequestData = sessionData.sessionData.data!!.value
        debugLogger?.logDeviceRequest("Decrypted DeviceRequest from reader", deviceRequestData)

        return DeviceRequest.decodeCbor(deviceRequestData)
    }
}
