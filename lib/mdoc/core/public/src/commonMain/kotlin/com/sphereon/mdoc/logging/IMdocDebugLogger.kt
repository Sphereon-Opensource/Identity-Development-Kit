/*
 * (c) 2026 Sphereon International B.V.
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
    fun logDeviceEngagement(
        description: String,
        bytes: ByteArray,
    )

    /**
     * Log DeviceEngagementBytes (Tag 24 wrapped).
     * Per ISO 18013-5: DeviceEngagementBytes = #6.24(bstr .cbor DeviceEngagement)
     */
    fun logDeviceEngagementBytes(
        description: String,
        bytes: ByteArray,
    )

    /**
     * Log EDeviceKey (holder's ephemeral public key).
     * Per ISO 18013-5 Section 9.1.1: Part of DeviceEngagement security.
     */
    fun logEDeviceKey(
        description: String,
        bytes: ByteArray,
    )

    /**
     * Log EDeviceKey being used for ECDH computation.
     * This is critical for debugging - it MUST match the EDeviceKey in DeviceEngagement.
     */
    fun logEDeviceKeyForEcdh(
        description: String,
        bytes: ByteArray,
    )

    /**
     * Log QR code data.
     * Per ISO 18013-5 Section 9.1.2: QR code engagement.
     */
    fun logQrCodeData(
        description: String,
        data: String,
    )

    // ================================
    // Session Establishment (ISO 18013-5 Section 9.1.4)
    // ================================

    /**
     * Log SessionEstablishment message.
     * Per ISO 18013-5 Section 9.1.4: First message from reader to holder.
     */
    fun logSessionEstablishment(
        description: String,
        bytes: ByteArray,
    )

    /**
     * Log EReaderKey (reader's ephemeral public key).
     * Per ISO 18013-5: Part of SessionEstablishment.
     */
    fun logEReaderKey(
        description: String,
        bytes: ByteArray,
    )

    /**
     * Log EReaderKeyBytes (Tag 24 wrapped).
     * Per ISO 18013-5: EReaderKeyBytes = #6.24(bstr .cbor EReaderKey)
     */
    fun logEReaderKeyBytes(
        description: String,
        bytes: ByteArray,
    )

    // ================================
    // Session Transcript (ISO 18013-5 Section 9.1.5)
    // ================================

    /**
     * Log SessionTranscript structure.
     * Per ISO 18013-5 Section 9.1.5: Used for key derivation.
     */
    fun logSessionTranscript(
        description: String,
        bytes: ByteArray,
    )

    /**
     * Log SessionTranscriptBytes (Tag 24 wrapped).
     * Per ISO 18013-5 Section 9.1.5.1: Used as HKDF salt.
     */
    fun logSessionTranscriptBytes(
        description: String,
        bytes: ByteArray,
    )

    /**
     * Log Handover data.
     * Per ISO 18013-5: Part of SessionTranscript.
     */
    fun logHandover(
        description: String,
        bytes: ByteArray?,
    )

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
        skReader: ByteArray,
    )

    // ================================
    // Data Transfer (ISO 18013-5 Section 9.1.6)
    // ================================

    /**
     * Log DeviceRequest (from reader).
     * Per ISO 18013-5 Section 8.3.2.1.2: Request for device data.
     */
    fun logDeviceRequest(
        description: String,
        bytes: ByteArray,
    )

    /**
     * Log DeviceResponse (from holder).
     * Per ISO 18013-5 Section 8.3.2.1.2: Response with device data.
     */
    fun logDeviceResponse(
        description: String,
        bytes: ByteArray,
    )

    /**
     * Log SessionData (encrypted transfer message).
     * Per ISO 18013-5 Section 9.1.6: Encrypted message wrapper.
     */
    fun logSessionData(
        description: String,
        bytes: ByteArray,
        isEncrypted: Boolean = true,
    )

    // ================================
    // BLE Transport (ISO 18013-5 Section 8.3.3.1)
    // ================================

    /**
     * Log BLE characteristic data being sent.
     */
    fun logBleSend(
        characteristicName: String,
        bytes: ByteArray,
    )

    /**
     * Log BLE characteristic data received.
     */
    fun logBleReceive(
        characteristicName: String,
        bytes: ByteArray,
    )

    /**
     * Log BLE service/characteristic configuration.
     */
    fun logBleConfig(
        description: String,
        serviceUuid: String,
        characteristics: Map<String, String>,
    )

    // ================================
    // General Purpose
    // ================================

    /**
     * Log any byte data with a custom event type.
     */
    fun logBytes(
        eventType: String,
        description: String,
        bytes: ByteArray,
    )

    /**
     * Log a text message (not bytes).
     */
    fun logText(
        eventType: String,
        description: String,
        text: String,
    )

    /**
     * Log an event with no data.
     */
    fun logEvent(
        eventType: String,
        description: String,
    )
}
