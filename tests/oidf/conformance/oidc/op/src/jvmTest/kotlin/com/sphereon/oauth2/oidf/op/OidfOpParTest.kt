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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 9126 (PAR) "Server" role conformance: discovery advertises `pushed_authorization_request_endpoint`
 * after flipping `par` to SUPPORTED, /par accepts a client-authenticated POST and returns a
 * `request_uri`, /authorize honours the URN, and the URN is single-use + expires.
 *
 * Uses [TestClock] from [OidfOpServerFixture] to fast-forward past the 60-second TTL without
 * sleeping the wall clock.
 */
class OidfOpParTest {
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
    fun discoveryAdvertisesPushedAuthorizationRequestEndpoint() =
        runTest {
            val response = client.get("${fixture.baseUrl}/.well-known/openid-configuration")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val parEndpoint = body["pushed_authorization_request_endpoint"]?.jsonPrimitive?.content
            assertNotNull(parEndpoint, "discovery must advertise pushed_authorization_request_endpoint when par=SUPPORTED")
            assertEquals("${fixture.baseUrl}/par", parEndpoint)
            // RFC 9126: require_pushed_authorization_requests is only emitted when explicitly required.
            // The harness leaves par=SUPPORTED (not REQUIRED) so the field MUST be absent or false.
            val required =
                body["require_pushed_authorization_requests"]
                    ?.jsonPrimitive
                    ?.content
                    ?.toBooleanStrictOrNull()
            assertTrue(required != true, "require_pushed_authorization_requests must not be true with par=SUPPORTED")
        }

