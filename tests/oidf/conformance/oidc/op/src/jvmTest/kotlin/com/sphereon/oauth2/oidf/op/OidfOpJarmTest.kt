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
 * OIDF JARM (https://openid.net/specs/oauth-v2-jarm.html) conformance probes against the OIDC OP
 * harness. Verifies:
 *  - discovery advertises `authorization_signing_alg_values_supported` + the `*.jwt`
 *    `response_modes_supported` entries when JARM is enabled,
 *  - the authorize callback delivers a signed JWT in `?response=...` for `response_mode=query.jwt`,
 *  - the same path delivers a fragment-carried JWT for `response_mode=fragment.jwt`,
 *  - `response_mode=form_post.jwt` returns an HTML auto-submit form with a `response` hidden
 *    input, and
 *  - a JARM request from a client without `authorization_signed_response_alg` configured is
 *    rejected post-redirect with `error=invalid_request`.
 */
class OidfOpJarmTest {
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
    fun discoveryAdvertisesJarmAlgs() =
        runTest {
            val response = client.get("${fixture.baseUrl}/.well-known/openid-configuration")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject

            val signingAlgs =
                body["authorization_signing_alg_values_supported"]?.jsonArray?.map { it.jsonPrimitive.content }
                    ?: emptyList()
            assertTrue(
                "RS256" in signingAlgs,
                "discovery must advertise authorization_signing_alg_values_supported with RS256: $signingAlgs",
            )

            val responseModes =
                body["response_modes_supported"]?.jsonArray?.map { it.jsonPrimitive.content }
                    ?: emptyList()
            listOf("query", "fragment", "form_post", "jwt", "query.jwt", "fragment.jwt", "form_post.jwt").forEach { mode ->
                assertTrue(
                    mode in responseModes,
                    "discovery must advertise response_modes_supported entry '$mode' when JARM is enabled: $responseModes",
                )
            }
        }

    @Test
    fun queryJwtResponseModeReturnsJarmJwtAsQueryParam() =
        runTest {
            val pkce = JarmPkceFixture("Q")
            val (callback, _) = runJarmAuthorizeFlow("query.jwt", pkce)
            val callbackUrl = callback ?: error("query.jwt must redirect, got null callback")
            val responseJwt =
                extractQueryParam(callbackUrl, "response")
                    ?: error("query.jwt callback must carry a `response` query parameter: $callbackUrl")
            val payload = decodeJwsPayload(responseJwt)
            assertJarmPayloadShape(payload)
        }

    @Test
    fun fragmentJwtResponseModeReturnsJarmJwtInFragment() =
        runTest {
            val pkce = JarmPkceFixture("F")
            val (callback, _) = runJarmAuthorizeFlow("fragment.jwt", pkce)
            val callbackUrl = callback ?: error("fragment.jwt must redirect, got null callback")
            val fragment = callbackUrl.substringAfter('#', "")
            assertTrue(fragment.isNotBlank(), "fragment.jwt callback must carry a fragment: $callbackUrl")
            val responseJwt =
                fragment
                    .split("&")
                    .map { it.split("=", limit = 2) }
                    .firstOrNull { it.firstOrNull() == "response" }
                    ?.getOrNull(1)
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                    ?: error("fragment.jwt fragment must contain `response=...`: $fragment")
            val payload = decodeJwsPayload(responseJwt)
            assertJarmPayloadShape(payload)
        }

    @Test
    fun formPostJwtResponseModeReturnsAutoSubmitFormWithJarmJwt() =
        runTest {
            val pkce = JarmPkceFixture("FP")
            val (location, htmlBody) = runJarmAuthorizeFlow("form_post.jwt", pkce, expectFormPost = true)
            assertTrue(location == null || location == "http://localhost:8080/test-callback", "form_post.jwt must not 302; got Location='$location'")
            assertNotNull(htmlBody, "form_post.jwt must return a 200 OK with auto-submit HTML form")
            assertTrue(htmlBody.contains("<form method=\"post\""))
            assertTrue(htmlBody.contains("name=\"response\""), "form_post.jwt body must contain a `response` hidden input")
            // Pull the `value="<jwt>"` out of the response hidden input and validate the payload.
            val responseValue = extractFormInputValue(htmlBody, "response")
            assertNotNull(responseValue, "could not extract response input value from: $htmlBody")
            val payload = decodeJwsPayload(responseValue)
            assertJarmPayloadShape(payload)
        }

    @Test
    fun jarmRequestForClientWithoutSigningAlgIsRejectedPostRedirect() =
        runTest {
            // The `oidf-op-public` client doesn't have `authorization-signed-response-alg`
            // configured. A JARM request for it must surface `invalid_request` post-redirect via
            // the registered redirect URI.
            val pkce = JarmPkceFixture("X")
            val authorizeUrl =
                "${fixture.baseUrl}/authorize?response_type=code" +
                    "&client_id=oidf-op-public" +
                    "&redirect_uri=${urlEncode("http://localhost:8080/test-callback")}" +
                    "&scope=openid" +
                    "&state=jarm-state-x" +
                    "&nonce=jarm-nonce-x" +
                    "&code_challenge=${pkce.challenge}" +
                    "&code_challenge_method=S256" +
                    "&response_mode=query.jwt"
            val response = client.get(authorizeUrl)
            assertEquals(HttpStatusCode.Found, response.status)
            val location = response.headers["Location"] ?: error("no Location")
            // Post-redirect surface: redirect to the callback with `error=invalid_request`.
            assertTrue(
                location.startsWith("http://localhost:8080/test-callback"),
                "JARM rejection must redirect to the registered redirect URI; got $location",
            )
            assertEquals("invalid_request", extractQueryParam(location, "error"))
        }

    /**
     * Drive Steps 1-3 of the harness happy-path with `response_mode` set to a JARM mode and
     * return the final callback URL (success path) or the form HTML body (form_post.jwt path).
     */
    private suspend fun runJarmAuthorizeFlow(
        responseMode: String,
        pkce: JarmPkceFixture,
        expectFormPost: Boolean = false,
    ): Pair<String?, String?> {
        val authorizeUrl =
            "${fixture.baseUrl}/authorize?response_type=code" +
                "&client_id=oidf-op-basic" +
                "&redirect_uri=${urlEncode("http://localhost:8080/test-callback")}" +
                "&scope=openid" +
                "&state=jarm-state-${responseMode.replace('.', '-')}" +
                "&nonce=jarm-nonce-${responseMode.replace('.', '-')}" +
                "&code_challenge=${pkce.challenge}" +
                "&code_challenge_method=S256" +
                "&response_mode=$responseMode"
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
        return if (expectFormPost) {
            assertEquals(
                HttpStatusCode.OK,
                resumed.status,
                "form_post.jwt must return 200 OK with HTML body; body=${resumed.bodyAsText()}",
            )
            null to resumed.bodyAsText()
        } else {
            val body = resumed.bodyAsText()
            assertEquals(HttpStatusCode.Found, resumed.status, "resumed authorize must redirect; body=$body")
            (resumed.headers["Location"] ?: error("no Location")) to null
        }
    }

    private fun assertJarmPayloadShape(payload: JsonObject) {
        assertEquals(fixture.baseUrl, payload["iss"]?.jsonPrimitive?.content, "iss must equal AS issuer")
        assertEquals("oidf-op-basic", payload["aud"]?.jsonPrimitive?.content, "aud must equal client_id")
        assertNotNull(payload["exp"], "exp must be present")
        assertNotNull(payload["code"], "code must be present in JARM payload")
        assertNotNull(payload["state"], "state must round-trip in JARM payload")
    }

    private fun decodeJwsPayload(jws: String): JsonObject {
        val payloadSegment = jws.split(".").getOrNull(1) ?: error("JWS must have three segments: $jws")
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

    private fun extractFormInputValue(
        html: String,
        name: String,
    ): String? {
        val pattern = Regex("name=\"$name\"\\s+value=\"([^\"]*)\"")
        return pattern.find(html)?.groupValues?.getOrNull(1)
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}

private class JarmPkceFixture(
    seed: String,
) {
    val verifier: String = "oidf-op-conformance-pkce-verifier-fixture-2026-jarm-$seed"
    val challenge: String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray()))
}
