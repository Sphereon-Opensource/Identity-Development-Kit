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

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import android.annotation.SuppressLint
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.BleErrors
import com.sphereon.data.link.ble.CharacteristicWriteError
import com.sphereon.data.link.ble.client.BleEvent
import com.sphereon.data.link.ble.model.GattCharacteristic
import com.sphereon.data.link.ble.model.GattPermission
import com.sphereon.data.link.ble.model.GattProperty
import com.sphereon.data.link.ble.model.GattService
import com.sphereon.data.link.ble.model.HasUuidId
import com.sphereon.data.link.ble.model.toUuidId
import com.sphereon.di.app.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * Returns the last 8 characters of a UUID string for compact logging
 */
private fun Uuid.toShortId(): String = this.toString().takeLast(8)

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<BlePlatformPeripheral>())
class AndroidBlePlatformPeripheral(
    val app: App,
    logManager: AppLogManager,
) : AbstractBlePlatformPeripheral(logManager, CoroutineScope(Dispatchers.IO + SupervisorJob())) {

    private val context = app.application as Context
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var connectedDevice: BluetoothDevice? = null
    private var isAdvertising: Boolean = false

    // For Android 8.0+ (API 26+) AdvertisingSet API with enhanced privacy options
    private var currentAdvertisingSet: AdvertisingSet? = null

    // Session ID tracking per connected device
    private val deviceSessionIds = mutableMapOf<String, Uuid>()
    private val deviceLastActivityTime = mutableMapOf<String, Long>()

    // Track whether GATT services are ready (added to server)
    // This prevents race condition where bonded iOS devices connect before services are registered
    private var servicesReady: Boolean = false

    // Store pending connection from bonded device that connected before services were ready
    private var pendingConnectionDevice: BluetoothDevice? = null
    private var pendingConnectionStatus: Int = 0

    // Cleanup stale sessions every 5 minutes (devices that haven't sent data in 10 minutes)
    private val SESSION_CLEANUP_INTERVAL_MS = 5 * 60 * 1000L
    private val SESSION_TIMEOUT_MS = 10 * 60 * 1000L

    // Mutex to prevent concurrent advertising attempts
    // This prevents race condition where two callers both pass the isAdvertising check
    // before either one completes, causing "callback instance already associated" error
    private val advertisingMutex = Mutex()

    // Track whether advertising is in progress (set BEFORE calling Android API)
    // This is different from isAdvertising which is set in the callback AFTER advertising starts
    private var advertisingInProgress: Boolean = false

    // Continuations for async operations
    private var serviceAddedContinuation: kotlinx.coroutines.CancellableContinuation<Boolean>? = null
    private var advertisingContinuation: kotlinx.coroutines.CancellableContinuation<Boolean>? = null
    private var characteristicWriteContinuation: kotlinx.coroutines.CancellableContinuation<Boolean>? = null
    private var connectionContinuation: kotlinx.coroutines.CancellableContinuation<Unit>? = null

    // FIFO event queue for BLE events - ensures strict ordering regardless of callback thread scheduling.
    // This is critical because Android BLE callbacks can arrive in rapid succession on different threads
    // (Binder threads), and launching separate coroutines for each would cause race conditions
    // where events get processed out of order, corrupting multi-chunk message assembly.
    // Note: This is mutable because the singleton may be reused after close() is called.
    @Volatile
    private var bleEventQueue: Channel<BleEvent> = Channel(capacity = Channel.UNLIMITED)

    @Volatile
    private var eventProcessorRunning = false
    private val eventQueueLock = Any()

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
     * Starts the BLE event processor coroutine if not already running.
     * This is called automatically from init and ensured before each enqueue.
     * Since this class is a singleton that may be reused after close(), we need
     * to be able to restart the processor.
     */
    private fun startEventProcessor() {
        synchronized(eventQueueLock) {
            if (eventProcessorRunning) {
                return
            }
            eventProcessorRunning = true
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
                synchronized(eventQueueLock) {
                    eventProcessorRunning = false
                }
                log.debug("BLE event processor coroutine completed")
            }
        }
    }

    /**
     * Ensures the event queue and processor are ready for use.
     * Recreates them if they were closed by a previous close() call.
     */
    private fun ensureEventQueueReady() {
        synchronized(eventQueueLock) {
            if (bleEventQueue.isClosedForSend) {
                log.debug("Recreating BLE event queue (was closed)")
                bleEventQueue = Channel(capacity = Channel.UNLIMITED)
            }
            if (!eventProcessorRunning) {
                // Release lock before starting processor to avoid deadlock
            }
        }
        // Start processor outside of lock
        if (!eventProcessorRunning) {
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

    /**
     * Get or create a session ID for a device.
     * Updates the last activity time for the device.
     */
    private fun getOrCreateSessionId(deviceAddress: String): Uuid {
        deviceLastActivityTime[deviceAddress] = System.currentTimeMillis()
        return deviceSessionIds.getOrPut(deviceAddress) {
            Uuid.random().also {
                log.info("Created new session ID ${it.toShortId()} for device $deviceAddress")
            }
        }
    }

    /**
     * Remove session ID for a device when it disconnects.
     * Returns the session ID that was removed, or Uuid.NIL if none existed.
     */
    private fun removeSessionId(deviceAddress: String): Uuid {
        val sessionId = deviceSessionIds.remove(deviceAddress)
        deviceLastActivityTime.remove(deviceAddress)
        if (sessionId != null) {
            log.info("Removed session ID ${sessionId.toShortId()} for disconnected device $deviceAddress")
        }
        return sessionId ?: Uuid.NIL
    }

    /**
     * Get existing session ID for a device, or Uuid.NIL if not connected.
     * Does NOT update activity time.
     */
    private fun getSessionId(deviceAddress: String): Uuid {
        return deviceSessionIds[deviceAddress] ?: Uuid.NIL
    }

    /**
     * Clean up sessions that have been inactive for too long.
     * This prevents memory leaks if disconnect events are missed.
     */
    private fun cleanupStaleSessions() {
        val now = System.currentTimeMillis()
        val staleDevices = deviceLastActivityTime.filter { (_, lastActivity) ->
            now - lastActivity > SESSION_TIMEOUT_MS
        }.keys.toList()

        if (staleDevices.isNotEmpty()) {
            log.warn("Cleaning up ${staleDevices.size} stale session(s): ${staleDevices.joinToString(", ")}")
            staleDevices.forEach { deviceAddress ->
                val sessionId = removeSessionId(deviceAddress)
                log.info("Cleaned up stale session ${sessionId.toShortId()} for device $deviceAddress")
            }
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            log.debug("onServiceAdded: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Mark services as ready
                // Note: Early connections (before services ready) are now disconnected immediately
                // in onConnectionStateChange. This forces readers to reconnect fresh after services
                // are registered, ensuring they see our GATT service during service discovery.
                servicesReady = true
                log.info("GATT service added successfully, servicesReady=true - waiting for advertising to start before accepting connections")

                serviceAddedContinuation?.resume(true)
            } else {
                serviceAddedContinuation?.resumeWithException(
                    IllegalStateException("Failed to add service: status=$status")
                )
            }
            serviceAddedContinuation = null
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val servicesCount = gattServer?.services?.size ?: 0

                    // Log connection details for debugging
                    log.info(
                        "*** CONNECTION RECEIVED: device=${device.address}, " +
                                "servicesReady=$servicesReady, isAdvertising=$isAdvertising, " +
                                "services_added=$servicesCount ***"
                    )

                    // CRITICAL: If we're not advertising yet, this connection is STALE.
                    // 
                    // Reasoning:
                    // - Each engagement uses a unique service UUID
                    // - Reader can only learn the UUID via QR code or NFC handover
                    // - QR/NFC sharing happens AFTER advertising starts
                    // - Therefore, any connection before advertising CANNOT be from a reader
                    //   that knows our current service UUID - it must be from a previous session
                    //
                    // We must disconnect stale connections because:
                    // - They will do service discovery and cache "no services" (since our new
                    //   service isn't added yet)
                    // - Even after we add services, they won't re-discover
                    // - The legitimate reader will connect AFTER receiving the UUID via QR/NFC
                    if (!isAdvertising) {
                        log.warn(
                            "*** STALE CONNECTION: device=${device.address} connected before advertising started. " +
                                    "This is from a previous session (reader can't know our UUID yet). Disconnecting. ***"
                        )
                        try {
                            gattServer?.cancelConnection(device)
                            log.info("Stale connection from ${device.address} disconnected")
                        } catch (e: Exception) {
                            log.warn("Failed to disconnect stale connection: ${e.message}")
                        }
                        return
                    }

                    // Advertising is active, so this could be a legitimate reader that received
                    // our service UUID via QR or NFC. Accept the connection.
                    processConnection(device, status)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    val sessionId = removeSessionId(device.address)
                    log.debug("onConnectionStateChange: device=${device.address} DISCONNECTED, session=${sessionId.toShortId()}, status=$status")
                    if (connectedDevice?.address == device.address) {
                        connectedDevice = null
                    }
                    // Also clear pending connection if it's this device
                    if (pendingConnectionDevice?.address == device.address) {
                        log.debug("Clearing pending connection for disconnected device ${device.address}")
                        pendingConnectionDevice = null
                    }

                    // Emit disconnection event using FIFO queue
                    enqueueEvent(
                        BleEvent.ConnectionStateChanged(
                            requestId = sessionId,
                            deviceAddress = device.address,
                            newState = newState,
                            status = status
                        )
                    )
                }

                else -> {
                    // For other states (CONNECTING, DISCONNECTING), use existing session ID if available
                    val sessionId = getSessionId(device.address)
                    log.debug("onConnectionStateChange: device=${device.address}, state=$newState, session=${sessionId.toShortId()}, status=$status")

                    enqueueEvent(
                        BleEvent.ConnectionStateChanged(
                            requestId = sessionId,
                            deviceAddress = device.address,
                            newState = newState,
                            status = status
                        )
                    )
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            try {
                log.debug("onCharacteristicReadRequest: device=${device.address}, characteristic=${characteristic.uuid}")
                // Platform implementations should handle read requests
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, byteArrayOf())
            } catch (e: Exception) {
                log.warn("Error handling characteristic read request: ${e.message}")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            try {
                val sessionId = getOrCreateSessionId(device.address)
                log.debug("onCharacteristicWriteRequest: device=${device.address}, session=${sessionId.toShortId()}, characteristic=${characteristic.uuid}, value size=${value.size}")

                if (responseNeeded) {
                    try {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    } catch (e: Exception) {
                        log.warn("Failed to send GATT response: ${e.message}")
                    }
                }

                // Build and enqueue characteristic changed event for FIFO processing.
                // Using enqueueEvent instead of scope.launch ensures strict ordering when
                // multiple BLE callbacks arrive simultaneously (same millisecond), which is
                // critical for correct multi-chunk message assembly.
                try {
                    val serviceUuid = gattServer?.services?.find { service ->
                        service.characteristics.any { it.uuid == characteristic.uuid }
                    }?.uuid

                    if (serviceUuid != null) {
                        enqueueEvent(
                            BleEvent.CharacteristicChanged(
                                requestId = sessionId,
                                deviceAddress = device.address,
                                service = Uuid.parse(serviceUuid.toString()).toUuidId(),
                                characteristic = Uuid.parse(characteristic.uuid.toString()).toUuidId(),
                                value = value
                            )
                        )
                    }
                } catch (e: Exception) {
                    log.warn("Error building characteristic changed event: ${e.message}")
                }
            } catch (e: Exception) {
                log.warn("Error handling characteristic write request: ${e.message}")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            try {
                val sessionId = getOrCreateSessionId(device.address)
                val isEnablingNotifications = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                val isEnablingIndications = value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                val isDisabling = value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)

                log.debug(
                    "onDescriptorWriteRequest: device=${device.address}, session=${sessionId.toShortId()}, " +
                            "descriptor=${descriptor.uuid}, characteristic=${descriptor.characteristic.uuid}, " +
                            "notifications=${isEnablingNotifications}, indications=${isEnablingIndications}, disabling=${isDisabling}"
                )

                if (responseNeeded) {
                    try {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    } catch (e: Exception) {
                        log.warn("Failed to send GATT response: ${e.message}")
                    }
                }

                // Emit descriptor write event using FIFO queue
                try {
                    val serviceUuid = gattServer?.services?.find { service ->
                        service.characteristics.any { it.uuid == descriptor.characteristic.uuid }
                    }?.uuid

                    if (serviceUuid != null) {
                        val gattDescriptor = com.sphereon.data.link.ble.model.GattDescriptor(
                            id = Uuid.parse(descriptor.uuid.toString()),
                            permissions = emptySet(), // Permissions not available from Android descriptor
                            characteristic = null // Circular reference avoided
                        )

                        enqueueEvent(
                            BleEvent.DescriptorWrite(
                                requestId = sessionId,
                                deviceAddress = device.address,
                                service = Uuid.parse(serviceUuid.toString()).toUuidId(),
                                characteristic = Uuid.parse(descriptor.characteristic.uuid.toString()).toUuidId(),
                                descriptor = gattDescriptor,
                                status = BluetoothGatt.GATT_SUCCESS
                            )
                        )
                    }
                } catch (e: Exception) {
                    log.warn("Error building descriptor write event: ${e.message}")
                }
            } catch (e: Exception) {
                log.warn("Error handling descriptor write request: ${e.message}")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            log.debug("onNotificationSent: device=${device.address}, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristicWriteContinuation?.resume(true)
            } else {
                characteristicWriteContinuation?.resumeWithException(
                    IllegalStateException("Notification failed: status=$status")
                )
            }
            characteristicWriteContinuation = null
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            val sessionId = getOrCreateSessionId(device.address)
            log.debug("onMtuChanged: device=${device.address}, session=${sessionId.toShortId()}, mtu=$mtu")
            // Emit MTU changed event using FIFO queue
            enqueueEvent(
                BleEvent.MtuChanged(
                    requestId = sessionId,
                    deviceAddress = device.address,
                    mtu = mtu,
                    status = BluetoothGatt.GATT_SUCCESS
                )
            )
        }
    }

    /**
     * Process a connection and emit the connection event.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun processConnection(device: BluetoothDevice, status: Int) {
        val sessionId = getOrCreateSessionId(device.address)
        val servicesCount = gattServer?.services?.size ?: 0
        log.info(
            "*** READER CONNECTED: device=${device.address}, session=${sessionId.toShortId()}, " +
                    "status=$status, services_added=$servicesCount, servicesReady=$servicesReady, advertising=$isAdvertising ***"
        )
        connectedDevice = device
        connectionContinuation?.resume(Unit)
        connectionContinuation = null

        // Emit connection event using FIFO queue
        enqueueEvent(
            BleEvent.ConnectionStateChanged(
                requestId = sessionId,
                deviceAddress = device.address,
                newState = BluetoothProfile.STATE_CONNECTED,
                status = status
            )
        )
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            log.info("Advertising started successfully (legacy API) - ready to accept connections")
            isAdvertising = true
            advertisingContinuation?.resume(true)
            advertisingContinuation = null
        }

        override fun onStartFailure(errorCode: Int) {
            log.error("Advertising failed (legacy API): errorCode=$errorCode")
            isAdvertising = false
            advertisingContinuation?.resumeWithException(
                IllegalStateException("Advertising failed: errorCode=$errorCode")
            )
            advertisingContinuation = null
        }
    }

    /**
     * Callback for AdvertisingSet API (Android 8.0+).
     * Used on Android 15+ for non-resolvable random address advertising.
     */
    private val advertisingSetCallback = object : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
            if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                log.info("Advertising started successfully with non-resolvable address (AdvertisingSet API), txPower=$txPower - ready to accept connections")
                currentAdvertisingSet = advertisingSet
                isAdvertising = true
                advertisingContinuation?.resume(true)
            } else {
                log.error("Advertising failed (AdvertisingSet API): status=$status")
                currentAdvertisingSet = null
                isAdvertising = false
                advertisingContinuation?.resumeWithException(
                    IllegalStateException("Advertising failed with AdvertisingSet API: status=$status")
                )
            }
            advertisingContinuation = null
        }

        override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
            log.info("Advertising stopped (AdvertisingSet API)")
            currentAdvertisingSet = null
            isAdvertising = false
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    override suspend fun startAdvertising(serviceUuid: Uuid): IdkResult<Unit, BleError> {
        log.info("Starting advertising for service: $serviceUuid")

        // Use mutex to prevent concurrent advertising attempts
        // This prevents the "callback instance already associated with advertising" error
        // that occurs when two callers both pass the isAdvertising check before either completes
        return advertisingMutex.withLock {
            // Double-check if advertising is already in progress or running
            if (advertisingInProgress) {
                log.warn("Advertising already in progress (waiting for callback), skipping duplicate call")
                return@withLock Unit.asOkResult()
            }
            if (isAdvertising) {
                log.warn("Advertising already running, skipping duplicate call")
                return@withLock Unit.asOkResult()
            }

            // Mark as in progress BEFORE calling Android API
            advertisingInProgress = true

            try {
                // DO NOT disconnect here - if a reader connected via bonding during addService(),
                // let it stay connected. It should discover services after connection.
                // Disconnecting here causes deadlock because awaitConnection() will wait forever
                // for a connection that already happened.

                // Check if Bluetooth adapter is available and enabled
                val adapter = bluetoothManager.adapter
                if (adapter == null || !adapter.isEnabled) {
                    val reason = if (adapter == null) "Bluetooth adapter not available" else "Bluetooth is disabled"
                    log.error(reason)
                    return@withLock BleErrors.connectionFailed(reason).asErrorResult()
                }

                advertiser = adapter.bluetoothLeAdvertiser
                    ?: return@withLock BleErrors.connectionFailed("Bluetooth LE Advertiser not available").asErrorResult()

                val data = AdvertiseData.Builder()
                    .setIncludeTxPowerLevel(false)
                    .addServiceUuid(ParcelUuid(serviceUuid.toJavaUuid()))
                    .build()

                // On Android 15+ (API 35+), use AdvertisingSet API with non-resolvable random address
                // This prevents bonded devices from recognizing us by resolving our random address
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    log.info("Using AdvertisingSet API with non-resolvable random address (Android 15+)")
                    startAdvertisingWithNonResolvableAddress(advertiser!!, data)
                } else {
                    // Fall back to legacy API on older Android versions
                    log.info("Using legacy advertising API (Android < 15)")
                    startAdvertisingLegacy(advertiser!!, data)
                }

                Unit.asOkResult()
            } catch (e: SecurityException) {
                log.error("Missing permissions to start advertising: ${e.message}")
                isAdvertising = false
                BleErrors.connectionFailed("Missing permissions to start advertising: ${e.message}", e).asErrorResult()
            } catch (e: Exception) {
                log.error("Failed to start advertising", exception = e)
                isAdvertising = false
                BleErrors.connectionFailed("Failed to start advertising: ${e.message}", e).asErrorResult()
            } finally {
                // Clear in-progress flag after the advertising API call completes (success or failure)
                // Note: isAdvertising is set to true in the callback when advertising actually starts
                advertisingInProgress = false
            }
        }
    }

    /**
     * Start advertising using the legacy AdvertiseSettings API.
     * Used on Android versions before 15.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private suspend fun startAdvertisingLegacy(advertiser: BluetoothLeAdvertiser, data: AdvertiseData) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        suspendCancellableCoroutine { continuation ->
            advertisingContinuation = continuation
            advertiser.startAdvertising(settings, data, advertiseCallback)
        }
    }

    /**
     * Start advertising using the AdvertisingSet API with non-resolvable random address.
     * This prevents bonded devices from recognizing us, ensuring each engagement starts fresh.
     * 
     * Only available on Android 15+ (API 35+).
     * 
     * ADDRESS_TYPE_RANDOM_NON_RESOLVABLE (value 3) means bonded devices cannot resolve our address
     * using the Identity Resolving Key (IRK) they stored during previous bonding.
     * 
     * We use reflection to access this API since it's very new and may not be available
     * in all compile SDK versions.
     */
    @SuppressLint("MissingPermission")
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun startAdvertisingWithNonResolvableAddress(advertiser: BluetoothLeAdvertiser, data: AdvertiseData) {
        val parametersBuilder = AdvertisingSetParameters.Builder()
            .setLegacyMode(true) // Use legacy advertising for broader compatibility
            .setConnectable(true)
            .setScannable(true)
            .setInterval(AdvertisingSetParameters.INTERVAL_LOW) // Low latency
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)

        // Try to set non-resolvable random address type using reflection
        // ADDRESS_TYPE_RANDOM_NON_RESOLVABLE = 3 (available in API 35+)
        val nonResolvableAddressSet = trySetNonResolvableAddressType(parametersBuilder)
        if (nonResolvableAddressSet) {
            log.info("Successfully configured non-resolvable random address for advertising")
        } else {
            log.warn("Could not set non-resolvable address type, using default (bonded devices may still reconnect)")
        }

        val parameters = parametersBuilder.build()

        suspendCancellableCoroutine { continuation ->
            advertisingContinuation = continuation
            advertiser.startAdvertisingSet(
                parameters,
                data,
                null, // No scan response data
                null, // No periodic advertising parameters
                null, // No periodic advertising data
                advertisingSetCallback
            )
        }
    }

    /**
     * Try to set ADDRESS_TYPE_RANDOM_NON_RESOLVABLE using reflection.
     * This is necessary because the API may not be available in all SDK versions.
     * 
     * @return true if successfully set, false otherwise
     */
    private fun trySetNonResolvableAddressType(builder: AdvertisingSetParameters.Builder): Boolean {
        return try {
            // ADDRESS_TYPE_RANDOM_NON_RESOLVABLE = 3
            val setOwnAddressTypeMethod = AdvertisingSetParameters.Builder::class.java.getMethod(
                "setOwnAddressType",
                Int::class.javaPrimitiveType
            )
            setOwnAddressTypeMethod.invoke(builder, 3) // 3 = ADDRESS_TYPE_RANDOM_NON_RESOLVABLE
            log.debug("setOwnAddressType(3) called successfully via reflection")
            true
        } catch (e: NoSuchMethodException) {
            log.debug("setOwnAddressType method not available on this Android version")
            false
        } catch (e: Exception) {
            log.warn("Failed to set non-resolvable address type: ${e.message}")
            false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override suspend fun stopAdvertising(): IdkResult<Unit, BleError> {
        log.info("Stopping advertising")
        // Reset in-progress flag when stopping
        advertisingInProgress = false
        return try {
            if (isAdvertising) {
                // Check if Bluetooth adapter is available and enabled before accessing advertiser
                val adapter = bluetoothManager.adapter
                if (adapter != null && adapter.isEnabled) {
                    // Get the advertiser reference if we don't have it
                    if (advertiser == null) {
                        advertiser = adapter.bluetoothLeAdvertiser
                    }

                    // Stop AdvertisingSet if active (Android 15+ with non-resolvable address)
                    currentAdvertisingSet?.let { advertisingSet ->
                        try {
                            advertiser?.stopAdvertisingSet(advertisingSetCallback)
                            log.debug("AdvertisingSet stopped successfully")
                        } catch (e: Exception) {
                            log.warn("Failed to stop AdvertisingSet: ${e.message}")
                        }
                        currentAdvertisingSet = null
                    }

                    // Stop legacy advertising if no AdvertisingSet was active
                    if (currentAdvertisingSet == null) {
                        advertiser?.let { adv ->
                            try {
                                adv.stopAdvertising(advertiseCallback)
                                log.debug("Legacy advertising stopped successfully")
                            } catch (e: Exception) {
                                log.warn("Failed to stop legacy advertising: ${e.message}")
                            }
                        }
                    }
                } else {
                    log.warn("Bluetooth adapter is null or disabled, skipping stop advertising")
                }
                isAdvertising = false
                currentAdvertisingSet = null
            } else {
                log.debug("Advertising already stopped, nothing to do")
            }
            advertiser = null
            Unit.asOkResult()
        } catch (e: SecurityException) {
            log.warn("Missing permissions to stop advertising: ${e.message}")
            isAdvertising = false
            currentAdvertisingSet = null
            advertiser = null
            Unit.asOkResult() // Return success since advertising will be stopped anyway due to missing permissions
        } catch (e: Exception) {
            log.error("Failed to stop advertising", exception = e)
            isAdvertising = false
            currentAdvertisingSet = null
            advertiser = null
            BleErrors.connectionFailed("Failed to stop advertising: ${e.message}", e).asErrorResult()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun addService(service: GattService): IdkResult<GattService, BleError> {
        log.info("Adding GATT service: ${service.id}")
        
        return try {
            // Reset ALL state for new engagement
            // CRITICAL: Reset isAdvertising to false so stale connections are rejected
            // until advertising actually starts for this new engagement.
            // This is important because AndroidBlePlatformPeripheral is a singleton and
            // isAdvertising could be true from a previous engagement.
            isAdvertising = false
            servicesReady = false
            pendingConnectionDevice = null
            pendingConnectionStatus = 0
            log.debug("Reset isAdvertising=false, servicesReady=false for new engagement")

            // Disconnect any currently connected devices BEFORE opening GATT server
            connectedDevice?.let { device ->
                log.warn("WARNING: Device ${device.address} is ALREADY connected before GATT server creation!")
                log.info("Force disconnecting device ${device.address} before creating GATT server")
                try {
                    gattServer?.cancelConnection(device)
                    connectedDevice = null
                    kotlinx.coroutines.delay(100)
                    log.info("Device disconnected successfully")
                } catch (e: Exception) {
                    log.error("Failed to force disconnect device: ${e.message}")
                }
            }

            // Close and recreate GATT server for each new service/engagement
            // This prevents readers from staying connected across engagements
            if (gattServer != null) {
                log.info("Closing existing GATT server before creating new one to prevent stale connections")

                // Close the old GATT server completely
                try {
                    gattServer?.close()
                    log.info("Old GATT server closed successfully")
                } catch (e: Exception) {
                    log.warn("Failed to close old GATT server: ${e.message}")
                }

                gattServer = null

                // Give Android time to fully release the GATT server and clear bonding cache
                kotlinx.coroutines.delay(300)
                log.info("Delay after closing old GATT server completed")
            }

            // Always create a fresh GATT server for each engagement
            log.info("Opening new GATT server")
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            log.info("GATT server opened successfully, proceeding to add service")

            val androidService = BluetoothGattService(
                service.id.toJavaUuid(),
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            // Add characteristics to the service
            service.characteristics.forEach { char ->
                val properties = mapProperties(char.properties)
                // Use READ and WRITE permissions as default
                val permissions = BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE

                val androidChar = BluetoothGattCharacteristic(
                    char.id.toJavaUuid(),
                    properties,
                    permissions
                )

                // Per ISO 18013-5, characteristics with NOTIFY property must have a CCCD descriptor
                // This is CRITICAL - without CCCD, central devices cannot enable notifications
                if (char.properties.contains(GattProperty.NOTIFY) || char.properties.contains(GattProperty.INDICATE)) {
                    val cccdUuid = java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                    val cccd = BluetoothGattDescriptor(
                        cccdUuid,
                        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                    )
                    androidChar.addDescriptor(cccd)
                    log.debug("Added CCCD descriptor to characteristic ${char.id} for notifications")
                }

                androidService.addCharacteristic(androidChar)
            }

            suspendCancellableCoroutine { continuation ->
                serviceAddedContinuation = continuation
                gattServer?.addService(androidService)
            }

            service.asOkResult()
        } catch (e: Exception) {
            log.error("Failed to add service", exception = e)
            BleErrors.connectionFailed("Failed to add service: ${e.message}", e).asErrorResult()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun removeService(service: HasUuidId): IdkResult<Unit, BleError> {
        log.info("Removing GATT service: ${service.id}")
        return try {
            val androidService = gattServer?.services?.find { it.uuid == service.id.toJavaUuid() }
            if (androidService != null) {
                gattServer?.removeService(androidService)
            }
            Unit.asOkResult()
        } catch (e: Exception) {
            log.error("Failed to remove service", exception = e)
            BleErrors.connectionFailed("Failed to remove service: ${e.message}", e).asErrorResult()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun notifyCharacteristicChanged(
        service: HasUuidId,
        characteristic: HasUuidId,
        value: ByteArray
    ): IdkResult<Unit, CharacteristicWriteError> {
        log.debug("Notifying characteristic changed: ${characteristic.id}")
        
        return try {
            val androidService = gattServer?.services?.find { it.uuid == service.id.toJavaUuid() }
                ?: return BleErrors.writeCharacteristicFailed("Service not found").asErrorResult()
            
            val androidChar = androidService.characteristics.find { it.uuid == characteristic.id.toJavaUuid() }
                ?: return BleErrors.writeCharacteristicFailed("Characteristic not found").asErrorResult()

            val device = connectedDevice
                ?: return BleErrors.writeCharacteristicFailed("No connected device").asErrorResult()

            suspendCancellableCoroutine { continuation ->
                characteristicWriteContinuation = continuation
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    @SuppressLint("InlinedApi")
                    val result = gattServer?.notifyCharacteristicChanged(device, androidChar, false, value)
                    if (result != BluetoothStatusCodes.SUCCESS) {
                        continuation.resumeWithException(
                            IllegalStateException("Failed to notify characteristic: result=$result")
                        )
                    }
                } else {
                    @Suppress("DEPRECATION")
                    androidChar.value = value
                    @Suppress("DEPRECATION")
                    if (!gattServer!!.notifyCharacteristicChanged(device, androidChar, false)) {
                        continuation.resumeWithException(
                            IllegalStateException("Failed to notify characteristic")
                        )
                    }
                }
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
        log.info("Waiting for connection from central device (connectedDevice=${connectedDevice?.address})")
        return try {
            // Check if a device is already connected (e.g., from bonding before we started waiting)
            if (connectedDevice != null) {
                log.info("Device already connected: ${connectedDevice?.address}, skipping wait")
                return Unit.asOkResult()
            }

            log.info("No device connected yet, waiting for connection...")
            suspendCancellableCoroutine { continuation ->
                connectionContinuation = continuation
                log.debug("Connection continuation set, will resume when device connects")
            }
            log.info("Connection established, proceeding")
            Unit.asOkResult()
        } catch (e: Exception) {
            log.error("Failed waiting for connection", exception = e)
            BleErrors.connectionFailed("Failed waiting for connection: ${e.message}", e).asErrorResult()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun disconnect(): IdkResult<Unit, BleError> {
        log.info("Disconnecting from central device")
        return try {
            connectedDevice?.let { device ->
                gattServer?.cancelConnection(device)
            }
            connectedDevice = null
            Unit.asOkResult()
        } catch (e: Exception) {
            log.error("Failed to disconnect", exception = e)
            BleErrors.connectionFailed("Failed to disconnect: ${e.message}", e).asErrorResult()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    override fun close() {
        log.info("Closing AndroidBlePlatformPeripheral")

        // Reset advertising state
        advertisingInProgress = false

        // Close the BLE event queue first to stop the event processor
        bleEventQueue.close()

        // Stop advertising safely, handling cases where Bluetooth might be disabled or unavailable
        if (isAdvertising) {
            try {
                // Check if Bluetooth adapter is available and enabled before accessing advertiser
                val adapter = bluetoothManager.adapter
                if (adapter != null && adapter.isEnabled) {
                    // Get the advertiser reference if we don't have it
                    if (advertiser == null) {
                        advertiser = adapter.bluetoothLeAdvertiser
                    }

                    // Stop AdvertisingSet if active (Android 15+ with non-resolvable address)
                    currentAdvertisingSet?.let {
                        try {
                            advertiser?.stopAdvertisingSet(advertisingSetCallback)
                            log.debug("AdvertisingSet stopped successfully during close")
                        } catch (e: Exception) {
                            log.warn("Failed to stop AdvertisingSet during close: ${e.message}")
                        }
                    }

                    // Stop legacy advertising
                    advertiser?.let { adv ->
                        try {
                            adv.stopAdvertising(advertiseCallback)
                            log.debug("Legacy advertising stopped successfully during close")
                        } catch (e: Exception) {
                            log.warn("Failed to stop legacy advertising during close: ${e.message}")
                        }
                    }
                } else {
                    log.warn("Bluetooth adapter is null or disabled during close, skipping stop advertising")
                }
            } catch (e: SecurityException) {
                log.warn("Missing permissions to stop advertising during close: ${e.message}")
            } catch (e: Exception) {
                log.error("Unexpected error stopping advertising during close: ${e.message}", exception = e)
            } finally {
                isAdvertising = false
                currentAdvertisingSet = null
            }
        }
        advertiser = null

        // Close GATT server safely
        try {
            gattServer?.close()
            log.debug("GATT server closed successfully")
        } catch (e: Exception) {
            log.error("Error closing GATT server: ${e.message}", exception = e)
        } finally {
            gattServer = null
        }

        connectedDevice = null

        // Reset connection state tracking
        servicesReady = false
        pendingConnectionDevice = null
        pendingConnectionStatus = 0

        // Clean up all session tracking
        val sessionCount = deviceSessionIds.size
        if (sessionCount > 0) {
            log.info("Cleaning up $sessionCount session(s) on close")
        }
        deviceSessionIds.clear()
        deviceLastActivityTime.clear()

        super.close()
    }

    private fun mapProperties(properties: Set<GattProperty>): Int {
        var result = 0
        properties.forEach { prop ->
            result = result or when (prop) {
                GattProperty.READ -> BluetoothGattCharacteristic.PROPERTY_READ
                GattProperty.WRITE -> BluetoothGattCharacteristic.PROPERTY_WRITE
                GattProperty.WRITE_WITHOUT_RESPONSE -> BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                GattProperty.NOTIFY -> BluetoothGattCharacteristic.PROPERTY_NOTIFY
                GattProperty.INDICATE -> BluetoothGattCharacteristic.PROPERTY_INDICATE
            }
        }
        return result
    }

    private fun mapPermissions(permissions: Set<GattPermission>): Int {
        var result = 0
        permissions.forEach { perm ->
            result = result or when (perm) {
                GattPermission.READ -> BluetoothGattCharacteristic.PERMISSION_READ
                GattPermission.READ_ENCRYPTED -> BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
                GattPermission.READ_ENCRYPTED_MITM -> BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM
                GattPermission.WRITE -> BluetoothGattCharacteristic.PERMISSION_WRITE
                GattPermission.WRITE_ENCRYPTED -> BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
                GattPermission.WRITE_ENCRYPTED_MITM -> BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM
            }
        }
        return result
    }

    @ContributesTo(AppScope::class)
    interface Component {
        val blePlatformPeripheral: BlePlatformPeripheral
    }
}
