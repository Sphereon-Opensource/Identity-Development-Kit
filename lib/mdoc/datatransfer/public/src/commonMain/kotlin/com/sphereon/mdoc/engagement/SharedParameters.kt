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

package com.sphereon.mdoc.engagement

import com.sphereon.core.compat.JsExportCompat
import kotlinx.coroutines.flow.StateFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Manages shared parameters that can be reused across multiple engagements.
 *
 * This object provides automatic parameter sharing to simplify the developer experience
 * when creating multiple engagements (e.g., QR + background NFC). If you don't provide
 * explicit UUIDs or keys, engagements will automatically use these shared values,
 * preventing conflicts and ensuring compatibility.
 *
 * ## Key Features:
 * - **Separate BLE UUIDs** for central client mode and peripheral server mode
 * - **Shared ephemeral key** across all engagements (unless explicitly overridden)
 * - **Automatic regeneration** on closeAll() for fresh verification sessions
 * - **Manual regeneration** via regenerate() when needed
 *
 * ## BLE UUID Modes:
 *
 * **Central Client Mode UUID:**
 * - Used when holder device initiates BLE connection (scans and connects)
 * - Typically used in QR code scenarios
 * - The QR code contains this UUID for the reader to advertise
 *
 * **Peripheral Server Mode UUID:**
 * - Used when holder device waits for BLE connection (advertises and accepts)
 * - Typically used in NFC scenarios
 * - Holder advertises this UUID after NFC handover
 *
 * **Same UUID for Both?**
 * - You CAN use the same UUID for both modes
 * - Some implementations prefer different UUIDs for clarity
 * - SharedParameters supports both approaches
 *
 * ## Usage Examples:
 *
 * ### Automatic Sharing (Easy):
 * ```kotlin
 * // All engagements automatically share these parameters
 * val manager = mdocEngagementManager()
 *
 * // QR engagement uses shared central client UUID
 * manager.createEngagement {
 *     engagement { qr { } }
 *     retrieval { ble { centralClientMode = true } }
 * }
 *
 * // NFC engagement uses shared peripheral server UUID
 * manager.createEngagement {
 *     engagement { nfc { } }
 *     retrieval { ble { peripheralServerMode = true } }
 * }
 *
 * // Check current shared UUIDs
 * val centralUuid = manager.sharedParameters.bleCentralClientUuid.value
 * val peripheralUuid = manager.sharedParameters.blePeripheralServerUuid.value
 * ```
 *
 * ### Manual Control (Advanced):
 * ```kotlin
 * // Regenerate parameters for new session
 * manager.sharedParameters.regenerate()
 *
 * // Or explicitly provide your own UUIDs in engagement config
 * manager.createEngagement {
 *     engagement { qr { } }
 *     retrieval {
 *         ble {
 *             centralClientMode = true
 *             uuid = Uuid.parse("your-specific-uuid")
 *         }
 *     }
 * }
 * ```
 *
 * ### Different vs Same UUIDs:
 * ```kotlin
 * // Approach 1: Different UUIDs (default)
 * // - bleCentralClientUuid = UUID-A
 * // - blePeripheralServerUuid = UUID-B
 * // Clear separation between modes
 *
 * // Approach 2: Same UUID (configure once)
 * manager.sharedParameters.useSameUuidForBothModes(Uuid.random())
 * // - bleCentralClientUuid = UUID-X
 * // - blePeripheralServerUuid = UUID-X
 * // Simpler, but less explicit
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SharedParameters", exact = true)
@JsExportCompat
interface SharedParameters {
    /**
     * Shared BLE UUID for central client mode (holder initiates connection).
     *
     * Used when holder device:
     * - Scans for BLE peripherals
     * - Connects to reader as BLE central
     *
     * Typically used in QR code engagements where:
     * 1. QR code contains this UUID
     * 2. Reader advertises on this UUID
     * 3. Holder scans and finds reader
     * 4. Holder connects as central client
     *
     * This UUID is automatically used if no explicit UUID provided in engagement config.
     */
    @OptIn(ExperimentalUuidApi::class)
    val bleCentralClientUuid: StateFlow<Uuid>

    /**
     * Shared BLE UUID for peripheral server mode (holder waits for connection).
     *
     * Used when holder device:
     * - Advertises as BLE peripheral
     * - Accepts connections from reader as BLE server
     *
     * Typically used in NFC engagements where:
     * 1. NFC handover exchanges engagement data
     * 2. Holder advertises on this UUID
     * 3. Reader scans and finds holder
     * 4. Reader connects as central client
     *
     * This UUID is automatically used if no explicit UUID provided in engagement config.
     */
    @OptIn(ExperimentalUuidApi::class)
    val blePeripheralServerUuid: StateFlow<Uuid>

    /**
     * Alias of the shared ephemeral key used across all engagements.
     *
     * The actual key is stored securely in the key management system.
     * This alias is used to retrieve the key when needed.
     *
     * Benefits of sharing ephemeral key:
     * - Consistent security context across engagements
     * - Efficient key management (one key, multiple uses)
     * - Automatic cleanup when session ends
     *
     * The key is regenerated on:
     * - Initial creation
     * - closeAll() call
     * - Manual regenerate() call
     */
    val ephemeralKeyAlias: StateFlow<String>

    /**
     * Regenerate all shared parameters (UUIDs and ephemeral key).
     *
     * Use this when:
     * - Starting a completely new verification session
     * - You want fresh parameters without closing engagements
     * - Security policy requires parameter rotation
     *
     * **Note:** This does NOT affect already-created engagements.
     * Only new engagements created after this call will use the new parameters.
     *
     * ## Example:
     * ```kotlin
     * // Complete one verification session
     * manager.closeAll()
     *
     * // Start fresh for new verification
     * manager.sharedParameters.regenerate()
     *
     * // Create new engagements with fresh parameters
     * manager.createEngagement { qr { } }
     * ```
     */
    suspend fun regenerate()

    /**
     * Configure both BLE modes to use the same UUID.
     *
     * This is a convenience method for implementations that don't need
     * separate UUIDs for central and peripheral modes.
     *
     * **Note:** This affects only NEW engagements created after this call.
     *
     * @param uuid The UUID to use for both modes (if null, generates a new random UUID)
     *
     * ## Example:
     * ```kotlin
     * // Use same UUID for both modes
     * manager.sharedParameters.useSameUuidForBothModes()
     *
     * // Or provide specific UUID
     * manager.sharedParameters.useSameUuidForBothModes(
     *     Uuid.parse("12345678-1234-1234-1234-123456789abc")
     * )
     * ```
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun useSameUuidForBothModes(uuid: Uuid? = null)
}
