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

package com.sphereon.mdoc.transport.ble

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.core.api.log.LogManager
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeTo
import com.sphereon.data.link.ble.client.BleEvent
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.SessionData
import com.sphereon.mdoc.SessionEstablishment
import com.sphereon.mdoc.transport.DataChannelEventDispatcher
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteStringBuilder
import kotlin.math.min
import kotlin.uuid.Uuid

/**
 * Implementation of BLE incoming data channel with explicit dependencies.
 *
 * This implementation is decoupled from engagement infrastructure.
 * All dependencies are passed explicitly via the constructor.
 *
 * ## Threading
 *
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("uses", exact = true)
 * This class uses a single-threaded coroutine scope to ensure in-order
 * processing of BLE characteristic changes. All received chunks are
 * processed sequentially to maintain message ordering.
 *
 * ## Chunking
 *
 * BLE has a maximum characteristic size (typically 512 bytes).
 * Large messages are split into chunks:
 * - First byte 0x01 = continuation (more chunks follow)
 * - First byte 0x00 = last chunk (message complete)
 * - First byte 0x02 = termination request
 *
 * @param logManager Logging service
 * @param role The role of this party (MDOC or MDOC_READER)
 * @param instanceId Unique identifier for this engagement instance
 * @param incomingCharacteristicId UUID of the characteristic to read from
 * @param eventDispatcher Optional dispatcher for events (can be null)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BleIncomingDataChannelImpl", exact = true)
class BleIncomingDataChannelImpl(
    logManager: LogManager,
    private val role: MdocRole,
    private val instanceId: Uuid,
    private val incomingCharacteristicId: Uuid,
    private var eventDispatcher: BleEventDispatcher? = null
) : BleIncomingDataChannel, BleEvent.Listener {

    // Buffer for events that arrive before dispatcher is set
    private val pendingEvents = mutableListOf<PendingEvent>()

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("PendingEvent", exact = true)
    private sealed class PendingEvent {
        data class SessionEstablishment(val instanceId: Uuid, val data: ByteArray) : PendingEvent()
        data class SessionData(val instanceId: Uuid, val data: ByteArray) : PendingEvent()
        data class Termination(val instanceId: Uuid, val data: ByteArray) : PendingEvent()
    }

    /**
     * Sets the event dispatcher for this channel.
     * Can be called after construction to provide late binding.
     *
     * Supports both BleEventDispatcher and the common DataChannelEventDispatcher interface.
     * When a dispatcher is set, any pending buffered events are immediately dispatched.
     */
    override fun setEventDispatcher(dispatcher: DataChannelEventDispatcher?) {
        // Adapt the common interface to BLE-specific interface
        eventDispatcher = if (dispatcher != null) {
            object : BleEventDispatcher {
                override fun dispatchSessionEstablishmentReceived(instanceId: Uuid, data: ByteArray) {
                    dispatcher.dispatchSessionEstablishmentReceived(instanceId, data)
                }

                override fun dispatchSessionDataReceived(instanceId: Uuid, data: ByteArray) {
                    dispatcher.dispatchSessionDataReceived(instanceId, data)
                }

                override fun dispatchTerminationReceived(instanceId: Uuid, data: ByteArray) {
                    dispatcher.dispatchTerminationReceived(instanceId, data)
                }

                override fun dispatchSessionDataSent(instanceId: Uuid, data: ByteArray) {
                    dispatcher.dispatchSessionDataSent(instanceId, data)
                }
            }
        } else {
            null
        }

        // Dispatch any pending events that arrived before the dispatcher was set
        if (eventDispatcher != null && pendingEvents.isNotEmpty()) {
            log.info("[$instanceId] Dispatching ${pendingEvents.size} buffered events")
            pendingEvents.forEach { event ->
                when (event) {
                    is PendingEvent.SessionEstablishment ->
                        eventDispatcher?.dispatchSessionEstablishmentReceived(event.instanceId, event.data)

                    is PendingEvent.SessionData ->
                        eventDispatcher?.dispatchSessionDataReceived(event.instanceId, event.data)

                    is PendingEvent.Termination ->
                        eventDispatcher?.dispatchTerminationReceived(event.instanceId, event.data)
                }
            }
            pendingEvents.clear()
        }
    }

    /**
     * Sets the BLE-specific event dispatcher.
     * Provided for backward compatibility.
     */
    fun setBleEventDispatcher(dispatcher: BleEventDispatcher?) {
        eventDispatcher = dispatcher
    }

    private val log = logManager.withTag("BleIncomingDataChannel-$instanceId")

    // Channel for delivering complete messages
    private var dataChannel: Channel<ByteArray> = Channel(capacity = Channel.UNLIMITED)

    // FIFO queue for incoming chunks - ensures strict ordering regardless of coroutine scheduling
    // This is critical because BLE characteristic changes can arrive in rapid succession
    // and limitedParallelism(1) does NOT guarantee the order of execution
    private val incomingChunkQueue: Channel<ByteArray> = Channel(capacity = Channel.UNLIMITED)

    // Coroutine scope for sequential processing
    private val processingScope = CoroutineScope(
        SupervisorJob() +
                Dispatchers.Default.limitedParallelism(1) +
                CoroutineName("BleIncomingDataChannel-$instanceId")
    )

    init {
        // Start a single consumer coroutine that processes chunks in strict FIFO order
        processingScope.launch {
            log.debug("[$instanceId] Starting chunk processor coroutine")
            for (chunk in incomingChunkQueue) {
                try {
                    handleIncomingData(chunk)
                } catch (e: Exception) {
                    log.error("[$instanceId] Error processing chunk: ${e.message}", exception = e)
                }
            }
            log.debug("[$instanceId] Chunk processor coroutine completed")
        }
    }

    // BLE characteristic size configuration
    private companion object {
        const val MAX_CHARACTERISTIC_SPEC_SIZE = 512
    }

    private val characteristicSize: AtomicInt = atomic(MAX_CHARACTERISTIC_SPEC_SIZE)

    // Buffer for assembling chunked messages
    private var data = ByteStringBuilder()

    // Most recent complete message
    private val lastData: AtomicRef<ByteArray?> = atomic(null)

    // Termination flag
    private val closeReceived: AtomicBoolean = atomic(false)

    // Message counter for debugging
    private val receivedMessageCount = atomic(0)

    override fun getMostRecentReceivedRaw(): ByteArray? = lastData.value

    override suspend fun receiveRaw(): ByteArray {
        log.debug("[$instanceId] receiveRaw() blocking called...")
        return dataChannel.receive().also {
            log.debug("[$instanceId] receiveRaw() returned ${it.size} bytes")
        }
    }

    override suspend fun receiveSessionEstablishment(): SessionEstablishment {
        require(role == MdocRole.MDOC) {
            "receiveSessionEstablishment() can only be called from MDOC (holder) role, current role: $role"
        }
        return SessionEstablishment.decodeCbor(receiveRaw())
    }

    override suspend fun receiveSessionData(): SessionData {
        // SessionData can be received by both roles:
        // - MDOC_READER: receives encrypted DeviceResponse from holder
        // - MDOC: could receive follow-up messages in future protocols
        return SessionData.decodeCbor(receiveRaw())
    }

    override suspend fun awaitExternalTermination(): Boolean {
        return receiveRaw().contentEquals(byteArrayOf(0x02))
    }

    /**
     * Handle a completed message (all chunks received).
     *
     * Validates the message based on role and dispatches appropriate events.
     * If no dispatcher is set yet, the event is buffered and will be dispatched
     * when the dispatcher is set via setEventDispatcher().
     */
    private fun handleCompletedMessage(message: ByteArray) {
        receivedMessageCount.incrementAndGet()
        log.debug("[$instanceId] Handling completed message counter(${receivedMessageCount.value}): ${message.size} bytes")

        when (role) {
            MdocRole.MDOC -> {
                // Holder side: Validate it's a SessionEstablishment
                try {
                    // Decode to validate (but keep original bytes for later use)
                    val sessionEstablishment = SessionEstablishment.decodeCbor(message)
                    log.info("[$instanceId] Session establishment received: ${sessionEstablishment.data.value.size} bytes")

                    // Dispatch event if dispatcher is available, otherwise buffer it
                    if (eventDispatcher != null) {
                        eventDispatcher?.dispatchSessionEstablishmentReceived(instanceId, message)
                    } else {
                        log.info("[$instanceId] No event dispatcher set yet, buffering SessionEstablishment event")
                        pendingEvents.add(PendingEvent.SessionEstablishment(instanceId, message))
                    }
                } catch (e: Exception) {
                    log.error("[$instanceId] Error handling session establishment: ${e.message}", exception = e)
                    val hexString = message.encodeTo(Encoding.HEX)
                    if (hexString.length <= 2000) {
                        log.debug(hexString)
                    } else {
                        hexString.chunked(2000).forEachIndexed { index, chunk ->
                            log.debug("(part ${index + 1}) $chunk")
                        }
                    }

                }
            }

            MdocRole.MDOC_READER -> {
                // Reader side: Validate it's SessionData
                try {
                    // Decode to validate (but keep original bytes for later decryption)
                    val sessionData = SessionData.decodeCbor(message)
                    log.info("[$instanceId] Session data received: ${message.size} bytes (status: ${sessionData.status})")

                    // Dispatch event if dispatcher is available, otherwise buffer it
                    if (eventDispatcher != null) {
                        eventDispatcher?.dispatchSessionDataReceived(instanceId, message)
                    } else {
                        log.info("[$instanceId] No event dispatcher set yet, buffering SessionData event")
                        pendingEvents.add(PendingEvent.SessionData(instanceId, message))
                    }
                } catch (e: Exception) {
                    log.error("[$instanceId] Error validating session data: ${e.message}", exception = e)
                    // Don't queue invalid data
                    return
                }
            }
        }
    }

    /**
     * Returns the number of completed messages received.
     */
    fun getReceivedMessageCount(): Int = receivedMessageCount.value

    /**
     * Handle incoming data chunk from BLE.
     *
     * Chunks are assembled into complete messages based on the first byte:
     * - 0x00 = last chunk (complete message)
     * - 0x01 = continuation (more chunks follow)
     * - 0x02 = stop/termination request
     */
    private suspend fun handleIncomingData(chunk: ByteArray) {
        log.debug("[$instanceId] Received ${chunk.size} bytes")
        require(chunk.isNotEmpty()) { "Invalid data length ${chunk.size}" }

        if (closeReceived.value) {
            log.error("[$instanceId] Received data after close request. Ignoring.")
            return
        }

        val start = chunk[0].toInt()
        when (start) {
            0x00 /* End of message */ -> {
                // Strip first byte (0x00 flag)
                data.append(chunk, 1, chunk.size)

                // Message complete
                val newMessage = data.toByteString().toByteArray()
                data = ByteStringBuilder() // Reset buffer
                lastData.value = newMessage

                log.debug("[$instanceId] Received ${newMessage.size} bytes (complete message)")

                // Send to channel and handle
                dataChannel.send(newMessage).also {
                    handleCompletedMessage(newMessage)
                }
            }

            0x01 /* Continuation */ -> {
                if (lastData.value == null && data.size == 0) {
                    log.debug("[$instanceId] Received first chunk of multi-part message")
                }

                // Strip first byte (0x01 flag)
                data.append(chunk, 1, chunk.size)

                log.debug("[$instanceId] Received ${chunk.size} bytes (continuation)")

                // Verify chunk size matches expected characteristic size
                val currentCharSize = characteristicSize.value
                if (chunk.size != currentCharSize) {
                    log.warn("[$instanceId] Received ${chunk.size} bytes, expected $currentCharSize bytes")
                }
            }

            0x02 /* Stop/Termination */ -> {
                if (chunk.size != 1) {
                    log.error("[$instanceId] Invalid termination request: ${chunk.size} bytes")
                    throw Error("Invalid termination request size: ${chunk.size}")
                }

                log.info("[$instanceId] Received termination request from remote party")
                closeReceived.value = true

                // Dispatch termination event if dispatcher is available, otherwise buffer it
                if (eventDispatcher != null) {
                    eventDispatcher?.dispatchTerminationReceived(instanceId, chunk)
                } else {
                    log.info("[$instanceId] No event dispatcher set yet, buffering Termination event")
                    pendingEvents.add(PendingEvent.Termination(instanceId, chunk))
                }
            }

            else -> {
                log.error("[$instanceId] Invalid first byte $start, expected 0x00, 0x01, or 0x02")
                throw Error("Invalid first byte $start in BLE message")
            }
        }
    }

    // BleEvent.Listener implementation

    override fun onCharacteristicChanged(event: BleEvent.CharacteristicChanged) {
        // Log ALL characteristic changed events for debugging
        log.info("[$instanceId] *** onCharacteristicChanged: char=${event.characteristic.id}, size=${event.value.size}, expectedChar=$incomingCharacteristicId ***")

        // Only process data from our characteristic
        if (event.characteristic.id != incomingCharacteristicId) {
            log.debug("[$instanceId] Ignoring characteristic ${event.characteristic.id}, expecting $incomingCharacteristicId")
            return
        }

        log.info("[$instanceId] Characteristic data received: ${event.value.size} bytes")

        // Enqueue chunk for FIFO processing - this ensures strict ordering
        // even when multiple BLE events arrive simultaneously on different threads.
        // The trySend is non-blocking and will not fail since the channel has unlimited capacity.
        val result = incomingChunkQueue.trySend(event.value)
        if (result.isFailure) {
            log.error("[$instanceId] Failed to enqueue chunk: ${result.exceptionOrNull()?.message}")
        }
    }

    override fun onMtuChanged(event: BleEvent.MtuChanged) {
        // Per ISO 18013-5, characteristic size is MTU - 3 bytes (for ATT overhead)
        val newSize = min(MAX_CHARACTERISTIC_SPEC_SIZE, event.mtu - 3)
        characteristicSize.value = newSize
        log.info("[$instanceId] MTU changed to ${event.mtu}, characteristic size now $newSize")
    }

    override fun onBleError(event: BleEvent.Error) {
        log.warn("[$instanceId] BLE error occurred: ${event.error.message}")
        log.warn("$event")
    }

    override fun onConnectionStateChanged(event: BleEvent.ConnectionStateChanged) {
        when (event.newState) {
            0, 3 -> {
                if (!closeReceived.value) {
                    log.warn("[$instanceId] Connection closed without termination message")
                }
            }

            2 -> {
                log.info("[$instanceId] BLE connection established")
            }
        }
    }

    // BleEvent.Listener methods (not used by this channel but required by interface)
    override fun onServicesDiscovered(event: BleEvent.ServicesDiscovered) {}
    override fun onCharacteristicRead(event: BleEvent.CharacteristicRead) {}
    override fun onCharacteristicWrite(event: BleEvent.CharacteristicWrite) {
        // Log write confirmations (no value - just status)
        log.debug("[$instanceId] onCharacteristicWrite: char=${event.characteristic.id}, status=${event.status}")
    }
    override fun onDescriptorRead(event: BleEvent.DescriptorRead) {}
    override fun onDescriptorWrite(event: BleEvent.DescriptorWrite) {}
    override fun onNotification(event: BleEvent.Notification) {}
    override fun onScanStarted(event: BleEvent.ScanStarted) {}
    override fun onScanStopped(event: BleEvent.ScanStopped) {}
    override fun onScanResult(event: BleEvent.ScanResult) {}
    override fun onDeviceFound(event: BleEvent.DeviceFound) {}

    override fun close() {
        log.info("[$instanceId] Closing BLE incoming data channel")

        // Close the incoming chunk queue first to stop the processor
        incomingChunkQueue.close()

        // Clear any pending data
        val channelResult = dataChannel.tryReceive()
        log.info("[$instanceId] Cleared channel: ${channelResult.getOrNull()?.size ?: 0} bytes")

        // Cancel processing scope
        processingScope.cancel()

        // Close data channel
        dataChannel.close()
    }
}
