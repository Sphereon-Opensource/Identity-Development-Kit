package com.sphereon.oauth2.server.authorization.provider

import com.sphereon.core.api.IdkResult
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig

/**
 * Exact-binding runtime authority for hosted federation.
 *
 * Implementations resolve a canonical federation binding UUID. They must not interpret slugs,
 * choose a default provider, or return a different binding when the requested one is unavailable.
 * Client credentials are materialized only for the one outbound request and are never included in
 * the provider projection returned to UI or persisted pending transactions.
 */
interface FederationProviderRuntimeResolver {
    suspend fun resolve(bindingId: String): IdkResult<FederationProviderConfig, AuthenticationError>

    suspend fun listEnabled(): IdkResult<List<FederationProviderConfig>, AuthenticationError>

    suspend fun clientAuthentication(
        bindingId: String,
        audience: String,
    ): IdkResult<ClientAuthenticationConfig, AuthenticationError>
}
