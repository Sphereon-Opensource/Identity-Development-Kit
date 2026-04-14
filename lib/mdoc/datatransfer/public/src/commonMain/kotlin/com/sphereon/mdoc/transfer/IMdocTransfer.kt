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

package com.sphereon.mdoc.transfer

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.MdocEngagementStateType
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.mdoc.transport.MdocTransport
import kotlinx.coroutines.flow.StateFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("for", exact = true)
 * Base interface for mdoc transfer implementations.
 * Provides common functionality for all transfer types (BLE, REST API, etc.)
 */
@JsExportCompat
interface IMdocTransfer<OpenResult : Any> : AutoCloseable {
    val connectionMethod: ConnectionMethod

    val engagementState: StateFlow<MdocEngagementStateType>
    val role: MdocRole

    /**
     * Connect to the peer
     *
     * @param senderKey The EDeviceKey for regular engagement and EReaderKey for reverse engagement
     * @param transfer The identifier to use for connection (UUID for BLE, URL for REST API, etc.)
     */
    suspend fun open(
        transfer: TransferInstance,
        senderKey: CoseKeyType =
            transfer.engagement.data
                .getEphemeralKey()
                .key,
    ): IdkResult<OpenResult, IdkErrorType>

    suspend fun messageSendBlocking(message: ByteArray)

    suspend fun messageReceiveBlocking(): ByteArray

    fun getCurrentState(): MdocEngagementStateType

    /**
     * Get the underlying transport implementation.
     * Useful for accessing transport-specific functionality (e.g., OID4VP session transcript).
     * Returns the wrapped transport for adapters, or self for direct implementations.
     */
    fun getUnderlyingTransport(): MdocTransport<*> = this as MdocTransport<*>

    /**
     * Set context data for sending - by default delegates to underlying transport.
     */
    fun setContextForSending(
        key: String,
        value: Any,
    ) {
        getUnderlyingTransport().setContextForSending(key, value)
    }

    /**
     * Get context data - by default delegates to underlying transport.
     */
    fun getContext(key: String): Any? = getUnderlyingTransport().getContext(key)
}

/**
 * Interface for transfers that support advertising (BLE peripheral mode).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("IMdocTransferWithAdvertising", exact = true)
@JsExportCompat
sealed interface IMdocTransferWithAdvertising<OpenResult : Any> : IMdocTransfer<OpenResult> {
    suspend fun startAdvertising()
}

/**
 * Interface for transfers that support scanning (BLE central mode).
 */
@JsExportCompat
sealed interface IMdocTransferWithScanning<OpenResult : Any> : IMdocTransfer<OpenResult> {
    suspend fun startScanning()
}
