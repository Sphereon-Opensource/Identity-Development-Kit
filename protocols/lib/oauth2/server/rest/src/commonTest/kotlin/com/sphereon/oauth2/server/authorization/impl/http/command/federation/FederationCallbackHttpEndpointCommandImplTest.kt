/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.oauth2.server.authorization.impl.http.command.federation

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.server.authorization.command.AuthorizationErrorResponseData
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseCommand
import com.sphereon.oauth2.server.authorization.command.federation.FederationCallbackOutcome
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationCallbackArgs
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationCallbackCommand
import com.sphereon.oauth2.server.authorization.impl.http.command.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.storage.PendingAuthorizationSessionStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FederationCallbackHttpEndpointCommandImplTest {
    @Test
    fun federationCompletionResumesOnMountedIssuerPath() =
        runTest {
            val command =
                FederationCallbackHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handleFederationCallbackCommand = CompletingCallbackCommand,
                    pendingAuthorizationSessionStore = EmptyPendingStore,
                    createAuthorizationErrorResponseCommand = UnusedErrorResponseCommand,
                    configProvider = TestOAuth2ServersConfigProvider(),
                    baseUrlResolver = MountedIssuerResolver,
                )

            val result =
                command.execute(
                    GenericHttpRequest(
                        method = "GET",
                        path = "/federation/callback",
                        queryParameters = mapOf("state" to "state-1", "code" to "code-1"),
                    ),
                )

            assertTrue(result.isOk, result.toString())
            assertEquals(302, result.value.statusCode)
            assertEquals(
                "https://sts.example/as/acme/authorize/callback?session_id=session-1",
                result.value.headers["Location"],
            )
        }

    private object CompletingCallbackCommand : HandleFederationCallbackCommand {
        override val commandId: String get() = HandleFederationCallbackCommand.COMMAND_ID
        override val inputTypeToken get() = typeToken<HandleFederationCallbackArgs>()
        override val outputTypeToken get() = typeToken<FederationCallbackOutcome>()
        override val isEnabled: Boolean = true

        override suspend fun supports(args: Any): Boolean = args is HandleFederationCallbackArgs

        override suspend fun execute(args: HandleFederationCallbackArgs): IdkResult<FederationCallbackOutcome, AuthenticationError> =
            Ok(FederationCallbackOutcome.federationComplete(sessionId = "session-1"))
    }

    private object UnusedErrorResponseCommand : CreateAuthorizationErrorResponseCommand {
        override val commandId: String get() = CreateAuthorizationErrorResponseCommand.COMMAND_ID
        override val inputTypeToken get() = typeToken<CreateAuthorizationErrorResponseArgs>()
        override val outputTypeToken get() = typeToken<AuthorizationErrorResponseData>()
        override val isEnabled: Boolean = true

        override suspend fun supports(args: Any): Boolean = args is CreateAuthorizationErrorResponseArgs

        override suspend fun execute(args: CreateAuthorizationErrorResponseArgs): IdkResult<AuthorizationErrorResponseData, IdkError> =
            error("The federation-complete branch must not create an authorization error response")
    }

    private object EmptyPendingStore : PendingAuthorizationSessionStore {
        override suspend fun create(session: AuthorizationSession): IdkResult<AuthorizationSession, IdkError> = Ok(session)

        override suspend fun findById(sessionId: String): IdkResult<AuthorizationSession?, IdkError> = Ok(null)

        override suspend fun remove(sessionId: String): IdkResult<Unit, IdkError> = Ok(Unit)
    }

    private object MountedIssuerResolver : FederationBaseUrlResolver {
        override suspend fun resolveBaseUrl(
            request: GenericHttpRequest,
            configProvider: com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider,
        ): String = "https://sts.example/as/acme"
    }
}
