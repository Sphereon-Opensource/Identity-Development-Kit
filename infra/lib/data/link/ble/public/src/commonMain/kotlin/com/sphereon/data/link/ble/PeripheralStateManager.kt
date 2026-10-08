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

package com.sphereon.data.link.ble

import com.sphereon.core.api.IdkResult
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.coroutines.flow.Flow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("PeripheralStateManager", exact = true)
interface PeripheralStateManager {
    suspend fun start(): IdkResult<Unit, BleErrors>

    suspend fun stop(): IdkResult<Unit, BleErrors>

    suspend fun notify(
        service: String,
        characteristic: String,
        value: ByteArray,
    ): IdkResult<Unit, BleErrors>

    @JsExportIgnoreCompat
    val state: Flow<PeripheralState>
}

@JsExportCompat
sealed class PeripheralState {
    object Idle : PeripheralState()

    object Advertising : PeripheralState()

    object Stopping : PeripheralState()
}
