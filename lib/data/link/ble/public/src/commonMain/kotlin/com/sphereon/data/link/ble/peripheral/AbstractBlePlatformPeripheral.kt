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

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.link.ble.peripheral

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.log.LogManager
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.CharacteristicWriteError
import com.sphereon.data.link.ble.client.BleEvent
import com.sphereon.data.link.ble.model.GattCharacteristic
import com.sphereon.data.link.ble.model.GattService
import com.sphereon.data.link.ble.model.HasUuidId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Abstract base class for BLE peripheral implementations across platforms.
 * Provides common event handling and lifecycle management.
 */
abstract class AbstractBlePlatformPeripheral(
    logManager: LogManager,
    protected val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) : BlePlatformPeripheral {

    protected val log = logManager.withTag("BlePlatformPeripheral")

    // Event flows for BLE events
    private val _externalBleEvents = MutableSharedFlow<BleEvent>(extraBufferCapacity = Int.MAX_VALUE)
    private val _internalBleEvents = MutableSharedFlow<BleEvent>(extraBufferCapacity = Int.MAX_VALUE)
    val externalBleEvents: SharedFlow<BleEvent> = _externalBleEvents.shareIn(scope, SharingStarted.Eagerly, replay = 0)
    protected val internalBleEvents: SharedFlow<BleEvent> = _internalBleEvents.shareIn(scope, SharingStarted.Eagerly, replay = 0)

    // List of registered event listeners
    private val listeners = mutableListOf<BleEvent.Listener>()

    /**
     * Emit an event to both internal and external listeners
     */
    protected suspend fun emitEvent(event: BleEvent) {
        log.debug("Emitting BLE event: ${event::class.simpleName}")
        _externalBleEvents.emit(event)
        _internalBleEvents.emit(event)

        // Notify all registered listeners
        // Create a snapshot to avoid ConcurrentModificationException
        // when listeners are added/removed during iteration
        val listenerSnapshot = listeners.toList()
        listenerSnapshot.forEach { listener ->
            try {
                when (event) {
                    is BleEvent.CharacteristicChanged -> listener.onCharacteristicChanged(event)
                    is BleEvent.CharacteristicRead -> listener.onCharacteristicRead(event)
                    is BleEvent.CharacteristicWrite -> listener.onCharacteristicWrite(event)
                    is BleEvent.ConnectionStateChanged -> listener.onConnectionStateChanged(event)
                    is BleEvent.DescriptorRead -> listener.onDescriptorRead(event)
                    is BleEvent.DescriptorWrite -> listener.onDescriptorWrite(event)
                    is BleEvent.DeviceFound -> listener.onDeviceFound(event)
                    is BleEvent.Error -> listener.onBleError(event)
                    is BleEvent.MtuChanged -> listener.onMtuChanged(event)
                    is BleEvent.Notification -> listener.onNotification(event)
                    is BleEvent.ScanResult -> listener.onScanResult(event)
                    is BleEvent.ScanStarted -> listener.onScanStarted(event)
                    is BleEvent.ScanStopped -> listener.onScanStopped(event)
                    is BleEvent.ServicesDiscovered -> listener.onServicesDiscovered(event)
                    is BleEvent.AbstractBleEvent -> {
                        // Generic handler for other AbstractBleEvent subclasses
                    }
                }
            } catch (e: Exception) {
                log.error("Error in BLE event listener", exception = e)
            }
        }
    }

    override fun getBleEventListeners(): Set<BleEvent.Listener> = listeners.toSet()

    override fun addBleEventListener(vararg listener: BleEvent.Listener): BleEvent.Handlers {
        log.debug("Adding ${listener.size} BLE event listener(s)")
        listeners.addAll(listener)
        return this
    }

    override fun removeBleEventListener(listener: BleEvent.Listener): BleEvent.Handlers {
        log.debug("Removing BLE event listener: ${listener::class.simpleName}")
        listeners.remove(listener)
        return this
    }

    override fun clearBleEventListeners(): BleEvent.Handlers {
        log.debug("Clearing all BLE event listeners")
        listeners.clear()
        return this
    }

    /**
     * Platform-specific implementation to start advertising
     */
    abstract override suspend fun startAdvertising(serviceUuid: Uuid): IdkResult<Unit, BleError>

    /**
     * Platform-specific implementation to stop advertising
     */
    abstract override suspend fun stopAdvertising(): IdkResult<Unit, BleError>

    /**
     * Platform-specific implementation to add a GATT service
     */
    abstract override suspend fun addService(service: GattService): IdkResult<GattService, BleError>

    /**
     * Platform-specific implementation to remove a GATT service
     */
    abstract override suspend fun removeService(service: HasUuidId): IdkResult<Unit, BleError>

    /**
     * Platform-specific implementation to notify characteristic changed
     */
    abstract override suspend fun notifyCharacteristicChanged(
        service: HasUuidId,
        characteristic: HasUuidId,
        value: ByteArray
    ): IdkResult<Unit, CharacteristicWriteError>

    /**
     * Platform-specific implementation to read characteristic
     */
    abstract override suspend fun readCharacteristic(
        service: HasUuidId,
        characteristic: HasUuidId
    ): IdkResult<GattCharacteristic, BleError>

    /**
     * Platform-specific implementation to wait for connection
     */
    abstract override suspend fun awaitConnection(): IdkResult<Unit, BleError>

    /**
     * Platform-specific implementation to disconnect
     */
    abstract override suspend fun disconnect(): IdkResult<Unit, BleError>

    /**
     * Clean up resources
     */
    override fun close() {
        log.info("Closing BlePlatformPeripheral")
        listeners.clear()
    }
}
