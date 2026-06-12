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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.verifier.config.Oid4vpVerifierInstanceResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Default IDK implementation: a pure-IDK deployment runs a single, config-only verifier under the
 * singular `oid4vp.verifier.*` namespace, so there is no per-request instance to resolve. It always
 * returns `null`, which leaves
 * [com.sphereon.openid.oid4vp.verifier.config.MutableOid4vpVerifierInstanceIdProvider] empty and
 * makes the verifier config provider fall back to that singular namespace.
 *
 * Multi-instance routing (request → persisted verifier party id under `oid4vp.verifiers.<id>.*`) is a
 * higher-layer concern: EDK/VDX contribute a resolver that replaces this default binding.
 *
 * Twin of the OID4VCI `DefaultOid4vciIssuerInstanceResolver` and the OAuth2
 * `DefaultOAuth2ServerInstanceResolver`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultOid4vpVerifierInstanceResolver : Oid4vpVerifierInstanceResolver {
    override suspend fun resolve(request: GenericHttpRequest): IdkResult<String?, IdkError> = Ok(null)
}
