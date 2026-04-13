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

package com.sphereon.mdoc.engagement

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Preset engagement configurations for common use cases.
 *
 * These presets provide quick-start configurations that follow best practices
 * for typical mDoc engagement scenarios. They can be used as-is or customized
 * by passing additional configuration blocks.
 *
 * ## Usage Examples
 *
 * ### Using a preset directly:
 * ```kotlin
 * val engagement = manager.createEngagement(EngagementPresets.backgroundNfc())
 * ```
 *
 * ### Customizing a preset:
 * ```kotlin
 * val engagement = manager.createEngagement {
 *     EngagementPresets.backgroundNfc()(this)
 *     // Add custom configuration...
 * }
 * ```
 */
object EngagementPresets {

    /**
     * Background NFC listener (passive, low power).
     *
     * **Suitable for:** Always-on holder apps that need to be ready for NFC taps
     * at any time, even when the app is in the background.
     *
     * **Configuration:**
     * - Engagement: NFC (passive listening)
     * - Retrieval: BLE central client mode
     *
     * **Battery Impact:** Low - NFC listening is OS-level and passive.
     * BLE scanning only starts after NFC tap completes.
     *
     * **Platform Notes:**
     * - Android: Requires NFC HCE (Host Card Emulation) permissions
     * - iOS: Requires NFCReaderSession entitlements
     *
     * @return Configuration block for background NFC
     */
    fun backgroundNfc(): EngagementConfiguration.() -> Unit = {
        engagement {
            nfc { }
        }
        retrieval {
            ble {
                centralClientMode = true
                peripheralServerMode = false
            }
        }
    }

    /**
     * Foreground QR code (active BLE scanning).
     *
     * **Suitable for:** User-initiated verification screens where the holder
     * displays a QR code for the verifier to scan.
     *
     * **Configuration:**
     * - Engagement: QR code with default "mdoc:" scheme
     * - Retrieval: BLE central client mode
     *
     * **Battery Impact:** Medium - Active BLE scanning while QR is displayed.
     * Scanning stops when engagement is closed.
     *
     * **Best Practice:** Close the engagement when the user dismisses the QR screen
     * to stop BLE scanning and save battery.
     *
     * @param scheme QR code URI scheme (default: "mdoc:")
     * @return Configuration block for foreground QR
     */
    fun foregroundQr(scheme: String = "mdoc:"): EngagementConfiguration.() -> Unit = {
        engagement {
            qr {
                this.scheme = scheme
            }
        }
        retrieval {
            ble {
                centralClientMode = true
                peripheralServerMode = false
            }
        }
    }

    /**
     * Reader scanning QR code from holder.
     *
     * **Suitable for:** Verifier apps that scan QR codes displayed by holder devices.
     *
     * **Configuration:**
     * - Retrieval: BLE peripheral server mode (advertises and waits for holder to connect)
     *
     * **Flow:**
     * 1. Reader scans QR code from holder's screen
     * 2. Reader starts BLE advertising with UUID from QR
     * 3. Holder connects to reader's BLE service
     * 4. Data exchange happens over BLE
     *
     * @return Configuration block for reader QR scan
     */
    fun readerQrScan(): EngagementConfiguration.() -> Unit = {
        retrieval {
            ble {
                centralClientMode = false
                peripheralServerMode = true
            }
        }
    }

    /**
     * Reader initiating NFC tap.
     *
     * **Suitable for:** Verifier apps with NFC reader hardware (e.g., point-of-sale terminals,
     * kiosks, or mobile devices acting as readers).
     *
     * **Configuration:**
     * - Engagement: NFC (reader initiates tap)
     * - Retrieval: BLE peripheral server mode
     *
     * **Flow:**
     * 1. Reader taps holder's device with NFC
     * 2. NFC handshake exchanges engagement data
     * 3. Reader starts BLE advertising
     * 4. Holder connects to reader via BLE
     * 5. Data exchange happens over BLE
     *
     * **Platform Notes:**
     * - Android: Requires NFC reader permissions
     * - iOS: Limited NFC reader capabilities
     *
     * @return Configuration block for reader NFC tap
     */
    fun readerNfcTap(): EngagementConfiguration.() -> Unit = {
        engagement {
            nfc { }
        }
        retrieval {
            ble {
                centralClientMode = false
                peripheralServerMode = true
            }
        }
    }

    /**
     * Dual-mode holder (NFC + QR simultaneously).
     *
     * **Suitable for:** Holder apps that want to support both background NFC
     * and foreground QR at the same time, giving users/verifiers flexibility.
     *
     * **Configuration:**
     * - Engagement: Both NFC and QR
     * - Retrieval: BLE central client mode (shared UUID and key)
     *
     * **Battery Impact:** Medium - NFC is passive, but QR triggers active BLE scanning.
     *
     * **Note:** Both engagements share the same ephemeral key and BLE UUID,
     * so whichever connects first will handle the transfer.
     *
     * **Usage:**
     * ```kotlin
     * // Create background NFC
     * val nfcEngagement = manager.createEngagement(EngagementPresets.backgroundNfc())
     *
     * // User wants to show QR - create it without closing NFC
     * val qrEngagement = manager.createEngagement(EngagementPresets.foregroundQr())
     *
     * // Both are active, whichever completes first wins
     * ```
     *
     * @return Configuration block for dual-mode operation
     */
    fun dualMode(qrScheme: String = "mdoc:"): EngagementConfiguration.() -> Unit = {
        engagement {
            nfc { }
            qr {
                this.scheme = qrScheme
            }
        }
        retrieval {
            ble {
                centralClientMode = true
                peripheralServerMode = false
            }
        }
    }

    /**
     * NFC-only holder (both engagement and retrieval via NFC).
     *
     * **Suitable for:** Scenarios where BLE is not available or desired,
     * and all communication happens over NFC.
     *
     * **Configuration:**
     * - Engagement: NFC
     * - Retrieval: NFC
     *
     * **Limitations:**
     * - NFC transfer has size constraints (~60KB practical limit)
     * - Requires continuous proximity during transfer
     * - Slower than BLE for large documents
     *
     * **Best for:** Small documents or environments where BLE is problematic
     * (e.g., high interference areas, strict RF regulations).
     *
     * @return Configuration block for NFC-only operation
     */
    fun nfcOnly(): EngagementConfiguration.() -> Unit = {
        engagement {
            nfc { }
        }
        retrieval {
            nfc { }
        }
    }

    /**
     * Custom preset builder.
     *
     * Allows creating custom presets by combining engagement and retrieval methods
     * with explicit configuration.
     *
     * @param engagementConfig Engagement configuration block
     * @param retrievalConfig Retrieval configuration block
     * @return Configuration block combining both
     */
    fun custom(
        engagementConfig: EngagementBuilder.() -> Unit,
        retrievalConfig: RetrievalBuilder.() -> Unit
    ): EngagementConfiguration.() -> Unit = {
        engagement(engagementConfig)
        retrieval(retrievalConfig)
    }
}

/**
 * Extension function to apply a preset to an existing configuration.
 *
 * @param preset The preset to apply
 */
fun EngagementConfiguration.applyPreset(preset: EngagementConfiguration.() -> Unit) {
    preset()
}
