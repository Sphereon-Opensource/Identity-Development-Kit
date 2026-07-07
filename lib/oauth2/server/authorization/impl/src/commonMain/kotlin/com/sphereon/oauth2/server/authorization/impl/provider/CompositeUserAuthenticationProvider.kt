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

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.AuthenticationContext
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Composite authentication provider that routes to the appropriate sub-provider
 * based on the login_hint parameter or authentication method.
 *
 * Route selection:
 * - `login_hint=oid4vp:{sessionId}` → wallet (OID4VP) provider
 * - No `oid4vp:` prefix or no login_hint → federation provider (default)
 *
 * This allows a single STS to support both federated (OIDC) and wallet (OID4VP) authentication.
 *
 * ## Scoping requirement
 *
 * This class MUST be constructed at [AppScope]: the `sessionProviderMap` is cross-session
 * routing state that tracks which sub-provider owns which OIDC session id, and a SessionScope
 * instance would lose that routing the moment the HTTP session ends (the callback arrives in
 * a different HTTP session than the initiation). The `@SingleIn(AppScope::class)` annotation
 * makes the intent explicit for any assembly that wires this via Metro.
 *
 * ## Multi-replica caveat (Sprint 2)
 *
 * `sessionProviderMap` is in-process. A load-balanced deployment where node A takes the
 * initiate call and node B handles the callback will fail to resolve the provider on node B.
 * Sprint 2 replaces this with a persistent `FederationSessionStore` (same pattern as
 * `AuthenticationSessionStore` in EDK auth).
 */
@SingleIn(AppScope::class)
class CompositeUserAuthenticationProvider(
    val federationProvider: UserAuthenticationProvider,
    private val walletProvider: UserAuthenticationProvider?,
) : SynchronizedObject(),
    UserAuthenticationProvider {
    companion object {
        const val OID4VP_PREFIX = "oid4vp:"
    }

    // Track which provider was used per session for proper delegation. Reads and writes are
    // guarded via `synchronized(this)` (atomicfu SynchronizedObject) so concurrent initiate /
    // callback traffic cannot corrupt the routing table.
    private val sessionProviderMap = mutableMapOf<String, UserAuthenticationProvider>()

    override suspend fun getAuthenticatedUser(sessionId: String): IdkResult<AuthenticatedUser?, AuthenticationError> {
        // Check if we know which provider this session belongs to
        val knownProvider = synchronized(this) { sessionProviderMap[sessionId] }
        if (knownProvider != null) {
            return knownProvider.getAuthenticatedUser(sessionId)
        }

        // If sessionId looks like an OID4VP session, try wallet provider first
        if (walletProvider != null) {
            val walletResult = walletProvider.getAuthenticatedUser(sessionId)
            if (walletResult.isOk && walletResult.value != null) {
                return walletResult
            }
        }

        // Try federation provider
        return federationProvider.getAuthenticatedUser(sessionId)
    }

    override suspend fun initiateAuthentication(
        sessionId: String,
        returnUrl: String,
        hint: AuthenticationHint?,
        context: AuthenticationContext?,
    ): IdkResult<String, AuthenticationError> {
        val provider = selectProvider(hint)
        synchronized(this) { sessionProviderMap[sessionId] = provider }
        return provider.initiateAuthentication(sessionId, returnUrl, hint, context)
    }

    override suspend fun authenticateWithCredentials(
        credentials: UserCredentials,
        context: AuthenticationContext?,
    ): IdkResult<String?, AuthenticationError> {
        // Try wallet provider first for custom credentials
        if (walletProvider != null && credentials is UserCredentials.Custom) {
            val result = walletProvider.authenticateWithCredentials(credentials, context)
            if (result.isOk && result.value != null) {
                return result
            }
        }
        return federationProvider.authenticateWithCredentials(credentials, context)
    }

    override suspend fun authenticateUserWithCredentials(
        credentials: UserCredentials,
        context: AuthenticationContext?,
    ): IdkResult<AuthenticatedUser?, AuthenticationError> {
        // Try wallet provider first for custom credentials
        if (walletProvider != null && credentials is UserCredentials.Custom) {
            val result = walletProvider.authenticateUserWithCredentials(credentials, context)
            if (result.isOk && result.value != null) {
                return result
            }
        }
        return federationProvider.authenticateUserWithCredentials(credentials, context)
    }

    override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> {
        // Logout from all providers
        walletProvider?.logout(userId)
        return federationProvider.logout(userId)
    }

    override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> {
        // Try wallet provider first
        if (walletProvider != null) {
            val result = walletProvider.getUserInfo(userId)
            if (result.isOk) {
                return result
            }
        }
        return federationProvider.getUserInfo(userId)
    }

    override suspend fun isAuthenticationMethodAvailable(method: AuthenticationMethod): IdkResult<Boolean, AuthenticationError> {
        return when (method) {
            AuthenticationMethod.CUSTOM -> {
                walletProvider?.isAuthenticationMethodAvailable(method) ?: Ok(false)
            }

            AuthenticationMethod.OAUTH -> {
                federationProvider.isAuthenticationMethodAvailable(method)
            }

            else -> {
                // Try both
                val fedResult = federationProvider.isAuthenticationMethodAvailable(method)
                if (fedResult.isOk && fedResult.value) {
                    return fedResult
                }
                walletProvider?.isAuthenticationMethodAvailable(method) ?: Ok(false)
            }
        }
    }

    private fun selectProvider(hint: AuthenticationHint?): UserAuthenticationProvider {
        val loginHint = hint?.loginHint
        if (loginHint != null && loginHint.startsWith(OID4VP_PREFIX) && walletProvider != null) {
            return walletProvider
        }
        return federationProvider
    }
}
