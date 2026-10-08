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

package com.sphereon.mdoc.transport.ble

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.log.LogManager
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.BleErrors
import com.sphereon.data.link.ble.NotificationFailedError
import com.sphereon.data.link.ble.ScanError
import com.sphereon.data.link.ble.client.BleEvent
import com.sphereon.data.link.ble.client.BlePlatformClient
import com.sphereon.data.link.ble.client.cmd.ScanDevicesArgs
import com.sphereon.data.link.ble.model.BleDevice
import com.sphereon.data.link.ble.model.GattService
import com.sphereon.data.link.ble.model.HasUuidId
import com.sphereon.data.link.ble.model.getServiceByCharacteristicId
import com.sphereon.data.link.ble.model.toUuidId
import com.sphereon.mdoc.MdocRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Implementation of BLE central mode service with explicit dependencies.
 *
 * This implementation does NOT depend on EngagementInstance. All dependencies
 * are passed explicitly in the constructor.
 *
 * @param blePlatformClient Platform-specific BLE client
 * @param logManager Log manager for logging
 * @param role The role of this party (MDOC or MDOC_READER)
 * @param characteristics The BLE service characteristics to use
 * @param incomingChannel The incoming data channel that needs to receive BLE events (optional)
 * @param outgoingChannel The outgoing data channel (optional, currently unused but kept for symmetry)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BleCentralServiceImpl", exact = true)
