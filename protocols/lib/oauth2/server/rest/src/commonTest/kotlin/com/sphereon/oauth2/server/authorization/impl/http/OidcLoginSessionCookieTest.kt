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

package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.oauth2.common.model.OAuth2ResponseMode
import com.sphereon.oauth2.server.authorization.command.AuthorizationErrorResponseData
import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestOutcome
import com.sphereon.oauth2.server.authorization.command.AuthorizationResponseData
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseCommand
import com.sphereon.oauth2.server.authorization.command.authorization.HandleAuthorizeCallbackArgs
import com.sphereon.oauth2.server.authorization.command.authorization.HandleAuthorizeCallbackCommand
import com.sphereon.oauth2.server.authorization.command.authorization.HandleAuthorizeRequestArgs
import com.sphereon.oauth2.server.authorization.command.authorization.HandleAuthorizeRequestCommand
import com.sphereon.oauth2.server.authorization.impl.http.command.TestMutableOidcLoginSessionIdProvider
import com.sphereon.oauth2.server.authorization.impl.http.command.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import com.sphereon.oauth2.server.authorization.impl.http.command.authorization.AuthorizeCallbackHttpEndpointCommandImpl
import com.sphereon.oauth2.server.authorization.impl.http.command.authorization.AuthorizeHttpEndpointCommandImpl
import com.sphereon.oauth2.server.authorization.impl.http.requiredaction.RequiredActionsRedirectHandler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies the Group G cookie-read wiring on the authorization HTTP endpoints, plus the shape of
 * the [loginSessionCookieHeader] helper subsequent groups use to emit `Set-Cookie`.
 *
 * Read path: request with no cookie leaves the provider null; request with `oidc_login_sid=abc`
 * publishes `"abc"` on the provider. Helper: produces an `HttpOnly; SameSite=Lax` cookie that
 * adds `Secure` only when called with `secure = true`.
 */
class OidcLoginSessionCookieTest {
    private fun authorizeCommand(provider: TestMutableOidcLoginSessionIdProvider): AuthorizeHttpEndpointCommandImpl =
        AuthorizeHttpEndpointCommandImpl(
            execution = TestSessionExecution(),
            handleAuthorizeRequestCommands =
                setOf(
                    object : HandleAuthorizeRequestCommand {
                        override val commandId: String get() = "test.authorize-request"
                        override val inputTypeToken get() = typeToken<HandleAuthorizeRequestArgs>()
                        override val outputTypeToken get() = typeToken<AuthorizationRequestOutcome>()
                        override val isEnabled: Boolean = true

                        override suspend fun supports(args: Any): Boolean = args is HandleAuthorizeRequestArgs

                        override suspend fun execute(args: HandleAuthorizeRequestArgs,): IdkResult<AuthorizationRequestOutcome, IdkError> =
                            Ok(AuthorizationRequestOutcome.AuthInitiated(authProviderRedirectUrl = "https://idp.example/login"))
                    },
                ),
            createAuthorizationErrorResponseCommand =
                object : CreateAuthorizationErrorResponseCommand {
                    override val commandId: String get() = CreateAuthorizationErrorResponseCommand.COMMAND_ID
                    override val inputTypeToken get() = typeToken<CreateAuthorizationErrorResponseArgs>()
                    override val outputTypeToken get() = typeToken<AuthorizationErrorResponseData>()
                    override val isEnabled: Boolean = true

                    override suspend fun supports(args: Any): Boolean = args is CreateAuthorizationErrorResponseArgs

                    override suspend fun execute(args: CreateAuthorizationErrorResponseArgs,): IdkResult<AuthorizationErrorResponseData, IdkError> =
                        Ok(
                            AuthorizationErrorResponseData(
                                error = args.error,
                                errorDescription = args.errorDescription,
                                state = args.state,
                                redirectUri = "${args.redirectUri}?error=${args.error}",
                                responseMode = args.responseMode,
                            ),
                        )
                },
            configProvider = TestOAuth2ServersConfigProvider(),
            baseUrlResolver = DefaultOAuth2ServerBaseUrlResolver(),
            loginSessionIdProvider = provider,
        )

