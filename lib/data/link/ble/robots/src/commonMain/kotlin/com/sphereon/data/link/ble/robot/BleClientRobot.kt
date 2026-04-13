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

package com.sphereon.data.link.ble.robot

import com.sphereon.data.link.ble.client.BlePlatformClient
import com.sphereon.data.link.ble.client.FakeBlePlatformClient
import com.sphereon.data.link.ble.model.BleDevice
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Test robot for interacting with BLE client (central mode) operations.
 *
 * Following [Amazon App Platform testing guidelines](https://amzn.github.io/app-platform/testing/),
 * this robot abstracts test interactions from the underlying fake implementation. Tests should
 * interact with robots rather than fakes directly for better maintainability.
 *
 * **Note:** Robots in App Platform are simply injected via constructor injection. Tests access them
 * by injecting them like any other dependency.
 *
 * ## Features:
 * - Configure discoverable devices
 * - Simulate failure scenarios
 * - Configure timing delays
 * - Simulate BLE events
 * - Assert connection state
 * - Verify data exchange
 *
 * ## Usage Example:
 * ```kotlin
 * @Test
 * fun `test BLE scan`() = runTest {
 *     val robot = component.bleClientRobot
 *     robot.addDiscoverableDevice(testDevice)
 *     robot.assertConnected()
 * }
 * ```
 *
 * @see <a href="https://amzn.github.io/app-platform/testing/">App Platform Testing Guide</a>
 */
@OptIn(ExperimentalUuidApi::class)
@Inject
@SingleIn(AppScope::class)
class BleClientRobot(
    private val blePlatformClient: BlePlatformClient
) {

    private val fakeClient: FakeBlePlatformClient
        get() = blePlatformClient as FakeBlePlatformClient

    // Configuration methods

    /**
     * Adds a device that will be returned during scan operations.
     */
    fun addDiscoverableDevice(device: BleDevice) {
        fakeClient.addDiscoverableDevice(device)
    }

    // Failure simulation methods

    /**
     * Configures the next scan operation to fail.
     */
    fun nextScanFails() {
        fakeClient.shouldFailNextScan = true
    }

    /**
     * Configures the next connection attempt to fail.
     */
    fun nextConnectionFails() {
        fakeClient.shouldFailNextConnect = true
    }

    /**
     * Configures the next write operation to fail.
     */
    fun nextWriteFails() {
        fakeClient.shouldFailNextWrite = true
    }

    // Timing configuration methods

    /**
     * Sets the delay for scan operations.
     * Useful for testing timeout handling.
     */
    fun setScanDelay(delay: Duration) {
        fakeClient.scanDelay = delay
    }

    /**
     * Sets the delay for connection operations.
     * Useful for testing timeout handling.
     */
    fun setConnectionDelay(delay: Duration) {
        fakeClient.connectDelay = delay
    }

    /**
     * Sets the delay for write operations.
     * Useful for testing timeout handling.
     */
    fun setWriteDelay(delay: Duration) {
        fakeClient.writeDelay = delay
    }

    // Event simulation methods

    /**
     * Simulates receiving a characteristic change notification from the peripheral.
     *
     * @param serviceId The service UUID
     * @param charId The characteristic UUID
     * @param value The data received
     */
    fun simulateCharacteristicChanged(
        serviceId: Uuid,
        charId: Uuid,
        value: ByteArray
    ) {
        fakeClient.simulateCharacteristicChanged(serviceId, charId, value)
    }

    /**
     * Simulates an MTU size change.
     *
     * @param newMtu The new MTU size
     */
    fun simulateMtuChange(newMtu: Int) {
        fakeClient.simulateMtuChange(newMtu)
    }

    /**
     * Simulates a connection state change.
     *
     * @param newState 0 = disconnected, 2 = connected, 3 = disconnecting
     */
    fun simulateConnectionStateChange(newState: Int) {
        fakeClient.simulateConnectionStateChange(newState)
    }

    // Assertion methods

    /**
     * Asserts that the client is currently connected to a device.
     */
    fun assertConnected() {
        fakeClient.isConnected.shouldBeTrue()
    }

    /**
     * Asserts that the client is not connected to any device.
     */
    fun assertDisconnected() {
        fakeClient.isConnected.shouldBeFalse()
    }

    /**
     * Asserts that specific data was written to a characteristic.
     *
     * @param charId The characteristic UUID
     * @param expectedData The expected data that should have been written
     */
    fun assertWrittenData(charId: Uuid, expectedData: ByteArray) {
        val written = fakeClient.getWrittenData(charId)
        written.shouldContain(expectedData)
    }

    /**
     * Asserts the current MTU size.
     *
     * @param expectedMtu The expected MTU size
     */
    fun assertCurrentMtu(expectedMtu: Int) {
        fakeClient.currentMtu shouldBe expectedMtu
    }

    /**
     * Clears all written data history.
     * Useful for isolating tests.
     */
    fun clearWrittenData() {
        fakeClient.clearWrittenData()
    }
}
