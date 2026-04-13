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
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.mdoc.SessionEncryption
import com.sphereon.mdoc.engagement.EngagementInstance
import com.sphereon.mdoc.transfer.TransferManager
import com.sphereon.mdoc.transport.IncomingDataChannel
import com.sphereon.mdoc.transport.OutgoingDataChannel

/**
 * Reader connection for reverse engagement (holder scans reader's QR).
 *
 * In reverse engagement:
 * 1. Reader creates ReaderEngagement and shows QR
 * 2. Holder scans QR and parses ReaderEngagement
 * 3. Holder determines BLE role (opposite of reader's announcement)
 * 4. Holder initiates connection to reader
 * 5. Reader waits for connection and starts transfer manager
 *
 * This implementation wraps the EngagementInstance created by
 * MdocReaderEngagementManager.createReaderEngagement() and extracts
 * the data channels from the TransferManager after the holder connects.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ReverseReaderConnection", exact = true)
class ReverseReaderConnection(
    private val readerEngagement: EngagementInstance,
    private val logService: SessionLogService
) : ReaderConnection {

    private val log = logService.logManager.withTag("ReverseReaderConnection")

    private var _isConnected = false
    override val isConnected: Boolean
        get() = _isConnected

    private lateinit var _transferManager: TransferManager

    /**
     * Get the transfer manager for this reverse engagement.
     * Exposed for accessing session transcript and other transfer-level information.
     */
    val transferManager: TransferManager
        get() {
            check(_isConnected) { "Connection not established. Call establish() first." }
            return _transferManager
        }

    override val incomingChannel: IncomingDataChannel
        get() {
            check(_isConnected) { "Connection not established. Call establish() first." }
            return _transferManager.incomingDataChannel
        }

    override val outgoingChannel: OutgoingDataChannel
        get() {
            check(_isConnected) { "Connection not established. Call establish() first." }
            return _transferManager.outgoingDataChannel
        }

    private lateinit var _sessionEncryption: SessionEncryption
    override val sessionEncryption: SessionEncryption
        get() {
            check(_isConnected) { "Connection not established. Call establish() first." }
            return _sessionEncryption
        }

    override suspend fun establish(): IdkResult<Unit, IdkError> {
        log.info("Establishing reverse engagement connection (holder scans reader's QR)")

        try {
            // Start the engagement and wait for holder to connect
            // This will:
            // 1. Start BLE advertising/scanning based on reader's announcement
            // 2. Wait for holder to connect
            // 3. Initialize transfer manager with data channels
            log.debug("Starting reader engagement and waiting for holder to connect...")

            val startResult = readerEngagement.tryOps().start()
            if (startResult.isErr) {
                log.error("Failed to start reader engagement: ${startResult.error.message}")
                return IdkError.UNKNOWN_ERROR(
                    message = "Failed to start reader engagement: ${startResult.error.message}",
                    exception = startResult.error.exception
                ).asErrorResult()
            }

            _transferManager = startResult.value

            // Wait for data channels to be initialized before marking as connected
            // This ensures channels are ready when accessed via incomingChannel/outgoingChannel properties
            log.debug("Waiting for data channels to be initialized...")
            _transferManager?.awaitChannels()
            log.debug("Data channels ready")

            _isConnected = true

            log.info("Reverse connection established - holder connected to reader")

            return Unit.asOkResult()
        } catch (e: Exception) {
            log.error("Exception establishing reverse connection", exception = e)
            return IdkError.UNKNOWN_ERROR(
                message = "Failed to establish reverse connection: ${e.message}",
                exception = e
            ).asErrorResult()
        }
    }

    fun setSessionEncryption(sessionEncryption: SessionEncryption) {
        _sessionEncryption = sessionEncryption
        log.debug("Session encryption configured for reverse connection")
    }

    override fun close() {
        log.debug("Closing reverse reader connection")
        _isConnected = false
        if (::_transferManager.isInitialized) {
            _transferManager.close()
        }
    }
}
