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
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.model.OAuth2ResponseMode
import com.sphereon.oauth2.common.model.ResponseType
import com.sphereon.oauth2.server.authorization.command.AuthorizationErrorResponseData
import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestData
import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestOutcome
import com.sphereon.oauth2.server.authorization.command.AuthorizationResponseData
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationSessionCommand
import com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationRequest
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationRequestCommand
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
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSession
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionIdProvider
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionStore
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionStoreError
import com.sphereon.oauth2.server.authorization.storage.PendingAuthorizationSessionStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Drives [StandardAuthorizeRequestCommandImpl]'s session-evaluation table end-to-end via the
 * command's `execute` entry point. Each row of the OIDC `prompt` / `max_age` / `id_token_hint`
 * spec table maps to one assertion here. Stubs cover everything below the evaluation step
 * (parse, verify, createSession, createCode, createResponse) so a single fixture exercises the
 * complete dispatch logic with deterministic inputs.
 */
class SessionEvaluationTest {
    private val ctx = OAuth2ServerTestContext("session-evaluation-test", this)

    private val issuer = "https://as.example.com"
    private val clientId = "test-client"
    private val redirectUri = "https://client.example/cb"
    private val state = "state-xyz"
    private val configProvider =
        TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers =
                    mapOf(
                        "default" to
                            OAuth2ServerInstanceConfig(
                                issuer = issuer,
                            ),
                    ),
            ),
        )

    /**
     * Build a faux JWS carrying [sub] and [iss] in the payload. The decoder only inspects those
     * two claims, so no real signing is needed.
     */
    private fun fauxIdTokenHint(
        sub: String,
        iss: String = issuer
    ): String {
        val header = "{\"alg\":\"none\"}".encodeToByteArray().encodeToBase64Url()
        val payload = "{\"iss\":\"$iss\",\"sub\":\"$sub\"}".encodeToByteArray().encodeToBase64Url()
        val sig = "sig".encodeToByteArray().encodeToBase64Url()
        return "$header.$payload.$sig"
    }

    @Test
    fun promptNoneNoSessionFailsLoginRequired() =
        runTest {
            val outcome = runOnce(prompt = "none", session = null)
            val err = assertIs<AuthorizationRequestOutcome.PostRedirectError>(outcome)
            assertEquals("login_required", err.error)
        }

    @Test
    fun noSessionNoPromptFallsThroughToFederationWhenOAuthAvailable() =
        runTest {
            val outcome = runOnce(prompt = null, session = null, oauthAvailable = true)
            val initiated = assertIs<AuthorizationRequestOutcome.AuthInitiated>(outcome)
            assertEquals(FAKE_IDP_REDIRECT, initiated.authProviderRedirectUrl)
        }

    @Test
    fun noSessionNoPromptRoutesToLoginWhenOAuthUnavailable() =
        runTest {
            val outcome = runOnce(prompt = null, session = null, oauthAvailable = false)
            val needs = assertIs<AuthorizationRequestOutcome.NeedsLogin>(outcome)
            assertEquals(false, needs.forceReauth)
        }

    @Test
    fun promptNoneWithSessionSucceedsSilentlyAndReusesAuthTime() =
        runTest {
            val authTime = Instant.fromEpochSeconds(1_700_000_000)
            val session = newLoginSession(authTime = authTime)
            val outcome = runOnce(prompt = "none", session = session)
            val completed = assertIs<AuthorizationRequestOutcome.WalletCompleted>(outcome)
            assertEquals("https://client.example/cb?code=fake-code", completed.authorizationResponseData.redirectUri)
            assertEquals(authTime.epochSeconds, capturedCodeArgs?.session?.authTime)
            assertEquals(session.sub, capturedCodeArgs?.userId)
        }

    @Test
    fun loginSessionAuthorizationClaimsSurviveUnavailableUserInfo() =
        runTest {
            val roles = JsonArray(listOf(JsonPrimitive("tenant-admin")))
            val session = newLoginSession(claims = mapOf("roles" to roles))

            val outcome = runOnce(prompt = "none", session = session)

            assertIs<AuthorizationRequestOutcome.WalletCompleted>(outcome)
            assertEquals(
                roles,
                capturedCodeArgs?.userClaims?.get("roles"),
                "server-authenticated session roles must reach the authorization code when split-AS userinfo is unavailable",
            )
        }

    @Test
    fun promptLoginForcesReauthRegardlessOfSession() =
        runTest {
            val outcome = runOnce(prompt = "login", session = newLoginSession())
            val needs = assertIs<AuthorizationRequestOutcome.NeedsLogin>(outcome)
            assertTrue(needs.forceReauth)
        }

    @Test
    fun promptSelectAccountForcesReauth() =
        runTest {
            val outcome = runOnce(prompt = "select_account", session = newLoginSession())
            val needs = assertIs<AuthorizationRequestOutcome.NeedsLogin>(outcome)
            assertTrue(needs.forceReauth)
        }

    @Test
    fun maxAgeStaleForcesReauth() =
        runTest {
            // Session authenticated 10 minutes ago, max_age = 60s -> stale.
            val tenMinutesAgo = NOW - kotlin.time.Duration.parse("PT10M")
            val outcome = runOnce(maxAge = 60, session = newLoginSession(authTime = tenMinutesAgo))
            val needs = assertIs<AuthorizationRequestOutcome.NeedsLogin>(outcome)
            assertTrue(needs.forceReauth)
        }

    @Test
    fun maxAgeWithinWindowReusesSession() =
        runTest {
            // Session authenticated 30 seconds ago, max_age = 300 -> within window.
            val thirtySecondsAgo = NOW - kotlin.time.Duration.parse("PT30S")
            val outcome = runOnce(maxAge = 300, session = newLoginSession(authTime = thirtySecondsAgo))
            assertIs<AuthorizationRequestOutcome.WalletCompleted>(outcome)
        }

    @Test
    fun idTokenHintWithMatchingSubAcceptsSession() =
        runTest {
            val session = newLoginSession(sub = "alice")
            val outcome = runOnce(idTokenHint = fauxIdTokenHint(sub = "alice"), session = session)
            assertIs<AuthorizationRequestOutcome.WalletCompleted>(outcome)
        }

    @Test
    fun idTokenHintWithDifferentSubFailsLoginRequired() =
        runTest {
            val session = newLoginSession(sub = "alice")
            val outcome = runOnce(idTokenHint = fauxIdTokenHint(sub = "bob"), session = session)
            val err = assertIs<AuthorizationRequestOutcome.PostRedirectError>(outcome)
            assertEquals("login_required", err.error)
        }

    @Test
    fun idTokenHintWithMismatchedIssuerIsIgnored() =
        runTest {
            // Hint minted by another OP -> decoder returns null -> evaluation cannot fail on it.
            val session = newLoginSession(sub = "alice")
            val outcome =
                runOnce(
                    idTokenHint = fauxIdTokenHint(sub = "bob", iss = "https://other.example.com"),
                    session = session,
                )
            assertIs<AuthorizationRequestOutcome.WalletCompleted>(outcome)
        }

    private var capturedCodeArgs: CreateAuthorizationCodeArgs? = null

    /**
     * Drive the command once with the given evaluation inputs. Returns the raw outcome so
     * each test asserts its expected variant directly.
     */
    private suspend fun runOnce(
        prompt: String? = null,
        maxAge: Int? = null,
        idTokenHint: String? = null,
        session: OidcLoginSession?,
        oauthAvailable: Boolean = true,
    ): AuthorizationRequestOutcome {
        capturedCodeArgs = null
        val parsed =
            AuthorizationRequestData(
                clientId = clientId,
                redirectUri = redirectUri,
                responseType = listOf(ResponseType.CODE),
                state = state,
                prompt =
                    com.sphereon.oauth2.server.authorization.model.Prompt
                        .parseSpaceSeparated(prompt),
                maxAge = maxAge,
                idTokenHint = idTokenHint,
            )
        val verified =
            VerifiedAuthorizationRequest(
                request = parsed,
                clientId = clientId,
                redirectUri = redirectUri,
                grantedScopes = emptyList(),
                pkceRequired = false,
                parRequired = false,
                responseMode = OAuth2ResponseMode.QUERY,
            )
        val authSession =
            AuthorizationSession(
                sessionId = "auth-session-1",
                clientId = clientId,
                redirectUri = redirectUri,
                state = state,
                responseType = "code",
                responseMode = OAuth2ResponseMode.QUERY,
                createdAt = NOW,
                expiresAt = NOW + kotlin.time.Duration.parse("PT15M"),
            )
        val service =
            object : StubAuthorizationServerService() {
                override val commands = TestCommandBundle(parsed, verified, authSession) { args -> capturedCodeArgs = args }
            }
        val store =
            object : OidcLoginSessionStore {
                override suspend fun create(s: OidcLoginSession): IdkResult<OidcLoginSession, OidcLoginSessionStoreError> = Ok(s)

                override suspend fun findById(id: String): IdkResult<OidcLoginSession?, OidcLoginSessionStoreError> = Ok(session)

                override suspend fun touch(
                    id: String,
                    now: Instant,
                    ttl: Int
                ) = Ok(null)

                override suspend fun recordRpParticipation(
                    sessionId: String,
                    clientId: String,
                    sid: String,
                ): IdkResult<OidcLoginSession?, OidcLoginSessionStoreError> = Ok(null)

                override suspend fun revoke(id: String) = Ok(Unit)

                override suspend fun revokeAllForUser(sub: String) = Ok(Unit)
            }
        val provider =
            object : OidcLoginSessionIdProvider {
                override fun currentLoginSessionId(): String? = if (session != null) session.sessionId else null
            }
        val command =
            StandardAuthorizeRequestCommandImpl(
                execution = ctx.execution,
                authorizationServerService = service,
                clientRegistry = StubClientRegistry(),
                serversConfigProvider = configProvider,
                userAuthProvider = StubUserAuthProvider(oauthAvailable = oauthAvailable),
                pendingAuthorizationSessionStore = StubPendingStore(),
                loginSessionStore = store,
                loginSessionIdProvider = provider,
                verifyRequestObjectCommand = StubVerifyRequestObjectCommand(ctx.execution),
                clock = FixedClock(NOW),
            )
        val result =
            command.execute(
                HandleAuthorizeRequestArgs(
                    queryParameters = mapOf("client_id" to clientId, "response_type" to "code"),
                    returnUrl = "https://as.example.com/authorize/callback",
                ),
            )
        assertTrue(result.isOk)
        return result.value
    }

    private fun newLoginSession(
        sessionId: String = "oidc-sid",
        sub: String = "alice",
        authTime: Instant = NOW - kotlin.time.Duration.parse("PT1M"),
        claims: Map<String, JsonElement> = emptyMap(),
    ): OidcLoginSession =
        OidcLoginSession(
            sessionId = sessionId,
            sub = sub,
            authTime = authTime,
            authMethod = AuthenticationMethod.PASSWORD,
            claims = claims,
            createdAt = authTime,
            absoluteExpiresAt = authTime + kotlin.time.Duration.parse("PT8H"),
            idleExpiresAt = authTime + kotlin.time.Duration.parse("PT30M"),
        )

    /** [AuthorizationServerService.Commands] wiring that returns canned values in the order the
     * standard authorize flow consumes them. */
    private class TestCommandBundle(
        private val parsed: AuthorizationRequestData,
        private val verified: VerifiedAuthorizationRequest,
        private val authSession: AuthorizationSession,
        private val onCreateCode: (CreateAuthorizationCodeArgs) -> Unit,
    ) : AuthorizationServerService.Commands {
        override val parseAuthorizationRequest =
            object : ParseAuthorizationRequestCommand {
                override val inputTypeToken = typeToken<ParseAuthorizationRequestArgs>()
                override val outputTypeToken = typeToken<AuthorizationRequestData>()
                override val isEnabled = true

                override suspend fun execute(args: ParseAuthorizationRequestArgs) = Ok(parsed)
            }
        override val verifyAuthorizationRequest =
            object : VerifyAuthorizationRequestCommand {
                override val inputTypeToken = typeToken<AuthorizationRequestData>()
                override val outputTypeToken = typeToken<VerifiedAuthorizationRequest>()
                override val isEnabled = true

                override suspend fun execute(args: AuthorizationRequestData) = Ok(verified)
            }
        override val createAuthorizationSession =
            object : CreateAuthorizationSessionCommand {
                override val inputTypeToken = typeToken<VerifiedAuthorizationRequest>()
                override val outputTypeToken = typeToken<AuthorizationSession>()
                override val isEnabled = true

                override suspend fun execute(args: VerifiedAuthorizationRequest) = Ok(authSession)
            }
        override val createAuthorizationCode =
            object : CreateAuthorizationCodeCommand {
                override val inputTypeToken = typeToken<CreateAuthorizationCodeArgs>()
                override val outputTypeToken = typeToken<StringResult>()
                override val isEnabled = true

                override suspend fun execute(args: CreateAuthorizationCodeArgs): IdkResult<StringResult, IdkError> {
                    onCreateCode(args)
                    return Ok(StringResult("fake-code"))
                }
            }
        override val createAuthorizationResponse =
            object : CreateAuthorizationResponseCommand {
                override val inputTypeToken = typeToken<CreateAuthorizationResponseArgs>()
                override val outputTypeToken = typeToken<AuthorizationResponseData>()
                override val isEnabled = true

                override suspend fun execute(args: CreateAuthorizationResponseArgs) =
                    Ok(
                        AuthorizationResponseData(
                            code = args.code,
                            state = args.state,
                            redirectUri = "${args.redirectUri}?code=${args.code}",
                            responseMode = args.responseMode,
                        ),
                    )
            }
        override val parseTokenRequest get() = throw NotImplementedError()
        override val verifyAuthorizationCodeGrant get() = throw NotImplementedError()
        override val verifyRefreshTokenGrant get() = throw NotImplementedError()
        override val verifyClientCredentialsGrant get() = throw NotImplementedError()
        override val verifyTokenExchangeGrant get() = throw NotImplementedError()
        override val verifyPreAuthorizedCodeGrant get() = throw NotImplementedError()
        override val createAccessToken get() = throw NotImplementedError()
        override val createRefreshToken get() = throw NotImplementedError()
        override val createTokenResponse get() = throw NotImplementedError()
        override val createAuthorizationErrorResponse: CreateAuthorizationErrorResponseCommand
            get() =
                object : CreateAuthorizationErrorResponseCommand {
                    override val inputTypeToken = typeToken<CreateAuthorizationErrorResponseArgs>()
                    override val outputTypeToken = typeToken<AuthorizationErrorResponseData>()
                    override val isEnabled = true

                    override suspend fun execute(args: CreateAuthorizationErrorResponseArgs) =
                        Ok(
                            AuthorizationErrorResponseData(
                                error = args.error,
                                errorDescription = args.errorDescription,
                                state = args.state,
                                redirectUri = args.redirectUri,
                                responseMode = args.responseMode,
                            ),
                        )
                }
        override val parsePushedAuthorizationRequest get() = throw NotImplementedError()
        override val verifyPushedAuthorizationRequest get() = throw NotImplementedError()
        override val createRequestUri get() = throw NotImplementedError()
        override val createPushedAuthorizationResponse get() = throw NotImplementedError()
        override val retrieveAuthorizationRequestByUri get() = throw NotImplementedError()
        override val parseIntrospectionRequest get() = throw NotImplementedError()
        override val introspectToken get() = throw NotImplementedError()
        override val parseRevocationRequest get() = throw NotImplementedError()
        override val revokeToken get() = throw NotImplementedError()
        override val buildServerMetadata get() = throw NotImplementedError()
        override val verifyClientAuthentication get() = throw NotImplementedError()
        override val createAttestationChallenge get() = throw NotImplementedError()
        override val createIdToken get() = throw NotImplementedError()
        override val getUserInfo get() = throw NotImplementedError()
        override val getJwks get() = throw NotImplementedError()
    }

    private class StubClientRegistry : ClientRegistry {
        override suspend fun getClient(clientId: String) =
            Ok(
                ClientRegistration(
                    clientId = clientId,
                    grantTypes = listOf(com.sphereon.oauth2.common.model.GrantType.AUTHORIZATION_CODE),
                    redirectUris = listOf("https://client.example/cb"),
                ),
            )

        override suspend fun registerClient(registration: ClientRegistration) = Ok(registration)

        override suspend fun updateClient(
            clientId: String,
            registration: ClientRegistration
        ) = Ok(registration)

        override suspend fun deleteClient(clientId: String): IdkResult<Unit, AuthorizationServerError> = Ok(Unit)

        override suspend fun listClients(
            limit: Int,
            offset: Int
        ) = Ok(emptyList<ClientRegistration>())

        override suspend fun findClientsByName(name: String) = Ok(emptyList<ClientRegistration>())

        override suspend fun clientExists(clientId: String) = Ok(true)

        override suspend fun verifyClientCredentials(
            clientId: String,
            clientSecret: String
        ) = Ok(false)
    }

    private class StubUserAuthProvider(
        private val oauthAvailable: Boolean = true
    ) : UserAuthenticationProvider {
        override suspend fun getAuthenticatedUser(sessionId: String): IdkResult<AuthenticatedUser?, AuthenticationError> = Ok(null)

        override suspend fun initiateAuthentication(
            sessionId: String,
            returnUrl: String,
            hint: AuthenticationHint?,
            context: AuthenticationContext?
        ) = Ok(FAKE_IDP_REDIRECT)

        override suspend fun authenticateWithCredentials(
            credentials: UserCredentials,
            context: AuthenticationContext?
        ) = Ok(null)

        override suspend fun logout(userId: String) = Ok(Unit)

        override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> = Err(AuthenticationError.UserNotFound())

        override suspend fun isAuthenticationMethodAvailable(method: AuthenticationMethod) = Ok(method == AuthenticationMethod.OAUTH && oauthAvailable)
    }

    private class StubPendingStore : PendingAuthorizationSessionStore {
        override suspend fun create(session: AuthorizationSession): IdkResult<AuthorizationSession, IdkError> = Ok(session)

        override suspend fun findById(sessionId: String): IdkResult<AuthorizationSession?, IdkError> = Ok(null)

        override suspend fun remove(sessionId: String): IdkResult<Unit, IdkError> = Ok(Unit)
    }

    private class FixedClock(
        private val instant: Instant
    ) : Clock {
        override fun now(): Instant = instant
    }

    companion object {
        private val NOW: Instant = Instant.fromEpochSeconds(1_700_000_000)
        private const val FAKE_IDP_REDIRECT = "https://idp.example/login"
    }
}
