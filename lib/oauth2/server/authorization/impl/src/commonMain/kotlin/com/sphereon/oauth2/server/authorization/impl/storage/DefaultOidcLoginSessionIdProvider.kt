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
 */

package com.sphereon.oauth2.server.authorization.impl.storage

import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.storage.MutableOidcLoginSessionIdProvider
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionIdProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Session-scoped holder backing both the read-only [OidcLoginSessionIdProvider] and the mutable
 * [MutableOidcLoginSessionIdProvider]. The same instance answers both bindings so the HTTP
 * adapter writing through the mutable interface is observed by any collaborator reading through
 * the read-only one.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OidcLoginSessionIdProvider>())
@ContributesBinding(SessionScope::class, binding = binding<MutableOidcLoginSessionIdProvider>())
class DefaultOidcLoginSessionIdProvider : MutableOidcLoginSessionIdProvider {
    private var sessionId: String? = null

    override fun currentLoginSessionId(): String? = sessionId

    override fun setCurrentLoginSessionId(sessionId: String) {
        this.sessionId = sessionId
    }

    override fun clearCurrentLoginSessionId() {
        this.sessionId = null
    }
}
