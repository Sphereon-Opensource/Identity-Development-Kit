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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerInstanceResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Default IDK implementation: a pure-IDK deployment runs a single, config-only issuer under the
 * singular `oid4vci.issuer.*` namespace, so there is no per-request instance to resolve. It always
 * returns `null`, which leaves [com.sphereon.openid.oid4vci.issuer.config.MutableOid4vciIssuerInstanceIdProvider]
 * empty and makes the issuer config provider fall back to that singular namespace.
 *
 * Multi-instance routing (request → persisted issuer party id under `oid4vci.issuers.<id>.*`) is a
 * higher-layer concern: EDK/VDX contribute a resolver that replaces this default binding.
 *
 * Twin of the OAuth2 `DefaultOAuth2ServerInstanceResolver` (whose IDK default likewise short-circuits
 * the single-instance case).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultOid4vciIssuerInstanceResolver : Oid4vciIssuerInstanceResolver {
    override suspend fun resolve(request: GenericHttpRequest): IdkResult<String?, IdkError> = Ok(null)
}
