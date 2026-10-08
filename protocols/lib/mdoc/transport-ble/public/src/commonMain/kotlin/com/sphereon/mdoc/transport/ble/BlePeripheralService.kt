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

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.mdoc.transport.ble

import com.sphereon.core.api.IdkResult
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.model.GattService
import com.sphereon.mdoc.MdocRole
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Interface for BLE peripheral mode service.
 *
 * In peripheral mode, the device advertises and accepts connections from central devices.
 * This is used when:
 * - Holder (MDOC) advertises for reader to connect
 * - Reader (MDOC_READER) advertises for holder to connect (reverse role mode)
 *
 * ## Lifecycle
 *
 * 1. Create service with explicit parameters (no EngagementInstance)
 * 2. Call `awaitAdvertising()` to start advertising the service
 * 3. Call `awaitConnection()` to wait for a central to connect
 * 4. Call `start()` to begin data transfer
 * 5. Use `incomingChannel()` and `outgoingChannel()` for data transfer
 * 6. Call `close()` when done
 *
 * ## Decoupled Design
 *
 * This interface does NOT depend on `EngagementInstance`. Instead, it takes
 * explicit parameters:
 * - `role` - The role of this party (MDOC or MDOC_READER)
 * - `characteristics` - The BLE service characteristics to use
 * - `serviceUuid` - The service UUID to advertise
 * - Data channels injected explicitly
 *
 * This allows the service to be created and tested independently of the
 * engagement infrastructure.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlePeripheralService", exact = true)
@JsExportCompat
interface BlePeripheralService : AutoCloseable {
    /**
     * The role of this party (MDOC or MDOC_READER).
     */
    val role: MdocRole

    /**
     * Start advertising the BLE service with the specified UUID.
     * The peripheral will advertise until a central device connects.
     *
     * @param serviceUuid The service UUID to advertise
     * @return Success or error
     */
    suspend fun awaitAdvertising(serviceUuid: Uuid): IdkResult<Unit, BleError>

    /**
     * Wait for a central device to connect and establish the connection.
     *
     * This method:
     * 1. Waits for power on (mainly for iOS)
     * 2. Calculates ident value from sender key
     * 3. Waits for central to connect
     * 4. Returns the GATT service
     *
     * @param senderKey The sender's public key (for ident calculation)
     * @return The GATT service, or an error
     */
    suspend fun awaitConnection(senderKey: CoseKeyType): IdkResult<GattService, BleError>

    /**
     * Start the data transfer.
     * In peripheral mode, this is a no-op as the connection is already established.
     *
     * @param service The GATT service (ignored in peripheral mode)
     * @return Success or error
     */
    suspend fun start(service: GattService): IdkResult<Unit, BleError>

    /**
     * Read DeviceEngagement from State characteristic for reverse engagement.
     *
     * @return DeviceEngagement bytes or error
     */
    suspend fun readDeviceEngagement(): IdkResult<ByteArray, BleError>

    /**
     * Get the incoming data channel for receiving messages from the central device.
     *
     * @return The incoming data channel
     */
    fun incomingChannel(): BleIncomingDataChannel

    /**
     * Get the outgoing data channel for sending messages to the central device.
     *
     * @return The outgoing data channel
     */
    fun outgoingChannel(): BleOutgoingDataChannel

    /**
     * Set callbacks for asynchronous events.
     *
     * @param onError Called if an error occurs asynchronously
     * @param onClosed Called on transport-specific termination
     */
    fun setCallbacks(
        onError: (error: Throwable) -> Unit,
        onClosed: () -> Unit,
    )
}

/**
 * Interface for HKDF-SHA256 provider.
 * This is abstracted because the implementation is platform-specific.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("HkdfProvider", exact = true)
@JsExportCompat
interface HkdfProvider {
    /**
     * Derive key material using HKDF-SHA256.
     *
     * @param ikm Input keying material
     * @param salt Optional salt value (empty if not used)
     * @param info Optional context information
     * @param outputLength Length of output key material in bytes
     * @return Derived key material
     */
    suspend fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int,
    ): ByteArray
}
