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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.BleErrors
import com.sphereon.data.link.ble.client.cmd.ScanDevicesArgs
import com.sphereon.data.link.ble.model.GattService
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.EngagementData
import com.sphereon.mdoc.engagement.MdocEngagementState
import com.sphereon.mdoc.transport.AbstractMdocTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * BLE central client transfer implementation.
 *
 * In central mode, this device scans for and connects to BLE peripherals.
 * This is used when:
 * - Reader (MDOC_READER) scans for and connects to holder's peripheral
 * - Holder (MDOC) scans for and connects to reader's peripheral (reverse role mode)
 *
 * ## Lifecycle
 *
 * 1. **Create** transfer with services and data channels
 * 2. **Open** - Scan for peripheral, connect, start services
 * 3. **Exchange** - Send and receive data via data channels
 * 4. **Close** - Clean up services and channels
 *
 * ## Thread Safety
 *
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("uses", exact = true)
@OptIn(ExperimentalObjCName::class)
@ObjCName("BleCentralClientTransfer", exact = true)
 * This class uses a mutex to protect state transitions during open/close.
 * Message send/receive methods can be called concurrently.
 *
 * @param connectionMethod The BLE connection method
 * @param execution Session execution context
 * @param bleCentralService The BLE central service for scanning/connecting
 * @param incomingChannel Data channel for receiving messages
 * @param outgoingChannel Data channel for sending messages
 * @param role The role of this party (MDOC or MDOC_READER)
 */
