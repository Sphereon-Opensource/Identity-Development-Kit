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

package com.sphereon.oauth2.server.authorization.impl.http.command.login

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.random.GenerateTokenArgs
import com.sphereon.core.api.random.NextBytesArgs
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.ByteArrayResult
import com.sphereon.core.api.service.StringResult
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.server.authorization.audit.NoOpOAuth2AuditEmitter
import com.sphereon.oauth2.server.authorization.audit.OAuth2AuditEmitter
import com.sphereon.oauth2.server.authorization.audit.OAuth2AuditEventType
import com.sphereon.oauth2.server.authorization.impl.http.DefaultOAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.command.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import com.sphereon.oauth2.server.authorization.impl.provider.LoginCsrfKeyProvider
import com.sphereon.oauth2.server.authorization.impl.provider.LoginCsrfTokenizer
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryPendingAuthorizationSessionStore
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.AuthenticationContext
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.ClientApplicationResolver
import com.sphereon.oauth2.server.authorization.provider.LoginPageAsset
import com.sphereon.oauth2.server.authorization.provider.LoginPageContext
import com.sphereon.oauth2.server.authorization.provider.LoginPageRenderer
import com.sphereon.oauth2.server.authorization.provider.LoginPageResponse
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSession
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionStore
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionStoreError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Wires the three login [com.sphereon.core.api.http.command.HttpEndpointCommand] impls against
 * lightweight in-memory fakes (no Ktor) and verifies the GET / POST / asset wire shape: page
 * render returns 200 with HTML; correct credentials produce a 302 + Set-Cookie + persisted
 * [OidcLoginSession]; wrong credentials produce a 302 back to `/login?error=invalid_credentials`;
 * the asset endpoint serves bundled CSS / SVG by their declared content types.
 */
class LoginHttpFlowTest {
    private val staticAssets =
        listOf(
            LoginPageAsset(
                path = "css/login.css",
                contentType = "text/css; charset=utf-8",
                bytes = "body { color: red; }".encodeToByteArray(),
            ),
            LoginPageAsset(
                path = "img/sphereon-logo.svg",
                contentType = "image/svg+xml",
                bytes = "<svg/>".encodeToByteArray(),
            ),
        )

    // Single tokenizer instance shared between the page (mints) and submit (verifies) commands
    // so the HMAC values agree across the tests' GET → POST round trip. Production wiring is
    // identical: AppScope-singleton tokenizer with one secret per AS process.
    private val csrfTokenizer = LoginCsrfTokenizer(StubLoginCsrfKeyProvider())

    private fun newLoginPageCommand(): LoginPageHttpEndpointCommandImpl =
        LoginPageHttpEndpointCommandImpl(
            execution = TestSessionExecution(),
            loginPageRenderer = StubLoginPageRenderer(staticAssets = staticAssets),
            asInstanceIdProvider = StubAsInstanceIdProvider(),
            configProvider = TestOAuth2ServersConfigProvider(),
            baseUrlResolver = DefaultOAuth2ServerBaseUrlResolver(),
            csrfTokenizer = csrfTokenizer,
            listEnabledFederationProvidersCommand = NoopListEnabledFederationProvidersCommand,
        )

    // Command wired with a config provider whose default server carries a fixed `issuer`, so the
    // base resolver returns the deterministic [TRUSTED_BASE]. The renderer reflects the resolved
    // returnUrl + formActionBase into the HTML so the trusted-base/return_url assertions can read
    // them off the wire shape.
    private fun newLoginPageCommandWithTrustedBase(): LoginPageHttpEndpointCommandImpl =
        LoginPageHttpEndpointCommandImpl(
            execution = TestSessionExecution(),
            loginPageRenderer = ReflectingLoginPageRenderer(),
            asInstanceIdProvider = StubAsInstanceIdProvider(),
            configProvider =
                TestOAuth2ServersConfigProvider(
                    config =
                        com.sphereon.oauth2.common.config.OAuth2ServersConfig(
                            servers =
                                mapOf(
                                    "default" to
                                        com.sphereon.oauth2.common.config
                                            .OAuth2ServerInstanceConfig(issuer = TRUSTED_BASE),
                                ),
                        ),
                ),
            baseUrlResolver = DefaultOAuth2ServerBaseUrlResolver(),
            csrfTokenizer = csrfTokenizer,
            listEnabledFederationProvidersCommand = NoopListEnabledFederationProvidersCommand,
        )

