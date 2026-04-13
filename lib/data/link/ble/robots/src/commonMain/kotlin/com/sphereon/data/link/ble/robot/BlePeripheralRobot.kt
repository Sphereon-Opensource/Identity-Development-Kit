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

package com.sphereon.data.link.ble.robot

import com.sphereon.data.link.ble.peripheral.BlePlatformPeripheral
import com.sphereon.data.link.ble.peripheral.FakeBlePlatformPeripheral
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Test robot for interacting with BLE peripheral (server mode) operations.
 *
 * Following [Amazon App Platform testing guidelines](https://amzn.github.io/app-platform/testing/),
 * this robot abstracts test interactions from the underlying fake implementation.
 *
 * **Note:** Robots in App Platform are simply injected via constructor injection. Tests access them
 * by injecting them like any other dependency.
 *
 * ## Features:
 * - Simulate client connections/disconnections
 * - Simulate client writes to characteristics
 * - Configure failure scenarios
 * - Configure timing delays
 * - Assert advertising state
 * - Verify data exchange
 * - Manage session state
 *
 * ## Usage Example:
 * ```kotlin
 * @Test
 * fun `test peripheral advertising`() = runTest {
 *     val robot = graph.blePeripheralRobot
 *     robot.simulateClientConnect("device123")
 *     robot.assertAdvertising()
 *     robot.assertActiveSessionCount(1)
 * }
 * ```
 *
 * @see <a href="https://amzn.github.io/app-platform/testing/">App Platform Testing Guide</a>
 */
@OptIn(ExperimentalUuidApi::class)
@Inject
@SingleIn(AppScope::class)
class BlePeripheralRobot(
    private val blePlatformPeripheral: BlePlatformPeripheral,
) {
    private val fakePeripheral: FakeBlePlatformPeripheral
        get() = blePlatformPeripheral as FakeBlePlatformPeripheral

    // Client simulation methods

    /**
     * Simulates a client (central) connecting to this peripheral.
     * Creates a new session ID for the device and emits connection event.
     *
     * @param deviceAddress The address of the connecting device
     */
    fun simulateClientConnect(deviceAddress: String) {
        fakePeripheral.simulateClientConnect(deviceAddress)
    }

    /**
     * Simulates a client (central) disconnecting from this peripheral.
     * Removes the session ID and emits disconnection event.
     *
     * @param deviceAddress The address of the disconnecting device
     */
    fun simulateClientDisconnect(deviceAddress: String) {
        fakePeripheral.simulateClientDisconnect(deviceAddress)
    }

    /**
     * Simulates a client writing data to a characteristic.
     *
     * @param deviceAddress The address of the writing device
     * @param serviceId The service UUID
     * @param charId The characteristic UUID
     * @param value The data being written
     */
    fun simulateClientWrite(
        deviceAddress: String,
        serviceId: Uuid,
        charId: Uuid,
        value: ByteArray,
    ) {
        fakePeripheral.simulateClientWrite(deviceAddress, serviceId, charId, value)
    }

    /**
     * Simulates an MTU change for a specific device.
     *
     * @param deviceAddress The device address
     * @param newMtu The new MTU size
     */
    fun simulateMtuChange(
        deviceAddress: String,
        newMtu: Int,
    ) {
        fakePeripheral.simulateMtuChange(deviceAddress, newMtu)
    }

    // Failure simulation methods

    /**
     * Configures the next advertising operation to fail.
     */
    fun nextAdvertiseFails() {
        fakePeripheral.shouldFailNextAdvertise = true
    }

    /**
     * Configures the next notify operation to fail.
     */
    fun nextNotifyFails() {
        fakePeripheral.shouldFailNextNotify = true
    }

    // Timing configuration methods

    /**
     * Sets the delay for advertising operations.
     */
    fun setAdvertiseDelay(delay: Duration) {
        fakePeripheral.advertiseDelay = delay
    }

    /**
     * Sets the delay for notify operations.
     */
    fun setNotifyDelay(delay: Duration) {
        fakePeripheral.notifyDelay = delay
    }

    /**
     * Sets the session timeout duration.
     * Sessions older than this will be cleaned up.
     *
     * @param timeout The timeout duration
     */
    fun setSessionTimeout(timeout: Duration) {
        fakePeripheral.setSessionTimeout(timeout)
    }

    // Assertion methods

    /**
     * Asserts that the peripheral is currently advertising.
     */
    fun assertAdvertising() {
        fakePeripheral.isAdvertising.shouldBeTrue()
    }

    /**
     * Asserts that the peripheral is not advertising.
     */
    fun assertNotAdvertising() {
        fakePeripheral.isAdvertising.shouldBeFalse()
    }

    /**
     * Asserts that at least one client is connected.
     */
    fun assertClientConnected() {
        fakePeripheral.getConnectedDeviceCount().shouldBeGreaterThan(0)
    }

    /**
     * Asserts that specific data was received via a write operation.
     *
     * @param charId The characteristic UUID
     * @param expectedData The expected data that should have been written
     */
    fun assertReceivedWrite(
        charId: Uuid,
        expectedData: ByteArray,
    ) {
        val writes = fakePeripheral.getReceivedWrites(charId)
        val receivedData = writes.map { it.second }
        receivedData.shouldContain(expectedData)
    }

    /**
     * Asserts that specific data was notified to clients.
     *
     * @param charId The characteristic UUID
     * @param expectedData The expected data that should have been notified
     */
    fun assertNotifiedData(
        charId: Uuid,
        expectedData: ByteArray,
    ) {
        val notified = fakePeripheral.getNotifiedData(charId)
        notified.shouldContain(expectedData)
    }

    /**
     * Asserts the number of active sessions.
     *
     * @param expectedCount The expected number of active sessions
     */
    fun assertActiveSessionCount(expectedCount: Int) {
        fakePeripheral.getActiveSessionCount() shouldBe expectedCount
    }

    /**
     * Manually runs session cleanup (removes stale sessions).
     * Useful for testing session timeout behavior.
     */
    fun runCleanup() {
        fakePeripheral.runCleanup()
    }

    /**
     * Clears all test data.
     * Useful for isolating tests.
     */
    fun clearTestData() {
        fakePeripheral.clearTestData()
    }
}
