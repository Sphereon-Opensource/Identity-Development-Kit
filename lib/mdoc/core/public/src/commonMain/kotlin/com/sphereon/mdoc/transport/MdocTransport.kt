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

package com.sphereon.mdoc.transport

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.EngagementData
import com.sphereon.mdoc.engagement.MdocEngagementState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Interface for mdoc transfer implementations.
 *
 * This is the core abstraction for transport-agnostic data exchange between
 * holder and reader. Each transport (BLE, NFC, REST API, OID4VP) provides
 * its own implementation of this interface.
 *
 * ## Lifecycle
 *
 * 1. **Create**: Instantiate with connection method and engagement data
 * 2. **Open**: Establish connection and perform handshake
 * 3. **Exchange**: Send and receive encrypted messages
 * 4. **Close**: Clean up resources
 *
 * ## Thread Safety
 *
 * Implementations should be thread-safe for concurrent calls to
 * messageReceiveBlocking() and messageSendBlocking() if the transport
 * supports full-duplex communication.
 *
 * @param T The type of connection identifier (UUID for BLE, String for REST API, etc.)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("IMdocTransfer", exact = true)
interface MdocTransport<T> : AutoCloseable {
    val engagementData: EngagementData? // null for reader in forward engagement for now

    /**
     * Connection method used by this transfer.
     */
    val connectionMethod: ConnectionMethod

    /**
     * Role of this party (MDOC for holder, MDOC_READER for reader).
     */
    val role: MdocRole

    /**
     * Session execution context for coroutines and logging.
     */
    val execution: SessionExecution

    /**
     * Current state of the engagement.
     *
     * States progress through:
     * INITIALIZING → CONNECTING → CONNECTED → TRANSFERRING → COMPLETED/ERROR
     */
    val engagementState: StateFlow<MdocEngagementState>

    /**
     * Open the connection and perform any necessary handshake.
     *
     * This establishes the transport-level connection (e.g., BLE connection,
     * HTTP connection) and performs any transport-specific initialization.
     *
     * After successful open(), the transfer is ready for message exchange.
     *
     * @param senderKey The sender's ephemeral public key (for session encryption)
     * @param id Connection identifier (transport-specific)
     * @return Result with connection identifier or error
     */
    suspend fun open(
        senderKey: CoseKeyType,
        id: T,
        engagementData: EngagementData? = null,
    ): IdkResult<T, IdkErrorType>

    /**
     * Receive a message (blocking).
     *
     * This blocks until a complete message is received from the remote party.
     * The message is already decrypted if transport-level encryption is used
     * (session-level encryption is handled separately by TransferManager).
     *
     * ## Threading
     *
     * This method blocks the calling coroutine until data is available.
     * Implementations should use appropriate timeout mechanisms.
     *
     * @return Raw message bytes
     * @throws Exception if receive fails or timeout occurs
     */
    suspend fun messageReceiveBlocking(): ByteArray

    /**
     * Send a message (blocking).
     *
     * This blocks until the complete message is sent to the remote party.
     * For channel-based transports (BLE/NFC), the message should already be
     * session-encrypted by TransferManager. For direct transports (REST API, OID4VP),
     * the transport is responsible for any required wrapping or encryption.
     *
     * ## Threading
     *
     * This method blocks the calling coroutine until data is sent.
     * Implementations should handle chunking if needed.
     *
     * @param message Raw message bytes to send
     * @throws Exception if send fails
     */
    suspend fun messageSendBlocking(message: ByteArray)

    /**
     * Set context data for sending (optional, transport-specific).
     *
     * Some transports may need additional context beyond the raw message bytes.
     * For example, OID4VP needs the DeviceResponse object to avoid encode/decode issues.
     *
     * Default implementation does nothing.
     *
     * @param key Context key
     * @param value Context value
     */
    fun setContextForSending(
        key: String,
        value: Any,
    ) {
        // Default: no-op
    }

    /**
     * Get context data (optional, transport-specific).
     *
     * Some transports may provide additional context.
     * For example, OID4VP can provide the SessionTranscript.
     *
     * Default implementation returns null.
     *
     * @param key Context key
     * @return Context value or null
     */
    fun getContext(key: String): Any? {
        // Default: no-op
        return null
    }

    /**
     * Get the incoming data channel if this transport uses data channels.
     *
     * This is used by TransferManager to access transport-specific data channels
     * for BLE event listening. Not all transports have data channels (e.g., REST API).
     *
     * @return The incoming data channel, or null if not applicable for this transport
     */
    fun getIncomingDataChannel(): Any? = null

    /**
     * Get the outgoing data channel if this transport uses data channels.
     *
     * This is used by TransferManager to access transport-specific data channels
     * for BLE event listening. Not all transports have data channels (e.g., REST API).
     *
     * @return The outgoing data channel, or null if not applicable for this transport
     */
    fun getOutgoingDataChannel(): Any? = null
}

/**
 * Abstract base implementation providing common functionality.
 *
 * This provides:
 * - State management
 * - Connection tracking
 * - Basic lifecycle management
 *
 * Transport-specific implementations should extend this class.
 *
 * @param T Connection identifier type
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AbstractMdocTransfer", exact = true)
abstract class AbstractMdocTransport<T>(
    override val connectionMethod: ConnectionMethod,
    override val execution: SessionExecution,
) : MdocTransport<T> {
    private var _engagementData: EngagementData? = null
    override val engagementData: EngagementData?
        get() = _engagementData

    /**
     * Whether the connection is open.
     */
    protected var isOpen: Boolean = false
        private set

    /**
     * Engagement state flow (mutable for subclasses).
     */
    protected val mutableEngagementState = MutableStateFlow(MdocEngagementState.INIT)

    /**
     * Public read-only view of engagement state.
     */
    override val engagementState: StateFlow<MdocEngagementState> = mutableEngagementState.asStateFlow()

    protected fun setEngagementData(data: EngagementData?) {
        _engagementData = data
    }

    /**
     * Mark connection as open.
     * Called by subclasses after successful open().
     */
    protected fun markOpen() {
        isOpen = true
        mutableEngagementState.value = MdocEngagementState.CONNECTED
    }

    /**
     * Mark connection as closed.
     * Called by subclasses during close().
     */
    protected fun markClosed() {
        isOpen = false
        if (mutableEngagementState.value !in setOf(MdocEngagementState.ERROR, MdocEngagementState.DISCONNECTED)) {
            mutableEngagementState.value = MdocEngagementState.DISCONNECTED
        }
    }

    /**
     * Check if connection is open, throw if not.
     */
    protected fun requireOpen() {
        require(isOpen) { "Connection is not open. Call open() first." }
    }

    /**
     * Default close implementation.
     * Subclasses should override to clean up transport-specific resources.
     */
    override fun close() {
        markClosed()
    }
}
