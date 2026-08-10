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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.PublicClientConfig
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeArgs
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.stepup.DefaultOAuth2AcrEnforcer
import com.sphereon.oauth2.server.authorization.impl.storage.DefaultOidcLoginSessionIdProvider
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryAuthorizationCodeStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryPushedAuthorizationRequestStorageImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.model.ConsentDecision
import com.sphereon.oauth2.server.authorization.model.SessionStatus
import com.sphereon.oauth2.server.authorization.requiredaction.RequiredAction
import com.sphereon.oauth2.server.authorization.requiredaction.RequiredActionEvaluator
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Verifies the [com.sphereon.oauth2.server.authorization.requiredaction.RequiredActionEvaluator]
 * gate in [CreateAuthorizationCodeCommandImpl]: when any evaluator returns at least one
 * [RequiredAction], the AS refuses to mint and surfaces the action ids on the
 * [AuthorizationServerError.RequiredActionsPending] error meta.
 */
class RequiredActionsGateTest {
    private val ctx = OAuth2ServerTestContext("required-actions-gate-test", this)
    private val now = Clock.System.now()

    @Test
    fun mintsCodeWhenNoEvaluatorsAreRegistered() =
        runTest {
            val command = newCommand(evaluators = emptySet())
            val result = command.execute(args())
            assertTrue(result.isOk, "no evaluators → mint must succeed")
        }

    @Test
    fun mintsCodeWhenAllEvaluatorsReturnEmpty() =
        runTest {
            val command =
                newCommand(
                    evaluators =
                        setOf(
                            EmptyEvaluator,
                            EmptyEvaluator,
                            EmptyEvaluator,
                        ),
                )
            val result = command.execute(args())
            assertTrue(result.isOk, "all-empty evaluators → mint must succeed")
        }

    @Test
    fun refusesMintWhenAnyEvaluatorReturnsAction() =
        runTest {
            val command =
                newCommand(
                    evaluators =
                        setOf(
                            StaticEvaluator(
                                listOf(
                                    RequiredAction(
                                        actionId = "must-change-password",
                                        displayName = "Change your password",
                                        metadata = mapOf("policy_id" to "rotate-90d"),
                                    ),
                                ),
                            ),
                        ),
                )
            val result = command.execute(args())
            assertTrue(!result.isOk, "evaluator with unmet action → mint must refuse")
            val err = result.error
            assertEquals("interaction_required", err.code)
            @Suppress("UNCHECKED_CAST")
            val ids = err.meta["required_action_ids"] as List<String>
            assertContains(ids, "must-change-password")
        }

    @Test
    fun unionsActionsAcrossEvaluators() =
        runTest {
            // Two evaluators each return a different action; the gate must surface both
            // so the orchestrator builds a single IDV graph that walks the user through
            // every obligation in one session.
            val command =
                newCommand(
                    evaluators =
                        setOf(
                            StaticEvaluator(listOf(RequiredAction(actionId = "must-change-password", displayName = "Rotate password"))),
                            StaticEvaluator(listOf(RequiredAction(actionId = "accept-terms", displayName = "Accept ToS v3"))),
                        ),
                )
            val result = command.execute(args())
            assertTrue(!result.isOk)
            @Suppress("UNCHECKED_CAST")
            val ids = result.error.meta["required_action_ids"] as List<String>
            assertContains(ids, "must-change-password")
            assertContains(ids, "accept-terms")
        }

    @Test
    fun evaluatorReceivesClientUserAndSession() =
        runTest {
            // Capture-evaluator records its inputs; assert what the gate passes in.
            val capture = CapturingEvaluator()
            val command = newCommand(evaluators = setOf(capture))
            command.execute(args())
            assertEquals(CLIENT_ID, capture.lastClient?.clientId)
            assertEquals(USER_ID, capture.lastUserId)
            assertEquals(SESSION_ID, capture.lastSession?.sessionId)
        }

