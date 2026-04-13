/*
 * Copyright 2023-2026 Sphereon International B.V.
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

import com.sphereon.core.api.encodeToHex
import com.sphereon.core.api.log.LogService
import com.sphereon.crypto.core.cose.CoseKeyCborCodec

class MdocDebugLoggerImpl(
    private val log: LogService,
    private val coseKeyCborCodec: CoseKeyCborCodec,
) : IMdocDebugLogger {
    override var enabled: Boolean = true

    override fun logDeviceEngagement(
        description: String,
        bytes: ByteArray,
    ) = logBytePayload("DEVICE_ENGAGEMENT", description, bytes)

    override fun logDeviceEngagementBytes(
        description: String,
        bytes: ByteArray,
    ) = logBytePayload("DEVICE_ENGAGEMENT_BYTES", description, bytes)

    override fun logEDeviceKey(
        description: String,
        bytes: ByteArray,
    ) {
        if (!enabled) {
            return
        }
        logBytePayload("E_DEVICE_KEY", description, bytes, decodeCoseKeySummary(bytes))
    }

    override fun logEDeviceKeyForEcdh(
        description: String,
        bytes: ByteArray,
    ) {
        if (!enabled) {
            return
        }
        logBytePayload("E_DEVICE_KEY_FOR_ECDH", description, bytes, decodeCoseKeySummary(bytes))
    }

    override fun logQrCodeData(
        description: String,
        data: String,
    ) = logText("QR_CODE", description, data)

    override fun logSessionEstablishment(
        description: String,
        bytes: ByteArray,
    ) = logBytePayload("SESSION_ESTABLISHMENT", description, bytes)

    override fun logEReaderKey(
        description: String,
        bytes: ByteArray,
    ) {
        if (!enabled) {
            return
        }
        logBytePayload("E_READER_KEY", description, bytes, decodeCoseKeySummary(bytes))
    }

    override fun logEReaderKeyBytes(
        description: String,
        bytes: ByteArray,
    ) = logBytePayload("E_READER_KEY_BYTES", description, bytes)

    override fun logSessionTranscript(
        description: String,
        bytes: ByteArray,
    ) = logBytePayload("SESSION_TRANSCRIPT", description, bytes)

    override fun logSessionTranscriptBytes(
        description: String,
        bytes: ByteArray,
    ) = logBytePayload("SESSION_TRANSCRIPT_BYTES", description, bytes)

    override fun logHandover(
        description: String,
        bytes: ByteArray?,
    ) {
        if (!enabled) {
            return
        }
        val message =
            buildString {
                append(prefix("HANDOVER", description))
                if (bytes == null) {
                    append("\nValue: null")
                } else {
                    appendHex(bytes)
                }
            }
        log.debug(message)
    }

    override fun logSessionKeyDerivation(
        description: String,
        sharedSecretZab: ByteArray,
        sessionTranscriptBytesHash: ByteArray,
        skDevice: ByteArray,
        skReader: ByteArray,
    ) {
        if (!enabled) {
            return
        }
        val message =
            buildString {
                append(prefix("SESSION_KEY_DERIVATION", description))
                appendLabeledHex("ZAB", sharedSecretZab)
                appendLabeledHex("SessionTranscriptBytesHash", sessionTranscriptBytesHash)
                appendLabeledHex("SKDevice", skDevice)
                appendLabeledHex("SKReader", skReader)
            }
        log.debug(message)
    }

    override fun logDeviceRequest(
        description: String,
        bytes: ByteArray,
    ) = logBytePayload("DEVICE_REQUEST", description, bytes)

    override fun logDeviceResponse(
        description: String,
        bytes: ByteArray,
    ) = logBytePayload("DEVICE_RESPONSE", description, bytes)

    override fun logSessionData(
        description: String,
        bytes: ByteArray,
        isEncrypted: Boolean,
    ) = logBytePayload("SESSION_DATA", "$description | Encrypted: $isEncrypted", bytes)

    override fun logBleSend(
        characteristicName: String,
        bytes: ByteArray,
    ) = logBytePayload("BLE_SEND", characteristicName, bytes)

    override fun logBleReceive(
        characteristicName: String,
        bytes: ByteArray,
    ) = logBytePayload("BLE_RECEIVE", characteristicName, bytes)

    override fun logBleConfig(
        description: String,
        serviceUuid: String,
        characteristics: Map<String, String>,
    ) {
        if (!enabled) {
            return
        }
        val message =
            buildString {
                append(prefix("BLE_CONFIG", description))
                append("\nService UUID: ")
                append(serviceUuid)
                if (characteristics.isEmpty()) {
                    append("\nCharacteristics: none")
                } else {
                    append("\nCharacteristics:")
                    characteristics.forEach { (name, uuid) ->
                        append("\n  ")
                        append(name)
                        append(": ")
                        append(uuid)
                    }
                }
            }
        log.debug(message)
    }

    override fun logBytes(
        eventType: String,
        description: String,
        bytes: ByteArray,
    ) = logBytePayload(eventType, description, bytes)

    override fun logText(
        eventType: String,
        description: String,
        text: String,
    ) {
        if (!enabled) {
            return
        }
        log.debug("${prefix(eventType, description)}\nText: $text")
    }

    override fun logEvent(
        eventType: String,
        description: String,
    ) {
        if (!enabled) {
            return
        }
        log.debug(prefix(eventType, description))
    }

    private fun logBytePayload(
        eventType: String,
        description: String,
        bytes: ByteArray,
        trailingSection: String? = null,
    ) {
        if (!enabled) {
            return
        }
        val message =
            buildString {
                append(prefix(eventType, description))
                appendHex(bytes)
                trailingSection?.let {
                    append("\n")
                    append(it)
                }
            }
        log.debug(message)
    }

    private fun prefix(
        eventType: String,
        description: String,
    ): String = "[MdocDebug][$eventType] $description"

    private fun decodeCoseKeySummary(bytes: ByteArray): String? {
        return runCatching {
            val decoded = coseKeyCborCodec.decode(bytes)
            if (!decoded.isOk) {
                return null
            }
            "Decoded COSE key: ${decoded.value.value}"
        }.getOrNull()
    }

    private fun StringBuilder.appendHex(bytes: ByteArray) {
        appendLabeledHex("Bytes", bytes)
    }

    private fun StringBuilder.appendLabeledHex(
        label: String,
        bytes: ByteArray,
    ) {
        val hex = bytes.encodeToHex()
        if (hex.length <= MAX_HEX_LINE_LENGTH) {
            append("\n")
            append(label)
            append(": ")
            append(hex)
            return
        }

        hex.chunked(MAX_HEX_LINE_LENGTH).forEachIndexed { index, chunk ->
            append("\n")
            append(label)
            append("[")
            append(index + 1)
            append("]: ")
            append(chunk)
        }
    }

    private companion object {
        const val MAX_HEX_LINE_LENGTH = 512
    }
}
