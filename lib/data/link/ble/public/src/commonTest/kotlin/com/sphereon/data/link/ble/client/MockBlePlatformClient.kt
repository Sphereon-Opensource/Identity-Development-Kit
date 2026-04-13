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

package com.sphereon.data.link.ble.client

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.asErrorResult
import com.sphereon.data.link.ble.*
import com.sphereon.data.link.ble.client.cmd.ScanDevicesArgs
import com.sphereon.data.link.ble.model.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Mock implementation of [BlePlatformClient] for testing central mode BLE operations.
 *
 * This mock simulates BLE hardware behavior for automated testing without requiring
 * physical devices. It tracks all operations, emits events, and allows configuration
 * of behavior including failures and delays.
 *
 * ## Key Features:
 * - Event emission simulation (characteristic changes, MTU changes, etc.)
 * - State tracking (connection, discovered services, MTU)
 * - Data tracking (written values, notification subscriptions)
 * - Configurable behavior (delays, failures)
 * - Thread-safe operations
 *
 * ## Usage Example:
 * ```kotlin
 * val mockClient = MockBlePlatformClient()
 * mockClient.addDiscoverableDevice(mockDevice)
 *
 * // Add listener to receive events
 * val listener: BleEvent.Listener = ...
 * mockClient.addBleEventListener(listener)
 *
 * // Simulate data arrival
 * mockClient.simulateCharacteristicChanged(serviceUuid, charUuid, data)
 * ```
 */
@OptIn(ExperimentalUuidApi::class)
class MockBlePlatformClient : BlePlatformClient {

    // Event emission
    private val eventListeners = mutableSetOf<BleEvent.Listener>()

    // State tracking
    var isConnected: Boolean = false
        private set
    var discoveredServices: List<GattService> = emptyList()
        private set
    var currentMtu: Int = 23 // Default BLE MTU
        private set
    var connectedDevice: BleDevice? = null
        private set
    private val requestId: Uuid = Uuid.random()

    // Mock data storage
    private val characteristicValues = mutableMapOf<Uuid, ByteArray>()
    private val writtenData = mutableMapOf<Uuid, MutableList<ByteArray>>()
    private val notificationSubscriptions = mutableSetOf<Uuid>()
    private val discoverableDevices = mutableListOf<BleDevice>()

    // Behavior configuration
    var scanDelay: Duration = 0.milliseconds
    var connectDelay: Duration = 0.milliseconds
    var writeDelay: Duration = 0.milliseconds
    var shouldFailNextWrite: Boolean = false
    var shouldFailNextConnect: Boolean = false
    var shouldFailNextScan: Boolean = false

    // Test helpers

    /**
     * Adds a device that will be returned during scan operations.
     */
    fun addDiscoverableDevice(device: BleDevice) {
        discoverableDevices.add(device)
    }

    /**
     * Simulates receiving a characteristic change notification from the peripheral.
     * This triggers [BleEvent.CharacteristicChanged] events to all listeners.
     */
    fun simulateCharacteristicChanged(serviceId: Uuid, charId: Uuid, value: ByteArray) {
        val service = discoveredServices.find { it.id == serviceId }
        val characteristic = service?.characteristics?.find { it.id == charId }

        if (service == null || characteristic == null) {
            throw IllegalArgumentException("Service $serviceId or characteristic $charId not found in discovered services")
        }

        val event = BleEvent.CharacteristicChanged(
            requestId = requestId,
            deviceAddress = connectedDevice?.address ?: "mock-device",
            service = service,
            characteristic = characteristic,
            value = value
        )

        eventListeners.forEach { it.onCharacteristicChanged(event) }
    }

    /**
     * Simulates a BLE connection state change.
     * @param newState 0 = disconnected, 2 = connected, 3 = disconnecting
     */
    fun simulateConnectionStateChange(newState: Int) {
        isConnected = (newState == 2)

        val event = BleEvent.ConnectionStateChanged(
            requestId = requestId,
            deviceAddress = connectedDevice?.address ?: "mock-device",
            newState = newState,
            status = 0 // Success
        )

        eventListeners.forEach { it.onConnectionStateChanged(event) }
    }

