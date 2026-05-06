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

package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.TokenFormat
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.runBlocking

/**
 * Tenant identifier the AS uses when no per-request tenant has been threaded into the session.
 * Single-tenant deployments stay on this default; multi-tenant deployments override the
 * binding with a tenant-aware variant that resolves via the session's tenant context.
 */
private const val DEFAULT_SIGNING_KEY_TENANT = "default"

/**
 * OAuth2 server identifier provider.
 *
 * Resolves the current sign-time identifier by consulting the [SigningKeyStore] for the
 * highest-priority `ACTIVE` key on the default tenant. The returned [ManagedOptsAlias] is a
 * pointer to the KMS-resolved key bytes; consuming commands (CreateAccessTokenCommandImpl,
 * CreateIdTokenCommandImpl, JWS-emitting commands) lazily resolve through the KMS at
 * signing time. Multi-key JWKS publication is handled separately by [GetJwksCommandImpl]
 * which reads the store directly.
 *
 * Bound `@SingleIn(SessionScope::class)`: the lookup happens once per AS session, so a
 * rotation that lands mid-session does not affect tokens minted within that session — new
 * sessions pick up the new active key. This matches mature IdPs' realm-key-cache semantics
 * (Keycloak's `DefaultKeyManager` caches per-realm with explicit eviction on rotation).
 *
 * When JWT tokens and OIDC are both disabled (opaque-token deployments), returns null so
 * the sign paths short-circuit cleanly.
 */
@ContributesTo(SessionScope::class)
interface DefaultOAuth2ConfigModule {
    @Provides
    @SingleIn(SessionScope::class)
    @Named("oauth2.serverIdentifier")
    fun provideDefaultServerIdentifier(
        configProvider: OAuth2ServersConfigProvider,
        signingKeyStore: SigningKeyStore,
    ): ManagedIdentifierOptsOrResult? {
        val config = configProvider.serverConfig

        // Opaque-token / no-OIDC deployments do not need a signing key. Return null so any
        // accidental sign path call surfaces a clear "no signing identifier configured"
        // error rather than wandering into the KMS with a default alias that does not exist.
        if (config.tokenFormat != TokenFormat.JWT && !config.oidc.isEnabled) {
            return null
        }

        // Read the active key for the default tenant. The DI provider runs synchronously, so
        // we bridge to the suspending `getActive` via runBlocking; the store implementation
        // is synchronous in practice (in-memory or quick Postgres lookup) so the bridge does
        // not block any meaningful work.
        val activeResult = runBlocking { signingKeyStore.getActive(DEFAULT_SIGNING_KEY_TENANT) }
        if (!activeResult.isOk) {
            // Surfacing as null lets the AS boot cleanly with no signer; the actual sign path
            // will then return its own typed error when invoked, with diagnostic context.
            return null
        }
        val active = activeResult.value ?: return null

        // The KMS provider resolves the actual private bytes lazily at signing time using the
        // KeyInfo's `alias` (or `kid` as fallback). The wire-visible kid on issued tokens
        // comes from the same KeyInfo, which the JWKS endpoint also publishes — guaranteeing
        // RP-side verification can pick the right entry.
        val identifier = active.keyInfo.alias ?: active.keyInfo.kid ?: active.kid
        return ManagedOptsAlias(identifier = identifier)
    }
}
