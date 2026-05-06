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
import kotlinx.serialization.json.jsonArray
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
 * RFC 8693 Token Exchange conformance probes. Verifies:
 *  - discovery advertises the token-exchange URN in `grant_types_supported` once
 *    `tokenExchange = SUPPORTED`,
 *  - a valid JWT subject_token grants a fresh access token whose payload preserves the original
 *    subject, and the response carries `issued_token_type`,
 *  - an invalid subject_token surfaces `invalid_grant` (or wraps it in `unauthorized_client`),
 *  - the `audience` request parameter narrows the issued token's `aud`,
 *  - `scope` narrows the granted scope.
 *
 * The test suite first runs the canonical authorization-code flow to mint a JWT subject token,
 * then exchanges that token via `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`.
 */
class OidfOpTokenExchangeTest {
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
    fun discoveryAdvertisesTokenExchangeGrant() =
        runTest {
            val response = client.get("${fixture.baseUrl}/.well-known/openid-configuration")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val grantTypes =
                body["grant_types_supported"]?.jsonArray?.map { it.jsonPrimitive.content }
                    ?: emptyList()
            assertTrue(
                "urn:ietf:params:oauth:grant-type:token-exchange" in grantTypes,
                "grant_types_supported must include the token-exchange URN: $grantTypes",
            )
        }

