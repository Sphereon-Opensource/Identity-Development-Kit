/*
 * (c) 2025 Sphereon International B.V.
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
 */

package com.sphereon.mdoc.logging

import com.sphereon.cbor.cborSerializer
import com.sphereon.core.api.log.LogService
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.cose.CoseKey
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * ISO 18013-5 Debug Logger interface for tracking mDoc engagement and retrieval operations.
 *
 * Use tag "MdocDebug" to filter logs. All byte data is logged in hex format
 * with clear descriptions per ISO 18013-5 specification.
 *
 * Implementations should be injected via DI in session scope.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("IMdocDebugLogger", exact = true)
interface IMdocDebugLogger {

    /**
     * Whether debug logging is enabled. Can be toggled at runtime.
     */
    var enabled: Boolean

    // ================================
    // Engagement Phase (ISO 18013-5 Section 9)
    // ================================

    /**
     * Log DeviceEngagement creation/encoding.
     * Per ISO 18013-5 Section 9.1.1: DeviceEngagement structure for device retrieval.
     */
    fun logDeviceEngagement(description: String, bytes: ByteArray)

    /**
     * Log DeviceEngagementBytes (Tag 24 wrapped).
     * Per ISO 18013-5: DeviceEngagementBytes = #6.24(bstr .cbor DeviceEngagement)
     */
    fun logDeviceEngagementBytes(description: String, bytes: ByteArray)

    /**
     * Log EDeviceKey (holder's ephemeral public key).
     * Per ISO 18013-5 Section 9.1.1: Part of DeviceEngagement security.
     */
    fun logEDeviceKey(description: String, bytes: ByteArray)

    /**
     * Log EDeviceKey being used for ECDH computation.
     * This is critical for debugging - it MUST match the EDeviceKey in DeviceEngagement.
     */
    fun logEDeviceKeyForEcdh(description: String, bytes: ByteArray)

    /**
     * Log QR code data.
     * Per ISO 18013-5 Section 9.1.2: QR code engagement.
     */
    fun logQrCodeData(description: String, data: String)

    // ================================
    // Session Establishment (ISO 18013-5 Section 9.1.4)
    // ================================

    /**
     * Log SessionEstablishment message.
     * Per ISO 18013-5 Section 9.1.4: First message from reader to holder.
     */
    fun logSessionEstablishment(description: String, bytes: ByteArray)

    /**
     * Log EReaderKey (reader's ephemeral public key).
     * Per ISO 18013-5: Part of SessionEstablishment.
     */
    fun logEReaderKey(description: String, bytes: ByteArray)

    /**
     * Log EReaderKeyBytes (Tag 24 wrapped).
     * Per ISO 18013-5: EReaderKeyBytes = #6.24(bstr .cbor EReaderKey)
     */
    fun logEReaderKeyBytes(description: String, bytes: ByteArray)

    // ================================
    // Session Transcript (ISO 18013-5 Section 9.1.5)
    // ================================

    /**
     * Log SessionTranscript structure.
     * Per ISO 18013-5 Section 9.1.5: Used for key derivation.
     */
    fun logSessionTranscript(description: String, bytes: ByteArray)

    /**
     * Log SessionTranscriptBytes (Tag 24 wrapped).
     * Per ISO 18013-5 Section 9.1.5.1: Used as HKDF salt.
     */
    fun logSessionTranscriptBytes(description: String, bytes: ByteArray)

    /**
     * Log Handover data.
     * Per ISO 18013-5: Part of SessionTranscript.
     */
    fun logHandover(description: String, bytes: ByteArray?)

    // ================================
    // Session Encryption (ISO 18013-5 Section 9.1.1.4)
    // ================================

    /**
     * Log session key derivation parameters.
     */
    fun logSessionKeyDerivation(
        description: String,
        sharedSecretZab: ByteArray,
        sessionTranscriptBytesHash: ByteArray,
        skDevice: ByteArray,
        skReader: ByteArray
    )

    // ================================
    // Data Transfer (ISO 18013-5 Section 9.1.6)
    // ================================

    /**
     * Log DeviceRequest (from reader).
     * Per ISO 18013-5 Section 8.3.2.1.2: Request for device data.
     */
    fun logDeviceRequest(description: String, bytes: ByteArray)

    /**
     * Log DeviceResponse (from holder).
     * Per ISO 18013-5 Section 8.3.2.1.2: Response with device data.
     */
    fun logDeviceResponse(description: String, bytes: ByteArray)

    /**
     * Log SessionData (encrypted transfer message).
     * Per ISO 18013-5 Section 9.1.6: Encrypted message wrapper.
     */
    fun logSessionData(description: String, bytes: ByteArray, isEncrypted: Boolean = true)

