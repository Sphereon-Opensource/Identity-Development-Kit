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

package com.sphereon.mdoc.transport.ble

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Strategy for choosing BLE mode when both central client and peripheral server
 * modes are available in the engagement (BLE-specific).
 *
 * ## When This Applies
 *
 * This strategy is used when a party RECEIVES an engagement that declares support
 * for BOTH BLE modes, and must choose ONE mode to use:
 *
 * **Forward Engagement:** Holder declares both modes → Reader uses strategy to pick one
 * **Reverse Engagement:** Reader declares both modes → Holder uses strategy to pick one
 *
 * The party that CREATED the engagement (declared both modes) will attempt BOTH modes
 * simultaneously and race them - this strategy does NOT apply to them.
 *
 * ## Mode Characteristics
 *
 * **Central Client Mode:**
 * - Scans for and connects to peripherals
 * - More reliable (active scanning)
 * - Higher power consumption
 * - Better for readers that need to initiate quickly
 *
 * **Peripheral Server Mode:**
 * - Advertises and accepts connections
 * - More power-efficient (passive advertising)
 * - Less reliable (depends on central finding it)
 * - Better for holders on battery power
 *
 * ## Strategy Options
 *
 * ### PREFER_CENTRAL (Default)
 * Choose central client mode regardless of role.
 * - **Pro:** Most reliable, faster connection establishment
 * - **Con:** Higher power consumption
 * - **Use case:** When reliability is more important than battery life
 *
 * ### PREFER_PERIPHERAL
 * Choose peripheral server mode regardless of role.
 * - **Pro:** Lower power consumption
 * - **Con:** Less reliable, slower discovery
 * - **Use case:** When battery life is critical
 *
 * ### READER_CENTRAL
 * Reader always uses central, holder always uses peripheral.
 * - **Pro:** Traditional BLE pattern (reader initiates)
 * - **Con:** Doesn't leverage holder's ability to scan
 * - **Use case:** When following standard BLE client-server model
 *
 * ### HOLDER_CENTRAL
 * Holder always uses central, reader always uses peripheral.
 * - **Pro:** Holder can find reader faster
 * - **Con:** Unconventional (reader is passive)
 * - **Use case:** When holder needs to quickly find available readers
 *
 * ## Example Usage
 *
 * ```kotlin
 * val bleFactory = BleTransportFactory(client, peripheral)
 * bleFactory.setModeSelectionStrategy(BleModeSelectionStrategy.PREFER_CENTRAL)
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BleModeSelectionStrategy", exact = true)
enum class BleModeSelectionStrategy {
    /**
     * Prefer central client mode (default - most reliable).
     *
     * When receiving an engagement with both modes, always choose central.
     * This provides the most reliable connection at the cost of higher power usage.
     */
    PREFER_CENTRAL,

    /**
     * Prefer peripheral server mode (more power-efficient).
     *
     * When receiving an engagement with both modes, always choose peripheral.
     * This is more battery-friendly but may take longer to establish connection.
     */
    PREFER_PERIPHERAL,

    /**
     * Reader always central, holder always peripheral.
     *
     * Traditional BLE pattern where the reader (verifier) acts as the central
     * client and the holder (device) acts as the peripheral server.
     *
     * - Reader receiving both modes → picks central
     * - Holder receiving both modes → picks peripheral
     */
    READER_CENTRAL,

    /**
     * Holder always central, reader always peripheral.
     *
     * Inverse pattern where the holder (device) acts as the central client
     * and the reader (verifier) acts as the peripheral server.
     *
     * - Holder receiving both modes → picks central
     * - Reader receiving both modes → picks peripheral
     */
    HOLDER_CENTRAL,
}
