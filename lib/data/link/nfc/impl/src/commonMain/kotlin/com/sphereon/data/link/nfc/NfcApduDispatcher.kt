/*
 * © 2025 Sphereon International B.V.
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

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.data.link.nfc.model.CommandApdu
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/*

*/
/**
 * A singleton that collects all incoming CommandApdu objects.
 *//*

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<INfcApduDispatcher>())
// When injected in session scope we still use the app implementation
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionNfcApduDispatcherImpl", exact = true)
class SessionNfcApduDispatcherImpl: AbstractApduDispatcher(), INfcApduDispatcher{

  @ContributesTo(SessionScope::class)

  @OptIn(ExperimentalObjCName::class)

  @ObjCName("Component", exact = true)

  interface Component {
    val sessionNfcApduDispatcher: INfcApduDispatcher
  }
}
*/


/*@Inject
@SingleIn(AppScope::class)
class AppNfcApduDispatcher: AbstractApduDispatcher() {
  @ContributesTo(AppScope::class)
  @OptIn(ExperimentalObjCName::class)
  @ObjCName("Component", exact = true)
  interface Component {
    val appNfcApduDispatcher: INfcApduDispatcher
  }
}*/

abstract class AbstractApduDispatcher: NfcApduDispatcher {
  private val _channel = Channel<CommandApdu>(Channel.UNLIMITED)

  /** Expose as a cold flow */
  override val apdus: Flow<CommandApdu> = _channel.receiveAsFlow()

  /** Called by the HostApduService */
  override suspend fun dispatch(apdu: CommandApdu) {
    _channel.send(apdu)
  }

  override fun tryDispatch(apdu: CommandApdu) {
    _channel.trySend(apdu)
  }

  override suspend fun receive(): CommandApdu {
      return _channel.receive()
  }
}
