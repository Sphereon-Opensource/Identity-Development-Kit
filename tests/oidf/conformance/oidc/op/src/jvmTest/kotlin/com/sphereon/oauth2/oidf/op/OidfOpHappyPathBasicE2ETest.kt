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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Happy-path authorization-code flow against the `oidf-op-basic` confidential client. Exercises
 * the full chain the OIDF Basic-OP `OIDCCBasic` test profile drives:
 *
 *  1. GET `/authorize?response_type=code&client_id=oidf-op-basic&...&code_challenge=...` →
 *     302 to `/login?session_id=...&return_url=...`.
 *  2. POST `/login` with form-encoded credentials → 302 to the `return_url` with a
 *     `Set-Cookie: oidc_login_sid=...` header (Group G).
 *  3. Following the resumed authorize → 302 to the registered redirect URI with `code` + `state`.
 *  4. POST `/token` with the code, PKCE verifier, and HTTP Basic client auth → 200 with
 *     `access_token`, `id_token`, `token_type=Bearer`, and `expires_in > 0`.
 *  5. Decoded id_token carries `iss=base-url`, `aud=oidf-op-basic`, the alice subject, the
 *     supplied nonce, and `auth_time`.
 *  6. GET `/userinfo` with the bearer access token returns `sub=urn:sphereon:oidf:op:alice`.
 */
class OidfOpHappyPathBasicE2ETest {
    private lateinit var fixture: OidfOpServerFixture
    private lateinit var client: HttpClient
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        fixture = OidfOpServerFixture()
        client =
            HttpClient(CIO) {
                followRedirects = false
            }
    }

    @AfterTest
    fun tearDown() {
        client.close()
        fixture.stop()
    }

    @Test
    fun authorizationCodeFlowSucceedsForOidfOpBasicClient() =
        runTest {
            // Step 1: kick off /authorize. Expect 302 → /login.
            val pkce = SimplePkceFixture()
            val authorizeUrl =
                "${fixture.baseUrl}/authorize?response_type=code" +
                    "&client_id=oidf-op-basic" +
                    "&redirect_uri=${urlEncode("http://localhost:8080/test-callback")}" +
                    "&scope=openid" +
                    "&state=xyz" +
                    "&nonce=oidf-nonce" +
                    "&code_challenge=${pkce.challenge}" +
                    "&code_challenge_method=S256"
            val authorizeResponse = client.get(authorizeUrl)
            assertEquals(
                HttpStatusCode.Found,
                authorizeResponse.status,
                "/authorize must redirect to /login when no OidcLoginSession cookie is present",
            )
            val loginRedirect =
                authorizeResponse.headers["Location"]
                    ?: error("/authorize must emit a Location header")
            assertTrue(loginRedirect.contains("/login"), "expected redirect to /login, got $loginRedirect")

            // Step 2: POST credentials. Expect 302 → return_url with Set-Cookie.
            val loginUrl = if (loginRedirect.startsWith("http")) loginRedirect else "${fixture.baseUrl}$loginRedirect"
            val sessionId =
                extractQueryParam(loginUrl, "session_id")
                    ?: error("/login redirect must carry session_id")
            val returnUrl =
                extractQueryParam(loginUrl, "return_url")
                    ?: error("/login redirect must carry return_url")
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
            assertEquals(
                HttpStatusCode.Found,
                loginResponse.status,
                "POST /login must 302 back to the pending authorize callback after correct credentials",
            )
            val cookie = loginResponse.headers["Set-Cookie"]
            assertNotNull(cookie, "POST /login must Set-Cookie oidc_login_sid")

            // Step 3: resume /authorize → 302 to the registered redirect URI with code + state.
            val resumedAuthorize =
                client.get(loginResponse.headers["Location"]!!) {
                    header("Cookie", cookie)
                }
            assertEquals(HttpStatusCode.Found, resumedAuthorize.status, "resumed authorize must redirect to the client redirect_uri")
            val callback = resumedAuthorize.headers["Location"] ?: error("missing callback Location")
            val code = extractQueryParam(callback, "code") ?: error("missing code")
            assertEquals("xyz", extractQueryParam(callback, "state"), "state must round-trip")

            // Step 4: POST /token with HTTP Basic and PKCE verifier.
            val basicAuth =
                java.util.Base64
                    .getEncoder()
                    .encodeToString("oidf-op-basic:oidf-op-basic-secret-2026".encodeToByteArray())
            val tokenResponse =
                client.submitForm(
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
            assertTrue(tokenResponse.status.isSuccess(), "POST /token must succeed; got ${tokenResponse.status}")
            val tokenBody = json.parseToJsonElement(tokenResponse.bodyAsText()).jsonObject
            val accessToken =
                tokenBody["access_token"]?.jsonPrimitive?.content
                    ?: error("token response must include access_token")
            val idToken =
                tokenBody["id_token"]?.jsonPrimitive?.content
                    ?: error("token response must include id_token")
            assertEquals("Bearer", tokenBody["token_type"]?.jsonPrimitive?.content)
            assertTrue(
                (tokenBody["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0) > 0,
                "expires_in must be positive",
            )

            // Step 5: decode id_token claims and validate against expectations.
            val payload = decodeJwsPayload(idToken)
            assertEquals(fixture.baseUrl, payload["iss"]?.jsonPrimitive?.content, "iss must equal harness base URL")
            assertEquals("oidf-op-basic", payload["aud"]?.jsonPrimitive?.content, "aud must equal client id")
            assertEquals(
                "urn:sphereon:oidf:op:alice",
                payload["sub"]?.jsonPrimitive?.content,
                "sub must equal alice's configured subject",
            )
            assertEquals("oidf-nonce", payload["nonce"]?.jsonPrimitive?.content, "nonce must round-trip")
            assertNotNull(payload["auth_time"], "auth_time must be present in the id_token")

            // Step 6: /userinfo must return alice's claims.
            val userInfoResponse =
                client.get("${fixture.baseUrl}/userinfo") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertTrue(userInfoResponse.status.isSuccess(), "GET /userinfo must succeed; got ${userInfoResponse.status}")
            val userInfo = json.parseToJsonElement(userInfoResponse.bodyAsText()).jsonObject
            assertEquals(
                "urn:sphereon:oidf:op:alice",
                userInfo["sub"]?.jsonPrimitive?.content,
                "/userinfo sub must equal alice's configured subject",
            )
        }

    private fun decodeJwsPayload(jws: String): JsonObject {
        val payloadSegment = jws.split(".").getOrNull(1) ?: error("JWS must have three segments")
        val padded =
            when (payloadSegment.length % 4) {
                0 -> payloadSegment
                2 -> "$payloadSegment=="
                3 -> "$payloadSegment="
                else -> error("Invalid base64url length: ${payloadSegment.length}")
            }
        val decoded =
            java.util.Base64
                .getUrlDecoder()
                .decode(padded)
                .decodeToString()
        return json.parseToJsonElement(decoded).jsonObject
    }

    private fun extractQueryParam(
        url: String,
        key: String
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
}

/**
 * Fixed S256 PKCE pair for the happy-path test. Verifier is the literal string below; challenge
 * is the base64url-encoded SHA-256 of the verifier bytes, computed offline so the harness does
 * not need a crypto provider for the test itself.
 */
private class SimplePkceFixture {
    val verifier: String = "oidf-op-conformance-pkce-verifier-fixture-2026-A"
    val challenge: String =
        java.util.Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest(verifier.encodeToByteArray())
            )
}
