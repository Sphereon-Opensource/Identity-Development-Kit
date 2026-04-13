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

package com.sphereon.mdoc.reader

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.mdoc.SessionEncryption
import com.sphereon.mdoc.transport.IncomingDataChannel
import com.sphereon.mdoc.transport.OutgoingDataChannel

/**
 * Abstraction for reader connection state and channels.
 *
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("abstracts", exact = true)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ReaderConnection", exact = true)
 * This interface abstracts the differences between forward and reverse engagement,
 * allowing the reader manager to use a unified API for request/response operations.
 *
 * ## Forward Engagement
 * Reader parses holder's DeviceEngagement, determines BLE role (opposite of holder),
 * and establishes connection.
 *
 * ## Reverse Engagement
 * Reader creates ReaderEngagement, holder scans and connects, reader waits for
 * connection to be established.
 *
 * In both cases, after connection is established, the reader uses the same
 * channels and session encryption for request/response exchange.
 */
interface ReaderConnection {
    /**
     * Whether the connection is established and ready for data exchange.
     */
    val isConnected: Boolean

    /**
     * Incoming data channel for receiving data from holder.
     @OptIn(ExperimentalObjCName::class)
     @ObjCName("from", exact = true)
     * Uses common channel interface from transport-core.
     */
    val incomingChannel: IncomingDataChannel

    /**
     * Outgoing data channel for sending data to holder.
     * Uses common channel interface from transport-core.
     */
    val outgoingChannel: OutgoingDataChannel

    /**
     * Session encryption for encrypting/decrypting messages.
     */
    val sessionEncryption: SessionEncryption

    /**
     * Establish the connection and initialize channels.
     *
     * Implementation differs between forward and reverse engagement:
     * - Forward: Reader initiates connection to holder
     * - Reverse: Reader waits for holder to connect
     *
     * @return Result with Unit on success, or error on failure
     */
    suspend fun establish(): IdkResult<Unit, IdkError>

    /**
     * Close the connection and clean up resources.
     */
    fun close()
}
