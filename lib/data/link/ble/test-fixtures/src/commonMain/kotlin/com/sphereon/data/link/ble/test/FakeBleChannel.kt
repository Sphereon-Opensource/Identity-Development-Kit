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
import com.sphereon.data.link.ble.client.FakeBlePlatformClient
import com.sphereon.data.link.ble.peripheral.FakeBlePlatformPeripheral
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Bidirectional communication channel that connects a fake central (client) with a fake peripheral (server).
 *
 * This helper class simplifies testing by automatically routing data between the central and peripheral:
 * - When central writes → triggers peripheral's `CharacteristicChanged` event
 * - When peripheral notifies → triggers central's `Notification` or `CharacteristicChanged` event
 *
 * ## Usage Example:
 * ```kotlin
 * val client = FakeBlePlatformClient()
 * val peripheral = FakeBlePlatformPeripheral()
 * val channel = FakeBleChannel(client, peripheral, "test-device")
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
 * @param central The fake central (client) device
 * @param peripheral The fake peripheral (server) device
 * @param deviceAddress The address to use for the fake device (default: "fake-device")
 */
@OptIn(ExperimentalUuidApi::class)
class FakeBleChannel(
    val central: FakeBlePlatformClient,
    val peripheral: FakeBlePlatformPeripheral,
    val deviceAddress: String = "fake-device",
) {
    private var isConnected = false
    private var autoRoutingEnabled = true // Enabled to route data between central and peripheral

    private val forwardingInProgress: AtomicBoolean = atomic(false) // Track to prevent infinite loops

    private val scope = CoroutineScope(SupervisorJob())

    init {
        // Set up automatic bidirectional data routing via SharedFlow collection

        // When central emits events, route relevant ones to peripheral
        scope.launch {
            central.bleEvents.collect { event ->
                when (event) {
                    is BleEvent.ConnectionStateChanged -> {
                        // When central connects (state=2), automatically notify peripheral
                        if (autoRoutingEnabled && event.newState == 2) {
                            peripheral.simulateClientConnect(event.deviceAddress)
                            isConnected = true
                        }
                    }

                    is BleEvent.CharacteristicWrite -> {
                        if (autoRoutingEnabled && isConnected && !forwardingInProgress.value) {
                            val data = central.getWrittenData(event.characteristic.id).lastOrNull()
                            if (data != null) {
                                forwardingInProgress.value = true
                                try {
                                    peripheral.simulateClientWrite(
                                        deviceAddress,
                                        event.service.id,
                                        event.characteristic.id,
                                        data,
                                    )
                                } finally {
                                    forwardingInProgress.value = false
                                }
                            }
                        }
                    }

                    else -> { /* no routing needed for other central events */ }
                }
            }
        }

        // When peripheral emits events, route notifications to central
        scope.launch {
            peripheral.bleEvents.collect { event ->
                when (event) {
                    is BleEvent.Notification -> {
                        if (autoRoutingEnabled && isConnected) {
                            // Forward peripheral notification to central as characteristic changed
                            central.simulateCharacteristicChanged(
                                event.service.id,
                                event.characteristic.id,
                                event.value,
                            )
                        }
                    }

                    // DON'T forward CharacteristicChanged events back to central!
                    // CharacteristicChanged is triggered by central's own write - the peripheral's
                    // own collectors will receive it directly from the peripheral.
                    else -> { /* no routing needed */ }
                }
            }
        }
    }

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
