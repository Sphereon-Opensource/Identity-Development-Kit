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

package com.sphereon.data.link.ble.client

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.TRANSPORT_LE
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_FAILURE
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.BleErrors
import com.sphereon.data.link.ble.CharacteristicReadError
import com.sphereon.data.link.ble.CharacteristicWriteError
import com.sphereon.data.link.ble.ConnectionFailedError
import com.sphereon.data.link.ble.MtuChangeError
import com.sphereon.data.link.ble.NotificationFailedError
import com.sphereon.data.link.ble.ScanError
import com.sphereon.data.link.ble.ServiceDiscoveryError
import com.sphereon.data.link.ble.client.cmd.ScanDevicesArgs
import com.sphereon.data.link.ble.filter.Filter
import com.sphereon.data.link.ble.filter.FilterPredicate
import com.sphereon.data.link.ble.model.BleDevice
import com.sphereon.data.link.ble.model.CharacteristicWriteMode
import com.sphereon.data.link.ble.model.GattCharacteristic
import com.sphereon.data.link.ble.model.GattDescriptor
import com.sphereon.data.link.ble.model.GattPermission
import com.sphereon.data.link.ble.model.GattProperty
import com.sphereon.data.link.ble.model.GattService
import com.sphereon.data.link.ble.model.HasUuidId
import com.sphereon.data.link.ble.model.IHasAddress
import com.sphereon.data.link.ble.model.toUuidId
import com.sphereon.di.app.App
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

val sureRequiredBlePermissions =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION, // optional, older Androids
        )
    } else {
        TODO("VERSION.SDK_INT < S")
    }

/**
 * Returns the last 8 characters of a UUID string for compact logging
 */
