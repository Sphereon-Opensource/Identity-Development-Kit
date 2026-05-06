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

package com.sphereon.oauth2.oidf.op

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Three sub-scenarios driving the OIDC `prompt` / `max_age` / `id_token_hint` semantics through
 * the browser-facing /authorize endpoint. Mirrors the OIDF Basic-OP `OIDCCPromptNone`,
 * `OIDCCMaxAge1`, and `OIDCCIdTokenHint` checkpoints:
 *
 *  1. `prompt=none` against an unauthenticated session must redirect with `error=login_required`
 *     instead of bouncing through `/login`.
 *  2. `max_age=1` after an existing successful login that's older than one second must trigger
 *     re-authentication (a fresh login redirect) instead of a silent code response.
 *  3. `id_token_hint=<id_token from previous flow>` whose `sub` does not match the user that
 *     would be re-authenticated must redirect with `error=login_required` so the client cannot
 *     coast on a stale identity.
 */
class OidfOpPromptMaxAgeIdTokenHintTest {
    private lateinit var fixture: OidfOpServerFixture
    private lateinit var client: HttpClient
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        fixture = OidfOpServerFixture()
        client = HttpClient(CIO) { followRedirects = false }
    }

    @AfterTest
    fun tearDown() {
        client.close()
        fixture.stop()
    }

    @Test
    fun promptNoneAgainstUnauthenticatedSessionReturnsLoginRequired() =
        runTest {
            // The conformance client `oidf-op-basic` is configured `require-pkce=true`, so the
            // harness must thread PKCE parameters through every authorize request. The OIDF
            // `OIDCCPromptNone` checkpoint drives the same shape, `prompt=none` is layered on
            // top of an otherwise-valid request, not a way to bypass spec parameter validation.
            val pkceChallenge =
                java.util.Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                        java.security.MessageDigest
                            .getInstance("SHA-256")
                            .digest("oidf-op-prompt-none-verifier-2026".encodeToByteArray()),
                    )
            val response =
                client.get(
                    "${fixture.baseUrl}/authorize?response_type=code" +
                        "&client_id=oidf-op-basic" +
                        "&redirect_uri=${urlEncode("http://localhost:8080/test-callback")}" +
                        "&scope=openid&state=p1" +
                        "&nonce=oidf-prompt-none-nonce" +
                        "&code_challenge=$pkceChallenge" +
                        "&code_challenge_method=S256" +
                        "&prompt=none",
                )
            assertEquals(HttpStatusCode.Found, response.status)
            val location = response.headers["Location"] ?: error("missing Location")
            assertTrue(
                location.contains("error=login_required"),
                "prompt=none without an active session must redirect with error=login_required, got $location",
            )
        }

    @Test
    fun maxAgeForcesReauthenticationWhenSessionIsStale() =
        runTest {
            // Establish an OIDC login session for alice and capture the cookie.
            val firstLogin = drivePkceLogin(state = "max-age-first", nonce = "max-age-first-nonce")
            val cookie = firstLogin.loginCookie

            // Advance the AppScope-bound [TestClock] by 2 virtual seconds. The session evaluator
            // reads its now() through this same clock, so `auth_time + max_age` lapses without
            // burning wall-clock time on Thread.sleep.
            fixture.testClock.advance(2.seconds)

            // Replay /authorize with the same cookie and `max_age=1`. The active session's
            // auth_time is now 2 virtual seconds in the past, exceeding the 1-second max_age, so
            // the AS must redirect to /login instead of issuing a code from the cookie.
            val pkce = ScopedPkceFixture(suffix = "max-age-second")
            val authorizeUrl =
                "${fixture.baseUrl}/authorize?response_type=code" +
                    "&client_id=oidf-op-basic" +
                    "&redirect_uri=${urlEncode("http://localhost:8080/test-callback")}" +
                    "&scope=openid" +
                    "&state=max-age-second" +
                    "&nonce=max-age-second-nonce" +
                    "&code_challenge=${pkce.challenge}" +
                    "&code_challenge_method=S256" +
                    "&max_age=1"
            val response =
                client.get(authorizeUrl) {
                    header("Cookie", cookie)
                }
            assertEquals(
                HttpStatusCode.Found,
                response.status,
                "stale max_age must redirect, got ${response.status}",
            )
            val location = response.headers["Location"] ?: error("missing Location header")
            assertTrue(
                location.contains("/login?"),
                "stale max_age must redirect to /login, got $location",
            )
            assertNotNull(
                extractQueryParam(location, "session_id"),
                "/login redirect must carry session_id, got $location",
            )
            assertNotNull(
                extractQueryParam(location, "return_url"),
                "/login redirect must carry return_url, got $location",
            )
            assertEquals(
                "true",
                extractQueryParam(location, "force_reauth"),
                "stale max_age must set force_reauth=true so the renderer cannot reuse the rejected session",
            )
        }

    @Test
    fun idTokenHintMismatchReturnsLoginRequired() =
        runTest {
            // Mint an id_token for alice. The id_token's `sub` claim is alice's configured
            // subject; the test will hand this hint to the AS while bob owns the active session.
            val aliceLogin = drivePkceLogin(state = "hint-alice", nonce = "hint-alice-nonce")
            val aliceIdToken = aliceLogin.idToken

            // Drive a parallel login as bob on a fresh client so the cookies stay separated.
            val bobClient = HttpClient(CIO) { followRedirects = false }
            try {
                val bobLogin =
                    drivePkceLogin(
                        state = "hint-bob",
                        nonce = "hint-bob-nonce",
                        username = "bob",
                        httpClient = bobClient,
                    )
                val bobCookie = bobLogin.loginCookie

                // Replay /authorize as bob (bob's cookie attached) with alice's id_token as the
                // hint and prompt=none. Per OIDC Core 1.0 §3.1.2.1, the OP cannot silently
                // re-authenticate when the hint's subject does not match the active session, so
                // it must surface error=login_required on the redirect URI.
                val pkce = ScopedPkceFixture(suffix = "hint-mismatch")
                val authorizeUrl =
                    "${fixture.baseUrl}/authorize?response_type=code" +
                        "&client_id=oidf-op-basic" +
                        "&redirect_uri=${urlEncode("http://localhost:8080/test-callback")}" +
                        "&scope=openid" +
                        "&state=hint-mismatch" +
                        "&nonce=hint-mismatch-nonce" +
                        "&code_challenge=${pkce.challenge}" +
                        "&code_challenge_method=S256" +
                        "&prompt=none" +
                        "&id_token_hint=${urlEncode(aliceIdToken)}"
                val response =
                    bobClient.get(authorizeUrl) {
                        header("Cookie", bobCookie)
                    }
                assertEquals(
                    HttpStatusCode.Found,
                    response.status,
                    "id_token_hint mismatch with prompt=none must 302, got ${response.status}",
                )
                val location = response.headers["Location"] ?: error("missing Location header")
                assertTrue(
                    location.startsWith("http://localhost:8080/test-callback"),
                    "error must land on the registered redirect_uri, got $location",
                )
                assertEquals(
                    "login_required",
                    extractQueryParam(location, "error"),
                    "id_token_hint sub mismatch must yield error=login_required, got $location",
                )
                assertEquals(
                    "hint-mismatch",
                    extractQueryParam(location, "state"),
                    "state must round-trip on the error response",
                )
            } finally {
                bobClient.close()
            }
        }

    /**
     * Drives a full PKCE auth-code login on the harness and returns the captured login cookie
     * plus the issued id_token. Used by the max_age and id_token_hint scenarios to seed an
     * active OIDC login session and (for the hint case) mint a real id_token to replay.
     */
    private suspend fun drivePkceLogin(
        state: String,
        nonce: String,
        username: String = "alice",
        httpClient: HttpClient = client,
    ): LoginResult {
        val pkce = ScopedPkceFixture(suffix = state)
        val authorizeUrl =
            "${fixture.baseUrl}/authorize?response_type=code" +
                "&client_id=oidf-op-basic" +
                "&redirect_uri=${urlEncode("http://localhost:8080/test-callback")}" +
                "&scope=openid" +
                "&state=$state" +
                "&nonce=$nonce" +
                "&code_challenge=${pkce.challenge}" +
                "&code_challenge_method=S256"
        val authorizeResponse = httpClient.get(authorizeUrl)
        assertEquals(
            HttpStatusCode.Found,
            authorizeResponse.status,
            "/authorize must redirect to /login on a cold-start request",
        )
        val loginRedirect =
            authorizeResponse.headers["Location"]
                ?: error("/authorize must emit a Location header")
        val loginUrl =
            if (loginRedirect.startsWith("http")) loginRedirect else "${fixture.baseUrl}$loginRedirect"
        val sessionId =
            extractQueryParam(loginUrl, "session_id")
                ?: error("/login redirect must carry session_id")
        val returnUrl =
            extractQueryParam(loginUrl, "return_url")
                ?: error("/login redirect must carry return_url")

        val loginResponse =
            submitLoginWithCsrf(
                client = httpClient,
                baseUrl = fixture.baseUrl,
                loginUrl = loginUrl,
                sessionId = sessionId,
                returnUrl = returnUrl,
                username = username,
                password = OidfOpBootstrap.FIXTURE_PASSWORD,
            )
        assertEquals(
            HttpStatusCode.Found,
            loginResponse.status,
            "POST /login must 302 back to the pending authorize callback",
        )
        val loginCookie =
            loginResponse.headers["Set-Cookie"]
                ?: error("POST /login must Set-Cookie oidc_login_sid")

        val resumedAuthorize =
            httpClient.get(loginResponse.headers["Location"]!!) {
                header("Cookie", loginCookie)
            }
        assertEquals(
            HttpStatusCode.Found,
            resumedAuthorize.status,
            "resumed authorize must redirect to the client redirect_uri",
        )
        val callback = resumedAuthorize.headers["Location"] ?: error("missing callback Location")
        val code = extractQueryParam(callback, "code") ?: error("missing code")

        val basicAuth =
            java.util.Base64
                .getEncoder()
                .encodeToString("oidf-op-basic:oidf-op-basic-secret-2026".encodeToByteArray())
        val tokenResponse =
            httpClient.submitForm(
                url = "${fixture.baseUrl}/token",
                formParameters =
                    Parameters.build {
                        append("grant_type", "authorization_code")
                        append("code", code)
                        append("redirect_uri", "http://localhost:8080/test-callback")
                        append("code_verifier", pkce.verifier)
                    },
            ) {
                header("Authorization", "Basic $basicAuth")
            }
        assertTrue(
            tokenResponse.status.isSuccess(),
            "POST /token must succeed; got ${tokenResponse.status}",
        )
        val tokenBody = json.parseToJsonElement(tokenResponse.bodyAsText()).jsonObject
        val idToken =
            tokenBody["id_token"]?.jsonPrimitive?.content
                ?: error("token response must include id_token")
        return LoginResult(loginCookie = loginCookie, idToken = idToken)
    }

    private fun extractQueryParam(
        url: String,
        key: String,
    ): String? {
        val q = url.substringAfter('?', missingDelimiterValue = "")
        return q
            .split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.firstOrNull() == key }
            ?.getOrNull(1)
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private data class LoginResult(
        val loginCookie: String,
        val idToken: String,
    )
}

/**
 * Fixed S256 PKCE pair scoped per call site. The verifier mixes [suffix] in so the multiple
 * sub-scenarios in this file produce distinct challenges and the AS does not see one verifier
 * replayed across two redemptions.
 */
private class ScopedPkceFixture(
    suffix: String,
) {
    val verifier: String = "oidf-op-prompt-maxage-hint-verifier-$suffix-2026"
    val challenge: String =
        java.util.Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest(verifier.encodeToByteArray()),
            )
}
