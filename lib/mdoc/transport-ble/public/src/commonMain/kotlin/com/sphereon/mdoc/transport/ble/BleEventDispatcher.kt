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

package com.sphereon.mdoc.transport.ble

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.Uuid

/**
 * Interface for dispatching BLE-related events.
 *
 * This abstraction allows data channels to dispatch events without depending
 * on the engagement infrastructure. Consumers can implement this interface
 * to receive notifications about BLE data transfer events.
 *
 * ## Usage
 *
 * ```kotlin
 * class MyEventHandler : BleEventDispatcher {
 *     override fun dispatchSessionEstablishmentReceived(instanceId: Uuid, data: ByteArray) {
 *         println("Session establishment received: ${data.size} bytes")
 *     }
 * }
 *
 * val channel = BleIncomingDataChannelImpl(
 *     logManager = logManager,
 *     role = MdocRole.MDOC,
 *     instanceId = Uuid.random(),
 *     incomingCharacteristicId = characteristicId,
 *     eventDispatcher = MyEventHandler()
 * )
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BleEventDispatcher", exact = true)
interface BleEventDispatcher {
    /**
     * Dispatched when session establishment data is received.
     *
     * Session establishment contains the encrypted DeviceRequest from the reader.
     * This is typically received by the holder (MDOC role).
     *
     * @param instanceId The unique identifier for this engagement instance
     * @param data The raw CBOR-encoded session establishment data
     */
    fun dispatchSessionEstablishmentReceived(
        instanceId: Uuid,
        data: ByteArray,
    )

    /**
     * Dispatched when session data is received.
     *
     * Session data contains the encrypted DeviceResponse from the holder.
     * This is typically received by the reader (MDOC_READER role).
     *
     * @param instanceId The unique identifier for this engagement instance
     * @param data The raw CBOR-encoded session data
     */
    fun dispatchSessionDataReceived(
        instanceId: Uuid,
        data: ByteArray,
    )

    /**
     * Dispatched when a termination request is received.
     *
     * This indicates that the remote party has requested to end the session.
     *
     * @param instanceId The unique identifier for this engagement instance
     * @param data The termination request data (typically just the 0x02 byte)
     */
    fun dispatchTerminationReceived(
        instanceId: Uuid,
        data: ByteArray,
    )

    /**
     * Dispatched when session data has been successfully sent.
     *
     * This confirms that data was transmitted to the remote party.
     *
     * @param instanceId The unique identifier for this engagement instance
     * @param data The raw data that was sent
     */
    fun dispatchSessionDataSent(
        instanceId: Uuid,
        data: ByteArray,
    )
}

/**
 * No-op implementation of BleEventDispatcher.
 *
 * Use this when you don't need event notifications.
 */
object NoOpBleEventDispatcher : BleEventDispatcher {
    override fun dispatchSessionEstablishmentReceived(
        instanceId: Uuid,
        data: ByteArray,
    ) {
        // No-op
    }

    override fun dispatchSessionDataReceived(
        instanceId: Uuid,
        data: ByteArray,
    ) {
        // No-op
    }

    override fun dispatchTerminationReceived(
        instanceId: Uuid,
        data: ByteArray,
    ) {
        // No-op
    }

    override fun dispatchSessionDataSent(
        instanceId: Uuid,
        data: ByteArray,
    ) {
        // No-op
    }
}
