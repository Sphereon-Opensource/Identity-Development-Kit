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
 */

package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.TokenFormat
import com.sphereon.oauth2.common.config.isEnabled

import kotlinx.coroutines.runBlocking
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn

private const val DEFAULT_SIGNING_KEY_ALIAS = "oauth2-server-signing"

/**
 * JVM/Native OAuth2 server identifier provider.
 *
 * Auto-resolves or creates a signing key via KMS when JWT tokens or OIDC are enabled.
 * Uses runBlocking (not available on JS/wasmJs).
 * The issuer URL provider is in OAuth2IssuerUrlModule (commonMain).
 */

@ContributesTo(SessionScope::class)
interface DefaultOAuth2ConfigModule {

    @Provides
    @SingleIn(SessionScope::class)
    @Named("oauth2.serverIdentifier")
    fun provideDefaultServerIdentifier(
        keyManagerService: KeyManagerService,
        configProvider: OAuth2ServersConfigProvider
    ): ManagedIdentifierOptsOrResult? {
        val config = configProvider.serverConfig

        // Only provide signing key if JWT tokens or OIDC are needed
        if (config.tokenFormat != TokenFormat.JWT && !config.oidc.isEnabled) {
            return null
        }

        return runBlocking {
            resolveOrCreateSigningKey(keyManagerService, config.signingKeyAlias)
        }
    }
}

/**
 * Resolves an existing signing key or creates a new one.
 *
 * @param keyManagerService KMS for key operations
 * @param configuredAlias Explicit alias from config, or null for auto-resolution
 * @return ManagedOptsAlias pointing to the signing key
 */
private suspend fun resolveOrCreateSigningKey(
    keyManagerService: KeyManagerService,
    configuredAlias: String?
): ManagedOptsAlias {
    val keys = keyManagerService.listKeysResult()

    if (keys.isOk) {
        val allKeys = keys.value.keys

        // If a specific alias is configured, check if it exists
        if (configuredAlias != null) {
            val existing = allKeys.firstOrNull { it.alias == configuredAlias }
            if (existing != null) {
                return ManagedOptsAlias(identifier = configuredAlias)
            }
            // Configured alias doesn't exist yet — create it
            keyManagerService.generateKeyResult(
                alias = configuredAlias,
                use = JwkUse.sig,
                alg = SignatureAlgorithm.ECDSA_SHA256
            )
            return ManagedOptsAlias(identifier = configuredAlias)
        }

        // No alias configured — use first available key
        if (allKeys.isNotEmpty()) {
            val first = allKeys.first()
            val alias = first.alias
            if (alias.isNotEmpty()) {
                return ManagedOptsAlias(identifier = alias)
            }
        }
    }

    // No keys exist or listing failed — create a new default key
    val alias = configuredAlias ?: DEFAULT_SIGNING_KEY_ALIAS
    keyManagerService.generateKeyResult(
        alias = alias,
        use = JwkUse.sig,
        alg = SignatureAlgorithm.ECDSA_SHA256
    )
    return ManagedOptsAlias(identifier = alias)
}
