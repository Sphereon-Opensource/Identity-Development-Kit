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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.RestApiOptions
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.mdoc.transport.ConnectionMethodBase
import com.sphereon.mdoc.transport.IncomingDataChannel
import com.sphereon.mdoc.transport.MdocTransport
import com.sphereon.mdoc.transport.MdocTransportRegistry
import com.sphereon.mdoc.transport.OutgoingDataChannel
import com.sphereon.mdoc.transport.TransportType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.Uuid

@Inject
@ContributesBinding(SessionScope::class, binding = binding<MdocTransferFactory>())
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocTransferFactoryImpl", exact = true)
class MdocTransferFactoryImpl(
    val transportRegistry: MdocTransportRegistry,  // Use modular transport registry
    val execution: SessionExecution
) : MdocTransferFactory {

    private val log = execution.log.logManager.withTag("MdocTransferFactory")

    init {
        log.info("MdocTransferFactory initialized with modular transports")
        log.info("Available transports: ${transportRegistry.supportedTransports}")
    }

    override fun setupTransfer(connectionMethod: ConnectionMethod, transfer: TransferInstance): IMdocTransfer<*> {
        val data = transfer.engagement.data
        val role = data.getRole()

        log.info("Setting up transfer for ${connectionMethod.transportType} role=$role")

        // Get modular transport factory from registry
        val factory = transportRegistry.getFactory(connectionMethod.transportType)
            ?: throw IllegalStateException("No transport factory for ${connectionMethod.transportType}")

        // The connectionMethod is already a NEW ConnectionMethod (from transport-core)
        // No conversion needed - pass it directly to the factory
        val transportConnectionMethod = connectionMethod

        log.info("Using modular factory: ${factory::class.simpleName}")

        // Create transfer using new modular factory
        // Pass engagement data for transports that need it (e.g., REST API)
        val newTransfer = factory.createTransfer(
            connectionMethod = transportConnectionMethod,
            execution = execution,
            role = role,
            engagementData = data  // Pass MdocEngagementData
        )

        // Wrap the new transport.IMdocTransfer in old datatransfer.IMdocTransfer adapter
        return TransferAdapter(newTransfer, connectionMethod, execution)
    }

    @ContributesTo(SessionScope::class)
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Component", exact = true)
    interface Component {
        val mdocTransferFactory: MdocTransferFactory
    }
}

/**
 * Adapter that wraps new transport.IMdocTransfer to implement old datatransfer.IMdocTransfer interface.
 * This is a thin delegation layer - all methods forward to the new modular implementation.
 *
 * Made internal so ConnectionManager can access it to extract data channels.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TransferAdapter", exact = true)
internal class TransferAdapter(
    private val delegate: MdocTransport<*>,
    override val connectionMethod: ConnectionMethod,
    private val execution: SessionExecution
) : IMdocTransfer<Any> {

    private val log = execution.log.logManager.withTag("TransferAdapter")

    /**
     * Access the underlying modular transport implementation.
     * Useful for accessing transport-specific functionality like OID4VP session transcript creation.
     */
    override fun getUnderlyingTransport(): MdocTransport<*> = delegate

    override val engagementState get() = delegate.engagementState
    override val role get() = delegate.role

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    override suspend fun open(transfer: TransferInstance, senderKey: CoseKeyType): IdkResult<Any, IdkErrorType> {
        log.debug("Delegating open() to new transport implementation with id: $transfer")

        // The new BLE transport uses kotlin.uuid.Uuid, but old interface passes String
        // Convert String UUID to kotlin.uuid.Uuid for BLE transport
        val result = when (delegate) {
            is MdocTransport<*> -> {
                // For BLE transport, extract the UUID from the connection method
                if (connectionMethod.transportType == TransportType.BLE) {
                    // Extract BLE options from connection method base class
                    // The connection method should be a ConnectionMethodBase with BleOptions
                    val baseMethod = connectionMethod as? ConnectionMethodBase<*>
                        ?: throw IllegalStateException("BLE transport but not ConnectionMethodBase: ${connectionMethod::class.simpleName}")

                    val bleOptions = baseMethod.options as? BleOptions
                        ?: throw IllegalStateException("BLE transport but options are not BleOptions: ${baseMethod.options::class.simpleName}")

                    // Determine which UUID to use based on the mode
                    val uuid = when {
                        bleOptions.peripheralServerMode -> {
                            bleOptions.peripheralServerModeUuid
                                ?: throw IllegalStateException("Peripheral server mode enabled but no UUID provided")
                        }

                        bleOptions.centralClientMode -> {
                            bleOptions.centralClientModeUuid
                                ?: throw IllegalStateException("Central client mode enabled but no UUID provided")
                        }

                        else -> throw IllegalStateException("BLE connection method has no mode enabled")
                    }

                    log.info("Using BLE UUID from connection method: $uuid (peripheral=${bleOptions.peripheralServerMode}, central=${bleOptions.centralClientMode})")

                    // Cast to Uuid-based transfer and call open
                    @Suppress("UNCHECKED_CAST")
                    val bleTransfer = delegate as MdocTransport<Uuid>
                    bleTransfer.open(senderKey, uuid, transfer.engagement.data)
                } else if (connectionMethod.transportType == TransportType.REST_API) {
                    val restApiOptions =
                        transfer.engagement.data.getRetrievalMethods().first { it.type == DeviceRetrievalMethodType.WEBSITE }.retrievalOptions as? RestApiOptions
                    requireNotNull(restApiOptions) { "Rest API options are required" }

                    (delegate as MdocTransport<String>).open(senderKey, restApiOptions.uri, transfer.engagement.data)
                } else {
                    // For other transports (REST API, etc.), use String directly
                    @Suppress("UNCHECKED_CAST")
                    (delegate as MdocTransport<String>).open(senderKey, transfer.id.toString(), transfer.engagement.data)
                }
            }
        }

        // The result is IdkResult<T, IdkErrorType>, but we need IdkResult<Any, IdkErrorType>
        // This cast is safe because Any is a supertype of T
        @Suppress("UNCHECKED_CAST")
        return result as IdkResult<Any, IdkErrorType>
    }

    override suspend fun messageSendBlocking(message: ByteArray) {
        log.debug("Delegating messageSendBlocking() - ${message.size} bytes")
        delegate.messageSendBlocking(message)
    }

    override suspend fun messageReceiveBlocking(): ByteArray {
        log.debug("Delegating messageReceiveBlocking()")
        return delegate.messageReceiveBlocking()
    }

    override fun getCurrentState() = delegate.engagementState.value

    override fun close() {
        log.debug("Delegating close()")
        delegate.close()
    }

    /**
     * Get the incoming data channel from the delegate transport.
     * Used by TransferManagerImpl to access data channels.
     *
     * Returns the common IncomingDataChannel interface (from transport-core).
     */
    fun getIncomingDataChannel(): IncomingDataChannel? {
        return delegate.getIncomingDataChannel() as? IncomingDataChannel
    }

    /**
     * Get the outgoing data channel from the delegate transport.
     * Used by TransferManagerImpl to access data channels.
     *
     * Returns the common OutgoingDataChannel interface (from transport-core).
     */
    fun getOutgoingDataChannel(): OutgoingDataChannel? {
        return delegate.getOutgoingDataChannel() as? OutgoingDataChannel
    }

    /**
     * Set context data for sending - delegates to underlying transport.
     */
    override fun setContextForSending(key: String, value: Any) {
        delegate.setContextForSending(key, value)
    }

    /**
     * Get context data - delegates to underlying transport.
     */
    override fun getContext(key: String): Any? {
        return delegate.getContext(key)
    }
}
