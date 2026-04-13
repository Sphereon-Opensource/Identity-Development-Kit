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

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.Uuid

/**
 * Interface for event dispatching from data channels.
 *
 * This allows data channels to dispatch events (e.g., SessionEstablishmentReceived)
 * without having a compile-time dependency on the datatransfer module.
 *
 * The datatransfer module can provide an implementation that bridges events
 * to its internal event system.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DataChannelEventDispatcher", exact = true)
interface DataChannelEventDispatcher {
    /**
     * Called when a session establishment message is received.
     * @param instanceId The transfer instance ID
     * @param data The raw session establishment bytes
     */
    fun dispatchSessionEstablishmentReceived(
        instanceId: Uuid,
        data: ByteArray,
    )

    /**
     * Called when session data is received.
     * @param instanceId The transfer instance ID
     * @param data The raw session data bytes
     */
    fun dispatchSessionDataReceived(
        instanceId: Uuid,
        data: ByteArray,
    )

    /**
     * Called when a termination message is received.
     * @param instanceId The transfer instance ID
     * @param data The raw termination bytes
     */
    fun dispatchTerminationReceived(
        instanceId: Uuid,
        data: ByteArray,
    )

    /**
     * Called when session data is sent.
     * @param instanceId The transfer instance ID
     * @param data The raw session data bytes that were sent
     */
    fun dispatchSessionDataSent(
        instanceId: Uuid,
        data: ByteArray,
    )
}

/**
 * Interface for incoming data channels in mdoc transports.
 *
 * This represents a channel for receiving data from the remote party.
 * Different transports (BLE, NFC, REST API) implement this interface
 * to provide their transport-specific data receiving mechanisms.
 *
 * ## Lifecycle
 *
 * 1. Channel is created by the transport factory
 * 2. Channel receives data from the remote party
 * 3. Application reads data via `receiveRaw()`
 * 4. Channel is closed when transfer completes
 *
 * ## Thread Safety
 *
 * Implementations should be thread-safe for concurrent reads if the
 * underlying transport supports it.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("IncomingDataChannel", exact = true)
interface IncomingDataChannel : AutoCloseable {
    /**
     * Check if the channel is closed.
     */
    val isClosed: Boolean
        get() = false

    /**
     * Receive raw data (blocking).
     *
     * This blocks until data is available from the remote party.
     *
     * @return Raw data bytes
     * @throws Exception if receive fails or channel is closed
     */
    suspend fun receiveRaw(): ByteArray

    /**
     * Set the event dispatcher for this channel (optional).
     * Channels that support event dispatching can implement this.
     */
    fun setEventDispatcher(dispatcher: DataChannelEventDispatcher?) {
        // Default: no-op, channels that need event dispatching override this
    }
}

/**
 * Interface for outgoing data channels in mdoc transports.
 *
 * This represents a channel for sending data to the remote party.
 * Different transports (BLE, NFC, REST API) implement this interface
 * to provide their transport-specific data sending mechanisms.
 *
 * ## Lifecycle
 *
 * 1. Channel is created by the transport factory
 * 2. Application sends data via `sendRaw()`
 * 3. Channel transmits data to the remote party
 * 4. Channel is closed when transfer completes
 *
 * ## Thread Safety
 *
 * Implementations should be thread-safe for concurrent writes if the
 * underlying transport supports it.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("OutgoingDataChannel", exact = true)
interface OutgoingDataChannel : AutoCloseable {
    /**
     * Check if the channel is closed.
     */
    val isClosed: Boolean
        get() = false

    /**
     * Send raw data (blocking).
     *
     * This blocks until data is sent to the remote party.
     *
     * @param data Raw data bytes to send
     * @return Number of bytes sent
     * @throws Exception if send fails or channel is closed
     */
    suspend fun sendRaw(data: ByteArray): Int

    /**
     * Send an end message to signal completion.
     *
     * This is used by some transports (e.g., BLE) to indicate
     * that no more data will be sent.
     *
     * @return Status code (0 = success for transports that return status)
     */
    suspend fun sendEndMessage(): Int {
        // Default implementation: no-op, return success
        return 0
    }
}
