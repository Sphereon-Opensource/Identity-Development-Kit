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
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.ScanError
import com.sphereon.data.link.ble.client.cmd.ScanDevicesArgs
import com.sphereon.data.link.ble.model.BleDevice
import com.sphereon.data.link.ble.model.GattService
import com.sphereon.mdoc.MdocRole
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Interface for BLE central mode service.
 *
 * In central mode, the device scans for and connects to BLE peripherals.
 * This is used when:
 * - Reader (MDOC_READER) connects to holder's peripheral
 * - Holder (MDOC) connects to reader's peripheral (reverse role mode)
 *
 * ## Lifecycle
 *
 * 1. Create service with explicit parameters (no EngagementInstance)
 * 2. Call `awaitConnecting()` to scan for and find peripheral
 * 3. Call `awaitConnected()` to connect and discover services/characteristics
 * 4. Call `start()` to begin data transfer
 * 5. Call `close()` when done
 *
 * ## Decoupled Design
 *
 * This interface does NOT depend on `EngagementInstance`. Instead, it takes
 * explicit parameters:
 * - `role` - The role of this party (MDOC or MDOC_READER)
 * - `characteristics` - The BLE service characteristics to use
 * - Platform dependencies injected in constructor
 *
 * This allows the service to be created and tested independently of the
 * engagement infrastructure.
 */
interface BleCentralService : AutoCloseable {
    /**
     * The role of this party (MDOC or MDOC_READER).
     */
    val role: MdocRole

    /**
     * Scan for and find a peripheral with the specified UUID.
     *
     * @param peripheralUuid The service UUID to scan for
     * @param scanSettings Scan configuration (timeout, max results, etc.)
     * @return The found BLE device, or an error
     */
    suspend fun awaitConnecting(
        peripheralUuid: Uuid,
        scanSettings: ScanDevicesArgs = ScanDevicesArgs(),
    ): IdkResult<BleDevice, ScanError>

    /**
     * Connect to the peripheral and discover services and characteristics.
     *
     * This method:
     * 1. Connects to the peripheral
     * 2. Sets MTU to maximum (512 bytes)
     * 3. Discovers services
     * 4. Discovers characteristics
     * 5. Subscribes to notifications (state and server2Client)
     *
     * @param senderKey The sender's public key (for ident calculation, if needed)
     * @param peripheral The peripheral device to connect to
     * @return The GATT service, or an error
     */
    suspend fun awaitConnected(
        senderKey: CoseKeyType,
        peripheral: BleDevice,
    ): IdkResult<GattService, BleError>

    /**
     * Start the data transfer by writing to the state characteristic.
     *
     * @param service The GATT service to use
     * @return Success or error
     */
    suspend fun start(service: GattService): IdkResult<Unit, BleError>

    /**
     * Write DeviceEngagement to State characteristic for reverse engagement.
     *
     * @param service The GATT service
     * @param deviceEngagement The holder's device engagement bytes
     * @return Success or error
     */
    suspend fun writeDeviceEngagement(
        service: GattService,
        deviceEngagement: ByteArray,
    ): IdkResult<Unit, BleError>

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
