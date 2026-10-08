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

package com.sphereon.mdoc.transport.nfc

import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.NfcOptions
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.mdoc.transport.ConnectionMethodBase
import com.sphereon.mdoc.transport.TransportType
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Connection method for NFC (Near Field Communication) transport.
 *
 * NFC is simpler than BLE - it only supports a single mode where the
 * reader device taps against the holder's device for data exchange.
 *
 * ## Usage
 * ```kotlin
 * val nfcMethod = NfcConnectionMethod(
 *     options = NfcOptions(
 *         maxCommandDataFieldLength = 255u,
 *         maxResponseDataFieldLength = 255u
 *     )
 * )
 * ```
 *
 * ## ISO 18013-5 Compliance
 *
 * This implements NFC data retrieval as specified in ISO 18013-5 section 8.3.3.
 * The connection method encapsulates the NFC options including:
 * - Maximum command APDU data field length
 * - Maximum response APDU data field length
 *
 * @param options NFC-specific configuration options
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("NfcConnectionMethod", exact = true)
data class NfcConnectionMethod(
    override val options: NfcOptions,
) : ConnectionMethodBase<NfcOptions>(options) {
    override val transportType: TransportType = TransportType.NFC

    override fun toDeviceRetrievalMethod(): DeviceRetrievalMethod =
        DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.NFC,
            retrievalOptions = options,
        )

    override fun toString(): String = "NfcConnectionMethod(options=$options)"

    companion object : Factory {
        /**
         * Checks if this factory supports the given device retrieval method.
         *
         * @param deviceRetrievalMethod The device retrieval method to check
         * @return true if this is an NFC retrieval method with NfcOptions
         */
        override fun supports(deviceRetrievalMethod: DeviceRetrievalMethod): Boolean =
            deviceRetrievalMethod.type == DeviceRetrievalMethodType.NFC &&
                deviceRetrievalMethod.retrievalOptions is NfcOptions

        /**
         * Creates an NFC connection method from a device retrieval method.
         *
         * @param deviceRetrievalMethod The device retrieval method to parse
         * @return NfcConnectionMethod if the method is supported, null otherwise
         */
        override fun create(deviceRetrievalMethod: DeviceRetrievalMethod): ConnectionMethod? {
            if (!supports(deviceRetrievalMethod)) {
                return null
            }
            val nfcOptions = deviceRetrievalMethod.retrievalOptions as NfcOptions
            return NfcConnectionMethod(options = nfcOptions)
        }
    }
}