private fun Uuid.toShortId(): String = this.toString().takeLast(8)

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidBlePlatformClient(
    val app: App,
    val logManager: AppLogManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : BlePlatformClient {
    private val _externalBleEvents = MutableSharedFlow<BleEvent>(extraBufferCapacity = Int.MAX_VALUE)
    private val mutableInternalBleEvents = MutableSharedFlow<BleEvent>(extraBufferCapacity = Int.MAX_VALUE)
    val externalBleEvents: SharedFlow<BleEvent> = _externalBleEvents.shareIn(scope, SharingStarted.Eagerly, replay = 0)
    private val internalBleEvents: SharedFlow<BleEvent> = mutableInternalBleEvents.shareIn(scope, SharingStarted.Eagerly, replay = 0)

    private val context: Context = app.application as Context

    val log = logManager.withTag("AndroidBlePlatformClient")
    private val adapter: BluetoothAdapter by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private val scanner: BluetoothLeScanner by lazy { adapter.bluetoothLeScanner }
    private var scanResults = mutableMapOf<String, ScanResult>()
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDevice: BluetoothDevice? = null
    private var suppressDisconnectEvents = false

    // Track the current scan callback so we can stop it if a new scan starts
    private var currentScanCallback: ScanCallback? = null

    // Single requestId for the current scan session, shared by all operations
    private var scanRequestId = Uuid.random()

    // Short ID derived from scanRequestId for compact logging
    private val shortId: String
        get() = scanRequestId.toShortId()

    val devices = mutableMapOf<String, BleDevice>()

    // Single mutex to serialize ALL GATT operations (read, write, descriptor ops, etc.)
    // The Android BLE stack can only handle one operation at a time
    private val gattOperationMutex = Mutex()

    override val bleEvents: SharedFlow<BleEvent> get() = externalBleEvents

    init {
        // We do not actively listen/filter in one of the above methods, as these events come in from remote. So let's register here and then dispatch to external
        internalBleEvents.filter { it is BleEvent.CharacteristicChanged }.onEach { externalDispatch(it) }.launchIn(scope = scope)

        // Also forward remote disconnect events to external listeners
        // Per ISO 18013-5, the reader should initiate the disconnect after receiving the DeviceResponse.
        // When the remote side disconnects, we need to notify external listeners so they can update their state.
        // The connect() method only listens for the first ConnectionStateChanged, so subsequent disconnects
        // from the remote side would be lost without this forwarding.
        internalBleEvents
            .filter { event ->
                event is BleEvent.ConnectionStateChanged && event.newState == 0 // STATE_DISCONNECTED
            }.onEach { event ->
                log.info("Remote disconnect detected, forwarding ConnectionStateChanged event to external listeners")
                externalDispatch(event)
            }.launchIn(scope = scope)
    }

    @OptIn(ExperimentalUuidApi::class)
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override suspend fun scan(args: ScanDevicesArgs): IdkResult<List<BleDevice>, ScanError> =
        suspendCancellableCoroutine { cont ->
            // Set the scan request ID for this session - all subsequent operations will use the same ID
            scanRequestId = args.requestId ?: Uuid.random()
            val requestId = scanRequestId

            log.info("[id: $shortId] scan() start: $args")
            log.info("[id: $shortId] Full requestId: $scanRequestId")

            // Extract and log the UUID filter explicitly
            val serviceUuidFilters = args.filters?.filterIsInstance<Filter.Service>()
            if (!serviceUuidFilters.isNullOrEmpty()) {
                serviceUuidFilters.forEach { filter ->
                    log.info("[id: $shortId] *** BLE SCAN: Scanning for SERVICE BLE UUID: ${filter.uuid} ***")
                }
            } else {
                log.info("[id: $shortId] *** BLE SCAN: No SERVICE BLE UUID filter - scanning for all devices ***")
            }

            val timeout = args.timeout

            // Flag to track if scan has already completed
            var scanCompleted = false
            val scanLock = Any()

            val callback =
                object : ScanCallback() {
                    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
                    override fun onScanResult(
                        callbackType: Int,
                        result: ScanResult,
                    ) {
                        log.info("[id: $shortId] onScanResult() start: $callbackType, $result")
                        scanResults.put(result.device.address, result)
                        val device = result.device
                        // FIXME: Do we want to populate the name at this point?
                        val name = device.name ?: result.scanRecord?.deviceName ?: "Unknown"
                        val uuids = result.scanRecord?.serviceUuids?.map { it.uuid.toKotlinUuid() } ?: emptyList()

                        val bleDevice =
                            BleDevice(address = device.address, name = name, rssi = result.rssi, services = uuids.map { it.toUuidId() })
                        if (!devices.containsKey(bleDevice.address)) {
                            externalDispatch(BleEvent.DeviceFound(requestId = requestId, deviceAddress = device.address, device = bleDevice))
                        }
                        devices[device.address] = bleDevice

                        if (devices.size >= args.maxResults) {
                            synchronized(scanLock) {
                                if (scanCompleted) {
                                    log.warn("[id: $shortId] Scan already completed, ignoring additional results")
                                    return
                                }
                                scanCompleted = true
                            }

                            if (!cont.isActive) {
                                log.warn("[id: $shortId] Got more ble results, but we already continued: $args")
                                return
                            }
                            log.info("[id: $shortId] Found enough devices ${args.maxResults}, stopping scan")
                            try {
                                scanner.stopScan(this)
                            } catch (expected: Exception) {
                                log.warn("[id: $shortId] Failed to stop scan in onScanResult: ${expected.message}")
                            }
                            externalDispatch(
                                BleEvent.ScanResult(
                                    requestId = requestId,
                                    deviceAddress = bluetoothGatt?.device?.address ?: "<unknown>",
                                    devices = devices.values.toSet(),
                                ),
                            )
                            externalDispatch(BleEvent.ScanStopped(requestId = requestId, deviceAddress = bluetoothGatt?.device?.address ?: "<unknown>"))
                            cont.resume(Ok(devices.values.toList()))
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        synchronized(scanLock) {
                            if (scanCompleted) {
                                log.warn("[id: $shortId] Scan already completed, ignoring scan failure")
                                return
                            }
                            scanCompleted = true
                        }

                        val error = BleErrors.scanFailed("Scan failed: $errorCode")
                        externalDispatch(
                            BleEvent.Error(
                                requestId = requestId,
                                error = error,
                                deviceAddress = bluetoothGatt?.device?.address ?: "<unknown>",
                                originalOperationId = "ScanStarted",
                            ),
                        )
                        log.warn("[id: $shortId] onScanFailed: $errorCode")
                        cont.resume(error.asErrorResult())
                    }
                }

            try {
                currentScanCallback?.let { scanner.stopScan(it) }
            } catch (expected: Throwable) {
                log.debug("[id: $shortId] Stop scan failed, on new scan start, ${expected.message}")
                // Just making sure we do not have a spurious scan running in the background. Is a no op in case nothing is running.
            }

            // Clean up any existing GATT connection properly before starting scan
            val existingGatt = this.bluetoothGatt
            if (existingGatt != null) {
                log.info("[id: $shortId] Cleaning up existing GATT connection before scan")
                try {
                    @SuppressLint("MissingPermission")
                    existingGatt.disconnect()
                    @SuppressLint("MissingPermission")
                    existingGatt.close()
                } catch (expected: Exception) {
                    log.warn("[id: $shortId] Failed to cleanup existing GATT: ${expected.message}")
                }
            }

            this.connectedDevice = null
            this.bluetoothGatt = null
            scanResults.clear()
            devices.clear()
            externalDispatch(BleEvent.ScanStarted(requestId = requestId, deviceAddress = "<unknown>"))

            val filters = args.filters?.let { listOf(FilterPredicate(it)).toScanFilters() }
            if (filters == null) {
                scanner.startScan(callback)
            } else {
                val settings =
                    ScanSettings
                        .Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()
                scanner.startScan(filters.native, settings, callback)
            }

            cont.invokeOnCancellation {
                log.info("[id: $shortId] Scan cancelled")
                synchronized(scanLock) {
                    scanCompleted = true
                }
                try {
                    scanner.stopScan(callback)
                } catch (expected: Exception) {
                    log.warn("[id: $shortId] Failed to stop scan on cancellation: ${expected.message}")
                }

                externalDispatch(BleEvent.ScanStopped(requestId = requestId, deviceAddress = bluetoothGatt?.device?.address ?: "<unknown>"))
            }

            CoroutineScope(Dispatchers.Default).launch {
                delay(timeout)

                synchronized(scanLock) {
                    if (scanCompleted) {
                        log.debug("[id: $shortId] Scan already completed before timeout, skipping timeout handling")
                        return@launch
                    }
                    scanCompleted = true
                }

                try {
                    if (!cont.isActive) {
                        log.debug("[id: $shortId] Continuation already completed before timeout")
                        return@launch
                    }

                    log.info("[id: $shortId] Scan timeout $timeout reached, stopping scan")
                    scanner.stopScan(callback)
                    externalDispatch(BleEvent.ScanResult(requestId = requestId, deviceAddress = bluetoothGatt?.device?.address ?: "<unknown>", devices = devices.values.toSet()))
                    externalDispatch(BleEvent.ScanStopped(requestId = requestId, deviceAddress = bluetoothGatt?.device?.address ?: "<unknown>"))
                    cont.resume(Ok(devices.values.toList()))
                } catch (expected: Exception) {
                    if (expected.message?.contains("Already resumed") != true) {
                        log.warn("[id: $shortId] Scan timeout handling failed: ${expected.message}")
                    }
                }
            }
            currentScanCallback = callback
        }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun connect(device: IHasAddress): IdkResult<BleDevice, ConnectionFailedError> {
        val requestId = scanRequestId
        log.info("[id: $shortId] connect() start: $device")
        return try {
            // Clean up any residual connection without emitting disconnect events
            val existingGatt = bluetoothGatt
            if (existingGatt != null) {
                log.info("[id: $shortId] Cleaning up existing connection before connecting to new device")
                suppressDisconnectEvents = true
                try {
                    @SuppressLint("MissingPermission")
                    existingGatt.disconnect()
                    @SuppressLint("MissingPermission")
                    existingGatt.close()
                } catch (expected: Exception) {
                    log.warn("[id: $shortId] Failed to cleanup existing connection: ${expected.message}")
                }
                this.bluetoothGatt = null
                this.connectedDevice = null
                suppressDisconnectEvents = false
            }

            val bluetoothDevice =
                adapter.bondedDevices.find { it.address == device.address } ?: scanResults.values.find { it.device.address == device.address }?.device ?: adapter.getRemoteDevice(
                    device.address,
                ) ?: return BleErrors.connectionFailed("Device not found: ${device.address}").asErrorResult()

            this.bluetoothGatt =
                bluetoothDevice.connectGatt(
                    context,
                    false,
                    AndroidBleClientCallback(
                        requestId = requestId,
                        log = log,
                        internalBleEvents = mutableInternalBleEvents,
                    ),
                    TRANSPORT_LE,
                )

            val event =
                internalBleEvents
                    .filter { it.requestId == requestId }
                    .filter { it is BleEvent.ConnectionStateChanged || (it is BleEvent.Error && it.originalEvent is BleEvent.ConnectionStateChanged) }
                    .first()

            return if (event is BleEvent.Error && event.originalEvent is BleEvent.ConnectionStateChanged) {
                val error = BleErrors.connectionFailed("Connection failed with status ${(event.originalEvent as BleEvent.ConnectionStateChanged).status}")
                error.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = error)) }
            } else if (event is BleEvent.ConnectionStateChanged) {
                if (event.newState == BluetoothProfile.STATE_CONNECTED) {
                    log.info("[id: $shortId] connect() done, connection status: ${event.newState}, result: ${event.status}")
                    this.connectedDevice = bluetoothGatt?.device
                    bluetoothGatt!!.toBleDevice().asOkResult().also { externalDispatch(event) }
                } else {
                    val error = BleErrors.connectionFailed("Connection failed with status ${event.status}")
                    error.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = error)) }
                }
            } else {
                log.error("Unexpected event: $event")
                error("Unexpected event: $event")
            }
        } catch (expected: Exception) {
            log.error("[id: $shortId] connect() failed: ${expected.message}", exception = expected)
            val error = BleErrors.connectionFailed("Connection failed", expected)
            externalDispatch(
                BleEvent.Error(
                    requestId = requestId,
                    deviceAddress = bluetoothGatt?.device?.address ?: "<unknown>",
                    error = error,
                    originalOperationId = "Connect",
                ),
            )
            error.asErrorResult()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun disconnect(): IdkResult<Unit, BleError> {
        val requestId = scanRequestId
        log.info("[id: $shortId] disconnect() called: $bluetoothGatt")
        log.info("[id: $shortId] Full requestId: $scanRequestId")
        val gatt = bluetoothGatt
        gatt?.disconnect()
        gatt?.close()
        this.bluetoothGatt = null
        this.connectedDevice = null

        // Only emit disconnect events if not suppressed (e.g., during cleanup before connect)
        if (!suppressDisconnectEvents) {
            externalDispatch(
                BleEvent.ConnectionStateChanged(
                    requestId = requestId,
                    deviceAddress = gatt?.device?.address ?: "<unknown>",
                    newState = BluetoothProfile.STATE_DISCONNECTED,
                    status = GATT_SUCCESS,
                ),
            )
        } else {
            log.info("[id: $shortId] disconnect(): suppressing disconnect event (cleanup before connect)")
        }
        // We are settng the scanRequest Id to NIL, so if a race condition happens we can track it.
        this.scanRequestId = Uuid.NIL
        log.info("[id: $shortId] disconnect(): done")
        return Ok(Unit)
    }

    @OptIn(ExperimentalUuidApi::class)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun setMtu(mtu: Int): IdkResult<Int, MtuChangeError> {
        val requestId = scanRequestId
        log.debug("[id: $shortId] setMtu() start, mtu: $mtu")
        val gatt = getBluetoothGatt("Mtu", { BleErrors.mtuChangeFailed("Not connected") }, requestId = requestId).onFailure { return it.asErrorResult() }.value

        if (gattOperationMutex.isLocked) {
            log.warn("[id: $shortId] setMtu(): MUTEX CONTENTION DETECTED - another operation is in progress")
            log.warn("[id: $shortId] Stack trace: ${Exception().stackTraceToString()}")
        }
        return gattOperationMutex.withLock {
            try {
                if (!gatt.requestMtu(mtu)) {
                    log.error("[id: $shortId] setMtu(): GATT REQUEST MTU failed to negotiate to $mtu")
                    val event = BleEvent.MtuChanged(requestId = requestId, deviceAddress = gatt.device.address, mtu = mtu, status = GATT_FAILURE)
                    val error = BleErrors.mtuChangeFailed("Mtu failed to be set to $mtu at request call")
                    return@withLock error.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = error)) }
                }

                val event =
                    internalBleEvents
                        .filter { it.requestId == requestId }
                        .filter { it is BleEvent.MtuChanged || (it is BleEvent.Error && it.originalEvent is BleEvent.MtuChanged) }
                        .first()
                return@withLock if (event is BleEvent.Error && event.originalEvent is BleEvent.MtuChanged) {
                    val error = BleErrors.mtuChangeFailed("Mtu change to $mtu failed with status ${event.originalEvent.status}")
                    error.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = error)) }
                } else if (event is BleEvent.MtuChanged) {
                    log.info("[id: $shortId] setMtu() done, mtu: ${event.mtu}, result: ${event.status}")
                    event.mtu.asOkResult().also { externalDispatch(event) }
                } else {
                    error("Unexpected event: $event")
                }
            } catch (expected: Exception) {
                log.error("[id: $shortId] setMtu(): failed: ${expected.message}", exception = expected)
                val error = BleErrors.mtuChangeFailed("Mtu change failed", expected)
                externalDispatch(
                    BleEvent.Error(
                        requestId = requestId,
                        deviceAddress = bluetoothGatt?.device?.address ?: "<unknown>",
                        error = error,
                        originalOperationId = "MtuChange",
                    ),
                )
                return@withLock error.asErrorResult()
            } finally {
                log.debug("[id: $shortId] setMtu(): releasing gattOperationMutex")
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun discoverServices(): IdkResult<List<GattService>, ServiceDiscoveryError> {
        val requestId = scanRequestId
        log.debug("[id: $shortId] discoverServices() start: $bluetoothGatt")
        val gatt =
            getBluetoothGatt(
                "DiscoverServices",
                { BleErrors.serviceDiscoveryFailed("Not connected") },
                requestId = requestId,
            ).onFailure { return it.asErrorResult() }.value

        if (gattOperationMutex.isLocked) {
            log.warn("[id: $shortId] discoverServices(): MUTEX CONTENTION DETECTED - another operation is in progress")
            log.warn("[id: $shortId] Stack trace: ${Exception().stackTraceToString()}")
        }
        return gattOperationMutex.withLock {
            try {
                if (!gatt.discoverServices()) {
                    val event = BleEvent.ServicesDiscovered(requestId = requestId, deviceAddress = gatt.device.address, services = emptyList(), status = GATT_FAILURE)
                    val error = BleErrors.serviceDiscoveryFailed("Service discovery failed at request call")
                    return@withLock error.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = error)) }
                }

                val event =
                    internalBleEvents
                        .filter { it.requestId == requestId }
                        .filter { it is BleEvent.ServicesDiscovered || (it is BleEvent.Error && it.originalEvent is BleEvent.ServicesDiscovered) }
                        .first()
                return@withLock if (event is BleEvent.Error && event.originalEvent is BleEvent.ServicesDiscovered) {
                    log.error("[id: $shortId] discoverServices(): failed with ${event.error.message.defaultMessage}")
                    val error = BleErrors.serviceDiscoveryFailed("Service discovery failed with ${event.error.message}")
                    error.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = error)) }
                } else if (event is BleEvent.ServicesDiscovered) {
                    log.info("[id: $shortId] discoverServices(): done, services: ${event.services.joinToString(",")}")
                    return@withLock event.services.asOkResult().also { externalDispatch(event) }
                } else {
                    error("Unexpected event: $event")
                }
            } catch (expected: Exception) {
                log.error("[id: $shortId] discoverServices(): failed: ${expected.message}", exception = expected)
                val error = BleErrors.serviceDiscoveryFailed("Service discovery failed", expected)
                externalDispatch(
                    BleEvent.Error(
                        requestId = requestId,
                        deviceAddress = bluetoothGatt?.device?.address ?: "<unknown>",
                        error = error,
                        originalOperationId = "ServiceDiscovery",
                    ),
                )
                return@withLock error.asErrorResult()
            } finally {
                log.debug("[id: $shortId] discoverServices(): releasing gattOperationMutex")
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun discoverServiceCharacteristics(
        service: HasUuidId,
        characteristics: List<HasUuidId>?,
    ): IdkResult<List<GattCharacteristic>, CharacteristicReadError> {
        val requestId = scanRequestId
        log.debug("[id: $shortId] discoverServiceCharacteristics() start: $bluetoothGatt, $service, $characteristics")
        val gattService =
            getGattService(
                "DiscoverServiceCharacteristics",
                service,
                { BleErrors.readCharacteristicFailed("Service not found: ${service.id}") },
                requestId = requestId,
            ).onFailure { return it.asErrorResult() }.value

        // If no specific characteristics are requested, return all
        if (characteristics.isNullOrEmpty()) {
            return gattService.characteristics
                .map { it.toGattCharacteristic() }
                .toList()
                .asOkResult()
        }

        // Check that all requested characteristics exist
        val discoveredCharacteristics = mutableListOf<GattCharacteristic>()

        for (requestedChar in characteristics) {
            val foundChar = gattService.characteristics.find { it.uuid.toKotlinUuid() == requestedChar.id }
            if (foundChar == null) {
                log.error("[id: $shortId] discoverServiceCharacteristics(): characteristic not found: ${requestedChar.id} in service ${service.id}")
                return BleErrors.readCharacteristicFailed("Characteristic not found: ${requestedChar.id} in service ${service.id}").asErrorResult()
            }
            discoveredCharacteristics.add(foundChar.toGattCharacteristic())
        }

        return discoveredCharacteristics.asOkResult()
    }

    @OptIn(ExperimentalUuidApi::class)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun readCharacteristic(
        service: HasUuidId,
        characteristic: HasUuidId,
    ): IdkResult<GattCharacteristic, CharacteristicReadError> {
        val requestId = scanRequestId

        fun createErrorEvent(bluetoothGattCharacteristic: BluetoothGattCharacteristic): IdkResult<BleEvent.CharacteristicRead, CharacteristicReadError> {
            val gattService =
                getGattService(
                    "CharacteristicRead",
                    service,
                    { BleErrors.readCharacteristicFailed("Service not found: ${service.id}") },
                    requestId = requestId,
                ).onFailure { return it.asErrorResult() }.value

            val device =
                getBluetoothDevice(
                    "CharacteristicRead",
                    { BleErrors.readCharacteristicFailed("Device not found") },
                    requestId = requestId,
                ).onFailure { return it.asErrorResult() }.value
            val event =
                BleEvent.CharacteristicRead(
                    deviceAddress = device.address,
                    service = gattService.toGattService(),
                    characteristic = bluetoothGattCharacteristic.toGattCharacteristic(),
                    value = byteArrayOf(),
                    requestId = requestId,
                    status = GATT_FAILURE,
                )
            return event.asOkResult()
        }

        log.debug("[id: $shortId] readCharacteristic() start: $bluetoothGatt, $service, $characteristic")
        // Let's first check whether the characteristic and service are available from local before going remote
        val bluetoothGattCharacteristic =
            getCharacteristicFromService("CharacteristicRead", service = service, characteristic = characteristic, {
                BleErrors.readCharacteristicFailed("Read characteristic failed at request call. Characteristic $characteristic not found")
            }, requestId = requestId)
                .onFailure {
                    return it.asErrorResult().also {
                        externalDispatch(
                            BleEvent.Error(
                                requestId = requestId,
                                deviceAddress = bluetoothGatt?.device?.address ?: "<unknown>",
                                error = it.error,
                                originalOperationId = "ReadCharacteristic",
                            ),
                        )
                    }
                }.value

        val gatt =
            getBluetoothGatt(
                "CharacteristicRead",
                { BleErrors.readCharacteristicFailed("Not connected") },
                requestId = requestId,
            ).onFailure { return it.asErrorResult() }.value

        if (gattOperationMutex.isLocked) {
            log.warn("[id: $shortId] readCharacteristic(): MUTEX CONTENTION DETECTED - another operation is in progress")
            log.warn("[id: $shortId] Stack trace: ${Exception().stackTraceToString()}")
        }
        return gattOperationMutex.withLock {
            try {
                if (!gatt.readCharacteristic(bluetoothGattCharacteristic)) {
                    val event = createErrorEvent(bluetoothGattCharacteristic).onFailure { return@withLock it.asErrorResult() }.value
                    val error = BleErrors.readCharacteristicFailed("Read characteristic failed at request call")
                    log.error("[id: $shortId] readCharacteristic(): failed with ${error.message}")
                    return@withLock error.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = error)) }
                }

                val event =
                    internalBleEvents
                        .filter { it.requestId == requestId }
                        .filter { it is BleEvent.CharacteristicRead || (it is BleEvent.Error && it.originalEvent is BleEvent.CharacteristicRead) }
                        .first()
                return@withLock if (event is BleEvent.Error && event.originalEvent is BleEvent.CharacteristicRead) {
                    val message = "readCharacteristic(): failed with ${event.message ?: event.error.message.defaultMessage}"
                    log.error("[id: $shortId] $message")
                    val error = BleErrors.readCharacteristicFailed(message)
                    error.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = error)) }
                } else if (event is BleEvent.CharacteristicRead) {
                    log.info("[id: $shortId] readCharacteristic(): done, characteristic: ${event.characteristic}")
                    event.characteristic.asOkResult().also { externalDispatch(event) }
                } else {
                    error("Unexpected event: $event")
                }
            } catch (expected: Exception) {
                log.error("[id: $shortId] readCharacteristic(): failed: ${expected.message}", exception = expected)
                val error = BleErrors.readCharacteristicFailed("Read characteristic failed", expected)
                externalDispatch(
                    BleEvent.Error(
                        requestId = requestId,
                        deviceAddress = bluetoothGatt?.device?.address ?: "<unknown>",
                        error = error,
                        originalOperationId = "ReadCharacteristic",
                    ),
                )
                return@withLock error.asErrorResult()
            } finally {
                log.debug("[id: $shortId] readCharacteristic(): releasing gattOperationMutex")
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun writeCharacteristic(
        service: HasUuidId,
        characteristic: HasUuidId,
        value: ByteArray,
        writeType: CharacteristicWriteMode,
    ): IdkResult<Int, CharacteristicWriteError> {
        val requestId = scanRequestId
        log.debug("[id: $shortId] writeCharacteristic() start: $bluetoothGatt, $service, $characteristic, $value, $writeType")
        val gatt =
            getBluetoothGatt(
                "WriteCharacteristic",
                { BleErrors.writeCharacteristicFailed("Service not found: ${service.id}") },
                requestId = requestId,
            ).onFailure { return it.asErrorResult() }.value
        val char =
            getCharacteristicFromService("WriteCharacteristic", service = service, characteristic = characteristic, {
                BleErrors.writeCharacteristicFailed("Characteristic not found: ${characteristic.id}")
            }, requestId = requestId).onFailure { return it.asErrorResult().also { log.error("[id: $shortId] ${it.error.message.defaultMessage}") } }.value

        if (gattOperationMutex.isLocked) {
            log.warn("[id: $shortId] writeCharacteristic(): MUTEX CONTENTION DETECTED - another operation is in progress")
            log.warn("[id: $shortId] Stack trace: ${Exception().stackTraceToString()}")
        }

        return gattOperationMutex.withLock {
            suspendCancellableCoroutine { continuation ->
                try {
                    log.debug("[id: $shortId] writeCharacteristic(): attempting write with type: $writeType")

                    // Set up event listener in the scope - this ensures it's subscribed before we make the BLE call
                    val job =
                        scope.launch {
                            try {
                                val event =
                                    internalBleEvents
                                        .filter { it.requestId == requestId }
                                        .filter { it is BleEvent.CharacteristicWrite || (it is BleEvent.Error && it.originalEvent is BleEvent.CharacteristicWrite) }
                                        .first()

                                log.debug("[id: $shortId] writeCharacteristic(): received event: ${event::class.simpleName}")

                                val result =
                                    if (event is BleEvent.Error && event.originalEvent is BleEvent.CharacteristicWrite) {
                                        val message = "writeCharacteristic(): failed with ${event.message ?: event.error.message.defaultMessage}"
                                        log.error("[id: $shortId] $message")
                                        val error = BleErrors.writeCharacteristicFailed(message)
                                        error.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = error)) }
                                    } else if (event is BleEvent.CharacteristicWrite) {
                                        log.info("[id: $shortId] writeCharacteristic(): done, characteristic: ${event.characteristic}")
                                        val writeStatus = if (event.status == GATT_SUCCESS) GATT_SUCCESS else GATT_FAILURE
                                        writeStatus.asOkResult().also { externalDispatch(event) }
                                    } else {
                                        log.error("[id: $shortId] writeCharacteristic(): Unexpected event $event")
                                        BleErrors.writeCharacteristicFailed("Unexpected event: $event").asErrorResult()
                                    }

                                if (continuation.isActive) {
                                    continuation.resume(result)
                                }
                            } catch (expected: Exception) {
                                if (continuation.isActive) {
                                    log.error("[id: $shortId] writeCharacteristic(): event handler failed: ${expected.message}", exception = expected)
                                    val error = BleErrors.writeCharacteristicFailed("Event handler failed: ${expected.message}", expected)
                                    continuation.resume(error.asErrorResult())
                                }
                            }
                        }

                    continuation.invokeOnCancellation {
                        log.debug("[id: $shortId] writeCharacteristic(): operation cancelled")
                        job.cancel()
                    }

                    // Now make the BLE call - the listener is already active
                    val writeStatus =
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
                            log.debug("[id: $shortId] writeCharacteristic(): using SDK 33+ API")
                            gatt.writeCharacteristic(char, value, writeType.value)
                        } else {
                            log.debug("[id: $shortId] writeCharacteristic(): using legacy API")
                            @Suppress("DEPRECATION")
                            char.setValue(value)
                            char.writeType = writeType.value
                            @Suppress("DEPRECATION")
                            gatt.writeCharacteristic(char).let { if (it) GATT_SUCCESS else GATT_FAILURE }
                        }
                    log.debug("[id: $shortId] writeCharacteristic(): write initiated with status: $writeStatus, writeType: $writeType")

                    if (writeStatus > GATT_SUCCESS) {
                        log.error("[id: $shortId] writeCharacteristic(): Error writing characteristic. Write status $writeStatus")
                        job.cancel()
                        val event =
                            BleEvent.CharacteristicWrite(
                                requestId = requestId,
                                deviceAddress = gatt.device.address,
                                service = service,
                                characteristic = characteristic,
                                status = GATT_FAILURE,
                            )
                        val error = BleErrors.writeCharacteristicFailed("writeCharacteristic(): Writing characteristic failed at request call. Status: $writeStatus")
                        if (continuation.isActive) {
                            continuation.resume(error.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = error)) })
                        }
                    }
                } catch (expected: Exception) {
                    log.error("[id: $shortId] writeCharacteristic(): failed: ${expected.message}", exception = expected)
                    val error = BleErrors.writeCharacteristicFailed("Write characteristic failed for ${characteristic.id}", expected)
                    val event =
                        BleEvent.CharacteristicWrite(
                            requestId = requestId,
                            deviceAddress = gatt.device.address,
                            service = service,
                            characteristic = characteristic,
                            status = GATT_FAILURE,
                        )
                    if (continuation.isActive) {
                        continuation.resume(error.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = error)) })
                    }
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun getDevice(): IdkResult<BleDevice, ConnectionFailedError> {
        val requestId = scanRequestId
        return connectedDevice?.let {
            log.debug("[id: $shortId] getDevice(): returning connected device ${it.address}")
            Ok(BleDevice(address = it.address, name = it.name))
        } ?: BleErrors.connectionFailed("No device connected").asErrorResult().also {
            log.warn("[id: $shortId] getDevice(): no device connected")
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun enableNotifications(
        service: HasUuidId,
        characteristic: HasUuidId,
    ): IdkResult<GattDescriptor, NotificationFailedError> {
        val requestId = scanRequestId
        log.debug("[id: $shortId] enableNotifications() start: $service, $characteristic")

        val gatt =
            getBluetoothGatt("EnableNotification", { BleErrors.notificationFailed("Not connected") }, requestId = requestId).onFailure { return it.asErrorResult() }.value
        val bluetoothGattCharacteristic =
            getCharacteristicFromService("EnableNotification", service = service, characteristic = characteristic, {
                BleErrors.notificationFailed("Characteristic not found: ${characteristic.id}")
            }, requestId = requestId).onFailure { return it.asErrorResult() }.value

        // This is Client Characteristic Configuration
        val clientCharacteristicConfigUuid = Uuid.parse("00002902-0000-1000-8000-00805f9b34fb")

        log.debug("[id: $shortId] Enabling notifications for characteristic ${bluetoothGattCharacteristic.uuid}")
        if (!gatt.setCharacteristicNotification(bluetoothGattCharacteristic, true)) {
            log.error("[id: $shortId] Error setting notification")
            val event =
                BleEvent.Notification(
                    requestId = requestId,
                    deviceAddress = gatt.device.address,
                    service = service,
                    characteristic = characteristic,
                    value = byteArrayOf(),
                )
            val error = BleErrors.notificationFailed("Enabling notifications failed at request call")
            return error.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = error)) }
        }

        val descriptor: BluetoothGattDescriptor? = bluetoothGattCharacteristic.getDescriptor(clientCharacteristicConfigUuid.toJavaUuid())
        if (descriptor == null) {
            log.error("[id: $shortId] Error setting notification. Could not read client characteristic config descriptor")
            val event =
                BleEvent.Notification(
                    requestId = requestId,
                    deviceAddress = gatt.device.address,
                    service = service,
                    characteristic = characteristic,
                    value = byteArrayOf(),
                )
            val error = BleErrors.notificationFailed("Enabling notifications failed because client characteristic config descriptor not found")
            return error.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = error)) }
        }

        if (gattOperationMutex.isLocked) {
            log.warn("[id: $shortId] enableNotifications(): MUTEX CONTENTION DETECTED - another operation is in progress")
            log.warn("[id: $shortId] Stack trace: ${Exception().stackTraceToString()}")
        }
        return gattOperationMutex.withLock {
            try {
                val writeSuccess =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val success = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        success == BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)
                    }
                if (!writeSuccess) {
                    log.error("[id: $shortId] Error setting notification. Could not write client characteristic config descriptor")
                    val event =
                        BleEvent.Notification(
                            requestId = requestId,
                            deviceAddress = gatt.device.address,
                            service = service,
                            characteristic = characteristic,
                            value = byteArrayOf(),
                        )
                    val error = BleErrors.notificationFailed("Enabling notifications failed at request call")
                    return@withLock error.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = error)) }
                }

                val event =
                    internalBleEvents
                        .filter { it.requestId == requestId }
                        .filter { it is BleEvent.DescriptorWrite || (it is BleEvent.Error && it.originalEvent is BleEvent.DescriptorWrite) }
                        .first()

                return@withLock if (event is BleEvent.Error && event.originalEvent is BleEvent.DescriptorWrite) {
                    val message = "enableNotifications(): failed with ${event.message ?: event.error.message.defaultMessage}"
                    log.error("[id: $shortId] $message")
                    val error = BleErrors.notificationFailed(message)
                    error.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = error)) }
                } else if (event is BleEvent.DescriptorWrite) {
                    val result = event.descriptor
                    log.info("[id: $shortId] enableNotifications(): done, descriptor: ${event.descriptor}")
                    result.asOkResult().also { externalDispatch(event) }
                } else {
                    error("Unexpected event: $event")
                }
            } catch (expected: Exception) {
                log.error("[id: $shortId] enableNotifications(): failed: ${expected.message}", exception = expected)
                val error = BleErrors.notificationFailed("Enabling notifications failed", expected)
                externalDispatch(
                    BleEvent.Error(
                        requestId = requestId,
                        deviceAddress = gatt.device.address,
                        error = error,
                        originalOperationId = "EnableNotification",
                    ),
                )
                error.asErrorResult()
            } finally {
                log.debug("[id: $shortId] enableNotifications(): releasing gattOperationMutex")
            }
        }
    }

    private fun externalDispatch(event: BleEvent) {
        val shortId = event.requestId.toShortId()

        if (event is BleEvent.Error) {
            log.error("[id: $shortId] externalDispatch(): dispatching error event: ${event.error.message.defaultMessage}")
        } else {
            log.info("[id: $shortId] externalDispatch(): dispatching event: ${event.operationId} id: ${event.requestId}, time: ${event.time}")
        }
        _externalBleEvents.tryEmit(event)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun <ErrorType : BleErrors> getGattService(
        operationId: String = "unknown",
        service: HasUuidId,
        errorOnNoServiceFoundCallback: () -> ErrorType,
        requestId: Uuid,
    ): IdkResult<BluetoothGattService, ErrorType> {
        val gatt =
            getBluetoothGatt(errorOnNoGattCallback = errorOnNoServiceFoundCallback, operationId = operationId, requestId = requestId)
                .onFailure {
                    return it.asErrorResult()
                }.value

        val gattService: BluetoothGattService? = gatt.getService(service.id.toJavaUuid())
        if (gattService == null) {
            log.warn("[id: $shortId] readCharacteristic(): service not found: ${service.id}")

            val errorOnNotFound = errorOnNoServiceFoundCallback()
            val event =
                BleEvent.Error(
                    requestId = requestId,
                    deviceAddress = gatt.device.address,
                    error = errorOnNotFound,
                    message = errorOnNotFound.message.defaultMessage,
                    originalOperationId = "CharacteristicRead",
                )
            return errorOnNotFound.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = errorOnNotFound)) }
        }
        return gattService.asOkResult()
    }

    @OptIn(ExperimentalUuidApi::class)
    fun <ErrorType : BleErrors> getCharacteristicFromService(
        operationId: String,
        service: HasUuidId,
        characteristic: HasUuidId,
        errorOnNoCharacteristicFoundCallback: () -> ErrorType,
        requestId: Uuid,
    ): IdkResult<BluetoothGattCharacteristic, ErrorType> {
        val gattService =
            getGattService(
                operationId = operationId,
                service = service,
                errorOnNoCharacteristicFoundCallback,
                requestId = requestId,
            ).onFailure { return it.asErrorResult() }.value

        val result: BluetoothGattCharacteristic? = gattService.getCharacteristic(characteristic.id.toJavaUuid())
        if (result == null) {
            val error = errorOnNoCharacteristicFoundCallback.invoke()
            log.warn("[id: $shortId] readCharacteristic(): characteristic not found: ${characteristic.id}")
            val deviceAddress =
                getBluetoothDevice(
                    operationId = "CharacteristicRead",
                    errorOnNoCharacteristicFoundCallback,
                    requestId = requestId,
                ).onFailure { return it.asErrorResult() }.value.address
            val event =
                BleEvent.Error(
                    requestId = requestId,
                    deviceAddress = deviceAddress,
                    error = error,
                    message = error.message.defaultMessage,
                    originalOperationId = "CharacteristicRead",
                )
            return error.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = error)) }
        }
        return result.asOkResult()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun <ErrorType : BleErrors> getBluetoothGatt(
        operationId: String = "unknown",
        errorOnNoGattCallback: () -> ErrorType,
        requestId: Uuid,
    ): IdkResult<BluetoothGatt, ErrorType> {
        val gatt = bluetoothGatt
        if (gatt != null) {
            return gatt.asOkResult()
        }

        val errorOnNotFound = errorOnNoGattCallback()
        log.error("[id: $shortId] Gatt could not be accessed. ${errorOnNotFound.message.defaultMessage}")

        val event =
            BleEvent.Error(
                requestId = requestId,
                deviceAddress = "<unknown because of gatt error>",
                error = errorOnNotFound,
                message = errorOnNotFound.message.defaultMessage,
                originalOperationId = operationId,
            )
        return errorOnNotFound.asErrorResult().also { externalDispatch(BleEvent.Error.from(event = event, error = errorOnNotFound)) }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun <ErrorType : BleErrors> getBluetoothDevice(
        operationId: String = "unknown",
        errorOnNoGattCallback: () -> ErrorType,
        requestId: Uuid,
    ): IdkResult<BluetoothDevice, ErrorType> {
        val gatt =
            getBluetoothGatt(errorOnNoGattCallback = errorOnNoGattCallback, operationId = operationId, requestId = requestId)
                .onFailure {
                    return it.asErrorResult().also { log.warn("[id: $shortId] getBluetoothDevice(): gatt is null") }
                }.value
        return gatt.device.asOkResult()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun close() {
        val requestId = scanRequestId
        log.info("[id: $shortId] close() called")

        @SuppressLint("InlinedApi")
        val granted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            log.warn("[id: $shortId] close(): no BLUETOOTH_CONNECT permission, skipping disconnect")
            mutableInternalBleEvents.resetReplayCache()
            _externalBleEvents.resetReplayCache()
            return
        }
        @SuppressLint("MissingPermission")

        CoroutineScope(Dispatchers.IO).launch {
            log.info("[id: $shortId] close(): calling disconnect on close")
            disconnect()
            log.info("[id: $shortId] close(): disconnect done on close")
            mutableInternalBleEvents.resetReplayCache()
            _externalBleEvents.resetReplayCache()
        }
    }

    @ContributesTo(AppScope::class)
    interface Graph {
        val blePlatformClient: BlePlatformClient
    }
}

