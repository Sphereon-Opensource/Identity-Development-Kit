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

import com.sphereon.mdoc.SessionData
import com.sphereon.mdoc.SessionEstablishment
import com.sphereon.mdoc.transport.IncomingDataChannel
import com.sphereon.mdoc.transport.OutgoingDataChannel

/**
 * Interface for BLE incoming data channel.
 *
 * Handles receiving data over BLE from the remote party.
 * Data can be received as raw bytes or as structured mdoc types.
 *
 * Extends the common IncomingDataChannel interface with BLE-specific methods.
 */
interface BleIncomingDataChannel : IncomingDataChannel {
    /**
     * Get the most recently received raw data.
     *
     * @return The most recent data received, or null if none
     */
    fun getMostRecentReceivedRaw(): ByteArray?

    /**
     * Receive raw data (blocking).
     *
     * Suspends until a complete message is received.
     *
     * @return The received data
     */
    override suspend fun receiveRaw(): ByteArray

    /**
     * Receive session establishment data.
     *
     * This is typically called by the holder (MDOC) to receive
     * the encrypted DeviceRequest from the reader.
     *
     * @return The session establishment data
     * @throws IllegalArgumentException if called from wrong role
     */
    suspend fun receiveSessionEstablishment(): SessionEstablishment

    /**
     * Receive session data.
     *
     * This is typically called by the reader (MDOC_READER) to receive
     * the encrypted DeviceResponse from the holder.
     *
     * @return The session data
     */
    suspend fun receiveSessionData(): SessionData

    /**
     * Wait for external termination request.
     *
     * @return true if termination was requested
     */
    suspend fun awaitExternalTermination(): Boolean
}

/**
 * Interface for BLE outgoing data channel.
 *
 * Handles sending data over BLE to the remote party.
 * Data can be sent as raw bytes or as structured mdoc types.
 *
 * Extends the common OutgoingDataChannel interface with BLE-specific methods.
 */
interface BleOutgoingDataChannel : OutgoingDataChannel {
    /**
     * Get the most recently sent raw data.
     *
     * @return The most recent data sent, or null if none
     */
    fun getMostRecentSentRaw(): ByteArray?

    /**
     * Send raw data (blocking).
     *
     * Suspends until the data is completely sent.
     *
     * @param data The data to send
     * @return Status code (0 = success)
     */
    override suspend fun sendRaw(data: ByteArray): Int

    /**
     * Send end-of-transfer message.
     *
     * Signals to the remote party that no more data will be sent.
     *
     * @return Status code (0 = success)
     */
    override suspend fun sendEndMessage(): Int

    /**
     * Send session establishment data.
     *
     * This is typically called by the reader (MDOC_READER) to send
     * the encrypted DeviceRequest to the holder.
     *
     * @param sessionEstablishment The session establishment to send
     * @return Status code (0 = success)
     */
    suspend fun sendSessionEstablishment(sessionEstablishment: SessionEstablishment): Int

    /**
     * Send session data.
     *
     * This is typically called by the holder (MDOC) to send
     * the encrypted DeviceResponse to the reader.
     *
     * @param sessionData The session data to send
     * @return Status code (0 = success)
     */
    suspend fun sendSessionData(sessionData: SessionData): Int
}
