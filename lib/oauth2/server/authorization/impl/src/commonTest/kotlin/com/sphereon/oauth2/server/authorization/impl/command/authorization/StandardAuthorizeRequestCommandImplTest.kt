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

package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestData
import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestOutcome
import com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.authorization.HandleAuthorizeRequestArgs
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.command.jar.StubVerifyRequestObjectCommand
import com.sphereon.oauth2.server.authorization.impl.command.orchestration.StubAuthorizationServerService
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.AuthenticationContext
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionIdProvider
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionStore
import com.sphereon.oauth2.server.authorization.storage.PendingAuthorizationSessionStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class StandardAuthorizeRequestCommandImplTest {
    private val ctx = OAuth2ServerTestContext("standard-authorize-request-test", this)

    private val configProvider =
        TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers = mapOf("default" to OAuth2ServerInstanceConfig(issuer = "https://as.example.com")),
            ),
        )

    private fun stubParseAuthorizationRequest(handler: suspend (ParseAuthorizationRequestArgs) -> IdkResult<AuthorizationRequestData, IdkError>): ParseAuthorizationRequestCommand =
        object : ParseAuthorizationRequestCommand {
            override val inputTypeToken = typeToken<ParseAuthorizationRequestArgs>()
            override val outputTypeToken = typeToken<AuthorizationRequestData>()
            override val isEnabled = true

            override suspend fun execute(args: ParseAuthorizationRequestArgs) = handler(args)
        }

    private class EmptyClientRegistry : ClientRegistry {
        override suspend fun getClient(clientId: String): IdkResult<ClientRegistration?, AuthorizationServerError.StorageError> = Ok(null)

        override suspend fun registerClient(registration: ClientRegistration): IdkResult<ClientRegistration, AuthorizationServerError.StorageError> = Ok(registration)

        override suspend fun updateClient(
            clientId: String,
            registration: ClientRegistration
        ): IdkResult<ClientRegistration, AuthorizationServerError> = Ok(registration)

        override suspend fun deleteClient(clientId: String): IdkResult<Unit, AuthorizationServerError> = Ok(Unit)

        override suspend fun listClients(
            limit: Int,
            offset: Int
        ): IdkResult<List<ClientRegistration>, AuthorizationServerError.StorageError> = Ok(emptyList())

        override suspend fun findClientsByName(name: String): IdkResult<List<ClientRegistration>, AuthorizationServerError.StorageError> = Ok(emptyList())

        override suspend fun clientExists(clientId: String): IdkResult<Boolean, AuthorizationServerError.StorageError> = Ok(false)

        override suspend fun verifyClientCredentials(
            clientId: String,
            clientSecret: String
        ): IdkResult<Boolean, AuthorizationServerError.StorageError> = Ok(false)
    }

    private class FakeUserAuthenticationProvider : UserAuthenticationProvider {
        override suspend fun getAuthenticatedUser(sessionId: String): IdkResult<AuthenticatedUser?, AuthenticationError> = Ok(null)

        override suspend fun initiateAuthentication(
            sessionId: String,
            returnUrl: String,
            hint: AuthenticationHint?,
            context: AuthenticationContext?
        ): IdkResult<String, AuthenticationError> = Err(AuthenticationError.Generic(description = "auth provider unused in this test"))

        override suspend fun authenticateWithCredentials(
            credentials: UserCredentials,
            context: AuthenticationContext?
        ): IdkResult<String?, AuthenticationError> = Ok(null)

        override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> = Ok(Unit)

        override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> = Err(AuthenticationError.UserNotFound())

        override suspend fun isAuthenticationMethodAvailable(method: AuthenticationMethod): IdkResult<Boolean, AuthenticationError> = Ok(false)
    }

    private class FakePendingAuthorizationSessionStore : PendingAuthorizationSessionStore {
        override suspend fun create(session: AuthorizationSession): IdkResult<AuthorizationSession, IdkError> = Ok(session)

        override suspend fun findById(sessionId: String): IdkResult<AuthorizationSession?, IdkError> = Ok(null)

        override suspend fun remove(sessionId: String): IdkResult<Unit, IdkError> = Ok(Unit)
    }

    private fun service(parseStub: ParseAuthorizationRequestCommand): AuthorizationServerService =
        object : StubAuthorizationServerService() {
            override val commands: AuthorizationServerService.Commands = TestCommands(parseStub)
        }

    private class TestCommands(
        private val parseStub: ParseAuthorizationRequestCommand,
    ) : AuthorizationServerService.Commands {
        override val parseTokenRequest get(): com.sphereon.oauth2.server.authorization.command.ParseTokenRequestCommand = throw NotImplementedError()
        override val verifyAuthorizationCodeGrant get(): com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantCommand = throw NotImplementedError()
        override val verifyRefreshTokenGrant get(): com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantCommand = throw NotImplementedError()
        override val verifyClientCredentialsGrant get(): com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantCommand = throw NotImplementedError()
        override val verifyTokenExchangeGrant get(): com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantCommand = throw NotImplementedError()
        override val verifyPreAuthorizedCodeGrant get(): com.sphereon.oauth2.server.authorization.command.VerifyPreAuthorizedCodeGrantCommand = throw NotImplementedError()
        override val createAccessToken get(): com.sphereon.oauth2.server.authorization.command.CreateAccessTokenCommand = throw NotImplementedError()
        override val createRefreshToken get(): com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenCommand = throw NotImplementedError()
        override val createTokenResponse get(): com.sphereon.oauth2.server.authorization.command.CreateTokenResponseCommand = throw NotImplementedError()
        override val parseAuthorizationRequest get() = parseStub
        override val verifyAuthorizationRequest get(): com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationRequestCommand = throw NotImplementedError()
        override val createAuthorizationSession get(): com.sphereon.oauth2.server.authorization.command.CreateAuthorizationSessionCommand = throw NotImplementedError()
        override val createAuthorizationCode get(): com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeCommand = throw NotImplementedError()
        override val createAuthorizationResponse get(): com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseCommand = throw NotImplementedError()
        override val createAuthorizationErrorResponse get(): com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseCommand = throw NotImplementedError()
        override val parsePushedAuthorizationRequest get(): com.sphereon.oauth2.server.authorization.command.ParsePushedAuthorizationRequestCommand = throw NotImplementedError()
        override val verifyPushedAuthorizationRequest get(): com.sphereon.oauth2.server.authorization.command.VerifyPushedAuthorizationRequestCommand = throw NotImplementedError()
        override val createRequestUri get(): com.sphereon.oauth2.server.authorization.command.CreateRequestUriCommand = throw NotImplementedError()
        override val createPushedAuthorizationResponse get(): com.sphereon.oauth2.server.authorization.command.CreatePushedAuthorizationResponseCommand = throw NotImplementedError()
        override val retrieveAuthorizationRequestByUri get(): com.sphereon.oauth2.server.authorization.command.RetrieveAuthorizationRequestByUriCommand = throw NotImplementedError()
        override val parseIntrospectionRequest get(): com.sphereon.oauth2.server.authorization.command.ParseIntrospectionRequestCommand = throw NotImplementedError()
        override val introspectToken get(): com.sphereon.oauth2.server.authorization.command.IntrospectTokenCommand = throw NotImplementedError()
        override val parseRevocationRequest get(): com.sphereon.oauth2.server.authorization.command.ParseRevocationRequestCommand = throw NotImplementedError()
        override val revokeToken get(): com.sphereon.oauth2.server.authorization.command.RevokeTokenCommand = throw NotImplementedError()
        override val buildServerMetadata get(): com.sphereon.oauth2.server.authorization.command.BuildServerMetadataCommand = throw NotImplementedError()
        override val verifyClientAuthentication get(): com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationCommand = throw NotImplementedError()
        override val createAttestationChallenge get(): com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeCommand = throw NotImplementedError()
        override val createIdToken get(): com.sphereon.oauth2.server.authorization.command.CreateIdTokenCommand = throw NotImplementedError()
        override val getUserInfo get(): com.sphereon.oauth2.server.authorization.command.GetUserInfoCommand = throw NotImplementedError()
        override val getJwks get(): com.sphereon.oauth2.server.authorization.command.GetJwksCommand = throw NotImplementedError()
    }

    private fun command(service: AuthorizationServerService): StandardAuthorizeRequestCommandImpl =
        StandardAuthorizeRequestCommandImpl(
            execution = ctx.execution,
            authorizationServerService = service,
            clientRegistry = EmptyClientRegistry(),
            serversConfigProvider = configProvider,
            userAuthProvider = FakeUserAuthenticationProvider(),
            pendingAuthorizationSessionStore = FakePendingAuthorizationSessionStore(),
            loginSessionStore = NoOpLoginSessionStore(),
            loginSessionIdProvider = NoOpLoginSessionIdProvider(),
            verifyRequestObjectCommand = StubVerifyRequestObjectCommand(ctx.execution),
            clock = FixedClock(Instant.fromEpochSeconds(1_700_000_000)),
        )

    private class NoOpLoginSessionStore : OidcLoginSessionStore {
        override suspend fun create(session: com.sphereon.oauth2.server.authorization.storage.OidcLoginSession) = Ok(session)

        override suspend fun findById(sessionId: String) = Ok(null)

        override suspend fun touch(
            sessionId: String,
            now: Instant,
            idleTtlSeconds: Int
        ) = Ok(null)

        override suspend fun recordRpParticipation(
            sessionId: String,
            clientId: String,
            sid: String,
        ) = Ok(null)

        override suspend fun revoke(sessionId: String) = Ok(Unit)

        override suspend fun revokeAllForUser(sub: String) = Ok(Unit)
    }

    private class NoOpLoginSessionIdProvider : OidcLoginSessionIdProvider {
        override fun currentLoginSessionId(): String? = null
    }

    private class FixedClock(
        private val instant: Instant
    ) : Clock {
        override fun now(): Instant = instant
    }

    @Test
    fun parseFailureProducesPreRedirectErrorOutcome() =
        runTest {
            val service =
                service(
                    parseStub = stubParseAuthorizationRequest { Err(IdkError.fromString(code = "invalid_request", message = "missing client_id")) },
                )

            val result = command(service).execute(HandleAuthorizeRequestArgs(queryParameters = emptyMap()))

            assertTrue(result.isOk)
            val outcome = result.value
            val pre = assertIs<AuthorizationRequestOutcome.PreRedirectError>(outcome)
            assertEquals("invalid_request", pre.error)
        }

    @Test
    fun supportsRejectsWalletLoginHint() =
        runTest {
            val service =
                service(
                    parseStub = stubParseAuthorizationRequest { error("not invoked") },
                )
            val cmd = command(service)
            val walletArgs = HandleAuthorizeRequestArgs(queryParameters = mapOf("login_hint" to "oid4vp:abc"))
            val standardArgs = HandleAuthorizeRequestArgs(queryParameters = mapOf("login_hint" to "user@example.com"))
            val emptyArgs = HandleAuthorizeRequestArgs(queryParameters = emptyMap())

            assertFalse(cmd.supports(walletArgs))
            assertTrue(cmd.supports(standardArgs))
            assertTrue(cmd.supports(emptyArgs))
        }
}
