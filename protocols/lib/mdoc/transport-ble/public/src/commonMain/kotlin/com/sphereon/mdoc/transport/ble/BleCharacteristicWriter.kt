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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.link.ble.CharacteristicWriteError
import com.sphereon.data.link.ble.model.HasUuidId
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Abstraction for writing to BLE characteristics that works for both central and peripheral modes.
 *
 * This interface enables BLE outgoing data channels to work in both:
 * - **Central (client) mode**: Writes to a remote peripheral's characteristic
 * - **Peripheral (server) mode**: Notifies connected centrals of a characteristic value change
 *
 * By abstracting the write mechanism, the data channel implementation can remain mode-agnostic
 * and be reused without code duplication.
 *
 * ## Architecture
 * ```
 * BleOutgoingDataChannel
 *         |
 *         v
 * BleCharacteristicWriter (interface)
 *         |
 *    +----+----+
 *    |         |
 *    v         v
 * Central   Peripheral
 * Writer    Writer
 * ```
 *
 * @see CentralCharacteristicWriter for central mode implementation
 * @see PeripheralCharacteristicWriter for peripheral mode implementation
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BleCharacteristicWriter", exact = true)
@JsExportCompat
interface BleCharacteristicWriter {
    /**
     * Writes a value to a BLE characteristic.
     *
     * The actual behavior depends on the implementation:
     * - **Central mode** ([CentralCharacteristicWriter]): Performs a GATT write operation to the remote characteristic
     * - **Peripheral mode** ([PeripheralCharacteristicWriter]): Sends a notification to connected central devices
     *
     * @param service The GATT service containing the characteristic
     * @param characteristic The characteristic to write to / notify on
     * @param value The byte array to write / notify
     * @return IdkResult containing the number of bytes written, or an error
     */
    suspend fun writeCharacteristic(
        service: HasUuidId,
        characteristic: HasUuidId,
        value: ByteArray,
    ): IdkResult<Int, CharacteristicWriteError>
}
