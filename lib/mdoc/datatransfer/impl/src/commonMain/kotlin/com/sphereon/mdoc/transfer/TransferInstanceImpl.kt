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

package com.sphereon.mdoc.transfer

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.toException
import com.sphereon.util.stringify
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DeviceResponse
import com.sphereon.mdoc.engagement.EngagementInstance
import com.sphereon.mdoc.transfer.device.DataRetrievalTransmissionType
import com.sphereon.mdoc.transport.TransportType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Removed expect/actual functions - using DI instead

@OptIn(ExperimentalObjCName::class)
@ObjCName("TransferInstanceImpl", exact = true)
class TransferInstanceImpl(
    override val engagement: EngagementInstance,
    override val manager: TransferManager,
    execution: SessionExecution,
    private val transferFactory: MdocTransferFactory,
    listeners: Set<MdocRetrievalEvent.Listener> = mutableSetOf()
) : MdocRetrievalEvent.Dispatcher, TransferInstance {
    private val listenersMutex = Mutex()
    private val listeners = listeners.toMutableSet()
    override val type: DataInstanceType = DataInstanceType.PER_ENGAGEMENT
    @OptIn(ExperimentalUuidApi::class)
    override val id: Uuid = engagement.data.getUuid(!(engagement.data.isRestApiEngagementSupported() || engagement.data.isRestApiRetrievalSupported())) ?: Uuid.random()
    private val log = execution.log.logManager.withTag("TransferInstance-${id}")
    override val transmissionTypesSupported = engagement.data.retrievalTransmissionTypesSupported()
    private val _events = MutableSharedFlow<MdocRetrievalEvent>(replay = 1, extraBufferCapacity = 64)
    override val events: SharedFlow<MdocRetrievalEvent> = _events.asSharedFlow()
    private val _states: MutableStateFlow<MdocRetrievalStateType> = MutableStateFlow(MdocRetrievalState.INIT)
    override val states: Flow<MdocRetrievalStateType> = _states.asStateFlow()

    // Internal atomic references for thread-safe access
    private val _deviceRequest: AtomicRef<DeviceRequest?> = atomic(null)
    private val _deviceResponse: AtomicRef<DeviceResponse?> = atomic(null)
    private val _transfer: AtomicRef<IMdocTransfer<*>?> = atomic(null)
    private val _transmissionTypeSelected: AtomicRef<DataRetrievalTransmissionType?> =
        atomic(transmissionTypesSupported.firstOrNull().takeIf { transmissionTypesSupported.size == 1 })

    // Public properties with custom accessors to maintain interface contract
    override var deviceRequest: DeviceRequest?
        get() = _deviceRequest.value
        set(value) {
            _deviceRequest.value = value
        }

    override var deviceResponse: DeviceResponse?
        get() = _deviceResponse.value
        set(value) {
            _deviceResponse.value = value
        }

    override var transfer: IMdocTransfer<*>
        get() = _transfer.value ?: throw IllegalStateException("Transfer has not been initialized. Call start() first.")
        set(value) {
            _transfer.value = value
        }

    override var transmissionTypeSelected: DataRetrievalTransmissionType?
        get() = _transmissionTypeSelected.value
        set(value) {
            _transmissionTypeSelected.value = value
        }

    // Connection methods are computed using engagement's getConnectionMethods()
    // which uses the transport registry internally
    override val connectionMethods: Set<ConnectionMethod> = engagement.getConnectionMethods(supportedOnly = false)
    val instanceCoroutineScope = engagement.sessionCoroutineScope.createChild(CoroutineName("instance-${id}"))

    val sessionComponent = execution.sessionContextManager.getActive().component

    override fun getCurrentState(): MdocRetrievalStateType {
        return _states.value
    }

    // Store the latest engagement event to use as context for retrieval events
    private val latestEngagementEvent: AtomicRef<com.sphereon.mdoc.engagement.MdocEngagementEvent?> = atomic(null)


    init {
        require(connectionMethods.isNotEmpty()) { "No connection methods available for retrieval phase. Please check your engagement configuration options" }
        dispatch(MdocRetrievalEvent.Initializing(engagement.id))
    }

    override suspend fun start(): TransferInstance {
        val result = tryOps().start()
        return if (result.isOk) result.value else throw result.error.toException()
    }

    override fun tryOps(): TransferInstance.Try = object : TransferInstance.Try {
        override suspend fun start(): IdkResult<TransferInstance, IdkErrorType> {
            return try {
                log.info("$id: Starting transfer instance with ${connectionMethods.size} connection methods")

                if (_transfer.value != null) {
                    return IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Transfer has already been started. Did you call start() twice?"
                    ).asErrorResult()
                }

                // Create transfers for all connection methods
                // The factory (especially BleTransportFactory) will handle mode selection
                log.info("$id: Creating transfers from ${connectionMethods.size} connection methods")
                val transfers = connectionMethods.map { method ->
                    log.debug("$id: Creating transfer for ${method.transportType}")
                    transferFactory.setupTransfer(method, this@TransferInstanceImpl)
                }

                // Start advertising for peripheral transfers
                log.info("$id: Starting advertising for ${transfers.count { it is IMdocTransferWithAdvertising<*> }} peripheral transfers")
                transfers.forEach { transfer ->
                    if (transfer is IMdocTransferWithAdvertising<*>) {
                        log.info("$id: Starting advertising for ${transfer.connectionMethod.transportType}")
                        transfer.startAdvertising()
                    }
                }

                // Race all transfers if multiple, or just open single transfer
                val selectedTransfer = if (transfers.size > 1) {
                    log.info("$id: Racing ${transfers.size} connection attempts - first to connect wins")
                    raceConnectionAttempts(transfers)
                } else {
                    // Single transfer - just open it directly
                    log.info("$id: Single transfer - opening directly")
                    val transfer = transfers.first()

                    // IMPORTANT: Set data channels BEFORE calling open() for channel-based transports
                    // This prevents a deadlock where:
                    // 1. receiveDeviceRequest() is called during BLE handshake and waits on awaitChannels()
                    // 2. awaitChannels() waits for setDataChannels() to be called
                    // 3. setDataChannels() was only called after open() completes
                    // 4. But open() waits for BLE handshake which can take time
                    //
                    // The channels exist as soon as setupTransfer() completes, so we can set them early.
                    val transferAdapter = transfer as? TransferAdapter
                    val incomingChannel = transferAdapter?.getIncomingDataChannel()
                    val outgoingChannel = transferAdapter?.getOutgoingDataChannel()

                    if (incomingChannel != null && outgoingChannel != null) {
                        log.info("$id: Setting data channels BEFORE open() for channel-based transport...")
                        (manager as TransferManagerImpl).setDataChannels(incomingChannel, outgoingChannel)
                        log.info("$id: Data channels set on manager (before open)")
                    }

                    val openResult = transfer.open(
                        transfer = this@TransferInstanceImpl,
                    )
                    if (openResult.isErr) {
                        val error = openResult.error
                        log.error("$id: Failed to open single transfer: $error")

                        // Check if the error was caused by cancellation
                        val exception = (error as? IdkError)?.exception
                        if (exception is CancellationException) {
                            log.info("$id: Transfer opening was cancelled - propagating cancellation")
                            throw exception
                        }

                        throw IllegalStateException("Failed to open transfer: $error")
                    }
                    log.info("$id: Single transfer opened successfully")
                    transfer
                }

                log.info("$id: Connection established via ${selectedTransfer.connectionMethod.transportType}")

                // Close all losing transfers
                val losingTransfers = transfers.filter { it != selectedTransfer }
                if (losingTransfers.isNotEmpty()) {
                    log.info("$id: Closing ${losingTransfers.size} losing transfer(s)")
                    losingTransfers.forEach { transfer ->
                        try {
                            log.debug("$id: Closing losing transfer: ${transfer.connectionMethod.transportType}")
                            transfer.close()
                        } catch (e: Exception) {
                            log.warn("$id: Error closing losing transfer: ${e.message}")
                        }
                    }
                }

                _transfer.value = selectedTransfer

                // Extract and set data channels from the winning transfer (if it uses channels)
                // Some transports (OID4VP, REST API) complete the full exchange during open()
                // and don't use data channels - they return null from getIncomingDataChannel()
                //
                // For single transfer with channels, this was already done above before open().
                // For raced transfers, we need to set channels here after selecting the winner.

                // All transfers are wrapped in TransferAdapter, so check if channels exist by calling the methods
                val transferAdapter = selectedTransfer as? TransferAdapter
                val incomingChannel = transferAdapter?.getIncomingDataChannel()
                val outgoingChannel = transferAdapter?.getOutgoingDataChannel()

                if (incomingChannel != null && outgoingChannel != null) {
                    // setDataChannels is idempotent - it's safe to call again if already set
                    log.info("$id: Ensuring data channels are set on manager...")
                    (manager as TransferManagerImpl).setDataChannels(incomingChannel, outgoingChannel)
                    log.info("$id: Data channels confirmed on manager")

                    // Wait for data channels to be fully initialized
                    log.info("$id: Waiting for data channels to be ready...")
                    (manager as? TransferManagerImpl)?.awaitChannels()
                    log.info("$id: Data channels initialized and ready")
                } else {
                    log.info("$id: Transfer does not use data channels (transport: ${selectedTransfer.connectionMethod.transportType}) - request available immediately")

                    // For non-channel transports (OID4VP, REST API), the request is ready immediately after open()
                    // Trigger receiveDeviceRequest() to create DeviceRequest and dispatch DocumentsSelectionProcessStart event
                    log.info("$id: Triggering receiveDeviceRequest() for non-channel transport...")
                    val deviceRequestResult = manager.tryOps().receiveDeviceRequest()
                    if (deviceRequestResult.isOk) {
                        log.info("$id: DeviceRequest received and DocumentsSelectionProcessStart dispatched")
                    } else {
                        log.error("$id: Failed to receive device request: ${deviceRequestResult.error}")
                        throw IllegalStateException("Failed to receive device request from ${selectedTransfer.connectionMethod.transportType} transport")
                    }
                }

                (this@TransferInstanceImpl).asOkResult()
            } catch (e: CancellationException) {
                // Propagate cancellation without wrapping it - this is not an error
                log.info("$id: Transfer start was cancelled - propagating cancellation")
                throw e
            } catch (e: Throwable) {
                log.error("$id: Error starting transfer instance", exception = e)
                IdkError.UNKNOWN_ERROR(exception = e).asErrorResult()
            }
        }
    }

    /**
     * Race multiple connection attempts and return the first one to succeed.
     *
     * All transfers are opened in parallel. The first one to successfully connect
     * wins, and all other attempts are cancelled.
     *
     * @param transfers List of transfers to race
     * @return The winning transfer (first to connect successfully)
     * @throws CancellationException if the coroutine scope is cancelled
     * @throws IllegalStateException if all connection attempts fail
     */
    private suspend fun raceConnectionAttempts(
        transfers: List<IMdocTransfer<*>>
    ): IMdocTransfer<*> = coroutineScope {
        log.info("$id: Racing ${transfers.size} connection attempts in parallel")

        // Launch async connection attempts for all transfers
        val deferreds = transfers.mapIndexed { index, transfer ->
            async {
                try {
                    log.info("$id: [Attempt ${index + 1}/${transfers.size}] Starting ${transfer.connectionMethod.transportType}")

                    val openResult = transfer.open(
                        transfer = this@TransferInstanceImpl,
                    )

                    if (openResult.isOk) {
                        log.info("$id: [Attempt ${index + 1}/${transfers.size}] SUCCESS: ${transfer.connectionMethod.transportType} connected")
                        transfer  // Return the successful transfer
                    } else {
                        log.info("$id: [Attempt ${index + 1}/${transfers.size}] FAILED: ${transfer.connectionMethod.transportType} - ${openResult.error}")
                        null
                    }
                } catch (e: CancellationException) {
                    // Propagate cancellation - this is expected when parent scope is cancelled
                    log.debug("$id: [Attempt ${index + 1}/${transfers.size}] CANCELLED: ${transfer.connectionMethod.transportType}")
                    throw e
                } catch (e: Exception) {
                    log.debug("$id: [Attempt ${index + 1}/${transfers.size}] EXCEPTION: ${transfer.connectionMethod.transportType} - ${e.message}")
                    null
                }
            }
        }

        // Use select to wait for the first successful connection
        log.info("$id: Waiting for first connection to succeed...")
        val winner = select<IMdocTransfer<*>?> {
            deferreds.forEachIndexed { index, deferred ->
                deferred.onAwait { result ->
                    if (result != null) {
                        log.info("$id: [Winner] Connection ${index + 1} won the race: ${result.connectionMethod.transportType}")
                        result
                    } else {
                        null  // This attempt failed, keep waiting
                    }
                }
            }
        } ?: run {
            // All attempts returned null - all failed
            log.error("$id: All ${transfers.size} connection attempts failed")
            throw IllegalStateException("All connection attempts failed. Check BLE permissions and device availability.")
        }

        // Cancel all other deferred jobs (they're still running if they haven't completed)
        log.info("$id: Cancelling ${deferreds.size - 1} losing connection attempts")
        deferreds.forEach { deferred ->
            if (!deferred.isCompleted || deferred.getCompleted() != winner) {
                deferred.cancel()
            }
        }

        log.info("$id: Connection race complete - winner: ${winner.connectionMethod.transportType}")
        winner
    }

    override fun getRetrievalEventListeners(): Set<MdocRetrievalEvent.Listener> {
        // Return a defensive copy to avoid concurrent modification
        return listeners.toSet()
    }

    override fun addRetrievalEventListener(vararg listener: MdocRetrievalEvent.Listener) {
        instanceCoroutineScope.launch {
            listenersMutex.withLock {
                listeners.addAll(listener)
            }
        }
    }

    override fun removeRetrievalEventListener(listener: MdocRetrievalEvent.Listener) {
        instanceCoroutineScope.launch {
            listenersMutex.withLock {
                listeners.remove(listener)
            }
        }
    }

    override fun clearRetrievalEventListeners() {
        instanceCoroutineScope.launch {
            listenersMutex.withLock {
                listeners.clear()
            }
        }
    }

    override fun close() {
        log.debug("$id: closing transfer instance")
        try {
            dispatch(MdocRetrievalEvent.Terminated(engagement.id))
            val currentTransfer = _transfer.value
            currentTransfer?.close()
        } catch (e: Throwable) {
            log.error("$id: Error closing transfer instance", exception = e)
        } finally {
            // TODO:
            // Potential race condition with dispatch above!
            this.instanceCoroutineScope.cancel()
            instanceCoroutineScope.launch {
                listenersMutex.withLock {
                    listeners.clear()
                }
            }
            log.debug("$id: transfer instance closed")
        }
    }

    override fun toString(): String {
        return "TransferInstance(engagement=${engagement.id}, type=$type, id=$id, transmissionTypesSupported=${stringify(transmissionTypesSupported)}, connectionMethods=${
            stringify(
                connectionMethods
            )
        }, transmissionTypeSelected=${_transmissionTypeSelected.value})"
    }

    override fun dispatch(event: MdocRetrievalEvent) {
        dispatch(event, null)
    }

    fun dispatch(event: MdocRetrievalEvent, engagementEvent: com.sphereon.mdoc.engagement.MdocEngagementEvent?) {
        instanceCoroutineScope.launch {
            // A transfer is considered active if it has reached TransmissionTypeSelected
            val isActive = _transmissionTypeSelected.value != null

            // Get the engagement event for context - use provided event, stored event, or fall back to replay cache
            val contextEvent = engagementEvent ?: latestEngagementEvent.value ?: engagement.events.replayCache.lastOrNull()
            log.debug("$id: Dispatching mdoc retrieval event with state: ${event.state} [isActive=$isActive, engagementState=${contextEvent?.state}, usingProvided=${engagementEvent != null}, usingStored=${engagementEvent == null && latestEngagementEvent.value != null}]")

            // Wrap the event to include engagement event context if available
            val eventWithContext = if (contextEvent != null) {
                MdocRetrievalEventWithEngagement(event, contextEvent)
            } else {
                log.debug("$id: No engagement event context available for retrieval event")
                event
            }

            _events.emit(eventWithContext)
            _states.emit(eventWithContext.state)

            // Get a snapshot of listeners under lock to avoid concurrent modification during iteration
            val listenerSnapshot = listenersMutex.withLock {
                listeners.toList()
            }
            listenerSnapshot.onRetrievalEvent(eventWithContext)
        }

    }

}

fun DataRetrievalTransmissionType.Companion.fromMdocTransfer(transfer: IMdocTransfer<*>): DataRetrievalTransmissionType {
    return when (transfer.connectionMethod.transportType) {
        TransportType.BLE -> DataRetrievalTransmissionType.BLE
        TransportType.REST_API -> DataRetrievalTransmissionType.WEBSITE
        else -> throw IllegalArgumentException("Unsupported transport type: ${transfer.connectionMethod.transportType}")
    }
}
