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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.core.util.toByteArray
import com.sphereon.core.util.toNSData
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.BleErrors
import com.sphereon.data.link.ble.CharacteristicReadError
import com.sphereon.data.link.ble.CharacteristicWriteError
import com.sphereon.data.link.ble.ConnectionFailedError
import com.sphereon.data.link.ble.MtuChangeError
import com.sphereon.data.link.ble.NotificationFailedError
import com.sphereon.data.link.ble.ScanError
import com.sphereon.data.link.ble.client.cmd.ScanDevicesArgs
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
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerStatePoweredOff
import platform.CoreBluetooth.CBCentralManagerStatePoweredOn
import platform.CoreBluetooth.CBCentralManagerStateResetting
import platform.CoreBluetooth.CBCentralManagerStateUnauthorized
import platform.CoreBluetooth.CBCentralManagerStateUnknown
import platform.CoreBluetooth.CBCentralManagerStateUnsupported
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithoutResponse
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.darwin.NSObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalForeignApi::class)
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosBlePlatformClient(
//    val app: App,
    logManager: AppLogManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : BlePlatformClient {
    private val _externalBleEvents = MutableSharedFlow<BleEvent>(extraBufferCapacity = Int.MAX_VALUE)
    private val mutableInternalBleEvents = MutableSharedFlow<BleEvent>(extraBufferCapacity = Int.MAX_VALUE)
    val externalBleEvents: SharedFlow<BleEvent> = _externalBleEvents.shareIn(scope, SharingStarted.Eagerly, replay = 0)
    private val internalBleEvents: SharedFlow<BleEvent> = mutableInternalBleEvents.shareIn(scope, SharingStarted.Eagerly, replay = 0)

    private val log = logManager.withTag("IosBlePlatformClient")

    // Dedicated deferred for power-on state - completed once when we first see PoweredOn
    private val powerOnDeferred = kotlinx.coroutines.CompletableDeferred<Unit>()

    private var centralManager: CBCentralManager? = null
    private var peripheral: CBPeripheral? = null
    private var connectedDevice: BleDevice? = null
    private var discoveredDevices = mutableMapOf<String, BleDevice>()

    var requestId = Uuid.random()

    private var maxCharacteristicSize = 512

    private sealed class PendingOperation<T> {
        abstract val deferred: kotlinx.coroutines.CompletableDeferred<T>

        data class PowerOn(
            override val deferred: kotlinx.coroutines.CompletableDeferred<Unit> = kotlinx.coroutines.CompletableDeferred(),
        ) : PendingOperation<Unit>()

        data class PeripheralDiscovery(
            override val deferred: kotlinx.coroutines.CompletableDeferred<Unit> = kotlinx.coroutines.CompletableDeferred(),
        ) : PendingOperation<Unit>()

        data class Connection(
            override val deferred: kotlinx.coroutines.CompletableDeferred<Unit> = kotlinx.coroutines.CompletableDeferred(),
        ) : PendingOperation<Unit>()

        data class ServiceDiscovery(
            override val deferred: kotlinx.coroutines.CompletableDeferred<Unit> = kotlinx.coroutines.CompletableDeferred(),
        ) : PendingOperation<Unit>()

        data class CharacteristicDiscovery(
            override val deferred: kotlinx.coroutines.CompletableDeferred<Unit> = kotlinx.coroutines.CompletableDeferred(),
        ) : PendingOperation<Unit>()

        data class CharacteristicRead(
            val characteristic: CBCharacteristic,
            override val deferred: kotlinx.coroutines.CompletableDeferred<Unit> = kotlinx.coroutines.CompletableDeferred(),
        ) : PendingOperation<Unit>()

        data class CharacteristicWrite(
            val characteristic: CBCharacteristic,
            override val deferred: kotlinx.coroutines.CompletableDeferred<Unit> = kotlinx.coroutines.CompletableDeferred(),
        ) : PendingOperation<Unit>()

        data class NotificationSubscription(
            val characteristic: CBCharacteristic,
            override val deferred: kotlinx.coroutines.CompletableDeferred<Unit> = kotlinx.coroutines.CompletableDeferred(),
        ) : PendingOperation<Unit>()
    }

    private var pendingOperation: PendingOperation<*>? = null

    private inline fun <reified T : PendingOperation<R>, R> setPendingOperation(operation: T): kotlinx.coroutines.CompletableDeferred<R> {
        check(pendingOperation == null) { "Cannot start operation, already waiting for ${pendingOperation!!::class.simpleName}" }
        pendingOperation = operation
        return operation.deferred
    }

    private inline fun <reified T : PendingOperation<Unit>> completePendingOperation(block: (T) -> Unit = {}) {
        val operation = pendingOperation
        if (operation is T) {
            pendingOperation = null
            block(operation)
            operation.deferred.complete(Unit)
        }
    }

    private inline fun <reified T : PendingOperation<Unit>> failPendingOperation(exception: Throwable) {
        val operation = pendingOperation
        if (operation is T) {
            pendingOperation = null
            operation.deferred.completeExceptionally(exception)
        }
    }

    @Suppress("CONFLICTING_OVERLOADS")
    private val peripheralDelegate =
        object : NSObject(), CBPeripheralDelegateProtocol {
            override fun peripheral(
                peripheral: CBPeripheral,
                didDiscoverServices: NSError?,
            ) {
                log.debug("didDiscoverServices peripheral=${peripheral.identifier}, error=$didDiscoverServices")
                if (didDiscoverServices != null) {
                    failPendingOperation<PendingOperation.ServiceDiscovery>(
                        Error("Service discovery failed: ${didDiscoverServices.localizedDescription}"),
                    )
                } else {
                    completePendingOperation<PendingOperation.ServiceDiscovery>()
                }
            }

            override fun peripheral(
                peripheral: CBPeripheral,
                didDiscoverCharacteristicsForService: CBService,
                error: NSError?,
            ) {
                log.debug("didDiscoverCharacteristicsForService service=${didDiscoverCharacteristicsForService.UUID}, error=$error")
                if (error != null) {
                    failPendingOperation<PendingOperation.CharacteristicDiscovery>(
                        Error("Characteristic discovery failed: ${error.localizedDescription}"),
                    )
                } else {
                    completePendingOperation<PendingOperation.CharacteristicDiscovery>()
                }
            }

            @ObjCSignatureOverride
            override fun peripheral(
                peripheral: CBPeripheral,
                didUpdateValueForCharacteristic: CBCharacteristic,
                error: NSError?,
            ) {
                // DIAGNOSTIC: This log MUST appear if iOS is invoking this callback
                log.info("!!!!!!!!!!! didUpdateValueForCharacteristic CALLBACK INVOKED !!!!!!!!!!!")
                val valueSize = didUpdateValueForCharacteristic.value?.length?.toInt() ?: 0
                log.info("didUpdateValueForCharacteristic: characteristic=${didUpdateValueForCharacteristic.UUID}, valueSize=$valueSize, error=$error")
                log.info("didUpdateValueForCharacteristic: peripheral.delegate is set: ${peripheral.delegate != null}")

                val operation = pendingOperation

                // Check if this is a read completion callback
                if (operation is PendingOperation.CharacteristicRead && operation.characteristic == didUpdateValueForCharacteristic) {
                    log.debug("didUpdateValueForCharacteristic: completing pending read operation")
                    if (error != null) {
                        failPendingOperation<PendingOperation.CharacteristicRead>(
                            Error("Read characteristic failed: ${error.localizedDescription}"),
                        )
                    } else {
                        completePendingOperation<PendingOperation.CharacteristicRead>()
                    }
                    // Otherwise it's a notification
                } else {
                    log.info("didUpdateValueForCharacteristic: received notification on ${didUpdateValueForCharacteristic.UUID}, valueSize=$valueSize")
                    if (error == null) {
                        val value = didUpdateValueForCharacteristic.value?.toByteArray() ?: byteArrayOf()
                        log.info("didUpdateValueForCharacteristic: dispatching CharacteristicChanged event with ${value.size} bytes")
                        internalDispatch(
                            BleEvent.CharacteristicChanged(
                                requestId = requestId,
                                deviceAddress = peripheral.identifier.UUIDString,
                                service = didUpdateValueForCharacteristic.service!!.toGattService(),
                                characteristic = didUpdateValueForCharacteristic.toGattCharacteristic(),
                                value = value,
                            ),
                        )
                    } else {
                        log.error("didUpdateValueForCharacteristic: notification error: ${error.localizedDescription}")
                    }
                }
            }

            @ObjCSignatureOverride
            override fun peripheral(
                peripheral: CBPeripheral,
                didUpdateNotificationStateForCharacteristic: CBCharacteristic,
                error: NSError?,
            ) {
                val isNotifying = didUpdateNotificationStateForCharacteristic.isNotifying()
                log.info("didUpdateNotificationStateForCharacteristic: characteristic=${didUpdateNotificationStateForCharacteristic.UUID}, isNotifying=$isNotifying, error=$error")

                val operation = pendingOperation
                if (operation is PendingOperation.NotificationSubscription && operation.characteristic == didUpdateNotificationStateForCharacteristic) {
                    if (error != null) {
                        log.error("Failed to update notification state for ${didUpdateNotificationStateForCharacteristic.UUID}: ${error.localizedDescription}")
                        failPendingOperation<PendingOperation.NotificationSubscription>(
                            Error("Failed to enable notifications: ${error.localizedDescription}"),
                        )
                    } else {
                        log.info("Notification ${if (isNotifying) "enabled" else "disabled"} for ${didUpdateNotificationStateForCharacteristic.UUID}")
                        completePendingOperation<PendingOperation.NotificationSubscription>()
                    }
                } else {
                    // Callback received but no matching pending operation (could be from a previous call or unsolicited)
                    if (error != null) {
                        log.error("Failed to update notification state for ${didUpdateNotificationStateForCharacteristic.UUID}: ${error.localizedDescription}")
                    } else {
                        log.info("Notification ${if (isNotifying) "enabled" else "disabled"} for ${didUpdateNotificationStateForCharacteristic.UUID}")
                    }
                }
            }

            @ObjCSignatureOverride
            override fun peripheral(
                peripheral: CBPeripheral,
                didWriteValueForCharacteristic: CBCharacteristic,
                error: NSError?,
            ) {
                log.debug("didWriteValueForCharacteristic characteristic=${didWriteValueForCharacteristic.UUID}, error=$error")

                val operation = pendingOperation
                if (operation is PendingOperation.CharacteristicWrite && operation.characteristic == didWriteValueForCharacteristic) {
                    log.debug("didWriteValueForCharacteristic: completing pending write operation")
                    if (error != null) {
                        failPendingOperation<PendingOperation.CharacteristicWrite>(
                            Error("Write characteristic failed: ${error.localizedDescription}"),
                        )
                    } else {
                        completePendingOperation<PendingOperation.CharacteristicWrite>()
                    }
                } else {
                    log.warn("didWriteValueForCharacteristic: callback received but no matching pending operation. operation=$operation, characteristic=${didWriteValueForCharacteristic.UUID}")
                }
            }

            override fun peripheral(
                peripheral: CBPeripheral,
                didModifyServices: List<*>,
            ) {
                log.warn("peripheral:didModifyServices invalidatedServices=$didModifyServices")
                externalDispatch(
                    BleEvent.Error(
                        requestId = requestId,
                        deviceAddress = peripheral.identifier.UUIDString,
                        error = BleErrors.connectionFailed("Remote service vanished"),
                        originalOperationId = "ServiceModified",
                    ),
                )
            }
        }

    private val centralManagerDelegate =
        object : NSObject(), CBCentralManagerDelegateProtocol {
            override fun centralManagerDidUpdateState(central: CBCentralManager) {
                val stateDescription =
                    when (central.state) {
                        CBCentralManagerStateUnknown -> "Unknown (0) - Bluetooth state is unknown"
                        CBCentralManagerStateResetting -> "Resetting (1) - Connection with system service was momentarily lost"
                        CBCentralManagerStateUnsupported -> "Unsupported (2) - Device does not support Bluetooth Low Energy"
                        CBCentralManagerStateUnauthorized -> "Unauthorized (3) - App is not authorized to use Bluetooth. Check Info.plist for NSBluetoothAlwaysUsageDescription"
                        CBCentralManagerStatePoweredOff -> "PoweredOff (4) - Bluetooth is turned off"
                        CBCentralManagerStatePoweredOn -> "PoweredOn (5) - Bluetooth is ready"
                        else -> "Unknown state code: ${central.state}"
                    }
                log.info("centralManagerDidUpdateState: $stateDescription")

                if (central.state == CBCentralManagerStatePoweredOn) {
                    log.info("Bluetooth is powered on and ready")
                    // Complete the dedicated power-on deferred (only completes once)
                    powerOnDeferred.complete(Unit)
                    // Also complete any pending power-on operation for backwards compatibility
                    completePendingOperation<PendingOperation.PowerOn>()
                } else if (central.state == CBCentralManagerStateUnauthorized) {
                    log.error("Bluetooth not authorized! Add NSBluetoothAlwaysUsageDescription to Info.plist")
                    failPendingOperation<PendingOperation.PowerOn>(
                        Error("Bluetooth not authorized. Add NSBluetoothAlwaysUsageDescription to Info.plist"),
                    )
                } else if (central.state == CBCentralManagerStatePoweredOff) {
                    log.warn("Bluetooth is powered off. User needs to enable Bluetooth in Settings")
                    failPendingOperation<PendingOperation.PowerOn>(
                        Error("Bluetooth is powered off. Please enable Bluetooth"),
                    )
                } else if (central.state == CBCentralManagerStateUnsupported) {
                    log.error("Bluetooth Low Energy is not supported on this device")
                    failPendingOperation<PendingOperation.PowerOn>(
                        Error("Bluetooth Low Energy is not supported on this device"),
                    )
                }
                // For Unknown and Resetting states, we wait - the callback will fire again
            }

            override fun centralManager(
                central: CBCentralManager,
                didDiscoverPeripheral: CBPeripheral,
                advertisementData: Map<Any?, *>,
                RSSI: NSNumber,
            ) {
                val discoveredPeripheral = didDiscoverPeripheral
                log.debug("didDiscoverPeripheral: peripheral=${discoveredPeripheral.identifier}, name=${discoveredPeripheral.name}, RSSI=$RSSI")

                val address = discoveredPeripheral.identifier.UUIDString
                val name = discoveredPeripheral.name ?: "Unknown"
                val rssi = RSSI.intValue

                val serviceUUIDs = advertisementData["kCBAdvDataServiceUUIDs"] as? List<*>
                val services =
                    serviceUUIDs?.mapNotNull {
                        (it as? CBUUID)?.UUIDString?.toFullBluetoothUuid()?.toUuidId()
                    } ?: emptyList()

                val device = BleDevice(address = address, name = name, rssi = rssi, services = services)

                if (!discoveredDevices.containsKey(address)) {
                    log.info("didDiscoverPeripheral: new device found: $address, total devices: ${discoveredDevices.size + 1}")
                    externalDispatch(
                        BleEvent.DeviceFound(
                            requestId = requestId,
                            deviceAddress = address,
                            device = device,
                        ),
                    )
                }
                discoveredDevices[address] = device

                // Check if we've reached the max results and should complete the scan
                if (discoveredDevices.size >= scanMaxResults) {
                    log.info("didDiscoverPeripheral: max results ($scanMaxResults) reached, completing scan")
                    scanCompletionDeferred?.complete(discoveredDevices.values.toList())
                }

                completePendingOperation<PendingOperation.PeripheralDiscovery> {
                    peripheral = discoveredPeripheral
                    peripheral?.delegate = peripheralDelegate
                }
            }

            override fun centralManager(
                central: CBCentralManager,
                didConnectPeripheral: CBPeripheral,
            ) {
                log.debug("didConnectPeripheral: peripheral=${didConnectPeripheral.identifier}")
                if (didConnectPeripheral == peripheral) {
                    completePendingOperation<PendingOperation.Connection>()
                } else {
                    log.warn("Callback for unexpected peripheral")
                }
            }

            override fun centralManager(
                central: CBCentralManager,
                didFailToConnectPeripheral: CBPeripheral,
                error: NSError?,
            ) {
                log.error("didFailToConnectPeripheral: peripheral=${didFailToConnectPeripheral.identifier}, error=$error")
                failPendingOperation<PendingOperation.Connection>(
                    Error("Failed to connect: ${error?.localizedDescription}"),
                )
            }

            override fun centralManager(
                central: CBCentralManager,
                didDisconnectPeripheral: CBPeripheral,
                timestamp: platform.CoreFoundation.CFAbsoluteTime,
                isReconnecting: Boolean,
                error: NSError?,
            ) {
                log.debug("didDisconnectPeripheral: peripheral=${didDisconnectPeripheral.identifier}, error=$error")
                externalDispatch(
                    BleEvent.ConnectionStateChanged(
                        requestId = requestId,
                        deviceAddress = didDisconnectPeripheral.identifier.UUIDString,
                        newState = 0, // STATE_DISCONNECTED
                        status = if (error == null) 0 else -1,
                    ),
                )
                pendingOperation?.deferred?.cancel()
                pendingOperation = null
            }
        }

    init {
        centralManager =
            CBCentralManager(
                delegate = centralManagerDelegate,
                queue = null,
                options = null,
            )

        // Listen for characteristic changes and forward to external dispatch
        log.info("INIT: Setting up CharacteristicChanged event forwarding")
        internalBleEvents
            .filter { it is BleEvent.CharacteristicChanged }
            .onEach { event ->
                log.info("INIT flow: Forwarding CharacteristicChanged to externalDispatch")
                externalDispatch(event)
            }.launchIn(scope = scope)
        log.info("INIT: CharacteristicChanged forwarding coroutine launched")
    }

    // Track scan completion deferred for use by didDiscoverPeripheral
    private var scanCompletionDeferred: kotlinx.coroutines.CompletableDeferred<List<BleDevice>>? = null
    private var scanMaxResults: Int = 1

    override suspend fun scan(args: ScanDevicesArgs): IdkResult<List<BleDevice>, ScanError> {
        log.info("scan() start: $args")
        this.requestId = args.requestId ?: Uuid.random()
        discoveredDevices.clear()
        scanMaxResults = args.maxResults

        return try {
            val manager = centralManager ?: return BleErrors.scanFailed("Central manager not initialized").asErrorResult()

            // Check for immediate failure states
            val currentState = manager.state
            val stateDescription =
                when (currentState) {
                    CBCentralManagerStateUnknown -> "Unknown"
                    CBCentralManagerStateResetting -> "Resetting"
                    CBCentralManagerStateUnsupported -> "Unsupported"
                    CBCentralManagerStateUnauthorized -> "Unauthorized"
                    CBCentralManagerStatePoweredOff -> "PoweredOff"
                    CBCentralManagerStatePoweredOn -> "PoweredOn"
                    else -> "Unknown($currentState)"
                }
            log.info("scan(): checking Bluetooth state, current state=$stateDescription ($currentState)")

            // Check for fatal states that won't resolve by waiting
            when (currentState) {
                CBCentralManagerStateUnauthorized -> {
                    log.error("scan(): Bluetooth not authorized! Add NSBluetoothAlwaysUsageDescription to Info.plist")
                    return BleErrors.scanFailed("Bluetooth not authorized. Please add NSBluetoothAlwaysUsageDescription and NSBluetoothPeripheralUsageDescription to Info.plist").asErrorResult()
                }

                CBCentralManagerStateUnsupported -> {
                    log.error("scan(): Bluetooth Low Energy is not supported on this device")
                    return BleErrors.scanFailed("Bluetooth Low Energy is not supported on this device").asErrorResult()
                }

                CBCentralManagerStatePoweredOff -> {
                    log.error("scan(): Bluetooth is powered off")
                    return BleErrors.scanFailed("Bluetooth is powered off. Please enable Bluetooth in Settings").asErrorResult()
                }
            }

            // Wait for Bluetooth to be powered on using the dedicated deferred
            // This deferred is completed in centralManagerDidUpdateState when PoweredOn is received
            if (currentState != CBCentralManagerStatePoweredOn) {
                log.info("scan(): Bluetooth not powered on yet (state=$stateDescription), waiting for powerOnDeferred...")
                try {
                    withTimeout(10000) {
                        // 10 second timeout for power on
                        powerOnDeferred.await()
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    val finalState = manager.state
                    val finalStateDesc =
                        when (finalState) {
                            CBCentralManagerStateUnknown -> "Unknown"
                            CBCentralManagerStateResetting -> "Resetting"
                            CBCentralManagerStateUnsupported -> "Unsupported"
                            CBCentralManagerStateUnauthorized -> "Unauthorized"
                            CBCentralManagerStatePoweredOff -> "PoweredOff"
                            CBCentralManagerStatePoweredOn -> "PoweredOn"
                            else -> "Unknown($finalState)"
                        }
                    log.error("scan(): timeout waiting for Bluetooth to power on. Final state: $finalStateDesc ($finalState)")
                    return BleErrors.scanFailed("Bluetooth not available - timed out waiting for power on (state=$finalStateDesc). Check that Bluetooth permissions are granted.").asErrorResult()
                }
                log.info("scan(): Bluetooth powered on (via deferred)")
            } else {
                log.debug("scan(): Bluetooth already powered on")
            }

            externalDispatch(BleEvent.ScanStarted(requestId = requestId, deviceAddress = "<scanning>"))

            // Build service UUIDs filter
            val serviceUUIDs =
                args.filters?.firstOrNull()?.let { filter ->
                    (filter as? com.sphereon.data.link.ble.filter.Filter.Service)?.uuid?.let {
                        listOf(CBUUID.UUIDWithString(it.toString()))
                    }
                }

            log.info("scan(): starting CoreBluetooth scan with serviceUUIDs=$serviceUUIDs")

            // Create a deferred that will be completed when enough devices are found
            val deferred = kotlinx.coroutines.CompletableDeferred<List<BleDevice>>()
            scanCompletionDeferred = deferred

            // Start scanning - callbacks will be delivered on Main queue
            manager.scanForPeripheralsWithServices(serviceUUIDs, null)
            log.info("scan(): CoreBluetooth scan started, waiting for devices (timeout=${args.timeout}, maxResults=${args.maxResults})")

            // Wait for either timeout or completion
            val devices =
                try {
                    withTimeout(args.timeout) {
                        deferred.await()
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    // Timeout reached - return whatever devices we found
                    log.info("scan(): timeout reached after ${args.timeout}, found ${discoveredDevices.size} devices")
                    discoveredDevices.values.toList()
                } finally {
                    scanCompletionDeferred = null
                    manager.stopScan()
                }

            log.info("scan(): scan finished, found ${devices.size} devices")

            externalDispatch(
                BleEvent.ScanResult(
                    requestId = requestId,
                    deviceAddress = "<scanning>",
                    devices = devices.toSet(),
                ),
            )
            externalDispatch(BleEvent.ScanStopped(requestId = requestId, deviceAddress = "<scanning>"))

            log.info("scan() complete, found ${devices.size} devices")
            devices.asOkResult()
        } catch (expected: Exception) {
            log.error("scan() failed: ${expected.message}", exception = expected)
            scanCompletionDeferred = null
            val error = BleErrors.scanFailed("Scan failed", expected)
            externalDispatch(
                BleEvent.Error(
                    requestId = requestId,
                    deviceAddress = "<scanning>",
                    error = error,
                    originalOperationId = "Scan",
                ),
            )
            error.asErrorResult()
        }
    }

    override suspend fun connect(device: IHasAddress): IdkResult<BleDevice, ConnectionFailedError> {
        log.info("connect() start: ${device.address}")
        return try {
            val manager = centralManager ?: return BleErrors.connectionFailed("Central manager not initialized").asErrorResult()

            // Find peripheral by UUID
            val peripheralToConnect =
                discoveredDevices[device.address]?.let {
                    peripheral?.takeIf { it.identifier.UUIDString == device.address }
                }

            if (peripheralToConnect == null) {
                // Try to scan for the device if not found
                val deferred = setPendingOperation(PendingOperation.PeripheralDiscovery())
                manager.scanForPeripheralsWithServices(null, null)
                deferred.await()
                manager.stopScan()
            }

            val peripheralToConnect2 = peripheral ?: return BleErrors.connectionFailed("Device not found: ${device.address}").asErrorResult()

            // Connect to peripheral
            val connectionDeferred = setPendingOperation(PendingOperation.Connection())
            manager.connectPeripheral(peripheralToConnect2, null)
            connectionDeferred.await()

            val bleDevice =
                BleDevice(
                    address = peripheralToConnect2.identifier.UUIDString,
                    name = peripheralToConnect2.name ?: "Unknown",
                    rssi = null,
                )
            connectedDevice = bleDevice

            externalDispatch(
                BleEvent.ConnectionStateChanged(
                    requestId = requestId,
                    deviceAddress = bleDevice.address,
                    newState = 2, // STATE_CONNECTED
                    status = 0,
                ),
            )

            log.info("connect() complete: ${bleDevice.address}")
            bleDevice.asOkResult()
        } catch (expected: Exception) {
            log.error("connect() failed: ${expected.message}", exception = expected)
            val error = BleErrors.connectionFailed("Connection failed", expected)
            externalDispatch(
                BleEvent.Error(
                    requestId = requestId,
                    deviceAddress = device.address,
                    error = error,
                    originalOperationId = "Connect",
                ),
            )
            error.asErrorResult()
        }
    }

    override suspend fun disconnect(): IdkResult<Unit, BleError> {
        log.info("disconnect() start")
        return try {
            val manager = centralManager
            val device = peripheral

            if (manager != null && device != null) {
                manager.cancelPeripheralConnection(device)
            }

            peripheral?.delegate = null
            peripheral = null
            connectedDevice = null

            externalDispatch(
                BleEvent.ConnectionStateChanged(
                    requestId = requestId,
                    deviceAddress = device?.identifier?.UUIDString ?: "<unknown>",
                    newState = 0, // STATE_DISCONNECTED
                    status = 0,
                ),
            )

            log.info("disconnect() complete")
            Unit.asOkResult()
        } catch (expected: Exception) {
            log.error("disconnect() failed: ${expected.message}", exception = expected)
            BleErrors.unknown("Disconnect failed", expected).asErrorResult()
        }
    }

    override suspend fun setMtu(mtu: Int): IdkResult<Int, MtuChangeError> {
        log.info("setMtu() start, mtu: $mtu")

        // iOS doesn't allow manual MTU configuration, it's handled automatically
        // The maximum characteristic size is determined by the peripheral
        val device = peripheral ?: return BleErrors.mtuChangeFailed("Not connected").asErrorResult()

        val maximumWriteValueLength =
            device
                .maximumWriteValueLengthForType(
                    CBCharacteristicWriteWithoutResponse,
                ).toInt()

        maxCharacteristicSize = minOf(maximumWriteValueLength, 512)

        log.info("setMtu() complete, using maxCharacteristicSize: $maxCharacteristicSize")

        externalDispatch(
            BleEvent.MtuChanged(
                requestId = requestId,
                deviceAddress = device.identifier.UUIDString,
                mtu = maxCharacteristicSize,
                status = 0,
            ),
        )

        return maxCharacteristicSize.asOkResult()
    }

    override suspend fun discoverServices(): IdkResult<List<GattService>, BleError> {
        log.info("discoverServices() start")
        return try {
            val device = peripheral ?: return BleErrors.serviceDiscoveryFailed("Not connected").asErrorResult()

            val deferred = setPendingOperation(PendingOperation.ServiceDiscovery())
            device.discoverServices(null)
            deferred.await()

            val cbServices = device.services?.mapNotNull { it as? CBService } ?: emptyList()
            log.info("discoverServices(): found ${cbServices.size} CBServices, now discovering characteristics for each")

            // On iOS, we need to discover characteristics for each service separately
            // Otherwise CBService.characteristics is null
            for (cbService in cbServices) {
                log.debug("discoverServices(): discovering characteristics for service ${cbService.UUID.UUIDString}")
                val charDeferred = setPendingOperation(PendingOperation.CharacteristicDiscovery())
                device.discoverCharacteristics(null, cbService)
                charDeferred.await()
                val charCount = cbService.characteristics?.size ?: 0
                log.debug("discoverServices(): service ${cbService.UUID.UUIDString} has $charCount characteristics")
            }

            // Now convert to GattService with characteristics populated
            val services = cbServices.map { it.toGattService() }

            // Log what we found for debugging
            for (service in services) {
                log.info("discoverServices(): service ${service.id} with ${service.characteristics.size} characteristics: ${service.characteristics.map { it.id }}")
            }

            externalDispatch(
                BleEvent.ServicesDiscovered(
                    requestId = requestId,
                    deviceAddress = device.identifier.UUIDString,
                    services = services,
                    status = 0,
                ),
            )

            log.info("discoverServices() complete: ${services.size} services with characteristics")
            services.asOkResult()
        } catch (expected: Exception) {
            log.error("discoverServices() failed: ${expected.message}", exception = expected)
            val error = BleErrors.serviceDiscoveryFailed("Service discovery failed", expected)
            externalDispatch(
                BleEvent.Error(
                    requestId = requestId,
                    deviceAddress = peripheral?.identifier?.UUIDString ?: "<unknown>",
                    error = error,
                    originalOperationId = "DiscoverServices",
                ),
            )
            error.asErrorResult()
        }
    }

    override suspend fun discoverServiceCharacteristics(
        service: HasUuidId,
        characteristics: List<HasUuidId>?,
    ): IdkResult<List<GattCharacteristic>, CharacteristicReadError> {
        log.info("discoverServiceCharacteristics() start: service=${service.id}")
        return try {
            val device = peripheral ?: return BleErrors.readCharacteristicFailed("Not connected").asErrorResult()

            val cbService =
                device.services?.firstOrNull {
                    (it as? CBService)?.UUID?.UUIDString?.toFullBluetoothUuid() == service.id
                } as? CBService ?: return BleErrors.readCharacteristicFailed("Service not found: ${service.id}").asErrorResult()

            if (cbService.characteristics == null) {
                val deferred = setPendingOperation(PendingOperation.CharacteristicDiscovery())
                device.discoverCharacteristics(null, cbService)
                deferred.await()
            }

            val discoveredChars =
                cbService.characteristics?.mapNotNull { char ->
                    (char as? CBCharacteristic)?.toGattCharacteristic()
                } ?: emptyList()

            // If specific characteristics requested, filter them
            val result =
                if (characteristics.isNullOrEmpty()) {
                    discoveredChars
                } else {
                    val requestedIds = characteristics.map { it.id }.toSet()
                    discoveredChars.filter { it.id in requestedIds }
                }

            log.info("discoverServiceCharacteristics() complete: ${result.size} characteristics")
            result.asOkResult()
        } catch (expected: Exception) {
            log.error("discoverServiceCharacteristics() failed: ${expected.message}", exception = expected)
            BleErrors.readCharacteristicFailed("Characteristic discovery failed", expected).asErrorResult()
        }
    }

    override suspend fun readCharacteristic(
        service: HasUuidId,
        characteristic: HasUuidId,
    ): IdkResult<GattCharacteristic, CharacteristicReadError> {
        log.info("readCharacteristic() start: service=${service.id}, characteristic=${characteristic.id}")
        return try {
            val device = peripheral ?: return BleErrors.readCharacteristicFailed("Not connected").asErrorResult()

            val cbCharacteristic =
                findCharacteristic(device, service, characteristic)
                    ?: return BleErrors.readCharacteristicFailed("Characteristic not found").asErrorResult()

            log.debug("readCharacteristic(): reading value, waiting for callback")
            val deferred = setPendingOperation(PendingOperation.CharacteristicRead(cbCharacteristic))
            device.readValueForCharacteristic(cbCharacteristic)
            try {
                withTimeout(30000) {
                    // 30 second timeout
                    log.debug("readCharacteristic(): waiting for callback")
                    deferred.await()
                    log.debug("readCharacteristic(): callback received")
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                pendingOperation = null
                log.error("readCharacteristic(): timeout waiting for callback")
                throw Error("Read characteristic timed out after 30 seconds")
            }

            val gattChar = cbCharacteristic.toGattCharacteristic()

            externalDispatch(
                BleEvent.CharacteristicRead(
                    requestId = requestId,
                    deviceAddress = device.identifier.UUIDString,
                    service = cbCharacteristic.service!!.toGattService(),
                    characteristic = gattChar,
                    value = cbCharacteristic.value?.toByteArray() ?: byteArrayOf(),
                    status = 0,
                ),
            )

            log.info("readCharacteristic() complete")
            gattChar.asOkResult()
        } catch (expected: Exception) {
            log.error("readCharacteristic() failed: ${expected.message}", exception = expected)
            BleErrors.readCharacteristicFailed("Read characteristic failed", expected).asErrorResult()
        }
    }

    override suspend fun writeCharacteristic(
        service: HasUuidId,
        characteristic: HasUuidId,
        value: ByteArray,
        writeType: CharacteristicWriteMode,
    ): IdkResult<Int, CharacteristicWriteError> {
        log.info("writeCharacteristic() start: service=${service.id}, characteristic=${characteristic.id}, valueSize=${value.size}")
        return try {
            val device = peripheral ?: return BleErrors.writeCharacteristicFailed("Not connected").asErrorResult().also { log.error("Peripheral not connected anymore!") }

            val cbCharacteristic =
                findCharacteristic(device, service, characteristic)
                    ?: return BleErrors.writeCharacteristicFailed("Characteristic not found").asErrorResult().also { log.error("Characteristic $characteristic not found anymore") }

            val nsData = value.toNSData()
            val cbWriteType =
                when (writeType) {
                    CharacteristicWriteMode.WRITE_TYPE_NO_RESPONSE -> CBCharacteristicWriteWithoutResponse
                    else -> CBCharacteristicWriteWithResponse
                }

            if (writeType == CharacteristicWriteMode.WRITE_TYPE_NO_RESPONSE) {
                // No response required
                log.debug("writeCharacteristic(): writing without response")
                device.writeValue(nsData, cbCharacteristic, cbWriteType)
            } else {
                // Response required - wait for didWriteValueForCharacteristic callback
                log.debug("writeCharacteristic(): writing with response, waiting for callback")
                val deferred = setPendingOperation(PendingOperation.CharacteristicWrite(cbCharacteristic))
                device.writeValue(nsData, cbCharacteristic, cbWriteType)
                try {
                    withTimeout(30000) {
                        // 30 second timeout
                        deferred.await()
                        log.debug("writeCharacteristic(): callback received")
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    pendingOperation = null
                    log.error("writeCharacteristic(): timeout waiting for callback")
                    throw Error("Write characteristic timed out after 30 seconds")
                }
            }

            externalDispatch(
                BleEvent.CharacteristicWrite(
                    requestId = requestId,
                    deviceAddress = device.identifier.UUIDString,
                    service = service,
                    characteristic = characteristic,
                    status = 0,
                ),
            )

            log.info("writeCharacteristic() complete")
            // DIAGNOSTIC: Verify delegate and notification state after write
            log.info("writeCharacteristic() DIAGNOSTIC: peripheral.delegate is set: ${device.delegate != null}")
            log.info("writeCharacteristic() DIAGNOSTIC: peripheral.state: ${device.state}")
            // Check if Server2Client characteristic still has notifications enabled
            val server2ClientUuid = kotlin.uuid.Uuid.parse("00000007-A123-48CE-896B-4C76973373E6")
            val server2ClientChar = findCharacteristic(device, service, server2ClientUuid.toUuidId())
            if (server2ClientChar != null) {
                log.info("writeCharacteristic() DIAGNOSTIC: Server2Client isNotifying: ${server2ClientChar.isNotifying()}")
            }
            0.asOkResult()
        } catch (expected: Exception) {
            log.error("writeCharacteristic() failed: ${expected.message}", exception = expected)
            BleErrors.writeCharacteristicFailed("Write characteristic failed", expected).asErrorResult()
        }
    }

    override suspend fun getDevice(): IdkResult<BleDevice, ConnectionFailedError> =
        connectedDevice?.asOkResult()
            ?: BleErrors.connectionFailed("No device connected").asErrorResult()

    override suspend fun enableNotifications(
        service: HasUuidId,
        characteristic: HasUuidId,
    ): IdkResult<GattDescriptor, NotificationFailedError> {
        log.info("enableNotifications() start: service=${service.id}, characteristic=${characteristic.id}")
        return try {
            val device = peripheral ?: return BleErrors.notificationFailed("Not connected").asErrorResult()

            val cbCharacteristic =
                findCharacteristic(device, service, characteristic)
                    ?: return BleErrors.notificationFailed("Characteristic not found").asErrorResult()

            // Wait for iOS to confirm notification subscription via didUpdateNotificationStateForCharacteristic
            log.debug("enableNotifications(): subscribing and waiting for confirmation callback")
            log.info("enableNotifications(): peripheral.delegate before setNotifyValue: ${device.delegate != null}")
            val deferred = setPendingOperation(PendingOperation.NotificationSubscription(cbCharacteristic))
            device.setNotifyValue(true, cbCharacteristic)
            log.info("enableNotifications(): called setNotifyValue(true), isNotifying=${cbCharacteristic.isNotifying()}")
            try {
                withTimeout(10000) {
                    // 10 second timeout for notification subscription
                    deferred.await()
                    log.debug("enableNotifications(): notification subscription confirmed by iOS")
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                pendingOperation = null
                log.error("enableNotifications(): timeout waiting for notification subscription confirmation")
                throw Error("Enable notifications timed out after 10 seconds")
            }

            // iOS handles CCCD automatically, create a descriptor representation
            val clientCharacteristicConfigUuid = Uuid.parse("00002902-0000-1000-8000-00805f9b34fb")
            val descriptor =
                GattDescriptor(
                    id = clientCharacteristicConfigUuid,
                    permissions = setOf(GattPermission.READ, GattPermission.WRITE),
                    characteristic = cbCharacteristic.toGattCharacteristic(),
                )

            externalDispatch(
                BleEvent.DescriptorWrite(
                    requestId = requestId,
                    deviceAddress = device.identifier.UUIDString,
                    service = service,
                    characteristic = characteristic,
                    descriptor = descriptor,
                    status = 0,
                ),
            )

            log.info("enableNotifications() complete")
            descriptor.asOkResult()
        } catch (expected: Exception) {
            log.error("enableNotifications() failed: ${expected.message}", exception = expected)
            BleErrors.notificationFailed("Enable notifications failed", expected).asErrorResult()
        }
    }

    private fun findCharacteristic(
        device: CBPeripheral,
        service: HasUuidId,
        characteristic: HasUuidId,
    ): CBCharacteristic? {
        val cbService =
            device.services?.firstOrNull {
                (it as? CBService)?.UUID?.UUIDString?.toFullBluetoothUuid() == service.id
            } as? CBService ?: return null

        return cbService.characteristics?.firstOrNull {
            (it as? CBCharacteristic)?.UUID?.UUIDString?.toFullBluetoothUuid() == characteristic.id
        } as? CBCharacteristic
    }

    override val bleEvents: SharedFlow<BleEvent> get() = externalBleEvents

    private fun internalDispatch(event: BleEvent) {
        if (event is BleEvent.CharacteristicChanged) {
            log.info("internalDispatch: CharacteristicChanged event for ${event.characteristic.id}, ${event.value.size} bytes")
        }
        val emitted = mutableInternalBleEvents.tryEmit(event)
        if (event is BleEvent.CharacteristicChanged) {
            log.info("internalDispatch: tryEmit result=$emitted, subscriptionCount=${mutableInternalBleEvents.subscriptionCount.value}")
        }
    }

    private fun externalDispatch(event: BleEvent) {
        if (event is BleEvent.Error) {
            log.error("externalDispatch(): error event: ${event.error.message.defaultMessage}")
        } else if (event is BleEvent.CharacteristicChanged) {
            log.info("externalDispatch(): CharacteristicChanged for ${event.characteristic.id}, ${event.value.size} bytes")
        } else {
            log.info("externalDispatch(): event: ${event.operationId}, id: ${event.requestId}")
        }

        _externalBleEvents.tryEmit(event)
    }

    override fun close() {
        scope.launch {
            try {
                log.info("close(): calling disconnect on close")
                disconnect()
                log.info("close(): disconnect done on close")
            } catch (expected: Exception) {
                log.error("close() failed: ${expected.message}", exception = expected)
            }
        }
    }
}

// Extension functions for CoreBluetooth conversions

/**
 * Converts a CBUUID string to a full 128-bit UUID.
 * iOS CoreBluetooth returns short UUIDs for standard Bluetooth SIG services:
 * - 4-char strings for 16-bit UUIDs (e.g., "1849")
 * - 8-char strings for 32-bit UUIDs (e.g., "12345678")
 *
 * These are expanded using the Bluetooth Base UUID: 00000000-0000-1000-8000-00805F9B34FB
 */
@OptIn(ExperimentalUuidApi::class)
fun String.toFullBluetoothUuid(): Uuid =
    when (this.length) {
        4 -> Uuid.parse("0000${this.uppercase()}-0000-1000-8000-00805F9B34FB")
        8 -> Uuid.parse("${this.uppercase()}-0000-1000-8000-00805F9B34FB")
        else -> Uuid.parse(this)
    }

@OptIn(ExperimentalUuidApi::class)
fun CBCharacteristic.toGattCharacteristic(): GattCharacteristic {
    val props = mutableSetOf<GattProperty>()

    val properties = this.properties.toInt()
    if (properties and 0x02 != 0) props.add(GattProperty.READ) // CBCharacteristicPropertyRead
    if (properties and 0x04 != 0) props.add(GattProperty.WRITE_WITHOUT_RESPONSE) // CBCharacteristicPropertyWriteWithoutResponse
    if (properties and 0x08 != 0) props.add(GattProperty.WRITE) // CBCharacteristicPropertyWrite
    if (properties and 0x10 != 0) props.add(GattProperty.NOTIFY) // CBCharacteristicPropertyNotify
    if (properties and 0x20 != 0) props.add(GattProperty.INDICATE) // CBCharacteristicPropertyIndicate

    return GattCharacteristic(
        id = this.UUID.UUIDString.toFullBluetoothUuid(),
        properties = props,
        service = this.service?.toGattService(),
    )
}

@OptIn(ExperimentalUuidApi::class)
fun CBService.toGattService(): GattService =
    GattService(
        id = this.UUID.UUIDString.toFullBluetoothUuid(),
        characteristics =
            this.characteristics?.mapNotNull { char ->
                (char as? CBCharacteristic)?.toGattCharacteristic(service = null)
            } ?: emptyList(),
    )

@OptIn(ExperimentalUuidApi::class)
fun CBCharacteristic.toGattCharacteristic(service: GattService?): GattCharacteristic {
    val props = mutableSetOf<GattProperty>()

    val properties = this.properties.toInt()
    if (properties and 0x02 != 0) props.add(GattProperty.READ)
    if (properties and 0x04 != 0) props.add(GattProperty.WRITE_WITHOUT_RESPONSE)
    if (properties and 0x08 != 0) props.add(GattProperty.WRITE)
    if (properties and 0x10 != 0) props.add(GattProperty.NOTIFY)
    if (properties and 0x20 != 0) props.add(GattProperty.INDICATE)

    return GattCharacteristic(
        id = this.UUID.UUIDString.toFullBluetoothUuid(),
        properties = props,
        service = service,
    )
}
