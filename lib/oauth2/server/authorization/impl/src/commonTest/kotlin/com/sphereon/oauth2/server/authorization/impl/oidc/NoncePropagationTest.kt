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

package com.sphereon.oauth2.server.authorization.impl.oidc

import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeArgs
import com.sphereon.oauth2.server.authorization.impl.command.authorization.CreateAuthorizationCodeCommandImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryAuthorizationCodeStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryPushedAuthorizationRequestStorageImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.ConsentDecision
import com.sphereon.oauth2.server.authorization.model.SessionStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Tests nonce propagation: session → authorization code.
 * Verifies that the OIDC nonce survives the authorization code creation flow.
 */
class NoncePropagationTest {
    private val ctx = OAuth2ServerTestContext("nonce-propagation-test", this)

    @Test
    fun nonceIsPreservedInCodeData() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val codeStorage = InMemoryAuthorizationCodeStorageImpl(storage)

            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(
                        servers =
                            mapOf(
                                "default" to
                                    OAuth2ServerInstanceConfig(
                                        issuer = "https://auth.example.com",
                                        oidc = FeaturePolicy.SUPPORTED,
                                    ),
                            ),
                    ),
                )

            val now = Clock.System.now()
            val consent =
                ConsentDecision(
                    userId = "user123",
                    clientId = "test-client",
                    granted = true,
                    grantedScopes = listOf("openid", "profile", "email"),
                    grantedAt = now,
                    rememberConsent = false,
                )

            val session =
                AuthorizationSession(
                    sessionId = "session-with-nonce",
                    clientId = "test-client",
                    redirectUri = "https://client.example.com/callback",
                    scope = "openid profile email",
                    state = "state-123",
                    responseType = "code",
                    codeChallenge = null,
                    codeChallengeMethod = null,
                    status = SessionStatus.AUTHORIZED,
                    authenticatedUserId = "user123",
                    consentDecision = consent,
                    createdAt = now,
                    expiresAt = now + 15.minutes,
                    additionalData = emptyMap(),
                    nonce = "test-nonce-abc123",
                    authTime = now.epochSeconds,
                )

            val codeCommand =
                CreateAuthorizationCodeCommandImpl(
                    execution = ctx.execution,
                    authorizationCodeStorage = codeStorage,
                    configProvider = configProvider,
                    secureRandom = defaultSecureRandom(),
                    loginSessionIdProvider =
                        com.sphereon.oauth2.server.authorization.impl.storage
                            .DefaultOidcLoginSessionIdProvider(),
                    pushedAuthorizationRequestStorage = InMemoryPushedAuthorizationRequestStorageImpl(storage, Clock.System),
                    acrEnforcer =
                        com.sphereon.oauth2.server.authorization.impl.stepup
                            .DefaultOAuth2AcrEnforcer(),
                    clientRegistry = noncePropagationStubClientRegistry(),
                    requiredActionEvaluators = emptySet(),
                )

            val codeResult =
                codeCommand.execute(
                    CreateAuthorizationCodeArgs(
                        session = session,
                        userId = "user123",
                        consent = consent,
                    ),
                )

            assertTrue(codeResult.isOk, "Code creation should succeed")
            val code = codeResult.value.value

            // Consume the code to retrieve its data and verify nonce propagation
            val consumed = codeStorage.consumeAuthorizationCode(code)
            assertTrue(consumed.isOk)
            val codeData = consumed.value
            assertNotNull(codeData)
            assertEquals("test-nonce-abc123", codeData.nonce, "Nonce must propagate from session to code")
            assertNotNull(codeData.authTime, "authTime must propagate from session to code")
        }

    @Test
    fun nullNonceIsPreservedAsNull() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val codeStorage = InMemoryAuthorizationCodeStorageImpl(storage)

            val configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(
                        servers =
                            mapOf(
                                "default" to
                                    OAuth2ServerInstanceConfig(
                                        issuer = "https://auth.example.com",
                                        oidc = FeaturePolicy.SUPPORTED,
                                    ),
                            ),
                    ),
                )

            val now = Clock.System.now()
            val consent =
                ConsentDecision(
                    userId = "user456",
                    clientId = "test-client",
                    granted = true,
                    grantedScopes = listOf("read", "write"),
                    grantedAt = now,
                    rememberConsent = false,
                )

            // Session without nonce (non-OIDC request)
            val session =
                AuthorizationSession(
                    sessionId = "session-no-nonce",
                    clientId = "test-client",
                    redirectUri = "https://client.example.com/callback",
                    scope = "read write",
                    state = "state-456",
                    responseType = "code",
                    codeChallenge = null,
                    codeChallengeMethod = null,
                    status = SessionStatus.AUTHORIZED,
                    authenticatedUserId = "user456",
                    consentDecision = consent,
                    createdAt = now,
                    expiresAt = now + 15.minutes,
                    additionalData = emptyMap(),
                    nonce = null,
                )

            val codeCommand =
                CreateAuthorizationCodeCommandImpl(
                    execution = ctx.execution,
                    authorizationCodeStorage = codeStorage,
                    configProvider = configProvider,
                    secureRandom = defaultSecureRandom(),
                    loginSessionIdProvider =
                        com.sphereon.oauth2.server.authorization.impl.storage
                            .DefaultOidcLoginSessionIdProvider(),
                    pushedAuthorizationRequestStorage = InMemoryPushedAuthorizationRequestStorageImpl(storage, Clock.System),
                    acrEnforcer =
                        com.sphereon.oauth2.server.authorization.impl.stepup
                            .DefaultOAuth2AcrEnforcer(),
                    clientRegistry = noncePropagationStubClientRegistry(),
                    requiredActionEvaluators = emptySet(),
                )

            val codeResult =
                codeCommand.execute(
                    CreateAuthorizationCodeArgs(
                        session = session,
                        userId = "user456",
                        consent = consent,
                    ),
                )

            assertTrue(codeResult.isOk)
            val code = codeResult.value.value

            val consumed = codeStorage.consumeAuthorizationCode(code)
            assertTrue(consumed.isOk)
            assertNull(consumed.value!!.nonce, "Null nonce should remain null")
        }

    /**
     * Permissive ClientRegistry stub. The required-actions gate looks up the client
     * unconditionally but this test class doesn't exercise required actions; the stub
     * returns a minimal registration so the lookup succeeds and the empty evaluator
     * set short-circuits the gate.
     */
    private fun noncePropagationStubClientRegistry(): com.sphereon.oauth2.server.authorization.storage.ClientRegistry =
        object : com.sphereon.oauth2.server.authorization.storage.ClientRegistry {
            private val client =
                com.sphereon.oauth2.server.authorization.model.ClientRegistration(
                    clientId = "test-client",
                    grantTypes = listOf(com.sphereon.oauth2.common.model.GrantType.AUTHORIZATION_CODE),
                    redirectUris = listOf("https://client.example.com/callback"),
                )

            override suspend fun getClient(clientId: String) =
                com.sphereon.core.api
                    .Ok(if (clientId == client.clientId) client else null)

            override suspend fun registerClient(registration: com.sphereon.oauth2.server.authorization.model.ClientRegistration) =
                com.sphereon.core.api
                    .Ok(registration)

            override suspend fun updateClient(
                clientId: String,
                registration: com.sphereon.oauth2.server.authorization.model.ClientRegistration,
            ): com.sphereon.core.api.IdkResult<com.sphereon.oauth2.server.authorization.model.ClientRegistration, com.sphereon.oauth2.server.authorization.error.AuthorizationServerError> =
                com.sphereon.core.api
                    .Ok(registration)

            override suspend fun deleteClient(clientId: String): com.sphereon.core.api.IdkResult<Unit, com.sphereon.oauth2.server.authorization.error.AuthorizationServerError> =
                com.sphereon.core.api
                    .Ok(Unit)

            override suspend fun listClients(
                limit: Int,
                offset: Int
            ) = com.sphereon.core.api
                .Ok(emptyList<com.sphereon.oauth2.server.authorization.model.ClientRegistration>())

            override suspend fun findClientsByName(name: String) =
                com.sphereon.core.api
                    .Ok(emptyList<com.sphereon.oauth2.server.authorization.model.ClientRegistration>())

            override suspend fun clientExists(clientId: String) =
                com.sphereon.core.api
                    .Ok(true)

            override suspend fun verifyClientCredentials(
                clientId: String,
                clientSecret: String
            ) = com.sphereon.core.api
                .Ok(true)
        }
}