    @Test
    fun evaluatorReceivesPermissivePublicClientFallback() =
        runTest {
            val capture = CapturingEvaluator()
            val command =
                newCommand(
                    evaluators = setOf(capture),
                    clientRegistry = SingleClientRegistry(client = null),
                    publicClients = PublicClientConfig(allowAny = true, permissiveRedirectUri = true),
                )

            val result = command.execute(args())

            assertTrue(result.isOk, "permitted unregistered public client must survive the required-actions gate")
            assertEquals(CLIENT_ID, capture.lastClient?.clientId)
            assertTrue(capture.lastClient?.requirePkce == true)
        }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun newCommand(
        evaluators: Set<RequiredActionEvaluator>,
        clientRegistry: ClientRegistry = SingleClientRegistry(),
        publicClients: PublicClientConfig = PublicClientConfig(),
    ): CreateAuthorizationCodeCommandImpl {
        val backing = InMemoryOAuth2BackingStorageImpl()
        return CreateAuthorizationCodeCommandImpl(
            execution = ctx.execution,
            authorizationCodeStorage = InMemoryAuthorizationCodeStorageImpl(backing),
            configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(
                        servers =
                            mapOf(
                                "default" to
                                    OAuth2ServerInstanceConfig(
                                        issuer = "https://auth.example.com",
                                        oidc = FeaturePolicy.SUPPORTED,
                                        publicClients = publicClients,
                                    ),
                            ),
                    ),
                ),
            secureRandom = defaultSecureRandom(),
            loginSessionIdProvider = DefaultOidcLoginSessionIdProvider(),
            pushedAuthorizationRequestStorage = InMemoryPushedAuthorizationRequestStorageImpl(backing, Clock.System),
            acrEnforcer = DefaultOAuth2AcrEnforcer(),
            clientRegistry = clientRegistry,
            requiredActionEvaluators = evaluators,
        )
    }

    private fun args(): CreateAuthorizationCodeArgs =
        CreateAuthorizationCodeArgs(
            session = session(),
            userId = USER_ID,
            consent = consent(),
        )

    private fun session(): AuthorizationSession =
        AuthorizationSession(
            sessionId = SESSION_ID,
            clientId = CLIENT_ID,
            redirectUri = "https://app.example.org/cb",
            scope = "openid",
            state = null,
            responseType = "code",
            codeChallenge = null,
            codeChallengeMethod = null,
            authTime = now.epochSeconds,
            status = SessionStatus.AUTHORIZED,
            authenticatedUserId = USER_ID,
            consentDecision = consent(),
            createdAt = now,
            expiresAt = now + 15.minutes,
            acrValues = null,
            maxAge = null,
            additionalData = emptyMap(),
        )

    private fun consent() =
        ConsentDecision(
            userId = USER_ID,
            clientId = CLIENT_ID,
            granted = true,
            grantedScopes = listOf("openid"),
            grantedAt = now,
            rememberConsent = false,
        )

    private object EmptyEvaluator : RequiredActionEvaluator {
        override suspend fun evaluate(
            client: ClientRegistration,
            userId: String,
            session: AuthorizationSession,
        ): List<RequiredAction> = emptyList()
    }

    private class StaticEvaluator(
        private val actions: List<RequiredAction>
    ) : RequiredActionEvaluator {
        override suspend fun evaluate(
            client: ClientRegistration,
            userId: String,
            session: AuthorizationSession,
        ): List<RequiredAction> = actions
    }

    private class CapturingEvaluator : RequiredActionEvaluator {
        var lastClient: ClientRegistration? = null
        var lastUserId: String? = null
        var lastSession: AuthorizationSession? = null

        override suspend fun evaluate(
            client: ClientRegistration,
            userId: String,
            session: AuthorizationSession,
        ): List<RequiredAction> {
            lastClient = client
            lastUserId = userId
            lastSession = session
            return emptyList()
        }
    }

    private class SingleClientRegistry(
        private val client: ClientRegistration? =
            ClientRegistration(
                clientId = CLIENT_ID,
                grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
                redirectUris = listOf("https://app.example.org/cb"),
            ),
    ) : ClientRegistry {

        override suspend fun getClient(clientId: String): IdkResult<ClientRegistration?, AuthorizationServerError.StorageError> =
            Ok(client?.takeIf { clientId == it.clientId })

        override suspend fun registerClient(registration: ClientRegistration): IdkResult<ClientRegistration, AuthorizationServerError.StorageError> = Ok(registration)

        override suspend fun updateClient(
            clientId: String,
            registration: ClientRegistration,
        ): IdkResult<ClientRegistration, AuthorizationServerError> = Ok(registration)

        override suspend fun deleteClient(clientId: String): IdkResult<Unit, AuthorizationServerError> = Ok(Unit)

        override suspend fun listClients(
            limit: Int,
            offset: Int,
        ): IdkResult<List<ClientRegistration>, AuthorizationServerError.StorageError> = Ok(emptyList())

        override suspend fun findClientsByName(name: String): IdkResult<List<ClientRegistration>, AuthorizationServerError.StorageError> = Ok(emptyList())

        override suspend fun clientExists(clientId: String): IdkResult<Boolean, AuthorizationServerError.StorageError> = Ok(true)

        override suspend fun verifyClientCredentials(
            clientId: String,
            clientSecret: String,
        ): IdkResult<Boolean, AuthorizationServerError.StorageError> = Ok(true)
    }

    companion object {
        private const val CLIENT_ID = "test-client"
        private const val USER_ID = "user-1"
        private const val SESSION_ID = "session-required-actions"
    }
}