    // ================================
    // BLE Transport (ISO 18013-5 Section 8.3.3.1)
    // ================================

    /**
     * Log BLE characteristic data being sent.
     */
    fun logBleSend(characteristicName: String, bytes: ByteArray)

    /**
     * Log BLE characteristic data received.
     */
    fun logBleReceive(characteristicName: String, bytes: ByteArray)

    /**
     * Log BLE service/characteristic configuration.
     */
    fun logBleConfig(description: String, serviceUuid: String, characteristics: Map<String, String>)

    // ================================
    // General Purpose
    // ================================

    /**
     * Log any byte data with a custom event type.
     */
    fun logBytes(eventType: String, description: String, bytes: ByteArray)

    /**
     * Log a text message (not bytes).
     */
    fun logText(eventType: String, description: String, text: String)

    /**
     * Log an event with no data.
     */
    fun logEvent(eventType: String, description: String)
}

/**
 * Implementation of IMdocDebugLogger that uses the injected LogService.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocDebugLoggerImpl", exact = true)
class MdocDebugLoggerImpl(
    private val logService: LogService
) : IMdocDebugLogger {

    override var enabled: Boolean = true

    private companion object {
        const val TAG = "MdocDebug"
        const val MAX_HEX_LINE_LENGTH = 2000
    }

    // ================================
    // Engagement Phase
    // ================================

    override fun logDeviceEngagement(description: String, bytes: ByteArray) {
        log(
            "DEVICE_ENGAGEMENT",
            """
            |$description
            |ISO 18013-5 Section 9.1.1: DeviceEngagement
            |Structure: [version, security, deviceRetrievalMethods?, serverRetrievalMethods?, protocolInfo?]
            |Security contains cipher suite and EDeviceKey (holder's ephemeral public key)
            """.trimMargin(),
            bytes
        )
    }

    override fun logDeviceEngagementBytes(description: String, bytes: ByteArray) {
        log(
            "DEVICE_ENGAGEMENT_BYTES",
            """
            |$description
            |ISO 18013-5: DeviceEngagementBytes = #6.24(bstr .cbor DeviceEngagement)
            |This is Tag 24 wrapped DeviceEngagement - used in SessionTranscript
            """.trimMargin(),
            bytes
        )
    }

    override fun logEDeviceKey(description: String, bytes: ByteArray) {
        logKeyWithJwk(
            "E_DEVICE_KEY",
            """
            |$description
            |ISO 18013-5 Section 9.1.1: EDeviceKey (holder's ephemeral public EC key)
            |Used for ECDH key agreement to derive session keys
            |Format: COSE_Key structure (kty=2/EC2, crv=1/P-256, x, y)
            """.trimMargin(),
            bytes,
            isTag24Wrapped = false
        )
    }

    override fun logEDeviceKeyForEcdh(description: String, bytes: ByteArray) {
        logKeyWithJwk(
            "E_DEVICE_KEY_FOR_ECDH",
            """
            |$description
            |CRITICAL: This shows the FULL ephemeral key (including private d value)
            |The x,y coordinates MUST match the EDeviceKey in DeviceEngagement
            |The d value is the private key used for ECDH computation
            |If x,y don't match DeviceEngagement, ECDH will produce wrong ZAB
            """.trimMargin(),
            bytes,
            isTag24Wrapped = false
        )
    }

    override fun logQrCodeData(description: String, data: String) {
        logText(
            "QR_CODE",
            """
            |$description
            |ISO 18013-5 Section 9.1.2: QR Code Engagement
            |Format: mdoc:<base64url-encoded DeviceEngagement>
            """.trimMargin(),
            data
        )
    }

    // ================================
    // Session Establishment
    // ================================

    override fun logSessionEstablishment(description: String, bytes: ByteArray) {
        log(
            "SESSION_ESTABLISHMENT",
            """
            |$description
            |ISO 18013-5 Section 9.1.4: SessionEstablishment
            |Structure: {eReaderKey: EReaderKeyBytes, data: EncryptedData}
            |Contains reader's ephemeral key and first encrypted message
            """.trimMargin(),
            bytes
        )
    }

    override fun logEReaderKey(description: String, bytes: ByteArray) {
        logKeyWithJwk(
            "E_READER_KEY",
            """
            |$description
            |ISO 18013-5: EReaderKey (reader's ephemeral public EC key)
            |Used for ECDH key agreement to derive session keys
            |Format: COSE_Key structure (kty=2/EC2, crv=1/P-256, x, y)
            """.trimMargin(),
            bytes,
            isTag24Wrapped = false
        )
    }

    override fun logEReaderKeyBytes(description: String, bytes: ByteArray) {
        logKeyWithJwk(
            "E_READER_KEY_BYTES",
            """
            |$description
            |ISO 18013-5: EReaderKeyBytes = #6.24(bstr .cbor EReaderKey)
            |This is Tag 24 wrapped EReaderKey - used in SessionTranscript
            """.trimMargin(),
            bytes,
            isTag24Wrapped = true
        )
    }

    // ================================
    // Session Transcript
    // ================================

    override fun logSessionTranscript(description: String, bytes: ByteArray) {
        log(
            "SESSION_TRANSCRIPT",
            """
            |$description
            |ISO 18013-5 Section 9.1.5: SessionTranscript
            |Structure: [DeviceEngagementBytes, EReaderKeyBytes, Handover]
            |This is the raw CBOR array (starts with 0x83 for 3-element array)
            """.trimMargin(),
            bytes
        )
    }

    override fun logSessionTranscriptBytes(description: String, bytes: ByteArray) {
        log(
            "SESSION_TRANSCRIPT_BYTES",
            """
            |$description
            |ISO 18013-5 Section 9.1.5.1: SessionTranscriptBytes = #6.24(bstr .cbor SessionTranscript)
            |This is Tag 24 wrapped SessionTranscript (starts with 0xd818)
            |SHA-256 of these bytes is used as HKDF salt for session key derivation
            """.trimMargin(),
            bytes
        )
    }

    override fun logHandover(description: String, bytes: ByteArray?) {
        if (bytes == null) {
            logText(
                "HANDOVER",
                """
                |$description
                |ISO 18013-5: Handover = null (QR code engagement)
                |For QR engagement, Handover is CBOR null (0xf6)
                """.trimMargin(),
                "null (0xf6)"
            )
        } else {
            log(
                "HANDOVER",
                """
                |$description
                |ISO 18013-5: Handover (NFC or other engagement type)
                |For NFC: [handoverSelectMessage, handoverRequestMessage?]
                """.trimMargin(),
                bytes
            )
        }
    }

    // ================================
    // Session Encryption
    // ================================

    override fun logSessionKeyDerivation(
        description: String,
        sharedSecretZab: ByteArray,
        sessionTranscriptBytesHash: ByteArray,
        skDevice: ByteArray,
        skReader: ByteArray
    ) {
        if (!enabled) return
        val sb = StringBuilder()
        sb.appendLine("SESSION_KEY_DERIVATION: $description")
        sb.appendLine(getShortStack())
        sb.appendLine("ISO 18013-5 Section 9.1.1.4: Session key derivation using HKDF")
        sb.appendLine("  - IKM: ECDH shared secret (ZAB)")
        sb.appendLine("  - Salt: SHA-256(SessionTranscriptBytes)")
        sb.appendLine("  - Info: 'SKDevice' or 'SKReader'")
        sb.appendLine()
        sb.appendLine("ZAB (ECDH shared secret, ${sharedSecretZab.size} bytes):")
        appendHexLines(sb, sharedSecretZab)
        sb.appendLine()
        sb.appendLine("SHA-256(SessionTranscriptBytes) (${sessionTranscriptBytesHash.size} bytes):")
        appendHexLines(sb, sessionTranscriptBytesHash)
        sb.appendLine()
        sb.appendLine("SKDevice (device session key, ${skDevice.size} bytes):")
        appendHexLines(sb, skDevice)
        sb.appendLine()
        sb.appendLine("SKReader (reader session key, ${skReader.size} bytes):")
        appendHexLines(sb, skReader)

        logService.info("[$TAG] $sb")
    }

    // ================================
    // Data Transfer
    // ================================

    override fun logDeviceRequest(description: String, bytes: ByteArray) {
        log(
            "DEVICE_REQUEST",
            """
            |$description
            |ISO 18013-5 Section 8.3.2.1.2: DeviceRequest
            |Structure: {version, docRequests: [DocRequest]?, oid4vpRequest?}
            |Contains requested namespaces and data elements
            """.trimMargin(),
            bytes
        )
    }

    override fun logDeviceResponse(description: String, bytes: ByteArray) {
        log(
            "DEVICE_RESPONSE",
            """
            |$description
            |ISO 18013-5 Section 8.3.2.1.2: DeviceResponse
            |Structure: {version, documents: [Document]?, documentErrors?, status}
            |Contains signed device data
            """.trimMargin(),
            bytes
        )
    }

    override fun logSessionData(description: String, bytes: ByteArray, isEncrypted: Boolean) {
        log(
            "SESSION_DATA",
            """
            |$description
            |ISO 18013-5 Section 9.1.6: SessionData
            |Structure: {data: EncryptedData?, status?}
            |Encrypted: $isEncrypted
            """.trimMargin(),
            bytes
        )
    }

    // ================================
    // BLE Transport
    // ================================

    override fun logBleSend(characteristicName: String, bytes: ByteArray) {
        log(
            "BLE_SEND",
            """
            |Sending data on BLE characteristic: $characteristicName
            |ISO 18013-5 Section 8.3.3.1: BLE data transfer
            """.trimMargin(),
            bytes
        )
    }

    override fun logBleReceive(characteristicName: String, bytes: ByteArray) {
        log(
            "BLE_RECEIVE",
            """
            |Received data on BLE characteristic: $characteristicName
            |ISO 18013-5 Section 8.3.3.1: BLE data transfer
            """.trimMargin(),
            bytes
        )
    }

    override fun logBleConfig(description: String, serviceUuid: String, characteristics: Map<String, String>) {
        if (!enabled) return
        val sb = StringBuilder()
        sb.appendLine("BLE_CONFIG: $description")
        sb.appendLine(getShortStack())
        sb.appendLine("ISO 18013-5 Section 8.3.3.1.1: BLE GATT Service")
        sb.appendLine("Service UUID: $serviceUuid")
        sb.appendLine("Characteristics:")
        characteristics.forEach { (name, uuid) ->
            sb.appendLine("  - $name: $uuid")
        }
        logService.info("[$TAG] $sb")
    }

    // ================================
    // General Purpose
    // ================================

    override fun logBytes(eventType: String, description: String, bytes: ByteArray) {
        log(eventType, description, bytes)
    }

    override fun logText(eventType: String, description: String, text: String) {
        if (!enabled) return
        val sb = StringBuilder()
        sb.appendLine("$eventType: $description")
        sb.appendLine(getShortStack())
        sb.appendLine("Value: $text")
        logService.info("[$TAG] $sb")
    }

    override fun logEvent(eventType: String, description: String) {
        if (!enabled) return
        val sb = StringBuilder()
        sb.appendLine("$eventType: $description")
        sb.appendLine(getShortStack())
        logService.info("[$TAG] $sb")
    }

    // ================================
    // Internal Helpers
    // ================================

    private fun logKeyWithJwk(eventType: String, description: String, bytes: ByteArray, isTag24Wrapped: Boolean) {
        if (!enabled) return
        val sb = StringBuilder()
        sb.appendLine("$eventType (${bytes.size} bytes)")
        sb.appendLine(getShortStack())
        sb.appendLine(description)
        sb.appendLine()
        sb.appendLine("Hex data (COSE_Key):")
        appendHexLines(sb, bytes)

        // Try to decode and convert to JWK
        try {
            val coseKey: CoseKey = if (isTag24Wrapped) {
                // Tag 24 wrapped - decode the CborEncodedItem first
                CoseKey.fromEncodedCborItem(cborSerializer.decode(bytes))
            } else {
                // Raw COSE_Key
                CoseKey.decodeCbor(bytes)
            }
            val jwk = CoseJoseKeyMappingService.toJoseJwk(coseKey)
            sb.appendLine()
            sb.appendLine("JWK representation:")
            sb.appendLine(jwk.toJsonString())
        } catch (e: Exception) {
            sb.appendLine()
            sb.appendLine("JWK conversion failed: ${e.message}")
        }

        logService.info("[$TAG] $sb")
    }

    private fun log(eventType: String, description: String, bytes: ByteArray) {
        if (!enabled) return
        val sb = StringBuilder()
        sb.appendLine("$eventType (${bytes.size} bytes)")
        sb.appendLine(getShortStack())
        sb.appendLine(description)
        sb.appendLine()
        sb.appendLine("Hex data:")
        appendHexLines(sb, bytes)

        logService.info("[$TAG] $sb")
    }

    private fun appendHexLines(sb: StringBuilder, bytes: ByteArray) {
        val hex = bytes.toHexString()
        if (hex.length <= MAX_HEX_LINE_LENGTH) {
            sb.appendLine(hex)
        } else {
            hex.chunked(MAX_HEX_LINE_LENGTH).forEachIndexed { index, chunk ->
                sb.appendLine("  [${index + 1}] $chunk")
            }
        }
    }

    private fun getShortStack(): String {
        val stackString = Exception("stack").stackTraceToString()
        val lines = stackString.lines()
            .filter { it.trim().startsWith("at ") }
            .drop(3)
            .filter { !it.contains("MdocDebugLogger") }
            .take(3)

        if (lines.isEmpty()) return "  (no stack available)"

        return lines.joinToString("\n") { line ->
            "  ${line.trim()}"
        }
    }

    private fun ByteArray.toHexString(): String {
        val hexChars = "0123456789abcdef"
        val result = StringBuilder(size * 2)
        for (byte in this) {
            val i = byte.toInt()
            result.append(hexChars[(i shr 4) and 0x0F])
            result.append(hexChars[i and 0x0F])
        }
        return result.toString()
    }
}
