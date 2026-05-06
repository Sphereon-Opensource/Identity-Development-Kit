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

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.federation.GetAuthenticatedUserCommand
import com.sphereon.oauth2.server.authorization.command.federation.GetUserInfoCommand
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationCallbackCommand
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationCommand
import com.sphereon.oauth2.server.authorization.provider.FederationProviderRegistry
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.storage.FederationSessionStore
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.binding

/**
 * Handler for reconciliation completion, called when the federation callback
 * is for a reconciliation flow. The STS service provides the implementation.
 */
fun interface ReconciliationCallbackHandler {
    /**
     * Complete reconciliation by sending extracted OIDC claims to the auth-bridge.
     *
     * @param claims Raw OIDC claims from the upstream IdP (ID token + userinfo merged)
     * @param providerId The OIDC provider ID that was used
     * @param issuer The IdP issuer URL
     * @param oid4vpSessionId The OID4VP session to complete
     * @return The redirect URL for the browser (typically frontend login page)
     */
    suspend fun onReconciliationComplete(
        claims: Map<String, Any>,
        providerId: String,
        issuer: String,
        oid4vpSessionId: String,
    ): String
}

/**
 * Production wiring of [AbstractFederatedUserAuthenticationProvider]. Every business operation
 * delegates to a [com.sphereon.core.api.service.ServiceCommand] per the IDK command pattern
 * (feedback_command_pattern_mandatory.md). Contributed as a [`UserAuthenticationProvider`][UserAuthenticationProvider]
 * map entry under the `"federated"` key; the active provider is selected by
 * [com.sphereon.oauth2.server.authorization.impl.provider.UserAuthenticationProviderDelegate]
 * (or its EDK tenant-aware counterpart).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<UserAuthenticationProvider>())
@StringKey("federated")
class FederatedUserAuthenticationProvider(
    providerRegistry: FederationProviderRegistry,
    sessionStore: FederationSessionStore,
    initiateProviderAuthenticationCommand: InitiateProviderAuthenticationCommand,
    handleFederationCallbackCommand: HandleFederationCallbackCommand,
    getAuthenticatedUserCommand: GetAuthenticatedUserCommand,
    getUserInfoCommand: GetUserInfoCommand,
) : AbstractFederatedUserAuthenticationProvider(
        providerRegistry = providerRegistry,
        sessionStore = sessionStore,
        initiateProviderAuthenticationCommand = initiateProviderAuthenticationCommand,
        handleFederationCallbackCommand = handleFederationCallbackCommand,
        getAuthenticatedUserCommand = getAuthenticatedUserCommand,
        getUserInfoCommand = getUserInfoCommand,
    )
