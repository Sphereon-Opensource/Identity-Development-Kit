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

package com.sphereon.mdoc.reader

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.mdoc.SessionEncryption
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.transport.IncomingDataChannel
import com.sphereon.mdoc.transport.MdocTransport
import com.sphereon.mdoc.transport.OutgoingDataChannel
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Reader connection for forward engagement (reader scans holder's QR).
 *
 * In forward engagement:
 * 1. Holder creates DeviceEngagement and shows QR
 * 2. Reader scans QR and parses DeviceEngagement
 * 3. Reader creates transport via modular transport registry
 * 4. Reader opens transport (establishes connection)
 * 5. Reader extracts data channels from transport
 *
 * This implementation uses the modular transport layer via [ReaderTransportHelper].
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalObjCName::class)
@ObjCName("ForwardReaderConnection", exact = true)
class ForwardReaderConnection(
    private val deviceEngagement: DeviceEngagement,
    private val readerId: Uuid,
    private val transportHelper: ReaderTransportHelper,
    private val logService: SessionLogService,
) : ReaderConnection {
    private val log = logService.logManager.withTag("ForwardReaderConnection")

    private var _isConnected = false
    override val isConnected: Boolean
        get() = _isConnected

    private lateinit var _incomingChannel: IncomingDataChannel
    override val incomingChannel: IncomingDataChannel
        get() {
            check(_isConnected) { "Connection not established. Call establish() first." }
            return _incomingChannel
        }

    private lateinit var _outgoingChannel: OutgoingDataChannel
    override val outgoingChannel: OutgoingDataChannel
        get() {
            check(_isConnected) { "Connection not established. Call establish() first." }
            return _outgoingChannel
        }

    private lateinit var _sessionEncryption: SessionEncryption
    override val sessionEncryption: SessionEncryption
        get() {
            check(_isConnected) { "Connection not established. Call establish() first." }
            return _sessionEncryption
        }

    private var transport: MdocTransport<*>? = null

    override suspend fun establish(): IdkResult<Unit, IdkError> {
        log.info("Establishing forward connection via modular transports")

        // 1. Create transport
        val transportResult = transportHelper.createTransport(deviceEngagement, readerId)
        if (transportResult.isErr) {
            log.error("Failed to create transport: ${transportResult.error.message}")
            return transportResult.error.asErrorResult()
        }
        transport = transportResult.value

        // 2. Open transport (this creates the data channels)
        // Note: We use the holder's ephemeral key as a placeholder
        // The actual reader ephemeral key will be generated in sendRequest()
        try {
            log.debug("Opening transport with device engagement key")
            // Cast to IMdocTransfer<Uuid> for BLE transport
            @Suppress("UNCHECKED_CAST")
            val typedTransport = transport as MdocTransport<Uuid>
            val openResult =
                typedTransport.open(
                    senderKey = deviceEngagement.security.eDeviceKeyBytes.data(),
                    id = readerId,
                )
            if (openResult.isErr) {
                log.error("Transport open failed: ${openResult.error.message}")
                return IdkError
                    .UNKNOWN_ERROR(
                        message = "Failed to open transport: ${openResult.error.message}",
                        exception = openResult.error.exception,
                    ).asErrorResult()
            }
            log.info("Transport opened successfully")
        } catch (expected: Exception) {
            log.error("Failed to open transport", exception = expected)
            return IdkError
                .UNKNOWN_ERROR(
                    message = "Failed to open transport: ${expected.message}",
                    exception = expected,
                ).asErrorResult()
        }

        // 3. Extract data channels
        val channels =
            transportHelper.extractChannels(transport!!)
                ?: return IdkError
                    .UNKNOWN_ERROR(
                        message = "Failed to extract data channels from transport",
                    ).asErrorResult()

        _incomingChannel = channels.first
        _outgoingChannel = channels.second
        _isConnected = true

        log.info("Forward connection established successfully")
        return Unit.asOkResult()
    }

    fun setSessionEncryption(sessionEncryption: SessionEncryption) {
        _sessionEncryption = sessionEncryption
        log.debug("Session encryption configured for forward connection")
    }

    override fun close() {
        log.debug("Closing forward reader connection")
        _isConnected = false
        transport?.close()
        transport = null
    }
}