    /**
     * Simulates an MTU size change.
     */
    fun simulateMtuChange(newMtu: Int) {
        currentMtu = newMtu

        val event = BleEvent.MtuChanged(
            requestId = requestId,
            deviceAddress = connectedDevice?.address ?: "mock-device",
            mtu = newMtu,
            status = 0 // Success
        )

        eventListeners.forEach { it.onMtuChanged(event) }
    }

    /**
     * Gets all data written to a specific characteristic.
     */
    fun getWrittenData(characteristicId: Uuid): List<ByteArray> {
        return writtenData[characteristicId]?.toList() ?: emptyList()
    }

    /**
     * Clears all written data history.
     */
    fun clearWrittenData() {
        writtenData.clear()
    }

    // BlePlatformClient implementation

    override suspend fun scan(args: ScanDevicesArgs): IdkResult<List<BleDevice>, ScanError> {
        if (shouldFailNextScan) {
            shouldFailNextScan = false
            return BleErrors.scanFailed("Mock scan failure").asErrorResult()
        }

        if (scanDelay > Duration.ZERO) {
            kotlinx.coroutines.delay(scanDelay)
        }

        val event = BleEvent.ScanResult(
            requestId = requestId,
            deviceAddress = "scanner",
            devices = discoverableDevices.toSet()
        )
        eventListeners.forEach { it.onScanResult(event) }

        return Ok(discoverableDevices.toList())
    }

    override suspend fun connect(device: IHasAddress): IdkResult<BleDevice, ConnectionFailedError> {
        if (shouldFailNextConnect) {
            shouldFailNextConnect = false
            return BleErrors.connectionFailed("Mock connection failure").asErrorResult()
        }

        if (connectDelay > Duration.ZERO) {
            kotlinx.coroutines.delay(connectDelay)
        }

        val bleDevice = if (device is BleDevice) {
            device
        } else {
            // Find in discoverable devices or create mock
            discoverableDevices.find { it.address == device.address }
                ?: BleDevice(
                    address = device.address,
                    name = "Mock Device",
                    services = emptyList()
                )
        }

        connectedDevice = bleDevice
        isConnected = true

        simulateConnectionStateChange(2) // Connected

        return Ok(bleDevice)
    }

    override suspend fun disconnect(): IdkResult<Unit, BleError> {
        isConnected = false
        connectedDevice = null
        discoveredServices = emptyList()

        simulateConnectionStateChange(0) // Disconnected

        return Ok(Unit)
    }

    override suspend fun setMtu(mtu: Int): IdkResult<Int, MtuChangeError> {
        if (!isConnected) {
            return BleErrors.mtuChangeFailed("Not connected").asErrorResult()
        }

        simulateMtuChange(mtu)
        return Ok(mtu)
    }

    override suspend fun discoverServices(): IdkResult<List<GattService>, BleError> {
        if (!isConnected) {
            return BleErrors.serviceDiscoveryFailed("Not connected").asErrorResult()
        }

        // Convert BleDevice.services to GattService if needed
        val services = if (connectedDevice?.services?.firstOrNull() is GattService) {
            @Suppress("UNCHECKED_CAST")
            connectedDevice?.services as? List<GattService> ?: emptyList()
        } else {
            emptyList()
        }

        discoveredServices = services

        val event = BleEvent.ServicesDiscovered(
            requestId = requestId,
            deviceAddress = connectedDevice?.address ?: "mock-device",
            services = discoveredServices,
            status = 0 // Success
        )

        eventListeners.forEach { it.onServicesDiscovered(event) }

        return Ok(discoveredServices)
    }

