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

package com.sphereon.mdoc.transport.ble

import com.sphereon.core.api.log.LogManager
import com.sphereon.data.link.ble.client.BleEvent
import com.sphereon.data.link.ble.model.GattCharacteristic
import com.sphereon.data.link.ble.model.GattService
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.SessionData
import com.sphereon.mdoc.SessionDataCborCodec
import com.sphereon.mdoc.SessionEstablishment
import com.sphereon.mdoc.SessionEstablishmentCborCodec
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.experimental.ExperimentalObjCName
import kotlin.math.min
import kotlin.native.ObjCName
import kotlin.uuid.Uuid

/**
 * Implementation of BLE outgoing data channel with explicit dependencies.
 *
 * This implementation is decoupled from engagement infrastructure.
 * All dependencies are passed explicitly via the constructor.
 *
 * ## Threading
 *
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("uses", exact = true)
 * This class uses a single-threaded coroutine scope to serialize writes.
 * All write requests are queued and processed sequentially to maintain
 * message ordering and avoid race conditions with BLE write operations.
 *
 * ## Chunking
 *
 * BLE has a maximum characteristic size (typically 512 bytes).
 * Large messages are split into chunks:
 * - First byte 0x01 = continuation (more chunks follow)
 * - First byte 0x00 = last chunk (message complete)
 *
 * @param logManager Logging service
 * @param role The role of this party (MDOC or MDOC_READER)
 * @param instanceId Unique identifier for this engagement instance
 * @param characteristicWriter Writer for BLE characteristics (mode-specific)
 * @param outgoingCharacteristicId UUID of the characteristic to write to
 * @param stateCharacteristicId UUID of the state characteristic
 * @param serviceUuid UUID of the GATT service
 * @param eventDispatcher Optional dispatcher for events (can be null)
 * @param sessionEstablishmentCborCodec Typed codec used for session establishment wire bytes
 * @param sessionDataCborCodec Typed codec used for session data wire bytes
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BleOutgoingDataChannelImpl", exact = true)
class BleOutgoingDataChannelImpl(
    logManager: LogManager,
    private val role: MdocRole,
    private val instanceId: Uuid,
    private val characteristicWriter: BleCharacteristicWriter,
    private val outgoingCharacteristicId: Uuid,
    private val stateCharacteristicId: Uuid,
    private val serviceUuid: Uuid,
    private var eventDispatcher: BleEventDispatcher? = null,
    private val sessionEstablishmentCborCodec: SessionEstablishmentCborCodec,
    private val sessionDataCborCodec: SessionDataCborCodec,
) : BleOutgoingDataChannel {
    /**
     * Sets the event dispatcher for this channel.
     * Can be called after construction to provide late binding.
     */
    fun setEventDispatcher(dispatcher: BleEventDispatcher?) {
        eventDispatcher = dispatcher
    }

    private val log = logManager.withTag("BleOutgoingDataChannel-$instanceId")

    // GATT service and characteristics (set via onServicesDiscovered or explicitly)
    private val outgoingCharacteristic: AtomicRef<GattCharacteristic?> = atomic(null)
    private val stateCharacteristic: AtomicRef<GattCharacteristic?> = atomic(null)
    private val service: AtomicRef<GattService?> = atomic(null)

    // Queue for serializing write operations
    private var sendQueue: Channel<WriteRequest> = Channel(capacity = Channel.UNLIMITED)

    // Call counter for debugging
    private val sendRawCallCounter = atomic(0)

    // Mutex for thread-safe sendRaw
    private val sendRawMutex = Mutex()

    // Coroutine scope for processing writes
    private val processingScope =
        CoroutineScope(
            SupervisorJob() +
                CoroutineName("BleOutgoingDataChannel-$instanceId"),
        )

    // BLE characteristic size configuration
    private companion object {
        const val MAX_CHARACTERISTIC_SPEC_SIZE = 512
    }

    private val characteristicSize: AtomicInt = atomic(MAX_CHARACTERISTIC_SPEC_SIZE)

    // Most recent sent data
    private val lastData: AtomicRef<ByteArray?> = atomic(null)

    init {
        log.info("Created BLE outgoing data channel: $instanceId")

        // Single coroutine to serialize writes
        processingScope.launch {
            for (req in sendQueue) {
                val currentService = service.value
                val currentOutgoingChar = outgoingCharacteristic.value
                val currentStateChar = stateCharacteristic.value

                if (currentService == null || currentOutgoingChar == null || currentStateChar == null) {
                    log.error("[$instanceId] Service or characteristics not initialized")
                    req.ack.completeExceptionally(Error("Service or characteristics not initialized"))
                    sendQueue.close()
                    continue
                }

                try {
                    log.debug("[$instanceId] Writing ${req.packet.size} bytes (first byte: 0x${req.packet[0].toString(16)})")

                    val status =
                        characteristicWriter.writeCharacteristic(
                            currentService,
                            currentOutgoingChar,
                            req.packet,
                        )

                    if (status.isOk) {
                        log.debug("[$instanceId] Write successful: ${status.value} bytes")
                        req.ack.complete(status.value)
                    } else {
                        log.error("[$instanceId] Write failed: ${status.error}")
                        req.ack.completeExceptionally(Error("Write failed: ${status.error}"))
                    }
                } catch (expected: Exception) {
                    log.error("[$instanceId] Exception during write: ${expected.message}", exception = expected)
                    req.ack.completeExceptionally(expected)
                    sendQueue.close()
                    break
                }
            }
        }
    }

    override fun getMostRecentSentRaw(): ByteArray? = lastData.value

    /**
     * Returns the number of times sendRaw has been called.
     */
    fun getSendRawCallCount(): Int = sendRawCallCounter.value

    /**
     * Internal write request for queueing.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("WriteRequest", exact = true)
    private data class WriteRequest(
        val packet: ByteArray,
        val ack: CompletableDeferred<Int>,
    )

    override suspend fun sendRaw(data: ByteArray): Int =
        sendRawMutex.withLock {
            sendRawCallCounter.incrementAndGet()
            lastData.value = data

            log.info("[$instanceId] Sending data counter(${sendRawCallCounter.value}), size(${data.size})")

            // End-of-transfer marker
            if (data.isEmpty()) {
                log.info("[$instanceId] Sending termination (0x02) - empty data")
                val endAck = CompletableDeferred<Int>()
                sendQueue.send(WriteRequest(byteArrayOf(0x02), endAck))
                return@withLock endAck.await()
            }

            val currentCharSize = characteristicSize.value
            val maxPayload = currentCharSize - 1 - 3 // -1 for flag byte, -3 for overhead
            var offset = 0
            var status = 0

            while (offset < data.size) {
                val chunkSize = min(maxPayload, data.size - offset)
                // Check if there's more data AFTER this chunk
                val flag: Byte =
                    if (offset + chunkSize < data.size) {
                        0x01
                    } else {
                        0x00
                    }

                val msg =
                    if (flag == 0x01.toByte()) {
                        "CONTINUATION"
                    } else {
                        "END"
                    }
                log.debug("[$instanceId] Sending $msg chunk of $chunkSize bytes")

                val packet =
                    ByteArray(chunkSize + 1).apply {
                        this[0] = flag
                        data.copyInto(this, 1, offset, offset + chunkSize)
                    }

                // Enqueue and wait for completion
                val ack = CompletableDeferred<Int>()
                sendQueue.send(WriteRequest(packet, ack))
                status = ack.await()

                if (status != 0) {
                    log.error("[$instanceId] Error sending data: status $status")
                    return@withLock status
                }

                offset += chunkSize
            }

            // Dispatch event if dispatcher available
            eventDispatcher?.dispatchSessionDataSent(instanceId, data)

            log.info("[$instanceId] Done sending data counter(${sendRawCallCounter.value}), size(${data.size}), status $status")
            return@withLock status
        }

    override suspend fun sendEndMessage(): Int {
        log.info("[$instanceId] Sending explicit end message (0x02)")
        val ack = CompletableDeferred<Int>()
        sendQueue.send(WriteRequest(byteArrayOf(0x02), ack))
        return ack.await()
    }

    override suspend fun sendSessionEstablishment(sessionEstablishment: SessionEstablishment): Int {
        log.info("[$instanceId] Sending SessionEstablishment with ${sessionEstablishment.data.value.size} bytes")
        val encoded = sessionEstablishmentCborCodec.encode(sessionEstablishment).getOrThrow()
        return sendRaw(encoded)
    }

    override suspend fun sendSessionData(sessionData: SessionData): Int {
        log.info("[$instanceId] Sending SessionData with ${sessionData.data?.value?.size ?: 0} bytes")
        val encoded = sessionDataCborCodec.encode(sessionData).getOrThrow()
        return sendRaw(encoded)
    }

    // Event collection job (started via startEventCollection)
    private var eventCollectionJob: Job? = null

    /**
     * Start collecting BLE events from the given SharedFlow.
     * This replaces the former BleEvent.Listener interface implementation.
     *
     * Events handled:
     * - MtuChanged: updates the characteristic size for chunking
     * - ServicesDiscovered: resolves the GATT service and characteristics
     * - ConnectionStateChanged: logs connection state transitions
     * - Error: logs BLE errors
     *
     * @param bleEvents SharedFlow of BLE events from BlePlatformClient or BlePlatformPeripheral
     * @param scope CoroutineScope in which to launch the collection
     */
    fun startEventCollection(
        bleEvents: SharedFlow<BleEvent>,
        scope: CoroutineScope,
    ) {
        eventCollectionJob?.cancel()
        eventCollectionJob =
            scope.launch {
                bleEvents.collect { event ->
                    when (event) {
                        is BleEvent.MtuChanged -> {
                            // Per ISO 18013-5, characteristic size is MTU - 3 bytes
                            val newSize = min(MAX_CHARACTERISTIC_SPEC_SIZE, event.mtu - 3)
                            characteristicSize.value = newSize
                            log.debug("[$instanceId] MTU changed to ${event.mtu}, characteristic size now $newSize")
                        }

                        is BleEvent.ServicesDiscovered -> {
                            log.debug("[$instanceId] Services discovered: ${event.services.size} services")

                            // Find the service that matches our serviceUuid
                            val discoveredService = event.services.find { it.id == serviceUuid }
                            if (discoveredService == null) {
                                log.error("[$instanceId] Service $serviceUuid not found. Available: ${event.services.map { it.id }}")
                                return@collect
                            }

                            // Find the characteristics we need
                            val discoveredStateChar = discoveredService.characteristics.find { it.id == stateCharacteristicId }
                            val discoveredOutgoingChar = discoveredService.characteristics.find { it.id == outgoingCharacteristicId }

                            if (discoveredStateChar == null || discoveredOutgoingChar == null) {
                                log.error("[$instanceId] Required characteristics not found")
                                return@collect
                            }

                            service.value = discoveredService
                            stateCharacteristic.value = discoveredStateChar
                            outgoingCharacteristic.value = discoveredOutgoingChar

                            log.debug("[$instanceId] Service and characteristics discovered successfully")
                        }

                        is BleEvent.ConnectionStateChanged -> {
                            when (event.newState) {
                                2 -> log.debug("[$instanceId] BLE connection established")
                                0, 3 -> log.debug("[$instanceId] BLE connection stopped")
                            }
                        }

                        is BleEvent.Error -> {
                            log.warn("[$instanceId] BLE error occurred: ${event.error.message}")
                            log.warn("$event")
                        }

                        else -> {}
                    }
                }
            }
    }

    /**
     * Directly set the service and characteristics (for peripheral mode).
     *
     * In peripheral mode, the service is created (not discovered), so we
     * need to set these directly after the service is created.
     *
     * @param gattService The GATT service that was created
     */
    fun setServiceAndCharacteristics(gattService: GattService) {
        log.info("[$instanceId] Setting service and characteristics directly (peripheral mode)")

        service.value = gattService

        // Find characteristics before assigning to atomic refs to avoid atomicfu transformer issues
        val foundStateChar = gattService.characteristics.find { it.id == stateCharacteristicId }
        val foundOutgoingChar = gattService.characteristics.find { it.id == outgoingCharacteristicId }

        stateCharacteristic.value = foundStateChar
        outgoingCharacteristic.value = foundOutgoingChar

        if (foundStateChar == null || foundOutgoingChar == null) {
            log.error("[$instanceId] Failed to find required characteristics")
            log.error("[$instanceId] Expected state: $stateCharacteristicId")
            log.error("[$instanceId] Expected outgoing: $outgoingCharacteristicId")
            log.error("[$instanceId] Available: ${gattService.characteristics.map { it.id }}")
        } else {
            log.info("[$instanceId] Service and characteristics set successfully")
        }
    }

    override fun close() {
        log.debug("[$instanceId] Closing BLE outgoing data channel")

        // Cancel event collection
        eventCollectionJob?.cancel()
        eventCollectionJob = null

        // Clear any pending requests
        val channelResult = sendQueue.tryReceive()
        log.debug("[$instanceId] Cleared channel: ${channelResult.getOrNull()?.packet?.size ?: 0} bytes")

        // Cancel processing scope
        processingScope.cancel()

        // Close send queue
        sendQueue.close()
    }
}
