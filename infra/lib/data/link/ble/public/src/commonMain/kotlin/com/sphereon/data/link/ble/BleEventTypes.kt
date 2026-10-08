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

package com.sphereon.data.link.ble

import com.sphereon.core.api.events.EventCategory
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventType

/**
 * BLE subsystem identifier for the core event system.
 */
object BleEventSubsystems {
    val BLE = EventSubsystem("ble")
}

/**
 * BLE-specific event types for the core event system.
 *
 * All types follow the pattern: `ble.<domain>.<action>`
 * and can be filtered using glob patterns (e.g., `ble.characteristic.*`, `ble.**`).
 */
object BleEventTypes {
    // Scanning
    val SCAN_STARTED = EventType("ble.scan.started")
    val SCAN_STOPPED = EventType("ble.scan.stopped")
    val SCAN_RESULT = EventType("ble.scan.result")
    val DEVICE_FOUND = EventType("ble.device.found")

    // Connection
    val CONNECTION_STATE_CHANGED = EventType("ble.connection.state.changed")
    val SERVICES_DISCOVERED = EventType("ble.services.discovered")
    val MTU_CHANGED = EventType("ble.mtu.changed")

    // Characteristics
    val CHARACTERISTIC_READ = EventType("ble.characteristic.read")
    val CHARACTERISTIC_WRITE = EventType("ble.characteristic.write")
    val CHARACTERISTIC_CHANGED = EventType("ble.characteristic.changed")

    // Descriptors
    val DESCRIPTOR_READ = EventType("ble.descriptor.read")
    val DESCRIPTOR_WRITE = EventType("ble.descriptor.write")

    // Notifications
    val NOTIFICATION = EventType("ble.notification")
}

/**
 * BLE-specific event categories for domain-level classification.
 */
object BleEventCategories {
    val CONNECTIVITY = EventCategory("connectivity")
    val DATA_EXCHANGE = EventCategory("data-exchange")
}