    @Test
    fun parEndpointIssuesRequestUriForValidPushedRequest() =
        runTest {
            val pkce = ParPkceFixture()
            val response = postPar(pkce, state = "par-state-1")
            assertEquals(HttpStatusCode.Created, response.status, "POST /par must return 201 Created on success")
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val uri = body["request_uri"]?.jsonPrimitive?.content ?: error("missing request_uri")
            assertTrue(
                uri.startsWith("urn:ietf:params:oauth:request_uri:"),
                "request_uri MUST be a urn:ietf:params:oauth:request_uri: URN, got $uri",
            )
            val expiresIn =
                body["expires_in"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: error("missing expires_in")
            assertTrue(expiresIn in 5..600, "expires_in must be within RFC 9126 §2.2 bounds (5s-10min); got $expiresIn")
        }

    @Test
    fun authorizeAcceptsRequestUriFromPar() =
        runTest {
            val pkce = ParPkceFixture()
            val parResponse = postPar(pkce, state = "par-state-2")
            val parBody = json.parseToJsonElement(parResponse.bodyAsText()).jsonObject
            val requestUri =
                parBody["request_uri"]?.jsonPrimitive?.content
                    ?: error("missing request_uri")

            // Per RFC 9126 §3, /authorize accepts only client_id + request_uri; everything else lives
            // in the pushed request. The AS resolves the stored request and proceeds to /login.
            val authorize =
                client.get(
                    "${fixture.baseUrl}/authorize?client_id=oidf-op-basic" +
                        "&request_uri=" + java.net.URLEncoder.encode(requestUri, "UTF-8"),
                )
            assertEquals(HttpStatusCode.Found, authorize.status)
            val location = authorize.headers["Location"] ?: error("/authorize must redirect")
            assertTrue(
                location.contains("/login"),
                "PAR-redeemed authorize must redirect to /login (no active session); got $location",
            )
        }

    @Test
    fun parRequestUriIsSingleUse() =
        runTest {
            val pkce = ParPkceFixture()
            val parResponse = postPar(pkce, state = "par-state-3")
            val requestUri =
                json
                    .parseToJsonElement(parResponse.bodyAsText())
                    .jsonObject["request_uri"]
                    ?.jsonPrimitive
                    ?.content
                    ?: error("no request_uri")

            // FAPI 2.0 SP §5.3.2.2 Note 3 + RetrieveAuthorizationRequestByUriCommandImpl: PAR
            // single-use is enforced at *authorization-code mint*, NOT at every GET /authorize
            // visit. Visiting /authorize twice in a row before login is allowed (lets the user
            // navigate back/refresh without losing the pushed request); the URN is consumed
            // atomically inside CreateAuthorizationCodeCommandImpl when a code is being issued.
            // To exercise the actual single-use guarantee we therefore have to drive a full
            // login → code-mint → token leg, and only THEN replay the URN.

            // Drive the first leg: authorize → login → callback with a fresh code (consumes PAR).
            val authorizeUrl =
                "${fixture.baseUrl}/authorize?client_id=oidf-op-basic" +
                    "&request_uri=" + java.net.URLEncoder.encode(requestUri, "UTF-8")
            val authorizeResponse = client.get(authorizeUrl)
            assertEquals(HttpStatusCode.Found, authorizeResponse.status)
            val loginRedirect =
                authorizeResponse.headers["Location"]
                    ?: error("/authorize must emit a Location header")
            val loginUrl = if (loginRedirect.startsWith("http")) loginRedirect else "${fixture.baseUrl}$loginRedirect"
            val sessionId = extractQueryParam(loginUrl, "session_id") ?: error("no session_id")
            val returnUrl = extractQueryParam(loginUrl, "return_url") ?: error("no return_url")
            val loginResponse =
                submitLoginWithCsrf(
                    client = client,
                    baseUrl = fixture.baseUrl,
                    loginUrl = loginUrl,
                    sessionId = sessionId,
                    returnUrl = returnUrl,
                    username = "alice",
                    password = OidfOpBootstrap.FIXTURE_PASSWORD,
                )
            assertEquals(HttpStatusCode.Found, loginResponse.status)
            val loginCookie = loginResponse.headers["Set-Cookie"] ?: error("POST /login must Set-Cookie")
            val resumed =
                client.get(loginResponse.headers["Location"]!!) {
                    header("Cookie", loginCookie)
                }
            assertEquals(HttpStatusCode.Found, resumed.status, "resumed /authorize must mint a code (PAR consumed here)")
            assertNotNull(extractQueryParam(resumed.headers["Location"]!!, "code"), "first leg must yield a code")

            // Replay the same request_uri after the code mint has consumed it. RFC 9126 §2.2
            // single-use guarantee: the URN MUST now resolve to "not found / consumed" → the
            // AS surfaces invalid_request_uri as a top-level error response (no redirect because
            // the saved request that carried the redirect_uri is gone).
            val replay =
                client.get(
                    "${fixture.baseUrl}/authorize?client_id=oidf-op-basic" +
                        "&request_uri=" + java.net.URLEncoder.encode(requestUri, "UTF-8"),
                )
            assertEquals(
                HttpStatusCode.BadRequest,
                replay.status,
                "Replaying a consumed PAR request_uri must yield invalid_request_uri (400)",
            )
            // The /authorize endpoint surfaces pre-redirect errors as the friendly HTML error
            // page (no `redirect_uri` is trustable once the PAR-stored request is gone), not as
            // a JSON envelope; the page text carries the error code so SIEM/UA can still
            // classify it. JSON envelopes are reserved for token-style endpoints (RFC 6749 §5.2).
            val replayBody = replay.bodyAsText()
            assertTrue(
                replayBody.contains("invalid_request_uri"),
                "Replay error page must mention invalid_request_uri; got: ${replayBody.take(200)}",
            )
        }

    @Test
    fun parRequestUriExpires() =
        runTest {
            val pkce = ParPkceFixture()
            val parResponse = postPar(pkce, state = "par-state-4")
            val requestUri =
                json
                    .parseToJsonElement(parResponse.bodyAsText())
                    .jsonObject["request_uri"]
                    ?.jsonPrimitive
                    ?.content
                    ?: error("no request_uri")

            // Fast-forward past the configured 60-second TTL on the test clock. Storage uses
            // Clock.System.now() inside `consumeRequest`, but the same shared TestClock backs both
            // the request lifetime computation in CreateRequestUriCommandImpl (Clock.System.now)
            // and the storage's expiry check. To force an "expired" outcome we advance well
            // beyond the TTL.
            fixture.testClock.advance(kotlin.time.Duration.parse("PT5M"))

            val expired =
                client.get(
                    "${fixture.baseUrl}/authorize?client_id=oidf-op-basic" +
                        "&request_uri=" + java.net.URLEncoder.encode(requestUri, "UTF-8"),
                )
            assertEquals(
                HttpStatusCode.BadRequest,
                expired.status,
                "Expired PAR request_uri must yield invalid_request_uri (400)",
            )
        }

    /**
     * Posts a PAR request authenticated with HTTP Basic against the `oidf-op-basic` confidential
     * client. Mirrors the parameters the harness already exercises for the standard /authorize
     * happy path.
     */
    private suspend fun postPar(
        pkce: ParPkceFixture,
        state: String,
    ) = client.submitForm(
        url = "${fixture.baseUrl}/par",
        formParameters =
            Parameters.build {
                append("response_type", "code")
                append("client_id", "oidf-op-basic")
                append("redirect_uri", "http://localhost:8080/test-callback")
                append("scope", "openid")
                append("state", state)
                append("nonce", "par-nonce-$state")
                append("code_challenge", pkce.challenge)
                append("code_challenge_method", "S256")
            },
    ) {
        val basicAuth =
            Base64
                .getEncoder()
                .encodeToString("oidf-op-basic:oidf-op-basic-secret-2026".encodeToByteArray())
        header("Authorization", "Basic $basicAuth")
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
}

private class ParPkceFixture {
    val verifier: String = "oidf-op-conformance-pkce-verifier-fixture-2026-P"
    val challenge: String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray()))
}
