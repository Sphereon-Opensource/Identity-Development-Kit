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

package com.sphereon.data.link.ble.client.cmd

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.session.CommandAdapter
import com.sphereon.core.api.session.CommandErrorMapper
import com.sphereon.core.api.session.CommandId
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.BleErrors
import com.sphereon.data.link.ble.BleResponse
import com.sphereon.data.link.ble.model.IHasAddress
import com.sphereon.di.session.SessionContext
import kotlin.uuid.ExperimentalUuidApi

@JsExportCompat
class FilterDeviceFromScanResultCommand(
    val device: IHasAddress,
    val maxRetries: Int? = 1,
) : CommandAdapter<BleResponse.Devices, ConnectArgs, BleError>(
        id = COMMAND_ID,
        errorMapper = BleCommandErrorMapper,
    ) {
    override suspend fun doExecute(
        args: BleResponse.Devices,
        applyDuring: (BleResponse.Devices) -> BleResponse.Devices,
    ): IdkResult<ConnectArgs, BleError> =
        args.devices.firstOrNull { it.address == device.address }?.let { ConnectArgs(device = it, maxRetries = maxRetries ?: 1).asOkResult() }
            ?: BleErrors.deviceNotFound(device.address).asErrorResult()

    companion object {
        const val COMMAND_ID: String = "ble.device.filter"
    }
}

private object BleCommandErrorMapper : CommandErrorMapper<BleError> {
    override fun unsupportedArg(
        command: Any,
        arg: Any,
    ): BleError = BleErrors.notSupported("Unsupported arguments for BLE command: $arg")

    override fun commandDisabled(commandId: String): BleError = BleErrors.notSupported("BLE command '$commandId' is disabled")

    override fun commandSkipped(
        commandId: String,
        reason: String?,
    ): BleError = BleErrors.cancelled(reason = reason ?: "BLE command '$commandId' was skipped")

    override fun notAuthorized(
        commandId: CommandId,
        reason: String,
    ): BleError = BleErrors.notSupported("Not authorized to execute '${commandId.value}': $reason")

    override fun unknown(
        message: String,
        cause: Throwable?,
    ): BleError = BleErrors.unknown(reason = message, throwable = cause)

    override fun allHandlersFailed(errors: List<BleError>): BleError = BleErrors.unknown(reason = "All BLE handlers failed", causes = errors)

    override fun invalidCommandId(commandId: String): BleError = BleErrors.notSupported("Invalid command ID format: $commandId")
}
