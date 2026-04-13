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

package com.sphereon.data.link.ble.peripheral

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.asErrorResult
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.BleErrors
import com.sphereon.data.link.ble.CharacteristicWriteError
import com.sphereon.data.link.ble.client.BleEvent
import com.sphereon.data.link.ble.model.GattCharacteristic
import com.sphereon.data.link.ble.model.GattDescriptor
import com.sphereon.data.link.ble.model.GattService
import com.sphereon.data.link.ble.model.HasUuidId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Mock implementation of [BlePlatformPeripheral] for testing peripheral mode BLE operations.
 *
 * This mock simulates BLE peripheral (server) behavior for automated testing without requiring
 * physical devices. It tracks connected centrals, manages session IDs, emits events, and allows
 * configuration of behavior including failures and delays.
 *
 * ## Key Features:
 * - Session ID tracking per connected device
 * - Event emission simulation (client writes, connections, disconnections)
 * - State tracking (advertising, connected devices, added services)
 * - Data tracking (notified data, received writes)
 * - Configurable behavior (delays, failures, session timeout)
 * - Thread-safe operations
 *
 * ## Usage Example:
 * ```kotlin
 * val mockPeripheral = MockBlePlatformPeripheral()
 *
 * // Collect events via SharedFlow
 * scope.launch {
 *     mockPeripheral.bleEvents.collect { event ->
 *         when (event) {
 *             is BleEvent.ConnectionStateChanged -> handleConnection(event)
 *             is BleEvent.CharacteristicChanged -> handleWrite(event)
 *             else -> {}
 *         }
 *     }
 * }
 *
 * // Simulate client connection
 * mockPeripheral.simulateClientConnect("device123")
 *
 * // Simulate client writing data
 * mockPeripheral.simulateClientWrite("device123", serviceUuid, charUuid, data)
 * ```
 */
@OptIn(ExperimentalUuidApi::class)
class MockBlePlatformPeripheral : BlePlatformPeripheral {
    // Event emission via SharedFlow
    private val _bleEvents = MutableSharedFlow<BleEvent>(extraBufferCapacity = Int.MAX_VALUE)
    override val bleEvents: SharedFlow<BleEvent> get() = _bleEvents

    // State tracking
    var isAdvertising: Boolean = false
        private set
    var addedServices: MutableList<GattService> = mutableListOf()
        private set

    // Session tracking: deviceAddress -> sessionId
    private val connectedDevices = mutableMapOf<String, Uuid>()

    // Session activity tracking: sessionId -> lastActivityTime
    private val sessionActivity = mutableMapOf<Uuid, Long>()

    // Mock data storage
    private val notifiedData = mutableMapOf<Uuid, MutableList<ByteArray>>()
    private val receivedWrites = mutableMapOf<Uuid, MutableList<Pair<String, ByteArray>>>()

    // Event capture for testing
    private val capturedEvents = mutableListOf<BleEvent>()

    // Behavior configuration
    var advertiseDelay: Duration = 0.milliseconds
    var connectionDelay: Duration = 0.milliseconds
    var notifyDelay: Duration = 0.milliseconds
    var shouldFailNextNotify: Boolean = false
    var shouldFailNextAdvertise: Boolean = false
    private var sessionTimeout: Duration = 10.minutes

    // Connection awaiter
    private var connectionAwaiter: CompletableDeferred<Unit>? = null

    // Test helpers

    /**
     * Simulates a client (central) connecting to this peripheral.
     * Creates a new session ID for the device and emits connection event.
     */
    fun simulateClientConnect(deviceAddress: String) {
        val sessionId = connectedDevices.getOrPut(deviceAddress) { Uuid.random() }
        sessionActivity[sessionId] = Clock.System.now().toEpochMilliseconds()

        val event =
            BleEvent.ConnectionStateChanged(
                requestId = sessionId,
                deviceAddress = deviceAddress,
                newState = 2, // Connected
                status = 0, // Success
            )

        capturedEvents.add(event)
        _bleEvents.tryEmit(event)

        // Complete any pending connection awaiter
        connectionAwaiter?.complete(Unit)
    }