    @Test
    fun tokenExchangeIssuesNewAccessTokenWhenSubjectTokenIsValid() =
        runTest {
            val (subjectAccessToken, _) = mintSubjectAccessToken()

            val exchangeResponse =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                            append("subject_token", subjectAccessToken)
                            append("subject_token_type", "urn:ietf:params:oauth:token-type:access_token")
                        },
                ) {
                    header("Authorization", "Basic ${basicAuth()}")
                }
            assertTrue(
                exchangeResponse.status.isSuccess(),
                "Token exchange must succeed; got ${exchangeResponse.status}: ${exchangeResponse.bodyAsText()}",
            )
            val body = json.parseToJsonElement(exchangeResponse.bodyAsText()).jsonObject
            val newAccessToken =
                body["access_token"]?.jsonPrimitive?.content
                    ?: error("token exchange must return access_token")
            assertEquals(
                "urn:ietf:params:oauth:token-type:access_token",
                body["issued_token_type"]?.jsonPrimitive?.content,
                "issued_token_type defaults to access_token per RFC 8693 §2.2.1",
            )
            assertEquals("Bearer", body["token_type"]?.jsonPrimitive?.content)

            val newPayload = decodeJwsPayload(newAccessToken)
            assertEquals(
                "urn:sphereon:oidf:op:alice",
                newPayload["sub"]?.jsonPrimitive?.content,
                "exchanged token must preserve original subject",
            )
        }

    @Test
    fun tokenExchangeRejectsInvalidSubjectToken() =
        runTest {
            val tampered = "header.payload.signature"
            val response =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                            append("subject_token", tampered)
                            append("subject_token_type", "urn:ietf:params:oauth:token-type:access_token")
                        },
                ) {
                    header("Authorization", "Basic ${basicAuth()}")
                }
            assertEquals(
                HttpStatusCode.BadRequest,
                response.status,
                "tampered subject_token must be rejected with 400",
            )
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val error = body["error"]?.jsonPrimitive?.content
            assertTrue(
                error == "invalid_grant" || error == "access_denied",
                "rejection must use invalid_grant or access_denied, got '$error'",
            )
        }

    @Test
    fun tokenExchangeNarrowsAudienceWhenRequested() =
        runTest {
            val (subjectAccessToken, _) = mintSubjectAccessToken()
            val targetAudience = "https://downstream.example.com/api"

            val exchangeResponse =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                            append("subject_token", subjectAccessToken)
                            append("subject_token_type", "urn:ietf:params:oauth:token-type:access_token")
                            append("audience", targetAudience)
                        },
                ) {
                    header("Authorization", "Basic ${basicAuth()}")
                }
            assertTrue(
                exchangeResponse.status.isSuccess(),
                "exchange with audience must succeed; got ${exchangeResponse.status}: ${exchangeResponse.bodyAsText()}",
            )
            val body = json.parseToJsonElement(exchangeResponse.bodyAsText()).jsonObject
            val newAccessToken =
                body["access_token"]?.jsonPrimitive?.content
                    ?: error("no access_token")
            val payload = decodeJwsPayload(newAccessToken)
            val aud = payload["aud"]
            val audValues =
                when {
                    aud?.jsonPrimitive?.isString == true -> listOf(aud.jsonPrimitive.content)
                    aud != null -> aud.jsonArray.map { it.jsonPrimitive.content }
                    else -> emptyList()
                }
            assertTrue(
                targetAudience in audValues,
                "issued access token's aud must contain '$targetAudience'; got $audValues",
            )
        }

    @Test
    fun tokenExchangeRespectsScopeNarrowing() =
        runTest {
            val (subjectAccessToken, _) = mintSubjectAccessToken()

            val exchangeResponse =
                client.submitForm(
                    url = "${fixture.baseUrl}/token",
                    formParameters =
                        Parameters.build {
                            append("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                            append("subject_token", subjectAccessToken)
                            append("subject_token_type", "urn:ietf:params:oauth:token-type:access_token")
                            append("scope", "profile")
                        },
                ) {
                    header("Authorization", "Basic ${basicAuth()}")
                }
            assertTrue(
                exchangeResponse.status.isSuccess(),
                "exchange with scope must succeed; got ${exchangeResponse.status}: ${exchangeResponse.bodyAsText()}",
            )
            val body = json.parseToJsonElement(exchangeResponse.bodyAsText()).jsonObject
            val grantedScope = body["scope"]?.jsonPrimitive?.content
            // Scope passed through; we don't strictly require equality (policy may further narrow).
            assertNotNull(grantedScope, "narrowed exchange must return a `scope` claim in the token response")
            assertTrue("profile" in grantedScope.split(" "), "narrowed scope must contain 'profile'; got '$grantedScope'")
        }

    /**
     * Drive the canonical authorization-code flow against the OIDF harness and return the issued
     * `(access_token, id_token)`.
     */
    private suspend fun mintSubjectAccessToken(): Pair<String, String> {
        val pkce = TxPkceFixture()
        val authorizeUrl =
            "${fixture.baseUrl}/authorize?response_type=code" +
                "&client_id=oidf-op-basic" +
                "&redirect_uri=${urlEncode("http://localhost:8080/test-callback")}" +
                "&scope=${urlEncode("openid profile email")}" +
                "&state=tx-state" +
                "&nonce=tx-nonce" +
                "&code_challenge=${pkce.challenge}" +
                "&code_challenge_method=S256"
        val authorizeResponse = client.get(authorizeUrl)
        assertEquals(HttpStatusCode.Found, authorizeResponse.status)
        val loginRedirect = authorizeResponse.headers["Location"] ?: error("no Location")
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
        val cookie = loginResponse.headers["Set-Cookie"] ?: error("no Set-Cookie")
        val resumed =
            client.get(loginResponse.headers["Location"]!!) {
                header("Cookie", cookie)
            }
        val callback = resumed.headers["Location"] ?: error("no callback Location")
        val code = extractQueryParam(callback, "code") ?: error("no code")
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
                header("Authorization", "Basic ${basicAuth()}")
            }
        assertTrue(tokenResponse.status.isSuccess(), "token mint must succeed; got ${tokenResponse.status}: ${tokenResponse.bodyAsText()}")
        val body = json.parseToJsonElement(tokenResponse.bodyAsText()).jsonObject
        val accessToken = body["access_token"]?.jsonPrimitive?.content ?: error("no access_token")
        val idToken = body["id_token"]?.jsonPrimitive?.content ?: error("no id_token")
        return accessToken to idToken
    }

    private fun basicAuth(): String =
        Base64
            .getEncoder()
            .encodeToString("oidf-op-basic:oidf-op-basic-secret-2026".encodeToByteArray())

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
            Base64
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
}

private class TxPkceFixture {
    val verifier: String = "oidf-op-conformance-pkce-verifier-fixture-2026-tx"
    val challenge: String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray()))
}
