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

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.link.ble.peripheral

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.log.LogManager
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
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
@JsExportCompat
abstract class AbstractBlePlatformPeripheral(
    logManager: LogManager,
    protected val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) : BlePlatformPeripheral {
    protected val log = logManager.withTag("BlePlatformPeripheral")

    // Event flows for BLE events
    private val mutableExternalBleEvents = MutableSharedFlow<BleEvent>(extraBufferCapacity = Int.MAX_VALUE)
    private val mutableInternalBleEvents = MutableSharedFlow<BleEvent>(extraBufferCapacity = Int.MAX_VALUE)

    @JsExportIgnoreCompat
    val externalBleEvents: SharedFlow<BleEvent> = mutableExternalBleEvents.shareIn(scope, SharingStarted.Eagerly, replay = 0)
    protected val internalBleEvents: SharedFlow<BleEvent> = mutableInternalBleEvents.shareIn(scope, SharingStarted.Eagerly, replay = 0)

    @JsExportIgnoreCompat
    override val bleEvents: SharedFlow<BleEvent> get() = externalBleEvents

    /**
     * Emit an event to both internal and external listeners
     */
    protected suspend fun emitEvent(event: BleEvent) {
        log.debug("Emitting BLE event: ${event::class.simpleName}")
        mutableExternalBleEvents.emit(event)
        mutableInternalBleEvents.emit(event)
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
        value: ByteArray,
    ): IdkResult<Unit, CharacteristicWriteError>

    /**
     * Platform-specific implementation to read characteristic
     */
    abstract override suspend fun readCharacteristic(
        service: HasUuidId,
        characteristic: HasUuidId,
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
    }
}
