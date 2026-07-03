/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.core.api.service.AuthAssuranceLevel
import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeArgs
import com.sphereon.oauth2.server.authorization.impl.stepup.DefaultOAuth2AcrEnforcer
import com.sphereon.oauth2.server.authorization.impl.storage.DefaultOidcLoginSessionIdProvider
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Verifies [CreateAuthorizationCodeCommandImpl] refuses to mint a code when the
 * achieved authentication doesn't satisfy the request's `acr_values` / `max_age` —
 * RFC 9470 / OIDC Core §3.1.2.1 step-up enforcement.
 *
 * Wires the IDK [DefaultOAuth2AcrEnforcer] (SAML PasswordProtectedTransport /
 * MobileTwoFactorContract / SmartcardPKI vocabulary) so the test exercises the
 * enforcement at the seam without standing up the broader EDK
 * [com.sphereon.authz.authzen.stepup.StepUpEvaluator] framework.
 */
class AcrStepUpEnforcementTest {
    private val ctx = OAuth2ServerTestContext("acr-stepup-test", this)
    private val now = Clock.System.now()

    @Test
    fun mintsCodeWhenNoAcrValuesRequested() =
        runTest {
            val command = newCommand()
            val session = newSession(acrValues = null)

            val result =
                command.execute(
                    CreateAuthorizationCodeArgs(
                        session = session,
                        userId = "user-1",
                        consent = consent(),
                    ),
                )
            assertTrue(result.isOk, "no acr_values demand → code must mint")
        }

    @Test
    fun mintsCodeWhenRequestedAcrIsSatisfied() =
        runTest {
            val command = newCommand()
            val session =
                newSession(
                    acrValues = listOf(DefaultOAuth2AcrEnforcer.SAML_MOBILE_TWO_FACTOR_CONTRACT),
                )

            val result =
                command.execute(
                    CreateAuthorizationCodeArgs(
                        session = session,
                        userId = "user-1",
                        consent = consent(),
                        acr = DefaultOAuth2AcrEnforcer.SAML_MOBILE_TWO_FACTOR_CONTRACT,
                        amr = listOf("mfa"),
                    ),
                )
            assertTrue(result.isOk, "AAL2 demand + AAL2 achieved → code must mint")
        }

    @Test
    fun mintsCodeWhenRequestedNistAcrIsSatisfied() =
        runTest {
            val command = newCommand()
            val session =
                newSession(
                    acrValues = listOf(AuthAssuranceLevel.AAL2.acr),
                )

            val result =
                command.execute(
                    CreateAuthorizationCodeArgs(
                        session = session,
                        userId = "user-1",
                        consent = consent(),
                        acr = AuthAssuranceLevel.AAL2.acr,
                        amr = listOf("mfa"),
                    ),
                )
            assertTrue(result.isOk, "canonical NIST AAL2 demand + NIST AAL2 achieved → code must mint")
        }

    @Test
    fun refusesCodeWhenAchievedAclTooWeak() =
        runTest {
            val command = newCommand()
            val session =
                newSession(
                    acrValues = listOf(DefaultOAuth2AcrEnforcer.SAML_MOBILE_TWO_FACTOR_CONTRACT),
                )

            val result =
                command.execute(
                    CreateAuthorizationCodeArgs(
                        session = session,
                        userId = "user-1",
                        consent = consent(),
                        acr = DefaultOAuth2AcrEnforcer.SAML_PASSWORD_PROTECTED_TRANSPORT,
                        amr = listOf("pwd"),
                    ),
                )
            assertTrue(!result.isOk, "AAL2 demand + AAL1 achieved → code must NOT mint")
            val err = result.error
            assertEquals("insufficient_user_authentication", err.code)
            assertNotNull(err.meta["required_aal"])
            assertEquals("AAL2", err.meta["required_aal"])
            assertEquals("AAL1", err.meta["current_aal"])
            assertEquals("INSUFFICIENT_ACR", err.meta["reason"])
        }

