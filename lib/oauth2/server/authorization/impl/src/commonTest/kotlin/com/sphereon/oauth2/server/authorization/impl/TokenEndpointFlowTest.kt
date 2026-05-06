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

package com.sphereon.oauth2.server.authorization.impl

import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenArgs
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenArgs
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantArgs
import com.sphereon.oauth2.server.authorization.impl.command.introspection.AuthServerIntrospectTokenCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.CreateRefreshTokenCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyAuthorizationCodeGrantCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyClientCredentialsGrantCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyRefreshTokenGrantCommandImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryAuthorizationCodeStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryClientRegistryImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryTokenStorageImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.model.AuthorizationCodeData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Integration tests for token endpoint operations
 *
 * Tests various grant types and token operations:
 * - Authorization code grant with PKCE
 * - Refresh token grant
 * - Client credentials grant
 * - Token introspection
 * - Token revocation
 */
class TokenEndpointFlowTest {
    private val ctx = OAuth2ServerTestContext("token-endpoint-test", this)
    private val execution = ctx.execution
    private val configProvider = TestOAuth2ServersConfigProvider()

    @Test
    fun `test authorization code grant verification with S256 PKCE`() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val codeStorage = InMemoryAuthorizationCodeStorageImpl(storage)
            val clientRegistry = InMemoryClientRegistryImpl(storage)

            // Register client
            val registerResult = clientRegistry.registerClient(TestFixtures.publicClient)
            assertTrue(registerResult.isOk)

            // Create authorization code with PKCE
            val now = Clock.System.now()
            val codeData =
                AuthorizationCodeData(
                    code = "test-auth-code-123",
                    clientId = TestFixtures.publicClient.clientId,
                    subject = "user123",
                    redirectUri = TestFixtures.publicClient.redirectUris.first(),
                    scope = "read write",
                    codeChallenge = TestFixtures.Pkce.CODE_CHALLENGE_S256,
                    codeChallengeMethod = PkceMethod.S256,
                    dpopJkt = null,
                    issuedAt = now,
                    expiresAt = now + 10.minutes,
                    used = false,
                    additionalData = emptyMap(),
                )

            val storeResult = codeStorage.storeAuthorizationCode(codeData.code, codeData)
            assertTrue(storeResult.isOk)

            // Verify grant with correct code verifier
            val verifyCommand =
                VerifyAuthorizationCodeGrantCommandImpl(
                    execution = execution,
                    authorizationCodeStorage = codeStorage,
                    tokenStorage = InMemoryTokenStorageImpl(storage),
                    clientRegistry = clientRegistry,
                    configProvider = configProvider,
                )

            val result =
                verifyCommand.execute(
                    VerifyAuthorizationCodeGrantArgs(
                        code = "test-auth-code-123",
                        redirectUri = TestFixtures.publicClient.redirectUris.first(),
                        clientId = TestFixtures.publicClient.clientId,
                        codeVerifier = TestFixtures.Pkce.CODE_VERIFIER,
                    ),
                )
            assertTrue(result.isOk)

            assertEquals("user123", result.value.subject)
            assertEquals(TestFixtures.publicClient.clientId, result.value.clientId)
            assertEquals("read write", result.value.scope)

            // Verify code is marked as used (single-use)
            val secondAttempt =
                verifyCommand.execute(
                    VerifyAuthorizationCodeGrantArgs(
                        code = "test-auth-code-123",
                        redirectUri = TestFixtures.publicClient.redirectUris.first(),
                        clientId = TestFixtures.publicClient.clientId,
                        codeVerifier = TestFixtures.Pkce.CODE_VERIFIER,
                    ),
                )

