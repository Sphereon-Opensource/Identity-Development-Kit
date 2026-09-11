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

package com.sphereon.openid.oid4vci.issuer.impl.config

import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.config.MutableOid4vciIssuerInstanceIdProvider
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerInstanceIdProvider
import com.sphereon.openid.oid4vci.issuer.config.requireCanonicalOid4vciIssuerInstanceId
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Session-scoped holder backing both the read-only [Oid4vciIssuerInstanceIdProvider] and the
 * mutable [MutableOid4vciIssuerInstanceIdProvider]. The same instance answers both bindings so the
 * HTTP adapter writing through the mutable interface is observed by any collaborator reading through
 * the read-only one.
 *
 * Twin of the OAuth2 `DefaultOAuth2ServerInstanceIdProvider`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciIssuerInstanceIdProvider>())
@ContributesBinding(SessionScope::class, binding = binding<MutableOid4vciIssuerInstanceIdProvider>())
class DefaultOid4vciIssuerInstanceIdProvider : MutableOid4vciIssuerInstanceIdProvider {
    private var instanceId: String? = null

    override fun currentInstanceId(): String? = instanceId

    override fun setCurrentInstanceId(instanceId: String) {
        this.instanceId = requireCanonicalOid4vciIssuerInstanceId(instanceId)
    }

    override fun clearCurrentInstanceId() {
        this.instanceId = null
    }
}
