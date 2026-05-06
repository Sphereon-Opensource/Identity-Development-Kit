/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.config

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata

/**
 * Resolved upstream provider + its discovered (or manually-supplied) metadata.
 *
 * Surfaces the tuple once so consumers that need both (e.g., the inbound back-channel
 * logout receiver) can look up config + metadata in a single call.
 */
data class ResolvedFederationProvider(
    val config: FederationProviderConfig,
    val metadata: AuthorizationServerMetadata,
)

/**
 * Caches upstream OIDC provider metadata across federation authentication requests.
 *
 * Before S2-4 every `FederatedUserAuthenticationProvider.initiateProviderAuthentication`
 * hit the upstream `.well-known/openid-configuration` endpoint fresh. With this resolver,
 * metadata is fetched once per provider and re-used for the TTL window (default 1 hour),
 * which matches typical OIDC IdP key-rotation cadences. An explicit [invalidate] call
 * re-fetches on key rotation, driven by a JWKS lookup miss from the BCL receiver.
 *
 * Implementations are responsible for honouring [FederationProviderConfig.discoveryEnabled]:
 * when false, [resolve] should synthesise metadata from the manual `*Override` fields
 * rather than calling the discovery endpoint.
 *
 * Thread-safe.
 */
interface FederationMetadataResolver {
    /**
     * Fetch metadata for the given provider, using the cache when fresh.
     */
    suspend fun resolve(providerConfig: FederationProviderConfig): IdkResult<AuthorizationServerMetadata, IdkError>

    /**
     * Drop the cached metadata for this provider so the next [resolve] call re-fetches.
     *
     * Used by the back-channel logout receiver after a JWKS signature-verify miss —
     * the upstream may have rotated keys between our last fetch and this logout_token.
     */
    suspend fun invalidate(providerConfig: FederationProviderConfig)

    /**
     * Reverse lookup: given an upstream `iss` claim, resolve the matching provider config
     * + its metadata. Returns null when no enabled provider has this issuer URL.
     *
     * Drives the BCL receiver: the incoming `logout_token.iss` names an upstream IdP;
     * we need its published signing algs + JWKS URI to validate the token.
     */
    suspend fun findByIssuer(issuer: String): ResolvedFederationProvider?
}
