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
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.AuthenticationContext
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Tests for [CompositeUserAuthenticationProvider].
 *
 * Verifies routing based on login_hint and proper delegation.
 */
class CompositeUserAuthenticationProviderTest {
    private val now = Clock.System.now()

    private val federationUser =
        AuthenticatedUser(
            userId = "fed-user-123",
            authenticatedAt = now,
            authenticationMethod = AuthenticationMethod.OAUTH,
        )

    private val walletUser =
        AuthenticatedUser(
            userId = "wallet-user-456",
            authenticatedAt = now,
            authenticationMethod = AuthenticationMethod.CUSTOM,
        )

    private val federationProvider =
        StubAuthProvider(
            authenticatedUser = federationUser,
            userInfo = UserInfo(userId = "fed-user-123", displayName = "Federation User"),
        )

    private val walletProvider =
        StubAuthProvider(
            authenticatedUser = walletUser,
            userInfo = UserInfo(userId = "wallet-user-456", displayName = "Wallet User"),
        )

    private val composite =
        CompositeUserAuthenticationProvider(
            federationProvider = federationProvider,
            walletProvider = walletProvider,
        )

    // --- initiateAuthentication routing ---

    @Test
    fun initiateAuthWithOid4vpLoginHintRoutesToWallet() =
        runTest {
            val hint = AuthenticationHint(loginHint = "oid4vp:session-abc123")
            val result = composite.initiateAuthentication("session-1", "https://return.url", hint)
            assertTrue(result.isOk)
            assertEquals("redirect:session-1", result.value)
            assertTrue(walletProvider.initiateAuthCalled)
        }

    @Test
    fun initiateAuthWithoutLoginHintRoutesToFederation() =
        runTest {
            val result = composite.initiateAuthentication("session-2", "https://return.url", null)
            assertTrue(result.isOk)
            assertEquals("redirect:session-2", result.value)
            assertTrue(federationProvider.initiateAuthCalled)
        }

    @Test
    fun initiateAuthWithNonOid4vpLoginHintRoutesToFederation() =
        runTest {
            val hint = AuthenticationHint(loginHint = "user@example.com")
            val result = composite.initiateAuthentication("session-3", "https://return.url", hint)
            assertTrue(result.isOk)
            assertTrue(federationProvider.initiateAuthCalled)
        }

    // --- getAuthenticatedUser routing ---

    @Test
    fun getAuthenticatedUserTriesWalletFirst() =
        runTest {
            val result = composite.getAuthenticatedUser("some-session")
            assertTrue(result.isOk)
            assertNotNull(result.value)
            assertEquals("wallet-user-456", result.value!!.userId)
        }

    @Test
    fun getAuthenticatedUserFallsToFederationWhenWalletReturnsNull() =
        runTest {
            val walletNoUser = StubAuthProvider(authenticatedUser = null)
            val comp =
                CompositeUserAuthenticationProvider(
                    federationProvider = federationProvider,
                    walletProvider = walletNoUser,
                )
            val result = comp.getAuthenticatedUser("some-session")
            assertTrue(result.isOk)
            assertNotNull(result.value)
            assertEquals("fed-user-123", result.value!!.userId)
        }

    @Test
    fun getAuthenticatedUserWithNoWalletProviderUsesFederation() =
        runTest {
            val comp =
                CompositeUserAuthenticationProvider(
                    federationProvider = federationProvider,
                    walletProvider = null,
                )
            val result = comp.getAuthenticatedUser("some-session")
            assertTrue(result.isOk)
            assertNotNull(result.value)
            assertEquals("fed-user-123", result.value!!.userId)
        }

    @Test
    fun getAuthenticatedUserUsesKnownProviderAfterInitiate() =
        runTest {
            // First, initiate with oid4vp hint → routes to wallet
            val hint = AuthenticationHint(loginHint = "oid4vp:session-xyz")
            composite.initiateAuthentication("session-xyz", "https://return.url", hint)

            // Now getAuthenticatedUser should use the known (wallet) provider
            val result = composite.getAuthenticatedUser("session-xyz")
            assertTrue(result.isOk)
            assertEquals("wallet-user-456", result.value?.userId)
        }

    // --- getUserInfo ---

    @Test
    fun getUserInfoTriesWalletFirstThenFederation() =
        runTest {
            val result = composite.getUserInfo("wallet-user-456")
            assertTrue(result.isOk)
            assertEquals("Wallet User", result.value.displayName)
        }

    @Test
    fun getUserInfoFallsToFederationWhenWalletErrors() =
        runTest {
            val walletErr = StubAuthProvider(userInfoError = true)
            val comp =
                CompositeUserAuthenticationProvider(
                    federationProvider = federationProvider,
                    walletProvider = walletErr,
                )
            val result = comp.getUserInfo("fed-user-123")
            assertTrue(result.isOk)
            assertEquals("Federation User", result.value.displayName)
        }

    // --- isAuthenticationMethodAvailable ---

    @Test
    fun customMethodDelegatesToWallet() =
        runTest {
            val result = composite.isAuthenticationMethodAvailable(AuthenticationMethod.CUSTOM)
            assertTrue(result.isOk)
            assertTrue(result.value)
        }

    @Test
    fun oauthMethodDelegatesToFederation() =
        runTest {
            val result = composite.isAuthenticationMethodAvailable(AuthenticationMethod.OAUTH)
            assertTrue(result.isOk)
            assertTrue(result.value)
        }

    @Test
    fun customMethodReturnsFalseWithNoWalletProvider() =
        runTest {
            val comp =
                CompositeUserAuthenticationProvider(
                    federationProvider = federationProvider,
                    walletProvider = null,
                )
            val result = comp.isAuthenticationMethodAvailable(AuthenticationMethod.CUSTOM)
            assertTrue(result.isOk)
            assertTrue(!result.value)
        }

    // --- logout ---

    @Test
    fun logoutDelegatesToBothProviders() =
        runTest {
            val result = composite.logout("user-id")
            assertTrue(result.isOk)
            assertTrue(federationProvider.logoutCalled)
            assertTrue(walletProvider.logoutCalled)
        }
}

/**
 * Stub [UserAuthenticationProvider] for testing.
 */
private class StubAuthProvider(
    private val authenticatedUser: AuthenticatedUser? = null,
    private val userInfo: UserInfo? = null,
    private val userInfoError: Boolean = false,
) : UserAuthenticationProvider {
    var initiateAuthCalled = false
    var logoutCalled = false

    override suspend fun getAuthenticatedUser(sessionId: String): IdkResult<AuthenticatedUser?, AuthenticationError> = Ok(authenticatedUser)

    override suspend fun initiateAuthentication(
        sessionId: String,
        returnUrl: String,
        hint: AuthenticationHint?,
        context: AuthenticationContext?,
    ): IdkResult<String, AuthenticationError> {
        initiateAuthCalled = true
        return Ok("redirect:$sessionId")
    }

    override suspend fun authenticateWithCredentials(
        credentials: UserCredentials,
        context: AuthenticationContext?,
    ): IdkResult<String?, AuthenticationError> = Ok(authenticatedUser?.userId)

    override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> {
        logoutCalled = true
        return Ok(Unit)
    }

    override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> {
        if (userInfoError || userInfo == null) {
            return Err(AuthenticationError.UserNotFound("User not found: $userId"))
        }
        return Ok(userInfo)
    }

    override suspend fun isAuthenticationMethodAvailable(method: AuthenticationMethod): IdkResult<Boolean, AuthenticationError> = Ok(true)
}
