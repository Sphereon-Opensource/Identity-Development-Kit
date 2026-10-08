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

package com.sphereon.data.link.nfc

import com.sphereon.data.link.nfc.model.CommandApdu
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

abstract class AbstractApduDispatcher : NfcApduDispatcher {
    private val channel = Channel<CommandApdu>(Channel.UNLIMITED)

    /** Expose as a cold flow */
    override val apdus: Flow<CommandApdu> = channel.receiveAsFlow()

    /** Called by the HostApduService */
    override suspend fun dispatch(apdu: CommandApdu) {
        channel.send(apdu)
    }

    override fun tryDispatch(apdu: CommandApdu) {
        channel.trySend(apdu)
    }

    override suspend fun receive(): CommandApdu = channel.receive()
}
