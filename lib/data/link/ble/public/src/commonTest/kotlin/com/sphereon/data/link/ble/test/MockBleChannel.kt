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

package com.sphereon.data.link.ble.test

import com.sphereon.data.link.ble.client.BleEvent
import com.sphereon.data.link.ble.client.MockBlePlatformClient
import com.sphereon.data.link.ble.peripheral.MockBlePlatformPeripheral
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Bidirectional communication channel that connects a mock central (client) with a mock peripheral (server).
 *
 * This helper class simplifies testing by automatically routing data between the central and peripheral:
 * - When central writes → triggers peripheral's `CharacteristicChanged` event
 * - When peripheral notifies → triggers central's `Notification` or `CharacteristicChanged` event
 *
 * ## Usage Example:
 * ```kotlin
 * val client = MockBlePlatformClient()
 * val peripheral = MockBlePlatformPeripheral()
 * val channel = MockBleChannel(client, peripheral, "test-device")
 *
 * // Establish connection
 * channel.establishConnection()
 *
 * // Negotiate MTU
 * channel.negotiateMtu(512)
 *
 * // Now data flows bidirectionally automatically
 * ```
 *
 * @param central The mock central (client) device
 * @param peripheral The mock peripheral (server) device
 * @param deviceAddress The address to use for the mock device (default: "mock-device")
 */
@OptIn(ExperimentalUuidApi::class)
class MockBleChannel(
    val central: MockBlePlatformClient,
    val peripheral: MockBlePlatformPeripheral,
    val deviceAddress: String = "mock-device",
) {
    private var isConnected = false

    /**
     * Establishes a mock BLE connection between the central and peripheral.
     *
     * This simulates:
     * - Peripheral starting to advertise
     * - Central discovering the peripheral
     * - Connection establishment
     * - Connection state events being emitted
     */
    suspend fun establishConnection() {
        if (isConnected) {
            return
        }

        // Peripheral side: simulate client connecting
        peripheral.simulateClientConnect(deviceAddress)

        // Central side: simulate connection established
        central.simulateConnectionStateChange(2) // 2 = connected

        isConnected = true
    }

    /**
     * Closes the mock BLE connection between central and peripheral.
     *
     * This simulates:
     * - Disconnection initiated
     * - Connection state events being emitted
     * - Resources being cleaned up
     */
    suspend fun closeConnection() {
        if (!isConnected) {
            return
        }

        // Peripheral side: simulate client disconnecting
        peripheral.simulateClientDisconnect(deviceAddress)

        // Central side: simulate disconnection
        central.simulateConnectionStateChange(0) // 0 = disconnected

        isConnected = false
    }

    /**
     * Negotiates MTU size on both sides of the connection.
     *
     * @param mtu The MTU size to negotiate (typically 23-512)
     */
    suspend fun negotiateMtu(mtu: Int) {
        check(isConnected) { "Cannot negotiate MTU when not connected" }

        // Notify both sides of MTU change
        central.simulateMtuChange(mtu)
        peripheral.simulateMtuChange(deviceAddress, mtu)
    }

    /**
     * Routes data from central write to peripheral characteristic changed event.
     * Call this after the central writes data to simulate the peripheral receiving it.
     *
     * @param serviceId The service UUID
     * @param characteristicId The characteristic UUID
     * @param data The data that was written
     */
    fun routeCentralWriteToPeripheral(
        serviceId: Uuid,
        characteristicId: Uuid,
        data: ByteArray,
    ) {
        peripheral.simulateClientWrite(deviceAddress, serviceId, characteristicId, data)
    }

    /**
     * Routes data from peripheral notification to central characteristic changed event.
     * Call this after the peripheral sends a notification to simulate the central receiving it.
     *
     * @param serviceId The service UUID
     * @param characteristicId The characteristic UUID
     * @param data The data that was notified
     */
    fun routePeripheralNotifyToCentral(
        serviceId: Uuid,
        characteristicId: Uuid,
        data: ByteArray,
    ) {
        central.simulateCharacteristicChanged(serviceId, characteristicId, data)
    }

    /**
     * Cleans up both mock devices.
     */
    fun cleanup() {
        central.close()
        peripheral.close()
    }
}
