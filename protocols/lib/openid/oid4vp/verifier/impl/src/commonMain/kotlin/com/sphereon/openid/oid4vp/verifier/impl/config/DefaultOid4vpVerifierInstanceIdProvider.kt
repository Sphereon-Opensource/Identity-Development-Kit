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

package com.sphereon.openid.oid4vp.verifier.impl.config

import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.verifier.config.MutableOid4vpVerifierInstanceIdProvider
import com.sphereon.openid.oid4vp.verifier.config.Oid4vpVerifierInstanceIdProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Session-scoped holder backing both the read-only [Oid4vpVerifierInstanceIdProvider] and the
 * mutable [MutableOid4vpVerifierInstanceIdProvider]. The same instance answers both bindings so the
 * HTTP adapter writing through the mutable interface is observed by any collaborator reading through
 * the read-only one.
 *
 * Twin of the OID4VCI `DefaultOid4vciIssuerInstanceIdProvider` and the OAuth2
 * `DefaultOAuth2ServerInstanceIdProvider`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vpVerifierInstanceIdProvider>())
@ContributesBinding(SessionScope::class, binding = binding<MutableOid4vpVerifierInstanceIdProvider>())
class DefaultOid4vpVerifierInstanceIdProvider : MutableOid4vpVerifierInstanceIdProvider {
    private var instanceId: String? = null

    override fun currentInstanceId(): String? = instanceId

    override fun setCurrentInstanceId(instanceId: String) {
        this.instanceId = instanceId
    }

    override fun clearCurrentInstanceId() {
        this.instanceId = null
    }
}