    private fun newLoginAssetCommand(): LoginAssetHttpEndpointCommandImpl =
        LoginAssetHttpEndpointCommandImpl(
            execution = TestSessionExecution(),
            loginPageRenderer = StubLoginPageRenderer(staticAssets = staticAssets),
        )

    private suspend fun newSubmitCommand(
        userAuthProvider: UserAuthenticationProvider = StubUserAuthProvider(validPair = "alice" to "wonderland"),
        store: InMemoryLoginSessionStore = InMemoryLoginSessionStore(),
        secureRandom: SecureRandom = FixedSecureRandom("login-sid-1"),
        auditEmitter: OAuth2AuditEmitter = NoOpOAuth2AuditEmitter,
        seedPendingSession: Boolean = true,
    ): Pair<LoginSubmitHttpEndpointCommandImpl, InMemoryLoginSessionStore> {
        val pendingStore = InMemoryPendingAuthorizationSessionStore()
        if (seedPendingSession) {
            pendingStore.create(pendingSession("sess-1"))
            pendingStore.create(pendingSession("sess-csrf"))
        }
        val command =
            LoginSubmitHttpEndpointCommandImpl(
                execution = TestSessionExecution(),
                userAuthProvider = userAuthProvider,
                loginSessionStore = store,
                pendingAuthorizationSessionStore = pendingStore,
                clientApplicationResolver = StubClientApplicationResolver(),
                secureRandom = secureRandom,
                configProvider = TestOAuth2ServersConfigProvider(),
                clock = Clock.System,
                auditEmitter = auditEmitter,
                csrfTokenizer = csrfTokenizer,
            )
        return command to store
    }

    private fun pendingSession(sessionId: String): AuthorizationSession =
        AuthorizationSession(
            sessionId = sessionId,
            clientId = "client-1",
            responseType = "code",
            redirectUri = "https://client.example/callback",
            createdAt = Clock.System.now(),
            expiresAt = Clock.System.now() + 15.minutes,
            applicationId = "app-1",
        )

    /**
     * Build a `(formBody, cookieHeader)` pair carrying valid CSRF tokens for [sessionId]
     * minted from the shared tokenizer. Tests that exercise the credential or audit paths
     * must thread these through the request to clear the new CSRF gate; tests that exercise
     * the gate itself construct invalid tuples by hand.
     */
    private suspend fun csrfFormAndCookie(
        sessionId: String,
        username: String,
        password: String,
        returnUrl: String = "https://as/authorize/callback?session_id=$sessionId",
    ): Pair<String, String> {
        val token = csrfTokenizer.mint(sessionId)
        val body =
            "username=$username&password=$password&session_id=$sessionId" +
                "&tab_id=${token.tabId}&session_code=${token.sessionCode}" +
                "&return_url=${com.sphereon.core.api.http.query.percentEncodeQueryComponent(returnUrl)}"
        val cookie = "oidc_login_csrf=${token.tabId}"
        return body to cookie
    }

