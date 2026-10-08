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

package com.sphereon.oauth2.server.authorization.impl.command.logout

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.command.logout.CreateLogoutTokenArgs
import com.sphereon.oauth2.server.authorization.command.logout.CreateLogoutTokenCommand
import com.sphereon.oauth2.server.authorization.command.logout.HandleEndSessionRequestArgs
import com.sphereon.oauth2.server.authorization.command.logout.LogoutOutcome
import com.sphereon.oauth2.server.authorization.command.logout.SendBackChannelLogoutArgs
import com.sphereon.oauth2.server.authorization.command.logout.SendBackChannelLogoutCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOidcLoginSessionStore
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.LogoutPageContext
import com.sphereon.oauth2.server.authorization.provider.LogoutPageRenderer
import com.sphereon.oauth2.server.authorization.provider.LogoutPageResponse
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Behavioural coverage for [HandleEndSessionRequestCommandImpl] driving the OIDC
 * RP-Initiated Logout 1.0 §2 flow:
 *
 *  - Registered `post_logout_redirect_uri` produces [LogoutOutcome.Redirect] with
 *    `state` echoed; cookie clearing is the HTTP shell's job and is not asserted here.
 *  - Unregistered `post_logout_redirect_uri` falls through to [LogoutOutcome.RenderPage]
 *    instead of redirecting to the unregistered URL.
 *  - When the login session has participating RPs with `frontchannel_logout_uri`, the
 *    rendered page context carries the iframe URL with `iss` and `sid` query parameters.
 *  - When participating RPs register `backchannel_logout_uri`, the orchestrator drives
 *    the create + send commands; failure of either is logged and does not abort the
 *    flow.
 */
class HandleEndSessionRequestCommandImplTest {
    private val ctx = OAuth2ServerTestContext("end-session-test", this)
    private val baseUrl = "https://as.example.com"

    @Test
    fun registeredPostLogoutRedirectUriYieldsRedirectOutcome() =
        runTest {
            val store = InMemoryOidcLoginSessionStore(Clock.System)
            val now = Clock.System.now()
            val session =
                OidcLoginSession(
                    sessionId = "sess-1",
                    sub = "alice",
                    authTime = now,
                    authMethod = AuthenticationMethod.PASSWORD,
                    createdAt = now,
                    absoluteExpiresAt = now + 30.minutes,
                    idleExpiresAt = now + 30.minutes,
                )
            store.create(session)

            val client =
                ClientRegistration(
                    clientId = "client-x",
                    grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
                    postLogoutRedirectUris = listOf("https://rp.example/post-logout"),
                )
            val command =
                HandleEndSessionRequestCommandImpl(
                    execution = ctx.execution,
                    loginSessionStore = store,
                    clientRegistry = SingleClientRegistry(client),
                    logoutPageRenderer = NoOpLogoutPageRenderer(),
                    createLogoutTokenCommand = StubCreateLogoutTokenCommand(),
                    sendBackChannelLogoutCommand = StubSendBackChannelLogoutCommand(),
                )
            val result =
                command.execute(
                    HandleEndSessionRequestArgs(
                        clientId = "client-x",
                        postLogoutRedirectUri = "https://rp.example/post-logout",
                        state = "abc",
                        currentLoginSessionId = "sess-1",
                        baseUrl = baseUrl,
                    ),
                )
            assertTrue(result.isOk)
            val outcome = result.value
            assertTrue(outcome is LogoutOutcome.Redirect, "registered redirect must yield Redirect outcome")
            assertEquals("https://rp.example/post-logout?state=abc", outcome.location)
            // Session must be revoked.
            val after = store.findById("sess-1")
            assertTrue(after.isOk)
            assertEquals(null, after.value)
        }

    @Test
    fun unregisteredPostLogoutRedirectFallsThroughToRenderedPage() =
        runTest {
            val store = InMemoryOidcLoginSessionStore(Clock.System)
            val now = Clock.System.now()
            store.create(
                OidcLoginSession(
                    sessionId = "sess-2",
                    sub = "bob",
                    authTime = now,
                    authMethod = AuthenticationMethod.PASSWORD,
                    createdAt = now,
                    absoluteExpiresAt = now + 30.minutes,
                    idleExpiresAt = now + 30.minutes,
                ),
            )
            val client =
                ClientRegistration(
                    clientId = "client-x",
                    grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
                    postLogoutRedirectUris = listOf("https://rp.example/post-logout"),
                )
            val command =
                HandleEndSessionRequestCommandImpl(
                    execution = ctx.execution,
                    loginSessionStore = store,
                    clientRegistry = SingleClientRegistry(client),
                    logoutPageRenderer = NoOpLogoutPageRenderer(),
                    createLogoutTokenCommand = StubCreateLogoutTokenCommand(),
                    sendBackChannelLogoutCommand = StubSendBackChannelLogoutCommand(),
                )
            val result =
                command.execute(
                    HandleEndSessionRequestArgs(
                        clientId = "client-x",
                        postLogoutRedirectUri = "https://evil.example/cb",
                        currentLoginSessionId = "sess-2",
                        baseUrl = baseUrl,
                    ),
                )
            assertTrue(result.isOk)
            assertTrue(result.value is LogoutOutcome.RenderPage)
        }

