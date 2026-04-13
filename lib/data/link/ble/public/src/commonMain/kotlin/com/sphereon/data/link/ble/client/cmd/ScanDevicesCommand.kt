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

import com.sphereon.core.api.IdkResult
import com.sphereon.data.link.ble.BleResponse
import com.sphereon.data.link.ble.ScanError
import com.sphereon.data.link.ble.client.BlePlatformClient
import com.sphereon.data.link.ble.filter.FilterPredicateBuilder
import com.sphereon.data.link.ble.model.BaseBleCommand
import com.sphereon.di.session.SessionContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


class ScanDevicesArgs(
    val maxRetries: Int = 1,
    val retryDelay: Duration = 500.milliseconds,
    val timeout: Duration = 10.seconds,
    val maxResults: Int = Int.MAX_VALUE,
    val requestId: Uuid? = Uuid.random(),
    filterBuilder: (FilterPredicateBuilder.() -> Unit)? = null,
) {
    val filters = FilterPredicateBuilder().apply { filterBuilder?.invoke(this) }.build()?.filters

    override fun toString(): String {
        return "ScanDevicesArgs(maxRetries=$maxRetries, retryDelay=$retryDelay, timeout=$timeout, maxResults=$maxResults, requestId=$requestId, filters=$filters)"
    }
}

class ScanDevicesCommand(
    private val client: BlePlatformClient,
) : BaseBleCommand<ScanDevicesArgs, BleResponse.Devices>() {
    override val id: String = COMMAND_ID

    override suspend fun doExecute(args: ScanDevicesArgs): IdkResult<BleResponse.Devices, ScanError> {
        return client.scan(args).map { devices ->
            BleResponse.Devices(devices)
        }
    }

    companion object {
        const val COMMAND_ID: String = "ble.device.scan"
    }
}