class BleCentralClientTransport(
    connectionMethod: BleConnectionMethod,
    execution: SessionExecution,
    private val bleCentralService: BleCentralService,
    private val incomingChannel: BleIncomingDataChannel,
    private val outgoingChannel: BleOutgoingDataChannel,
    override val role: MdocRole
) : AbstractMdocTransport<Uuid>(connectionMethod, execution) {

    private val log = execution.log
    private val mutex = Mutex()

    init {
        // Note: We don't check connectionMethod.options.centralClientMode here because
        // in forward engagement with role reversal, the reader may be acting as central
        // even though the device engagement (holder's announcement) says peripheralServerMode=true.
        // The BleTransportFactory is responsible for choosing the correct transfer type
        // based on role and announced modes.

        // Register for BLE disconnect events to update engagement state
        bleCentralService.setCallbacks(
            onError = { error ->
                log.error("BLE central error: ${error.message}", exception = error)
                // Don't close here - let the transport handle errors through normal flow
            },
            onClosed = {
                log.info("onClosed callback: BLE connection closed by remote, state before=${_engagementState.value}")
                // Mark the transport as closed so engagement state becomes DISCONNECTED
                markClosed()
                log.info("markClosed() completed, state after=${_engagementState.value}")
            }
        )
    }

    override suspend fun open(senderKey: CoseKeyType, id: Uuid, engagementData: EngagementData?): IdkResult<Uuid, BleError> {
        log.info("open() called for BLE central client mode, scanning for UUID: $id")

        mutex.withLock {
            check(_engagementState.value == MdocEngagementState.INIT) {
                "open() called in wrong state: ${_engagementState.value}. Expected ${MdocEngagementState.INIT}"
            }
            _engagementData = engagementData

            try {
                // Step 1: Scan for peripheral with the service UUID
                _engagementState.value = MdocEngagementState.BLE_SCANNING
                log.info("Scanning for peripheral with UUID: $id")

                val scanSettings = ScanDevicesArgs(
                    timeout = 90.seconds,
                    maxResults = 1,
                    requestId = id
                ) {
                    services = listOf(id)
                }

                val deviceResult = bleCentralService.awaitConnecting(id, scanSettings)
                if (deviceResult.isErr) {
                    log.error("Failed to find peripheral: ${deviceResult.error.message}")
                    _engagementState.value = MdocEngagementState.ERROR
                    return deviceResult.error.asErrorResult()
                }
                val device = deviceResult.value

                log.info("Found peripheral: ${device.address}")

                // Step 2: Connect to peripheral and discover services
                _engagementState.value = MdocEngagementState.CONNECTING
                log.info("Connecting to peripheral...")

                val serviceResult = bleCentralService.awaitConnected(senderKey, device)
                if (serviceResult.isErr) {
                    log.error("Failed to connect to peripheral: ${serviceResult.error.message}")
                    _engagementState.value = MdocEngagementState.ERROR
                    return serviceResult.error.asErrorResult()
                }
                val service = serviceResult.value

                log.info("Connected to peripheral, service: ${service.id}")

                // Step 3: Start data transfer
                _engagementState.value = MdocEngagementState.CONNECTED
                log.info("Starting data transfer...")

                val startResult = bleCentralService.start(service)
                if (startResult.isErr) {
                    log.error("Failed to start data transfer: ${startResult.error.message}")
                    _engagementState.value = MdocEngagementState.ERROR
                    return startResult.error.asErrorResult()
                }

                log.info("BLE central client transfer opened successfully")
                markOpen()

                return id.asOkResult()

            } catch (e: CancellationException) {
                // Propagate cancellation - this is expected when the coroutine is cancelled
                log.info("BLE central client transfer opening was cancelled")
                _engagementState.value = MdocEngagementState.DISCONNECTED
                throw e
            } catch (e: Throwable) {
                log.error("Error opening BLE central client transfer", exception = e)
                _engagementState.value = MdocEngagementState.ERROR
                return BleErrors.connectionFailed(
                    "Failed to open BLE central client transfer: ${e.message}",
                    e
                ).asErrorResult()
            }
        }
    }

    override suspend fun messageReceiveBlocking(): ByteArray {
        requireOpen()
        log.debug("Receiving message...")

        return try {
            val message = incomingChannel.receiveRaw()
            log.debug("Received ${message.size} bytes")
            message
        } catch (e: CancellationException) {
            // Propagate cancellation without failing the transport
            log.debug("Message receive was cancelled")
            throw e
        } catch (e: Throwable) {
            if (_engagementState.value == MdocEngagementState.DISCONNECTED) {
                log.error("Transport was closed whilst awaiting a message")
                throw IllegalStateException("Transport was closed whilst awaiting a message", e)
            }
            log.error("Error receiving message", exception = e)
            mutex.withLock {
                failTransport(e)
            }
            throw e
        }
    }

    override suspend fun messageSendBlocking(message: ByteArray) {
        requireOpen()
        log.debug("Sending ${message.size} bytes...")

        try {
            if (message.isEmpty()) {
                // Send end message to signal completion
                log.info("Sending end message")
                outgoingChannel.sendEndMessage()
            } else {
                outgoingChannel.sendRaw(message)
                log.debug("Message sent successfully")
            }
        } catch (e: CancellationException) {
            // Propagate cancellation without failing the transport
            log.debug("Message send was cancelled")
            throw e
        } catch (e: Throwable) {
            if (_engagementState.value == MdocEngagementState.DISCONNECTED) {
                log.error("Transport was closed whilst sending a message")
                throw IllegalStateException("Transport was closed whilst sending a message", e)
            }
            log.error("Error sending message", exception = e)
            mutex.withLock {
                failTransport(e)
            }
            throw e
        }
    }

    /**
     * Mark the transport as failed and clean up.
     */
    private fun failTransport(error: Throwable) {
        check(mutex.isLocked) { "failTransport called without holding lock" }

        if (_engagementState.value.order >= MdocEngagementState.ERROR.order) {
            // Already in error state
            return
        }

        log.error("Failing transport", exception = error)
        _engagementState.value = MdocEngagementState.ERROR

        try {
            bleCentralService.close()
        } catch (closeError: Throwable) {
            log.error("Error closing BLE service during failure", exception = closeError)
        }
    }

    override fun getIncomingDataChannel(): Any {
        return incomingChannel
    }

    override fun getOutgoingDataChannel(): Any {
        return outgoingChannel
    }

    /**
     * Write DeviceEngagement to State characteristic for reverse engagement.
     * This should be called after connection is established when the holder needs
     * to send its DeviceEngagement to the reader.
     *
     * @param service The GATT service (discovered during open)
     * @param deviceEngagement The holder's device engagement bytes
     */
    suspend fun writeDeviceEngagement(service: GattService, deviceEngagement: ByteArray): IdkResult<Unit, BleError> {
        return bleCentralService.writeDeviceEngagement(service, deviceEngagement)
    }

    override fun close() {
        log.info("Closing BLE central client transfer")

        try {
            // Close data channels
            (incomingChannel as? AutoCloseable)?.close()
            (outgoingChannel as? AutoCloseable)?.close()

            // Close BLE service
            bleCentralService.close()

        } catch (e: Throwable) {
            log.error("Error closing BLE central client transfer", exception = e)
        } finally {
            super.close()
        }
    }
}