    @Test
    fun frontChannelIframesIncludeIssAndSidWhenSessionRequired() =
        runTest {
            val store = InMemoryOidcLoginSessionStore(Clock.System)
            val now = Clock.System.now()
            store.create(
                OidcLoginSession(
                    sessionId = "sess-3",
                    sub = "alice",
                    authTime = now,
                    authMethod = AuthenticationMethod.PASSWORD,
                    createdAt = now,
                    absoluteExpiresAt = now + 30.minutes,
                    idleExpiresAt = now + 30.minutes,
                    rpSessions = mapOf("rp-1" to "session-3-sid"),
                ),
            )
            val client =
                ClientRegistration(
                    clientId = "rp-1",
                    grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
                    frontchannelLogoutUri = "https://rp1.example/fc-logout",
                    frontchannelLogoutSessionRequired = true,
                )
            val capturedCtx = arrayOfNulls<LogoutPageContext>(1)
            val command =
                HandleEndSessionRequestCommandImpl(
                    execution = ctx.execution,
                    loginSessionStore = store,
                    clientRegistry = SingleClientRegistry(client),
                    logoutPageRenderer = CapturingLogoutPageRenderer(capturedCtx),
                    createLogoutTokenCommand = StubCreateLogoutTokenCommand(),
                    sendBackChannelLogoutCommand = StubSendBackChannelLogoutCommand(),
                )
            val result =
                command.execute(
                    HandleEndSessionRequestArgs(
                        clientId = "rp-1",
                        currentLoginSessionId = "sess-3",
                        baseUrl = baseUrl,
                    ),
                )
            assertTrue(result.isOk)
            val rendered = capturedCtx[0]
            assertNotNull(rendered)
            assertEquals(1, rendered.iframes.size)
            val iframe = rendered.iframes.single()
            assertEquals("rp-1", iframe.clientId)
            assertTrue(iframe.iframeUrl.contains("https://rp1.example/fc-logout"))
            assertTrue(iframe.iframeUrl.contains("iss="))
            assertTrue(iframe.iframeUrl.contains("sid="))
        }

    @Test
    fun backChannelDeliveryIsTriggeredForParticipatingRpsWithBackchannelUri() =
        runTest {
            val store = InMemoryOidcLoginSessionStore(Clock.System)
            val now = Clock.System.now()
            store.create(
                OidcLoginSession(
                    sessionId = "sess-4",
                    sub = "alice",
                    authTime = now,
                    authMethod = AuthenticationMethod.PASSWORD,
                    createdAt = now,
                    absoluteExpiresAt = now + 30.minutes,
                    idleExpiresAt = now + 30.minutes,
                    rpSessions = mapOf("rp-2" to "session-4-sid"),
                ),
            )
            val client =
                ClientRegistration(
                    clientId = "rp-2",
                    grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
                    backchannelLogoutUri = "https://rp2.example/bc-logout",
                    backchannelLogoutSessionRequired = true,
                )
            val capturedCreate = mutableListOf<CreateLogoutTokenArgs>()
            val capturedSend = mutableListOf<SendBackChannelLogoutArgs>()
            val command =
                HandleEndSessionRequestCommandImpl(
                    execution = ctx.execution,
                    loginSessionStore = store,
                    clientRegistry = SingleClientRegistry(client),
                    logoutPageRenderer = NoOpLogoutPageRenderer(),
                    createLogoutTokenCommand = RecordingCreateLogoutTokenCommand(capturedCreate),
                    sendBackChannelLogoutCommand = RecordingSendBackChannelLogoutCommand(capturedSend),
                )
            val result =
                command.execute(
                    HandleEndSessionRequestArgs(
                        clientId = "rp-2",
                        currentLoginSessionId = "sess-4",
                        baseUrl = baseUrl,
                    ),
                )
            assertTrue(result.isOk)
            assertEquals(1, capturedCreate.size, "logout_token must be minted once for the participating RP")
            assertEquals("rp-2", capturedCreate.first().clientId)
            assertEquals("alice", capturedCreate.first().sub)
            assertEquals("session-4-sid", capturedCreate.first().sid)
            assertEquals(baseUrl, capturedCreate.first().issuer)
            assertEquals(1, capturedSend.size, "POST must be sent once to the participating RP")
            assertEquals("https://rp2.example/bc-logout", capturedSend.first().backchannelLogoutUri)
        }