@OptIn(ExperimentalUuidApi::class)
fun BluetoothGattCharacteristic.toGattCharacteristic(service: GattService? = null): GattCharacteristic {
    val props = mutableSetOf<GattProperty>()
    if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) props.add(GattProperty.READ)
    if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) props.add(GattProperty.WRITE)
    if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) props.add(GattProperty.WRITE_WITHOUT_RESPONSE)
    if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) props.add(GattProperty.NOTIFY)
    if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) props.add(GattProperty.INDICATE)

    return GattCharacteristic(
        id = uuid.toKotlinUuid(),
        properties = props,
        service = service,
    )
}

@OptIn(ExperimentalUuidApi::class)
fun BluetoothGattService.toGattService(): GattService =
    GattService(
        // service is null to not create an infinite loop
        id = this.uuid.toKotlinUuid(),
        characteristics = this.characteristics.map { it.toGattCharacteristic(service = null) },
    )

fun BluetoothGatt.toMinimalBleDevice(): BleDevice {
    val device = this.device ?: throw IllegalStateException("BluetoothGatt has no device")

    return BleDevice(
        address = device.address,
        name = "unknown",
    )
}

@OptIn(ExperimentalUuidApi::class)
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun BluetoothGatt.toBleDevice(): BleDevice {
    val device = this.device ?: throw IllegalStateException("BluetoothGatt has no device")

    return BleDevice(
        address = device.address,
        name = device.name,
        rssi = null, // TODO
        services = device.uuids?.map { it.uuid.toKotlinUuid().toUuidId() }?.toList(),
    )
}

@OptIn(ExperimentalUuidApi::class)
fun BluetoothGattDescriptor.toGattDescriptor(characteristic: GattCharacteristic? = null): GattDescriptor {
    val permissionsResult =
        mutableSetOf<GattPermission>().apply {
            if (permissions and BluetoothGattDescriptor.PERMISSION_READ != 0) add(GattPermission.READ)
            if (permissions and BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED != 0) add(GattPermission.READ_ENCRYPTED)
            if (permissions and BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM != 0) add(GattPermission.READ_ENCRYPTED_MITM)
            if (permissions and BluetoothGattDescriptor.PERMISSION_WRITE != 0) add(GattPermission.WRITE)
            if (permissions and BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED != 0) add(GattPermission.WRITE_ENCRYPTED)
            if (permissions and BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM != 0) add(GattPermission.WRITE_ENCRYPTED_MITM)
        }

    return GattDescriptor(
        id = uuid.toKotlinUuid(),
        permissions = permissionsResult,
        characteristic = characteristic,
    )
}
