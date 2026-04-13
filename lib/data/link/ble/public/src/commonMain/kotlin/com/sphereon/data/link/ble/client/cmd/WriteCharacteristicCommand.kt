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
import com.sphereon.data.link.ble.BleResponse
import com.sphereon.data.link.ble.CharacteristicWriteError
import com.sphereon.data.link.ble.client.BlePlatformClient
import com.sphereon.data.link.ble.model.BaseBleCommand
import com.sphereon.data.link.ble.model.CharacteristicWriteMode
import com.sphereon.data.link.ble.model.HasUuidId
import com.sphereon.di.session.SessionContext
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalObjCName::class)

@ObjCName("BleWriteCharacteristicArgs", exact = true)

data class BleWriteCharacteristicArgs(
    val service: HasUuidId,
    val characteristic: HasUuidId,
    val value: ByteArray,
    val writeMode: CharacteristicWriteMode = CharacteristicWriteMode.WRITE_TYPE_NO_RESPONSE,
)

class WriteCharacteristicCommand(
    private val client: BlePlatformClient
) : BaseBleCommand<BleWriteCharacteristicArgs, BleResponse.Success>() {

    override val id: String = COMMAND_ID

    override suspend fun doExecute(args: BleWriteCharacteristicArgs): IdkResult<BleResponse.Success, CharacteristicWriteError> {
        return client.writeCharacteristic(args.service, args.characteristic, args.value, args.writeMode)
            .map { BleResponse.Success }
    }

    companion object {
        const val COMMAND_ID = "ble.characteristic.write"
    }
}

