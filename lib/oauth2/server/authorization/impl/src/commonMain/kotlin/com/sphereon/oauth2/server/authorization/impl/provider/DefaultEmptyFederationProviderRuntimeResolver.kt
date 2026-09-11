package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.FederationProviderRuntimeResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** Fail-closed binding for assemblies that do not mount the VDX authorization-server repository. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FederationProviderRuntimeResolver>())
class DefaultEmptyFederationProviderRuntimeResolver : FederationProviderRuntimeResolver {
    override suspend fun resolve(bindingId: String): IdkResult<FederationProviderConfig, AuthenticationError> =
        Err(AuthenticationError.Generic(description = "Federation binding '$bindingId' is unavailable"))

    override suspend fun listEnabled(): IdkResult<List<FederationProviderConfig>, AuthenticationError> = Ok(emptyList())

    override suspend fun clientAuthentication(bindingId: String, audience: String): IdkResult<ClientAuthenticationConfig, AuthenticationError> =
        Err(AuthenticationError.Generic(description = "Federation binding '$bindingId' client authentication is unavailable"))
}