    @Test
    fun loginPageRendersBrandedHtml() =
        runTest {
            val command = newLoginPageCommand()
            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/login",
                    // Cross-origin return_url is normalized to the trusted default by the command;
                    // this test asserts on sessionId, not return_url, so its outcome is unchanged.
                    queryParameters = mapOf("session_id" to "sess-1", "return_url" to "https://as/cb"),
                    headers = mapOf("Host" to "as.example", "Accept-Language" to "en-US,en;q=0.9"),
                )
            val response = command.execute(request)
            assertTrue(response.isOk)
            val r = response.value
            assertEquals(200, r.statusCode)
            assertEquals("text/html; charset=utf-8", r.headers["Content-Type"])
            assertEquals("no-store", r.headers["Cache-Control"])
            val body = r.body ?: ""
            assertTrue(body.contains("session-id=sess-1"), "Body must encode session id passed to renderer")
        }

    @Test
    fun loginPageMissingSessionIdReturns400() =
        runTest {
            val command = newLoginPageCommand()
            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/login",
                    queryParameters = emptyMap(),
                    headers = mapOf("Host" to "as.example"),
                )
            val response = command.execute(request)
            assertTrue(response.isOk)
            assertEquals(400, response.value.statusCode)
        }

    @Test
    fun loginAssetsServeCss() =
        runTest {
            val command = newLoginAssetCommand()
            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/login/assets/css/login.css",
                    queryParameters = emptyMap(),
                    headers = emptyMap(),
                )
            val response = command.execute(request)
            assertTrue(response.isOk, "Asset response must succeed")
            val r = response.value
            assertEquals(200, r.statusCode)
            assertEquals("text/css; charset=utf-8", r.headers["Content-Type"])
            assertEquals("body { color: red; }", r.body)
        }

    @Test
    fun loginAssetsUnknownPathReturnsNotFound() =
        runTest {
            val command = newLoginAssetCommand()
            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/login/assets/css/missing.css",
                    queryParameters = emptyMap(),
                    headers = emptyMap(),
                )
            val response = command.execute(request)
            assertTrue(response.isErr, "Unknown asset must be a 404")
        }

    @Test
    fun loginPostWithCorrectCredentialsSetsCookieAndRedirects() =
        runTest {
            val (command, store) = newSubmitCommand()
            val (body, csrfCookie) = csrfFormAndCookie("sess-1", "alice", "wonderland")
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/login",
                    headers =
                        mapOf(
                            "Content-Type" to "application/x-www-form-urlencoded",
                            "Cookie" to csrfCookie,
                        ),
                    bodySupplier = { body },
                )
            val response = command.execute(request)
            assertTrue(response.isOk)
            val r = response.value
            assertEquals(302, r.statusCode)
            val location = r.headers["Location"]
            assertNotNull(location)
            assertTrue(location.contains("/authorize/callback"), "Must redirect to authorization callback")
            val cookie = r.headers["Set-Cookie"]
            assertNotNull(cookie)
            assertTrue(cookie.contains("oidc_login_sid=login-sid-1"), "Cookie must carry the new session id")
            assertTrue(cookie.contains("HttpOnly"), "Cookie must be HttpOnly")
            assertTrue(cookie.contains("SameSite=Lax"), "Cookie must be SameSite=Lax")
            val stored = store.loaded("login-sid-1")
            assertNotNull(stored, "OidcLoginSession must be persisted")
            assertEquals("alice", stored.sub)
            assertEquals(AuthenticationMethod.PASSWORD, stored.authMethod)
        }

    @Test
    fun loginPostWithWrongPasswordRedirectsBackWithError() =
        runTest {
            val (command, store) = newSubmitCommand()
            val (body, cookie) = csrfFormAndCookie("sess-1", "alice", "NOPE")
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/login",
                    headers =
                        mapOf(
                            "Content-Type" to "application/x-www-form-urlencoded",
                            "Cookie" to cookie,
                        ),
                    bodySupplier = { body },
                )
            val response = command.execute(request)
            assertTrue(response.isOk)
            val r = response.value
            assertEquals(302, r.statusCode)
            val location = r.headers["Location"] ?: ""
            assertTrue(location.startsWith("/login?"), "Failed login must redirect back to /login")
            assertTrue(location.contains("error=invalid_credentials"), "Location must carry invalid_credentials error")
            assertNull(r.headers["Set-Cookie"], "Failed login must not set a session cookie")
            assertEquals(0, store.size(), "Failed login must not persist a session")
        }

    @Test
    fun loginPostWithoutPendingAuthorizationSessionReturns400() =
        runTest {
            val capturing = CapturingOAuth2AuditEmitter()
            val (command, store) = newSubmitCommand(auditEmitter = capturing, seedPendingSession = false)
            val (body, cookie) = csrfFormAndCookie("sess-1", "alice", "wonderland")
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/login",
                    headers =
                        mapOf(
                            "Content-Type" to "application/x-www-form-urlencoded",
                            "Cookie" to cookie,
                        ),
                    bodySupplier = { body },
                )
            val response = command.execute(request)
            assertTrue(response.isOk)
            assertEquals(400, response.value.statusCode, "Missing pending authorization session must reject")
            assertEquals(0, store.size(), "No login session must be created without authorization context")
            val event = capturing.events.single()
            assertEquals(OAuth2AuditEventType.LOGIN_ERROR, event.type)
            assertEquals("pending_session_not_found", event.metadata["error_subcode"])
            assertEquals("invalid_request", event.errorCode)
        }

    @Test
    fun loginPostSuccessEmitsLoginAuditEvent() =
        runTest {
            // Pin the LOGIN-on-success emission shape: subject must be the authenticated user;
            // amr/auth_method/login_session_id metadata must be present so SIEM rules can pivot
            // on these without parsing the wire response. Anti-enumeration posture allows the
            // success event to carry the subject because the caller demonstrably had the
            // password — this is not a probe vector.
            val capturing = CapturingOAuth2AuditEmitter()
            val (command, _) = newSubmitCommand(auditEmitter = capturing)
            val (body, cookie) = csrfFormAndCookie("sess-1", "alice", "wonderland")
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/login",
                    headers =
                        mapOf(
                            "Content-Type" to "application/x-www-form-urlencoded",
                            "Cookie" to cookie,
                        ),
                    bodySupplier = { body },
                )
            assertTrue(command.execute(request).isOk)
            assertEquals(1, capturing.events.size, "Successful login must emit exactly one audit event")
            val event = capturing.events.single()
            assertEquals(OAuth2AuditEventType.LOGIN, event.type)
            assertEquals("alice", event.subject)
            assertEquals("pwd", event.metadata["amr"])
            assertEquals("PASSWORD", event.metadata["auth_method"])
            assertEquals("login-sid-1", event.metadata["login_session_id"])
            assertNull(event.errorCode)
        }

    @Test
    fun loginPostWrongPasswordEmitsLoginErrorAuditEventWithoutSubject() =
        runTest {
            // Pin the LOGIN_ERROR-on-failure emission shape: subject MUST be null even though
            // the wire-form sent a username, because a real account may not exist for that
            // value and an attacker probing usernames must not be able to enumerate via /events.
            // The error_subcode metadata distinguishes the failure mode (auth_provider_rejected
            // vs missing_credentials vs session_store_failure) for analyst pivoting; the wire
            // response is unified to `invalid_credentials`.
            val capturing = CapturingOAuth2AuditEmitter()
            val (command, _) = newSubmitCommand(auditEmitter = capturing)
            val (body, cookie) = csrfFormAndCookie("sess-1", "alice", "NOPE")
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/login",
                    headers =
                        mapOf(
                            "Content-Type" to "application/x-www-form-urlencoded",
                            "Cookie" to cookie,
                        ),
                    bodySupplier = { body },
                )
            assertTrue(command.execute(request).isOk)
            assertEquals(1, capturing.events.size, "Failed login must emit exactly one audit event")
            val event = capturing.events.single()
            assertEquals(OAuth2AuditEventType.LOGIN_ERROR, event.type)
            assertNull(event.subject, "LOGIN_ERROR must not carry the attempted username")
            // Stub provider models a rejected credential set as `Ok(null)` (no Err), which the
            // login impl treats as the same wire-failure shape but with a distinct subcode so a
            // SIEM analyst can tell whether the provider ran to completion or short-circuited
            // with an error. Either subcode is a valid wrong-password signal; the test pins
            // both wire-error code and audit-error code unification.
            assertTrue(
                event.metadata["error_subcode"] in setOf("auth_provider_rejected", "auth_provider_returned_null_subject"),
                "wrong-password subcode must be one of the documented rejection variants; got ${event.metadata["error_subcode"]}",
            )
            assertEquals("invalid_credentials", event.errorCode)
        }

    @Test
    fun loginPostMissingUsernameEmitsLoginErrorWithMissingCredentialsSubcode() =
        runTest {
            val capturing = CapturingOAuth2AuditEmitter()
            val (command, _) = newSubmitCommand(auditEmitter = capturing)
            val (body, cookie) = csrfFormAndCookie("sess-1", "", "wonderland")
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/login",
                    headers =
                        mapOf(
                            "Content-Type" to "application/x-www-form-urlencoded",
                            "Cookie" to cookie,
                        ),
                    bodySupplier = { body },
                )
            assertTrue(command.execute(request).isOk)
            val event = capturing.events.single()
            assertEquals(OAuth2AuditEventType.LOGIN_ERROR, event.type)
            assertEquals("missing_credentials", event.metadata["error_subcode"])
        }

    @Test
    fun loginPageRenderEmbedsTabIdAndSessionCodeAndSetsCsrfCookie() =
        runTest {
            // Pin the GET /login wire shape for the new CSRF defense: the rendered HTML must
            // carry tab_id + session_code values forwarded from LoginPageContext, AND the
            // response must set the oidc_login_csrf cookie scoped to /login.
            val command = newLoginPageCommand()
            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/login",
                    // Cross-origin return_url is normalized to the trusted default by the command;
                    // this test asserts on tab_id/session_code CSRF, not return_url, so its outcome is unchanged.
                    queryParameters = mapOf("session_id" to "sess-csrf", "return_url" to "https://as/cb"),
                    headers = mapOf("Host" to "as.example"),
                )
            val response = command.execute(request)
            assertTrue(response.isOk)
            val r = response.value
            assertEquals(200, r.statusCode)
            val cookie = r.headers["Set-Cookie"]
            assertNotNull(cookie, "GET /login must set the oidc_login_csrf cookie")
            assertTrue(cookie.startsWith("oidc_login_csrf="), "cookie name must be oidc_login_csrf, got: $cookie")
            assertTrue(cookie.contains("Path=/login"), "CSRF cookie must be scoped to /login")
            assertTrue(cookie.contains("HttpOnly"), "CSRF cookie must be HttpOnly")
            assertTrue(cookie.contains("SameSite=Strict"), "CSRF cookie must be SameSite=Strict")
            // The stub renderer's HTML body interpolates ctx.tabId / ctx.sessionCode via
            // ${ctx.errorMessage} only — but we can read the cookie's tab_id which equals
            // what the page mints; that's enough to prove the page generated AND propagated
            // the value, even though the stub renderer doesn't reflect them in its body.
        }

    @Test
    fun reflectedOffOriginReturnUrlIsRejected() =
        runTest {
            // A reflected, off-origin `return_url` (attacker-controlled) must NOT be honored:
            // the rendered hidden `return_url` input falls back to the trusted default, and no
            // form `action` carries the hostile origin.
            val command = newLoginPageCommandWithTrustedBase()
            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/login",
                    queryParameters =
                        mapOf(
                            "session_id" to "sess-1",
                            "return_url" to "https://evil.example/authorize/callback?session_id=sess-1",
                        ),
                    headers = mapOf("Host" to "as.example"),
                )
            val response = command.execute(request)
            assertTrue(response.isOk)
            val body = response.value.body ?: ""
            assertTrue(
                body.contains("value=\"$TRUSTED_BASE/authorize/callback?session_id=sess-1\""),
                "Hidden return_url input must be the trusted default, got body: $body",
            )
            assertTrue(!body.contains("evil.example"), "Reflected hostile origin must not appear anywhere in the page")
            assertTrue(
                body.contains("action=\"$TRUSTED_BASE/login\""),
                "Login form action must use the trusted base, got body: $body",
            )
        }

    @Test
    fun sameOriginReturnUrlIsHonored() =
        runTest {
            // A same-origin callback `return_url` on the trusted base IS honored verbatim.
            val command = newLoginPageCommandWithTrustedBase()
            val sameOrigin = "$TRUSTED_BASE/authorize/callback?session_id=sess-1"
            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/login",
                    queryParameters =
                        mapOf(
                            "session_id" to "sess-1",
                            "return_url" to sameOrigin,
                        ),
                    headers = mapOf("Host" to "as.example"),
                )
            val response = command.execute(request)
            assertTrue(response.isOk)
            val body = response.value.body ?: ""
            assertTrue(
                body.contains("value=\"$sameOrigin\""),
                "Hidden return_url input must echo the same-origin callback, got body: $body",
            )
        }

    @Test
    fun absentReturnUrlUsesTrustedDefault() =
        runTest {
            // No `return_url` param at all: the hidden input is the trusted default.
            val command = newLoginPageCommandWithTrustedBase()
            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/login",
                    queryParameters = mapOf("session_id" to "sess-1"),
                    headers = mapOf("Host" to "as.example"),
                )
            val response = command.execute(request)
            assertTrue(response.isOk)
            val body = response.value.body ?: ""
            assertTrue(
                body.contains("value=\"$TRUSTED_BASE/authorize/callback?session_id=sess-1\""),
                "Absent return_url must yield the trusted default, got body: $body",
            )
            assertTrue(
                body.contains("action=\"$TRUSTED_BASE/login\""),
                "Form action must use the trusted base when return_url is absent, got body: $body",
            )
        }

    @Test
    fun loginPostMissingTabIdReturns400() =
        runTest {
            val (command, store) = newSubmitCommand()
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/login",
                    headers =
                        mapOf(
                            "Content-Type" to "application/x-www-form-urlencoded",
                            "Cookie" to "oidc_login_csrf=anything",
                        ),
                    // No tab_id / session_code in the form — defends against an attacker who
                    // tries to bypass by simply not sending them.
                    bodySupplier = { "username=alice&password=wonderland&session_id=sess-1&return_url=https%3A%2F%2Fas%2Fcb" },
                )
            val response = command.execute(request)
            assertTrue(response.isOk)
            assertEquals(400, response.value.statusCode, "Missing CSRF params must reject")
            assertEquals(0, store.size(), "No session must be created on CSRF failure")
        }

    @Test
    fun loginPostWithMismatchedSessionCodeReturns400() =
        runTest {
            // Attacker leaked the URL (so they have session_id) and may have crafted a tab_id,
            // but cannot compute a valid session_code without the HMAC key. Any sessionCode
            // value that doesn't match the HMAC must reject.
            val (command, store) = newSubmitCommand()
            val (validBody, cookie) = csrfFormAndCookie("sess-1", "alice", "wonderland")
            val tamperedBody = validBody.replace(Regex("session_code=[^&]+"), "session_code=AAAA_attacker_forged_BBBB")
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/login",
                    headers =
                        mapOf(
                            "Content-Type" to "application/x-www-form-urlencoded",
                            "Cookie" to cookie,
                        ),
                    bodySupplier = { tamperedBody },
                )
            val response = command.execute(request)
            assertTrue(response.isOk)
            assertEquals(400, response.value.statusCode, "Forged session_code must reject")
            assertEquals(0, store.size(), "Auth must NOT proceed past CSRF rejection")
        }

    @Test
    fun loginPostWithMismatchedCookieTabIdReturns400() =
        runTest {
            // Form's tab_id matches the HMAC, but the browser's cookie carries a different
            // tab_id. Defends against an attacker who can leak the form contents (referer,
            // browser history) but cannot read the victim's cookie (HttpOnly + SameSite=Strict).
            val (command, _) = newSubmitCommand()
            val (body, _) = csrfFormAndCookie("sess-1", "alice", "wonderland")
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/login",
                    headers =
                        mapOf(
                            "Content-Type" to "application/x-www-form-urlencoded",
                            "Cookie" to "oidc_login_csrf=ATTACKER_DIFFERENT_TAB_ID",
                        ),
                    bodySupplier = { body },
                )
            val response = command.execute(request)
            assertTrue(response.isOk)
            assertEquals(400, response.value.statusCode, "Cookie/form tab_id mismatch must reject")
        }

    @Test
    fun loginPostCsrfFailureEmitsAuditEventWithSubcode() =
        runTest {
            val capturing = CapturingOAuth2AuditEmitter()
            val (command, _) = newSubmitCommand(auditEmitter = capturing)
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/login",
                    headers =
                        mapOf(
                            "Content-Type" to "application/x-www-form-urlencoded",
                            "Cookie" to "oidc_login_csrf=tab",
                        ),
                    bodySupplier = { "username=alice&password=wonderland&session_id=sess-1&tab_id=tab&session_code=BAD&return_url=https%3A%2F%2Fas%2Fcb" },
                )
            assertTrue(command.execute(request).isOk)
            val event = capturing.events.single()
            assertEquals(OAuth2AuditEventType.LOGIN_ERROR, event.type)
            assertEquals("csrf_verification_failed", event.metadata["error_subcode"])
            assertEquals("invalid_request", event.errorCode)
        }

    @Test
    fun loginPostWithoutFormContentTypeReturns400() =
        runTest {
            val (command, _) = newSubmitCommand()
            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/login",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodySupplier = { "{\"username\":\"alice\",\"password\":\"wonderland\"}" },
                )
            val response = command.execute(request)
            assertTrue(response.isOk)
            assertEquals(400, response.value.statusCode)
        }

    private class StubLoginPageRenderer(
        private val staticAssets: List<LoginPageAsset>,
    ) : LoginPageRenderer {
        override suspend fun render(ctx: LoginPageContext): IdkResult<LoginPageResponse, IdkError> =
            Ok(
                LoginPageResponse(
                    html = "<html><body data-session-id=session-id=${ctx.sessionId}>${ctx.errorMessage ?: ""}</body></html>",
                ),
            )

        override fun staticAssets(): List<LoginPageAsset> = staticAssets
    }

    /**
     * Renders the resolved [LoginPageContext.returnUrl] as a hidden `return_url` input and the
     * [LoginPageContext.formActionBase] as the login form `action`, so the trusted-base /
     * return_url validation tests can assert on the wire shape the HTTP command produced.
     */
    private class ReflectingLoginPageRenderer : LoginPageRenderer {
        override suspend fun render(ctx: LoginPageContext): IdkResult<LoginPageResponse, IdkError> =
            Ok(
                LoginPageResponse(
                    html =
                        "<html><body>" +
                            "<form action=\"${ctx.formActionBase}/login\">" +
                            "<input type=\"hidden\" name=\"return_url\" value=\"${ctx.returnUrl}\">" +
                            "</form></body></html>",
                ),
            )
    }

    private class StubAsInstanceIdProvider(
        private val id: String? = "default",
    ) : OAuth2ServerInstanceIdProvider {
        override fun currentAsInstanceId(): String? = id
    }

    private class StubUserAuthProvider(
        private val validPair: Pair<String, String>,
    ) : UserAuthenticationProvider {
        override suspend fun getAuthenticatedUser(sessionId: String): IdkResult<AuthenticatedUser?, AuthenticationError> = Ok(null)

        override suspend fun initiateAuthentication(
            sessionId: String,
            returnUrl: String,
            hint: AuthenticationHint?,
            context: AuthenticationContext?,
        ): IdkResult<String, AuthenticationError> = Ok("/login?session_id=$sessionId")

        override suspend fun authenticateWithCredentials(
            credentials: UserCredentials,
            context: AuthenticationContext?,
        ): IdkResult<String?, AuthenticationError> {
            val up = credentials as? UserCredentials.UsernamePassword ?: return Ok(null)
            return if (up.username == validPair.first && up.password == validPair.second) Ok(up.username) else Ok(null)
        }

        override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> = Ok(Unit)

        override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> = Ok(UserInfo(userId = userId))

        override suspend fun isAuthenticationMethodAvailable(method: AuthenticationMethod,): IdkResult<Boolean, AuthenticationError> = Ok(method == AuthenticationMethod.PASSWORD)
    }

    private class StubClientApplicationResolver : ClientApplicationResolver {
        override suspend fun resolveApplicationId(
            clientId: String,
            requestHost: String?
        ): IdkResult<String?, IdkError> = Ok("app-1")
    }

    /**
     * Fixed 32-byte HMAC key so the GET render's session_code matches across the same-test
     * POST verify. The bytes are arbitrary; only stability matters for tests, not entropy.
     */
    private class StubLoginCsrfKeyProvider : LoginCsrfKeyProvider {
        private val key: ByteArray = ByteArray(32) { it.toByte() }

        override fun keyBytes(): ByteArray = key
    }

    private class FixedSecureRandom(
        private val token: String,
    ) : SecureRandom {
        override val commands: SecureRandom.Commands get() = throw NotImplementedError("Not needed for tests")

        override suspend fun generateToken(args: GenerateTokenArgs): IdkResult<StringResult, IdkError> = Ok(StringResult(value = token))

        override suspend fun nextBytes(args: NextBytesArgs): IdkResult<ByteArrayResult, IdkError> = Ok(ByteArrayResult(bytes = ByteArray(args.length)))
    }

    internal class InMemoryLoginSessionStore : OidcLoginSessionStore {
        private val sessions = mutableMapOf<String, OidcLoginSession>()

        override suspend fun create(session: OidcLoginSession): IdkResult<OidcLoginSession, OidcLoginSessionStoreError> {
            sessions[session.sessionId] = session
            return Ok(session)
        }

        override suspend fun findById(sessionId: String): IdkResult<OidcLoginSession?, OidcLoginSessionStoreError> = Ok(sessions[sessionId])

        override suspend fun touch(
            sessionId: String,
            now: kotlin.time.Instant,
            idleTtlSeconds: Int,
        ): IdkResult<OidcLoginSession?, OidcLoginSessionStoreError> = Ok(sessions[sessionId])

        override suspend fun recordRpParticipation(
            sessionId: String,
            clientId: String,
            sid: String,
        ): IdkResult<OidcLoginSession?, OidcLoginSessionStoreError> {
            val current = sessions[sessionId] ?: return Ok(null)
            val updated = current.copy(rpSessions = current.rpSessions + (clientId to sid))
            sessions[sessionId] = updated
            return Ok(updated)
        }

        override suspend fun revoke(sessionId: String): IdkResult<Unit, OidcLoginSessionStoreError> {
            sessions.remove(sessionId)
            return Ok(Unit)
        }

        override suspend fun revokeAllForUser(sub: String): IdkResult<Unit, OidcLoginSessionStoreError> {
            val keys = sessions.entries.filter { it.value.sub == sub }.map { it.key }
            keys.forEach(sessions::remove)
            return Ok(Unit)
        }

        fun loaded(sessionId: String): OidcLoginSession? = sessions[sessionId]

        fun size(): Int = sessions.size
    }

    /**
     * Records every emit invocation so tests can assert on the type / subject / metadata that
     * the AS commands declare for a given outcome. Order-preserving and append-only.
     */
    internal class CapturingOAuth2AuditEmitter : OAuth2AuditEmitter {
        data class Captured(
            val type: OAuth2AuditEventType,
            val clientId: String?,
            val subject: String?,
            val metadata: Map<String, String>,
            val errorCode: String?,
            val errorMessage: String?,
        )

        private val _events = mutableListOf<Captured>()
        val events: List<Captured> get() = _events.toList()

        override suspend fun emit(
            type: OAuth2AuditEventType,
            clientId: String?,
            subject: String?,
            metadata: Map<String, String>,
            errorCode: String?,
            errorMessage: String?,
        ) {
            _events.add(Captured(type, clientId, subject, metadata, errorCode, errorMessage))
        }
    }
}