    @Test
    fun idTokenHintWithSidIsHonouredWhenCookieAbsent() =
        runTest {
            val store = InMemoryOidcLoginSessionStore(Clock.System)
            val now = Clock.System.now()
            store.create(
                OidcLoginSession(
                    sessionId = "sess-5",
                    sub = "alice",
                    authTime = now,
                    authMethod = AuthenticationMethod.PASSWORD,
                    createdAt = now,
                    absoluteExpiresAt = now + 30.minutes,
                    idleExpiresAt = now + 30.minutes,
                ),
            )
            val client =
                ClientRegistration(
                    clientId = "client-x",
                    grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
                    postLogoutRedirectUris = listOf("https://rp.example/done"),
                )
            val command =
                HandleEndSessionRequestCommandImpl(
                    execution = ctx.execution,
                    loginSessionStore = store,
                    clientRegistry = SingleClientRegistry(client),
                    logoutPageRenderer = NoOpLogoutPageRenderer(),
                    createLogoutTokenCommand = StubCreateLogoutTokenCommand(),
                    sendBackChannelLogoutCommand = StubSendBackChannelLogoutCommand(),
                )

            val hint =
                stubIdTokenHint(
                    iss = baseUrl,
                    sub = "alice",
                    aud = "client-x",
                    sid = "sess-5",
                )
            val result =
                command.execute(
                    HandleEndSessionRequestArgs(
                        idTokenHint = hint,
                        postLogoutRedirectUri = "https://rp.example/done",
                        currentLoginSessionId = null,
                        baseUrl = baseUrl,
                    ),
                )
            assertTrue(result.isOk)
            assertTrue(result.value is LogoutOutcome.Redirect)
            // Session must have been revoked despite no cookie supplied.
            assertEquals(null, store.findById("sess-5").value)
        }

    private fun stubIdTokenHint(
        iss: String,
        sub: String,
        aud: String,
        sid: String,
    ): String {
        val header = "{\"alg\":\"none\"}".encodeToByteArray().encodeToBase64Url()
        val payload =
            "{\"iss\":\"$iss\",\"sub\":\"$sub\",\"aud\":\"$aud\",\"sid\":\"$sid\"}".encodeToByteArray().encodeToBase64Url()
        val sig = "sig".encodeToByteArray().encodeToBase64Url()
        return "$header.$payload.$sig"
    }

    private class SingleClientRegistry(
        private val client: ClientRegistration,
    ) : ClientRegistry {
        override suspend fun getClient(clientId: String) = Ok(if (clientId == client.clientId) client else null)

        override suspend fun registerClient(registration: ClientRegistration) = Ok(registration)

        override suspend fun updateClient(
            clientId: String,
            registration: ClientRegistration
        ) = Ok(registration)

        override suspend fun deleteClient(clientId: String) = Ok(Unit)

        override suspend fun listClients(
            limit: Int,
            offset: Int
        ) = Ok(listOf(client))

        override suspend fun findClientsByName(name: String) = Ok(listOf(client))

        override suspend fun clientExists(clientId: String) = Ok(clientId == client.clientId)

        override suspend fun verifyClientCredentials(
            clientId: String,
            clientSecret: String
        ) = Ok(false)
    }

    private class NoOpLogoutPageRenderer : LogoutPageRenderer {
        override suspend fun render(ctx: LogoutPageContext) = Ok(LogoutPageResponse(html = "<html></html>"))
    }

    private class CapturingLogoutPageRenderer(
        private val sink: Array<LogoutPageContext?>,
    ) : LogoutPageRenderer {
        override suspend fun render(ctx: LogoutPageContext): IdkResult<LogoutPageResponse, IdkError> {
            sink[0] = ctx
            return Ok(LogoutPageResponse(html = "<html></html>"))
        }
    }

    private class StubCreateLogoutTokenCommand : CreateLogoutTokenCommand {
        override val inputTypeToken = typeToken<CreateLogoutTokenArgs>()
        override val outputTypeToken = typeToken<StringResult>()
        override val isEnabled = true

        override suspend fun execute(args: CreateLogoutTokenArgs): IdkResult<StringResult, IdkError> = Ok(StringResult("stub.jwt.token"))
    }

    private class RecordingCreateLogoutTokenCommand(
        private val sink: MutableList<CreateLogoutTokenArgs>,
    ) : CreateLogoutTokenCommand {
        override val inputTypeToken = typeToken<CreateLogoutTokenArgs>()
        override val outputTypeToken = typeToken<StringResult>()
        override val isEnabled = true

        override suspend fun execute(args: CreateLogoutTokenArgs): IdkResult<StringResult, IdkError> {
            sink.add(args)
            return Ok(StringResult("recorded.jwt.token"))
        }
    }

    private class StubSendBackChannelLogoutCommand : SendBackChannelLogoutCommand {
        override val inputTypeToken = typeToken<SendBackChannelLogoutArgs>()
        override val outputTypeToken = typeToken<Unit>()
        override val isEnabled = true

        override suspend fun execute(args: SendBackChannelLogoutArgs): IdkResult<Unit, IdkError> = Ok(Unit)
    }

    private class RecordingSendBackChannelLogoutCommand(
        private val sink: MutableList<SendBackChannelLogoutArgs>,
    ) : SendBackChannelLogoutCommand {
        override val inputTypeToken = typeToken<SendBackChannelLogoutArgs>()
        override val outputTypeToken = typeToken<Unit>()
        override val isEnabled = true

        override suspend fun execute(args: SendBackChannelLogoutArgs): IdkResult<Unit, IdkError> {
            sink.add(args)
            return Ok(Unit)
        }
    }

    @Suppress("unused")
    private val storageError: AuthorizationServerError.StorageError =
        AuthorizationServerError.StorageError(operation = "test", details = "ignored")
}