    /**
     * Simulates a client (central) disconnecting from this peripheral.
     * Removes the session ID and emits disconnection event.
     */
    fun simulateClientDisconnect(deviceAddress: String) {
        val sessionId = connectedDevices.remove(deviceAddress)
        if (sessionId != null) {
            sessionActivity.remove(sessionId)

            val event =
                BleEvent.ConnectionStateChanged(
                    requestId = sessionId,
                    deviceAddress = deviceAddress,
                    newState = 0, // Disconnected
                    status = 0, // Success
                )

            capturedEvents.add(event)
            _bleEvents.tryEmit(event)
        }
    }

    /**
     * Simulates a client (central) writing data to a characteristic.
     * Emits [BleEvent.CharacteristicChanged] to all collectors.
     */
    fun simulateClientWrite(
        deviceAddress: String,
        serviceId: Uuid,
        charId: Uuid,
        value: ByteArray,
    ) {
        val sessionId =
            connectedDevices[deviceAddress]
                ?: throw IllegalArgumentException("Device $deviceAddress is not connected")

        // Update activity timestamp
        sessionActivity[sessionId] = Clock.System.now().toEpochMilliseconds()

        // Store received write
        receivedWrites.getOrPut(charId) { mutableListOf() }.add(deviceAddress to value)

        // Find service and characteristic
        val service = addedServices.find { it.id == serviceId }
        val characteristic = service?.characteristics?.find { it.id == charId }

        if (service == null || characteristic == null) {
            throw IllegalArgumentException("Service $serviceId or characteristic $charId not found")
        }

        val event =
            BleEvent.CharacteristicChanged(
                requestId = sessionId,
                deviceAddress = deviceAddress,
                service = service,
                characteristic = characteristic,
                value = value,
            )

        capturedEvents.add(event)
        _bleEvents.tryEmit(event)
    }

    /**
     * Simulates MTU change for a specific device.
     */
    fun simulateMtuChange(
        deviceAddress: String,
        newMtu: Int,
    ) {
        val sessionId =
            connectedDevices[deviceAddress]
                ?: throw IllegalArgumentException("Device $deviceAddress is not connected")

        val event =
            BleEvent.MtuChanged(
                requestId = sessionId,
                deviceAddress = deviceAddress,
                mtu = newMtu,
                status = 0, // Success
            )

        capturedEvents.add(event)
        _bleEvents.tryEmit(event)
    }

    /**
     * Gets all data notified on a specific characteristic.
     */
    fun getNotifiedData(characteristicId: Uuid): List<ByteArray> = notifiedData[characteristicId]?.toList() ?: emptyList()

    /**
     * Gets all writes received on a specific characteristic.
     */
    fun getReceivedWrites(characteristicId: Uuid): List<Pair<String, ByteArray>> = receivedWrites[characteristicId]?.toList() ?: emptyList()

    /**
     * Gets the number of currently connected devices.
     */
    fun getConnectedDeviceCount(): Int = connectedDevices.size

    /**
     * Gets all captured events for testing verification.
     */
    fun getCapturedEvents(): List<BleEvent> = capturedEvents.toList()

    /**
     * Gets the number of active sessions.
     */
    fun getActiveSessionCount(): Int = connectedDevices.size

    /**
     * Configures the session timeout duration for testing.
     */
    fun setSessionTimeout(timeout: Duration) {
        sessionTimeout = timeout
    }

    /**
     * Manually runs session cleanup (removes stale sessions).
     */
    fun runCleanup() {
        val now = Clock.System.now().toEpochMilliseconds()
        val staleSessionIds =
            sessionActivity
                .filter { (_, lastActivity) ->
                    (now - lastActivity).compareTo(sessionTimeout.inWholeMilliseconds) > 0
                }.keys

        // Remove stale sessions
        val devicesToRemove = connectedDevices.filter { it.value in staleSessionIds }.keys
        devicesToRemove.forEach { deviceAddress ->
            connectedDevices.remove(deviceAddress)
        }

        staleSessionIds.forEach { sessionId ->
            sessionActivity.remove(sessionId)
        }
    }

