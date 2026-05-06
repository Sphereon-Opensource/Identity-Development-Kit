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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end coverage for the `grant_type=refresh_token` flow against the `oidf-op-basic`
 * confidential client. The OIDF Basic-OP profile exercises refresh-token rotation per
 * RFC 6749 §6 + §5.2, with token rotation per RFC 6819 §5.2.2.3 (default
 * `oauth2.servers.default.refresh-token-rotation=true`).
 *
 * Two scenarios:
 *
 *  1. [refreshGrantIssuesNewAccessTokenAndRotatesRefreshToken]: drive auth-code login → token
 *     endpoint (auth-code grant), capture (access_token, refresh_token, id_token); POST /token
 *     with `grant_type=refresh_token` + Basic auth → assert NEW access_token and NEW
 *     refresh_token. OIDC Core §12 says the AS MAY return a new id_token; the test inspects
 *     whichever shape the IDK actually returns and asserts on it.
 *  2. [oldRefreshTokenIsInvalidatedAfterRotation]: refresh once, then attempt to refresh again
 *     with the original (now-rotated) refresh_token → assert 400 with `error=invalid_grant`,
 *     and confirm the rotated refresh_token still works on a fresh refresh.
 */
class OidfOpRefreshTokenE2ETest {
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
    fun refreshGrantIssuesNewAccessTokenAndRotatesRefreshToken() =
        runTest {
            // Step 1: drive auth-code login → /token (auth-code grant) → capture all three tokens.
            val initial = drivePkceLoginAndExchangeCode(state = "refresh-rotate", nonce = NONCE_REFRESH_ROTATE)
            val originalAccessToken = initial.accessToken
            val originalRefreshToken = initial.refreshToken
            val originalIdToken = initial.idToken

            // Decode the original id_token to capture identity claims for later comparison.
            // OIDC Core 1.0 §12 reissue must echo these on the refreshed id_token.
            val originalPayload = decodeJwsPayload(originalIdToken)
            val originalSub =
                originalPayload["sub"]?.jsonPrimitive?.content
                    ?: error("original id_token must carry sub")
            val originalAuthTime =
                originalPayload["auth_time"]?.jsonPrimitive?.content
                    ?: error("original id_token must carry auth_time")
            val originalIat =
                originalPayload["iat"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: error("original id_token must carry iat")
            val originalNonce =
                originalPayload["nonce"]?.jsonPrimitive?.content
                    ?: error("original id_token must carry nonce because /authorize was called with nonce=$NONCE_REFRESH_ROTATE")
            val originalSid = originalPayload["sid"]?.jsonPrimitive?.content
            assertEquals(fixture.baseUrl, originalPayload["iss"]?.jsonPrimitive?.content)
            assertEquals("oidf-op-basic", originalPayload["aud"]?.jsonPrimitive?.content)
            assertEquals(NONCE_REFRESH_ROTATE, originalNonce, "original id_token nonce must equal the /authorize nonce")

            // Step 2: POST /token with grant_type=refresh_token + Basic auth.
            val basicAuth =
                java.util.Base64
                    .getEncoder()
                    .encodeToString("oidf-op-basic:oidf-op-basic-secret-2026".encodeToByteArray())
            val refreshResponse =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "refresh_token")
                            append("refresh_token", originalRefreshToken)
                            append("scope", "openid")
                        },
                ) {
                    header("Authorization", "Basic $basicAuth")
                }
            assertTrue(
                refreshResponse.status.isSuccess(),
                "POST /token (refresh_token grant) must succeed; got ${refreshResponse.status}: ${refreshResponse.bodyAsText()}",
            )
            val refreshBody = json.parseToJsonElement(refreshResponse.bodyAsText()).jsonObject
            val newAccessToken =
                refreshBody["access_token"]?.jsonPrimitive?.content
                    ?: error("refresh response must include access_token")
            assertEquals("Bearer", refreshBody["token_type"]?.jsonPrimitive?.content)
            assertTrue(
                (refreshBody["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0) > 0,
                "expires_in must be positive on refresh response",
            )
            assertNotEquals(
                originalAccessToken,
                newAccessToken,
                "refresh grant must mint a fresh access_token distinct from the original",
            )

            val newRefreshToken =
                refreshBody["refresh_token"]?.jsonPrimitive?.content
                    ?: error(
                        "refresh response must include a rotated refresh_token when " +
                            "oauth2.servers.default.refresh-token-rotation=true (the default)",
                    )
            assertNotEquals(
                originalRefreshToken,
                newRefreshToken,
                "rotated refresh_token must differ from the consumed one (RFC 6819 §5.2.2.3)",
            )

            // OIDC Core 1.0 §12: AS reissues an id_token on refresh when the original grant
            // carried `openid` (the request above sent `scope=openid`). auth_time/nonce/sid
            // MUST be preserved (re-auth was not forced), iss/aud/sub MUST match the original,
            // and iat MUST be fresh (later than the original) to reflect the new issuance.
            val refreshedIdToken =
                refreshBody["id_token"]?.jsonPrimitive?.content
                    ?: error("refresh response must include id_token (OIDC Core 1.0 §12) when scope contains openid")
            val refreshedPayload = decodeJwsPayload(refreshedIdToken)
            assertEquals(
                originalSub,
                refreshedPayload["sub"]?.jsonPrimitive?.content,
                "refreshed id_token sub must equal the original",
            )
            assertEquals(
                fixture.baseUrl,
                refreshedPayload["iss"]?.jsonPrimitive?.content,
                "refreshed id_token iss must equal the harness base URL",
            )
            assertEquals(
                "oidf-op-basic",
                refreshedPayload["aud"]?.jsonPrimitive?.content,
                "refreshed id_token aud must equal the client id",
            )
            assertEquals(
                originalAuthTime,
                refreshedPayload["auth_time"]?.jsonPrimitive?.content,
                "auth_time must be preserved across refresh: re-authentication was not forced",
            )
            assertEquals(
                originalNonce,
                refreshedPayload["nonce"]?.jsonPrimitive?.content,
                "refreshed id_token nonce must echo the original /authorize nonce (OIDC Core 1.0 §3.1.3.7 step 11)",
            )
            if (originalSid != null) {
                assertEquals(
                    originalSid,
                    refreshedPayload["sid"]?.jsonPrimitive?.content,
                    "refreshed id_token sid must equal the original login session id",
                )
            }
            val refreshedIat =
                refreshedPayload["iat"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: error("refreshed id_token must carry iat")
            assertTrue(
                refreshedIat >= originalIat,
                "refreshed id_token iat ($refreshedIat) must be >= original iat ($originalIat)",
            )

            // Step 3: confirm the new access_token validates against /userinfo. This proves the
            // freshly-minted token threads through to the resource server.
            val userInfoResponse =
                client.get("${fixture.baseUrl}/userinfo") {
                    header("Authorization", "Bearer $newAccessToken")
                }
            assertTrue(
                userInfoResponse.status.isSuccess(),
                "GET /userinfo with the refreshed access_token must succeed; got ${userInfoResponse.status}",
            )
            val userInfo = json.parseToJsonElement(userInfoResponse.bodyAsText()).jsonObject
            assertEquals(
                originalSub,
                userInfo["sub"]?.jsonPrimitive?.content,
                "/userinfo sub must match the original id_token sub",
            )
        }

    @Test
    fun oldRefreshTokenIsInvalidatedAfterRotation() =
        runTest {
            // Step 1: drive auth-code login → capture refresh_token A.
            val initial = drivePkceLoginAndExchangeCode(state = "refresh-replay", nonce = "refresh-replay-nonce")
            val refreshTokenA = initial.refreshToken

            val basicAuth =
                java.util.Base64
                    .getEncoder()
                    .encodeToString("oidf-op-basic:oidf-op-basic-secret-2026".encodeToByteArray())

            // Step 2: redeem refresh_token A → receive rotated refresh_token B.
            val firstRefresh =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "refresh_token")
                            append("refresh_token", refreshTokenA)
                            append("scope", "openid")
                        },
                ) {
                    header("Authorization", "Basic $basicAuth")
                }
            assertTrue(
                firstRefresh.status.isSuccess(),
                "first refresh must succeed; got ${firstRefresh.status}: ${firstRefresh.bodyAsText()}",
            )
            val firstRefreshBody = json.parseToJsonElement(firstRefresh.bodyAsText()).jsonObject
            val refreshTokenB =
                firstRefreshBody["refresh_token"]?.jsonPrimitive?.content
                    ?: error("first refresh must rotate and return refresh_token B")
            assertNotEquals(refreshTokenA, refreshTokenB, "rotation must produce a new token value")

            // Step 3: replay refresh_token A → must be rejected with invalid_grant.
            val replayedA =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "refresh_token")
                            append("refresh_token", refreshTokenA)
                            append("scope", "openid")
                        },
                ) {
                    header("Authorization", "Basic $basicAuth")
                }
            assertEquals(
                HttpStatusCode.BadRequest,
                replayedA.status,
                "replaying a rotated refresh_token must yield 400 (RFC 6749 §5.2)",
            )
            val replayedBody = json.parseToJsonElement(replayedA.bodyAsText()).jsonObject
            assertEquals(
                "invalid_grant",
                replayedBody["error"]?.jsonPrimitive?.content,
                "replayed (rotated-out) refresh_token must surface error=invalid_grant",
            )

            // Step 4: refresh_token B still works → confirms only A was invalidated, not the chain.
            val secondRefresh =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "refresh_token")
                            append("refresh_token", refreshTokenB)
                            append("scope", "openid")
                        },
                ) {
                    header("Authorization", "Basic $basicAuth")
                }
            assertTrue(
                secondRefresh.status.isSuccess(),
                "rotated refresh_token B must remain valid for one more refresh; got ${secondRefresh.status}",
            )
        }

    /**
     * Drives the full PKCE auth-code login + token exchange and returns access_token,
     * refresh_token, and id_token. Mirrors the helper in [OidfOpPromptMaxAgeIdTokenHintTest]
     * but inlined here so cross-test helper drift cannot break this scenario.
     */
    private suspend fun drivePkceLoginAndExchangeCode(
        state: String,
        nonce: String,
        username: String = "alice",
    ): TokenSet {
        val pkce = RefreshTokenPkceFixture(suffix = state)
        val authorizeUrl =
            "${fixture.baseUrl}/authorize?response_type=code" +
                "&client_id=oidf-op-basic" +
                "&redirect_uri=${urlEncode("http://localhost:8080/test-callback")}" +
                "&scope=openid" +
                "&state=$state" +
                "&nonce=$nonce" +
                "&code_challenge=${pkce.challenge}" +
                "&code_challenge_method=S256"
        val authorizeResponse = client.get(authorizeUrl)
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
                client = client,
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
            client.get(loginResponse.headers["Location"]!!) {
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
        assertTrue(
            tokenResponse.status.isSuccess(),
            "POST /token (auth-code grant) must succeed; got ${tokenResponse.status}",
        )
        val tokenBody = json.parseToJsonElement(tokenResponse.bodyAsText()).jsonObject
        val accessToken =
            tokenBody["access_token"]?.jsonPrimitive?.content
                ?: error("auth-code response must include access_token")
        val refreshToken =
            tokenBody["refresh_token"]?.jsonPrimitive?.content
                ?: error("auth-code response must include refresh_token")
        val idToken =
            tokenBody["id_token"]?.jsonPrimitive?.content
                ?: error("auth-code response must include id_token")
        assertNotNull(loginCookie, "login cookie must be captured")
        return TokenSet(accessToken = accessToken, refreshToken = refreshToken, idToken = idToken)
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

    private data class TokenSet(
        val accessToken: String,
        val refreshToken: String,
        val idToken: String,
    )

    private companion object {
        /**
         * Nonce sent on the rotation scenario's `/authorize` request. Captured here so the
         * refreshed-id_token assertions can compare against a known constant rather than
         * re-deriving from the authorize-helper's local variable.
         */
        const val NONCE_REFRESH_ROTATE: String = "refresh-rotate-nonce"
    }
}

/**
 * Fixed S256 PKCE pair scoped per call site. Each refresh-token sub-scenario in this file mints
 * a unique verifier so the AS does not see one verifier replayed across distinct authorize
 * sessions, even though the auth code itself is single-use.
 */
private class RefreshTokenPkceFixture(
    suffix: String,
) {
    val verifier: String = "oidf-op-refresh-token-verifier-$suffix-2026"
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
