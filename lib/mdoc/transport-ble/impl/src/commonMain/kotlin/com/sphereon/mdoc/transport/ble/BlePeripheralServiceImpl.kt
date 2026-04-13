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

package com.sphereon.mdoc.transport.ble

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.log.LogManager
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.BleErrors
import com.sphereon.data.link.ble.CancelledError
import com.sphereon.data.link.ble.model.GattCharacteristic
import com.sphereon.data.link.ble.model.GattProperty
import com.sphereon.data.link.ble.model.GattService
import com.sphereon.data.link.ble.peripheral.BlePlatformPeripheral
import com.sphereon.mdoc.MdocRole
import kotlinx.coroutines.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Implementation of BLE peripheral mode service with explicit dependencies.
 *
 * This implementation does NOT depend on EngagementInstance. All dependencies
 * are passed explicitly in the constructor.
 *
 * @param blePlatformPeripheral Platform-specific BLE peripheral
 * @param logManager Log manager for logging
 * @param role The role of this party (MDOC or MDOC_READER)
 * @param characteristics The BLE service characteristics to use
 * @param serviceUuid The service UUID to use for advertising and GATT service
 * @param incomingDataChannel The incoming data channel for receiving messages
 * @param outgoingDataChannel The outgoing data channel for sending messages
 * @param hkdfProvider Provider for HKDF-SHA256 (platform-specific)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlePeripheralServiceImpl", exact = true)
