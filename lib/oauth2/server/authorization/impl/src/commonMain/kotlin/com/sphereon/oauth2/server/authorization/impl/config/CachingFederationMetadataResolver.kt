/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.client.client.OAuth2Client
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.config.FederationMetadataResolver
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import com.sphereon.oauth2.server.authorization.config.ResolvedFederationProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * In-memory caching resolver. One entry per issuer URL. Default TTL 1h.
 *
 * @param oauth2Client performs the `.well-known/openid-configuration` fetch.
 * @param providersProvider returns the currently enabled provider configs (called every
 *   `findByIssuer` for freshness — configs are read from PropertyResolver which may reload).
 * @param ttl how long a successful discovery response is considered fresh.
 * @param clock injected for deterministic testing.
 */
class CachingFederationMetadataResolver(
    private val oauth2Client: OAuth2Client,
    private val providersProvider: () -> List<FederationProviderConfig>,
    private val ttl: Duration = 1.hours,
    private val clock: Clock = Clock.System,
) : FederationMetadataResolver {
    private data class CacheEntry(
        val metadata: AuthorizationServerMetadata,
        val expiresAt: Instant,
    )

    private val cache = mutableMapOf<String, CacheEntry>()
    private val mutex = Mutex()

    override suspend fun resolve(providerConfig: FederationProviderConfig): IdkResult<AuthorizationServerMetadata, IdkError> {
        val key = providerConfig.issuerUrl

        mutex.withLock {
            cache[key]?.let { entry ->
                if (clock.now() < entry.expiresAt) {
                    return Ok(entry.metadata)
                }
                cache.remove(key)
            }
        }

        if (!providerConfig.discoveryEnabled) {
            return Ok(manualMetadata(providerConfig))
        }

        val fetched =
            oauth2Client
                .fetchAuthorizationServerMetadata(providerConfig.issuerUrl)
                .getOrElse { return Err(it) }

        mutex.withLock {
            cache[key] = CacheEntry(fetched, clock.now() + ttl)
        }
        return Ok(fetched)
    }

    override suspend fun invalidate(providerConfig: FederationProviderConfig) {
        mutex.withLock { cache.remove(providerConfig.issuerUrl) }
    }

    override suspend fun findByIssuer(issuer: String): ResolvedFederationProvider? {
        val config = providersProvider().firstOrNull { it.enabled && it.issuerUrl == issuer } ?: return null
        val metadata = resolve(config).getOrElse { return null }
        return ResolvedFederationProvider(config, metadata)
    }

    /**
     * Build a minimal metadata object from the config's manual overrides when discovery is disabled.
     * The resulting metadata has only what the operator supplied — any consumer that needs an
     * endpoint not in `*Override` will see null and must surface a clear configuration error.
     */
    private fun manualMetadata(providerConfig: FederationProviderConfig): AuthorizationServerMetadata =
        AuthorizationServerMetadata(
            issuer = providerConfig.issuerUrl,
            authorizationEndpoint = providerConfig.authorizationEndpointOverride,
            tokenEndpoint =
                providerConfig.tokenEndpointOverride
                    ?: error("tokenEndpointOverride must be set when discoveryEnabled=false for '${providerConfig.id}'"),
            userinfoEndpoint = providerConfig.userinfoEndpointOverride,
        )
}
