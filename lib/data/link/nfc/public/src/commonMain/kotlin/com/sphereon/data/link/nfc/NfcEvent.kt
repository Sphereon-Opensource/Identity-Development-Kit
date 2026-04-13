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

package com.sphereon.data.link.nfc

import com.sphereon.core.api.events.EventType
import com.sphereon.data.link.nfc.model.NfcError
import com.sphereon.data.link.nfc.model.NfcErrors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Sealed interface for typed NFC event payloads.
 *
 * Each variant represents an NFC-specific event that can be emitted
 * through the core event system. Use [toPayload] to serialize event
 * data into a [JsonObject] for [com.sphereon.core.events.Event.payload],
 * and [eventType] to get the corresponding [EventType].
 *
 * Example usage with the core event system:
 * ```kotlin
 * val nfcEvent = NfcEvent.TagDetected(tagId = "04:A2:C5:21")
 * eventService.emit(
 *     eventService.eventBuilder()
 *         .type(nfcEvent.eventType)
 *         .origin("nfc.scan")
 *         .subsystem(NfcEventSubsystems.NFC)
 *         .category(EventCategories.LIFECYCLE)
 *         .payload(nfcEvent.toPayload())
 *         .build()
 * )
 * ```
 */
sealed interface NfcEvent {
    /** The core event type corresponding to this NFC event. */
    val eventType: EventType

    /** Serialize this event's data to a [JsonObject] for the core event payload. */
    fun toPayload(): JsonObject

    /** Convert to an [NfcError] for error reporting. */
    fun toError(
        reason: String,
        exception: Throwable? = null,
    ): NfcError

    // --- Tag lifecycle ---

    data class TagDetected(
        val tagId: String,
        val tagType: String? = null,
    ) : NfcEvent {
        override val eventType: EventType get() = NfcEventTypes.TAG_DETECTED

        override fun toPayload() =
            buildJsonObject {
                put("tagId", tagId)
                tagType?.let { put("tagType", it) }
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ) = NfcErrors.unknown(reason = reason, throwable = exception)
    }

    data class TagConnected(
        val tagId: String,
    ) : NfcEvent {
        override val eventType: EventType get() = NfcEventTypes.TAG_CONNECTED

        override fun toPayload() =
            buildJsonObject {
                put("tagId", tagId)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ) = NfcErrors.connectionFailed(reason = reason, throwable = exception)
    }

    data class TagLost(
        val tagId: String,
        val reason: String? = null,
    ) : NfcEvent {
        override val eventType: EventType get() = NfcEventTypes.TAG_LOST

        override fun toPayload() =
            buildJsonObject {
                put("tagId", tagId)
                reason?.let { put("reason", it) }
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ) = NfcErrors.tagLost(reason = reason, throwable = exception)
    }

    // --- Session lifecycle ---

    data class SessionStarted(
        val message: String? = null,
    ) : NfcEvent {
        override val eventType: EventType get() = NfcEventTypes.SESSION_STARTED

        override fun toPayload() =
            buildJsonObject {
                message?.let { put("message", it) }
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ) = NfcErrors.sessionFailed(reason = reason, throwable = exception)
    }

    data class SessionEnded(
        val reason: String? = null,
    ) : NfcEvent {
        override val eventType: EventType get() = NfcEventTypes.SESSION_ENDED

        override fun toPayload() =
            buildJsonObject {
                reason?.let { put("reason", it) }
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ) = NfcErrors.sessionFailed(reason = reason, throwable = exception)
    }

    // --- APDU operations ---

    data class ApduSent(
        val tagId: String,
        val ins: Int,
        val payloadSize: Int,
    ) : NfcEvent {
        override val eventType: EventType get() = NfcEventTypes.APDU_SENT

        override fun toPayload() =
            buildJsonObject {
                put("tagId", tagId)
                put("ins", ins)
                put("payloadSize", payloadSize)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ) = NfcErrors.transceiveFailed(reason = reason, throwable = exception)
    }

    data class ApduReceived(
        val tagId: String,
        val status: Int,
        val payloadSize: Int,
    ) : NfcEvent {
        override val eventType: EventType get() = NfcEventTypes.APDU_RECEIVED

        override fun toPayload() =
            buildJsonObject {
                put("tagId", tagId)
                put("status", status)
                put("payloadSize", payloadSize)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ) = NfcErrors.commandFailed(reason = reason, status = status, throwable = exception)
    }

    // --- NDEF operations ---

    data class NdefMessageRead(
        val tagId: String,
        val recordCount: Int,
        val totalSize: Int,
    ) : NfcEvent {
        override val eventType: EventType get() = NfcEventTypes.NDEF_MESSAGE_READ

        override fun toPayload() =
            buildJsonObject {
                put("tagId", tagId)
                put("recordCount", recordCount)
                put("totalSize", totalSize)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ) = NfcErrors.ndefParseFailed(reason = reason, throwable = exception)
    }

    data class NdefMessageWritten(
        val tagId: String,
        val totalSize: Int,
    ) : NfcEvent {
        override val eventType: EventType get() = NfcEventTypes.NDEF_MESSAGE_WRITTEN

        override fun toPayload() =
            buildJsonObject {
                put("tagId", tagId)
                put("totalSize", totalSize)
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ) = NfcErrors.transceiveFailed(reason = reason, throwable = exception)
    }

    // --- Scanning ---

    data class ScanStarted(
        val message: String? = null,
    ) : NfcEvent {
        override val eventType: EventType get() = NfcEventTypes.SCAN_STARTED

        override fun toPayload() =
            buildJsonObject {
                message?.let { put("message", it) }
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ) = NfcErrors.scanFailed(reason = reason, throwable = exception)
    }

    data class ScanStopped(
        val reason: String? = null,
    ) : NfcEvent {
        override val eventType: EventType get() = NfcEventTypes.SCAN_STOPPED

        override fun toPayload() =
            buildJsonObject {
                reason?.let { put("reason", it) }
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ) = NfcErrors.scanFailed(reason = reason, throwable = exception)
    }

    // --- Error ---

    data class Error(
        val error: NfcError,
        val errorMessage: String? = error.message.defaultMessage,
        val originalEvent: NfcEvent? = null,
    ) : NfcEvent {
        override val eventType: EventType get() = originalEvent?.eventType ?: NfcEventTypes.SESSION_ENDED

        override fun toPayload() =
            buildJsonObject {
                put("errorCode", error.code)
                errorMessage?.let { put("errorMessage", it) }
                originalEvent?.let { put("originalEventType", it.eventType.value) }
            }

        override fun toError(
            reason: String,
            exception: Throwable?,
        ): NfcError =
            originalEvent?.toError(reason, exception)
                ?: NfcErrors.unknown(reason = errorMessage ?: reason, throwable = exception)
    }
}
