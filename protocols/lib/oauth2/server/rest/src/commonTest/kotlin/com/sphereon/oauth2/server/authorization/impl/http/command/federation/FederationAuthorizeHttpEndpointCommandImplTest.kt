/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.oauth2.server.authorization.impl.http.command.federation

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.server.authorization.command.federation.AuthorizationUrl
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationCommand
import com.sphereon.oauth2.server.authorization.impl.http.command.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRoute
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRouteBinding
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRouteDecision
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRoutePlanner
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRouteRequest
import com.sphereon.oauth2.server.authorization.storage.PendingAuthorizationSessionStore
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FederationAuthorizeHttpEndpointCommandImplTest {
    @Test
    fun pendingAuthorizationSessionAndReturnUrlArePreserved() =
        runTest {
            val initiate = CapturingInitiateCommand()
            val command = command(initiate)
            val result =
                command.execute(
                    GenericHttpRequest(
                        method = "GET",
                        path = "/federation/authorize",
                        queryParameters =
                            mapOf(
                                "provider" to BINDING_ID,
                                "session_id" to "pending-123",
                                "return_url" to "$ISSUER/authorize/callback?session_id=pending-123",
                            ),
                    ),
                )
            assertTrue(result.isOk)
            assertEquals(302, result.value.statusCode)
            assertEquals("pending-123", initiate.lastArgs?.sessionId)
            assertEquals("$ISSUER/authorize/callback?session_id=pending-123", initiate.lastArgs?.returnUrl)
            assertEquals(APPLICATION_ID, initiate.lastArgs?.applicationId)
        }

    @Test
    fun untrustedReturnUrlFallsBackToIssuerCallback() =
        runTest {
            val initiate = CapturingInitiateCommand()
            command(initiate).execute(
                GenericHttpRequest(
                    method = "GET",
                    path = "/federation/authorize",
                    queryParameters =
                        mapOf(
                            "provider" to BINDING_ID,
                            "session_id" to "pending-123",
                            "return_url" to "https://evil.example/authorize/callback?session_id=pending-123",
                        ),
                ),
            )
            assertEquals("$ISSUER/authorize/callback?session_id=pending-123", initiate.lastArgs?.returnUrl)
        }

    @Test
    fun federationWithoutPendingAuthorizationSessionIsRejected() =
        runTest {
            val initiate = CapturingInitiateCommand()
            val result =
                command(initiate).execute(
                    GenericHttpRequest(
                        method = "GET",
                        path = "/federation/authorize",
                        queryParameters = mapOf("provider" to BINDING_ID),
                    ),
                )
            assertTrue(result.isErr)
            assertEquals(null, initiate.lastArgs)
        }

    @Test
    fun forcedReauthenticationIsForwardedToFederation() =
        runTest {
            val initiate = CapturingInitiateCommand()
            command(initiate).execute(
                GenericHttpRequest(
                    method = "GET",
                    path = "/federation/authorize",
                    queryParameters =
                        mapOf(
                            "provider" to BINDING_ID,
                            "session_id" to "pending-123",
                            "force_reauth" to "true",
                        ),
                ),
            )
            assertEquals(true, initiate.lastArgs?.forceReauth)
        }

    private fun command(initiate: CapturingInitiateCommand): FederationAuthorizeHttpEndpointCommandImpl =
        FederationAuthorizeHttpEndpointCommandImpl(
            execution = TestSessionExecution(),
            initiateProviderAuthenticationCommand = initiate,
            configProvider =
                TestOAuth2ServersConfigProvider(
                    OAuth2ServersConfig(servers = mapOf("default" to OAuth2ServerInstanceConfig(issuer = ISSUER))),
                ),
            baseUrlResolver = DefaultFederationBaseUrlResolver(),
            pendingAuthorizationSessionStore = PendingStore,
            authenticationRoutePlanner = AllowingAuthenticationRoutePlanner,
        )

    private object PendingStore : PendingAuthorizationSessionStore {
        override suspend fun create(session: AuthorizationSession): IdkResult<AuthorizationSession, IdkError> = Ok(session)

        override suspend fun findById(sessionId: String): IdkResult<AuthorizationSession?, IdkError> =
            Ok(
                if (sessionId == "pending-123") {
                    AuthorizationSession(
                        sessionId = sessionId,
                        clientId = "client",
                        applicationId = APPLICATION_ID,
                        responseType = "code",
                        redirectUri = "https://rp.example/callback",
                        createdAt = Clock.System.now(),
                        expiresAt = Clock.System.now() + 10.minutes,
                        authenticationRoute =
                            AuthenticationRouteDecision(
                                route = AuthenticationRoute.CHOOSER,
                                hostedAuthorizationServerId = HOSTED_AS_ID,
                                hostedAuthorizationServerRevision = 1,
                                localLoginAllowed = true,
                                eligibleBindings =
                                    listOf(
                                        AuthenticationRouteBinding(
                                            bindingId = BINDING_ID,
                                            upstreamResourceId = UPSTREAM_RESOURCE_ID,
                                            displayName = "Federated fixture",
                                            upstreamIssuer = "https://idp.example",
                                            bindingRevision = 1,
                                            upstreamResourceRevision = 1,
                                            claimsMapping = emptyMap(),
                                        ),
                                    ),
                            ),
                    )
                } else {
                    null
                },
            )

        override suspend fun remove(sessionId: String): IdkResult<Unit, IdkError> = Ok(Unit)
    }

    private class CapturingInitiateCommand : InitiateProviderAuthenticationCommand {
        var lastArgs: InitiateProviderAuthenticationArgs? = null
        override val commandId: String get() = InitiateProviderAuthenticationCommand.COMMAND_ID
        override val inputTypeToken get() = typeToken<InitiateProviderAuthenticationArgs>()
        override val outputTypeToken get() = typeToken<AuthorizationUrl>()
        override val isEnabled: Boolean = true
        override suspend fun supports(args: Any): Boolean = args is InitiateProviderAuthenticationArgs
        override suspend fun execute(args: InitiateProviderAuthenticationArgs): IdkResult<AuthorizationUrl, AuthenticationError> {
            lastArgs = args
            return Ok(AuthorizationUrl("https://idp.example/authorize"))
        }
    }

    private object AllowingAuthenticationRoutePlanner : AuthenticationRoutePlanner {
        override suspend fun decide(request: AuthenticationRouteRequest): IdkResult<AuthenticationRouteDecision, IdkError> =
            error("Route planning is not used by the federation HTTP endpoint test")

        override suspend fun revalidate(
            decision: AuthenticationRouteDecision,
            selectedBindingId: String,
        ): IdkResult<Unit, IdkError> = Ok(Unit)
    }

    companion object {
        private const val ISSUER = "https://sts.example"
        private const val HOSTED_AS_ID = "00000000-0000-4000-8000-000000000001"
        private const val BINDING_ID = "00000000-0000-4000-8000-000000000002"
        private const val UPSTREAM_RESOURCE_ID = "00000000-0000-4000-8000-000000000003"
        private const val APPLICATION_ID = "00000000-0000-4000-8000-000000000004"
    }
}
