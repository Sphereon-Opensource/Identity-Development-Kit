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

package com.sphereon.mdoc.transport.ble

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.mdoc.transport.ConnectionMethodBase
import com.sphereon.mdoc.transport.TransportType
import kotlin.uuid.Uuid

/**
 * Connection method for BLE (Bluetooth Low Energy) transport.
 *
 * This supports two modes:
 * - **Central Client Mode**: Device acts as BLE central, connects to peripheral
 * - **Peripheral Server Mode**: Device acts as BLE peripheral, advertises service
 *
 * ## Usage
 * ```kotlin
 * val bleMethod = BleConnectionMethod(
 *     options = BleOptions(
 *         centralClientMode = true,
 *         centralClientModeUuid = Uuid.random(),
 *         peripheralServerMode = false
 *     )
 * )
 * ```
 *
 * @param options BLE-specific configuration options
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BleConnectionMethod", exact = true)
data class BleConnectionMethod(
    override val options: BleOptions
) : ConnectionMethodBase<BleOptions>(options) {

    override val transportType: TransportType = TransportType.BLE

    override fun toDeviceRetrievalMethod(): DeviceRetrievalMethod {
        return DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.BLE,
            retrievalOptions = options
        )
    }

    override fun toString(): String {
        return "BleConnectionMethod(options=$options)"
    }

    companion object : Factory {

        override fun supports(deviceRetrievalMethod: DeviceRetrievalMethod): Boolean {
            return deviceRetrievalMethod.type == DeviceRetrievalMethodType.BLE &&
                    deviceRetrievalMethod.retrievalOptions is BleOptions
        }

        override fun create(deviceRetrievalMethod: DeviceRetrievalMethod): ConnectionMethod? {
            if (!supports(deviceRetrievalMethod)) {
                return null
            }
            val bleOptions = deviceRetrievalMethod.retrievalOptions as BleOptions
            return BleConnectionMethod(options = bleOptions)
        }

        /**
         * Combines multiple BLE connection methods into a single one.
         *
         * If multiple BLE methods are provided with compatible settings, this combines
         * them into a single method that supports both central and peripheral modes.
         *
         * Requirements:
         * - All methods must use the same UUID
         * - MAC addresses and other settings are merged
         *
         * @param connectionMethods List of connection methods to combine
         * @return Combined list where BLE methods are merged
         * @throws IllegalArgumentException if UUIDs don't match
         */
        fun combine(connectionMethods: List<ConnectionMethod>): List<ConnectionMethod> {
            val result = mutableSetOf<ConnectionMethod>()

            // Keep non-BLE methods as-is
            result.addAll(connectionMethods.filter { it !is BleConnectionMethod })

            // Get all BLE methods
            val bleMethods = connectionMethods.filterIsInstance<BleConnectionMethod>()
            if (bleMethods.size <= 1) {
                return connectionMethods
            }

            // Merge BLE methods
            var supportsPeripheralServerMode = false
            var supportsCentralClientMode = false
            var uuid: Uuid? = null
            var mac: ByteArray? = null

            for (ble in bleMethods) {
                if (ble.options.peripheralServerMode) {
                    supportsPeripheralServerMode = true
                }
                if (ble.options.centralClientMode) {
                    supportsCentralClientMode = true
                }

                val ccUuid = ble.options.centralClientModeUuid
                val psUuid = ble.options.peripheralServerModeUuid

                if (uuid == null) {
                    uuid = ccUuid ?: psUuid
                } else {
                    require(ccUuid == null || uuid == ccUuid) {
                        "UUIDs for BLE central client mode are not the same"
                    }
                    require(psUuid == null || uuid == psUuid) {
                        "UUIDs for BLE peripheral server mode are not the same"
                    }
                }

                if (mac == null && ble.options.peripheralServerModeDeviceAddress != null) {
                    mac = ble.options.peripheralServerModeDeviceAddress
                }
            }

            val combined = BleConnectionMethod(
                options = BleOptions(
                    peripheralServerMode = supportsPeripheralServerMode,
                    centralClientMode = supportsCentralClientMode,
                    peripheralServerModeUuid = if (supportsPeripheralServerMode) uuid else null,
                    centralClientModeUuid = if (supportsCentralClientMode) uuid else null,
                    peripheralServerModeDeviceAddress = mac
                )
            )

            return listOf(combined) + result
        }

        /**
         * Disambiguates connection methods that support multiple modes.
         *
         * For BLE methods that support both central and peripheral modes, this splits
         * them into separate connection method instances, one for each mode.
         *
         * This is the reverse of [combine].
         *
         * @param connectionMethods List of connection methods
         * @param role Device role (affects how MAC addresses are distributed)
         * @return Disambiguated list where each method supports exactly one mode
         */
        fun disambiguate(
            connectionMethods: List<ConnectionMethod>,
            role: MdocRole
        ): List<ConnectionMethod> {
            val result = mutableSetOf<ConnectionMethod>()

            for (connectionMethod in connectionMethods) {
                // Only BLE needs disambiguation
                if (connectionMethod !is BleConnectionMethod) {
                    result.add(connectionMethod)
                    continue
                }

                val options = connectionMethod.options

                // If only one mode is set, no need to disambiguate
                if (!options.centralClientMode || !options.peripheralServerMode) {
                    result.add(connectionMethod)
                    continue
                }

                // Both central and peripheral - separate them
                val centralClientMode = BleConnectionMethod(
                    options.copy(
                        peripheralServerMode = false,
                        peripheralServerModeUuid = null
                    )
                )
                result.add(centralClientMode)

                val peripheralServerMode = BleConnectionMethod(
                    options.copy(
                        centralClientMode = false,
                        centralClientModeUuid = null
                    )
                )
                result.add(peripheralServerMode)
            }

            return result.toList()
        }
    }
}