    @Test
    fun refusesCodeWhenMaxAgeExpired() =
        runTest {
            val command = newCommand()
            // Auth happened 10min ago; max_age 60s → too stale.
            val session =
                newSession(
                    acrValues = null,
                    authTime = (now - 10.minutes).epochSeconds,
                    maxAge = 60,
                )

            val result =
                command.execute(
                    CreateAuthorizationCodeArgs(
                        session = session,
                        userId = "user-1",
                        consent = consent(),
                        acr = DefaultOAuth2AcrEnforcer.SAML_MOBILE_TWO_FACTOR_CONTRACT,
                        amr = listOf("mfa"),
                    ),
                )
            assertTrue(!result.isOk, "stale auth (max_age elapsed) → code must NOT mint")
            assertEquals("STALE_AUTH", result.error.meta["reason"])
            assertEquals(60L, result.error.meta["max_age"])
        }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun newCommand(): CreateAuthorizationCodeCommandImpl {
        val backing = InMemoryOAuth2BackingStorageImpl()
        val codeStorage = InMemoryAuthorizationCodeStorageImpl(backing)
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
        return CreateAuthorizationCodeCommandImpl(
            execution = ctx.execution,
            authorizationCodeStorage = codeStorage,
            configProvider = configProvider,
            secureRandom = defaultSecureRandom(),
            loginSessionIdProvider = DefaultOidcLoginSessionIdProvider(),
            pushedAuthorizationRequestStorage = InMemoryPushedAuthorizationRequestStorageImpl(backing, Clock.System),
            acrEnforcer = DefaultOAuth2AcrEnforcer(),
            clientRegistry = StubAcceptingClientRegistry(),
            requiredActionEvaluators = emptySet(),
        )
    }

    private fun newSession(
        acrValues: List<String>?,
        authTime: Long = now.epochSeconds,
        maxAge: Long? = null,
    ): AuthorizationSession =
        AuthorizationSession(
            sessionId = "sess-$authTime-${maxAge ?: 0}",
            clientId = "test-client",
            redirectUri = "https://app.example.org/cb",
            scope = "openid",
            state = null,
            responseType = "code",
            codeChallenge = null,
            codeChallengeMethod = null,
            authTime = authTime,
            status = SessionStatus.AUTHORIZED,
            authenticatedUserId = "user-1",
            consentDecision = consent(),
            createdAt = now,
            expiresAt = now + 15.minutes,
            acrValues = acrValues,
            maxAge = maxAge,
            additionalData = emptyMap(),
        )

    private fun consent(): ConsentDecision =
        ConsentDecision(
            userId = "user-1",
            clientId = "test-client",
            granted = true,
            grantedScopes = listOf("openid"),
            grantedAt = now,
            rememberConsent = false,
        )

    /**
     * One-client stub registry that returns a permissive ClientRegistration. The ACR
     * step-up tests don't exercise required-actions, but the gate's ClientRegistry
     * lookup runs unconditionally; this stub keeps it green.
     */
    private class StubAcceptingClientRegistry : com.sphereon.oauth2.server.authorization.storage.ClientRegistry {
        private val client =
            com.sphereon.oauth2.server.authorization.model.ClientRegistration(
                clientId = "test-client",
                grantTypes = listOf(com.sphereon.oauth2.common.model.GrantType.AUTHORIZATION_CODE),
                redirectUris = listOf("https://app.example.org/cb"),
            )

        override suspend fun getClient(
            clientId: String
        ): com.sphereon.core.api.IdkResult<com.sphereon.oauth2.server.authorization.model.ClientRegistration?, com.sphereon.oauth2.server.authorization.error.AuthorizationServerError.StorageError> =
            com.sphereon.core.api
                .Ok(if (clientId == client.clientId) client else null)

        override suspend fun registerClient(
            registration: com.sphereon.oauth2.server.authorization.model.ClientRegistration
        ): com.sphereon.core.api.IdkResult<com.sphereon.oauth2.server.authorization.model.ClientRegistration, com.sphereon.oauth2.server.authorization.error.AuthorizationServerError.StorageError> =
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
            offset: Int,
        ): com.sphereon.core.api.IdkResult<
            List<com.sphereon.oauth2.server.authorization.model.ClientRegistration>,
            com.sphereon.oauth2.server.authorization.error.AuthorizationServerError.StorageError,
        > =
            com.sphereon.core.api
                .Ok(emptyList())

        override suspend fun findClientsByName(
            name: String,
        ): com.sphereon.core.api.IdkResult<
            List<com.sphereon.oauth2.server.authorization.model.ClientRegistration>,
            com.sphereon.oauth2.server.authorization.error.AuthorizationServerError.StorageError,
        > =
            com.sphereon.core.api
                .Ok(emptyList())

        override suspend fun clientExists(clientId: String): com.sphereon.core.api.IdkResult<Boolean, com.sphereon.oauth2.server.authorization.error.AuthorizationServerError.StorageError> =
            com.sphereon.core.api
                .Ok(true)

        override suspend fun verifyClientCredentials(
            clientId: String,
            clientSecret: String,
        ): com.sphereon.core.api.IdkResult<Boolean, com.sphereon.oauth2.server.authorization.error.AuthorizationServerError.StorageError> =
            com.sphereon.core.api
                .Ok(true)
    }
}
