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
import com.sphereon.data.link.ble.CancelledError
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.EngagementData
import com.sphereon.mdoc.engagement.MdocEngagementState
import com.sphereon.mdoc.transport.AbstractMdocTransport
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * BLE peripheral server transfer implementation.
 *
 * In peripheral mode, this device advertises and accepts connections from central devices.
 * This is used when:
 * - Holder (MDOC) advertises for reader to connect
 * - Reader (MDOC_READER) advertises for holder to connect (reverse role mode)
 *
 * ## Lifecycle
 *
 * 1. **Create** transfer with services and data channels
 * 2. **Open** - Advertise service, wait for connection, start transfer
 * 3. **Exchange** - Send and receive data via data channels
 * 4. **Close** - Clean up services and channels
 *
 * ## Thread Safety
 *
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("uses", exact = true)
 * This class uses a mutex to protect state transitions during open/close.
 * Message send/receive methods can be called concurrently.
 *
 * @param connectionMethod The BLE connection method
 * @param execution Session execution context
 * @param blePeripheralService The BLE peripheral service for advertising/accepting connections
 * @param role The role of this party (MDOC or MDOC_READER)
 */
class BlePeripheralServerTransport(
    connectionMethod: BleConnectionMethod,
    execution: SessionExecution,
    private val blePeripheralService: BlePeripheralService,
    override val role: MdocRole
) : AbstractMdocTransport<Uuid>(connectionMethod, execution) {

    private val log = execution.log
    private val mutex = Mutex()

    // Data channels are provided by the peripheral service
    private val incomingChannel: BleIncomingDataChannel
        get() = blePeripheralService.incomingChannel()

    private val outgoingChannel: BleOutgoingDataChannel
        get() = blePeripheralService.outgoingChannel()

    /**
     * Track whether the remote device (reader/central) has disconnected.
     * Per ISO 18013-5, in forward engagement the reader should initiate the disconnect,
     * not the holder. We track this to avoid prematurely closing the connection.
     */
    private val remoteDisconnected = atomic(false)

    /**
     * Track whether close() has been called by the application.
     * If the app calls close() before the reader disconnects, we defer cleanup until
     * the reader actually disconnects.
     */
    private val closeRequested = atomic(false)

    init {
        // Note: We don't check connectionMethod.options.peripheralServerMode here because
        // in forward engagement with role reversal, the reader may be acting as peripheral
        // even though the device engagement (holder's announcement) says centralClientMode=true.
        // The BleTransportFactory is responsible for choosing the correct transfer type
        // based on role and announced modes.

        // Register for BLE disconnect events to update engagement state
        blePeripheralService.setCallbacks(
            onError = { error ->
                log.error("BLE peripheral error: ${error.message}", exception = error)
                // Don't close here - let the transport handle errors through normal flow
            },
            onClosed = {
                log.info("BLE connection closed by remote device (reader)")
                // Mark that the remote device has disconnected
                remoteDisconnected.value = true
                // Mark the transport as closed so engagement state becomes DISCONNECTED
                markClosed()

                // If close() was already called by the app but we deferred cleanup,
                // now is the time to actually close the BLE service
                if (closeRequested.value) {
                    log.info("Reader disconnected after close() was called - completing deferred cleanup")
                    try {
                        blePeripheralService.close()
                    } catch (e: Throwable) {
                        log.error("Error during deferred BLE service close", exception = e)
                    }
                }
            }
        )
    }

    override suspend fun open(senderKey: CoseKeyType, id: Uuid, engagementData: EngagementData?): IdkResult<Uuid, BleError> {
        log.info("open() called for BLE peripheral server mode, advertising UUID: $id")

        mutex.withLock {
            check(_engagementState.value == MdocEngagementState.INIT) {
                "open() called in wrong state: ${_engagementState.value}. Expected ${MdocEngagementState.INIT}"
            }

            _engagementData = engagementData
            try {
                // Step 1: Start advertising with the service UUID
                _engagementState.value = MdocEngagementState.BLE_ADVERTISING
                log.info("Starting advertising with UUID: $id")

                val advResult = blePeripheralService.awaitAdvertising(id)
                if (advResult.isErr) {
                    val error = advResult.error
                    if (error is CancelledError) {
                        // Cancellation is expected when racing connections - not an error
                        log.info("Advertising was cancelled (racing connections)")
                        _engagementState.value = MdocEngagementState.DISCONNECTED
                    } else {
                        log.error("Failed to start advertising: ${error.message}")
                        _engagementState.value = MdocEngagementState.ERROR
                    }
                    return error.asErrorResult()
                }

                log.info("Advertising started, waiting for central to connect...")

                // Step 2: Wait for central device to connect
                _engagementState.value = MdocEngagementState.CONNECTING

                val serviceResult = blePeripheralService.awaitConnection(senderKey)
                if (serviceResult.isErr) {
                    val error = serviceResult.error
                    if (error is CancelledError) {
                        // Cancellation is expected when racing connections - not an error
                        log.info("Connection waiting was cancelled (racing connections)")
                        _engagementState.value = MdocEngagementState.DISCONNECTED
                    } else {
                        log.error("Failed to accept connection: ${error.message}")
                        _engagementState.value = MdocEngagementState.ERROR
                    }
                    return error.asErrorResult()
                }
                val service = serviceResult.value

                log.info("Central device connected, service: ${service.id}")

                // Step 3: Start data transfer
                _engagementState.value = MdocEngagementState.CONNECTED
                log.info("Starting data transfer...")

                val startResult = blePeripheralService.start(service)
                if (startResult.isErr) {
                    log.error("Failed to start data transfer: ${startResult.error.message}")
                    _engagementState.value = MdocEngagementState.ERROR
                    return startResult.error.asErrorResult()
                }

                log.info("BLE peripheral server transfer opened successfully")
                markOpen()

                return id.asOkResult()

            } catch (e: CancellationException) {
                // Propagate cancellation - this is expected when the coroutine is cancelled
                log.info("BLE peripheral server transfer opening was cancelled")
                _engagementState.value = MdocEngagementState.DISCONNECTED
                throw e
            } catch (e: Throwable) {
                log.error("Error opening BLE peripheral server transfer", exception = e)
                _engagementState.value = MdocEngagementState.ERROR
                return BleErrors.connectionFailed(
                    "Failed to open BLE peripheral server transfer: ${e.message}",
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
            blePeripheralService.close()
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
     * Read DeviceEngagement from State characteristic for reverse engagement.
     * This should be called after connection is established when the reader needs
     * to receive the holder's DeviceEngagement.
     */
    suspend fun readDeviceEngagement(): IdkResult<ByteArray, BleError> {
        return blePeripheralService.readDeviceEngagement()
    }

    override fun close() {
        log.info("Closing BLE peripheral server transfer (role=$role, remoteDisconnected=${remoteDisconnected.value})")

        // Mark that close() has been called - needed for deferred cleanup
        closeRequested.value = true

        try {
            // Per ISO 18013-5, in forward engagement the READER should initiate the disconnect,
            // not the holder (MDOC). If we're the holder and the reader hasn't disconnected yet,
            // we should NOT actively close the GATT server as that would terminate the connection
            // from the holder side, which violates the protocol.
            //
            // However, we DO clean up if:
            // 1. The reader has already disconnected (remoteDisconnected = true)
            // 2. We are the reader (role = MDOC_READER) - reader can close anytime
            // 3. The transport is in an error state
            val shouldCloseGattServer = when {
                remoteDisconnected.value -> {
                    log.info("Reader has already disconnected, safe to close GATT server")
                    true
                }
                role == MdocRole.MDOC_READER -> {
                    log.info("We are the reader, initiating disconnect is allowed")
                    true
                }
                _engagementState.value == MdocEngagementState.ERROR -> {
                    log.info("Transport is in error state, closing GATT server")
                    true
                }
                else -> {
                    // We are the holder (MDOC) and the reader hasn't disconnected yet.
                    // Per ISO 18013-5, we should NOT close the GATT server.
                    // The reader should close the connection after receiving the device response.
                    log.info("Holder (MDOC) close() called but reader hasn't disconnected yet. " +
                            "Per ISO 18013-5, the reader should initiate the disconnect. " +
                            "Not closing GATT server - waiting for reader to disconnect.")
                    false
                }
            }

            if (shouldCloseGattServer) {
                // Close BLE service (which will close data channels and GATT server)
                blePeripheralService.close()
                
                // Only mark the transport as closed when we actually close the GATT server.
                // This sets the state to DISCONNECTED and triggers the engagement Disconnected event.
                super.close()
            } else {
                // Don't close the GATT server, and don't mark transport as closed yet.
                // Per ISO 18013-5, we wait for the reader to initiate the disconnect.
                // When the reader disconnects:
                // 1. The onClosed callback in init{} will be invoked
                // 2. markClosed() will be called to set state to DISCONNECTED
                // 3. blePeripheralService.close() will be called for cleanup
                // This ensures the Disconnected event is only dispatched after the reader actually disconnects.
                log.info("Deferring transport close until reader disconnects (per ISO 18013-5)")
            }

        } catch (e: Throwable) {
            log.error("Error closing BLE peripheral server transfer", exception = e)
            // Still mark as closed on error
            super.close()
        }
    }
}