            assertTrue(secondAttempt.isErr)
        }

    @Test
    fun `test authorization code grant with wrong PKCE verifier fails`() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val codeStorage = InMemoryAuthorizationCodeStorageImpl(storage)

            // Create authorization code with PKCE
            val now = Clock.System.now()
            val codeData =
                AuthorizationCodeData(
                    code = "test-code-456",
                    clientId = TestFixtures.publicClient.clientId,
                    subject = "user456",
                    redirectUri = TestFixtures.publicClient.redirectUris.first(),
                    scope = "read",
                    codeChallenge = TestFixtures.Pkce.CODE_CHALLENGE_S256,
                    codeChallengeMethod = PkceMethod.S256,
                    dpopJkt = null,
                    issuedAt = now,
                    expiresAt = now + 10.minutes,
                    used = false,
                    additionalData = emptyMap(),
                )

            val storeResult = codeStorage.storeAuthorizationCode(codeData.code, codeData)
            assertTrue(storeResult.isOk)

            val verifyCommand =
                VerifyAuthorizationCodeGrantCommandImpl(
                    execution = execution,
                    authorizationCodeStorage = codeStorage,
                    tokenStorage = InMemoryTokenStorageImpl(storage),
                    clientRegistry = InMemoryClientRegistryImpl(storage),
                    configProvider = configProvider,
                )

            // Try with wrong verifier
            val result =
                verifyCommand.execute(
                    VerifyAuthorizationCodeGrantArgs(
                        code = "test-code-456",
                        redirectUri = TestFixtures.publicClient.redirectUris.first(),
                        clientId = TestFixtures.publicClient.clientId,
                        codeVerifier = "wrong-verifier",
                    ),
                )

            assertTrue(result.isErr)
        }

    @Test
    fun `test refresh token grant verification`() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val tokenStorage = InMemoryTokenStorageImpl(storage)
            val clientRegistry = InMemoryClientRegistryImpl(storage)

            // Register client
            val registerResult = clientRegistry.registerClient(TestFixtures.confidentialClient)
            assertTrue(registerResult.isOk)

            // Create refresh token
            val createCommand =
                CreateRefreshTokenCommandImpl(
                    execution = execution,
                    tokenStorage = tokenStorage,
                    configProvider = configProvider,
                    secureRandom = defaultSecureRandom(),
                )

            val createResult =
                createCommand.execute(
                    CreateRefreshTokenArgs(
                        subject = "user789",
                        clientId = TestFixtures.confidentialClient.clientId,
                        scope = "read write",
                        expiresInSeconds = 86400,
                    ),
                )
            assertTrue(createResult.isOk)
            val refreshToken = createResult.value.value

            assertNotNull(refreshToken)

            // Verify refresh token grant
            val verifyCommand =
                VerifyRefreshTokenGrantCommandImpl(
                    execution = execution,
                    tokenStorage = tokenStorage,
                )

            val result =
                verifyCommand.execute(
                    VerifyRefreshTokenGrantArgs(
                        refreshToken = refreshToken,
                        clientId = TestFixtures.confidentialClient.clientId,
                        requestedScope = "read", // Subset of original scope
                    ),
                )
            assertTrue(result.isOk)

            assertEquals("user789", result.value.subject)
            assertEquals(TestFixtures.confidentialClient.clientId, result.value.clientId)
            assertEquals("read", result.value.scope) // Should be the requested subset
        }

    @Test
    fun `test refresh token with broader scope than original fails`() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val tokenStorage = InMemoryTokenStorageImpl(storage)

            // Create refresh token with limited scope
            val createCommand =
                CreateRefreshTokenCommandImpl(
                    execution = execution,
                    tokenStorage = tokenStorage,
                    configProvider = configProvider,
                    secureRandom = defaultSecureRandom(),
                )

            val createResult =
                createCommand.execute(
                    CreateRefreshTokenArgs(
                        subject = "user-limited",
                        clientId = TestFixtures.confidentialClient.clientId,
                        scope = "read", // Only read scope
                        expiresInSeconds = 86400,
                    ),
                )
            assertTrue(createResult.isOk)
            val refreshToken = createResult.value.value

            // Try to get broader scope
            val verifyCommand =
                VerifyRefreshTokenGrantCommandImpl(
                    execution = execution,
                    tokenStorage = tokenStorage,
                )

            val result =
                verifyCommand.execute(
                    VerifyRefreshTokenGrantArgs(
                        refreshToken = refreshToken,
                        clientId = TestFixtures.confidentialClient.clientId,
                        requestedScope = "read write admin", // Broader than original!
                    ),
                )

            assertTrue(result.isErr)
        }

    @Test
    fun `test client credentials grant verification`() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val clientRegistry = InMemoryClientRegistryImpl(storage)

            // Register client with client_credentials grant
            val registerResult = clientRegistry.registerClient(TestFixtures.confidentialClient)
            assertTrue(registerResult.isOk)

            val verifyCommand =
                VerifyClientCredentialsGrantCommandImpl(
                    execution = execution,
                    clientRegistry = clientRegistry,
                )

            val result =
                verifyCommand.execute(
                    VerifyClientCredentialsGrantArgs(
                        clientId = TestFixtures.confidentialClient.clientId,
                        requestedScope = "read",
                    ),
                )
            assertTrue(result.isOk)

            assertEquals(TestFixtures.confidentialClient.clientId, result.value.clientId)
            assertEquals("read", result.value.scope)
        }

    @Test
    fun `test client credentials grant with unauthorized grant type fails`() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val clientRegistry = InMemoryClientRegistryImpl(storage)

            // Register client without client_credentials grant
            val client =
                TestFixtures.publicClient.copy(
                    grantTypes = listOf(GrantType.AUTHORIZATION_CODE), // Only auth code
                )
            val registerResult = clientRegistry.registerClient(client)
            assertTrue(registerResult.isOk)

            val verifyCommand =
                VerifyClientCredentialsGrantCommandImpl(
                    execution = execution,
                    clientRegistry = clientRegistry,
                )

            val result =
                verifyCommand.execute(
                    VerifyClientCredentialsGrantArgs(
                        clientId = client.clientId,
                        requestedScope = "read",
                    ),
                )

            assertTrue(result.isErr)
        }

    @Test
    fun `test token introspection for active token`() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val tokenStorage = InMemoryTokenStorageImpl(storage)

            // Create access token
            val accessToken =
                TestFixtures.createAccessToken(
                    accessToken = "active-token-123",
                    clientId = TestFixtures.confidentialClient.clientId,
                    subject = "user-active",
                    scope = "read write",
                )

            val storeResult = tokenStorage.storeAccessToken(accessToken.accessToken, accessToken)
            assertTrue(storeResult.isOk)

            // Introspect token
            val introspectCommand =
                AuthServerIntrospectTokenCommandImpl(
                    execution = execution,
                    tokenStorage = tokenStorage,
                    configProvider = configProvider,
                )

            val result =
                introspectCommand.execute(
                    IntrospectTokenArgs(
                        token = "active-token-123",
                        tokenTypeHint = "access_token",
                        clientId = TestFixtures.confidentialClient.clientId,
                    ),
                )
            assertTrue(result.isOk)

            assertTrue(result.value.active)
            assertEquals("user-active", result.value.username)
            assertEquals(TestFixtures.confidentialClient.clientId, result.value.clientId)
            assertEquals("read write", result.value.scope)
            assertNotNull(result.value.exp)
        }

    @Test
    fun `test token introspection for revoked token`() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val tokenStorage = InMemoryTokenStorageImpl(storage)

            // Create revoked access token
            val accessToken =
                TestFixtures
                    .createAccessToken(
                        accessToken = "revoked-token-456",
                        clientId = TestFixtures.confidentialClient.clientId,
                        subject = "user-revoked",
                    ).copy(revoked = true)

            val storeResult = tokenStorage.storeAccessToken(accessToken.accessToken, accessToken)
            assertTrue(storeResult.isOk)

            // Introspect token
            val introspectCommand =
                AuthServerIntrospectTokenCommandImpl(
                    execution = execution,
                    tokenStorage = tokenStorage,
                    configProvider = configProvider,
                )

            val result =
                introspectCommand.execute(
                    IntrospectTokenArgs(
                        token = "revoked-token-456",
                        tokenTypeHint = "access_token",
                        clientId = TestFixtures.confidentialClient.clientId,
                    ),
                )
            assertTrue(result.isOk)

            assertFalse(result.value.active) // Should be inactive
        }

    @Test
    fun `test token introspection for non-existent token`() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val tokenStorage = InMemoryTokenStorageImpl(storage)

            val introspectCommand =
                AuthServerIntrospectTokenCommandImpl(
                    execution = execution,
                    tokenStorage = tokenStorage,
                    configProvider = configProvider,
                )

            val result =
                introspectCommand.execute(
                    IntrospectTokenArgs(
                        token = "non-existent-token",
                        tokenTypeHint = "access_token",
                        clientId = TestFixtures.confidentialClient.clientId,
                    ),
                )
            assertTrue(result.isOk)

            assertFalse(result.value.active) // Should be inactive
        }
}
