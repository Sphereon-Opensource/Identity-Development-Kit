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

package com.sphereon.oauth2.oidf.op

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOpts
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.crypto.resolution.managed.MultiManagedIdentifierService
import com.sphereon.crypto.resolution.tryManagedIdentifierToJwk
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.signing.AsSigningKeyPublicJwkResolver
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** Explicit public-material host capability for this in-process, IDK-only conformance OP. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AsSigningKeyPublicJwkResolver>())
@ContributesBinding(SessionScope::class, binding = binding<AsSigningKeyPublicJwkResolver?>())
class OidfOpLocalSigningKeyPublicJwkResolver(
    private val execution: SessionExecution,
    private val localIdentifiers: MultiManagedIdentifierService,
) : AsSigningKeyPublicJwkResolver {
    override suspend fun resolve(signingKey: OAuth2SigningKey): Jwk? {
        // The registered descriptor owns the key lookup; foreign tenant material is never reused.
        if (signingKey.tenantId != execution.tenantId) return null
        val identifier: ManagedIdentifierOpts = ManagedOptsKeyInfo(identifier = signingKey.keyInfo)
        val resolved = localIdentifiers.resolve(identifier)
        if (!resolved.isOk) return null
        val jwk = tryManagedIdentifierToJwk(resolved.value).getOrNull()?.identifier?.toPublicKey() as? Jwk ?: return null
        return if (jwk.kid == signingKey.kid) jwk else jwk.copy(kid = signingKey.kid)
    }
}
