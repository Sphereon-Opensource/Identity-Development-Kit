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

package com.sphereon.mdoc.transport.nfc

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.EngagementData
import com.sphereon.mdoc.transport.AbstractMdocTransport

/**
 * NFC transport implementation for mdoc data exchange.
 *
 * This implements the NFC-based data transfer as specified in ISO 18013-5
 * section 8.3.3. NFC is simpler than BLE as it doesn't have multiple modes
 * (central/peripheral) - the reader device simply taps against the holder's
 * device to initiate the connection.
 *
 * ## Data Exchange
 *
 * NFC uses APDU (Application Protocol Data Unit) commands for data exchange:
 * - Reader sends command APDUs to holder
 * - Holder responds with response APDUs
 *
 * The maximum data field lengths are negotiated via the NfcOptions in the
 * device engagement.
 *
 * ## Session Scope Dependencies
 *
 * The NFC APDU dispatcher is session-scoped and should be obtained from the
 * execution context when needed. This allows proper lifecycle management and
 * prevents scope mixing issues.
 *
 * @param connectionMethod NFC connection method with configuration
 * @param execution Session execution context
 * @param role Device role (MDOC or MDOC_READER)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("NfcTransfer", exact = true)
class NfcTransport(
    connectionMethod: NfcConnectionMethod,
    execution: SessionExecution,
    override val role: MdocRole
) : AbstractMdocTransport<NfcHandle>(connectionMethod, execution) {

    private val log = execution.log

    private val nfcOptions = connectionMethod.options

    override suspend fun open(
        senderKey: com.sphereon.crypto.core.cose.CoseKeyType,
        id: NfcHandle,
        engagementData: EngagementData?

    ): com.sphereon.core.api.IdkResult<NfcHandle, com.sphereon.core.api.error.IdkErrorType> {
        log.info("Opening NFC transfer: role=$role, maxCommand=${nfcOptions.maxCommandDataFieldLength}, maxResponse=${nfcOptions.maxResponseDataFieldLength}")

        // TODO: Implement actual NFC opening logic
        // This would involve:
        // 1. Setting up NFC service selection
        // 2. Establishing NDEF handover if needed
        // 3. Preparing APDU command/response channels

        _engagementData = engagementData
        markOpen()

        return Ok(
            NfcHandle(
                maxCommandLength = nfcOptions.maxCommandDataFieldLength,
                maxResponseLength = nfcOptions.maxResponseDataFieldLength
            )
        )
    }

    override suspend fun messageReceiveBlocking(): ByteArray {
        requireOpen()
        log.debug("Receiving data over NFC")

        // TODO: Implement actual NFC receiving
        // This would involve:
        // 1. Obtaining NfcApduDispatcher from execution context
        // 2. Receiving APDU commands via nfcApduDispatcher
        // 3. Reassembling fragmented data
        // 4. Sending response APDUs

        throw NotImplementedError("NFC receive not yet implemented")
    }

    override suspend fun messageSendBlocking(message: ByteArray) {
        requireOpen()
        log.debug("Sending ${message.size} bytes over NFC")

        // TODO: Implement actual NFC sending
        // This would involve:
        // 1. Obtaining NfcApduDispatcher from execution context
        // 2. Fragmenting data according to maxCommandDataFieldLength
        // 3. Sending APDU commands via nfcApduDispatcher
        // 4. Handling response status words

        throw NotImplementedError("NFC send not yet implemented")
    }

    override fun close() {
        log.info("Closing NFC transfer")

        // TODO: Implement actual NFC closing
        // This would involve:
        // 1. Sending termination APDU
        // 2. Cleaning up NFC resources

        super.close()
    }
}

/**
 * Handle representing an open NFC connection.
 *
 * @param maxCommandLength Maximum length of command APDU data field
 * @param maxResponseLength Maximum length of response APDU data field
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("NfcHandle", exact = true)
data class NfcHandle(
    val maxCommandLength: UInt,
    val maxResponseLength: UInt
)