class BlePeripheralServiceImpl(
    private val blePlatformPeripheral: BlePlatformPeripheral,
    private val logManager: LogManager,
    override val role: MdocRole,
    private val characteristics: MdocBleServiceCharacteristics,
    private val serviceUuid: Uuid,
    private val _incomingDataChannel: BleIncomingDataChannel,
    private val _outgoingDataChannel: BleOutgoingDataChannel,
    private val hkdfProvider: HkdfProvider
) : BlePeripheralService {

    private val log = logManager.withTag("BlePeripheralService")

    private var onErrorCallback: ((Throwable) -> Unit)? = null
    private var onClosedCallback: (() -> Unit)? = null

    private var identValue: ByteArray? = null
    private var createdService: GattService? = null
    private var receivedDeviceEngagement: ByteArray? = null

    // Deferreds for tracking connection, notification subscriptions, and START command
    // These are created during awaitAdvertising() and completed by event listeners
    private val connectionDeferred = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val subscriptionsReady = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val startCommandReceived = kotlinx.coroutines.CompletableDeferred<Unit>()

    // Track which characteristics have been subscribed to
    private val subscribedCharacteristics = mutableSetOf<Uuid>()

    // Persistent listener to capture BLE events during the entire peripheral lifecycle
    // This includes connection state changes, descriptor writes, characteristic writes, and DeviceEngagement exchange
    private val peripheralEventListener = object : com.sphereon.data.link.ble.client.BleEvent.Listener {
        override fun onCharacteristicChanged(event: com.sphereon.data.link.ble.client.BleEvent.CharacteristicChanged) {
            // Any characteristic write proves a central is connected.
            // On iOS, we don't get a "central connected" callback at the BLE link layer,
            // so we detect connection when the central performs any GATT operation.
            if (!connectionDeferred.isCompleted) {
                log.info("Central client connected (detected via characteristic write to ${event.characteristic.id})")
                connectionDeferred.complete(Unit)
            }

            // Per ISO 18013-5, central writes commands to State characteristic:
            // - 0x01 = START command (begin session)
            // - 0x02 = END command (terminate session)
            if (event.characteristic.id == characteristics.state) {
                if (event.value.isNotEmpty()) {
                    when (event.value[0]) {
                        0x01.toByte() -> {
                            log.info("Received START command (0x01) from central on State characteristic")
                            startCommandReceived.complete(Unit)
                        }
                        0x02.toByte() -> {
                            // Per ISO 18013-5, END command indicates reader wants to terminate session
                            log.info("Received END command (0x02) from central - reader initiated session termination")
                            // Notify transport that the session should be closed
                            onClosedCallback?.invoke()
                        }
                        else -> {
                            // For reverse engagement, holder writes DeviceEngagement to State characteristic
                            log.info("Data received on State characteristic (${event.value.size} bytes, first byte=0x${event.value[0].toString(16)})")
                            receivedDeviceEngagement = event.value
                        }
                    }
                } else {
                    log.warn("Empty data received on State characteristic")
                }
            }

            // If we receive data on Client2Server, the reader is sending the device request
            // without following the full ISO 18013-5 handshake (subscribe + START command).
            // We should treat this as "ready to transfer" to handle non-compliant readers.
            if (event.characteristic.id == characteristics.client2Server) {
                if (!subscriptionsReady.isCompleted) {
                    log.info("Received data on Client2Server before subscriptions - treating as ready")
                    subscriptionsReady.complete(Unit)
                }
                if (!startCommandReceived.isCompleted) {
                    log.info("Received data on Client2Server before START command - treating as started")
                    startCommandReceived.complete(Unit)
                }
            }
        }

        override fun onConnectionStateChanged(event: com.sphereon.data.link.ble.client.BleEvent.ConnectionStateChanged) {
            log.info("onConnectionStateChanged: newState=${event.newState}, device=${event.deviceAddress}")
            when (event.newState) {
                2 -> { // STATE_CONNECTED
                    log.info("Central client connected to peripheral (via ConnectionStateChanged event)")
                    if (!connectionDeferred.isCompleted) {
                        connectionDeferred.complete(Unit)
                    }
                }

                0 -> { // STATE_DISCONNECTED
                    log.info("Central client disconnected from peripheral")
                    // Notify transport that the connection was closed by the reader
                    onClosedCallback?.invoke()
                }
            }
        }

        override fun onDescriptorWrite(event: com.sphereon.data.link.ble.client.BleEvent.DescriptorWrite) {
            // Per ISO 18013-5, central subscribes to State and Server2Client characteristics
            // Track subscriptions so we know when we're ready
            log.info("*** DESCRIPTOR WRITE EVENT: characteristic=${event.characteristic.id}, device=${event.deviceAddress}, status=${event.status}")
            log.info("    Expected State=${characteristics.state}, Server2Client=${characteristics.server2Client}")
            subscribedCharacteristics.add(event.characteristic.id)
            log.info("    Subscribed characteristics so far: $subscribedCharacteristics")

            // Check if both required characteristics are subscribed
            val stateSubscribed = subscribedCharacteristics.contains(characteristics.state)
            val server2ClientSubscribed = subscribedCharacteristics.contains(characteristics.server2Client)
            log.info("    stateSubscribed=$stateSubscribed, server2ClientSubscribed=$server2ClientSubscribed")

            if (stateSubscribed && server2ClientSubscribed) {
                log.info("Both required characteristics subscribed, peripheral is ready")
                subscriptionsReady.complete(Unit)
            }
        }

        // Implement other required methods as no-ops
        override fun onServicesDiscovered(event: com.sphereon.data.link.ble.client.BleEvent.ServicesDiscovered) {}
        override fun onMtuChanged(event: com.sphereon.data.link.ble.client.BleEvent.MtuChanged) {}
        override fun onCharacteristicRead(event: com.sphereon.data.link.ble.client.BleEvent.CharacteristicRead) {}
        override fun onCharacteristicWrite(event: com.sphereon.data.link.ble.client.BleEvent.CharacteristicWrite) {}
        override fun onDescriptorRead(event: com.sphereon.data.link.ble.client.BleEvent.DescriptorRead) {}
        override fun onNotification(event: com.sphereon.data.link.ble.client.BleEvent.Notification) {}
        override fun onScanStarted(event: com.sphereon.data.link.ble.client.BleEvent.ScanStarted) {}
        override fun onScanStopped(event: com.sphereon.data.link.ble.client.BleEvent.ScanStopped) {}
        override fun onScanResult(event: com.sphereon.data.link.ble.client.BleEvent.ScanResult) {}
        override fun onDeviceFound(event: com.sphereon.data.link.ble.client.BleEvent.DeviceFound) {}
        override fun onBleError(event: com.sphereon.data.link.ble.client.BleEvent.Error) {}
    }

    override fun setCallbacks(
        onError: (error: Throwable) -> Unit,
        onClosed: () -> Unit
    ) {
        this.onErrorCallback = onError
        this.onClosedCallback = onClosed
    }

    override suspend fun awaitAdvertising(serviceUuid: Uuid): IdkResult<Unit, BleError> {
        log.info("Starting advertising with service UUID: $serviceUuid")

        return try {
            // Stop any previous advertising first to ensure clean state
            // This is critical to prevent readers from connecting to old GATT servers
            log.debug("Stopping any previous advertising before starting new one")
            blePlatformPeripheral.stopAdvertising()

            // Longer delay to allow GATT server to fully clean up and readers to disconnect
            // Android readers may stay connected based on MAC address even after advertising stops
            // This ensures a clean slate before starting new GATT server
            kotlinx.coroutines.delay(500)

            // Wait for Bluetooth power on (mainly for iOS)
            awaitPowerOn()

            // Create GATT service with characteristics
            createdService = createGattService()
            log.info("Created GATT service: ${createdService?.id}")

            // CRITICAL: Register event listeners BEFORE adding service to catch early connections
            // iOS devices with cached connections may reconnect immediately when GATT server opens,
            // which happens inside addService(). We must be listening before that happens.
            log.info("*** REGISTERING BLE EVENT LISTENERS ***")
            log.info("  - peripheralEventListener for connection/subscriptions/START")
            log.info("  - incomingDataChannel (${_incomingDataChannel::class.simpleName}) for Client2Server writes")
            log.info("  - outgoingDataChannel (${_outgoingDataChannel::class.simpleName}) for Server2Client notifications")
            blePlatformPeripheral.addBleEventListener(peripheralEventListener)
            blePlatformPeripheral.addBleEventListener(_incomingDataChannel as com.sphereon.data.link.ble.client.BleEvent.Listener)
            blePlatformPeripheral.addBleEventListener(_outgoingDataChannel as com.sphereon.data.link.ble.client.BleEvent.Listener)
            log.info("*** BLE EVENT LISTENERS REGISTERED - total: ${blePlatformPeripheral.getBleEventListeners().size} ***")

            // Register the service with the BLE peripheral
            // This is crucial for FakeBlePlatformPeripheral to know about the service
            // Note: During migration, the old peripheral service initializes data channels,
            // but the NEW modular transport registers the GATT service
            // WARNING: iOS devices may connect during this call if they have cached our device
            blePlatformPeripheral.addService(createdService!!)
            log.info("GATT service registered with BLE peripheral by modular transport")

            // Set the service on the outgoing data channel
            // In peripheral mode, we create the service (not discover it), so set it now
            (_outgoingDataChannel as? BleOutgoingDataChannelImpl)?.setServiceAndCharacteristics(createdService!!)
            log.info("Service and characteristics set on outgoing channel")

            // Start platform-specific advertising
            platformAdvertiseService(serviceUuid)

            log.info("Advertising started")
            Unit.asOkResult()
        } catch (e: CancellationException) {
            // Return cancellation as a result - this is expected when racing connections
            log.info("Advertising was cancelled")
            BleErrors.cancelled("Advertising was cancelled", e).asErrorResult()
        } catch (e: Throwable) {
            log.error("Failed to start advertising", exception = e)
            BleErrors.connectionFailed("Failed to start advertising: ${e.message}", e).asErrorResult()
        }
    }

    override suspend fun awaitConnection(senderKey: CoseKeyType): IdkResult<GattService, BleError> {
        log.info("Waiting for connection from central device")

        return try {
            // Calculate ident value from sender key
            calculateAndSetIdentValue(senderKey)

            // Wait for central to connect (platform-specific)
            val service = platformWaitForConnection()

            log.info("Connection established")
            service.asOkResult()
        } catch (e: CancellationException) {
            // Return cancellation as a result - this is expected when racing connections
            // (e.g., when another transport wins the connection race)
            log.info("Connection waiting was cancelled")
            BleErrors.cancelled("Connection waiting was cancelled", e).asErrorResult()
        } catch (e: Throwable) {
            log.error("Failed to establish connection", exception = e)
            BleErrors.connectionFailed("Failed to establish connection: ${e.message}", e).asErrorResult()
        }
    }

    override suspend fun start(service: GattService): IdkResult<Unit, BleError> {
        log.info("Starting data transfer")
        // In peripheral mode, the connection is already established
        // This is a no-op, just return success
        return Unit.asOkResult()
    }

    /**
     * Read DeviceEngagement from State characteristic for reverse engagement.
     * Per ISO 18013-7, in reverse engagement the holder writes its DeviceEngagement
     * to the State characteristic and the reader reads it to get the holder's ephemeral key.
     *
     * @return DeviceEngagement bytes or error
     */
    override suspend fun readDeviceEngagement(): IdkResult<ByteArray, BleError> {
        log.info("Waiting to read DeviceEngagement from State characteristic...")

        // Check if already received (captured by persistent listener)
        if (receivedDeviceEngagement != null) {
            log.info("DeviceEngagement already received (${receivedDeviceEngagement!!.size} bytes)")
            return receivedDeviceEngagement!!.asOkResult()
        }

        // Wait for DeviceEngagement to arrive
        try {
            kotlinx.coroutines.withTimeout(30000) {
                while (receivedDeviceEngagement == null) {
                    kotlinx.coroutines.delay(100)
                }
            }
            log.info("DeviceEngagement received after waiting (${receivedDeviceEngagement!!.size} bytes)")
            return receivedDeviceEngagement!!.asOkResult()
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            log.error("Timeout waiting for DeviceEngagement")
            return BleErrors.readCharacteristicFailed("Timeout waiting for DeviceEngagement").asErrorResult()
        }
    }

    override fun incomingChannel(): BleIncomingDataChannel {
        return _incomingDataChannel
    }

    override fun outgoingChannel(): BleOutgoingDataChannel {
        return _outgoingDataChannel
    }

    /**
     * Create a GATT service with the required characteristics for mDoc transfer.
     * Per ISO 18013-5, characteristics are configured as follows:
     * - State: Notify, Write Without Response
     * - Client2Server: Write Without Response
     * - Server2Client: Notify
     */
    private fun createGattService(): GattService {
        return GattService(
            id = serviceUuid,
            characteristics = listOf(
                // State characteristic (notify and write without response)
                // Per ISO 18013-5: Notify, Write Without Response
                // Central writes 0x01 to start data transfer
                GattCharacteristic(
                    id = characteristics.state,
                    properties = setOf(GattProperty.NOTIFY, GattProperty.WRITE_WITHOUT_RESPONSE),
                    service = null
                ),
                // Client to Server (central writes to peripheral)
                // Per ISO 18013-5: Write Without Response
                GattCharacteristic(
                    id = characteristics.client2Server,
                    properties = setOf(GattProperty.WRITE_WITHOUT_RESPONSE),
                    service = null
                ),
                // Server to Client (peripheral notifies central)
                // Per ISO 18013-5: Notify (only)
                GattCharacteristic(
                    id = characteristics.server2Client,
                    properties = setOf(GattProperty.NOTIFY),
                    service = null
                )
            )
        )
    }

    /**
     * Calculate the ident value from the sender key using HKDF.
     */
    private suspend fun calculateAndSetIdentValue(senderKey: CoseKeyType) {
        val coseKey = when (senderKey) {
            is CoseKey -> senderKey
            else -> throw IllegalArgumentException("Unsupported CoseKeyType: ${senderKey::class.simpleName}")
        }

        val ikm = CborEncodedItem.fromData(coseKey.toCborStructure()).encodeCbor()
        val info = "BLEIdent".encodeToByteArray()
        val salt = byteArrayOf()

        identValue = hkdfProvider.hkdfSha256(ikm, salt, info, 16)
        log.debug("Calculated ident value")
    }

    /**
     * Wait for Bluetooth power on (mainly for iOS).
     * On platforms that don't require this, this is a no-op.
     */
    private suspend fun awaitPowerOn() {
        // Platform-specific implementations can override this
        // For now, we assume power is on
        log.debug("Bluetooth power on (no-op)")
    }

    /**
     * Platform-specific implementation to advertise the BLE service.
     * This calls the platform peripheral's startAdvertising method.
     */
    private suspend fun platformAdvertiseService(serviceUuid: Uuid) {
        log.info("Starting BLE advertising via platform peripheral")

        val result = blePlatformPeripheral.startAdvertising(serviceUuid)
        if (result.isErr) {
            throw IllegalStateException("Failed to start advertising: ${result.error}")
        }

        log.info("BLE advertising started successfully")
    }

    /**
     * Platform-specific implementation to wait for connection.
     *
     * This waits for:
     * 1. Central to connect (tracked by peripheralEventListener registered in awaitAdvertising)
     * 2. Central to subscribe to both State and Server2Client characteristics
     * 3. Central to write 0x01 (START command) to State characteristic
     *
     * Per ISO 18013-5, after subscribing to notifications, the central writes 0x01
     * to the State characteristic to signal "start data transfer".
     */
    private suspend fun platformWaitForConnection(): GattService {
        // Wait for client to connect (deferred completed by peripheralEventListener)
        log.debug("Waiting for central client to connect...")
        connectionDeferred.await()
        log.info("Central client connection detected, waiting for notification subscriptions...")

        // Wait for central to subscribe to notifications (deferred completed by peripheralEventListener)
        subscriptionsReady.await()
        log.info("Central has subscribed to notifications, waiting for START command...")

        // Wait for central to write START command (0x01) to State characteristic
        // This signals the central is ready to begin data transfer
        startCommandReceived.await()
        log.info("Received START command from central, data transfer can begin")

        return createdService ?: throw IllegalStateException("Service not created")
    }

    override fun close() {
        log.info("Closing BLE peripheral service")

        try {
            // Remove the persistent event listener first to stop receiving events
            blePlatformPeripheral.removeBleEventListener(peripheralEventListener)

            // Remove event listeners for data channels
            blePlatformPeripheral.removeBleEventListener(_incomingDataChannel as com.sphereon.data.link.ble.client.BleEvent.Listener)
            blePlatformPeripheral.removeBleEventListener(_outgoingDataChannel as com.sphereon.data.link.ble.client.BleEvent.Listener)

            // Close data channels
            (_incomingDataChannel as? AutoCloseable)?.close()
            (_outgoingDataChannel as? AutoCloseable)?.close()

            // NOTE: Do NOT invoke onClosedCallback here.
            // The callback should ONLY be invoked when the REMOTE device disconnects
            // (handled in peripheralEventListener.onConnectionStateChanged).
            // Invoking it here would cause recursive loops when the transport
            // calls close() in response to a disconnect.
        } catch (e: Throwable) {
            log.error("Error in close", exception = e)
        }

        // Close platform peripheral
        blePlatformPeripheral.close()
    }
}

/**
 * Platform-specific HKDF provider factory.
 * Each platform provides its own implementation.
 */
expect fun createPlatformHkdfProvider(): HkdfProvider