    override suspend fun readCharacteristic(
        service: HasUuidId,
        characteristic: HasUuidId
    ): IdkResult<GattCharacteristic, CharacteristicReadError> {
        if (!isConnected) {
            return BleErrors.readCharacteristicFailed("Not connected").asErrorResult()
        }

        val value = characteristicValues[characteristic.id] ?: byteArrayOf()
        val gattService = discoveredServices.find { it.id == service.id }
            ?: return BleErrors.readCharacteristicFailed("Service not found").asErrorResult()

        val gattChar = gattService.characteristics.find { it.id == characteristic.id }
            ?: return BleErrors.readCharacteristicFailed("Characteristic not found").asErrorResult()

        // Note: GattCharacteristic doesn't store value, so we just return the characteristic
        // The value is tracked separately in characteristicValues map
        return Ok(gattChar)
    }

    override suspend fun writeCharacteristic(
        service: HasUuidId,
        characteristic: HasUuidId,
        value: ByteArray,
        writeType: CharacteristicWriteMode
    ): IdkResult<Int, CharacteristicWriteError> {
        if (!isConnected) {
            return BleErrors.writeCharacteristicFailed("Not connected").asErrorResult()
        }

        if (shouldFailNextWrite) {
            shouldFailNextWrite = false
            return BleErrors.writeCharacteristicFailed("Mock write failure").asErrorResult()
        }

        if (writeDelay > Duration.ZERO) {
            kotlinx.coroutines.delay(writeDelay)
        }

        // Store written data
        characteristicValues[characteristic.id] = value
        writtenData.getOrPut(characteristic.id) { mutableListOf() }.add(value)

        // Emit write event
        val event = BleEvent.CharacteristicWrite(
            requestId = requestId,
            deviceAddress = connectedDevice?.address ?: "mock-device",
            service = service,
            characteristic = characteristic,
            status = 0 // Success
        )

        eventListeners.forEach { it.onCharacteristicWrite(event) }

        return Ok(value.size)
    }

    override suspend fun getDevice(): IdkResult<BleDevice, ConnectionFailedError> {
        return if (connectedDevice != null) {
            Ok(connectedDevice!!)
        } else {
            BleErrors.connectionFailed("No device connected").asErrorResult()
        }
    }

    override suspend fun enableNotifications(
        service: HasUuidId,
        characteristic: HasUuidId
    ): IdkResult<GattDescriptor, NotificationFailedError> {
        if (!isConnected) {
            return BleErrors.notificationFailed("Not connected").asErrorResult()
        }

        notificationSubscriptions.add(characteristic.id)

        val descriptor = GattDescriptor(
            id = Uuid.random(),
            permissions = emptySet(),
            characteristic = null
        )

        return Ok(descriptor)
    }

    override suspend fun discoverServiceCharacteristics(
        service: HasUuidId,
        characteristics: List<HasUuidId>?
    ): IdkResult<List<GattCharacteristic>, CharacteristicReadError> {
        if (!isConnected) {
            return BleErrors.readCharacteristicFailed("Not connected").asErrorResult()
        }

        val gattService = discoveredServices.find { it.id == service.id }
            ?: return BleErrors.readCharacteristicFailed("Service not found").asErrorResult()

        val result = if (characteristics != null) {
            val charIds = characteristics.map { it.id }.toSet()
            gattService.characteristics.filter { it.id in charIds }
        } else {
            gattService.characteristics
        }

        return Ok(result)
    }

    // Event listener management

    override fun getBleEventListeners(): Set<BleEvent.Listener> = eventListeners.toSet()

    override fun addBleEventListener(vararg listener: BleEvent.Listener): BlePlatformClient {
        eventListeners.addAll(listener)
        return this
    }

    override fun removeBleEventListener(listener: BleEvent.Listener): BlePlatformClient {
        eventListeners.remove(listener)
        return this
    }

    override fun clearBleEventListeners(): BlePlatformClient {
        eventListeners.clear()
        return this
    }

    override fun close() {
        isConnected = false
        connectedDevice = null
        discoveredServices = emptyList()
        eventListeners.clear()
        writtenData.clear()
        characteristicValues.clear()
        notificationSubscriptions.clear()
    }
}