    /**
     * Clears all test data.
     */
    fun clearTestData() {
        notifiedData.clear()
        receivedWrites.clear()
        capturedEvents.clear()
    }

    // BlePlatformPeripheral implementation

    override suspend fun startAdvertising(serviceUuid: Uuid): IdkResult<Unit, BleError> {
        if (shouldFailNextAdvertise) {
            shouldFailNextAdvertise = false
            return BleErrors.advertiseFailed("Mock advertising failure").asErrorResult()
        }

        if (advertiseDelay > Duration.ZERO) {
            delay(advertiseDelay)
        }

        isAdvertising = true
        return Ok(Unit)
    }

    override suspend fun stopAdvertising(): IdkResult<Unit, BleError> {
        isAdvertising = false
        return Ok(Unit)
    }

    override suspend fun addService(service: GattService): IdkResult<GattService, BleError> {
        addedServices.add(service)
        return Ok(service)
    }

    override suspend fun removeService(service: HasUuidId): IdkResult<Unit, BleError> {
        addedServices.removeAll { it.id == service.id }
        return Ok(Unit)
    }

    override suspend fun notifyCharacteristicChanged(
        service: HasUuidId,
        characteristic: HasUuidId,
        value: ByteArray,
    ): IdkResult<Unit, CharacteristicWriteError> {
        if (shouldFailNextNotify) {
            shouldFailNextNotify = false
            return BleErrors.writeCharacteristicFailed("Mock notify failure").asErrorResult()
        }

        if (notifyDelay > Duration.ZERO) {
            delay(notifyDelay)
        }

        // Store notified data
        notifiedData.getOrPut(characteristic.id) { mutableListOf() }.add(value)

        // Emit notification events to all connected devices
        connectedDevices.forEach { (deviceAddress, sessionId) ->
            // Update activity
            sessionActivity[sessionId] = Clock.System.now().toEpochMilliseconds()

            val gattService = addedServices.find { it.id == service.id }
            val gattChar = gattService?.characteristics?.find { it.id == characteristic.id }

            if (gattService != null && gattChar != null) {
                val event =
                    BleEvent.Notification(
                        requestId = sessionId,
                        deviceAddress = deviceAddress,
                        service = gattService,
                        characteristic = gattChar,
                        value = value,
                    )

                capturedEvents.add(event)
                _bleEvents.tryEmit(event)
            }
        }

        return Ok(Unit)
    }

    override suspend fun readCharacteristic(
        service: HasUuidId,
        characteristic: HasUuidId,
    ): IdkResult<GattCharacteristic, BleError> {
        val gattService =
            addedServices.find { it.id == service.id }
                ?: return BleErrors.readCharacteristicFailed("Service not found").asErrorResult()

        val gattChar =
            gattService.characteristics.find { it.id == characteristic.id }
                ?: return BleErrors.readCharacteristicFailed("Characteristic not found").asErrorResult()

        return Ok(gattChar)
    }

    override suspend fun awaitConnection(): IdkResult<Unit, BleError> {
        if (connectionDelay > Duration.ZERO) {
            delay(connectionDelay)
        }

        // If already connected, return immediately
        if (connectedDevices.isNotEmpty()) {
            return Ok(Unit)
        }

        // Otherwise, wait for a connection
        connectionAwaiter = CompletableDeferred()
        connectionAwaiter?.await()

        return Ok(Unit)
    }

    override suspend fun disconnect(): IdkResult<Unit, BleError> {
        // Disconnect all connected devices
        connectedDevices.keys.toList().forEach { deviceAddress ->
            simulateClientDisconnect(deviceAddress)
        }

        return Ok(Unit)
    }

    override fun close() {
        isAdvertising = false
        connectedDevices.clear()
        sessionActivity.clear()
        addedServices.clear()
        notifiedData.clear()
        receivedWrites.clear()
        capturedEvents.clear()
        connectionAwaiter?.cancel()
    }
}
