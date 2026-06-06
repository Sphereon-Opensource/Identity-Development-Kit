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

package com.sphereon.di.session

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Deferred lookup of the current [SessionContext].
 *
 * App-scoped collaborators (audit log services, correlation gateways) cannot capture
 * a [SessionContext] at construction time, because the relevant session varies per
 * invocation and a captured instance would leak across sessions. They inject a
 * [SessionContextProvider] instead and call [get] at the moment they need it.
 *
 * Implementations must resolve the *current* session each call and may return `null`
 * when no session can be safely resolved (for example outside a session-bound
 * coroutine context).
 *
 * This is a single-method [fun interface] rather than a bare `() -> SessionContext?`
 * so it is a unique, named binding on the DI graph.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionContextProvider", exact = true)
fun interface SessionContextProvider {
    fun get(): SessionContext?
}
