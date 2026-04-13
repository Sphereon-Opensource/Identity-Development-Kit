/*
 * Â© 2025 Sphereon International B.V.
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

package com.sphereon.data.link.ble.client.cmd

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.data.link.ble.BleResponse
import com.sphereon.data.link.ble.ConnectionFailedError
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.client.BlePlatformClient
import com.sphereon.data.link.ble.model.BaseBleCommand
import com.sphereon.data.link.ble.model.IHasAddress
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalObjCName::class)

@ObjCName("ConnectArgs", exact = true)

data class ConnectArgs(val device: IHasAddress, val maxRetries: Int = 1)


class ConnectDeviceCommand(
    private val client: BlePlatformClient,
) : BaseBleCommand<ConnectArgs, BleResponse.Connect>() {
    override val id: String = COMMAND_ID

    override suspend fun doExecute(args: ConnectArgs): IdkResult<BleResponse.Connect, ConnectionFailedError> {
        val (device, maxRetries) = args
        return client.connect(device).map { BleResponse.Connect(it) }
    }


    suspend fun fromScan(
        scanResult: BleResponse.Devices,
        device: IHasAddress,
        maxRetries: Int? = 1
    ): IdkResult<BleResponse.Connect, BleError> {
        val filterCommand = FilterDeviceFromScanResultCommand(device, maxRetries).execute(scanResult)
        if (filterCommand.isErr) {
            return filterCommand.error.asErrorResult()
        }
        return execute(filterCommand.value)
    }

    companion object {
        const val COMMAND_ID: String = "ble.device.connect"
    }
}
