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

import com.sphereon.core.api.events.EventCategory
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventType

/**
 * NFC subsystem identifier for the core event system.
 */
object NfcEventSubsystems {
    val NFC = EventSubsystem("nfc")
}

/**
 * NFC-specific event types for the core event system.
 *
 * All types follow the pattern: `nfc.<domain>.<action>`
 * and can be filtered using glob patterns (e.g., `nfc.tag.*`, `nfc.**`).
 */
object NfcEventTypes {
    // Tag lifecycle
    val TAG_DETECTED = EventType("nfc.tag.detected")
    val TAG_CONNECTED = EventType("nfc.tag.connected")
    val TAG_LOST = EventType("nfc.tag.lost")

    // Session lifecycle
    val SESSION_STARTED = EventType("nfc.session.started")
    val SESSION_ENDED = EventType("nfc.session.ended")

    // APDU operations
    val APDU_SENT = EventType("nfc.apdu.sent")
    val APDU_RECEIVED = EventType("nfc.apdu.received")

    // NDEF operations
    val NDEF_MESSAGE_READ = EventType("nfc.ndef.read")
    val NDEF_MESSAGE_WRITTEN = EventType("nfc.ndef.written")

    // Scanning
    val SCAN_STARTED = EventType("nfc.scan.started")
    val SCAN_STOPPED = EventType("nfc.scan.stopped")
}

/**
 * NFC-specific event categories for domain-level classification.
 */
object NfcEventCategories {
    val CONNECTIVITY = EventCategory("connectivity")
    val DATA_EXCHANGE = EventCategory("data-exchange")
}
