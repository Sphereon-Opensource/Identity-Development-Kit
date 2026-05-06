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
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 9101 (JAR) "Server" role conformance: discovery advertises JAR support, the AS rejects
 * malformed / unsigned / both-present `request` parameters, and the response is a 400 (or
 * post-redirect error) per RFC 9101 §5.
 *
 * The full happy-path "AS verifies signed request object" flow requires an RSA-keyed test client
 * with registered JWKS, which is wired separately in the OIDF JAR conformance suite (the
 * `OIDCCRequestObject*` plan). This focused harness covers the discovery + reject-on-bad-input
 * surface so that regressions to the JAR rejection layer are caught at the IDK level.
 */
class OidfOpJarTest {
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
    fun discoveryAdvertisesJarSupport() =
        runTest {
            val response = client.get("${fixture.baseUrl}/.well-known/openid-configuration")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject

            assertEquals(
                true,
                body["request_parameter_supported"]?.jsonPrimitive?.content?.toBoolean(),
                "discovery must advertise request_parameter_supported=true when JAR is enabled",
            )
            assertEquals(
                true,
                body["request_uri_parameter_supported"]?.jsonPrimitive?.content?.toBoolean(),
                "discovery must advertise request_uri_parameter_supported=true when JAR is enabled",
            )

            val advertisedAlgs =
                body["request_object_signing_alg_values_supported"]
                    ?.jsonArray
                    ?.map { it.jsonPrimitive.content }
            assertNotNull(advertisedAlgs, "request_object_signing_alg_values_supported must be advertised")
            assertTrue(
                advertisedAlgs.contains("RS256"),
                "request_object_signing_alg_values_supported must include RS256, got: $advertisedAlgs",
            )

            // require_request_uri_registration is only emitted when the AS demands registration.
            // The fixture leaves it false so the field MUST be absent or false.
            val required =
                body["require_request_uri_registration"]
                    ?.jsonPrimitive
                    ?.content
                    ?.toBooleanStrictOrNull()
            assertTrue(required != true, "require_request_uri_registration must not be true with default config")
        }

    @Test
    fun authorizeRejectsRequestAndRequestUriBothPresent() =
        runTest {
            val state = "jar-state-1"
            val fakeJwt = "eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJjbGllbnQifQ.sig"
            val location =
                "${fixture.baseUrl}/authorize?client_id=oidf-op-basic" +
                    "&request=${URLEncoder.encode(fakeJwt, "UTF-8")}" +
                    "&request_uri=${URLEncoder.encode("https://example.com/jar", "UTF-8")}" +
                    "&response_type=code&scope=openid&state=$state"
            val response = client.get(location)
            assertTrue(
                response.status.value in setOf(302, 400),
                "RFC 9101 §5: request + request_uri together must be rejected; got ${response.status}",
            )
            // Pre-redirect body OR redirect-encoded error code must reflect the violation.
            val body = response.bodyAsText().lowercase()
            assertTrue(
                "invalid_request" in body || "invalid_request" in (response.headers["Location"] ?: ""),
                "Response must surface invalid_request; got body='$body', location='${response.headers["Location"]}'",
            )
        }

    @Test
    fun authorizeRejectsUnsignedRequestObject() =
        runTest {
            val unsignedJwt =
                buildAlgNoneJwt(
                    clientId = "oidf-op-basic",
                    aud = fixture.baseUrl,
                )
            val state = "jar-state-2"
            val location =
                "${fixture.baseUrl}/authorize?client_id=oidf-op-basic" +
                    "&request=${URLEncoder.encode(unsignedJwt, "UTF-8")}" +
                    "&response_type=code&scope=openid&state=$state" +
                    "&redirect_uri=${URLEncoder.encode("http://localhost:8080/test-callback", "UTF-8")}"
            val response = client.get(location)

            // RFC 9101 §6 + RFC 8725 §2.1: alg=none is forbidden. Either pre-redirect 400 or
            // post-redirect error=invalid_request_object are acceptable; both fail the request.
            assertTrue(
                response.status.value in setOf(302, 400),
                "alg=none JAR must be rejected; got ${response.status}",
            )
            val combined =
                response.bodyAsText().lowercase() + " " +
                    (response.headers["Location"] ?: "").lowercase()
            assertTrue(
                "invalid_request_object" in combined ||
                    "invalid_request" in combined,
                "Response must surface invalid_request_object (or invalid_request) for alg=none JAR; got: $combined",
            )
        }

    /**
     * Builds an unsigned JWT (`alg=none`, empty signature) carrying minimal authorization-request
     * claims. Used to verify the AS refuses unprotected JARs, per RFC 9101 §6.
     */
    private fun buildAlgNoneJwt(
        clientId: String,
        aud: String,
    ): String {
        val nowSeconds = (System.currentTimeMillis() / 1000)
        val header = """{"alg":"none","typ":"oauth-authz-req+jwt"}"""
        val payload =
            """{"iss":"$clientId","aud":"$aud","client_id":"$clientId","response_type":"code",""" +
                """"scope":"openid","exp":${nowSeconds + 300},"iat":$nowSeconds,"jti":"jar-fixture"}"""
        val b64 = Base64.getUrlEncoder().withoutPadding()
        return b64.encodeToString(header.encodeToByteArray()) +
            "." +
            b64.encodeToString(payload.encodeToByteArray()) +
            "."
    }
}
