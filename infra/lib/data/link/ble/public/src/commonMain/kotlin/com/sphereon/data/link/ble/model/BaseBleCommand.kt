/*
 * Â© 2026 Sphereon International B.V.
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

package com.sphereon.data.link.ble.model

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.link.ble.BleError
import com.sphereon.data.link.ble.BleErrors
import com.sphereon.data.link.ble.BleResponse
import kotlinx.coroutines.delay

@JsExportCompat
abstract class BaseBleCommand<Arg : Any, BleResp : BleResponse>(
    private val maxRetries: Int = 0,
    private val retryDelayMs: Long = 500,
) : BleCommand<Arg, BleResp> {
    override val isEnabled: Boolean = true

    override suspend fun execute(args: Arg): IdkResult<BleResp, BleError> {
        var attempt = 0
        var lastError: IdkResult<BleResp, BleError>? = null

        while (attempt <= maxRetries) {
            val result =
                runCatching { doExecute(args) }
                    .getOrElse { return BleErrors.unknown(it.message ?: "Exception", it).asErrorResult() }

            if (result.isOk) {
                return result
            }

            lastError = result
            attempt++
            if (attempt <= maxRetries) {
                delay(retryDelayMs)
            }
        }
        return lastError ?: BleErrors.unknown(reason = "Unknown error, after $maxRetries was exhausted").asErrorResult()
    }

    protected abstract suspend fun doExecute(args: Arg): IdkResult<BleResp, BleError>
}
