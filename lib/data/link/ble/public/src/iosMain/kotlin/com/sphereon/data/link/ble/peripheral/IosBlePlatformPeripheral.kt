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

@file:OptIn(ExperimentalUuidApi::class, ExperimentalForeignApi::class)

package com.sphereon.data.link.ble.peripheral

import com.sphereon.core.api.log.AppLogManager
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.util.toByteArray
import com.sphereon.core.util.toNSData
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.BleErrors
import com.sphereon.data.link.ble.CharacteristicWriteError
import com.sphereon.data.link.ble.client.BleEvent
import com.sphereon.data.link.ble.model.GattCharacteristic
import com.sphereon.data.link.ble.model.GattProperty
import com.sphereon.data.link.ble.model.GattService
import com.sphereon.data.link.ble.model.HasUuidId
import com.sphereon.data.link.ble.model.toUuidId
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import dev.zacsweers.metro.Inject
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBATTErrorSuccess
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBAttributePermissionsReadable
import platform.CoreBluetooth.CBAttributePermissionsWriteable
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBPeripheralManagerStatePoweredOn
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSArray
import platform.Foundation.NSError
import platform.darwin.NSObject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Returns the last 8 characters of a UUID string for compact logging
 */
private fun Uuid.toShortId(): String = this.toString().takeLast(8)

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<BlePlatformPeripheral>())
class IosBlePlatformPeripheral(
    logManager: AppLogManager,
) : AbstractBlePlatformPeripheral(logManager, CoroutineScope(Dispatchers.IO + SupervisorJob())) {

    private var peripheralManager: CBPeripheralManager? = null
    private var connectedCentral: platform.CoreBluetooth.CBCentral? = null

    // Session ID tracking per connected device (using central's identifier)
    private val deviceSessionIds = mutableMapOf<String, Uuid>()
    private val deviceLastActivityTime = mutableMapOf<String, Long>()

    // Cleanup stale sessions every 5 minutes (devices that haven't sent data in 10 minutes)
    private val SESSION_CLEANUP_INTERVAL_MS = 5 * 60 * 1000L
    private val SESSION_TIMEOUT_MS = 10 * 60 * 1000L
    
    // Continuations for async operations
    private var powerOnContinuation: kotlinx.coroutines.CancellableContinuation<Unit>? = null
    private var serviceAddedContinuation: kotlinx.coroutines.CancellableContinuation<Unit>? = null
    private var advertisingContinuation: kotlinx.coroutines.CancellableContinuation<Unit>? = null
    private var writeReadyContinuation: kotlinx.coroutines.CancellableContinuation<Unit>? = null
    private var connectionContinuation: kotlinx.coroutines.CancellableContinuation<Unit>? = null

    // Store characteristics for notification
    private val characteristicsMap = mutableMapOf<Uuid, CBMutableCharacteristic>()

    // FIFO event queue for BLE events - ensures strict ordering regardless of callback thread scheduling.
    // This is critical because iOS CoreBluetooth callbacks can arrive in rapid succession,
    // and launching separate coroutines for each would cause race conditions
    // where events get processed out of order, corrupting multi-chunk message assembly.
    // Note: This is mutable because the singleton may be reused after close() is called.
    private var bleEventQueue: Channel<BleEvent> = Channel(capacity = Channel.UNLIMITED)

    private val eventProcessorRunning = atomic(false)
    private val eventQueueMutex = Mutex()

    /**
     * Starts the BLE event processor coroutine if not already running.
     * This is called automatically from init and ensured before each enqueue.
     * Since this class is a singleton that may be reused after close(), we need
     * to be able to restart the processor.
     */
    private fun startEventProcessor() {
        // Use compareAndSet to atomically check and set the flag
        if (!eventProcessorRunning.compareAndSet(expect = false, update = true)) {
            return
        }

        scope.launch {
            log.debug("Starting BLE event processor coroutine")
            try {
                for (event in bleEventQueue) {
                    try {
                        emitEvent(event)
                    } catch (e: Exception) {
                        log.error("Error emitting BLE event: ${e.message}", exception = e)
                    }
                }
            } finally {
                eventProcessorRunning.value = false
                log.debug("BLE event processor coroutine completed")
            }
        }
    }

    /**
     * Ensures the event queue and processor are ready for use.
     * Recreates them if they were closed by a previous close() call.
     */
    private fun ensureEventQueueReady() {
        // Check if queue needs recreation - use a simple synchronized block via mutex in coroutine context
        // For non-suspend context, we just check and recreate if needed
        if (bleEventQueue.isClosedForSend) {
            log.debug("Recreating BLE event queue (was closed)")
            bleEventQueue = Channel(capacity = Channel.UNLIMITED)
        }
        // Start processor if not running
        if (!eventProcessorRunning.value) {
            startEventProcessor()
        }
    }

    /**
     * Enqueue a BLE event for FIFO processing.
     * This should be used instead of scope.launch { emitEvent(...) } to ensure event ordering.
     */
    private fun enqueueEvent(event: BleEvent) {
        ensureEventQueueReady()
        val result = bleEventQueue.trySend(event)
        if (result.isFailure) {
            log.error("Failed to enqueue BLE event: ${result.exceptionOrNull()?.message}")
        }
    }

    init {
        // Start periodic cleanup of stale sessions
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(SESSION_CLEANUP_INTERVAL_MS)
                cleanupStaleSessions()
            }
        }

        // Start the event processor
        startEventProcessor()
    }

    /**
     * Get or create a session ID for a device.
     * Updates the last activity time for the device.
     */
    private fun getOrCreateSessionId(deviceIdentifier: String): Uuid {
        deviceLastActivityTime[deviceIdentifier] = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        return deviceSessionIds.getOrPut(deviceIdentifier) {
            Uuid.random().also {
                log.info("Created new session ID ${it.toShortId()} for device $deviceIdentifier")
            }
        }
    }

    /**
     * Remove session ID for a device when it disconnects.
     * Returns the session ID that was removed, or Uuid.NIL if none existed.
     */
    private fun removeSessionId(deviceIdentifier: String): Uuid {
        val sessionId = deviceSessionIds.remove(deviceIdentifier)
        deviceLastActivityTime.remove(deviceIdentifier)
        if (sessionId != null) {
            log.info("Removed session ID ${sessionId.toShortId()} for disconnected device $deviceIdentifier")
        }
        return sessionId ?: Uuid.NIL
    }

    /**
     * Get existing session ID for a device, or Uuid.NIL if not connected.
     * Does NOT update activity time.
     */
    private fun getSessionId(deviceIdentifier: String): Uuid {
        return deviceSessionIds[deviceIdentifier] ?: Uuid.NIL
    }

    /**
     * Clean up sessions that have been inactive for too long.
     * This prevents memory leaks if disconnect events are missed.
     */
    private fun cleanupStaleSessions() {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val staleDevices = deviceLastActivityTime.filter { (_, lastActivity) ->
            (now - lastActivity) > SESSION_TIMEOUT_MS
        }.keys.toList()

        if (staleDevices.isNotEmpty()) {
            log.warn("Cleaning up ${staleDevices.size} stale session(s): ${staleDevices.joinToString(", ")}")
            staleDevices.forEach { deviceIdentifier ->
                val sessionId = removeSessionId(deviceIdentifier)
                log.info("Cleaned up stale session ${sessionId.toShortId()} for device $deviceIdentifier")
            }
        }
    }

    private val peripheralManagerDelegate = object : NSObject(), CBPeripheralManagerDelegateProtocol {
        override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
            log.debug("peripheralManagerDidUpdateState: ${peripheral.state}")
            when (peripheral.state.toInt()) {
                CBPeripheralManagerStatePoweredOn.toInt() -> {
                    powerOnContinuation?.resume(Unit)
                    powerOnContinuation = null
                }
                else -> {
                    powerOnContinuation?.resumeWithException(
                        IllegalStateException("Peripheral manager not powered on: state=${peripheral.state}")
                    )
                    powerOnContinuation = null
                }
            }
        }

        override fun peripheralManager(
            peripheral: CBPeripheralManager,
            didAddService: platform.CoreBluetooth.CBService,
            error: NSError?
        ) {
            log.debug("didAddService: ${didAddService.UUID().UUIDString}, error=$error")
            if (error != null) {
                serviceAddedContinuation?.resumeWithException(
                    IllegalStateException("Failed to add service: ${error.localizedDescription}")
                )
            } else {
                serviceAddedContinuation?.resume(Unit)
            }
            serviceAddedContinuation = null
        }

        override fun peripheralManagerDidStartAdvertising(
            peripheral: CBPeripheralManager,
            error: NSError?
        ) {
            log.debug("didStartAdvertising: error=$error")
            if (error != null) {
                advertisingContinuation?.resumeWithException(
                    IllegalStateException("Failed to start advertising: ${error.localizedDescription}")
                )
            } else {
                advertisingContinuation?.resume(Unit)
            }
            advertisingContinuation = null
        }

        override fun peripheralManager(
            peripheral: CBPeripheralManager,
            didReceiveWriteRequests: List<*>
        ) {
            log.debug("didReceiveWriteRequests: ${didReceiveWriteRequests.size} requests")

            didReceiveWriteRequests.forEach { request ->
                val attRequest = request as CBATTRequest
                peripheral.respondToRequest(attRequest, CBATTErrorSuccess)

                val central = attRequest.central
                val deviceIdentifier = central.identifier.UUIDString
                val sessionId = getOrCreateSessionId(deviceIdentifier)

                // If this is the first interaction from a central, treat it as a connection.
                // This is important because iOS peripheral mode doesn't have a direct
                // "central connected" callback. The central might write data before
                // subscribing to notifications, so we need to detect connection here too.
                if (connectedCentral == null) {
                    log.info("Central connected via write request: $deviceIdentifier")
                    connectedCentral = central
                    connectionContinuation?.resume(Unit)
                    connectionContinuation = null

                    // Emit connection state changed event using FIFO queue for ordering
                    enqueueEvent(
                        BleEvent.ConnectionStateChanged(
                            requestId = sessionId,
                            deviceAddress = deviceIdentifier,
                            newState = 2, // Connected
                            status = 0 // Success
                        )
                    )
                }

                // Emit characteristic changed event so data channels can process it.
                // Using enqueueEvent instead of scope.launch ensures strict ordering when
                // multiple BLE callbacks arrive simultaneously, which is critical for
                // correct multi-chunk message assembly.
                val charUuid = Uuid.parse(attRequest.characteristic.UUID().UUIDString)
                val serviceUuid = characteristicsMap.entries.find { it.value.UUID().UUIDString == attRequest.characteristic.UUID().UUIDString }?.key
                    ?: Uuid.NIL

                log.debug("didReceiveWriteRequests: device=$deviceIdentifier, session=${sessionId.toShortId()}, char=$charUuid")

                enqueueEvent(
                    BleEvent.CharacteristicChanged(
                        requestId = sessionId,
                        deviceAddress = deviceIdentifier,
                        service = serviceUuid.toUuidId(),
                        characteristic = charUuid.toUuidId(),
                        value = attRequest.value?.toByteArray() ?: byteArrayOf()
                    )
                )
            }
        }

        override fun peripheralManager(
            peripheral: CBPeripheralManager,
            didReceiveReadRequest: CBATTRequest
        ) {
            log.debug("didReceiveReadRequest: ${didReceiveReadRequest.characteristic.UUID().UUIDString}")

            val central = didReceiveReadRequest.central
            val deviceIdentifier = central.identifier.UUIDString

            // If this is the first interaction from a central, treat it as a connection.
            if (connectedCentral == null) {
                log.info("Central connected via read request: $deviceIdentifier")
                connectedCentral = central
                val sessionId = getOrCreateSessionId(deviceIdentifier)
                connectionContinuation?.resume(Unit)
                connectionContinuation = null

                // Emit connection state changed event using FIFO queue for ordering
                enqueueEvent(
                    BleEvent.ConnectionStateChanged(
                        requestId = sessionId,
                        deviceAddress = deviceIdentifier,
                        newState = 2, // Connected
                        status = 0 // Success
                    )
                )
            }

            peripheral.respondToRequest(didReceiveReadRequest, CBATTErrorSuccess)
        }

        override fun peripheralManagerIsReadyToUpdateSubscribers(peripheral: CBPeripheralManager) {
            log.debug("isReadyToUpdateSubscribers")
            writeReadyContinuation?.resume(Unit)
            writeReadyContinuation = null
        }

        override fun peripheralManager(
            peripheral: CBPeripheralManager,
            central: platform.CoreBluetooth.CBCentral,
            didSubscribeToCharacteristic: CBCharacteristic
        ) {
            val deviceIdentifier = central.identifier.UUIDString
            val sessionId = getOrCreateSessionId(deviceIdentifier)
            log.debug("didSubscribeToCharacteristic: ${didSubscribeToCharacteristic.UUID().UUIDString}, device=$deviceIdentifier, session=${sessionId.toShortId()}")

            if (connectedCentral == null) {
                connectedCentral = central
                connectionContinuation?.resume(Unit)
                connectionContinuation = null

                // Emit connection state changed event using FIFO queue for ordering
                enqueueEvent(
                    BleEvent.ConnectionStateChanged(
                        requestId = sessionId,
                        deviceAddress = deviceIdentifier,
                        newState = 2, // Connected
                        status = 0 // Success
                    )
                )
            }
        }

        // Note: didUnsubscribeFromCharacteristic is not reliably called on iOS
        // We rely on the timeout-based cleanup mechanism instead to prevent memory leaks
        /*override fun peripheralManager(
            peripheral: CBPeripheralManager,
            central: platform.CoreBluetooth.CBCentral,
            didUnsubscribeFromCharacteristic: CBCharacteristic
        ) {
            val deviceIdentifier = central.identifier.UUIDString
            val sessionId = removeSessionId(deviceIdentifier)
            log.debug("didUnsubscribeFromCharacteristic: ${didUnsubscribeFromCharacteristic.UUID().UUIDString}, device=$deviceIdentifier, session=${sessionId.toShortId()}")
            
            if (connectedCentral?.identifier?.UUIDString == deviceIdentifier) {
                connectedCentral = null
            }
            
            // Emit disconnection event
            scope.launch {
                emitEvent(
                    BleEvent.ConnectionStateChanged(
                        requestId = sessionId,
                        deviceAddress = deviceIdentifier,
                        newState = 0, // Disconnected
                        status = 0 // Success
                    )
                )
            }
        }*/
    }

    init {
        peripheralManager = CBPeripheralManager(
            delegate = peripheralManagerDelegate,
            queue = null,
            options = null
        )
    }

    private suspend fun waitForPowerOn() {
        if (peripheralManager?.state != CBPeripheralManagerStatePoweredOn) {
            suspendCancellableCoroutine { continuation ->
                powerOnContinuation = continuation
            }
        }
    }

    override suspend fun startAdvertising(serviceUuid: Uuid): IdkResult<Unit, BleError> {
        log.info("Starting advertising for service: $serviceUuid")
        
        return try {
            waitForPowerOn()

            suspendCancellableCoroutine { continuation ->
                advertisingContinuation = continuation
                peripheralManager?.startAdvertising(
                    mapOf(
                        CBAdvertisementDataServiceUUIDsKey to listOf(
                            CBUUID.UUIDWithString(serviceUuid.toString())
                        ) as NSArray
                    )
                )
            }

            Unit.asOkResult()
        } catch (e: Exception) {
            log.error("Failed to start advertising", exception = e)
            BleErrors.connectionFailed("Failed to start advertising: ${e.message}", e).asErrorResult()
        }
    }

    override suspend fun stopAdvertising(): IdkResult<Unit, BleError> {
        log.info("Stopping advertising")
        return try {
            peripheralManager?.stopAdvertising()
            Unit.asOkResult()
        } catch (e: Exception) {
            log.error("Failed to stop advertising", exception = e)
            BleErrors.connectionFailed("Failed to stop advertising: ${e.message}", e).asErrorResult()
        }
    }

    override suspend fun addService(service: GattService): IdkResult<GattService, BleError> {
        log.info("Adding GATT service: ${service.id}")

        return try {
            waitForPowerOn()

            val cbService = CBMutableService(
                type = CBUUID.UUIDWithString(service.id.toString()),
                primary = true
            )

            val characteristics = mutableListOf<CBMutableCharacteristic>()
            service.characteristics.forEach { char ->
                val properties = mapProperties(char.properties)
                // Use READ and WRITE permissions as default for iOS
                val permissions = CBAttributePermissionsReadable or CBAttributePermissionsWriteable

                val cbChar = CBMutableCharacteristic(
                    type = CBUUID.UUIDWithString(char.id.toString()),
                    properties = properties,
                    value = null,
                    permissions = permissions
                )
                characteristics.add(cbChar)
                characteristicsMap[char.id] = cbChar
            }

            cbService.setCharacteristics(characteristics)

            suspendCancellableCoroutine { continuation ->
                serviceAddedContinuation = continuation
                peripheralManager?.addService(cbService)
            }

            service.asOkResult()
        } catch (e: Exception) {
            log.error("Failed to add service", exception = e)
            BleErrors.connectionFailed("Failed to add service: ${e.message}", e).asErrorResult()
        }
    }

    override suspend fun removeService(service: HasUuidId): IdkResult<Unit, BleError> {
        log.info("Removing GATT service: ${service.id}")
        return try {
            peripheralManager?.removeAllServices()
            Unit.asOkResult()
        } catch (e: Exception) {
            log.error("Failed to remove service", exception = e)
            BleErrors.connectionFailed("Failed to remove service: ${e.message}", e).asErrorResult()
        }
    }

    override suspend fun notifyCharacteristicChanged(
        service: HasUuidId,
        characteristic: HasUuidId,
        value: ByteArray
    ): IdkResult<Unit, CharacteristicWriteError> {
        log.debug("Notifying characteristic changed: ${characteristic.id}")
        
        return try {
            val cbChar = characteristicsMap[characteristic.id]
                ?: return BleErrors.writeCharacteristicFailed("Characteristic not found").asErrorResult()

            val wasSent = peripheralManager?.updateValue(
                value = value.toNSData(),
                forCharacteristic = cbChar,
                onSubscribedCentrals = null
            ) ?: false

            if (!wasSent) {
                // Wait for ready to update
                suspendCancellableCoroutine { continuation ->
                    writeReadyContinuation = continuation
                }
                
                // Try again
                peripheralManager?.updateValue(
                    value = value.toNSData(),
                    forCharacteristic = cbChar,
                    onSubscribedCentrals = null
                )
            }

            Unit.asOkResult()
        } catch (e: Exception) {
            log.error("Failed to notify characteristic", exception = e)
            BleErrors.writeCharacteristicFailed("Failed to notify characteristic: ${e.message}", e).asErrorResult()
        }
    }

    override suspend fun readCharacteristic(
        service: HasUuidId,
        characteristic: HasUuidId
    ): IdkResult<GattCharacteristic, BleError> {
        // Peripheral mode typically doesn't read its own characteristics
        return BleErrors.readCharacteristicFailed("Read not supported in peripheral mode").asErrorResult()
    }

    override suspend fun awaitConnection(): IdkResult<Unit, BleError> {
        log.info("Waiting for connection from central device")
        return try {
            suspendCancellableCoroutine { continuation ->
                connectionContinuation = continuation
            }
            Unit.asOkResult()
        } catch (e: Exception) {
            log.error("Failed waiting for connection", exception = e)
            BleErrors.connectionFailed("Failed waiting for connection: ${e.message}", e).asErrorResult()
        }
    }

    override suspend fun disconnect(): IdkResult<Unit, BleError> {
        log.info("Disconnecting from central device")
        return try {
            connectedCentral = null
            Unit.asOkResult()
        } catch (e: Exception) {
            log.error("Failed to disconnect", exception = e)
            BleErrors.connectionFailed("Failed to disconnect: ${e.message}", e).asErrorResult()
        }
    }

    override fun close() {
        log.info("Closing IosBlePlatformPeripheral")
        peripheralManager?.stopAdvertising()
        peripheralManager?.removeAllServices()
        peripheralManager?.delegate = null
        peripheralManager = null
        connectedCentral = null
        characteristicsMap.clear()

        // Close the event queue to stop the processor
        bleEventQueue.close()

        // Clean up all session tracking
        val sessionCount = deviceSessionIds.size
        if (sessionCount > 0) {
            log.info("Cleaning up $sessionCount session(s) on close")
        }
        deviceSessionIds.clear()
        deviceLastActivityTime.clear()

        super.close()
    }

    private fun mapProperties(properties: Set<GattProperty>): ULong {
        var result: ULong = 0u
        properties.forEach { prop ->
            result = result or when (prop) {
                GattProperty.READ -> CBCharacteristicPropertyRead
                GattProperty.WRITE -> CBCharacteristicPropertyWrite
                GattProperty.WRITE_WITHOUT_RESPONSE -> platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
                GattProperty.NOTIFY -> CBCharacteristicPropertyNotify
                GattProperty.INDICATE -> CBCharacteristicPropertyNotify // iOS uses same for both
            }
        }
        return result
    }

    @ContributesTo(AppScope::class)
    interface Component {
        val blePlatformPeripheral: BlePlatformPeripheral
    }
}