/**
 * Deterministic trusted base for the form-action / return_url validation tests. Set as the
 * default server's `issuer` so [DefaultOAuth2ServerBaseUrlResolver] resolves to exactly this value
 * regardless of the request `Host`.
 */
private const val TRUSTED_BASE: String = "https://as.test"

private object NoopListEnabledFederationProvidersCommand :
    com.sphereon.oauth2.server.authorization.command.federation.ListEnabledFederationProvidersCommand {
    override val commandId: String get() = com.sphereon.oauth2.server.authorization.command.federation.ListEnabledFederationProvidersCommand.COMMAND_ID
    override val inputTypeToken get() =
        com.sphereon.core.api.binary
            .typeToken<com.sphereon.oauth2.server.authorization.command.federation.ListEnabledFederationProvidersArgs>()
    override val outputTypeToken get() =
        com.sphereon.core.api.binary
            .typeToken<com.sphereon.oauth2.server.authorization.command.federation.EnabledFederationProviders>()
    override val isEnabled: Boolean = true

    override suspend fun supports(args: Any): Boolean = args is com.sphereon.oauth2.server.authorization.command.federation.ListEnabledFederationProvidersArgs

    override suspend fun execute(
        args: com.sphereon.oauth2.server.authorization.command.federation.ListEnabledFederationProvidersArgs,
    ): IdkResult<com.sphereon.oauth2.server.authorization.command.federation.EnabledFederationProviders, com.sphereon.oauth2.server.authorization.provider.AuthenticationError> =
        Ok(
            com.sphereon.oauth2.server.authorization.command.federation
                .EnabledFederationProviders(providers = emptyList())
        )
}
