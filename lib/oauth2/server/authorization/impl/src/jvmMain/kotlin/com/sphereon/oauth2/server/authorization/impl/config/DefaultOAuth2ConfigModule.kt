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
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

private const val DEFAULT_SIGNING_KEY_ALIAS = "oauth2-server-signing"

/**
 * OAuth2 server identifier provider.
 *
 * Provides a [ManagedOptsAlias] pointing to the configured signing key alias.
 * The key is resolved lazily by the consuming commands (CreateAccessTokenCommandImpl,
 * CreateIdTokenCommandImpl, GetJwksCommandImpl) when they actually need to sign —
 * not eagerly at DI construction time.
 *
 * When JWT tokens and OIDC are both disabled, returns null (opaque tokens).
 */
@ContributesTo(SessionScope::class)
interface DefaultOAuth2ConfigModule {
    @Provides
    @SingleIn(SessionScope::class)
    @Named("oauth2.serverIdentifier")
    fun provideDefaultServerIdentifier(configProvider: OAuth2ServersConfigProvider): ManagedIdentifierOptsOrResult? {
        val config = configProvider.serverConfig

        // Only provide signing key reference if JWT tokens or OIDC are needed
        if (config.tokenFormat != TokenFormat.JWT && !config.oidc.isEnabled) {
            return null
        }

        // Return alias reference — the KMS resolves the actual key lazily at signing time.
        // If the key doesn't exist yet, the KMS command will fail with a clear error
        // at token creation time, not silently at DI construction time.
        val alias = config.signingKeyAlias ?: DEFAULT_SIGNING_KEY_ALIAS
        return ManagedOptsAlias(identifier = alias)
    }
}