    private fun callbackCommand(provider: TestMutableOidcLoginSessionIdProvider): AuthorizeCallbackHttpEndpointCommandImpl =
        AuthorizeCallbackHttpEndpointCommandImpl(
            execution = TestSessionExecution(),
            handleAuthorizeCallbackCommand =
                object : HandleAuthorizeCallbackCommand {
                    override val commandId: String get() = HandleAuthorizeCallbackCommand.COMMAND_ID
                    override val inputTypeToken get() = typeToken<HandleAuthorizeCallbackArgs>()
                    override val outputTypeToken get() = typeToken<AuthorizationResponseData>()
                    override val isEnabled: Boolean = true

                    override suspend fun supports(args: Any): Boolean = args is HandleAuthorizeCallbackArgs

                    override suspend fun execute(args: HandleAuthorizeCallbackArgs,): IdkResult<AuthorizationResponseData, IdkError> =
                        Ok(
                            AuthorizationResponseData(
                                code = "auth-code",
                                state = "s",
                                redirectUri = "https://client.example/cb?code=auth-code&state=s",
                                responseMode = OAuth2ResponseMode.QUERY,
                            ),
                        )
                },
            loginSessionIdProvider = provider,
            configProvider = TestOAuth2ServersConfigProvider(),
            requiredActionsRedirectHandler = NoopRedirectHandler,
            baseUrlResolver = DefaultOAuth2ServerBaseUrlResolver(),
        )

    @Test
    fun authorize_withoutCookie_leavesProviderNull() =
        runTest {
            val provider = TestMutableOidcLoginSessionIdProvider()
            val command = authorizeCommand(provider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/authorize",
                    queryParameters = mapOf("client_id" to "c", "response_type" to "code"),
                    headers = mapOf("Host" to "as.example"),
                )
            assertTrue(command.execute(request).isOk)
            assertNull(provider.currentLoginSessionId())
        }

    @Test
    fun authorize_withCookie_populatesProvider() =
        runTest {
            val provider = TestMutableOidcLoginSessionIdProvider()
            val command = authorizeCommand(provider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/authorize",
                    queryParameters = mapOf("client_id" to "c", "response_type" to "code"),
                    headers = mapOf("Host" to "as.example", "Cookie" to "oidc_login_sid=abc"),
                )
            assertTrue(command.execute(request).isOk)
            assertEquals("abc", provider.currentLoginSessionId())
        }

    @Test
    fun authorize_withCookieAmongOthers_populatesProvider() =
        runTest {
            val provider = TestMutableOidcLoginSessionIdProvider()
            val command = authorizeCommand(provider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/authorize",
                    queryParameters = mapOf("client_id" to "c", "response_type" to "code"),
                    headers =
                        mapOf(
                            "Host" to "as.example",
                            "Cookie" to "tracker=xyz; oidc_login_sid=abc-123_ZZZ; csrf=k",
                        ),
                )
            assertTrue(command.execute(request).isOk)
            assertEquals("abc-123_ZZZ", provider.currentLoginSessionId())
        }

    @Test
    fun authorize_withMalformedCookieHeader_leavesProviderNull() =
        runTest {
            val provider = TestMutableOidcLoginSessionIdProvider()
            val command = authorizeCommand(provider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/authorize",
                    queryParameters = mapOf("client_id" to "c", "response_type" to "code"),
                    headers = mapOf("Host" to "as.example", "Cookie" to "garbage; without; equals"),
                )
            assertTrue(command.execute(request).isOk)
            assertNull(provider.currentLoginSessionId())
        }

    @Test
    fun callback_withCookie_populatesProvider() =
        runTest {
            val provider = TestMutableOidcLoginSessionIdProvider()
            val command = callbackCommand(provider)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/authorize/callback",
                    queryParameters = mapOf("session_id" to "sess-1"),
                    headers = mapOf("Cookie" to "oidc_login_sid=cb-token"),
                )
            assertTrue(command.execute(request).isOk)
            assertEquals("cb-token", provider.currentLoginSessionId())
        }

    @Test
    fun loginSessionCookieHeader_secureFalse_omitsSecureFlag() {
        val header = loginSessionCookieHeader(sessionId = "abc", secure = false)
        assertEquals("oidc_login_sid=abc; Path=/; HttpOnly; SameSite=Lax", header)
    }

    @Test
    fun loginSessionCookieHeader_secureTrue_includesSecureFlag() {
        val header = loginSessionCookieHeader(sessionId = "abc", secure = true)
        assertEquals("oidc_login_sid=abc; Path=/; HttpOnly; SameSite=Lax; Secure", header)
        assertNotNull(header.split("; ").firstOrNull { it == "Secure" })
    }
}

private object NoopRedirectHandler : RequiredActionsRedirectHandler {
    override suspend fun handle(
        error: IdkError,
        sessionId: String,
        baseUrl: String
    ): GenericHttpResponse? = null
}
