/*
 * Copyright 2023-2026 Sphereon International B.V.
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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.client.OAuth2Client
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.config.FederationMetadataResolver
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import com.sphereon.oauth2.server.authorization.config.ResolvedFederationProvider
import com.sphereon.oauth2.server.authorization.provider.FederationProviderRuntimeResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default uncached [FederationMetadataResolver] — fetches `.well-known/openid-configuration`
 * on every [resolve] call (or synthesises metadata from `*Override` fields when discovery is
 * disabled). Deployments wanting cached discovery contribute [CachingFederationMetadataResolver]
 * with `replaces = [DirectFederationMetadataResolver::class]`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FederationMetadataResolver>())
class DirectFederationMetadataResolver(
    private val oauth2Client: OAuth2Client,
    private val providerResolver: FederationProviderRuntimeResolver,
) : FederationMetadataResolver {
    override suspend fun resolve(providerConfig: FederationProviderConfig): IdkResult<AuthorizationServerMetadata, IdkError> {
        if (!providerConfig.discoveryEnabled) {
            return Ok(manualMetadata(providerConfig))
        }
        return oauth2Client.fetchAuthorizationServerMetadata(providerConfig.issuerUrl)
    }

    override suspend fun invalidate(providerConfig: FederationProviderConfig) {
        // Uncached resolver: nothing to invalidate.
    }

    override suspend fun findByIssuer(issuer: String): ResolvedFederationProvider? {
        val config = providerResolver.listEnabled().getOrElse { return null }.firstOrNull { it.issuerUrl == issuer } ?: return null
        val metadata = resolve(config).getOrElse { return null }
        return ResolvedFederationProvider(config, metadata)
    }

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
