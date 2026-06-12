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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.server.authorization.command.federation.FederationCallbackOutcome
import com.sphereon.oauth2.server.authorization.command.federation.GetAuthenticatedUserArgs
import com.sphereon.oauth2.server.authorization.command.federation.GetAuthenticatedUserCommand
import com.sphereon.oauth2.server.authorization.command.federation.GetUserInfoArgs
import com.sphereon.oauth2.server.authorization.command.federation.GetUserInfoCommand
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationCallbackArgs
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationCallbackCommand
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationCommand
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.AuthenticationContext
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.FederationProviderRegistry
import com.sphereon.oauth2.server.authorization.provider.FlowContext
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import com.sphereon.oauth2.server.authorization.storage.FederationSessionStore

/**
 * Thin facade over the federation-flow ServiceCommands. Exists to satisfy the single
 * [UserAuthenticationProvider] binding consumed by the OAuth2 AS HTTP layer.
 *
 * Tenant binding comes from [com.sphereon.core.api.context.SessionExecution] at the point of
 * use, never travels as a method argument or a field on a persisted record
 * (feedback_session_scope_tenant.md). Each command's error type is [AuthenticationError]
 * directly, so facade methods are pure delegation: no `mapError`, no per-call error projection.
 * The output wrappers ([com.sphereon.oauth2.server.authorization.command.federation.AuthenticatedUserResult],
 * [com.sphereon.oauth2.server.authorization.command.federation.AuthorizationUrl]) survive because
 * the framework's `TOutput : Any` constraint forbids nullable outputs and binary transport keeps
 * a typed wrapper for `String` payloads.
 */
abstract class AbstractFederatedUserAuthenticationProvider(
    private val providerRegistry: FederationProviderRegistry,
    private val sessionStore: FederationSessionStore,
    private val initiateProviderAuthenticationCommand: InitiateProviderAuthenticationCommand,
    private val handleFederationCallbackCommand: HandleFederationCallbackCommand,
    private val getAuthenticatedUserCommand: GetAuthenticatedUserCommand,
    private val getUserInfoCommand: GetUserInfoCommand,
) : UserAuthenticationProvider {
    fun resolveKnownProvider(providerId: String): FederationProviderConfig? = providerRegistry.findById(providerId)

    fun getEnabledProviders(): List<FederationProviderConfig> = providerRegistry.enabled()

    override suspend fun getAuthenticatedUser(sessionId: String): IdkResult<AuthenticatedUser?, AuthenticationError> =
        getAuthenticatedUserCommand
            .execute(GetAuthenticatedUserArgs(sessionId = sessionId))
            .map { it.user }

    override suspend fun initiateAuthentication(
        sessionId: String,
        returnUrl: String,
        hint: AuthenticationHint?,
        context: AuthenticationContext?,
    ): IdkResult<String, AuthenticationError> {
        val providerId =
            hint?.providerId
                ?: providerRegistry.defaultProviderId()
                ?: return Err(AuthenticationError.Generic(description = "No federation provider specified and no default configured"))
        return initiateProviderAuthentication(
            sessionId = sessionId,
            returnUrl = returnUrl,
            providerId = providerId,
            hint = hint,
            applicationId = context?.applicationId,
        )
    }

    suspend fun initiateProviderAuthentication(
        sessionId: String,
        returnUrl: String,
        providerId: String,
        callbackPath: String? = null,
        flowContext: FlowContext? = null,
        hint: AuthenticationHint? = null,
        applicationId: String? = null,
    ): IdkResult<String, AuthenticationError> =
        initiateProviderAuthenticationCommand
            .execute(
                InitiateProviderAuthenticationArgs(
                    sessionId = sessionId,
                    returnUrl = returnUrl,
                    providerId = providerId,
                    callbackPath = callbackPath,
                    flowContext = flowContext,
                    hint = hint,
                    applicationId = applicationId,
                ),
            ).map { it.value }

    suspend fun handleFederationCallback(
        code: String,
        state: String,
    ): IdkResult<FederationCallbackOutcome, AuthenticationError> = handleFederationCallbackCommand.execute(HandleFederationCallbackArgs(code = code, state = state))

    override suspend fun authenticateWithCredentials(
        credentials: UserCredentials,
        context: AuthenticationContext?,
    ): IdkResult<String?, AuthenticationError> = Err(AuthenticationError.Generic(description = "Federated provider does not support direct credential authentication"))

    override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> {
        sessionStore.removeCachedUserClaims(userId)
        return Ok(Unit)
    }

    override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> = getUserInfoCommand.execute(GetUserInfoArgs(userId = userId))

    override suspend fun isAuthenticationMethodAvailable(method: AuthenticationMethod): IdkResult<Boolean, AuthenticationError> = Ok(method == AuthenticationMethod.OAUTH)
}
