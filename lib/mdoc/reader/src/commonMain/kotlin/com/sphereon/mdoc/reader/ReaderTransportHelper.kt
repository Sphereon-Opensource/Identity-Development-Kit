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
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.transport.IncomingDataChannel
import com.sphereon.mdoc.transport.MdocTransport
import com.sphereon.mdoc.transport.MdocTransportRegistry
import com.sphereon.mdoc.transport.OutgoingDataChannel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Helper for creating transports in reader forward engagement.
 *
 * The reader doesn't create an EngagementInstance in forward engagement,
 * so it can't use the standard ConnectionManager flow. This helper
 * creates transports directly for reader use.
 *
 * This replaces the old [ReaderBleConnectionHelper] with a modular transport-based approach.
 */
@OptIn(ExperimentalUuidApi::class,ExperimentalObjCName::class)
@ObjCName("ReaderTransportHelper", exact = true)
class ReaderTransportHelper(
    private val transportRegistry: MdocTransportRegistry,
    private val execution: SessionExecution,
    private val logService: SessionLogService
) {
    private val log = logService.logManager.withTag("ReaderTransportHelper")

    /**
     * Create a transport for reader forward engagement.
     *
     * This method extracts the connection method from the device engagement
     * and delegates to the appropriate transport factory. The transport factory
     * is responsible for handling role-specific logic (e.g., BLE role reversal
     * where reader acts as the opposite of the holder).
     *
     * @param deviceEngagement The holder's device engagement (from QR scan)
     * @param readerId The reader's unique ID
     * @return Result with transport or error
     */
    suspend fun createTransport(
        deviceEngagement: DeviceEngagement,
        readerId: Uuid
    ): IdkResult<MdocTransport<*>, IdkError> {

        // 1. Extract connection method from device engagement using the registry
        val connectionMethod = deviceEngagement.deviceRetrievalMethods
            ?.firstNotNullOfOrNull { retrievalMethod ->
                val factory = transportRegistry.getConnectionMethodFactory(retrievalMethod)
                factory?.create(retrievalMethod)
            }
            ?: return IdkError.NOT_FOUND_ERROR(
                message = "No supported connection method in device engagement"
            ).asErrorResult()

        log.info("Creating transport for: ${connectionMethod.transportType}")

        // 2. Get transport factory from registry
        val factory = transportRegistry.getFactory(connectionMethod.transportType)
            ?: return IdkError.NOT_FOUND_ERROR(
                message = "No factory for transport: ${connectionMethod.transportType}"
            ).asErrorResult()

        // 3. Create transport with READER role
        // The factory is responsible for handling role-specific adjustments.
        // For example, BleTransportFactory will reverse the BLE roles when it sees
        // MdocRole.MDOC_READER, making the reader act as central if holder is peripheral.
        log.info("Creating transport with connection method: ${connectionMethod::class.simpleName}")

        val transport = factory.createTransfer(
            connectionMethod = connectionMethod,
            execution = execution,
            role = MdocRole.MDOC_READER,  // ← Factory uses this to adjust behavior
            engagementData = null  // Reader doesn't have engagement data in forward engagement
        )

        log.info("Transport created: ${transport::class.simpleName}")
        return transport.asOkResult()
    }

    /**
     * Extract data channels from an opened transport.
     *
     * The transport must implement the data channel accessors.
     * This method retrieves the channels for use by the reader.
     *
     * @param transport The transport to extract channels from
     * @return Pair of (incoming, outgoing) channels, or null if transport doesn't provide channels
     */
    fun extractChannels(transport: MdocTransport<*>): Pair<IncomingDataChannel, OutgoingDataChannel>? {
        // Access the channels via the transport's accessor methods
        val incoming = transport.getIncomingDataChannel() as? IncomingDataChannel
        val outgoing = transport.getOutgoingDataChannel() as? OutgoingDataChannel

        if (incoming != null && outgoing != null) {
            log.debug("Successfully extracted channels from transport")
            return Pair(incoming, outgoing)
        } else {
            log.error("Transport does not provide data channels")
            return null
        }
    }
}