class BleCentralServiceImpl(
    private val blePlatformClient: BlePlatformClient,
    private val logManager: LogManager,
    override val role: MdocRole,
    private val characteristics: MdocBleServiceCharacteristics,
    private val incomingChannel: BleIncomingDataChannel? = null,
    private val outgoingChannel: BleOutgoingDataChannel? = null,
) : BleCentralService {
    private val log = logManager.withTag("BleCentralService")

    private var onErrorCallback: ((Throwable) -> Unit)? = null
    private var onClosedCallback: (() -> Unit)? = null

    /**
     * The service UUID we scanned for. This is stored during awaitConnecting()
     * and used in awaitConnected() to find the correct service among multiple
     * services that may have the same characteristic UUIDs.
     */
    private var scannedServiceUuid: Uuid? = null

    /**
     * Track whether we've already invoked the onClosedCallback to avoid double invocation
     * (once from remote disconnect, once from explicit close() call).
     */
    private var closedCallbackInvoked = false

    // Coroutine scope for event collection
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Job tracking event collection
    private var eventCollectionJob: Job? = null

    init {
        // Start collecting BLE events to detect remote disconnect and END commands
        eventCollectionJob =
            eventScope.launch {
                blePlatformClient.bleEvents.collect { event ->
                    when (event) {
                        is BleEvent.ConnectionStateChanged -> {
                            log.info("BLE connection state changed: newState=${event.newState}, device=${event.deviceAddress}, status=${event.status}")
                            when (event.newState) {
                                0 -> { // STATE_DISCONNECTED
                                    log.info("DISCONNECT DETECTED: Remote peripheral disconnected (closedCallbackInvoked=$closedCallbackInvoked)")
                                    // Invoke onClosedCallback to signal that the remote device initiated disconnect
                                    // This will cause the transport to call markClosed() and trigger the success flow
                                    if (!closedCallbackInvoked) {
                                        closedCallbackInvoked = true
                                        log.info("Invoking onClosedCallback to signal transport closure")
                                        onClosedCallback?.invoke()
                                        log.info("onClosedCallback completed")
                                    } else {
                                        log.debug("onClosedCallback already invoked, skipping")
                                    }
                                }

                                2 -> { // STATE_CONNECTED
                                    log.debug("Connection state: CONNECTED")
                                }

                                else -> {
                                    log.debug("Connection state: ${event.newState}")
                                }
                            }
                        }

                        is BleEvent.CharacteristicChanged -> {
                            // Per ISO 18013-5, the peripheral (reader) can send END command (0x02) on State characteristic
                            // to terminate the session
                            if (event.characteristic.id == characteristics.state) {
                                if (event.value.isNotEmpty() && event.value[0] == 0x02.toByte()) {
                                    log.info("Received END command (0x02) from peripheral - reader initiated session termination")
                                    if (!closedCallbackInvoked) {
                                        closedCallbackInvoked = true
                                        onClosedCallback?.invoke()
                                    }
                                } else {
                                    log.debug("State characteristic notification: ${event.value.size} bytes")
                                }
                            }
                        }

                        else -> {}
                    }
                }
            }
        log.info("BLE event collection started for remote disconnect detection")
    }

    override fun setCallbacks(
        onError: (error: Throwable) -> Unit,
        onClosed: () -> Unit,
    ) {
        this.onErrorCallback = onError
        this.onClosedCallback = onClosed
    }

    override suspend fun awaitConnecting(
        peripheralUuid: Uuid,
        scanSettings: ScanDevicesArgs,
    ): IdkResult<BleDevice, ScanError> {
        log.info("Scanning for peripheral with UUID: $peripheralUuid")

        // Store the service UUID we're scanning for - this will be used in awaitConnected()
        // to find the correct service among potentially multiple services with the same characteristics
        scannedServiceUuid = peripheralUuid
        log.info("Stored scanned service UUID: $peripheralUuid")

        // Use the provided scan settings, or create default with peripheral UUID
        val scanResult = blePlatformClient.scan(scanSettings)

        if (scanResult.isErr) {
            log.error("Failed to scan for device: ${scanResult.error}")
            return scanResult.error.asErrorResult()
        }

        val devices = scanResult.value
        if (devices.isEmpty()) {
            log.error("No devices found with UUID $peripheralUuid")
            return BleErrors.scanFailed("No devices found with UUID $peripheralUuid").asErrorResult()
        }

        if (devices.size > 1) {
            log.warn("Multiple devices found (${devices.size}), using first one")
        }

        val device = devices.first()
        log.info("Found peripheral: ${device.address}")
        return device.asOkResult()
    }

    override suspend fun awaitConnected(
        senderKey: CoseKeyType,
        peripheral: BleDevice,
    ): IdkResult<GattService, BleError> {
        log.info("Connecting to peripheral: ${peripheral.address}")

        // Step 1: Connect to peripheral
        val connectResult = blePlatformClient.connect(device = peripheral)
        if (connectResult.isErr) {
            log.error("Failed to connect to peripheral: ${connectResult.error}")
            return connectResult.error.asErrorResult()
        }
        log.info("Connected to peripheral")

        // Step 2: Set MTU to maximum (512 bytes for data + 3 bytes overhead)
        val mtuResult = blePlatformClient.setMtu(515)
        if (mtuResult.isErr) {
            log.error("Failed to set MTU: ${mtuResult.error}")
            return mtuResult.error.asErrorResult()
        }
        log.info("MTU set to ${mtuResult.value}")

        // Step 3: Discover services
        val servicesResult = blePlatformClient.discoverServices()
        if (servicesResult.isErr) {
            log.error("Failed to discover services: ${servicesResult.error}")
            return servicesResult.error.asErrorResult()
        }
        val services = servicesResult.value
        log.info("Discovered ${services.size} services")

        // Step 4: Find the correct mdoc service by its UUID (stored during awaitConnecting)
        // IMPORTANT: We must use the service UUID we scanned for, NOT just find any service
        // with the state characteristic. The reader may have multiple services with identical
        // characteristic UUIDs, and we need the specific service from the DeviceEngagement.
        val targetServiceUuid = scannedServiceUuid
        log.info("Looking for service UUID: $targetServiceUuid (scanned service)")

        val service =
            if (targetServiceUuid != null) {
                // Find service by UUID (preferred - more reliable)
                services.find { it.id == targetServiceUuid }
                    ?: services.getServiceByCharacteristicId(characteristics.state) // Fallback
            } else {
                // No stored UUID, fallback to characteristic-based lookup
                log.warn("No scanned service UUID stored, falling back to characteristic-based lookup")
                services.getServiceByCharacteristicId(characteristics.state)
            }

        if (service == null) {
            log.error("Service not found for UUID $targetServiceUuid or characteristic ${characteristics.state}")
            return BleErrors.readCharacteristicFailed("Remote mdoc service not found").asErrorResult()
        }
        log.info(
            "Found service: ${service.id} (matched by ${if (service.id == targetServiceUuid) {
                "UUID"
            } else {
                "characteristic"
            }})"
        )

        // Step 5: Discover characteristics
        val characteristicIds =
            listOfNotNull(
                characteristics.state,
                characteristics.client2Server,
                characteristics.server2Client,
                characteristics.ident,
            ).map { it.toUuidId() }

        val charsResult = blePlatformClient.discoverServiceCharacteristics(service, characteristicIds)
        if (charsResult.isErr) {
            log.error("Failed to discover characteristics: ${charsResult.error}")
            return charsResult.error.asErrorResult()
        }
        log.info("Discovered ${charsResult.value.size} characteristics")

        // Step 6: Subscribe to notifications (state and server2Client)
        val notificationCharacteristics =
            setOf(
                characteristics.state.toUuidId(),
                characteristics.server2Client.toUuidId(),
            )

        val subscribeResult = subscribeToCharacteristics(service, notificationCharacteristics)
        if (subscribeResult.isErr) {
            log.error("Failed to subscribe to notifications: ${subscribeResult.error}")
            return subscribeResult.error.asErrorResult()
        }
        log.info("Subscribed to notifications")

        // TODO: If role is MDOC, calculate reader ident (requires HKDF)
        // For now, we skip this as it's not always needed

        log.info("awaitConnected() complete")
        return service.asOkResult()
    }

    override suspend fun start(service: GattService): IdkResult<Unit, BleError> {
        log.info("Starting data transfer by writing to state characteristic")

        // Start event collection on incoming channel BEFORE starting data transfer
        // This ensures the channel receives notification events from the peripheral
        if (incomingChannel is BleIncomingDataChannelImpl) {
            incomingChannel.startEventCollection(blePlatformClient.bleEvents, eventScope)
            log.info("Incoming channel event collection started")
        } else if (incomingChannel != null) {
            log.warn("Incoming channel is not BleIncomingDataChannelImpl, notifications may not work")
        }

        // Set service and characteristics on outgoing channel for central client mode
        // In central mode, we write to characteristics instead of notifying
        if (outgoingChannel != null && outgoingChannel is BleOutgoingDataChannelImpl) {
            outgoingChannel.setServiceAndCharacteristics(service)
            log.info("Service and characteristics set on outgoing channel")
        }

        // Write START command (0x01) to state characteristic
        val writeResult =
            blePlatformClient.writeCharacteristic(
                service = service,
                characteristic = characteristics.state.toUuidId(),
                value = byteArrayOf(MdocReaderServiceChars.Command.START.toByte()),
            )

        if (writeResult.isErr) {
            log.error("Failed to write to state characteristic: ${writeResult.error}")
            return writeResult.error.asErrorResult()
        }

        log.info("Data transfer started")
        return Unit.asOkResult()
    }

    /**
     * Write DeviceEngagement to State characteristic for reverse engagement.
     * Per ISO 18013-7, in reverse engagement the holder writes its DeviceEngagement
     * to the State characteristic so the reader can extract the holder's ephemeral key.
     *
     * @param service The GATT service
     * @param deviceEngagement The holder's device engagement to send
     * @return Success or error
     */
    override suspend fun writeDeviceEngagement(
        service: GattService,
        deviceEngagement: ByteArray,
    ): IdkResult<Unit, BleError> {
        log.info("Writing DeviceEngagement to State characteristic (${deviceEngagement.size} bytes)")

        val writeResult =
            blePlatformClient.writeCharacteristic(
                service = service,
                characteristic = characteristics.state.toUuidId(),
                value = deviceEngagement,
            )

        if (writeResult.isErr) {
            log.error("Failed to write DeviceEngagement: ${writeResult.error}")
            return writeResult.error.asErrorResult()
        }

        log.info("DeviceEngagement written successfully")
        return Unit.asOkResult()
    }

    private suspend fun subscribeToCharacteristics(
        service: GattService,
        characteristics: Set<HasUuidId>,
    ): IdkResult<Unit, NotificationFailedError> {
        for (characteristic in characteristics) {
            val result = blePlatformClient.enableNotifications(service, characteristic)
            if (result.isErr) {
                log.error("Failed to enable notifications for $characteristic: ${result.error}")
                return result.error.asErrorResult()
            }
        }
        return Unit.asOkResult()
    }

    override fun close() {
        log.info("Closing BLE central service (closedCallbackInvoked=$closedCallbackInvoked)")

        // Cancel event collection scope to stop receiving events during close
        eventCollectionJob?.cancel()
        eventCollectionJob = null
        eventScope.cancel()

        // Only invoke callback if not already invoked (e.g., by remote disconnect)
        // This prevents double callback invocation
        if (!closedCallbackInvoked) {
            closedCallbackInvoked = true
            try {
                onClosedCallback?.invoke()
            } catch (expected: Throwable) {
                log.error("Error in onClosed callback", exception = expected)
            }
        } else {
            log.debug("onClosedCallback already invoked by remote disconnect, skipping")
        }

        blePlatformClient.close()
    }
}
