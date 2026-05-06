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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies the `/.well-known/openid-configuration` and `jwks_uri` endpoints exposed by the
 * OIDF conformance OP harness. Mirrors the OIDF Basic-OP `OIDCCDiscoveryEndpoint` and
 * `OIDCCJwksUri` checkpoints: discovery must publish the issuer, the standard endpoints,
 * RS256 in `id_token_signing_alg_values_supported`, and the JWKS must expose at least one
 * `use=sig` RS256 RSA key with a non-empty kid.
 */
class OidfOpDiscoveryTest {
    private lateinit var fixture: OidfOpServerFixture
    private lateinit var client: HttpClient
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        fixture = OidfOpServerFixture()
        client = HttpClient(CIO)
    }

    @AfterTest
    fun tearDown() {
        client.close()
        fixture.stop()
    }

    @Test
    fun discoveryDocumentExposesRequiredFields() =
        runTest {
            val response = client.get("${fixture.baseUrl}/.well-known/openid-configuration")
            assertEquals(HttpStatusCode.OK, response.status, "discovery must return 200")

            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(
                fixture.baseUrl,
                body["issuer"]?.jsonPrimitive?.content,
                "issuer must match the harness base URL",
            )
            assertNotNull(body["authorization_endpoint"], "authorization_endpoint must be advertised")
            assertNotNull(body["token_endpoint"], "token_endpoint must be advertised")
            assertNotNull(body["jwks_uri"], "jwks_uri must be advertised")
            assertNotNull(body["userinfo_endpoint"], "userinfo_endpoint must be advertised")

            assertTrue(
                body["id_token_signing_alg_values_supported"]
                    ?.jsonArray
                    ?.map { it.jsonPrimitive.content }
                    ?.contains("RS256") == true,
                "discovery must advertise RS256 for id_token signing",
            )
            assertTrue(
                body["response_types_supported"]
                    ?.jsonArray
                    ?.map { it.jsonPrimitive.content }
                    ?.contains("code") == true,
                "discovery must advertise the code response type",
            )
            assertTrue(
                body["subject_types_supported"]
                    ?.jsonArray
                    ?.map { it.jsonPrimitive.content }
                    ?.contains("public") == true,
                "discovery must advertise the public subject type",
            )
            val grantTypes =
                body["grant_types_supported"]
                    ?.jsonArray
                    ?.map { it.jsonPrimitive.content }
                    ?: emptyList()
            assertTrue(grantTypes.contains("authorization_code"), "discovery must advertise authorization_code")
            assertTrue(grantTypes.contains("refresh_token"), "discovery must advertise refresh_token")
        }

    @Test
    fun jwksExposesPublicSigningKey() =
        runTest {
            val discovery =
                json
                    .parseToJsonElement(client.get("${fixture.baseUrl}/.well-known/openid-configuration").bodyAsText())
                    .jsonObject
            val jwksUri =
                discovery["jwks_uri"]?.jsonPrimitive?.content
                    ?: error("discovery must advertise a jwks_uri")

            val jwksResponse = client.get(jwksUri)
            assertEquals(HttpStatusCode.OK, jwksResponse.status, "JWKS endpoint must return 200")

            val jwks = json.parseToJsonElement(jwksResponse.bodyAsText()).jsonObject
            val keys = jwks["keys"] as? JsonArray ?: error("JWKS must include a keys array")
            assertTrue(keys.isNotEmpty(), "JWKS must publish at least one key")

            val signingKey =
                keys
                    .map { it.jsonObject }
                    .firstOrNull { it["use"]?.jsonPrimitive?.content == "sig" }
                    ?: error("JWKS must include a use=sig key")

            assertEquals("RS256", signingKey["alg"]?.jsonPrimitive?.content, "signing key alg must be RS256")
            assertEquals("RSA", signingKey["kty"]?.jsonPrimitive?.content, "signing key kty must be RSA")
            val kid = signingKey["kid"]?.jsonPrimitive?.content
            assertNotNull(kid, "signing key must carry a kid")
            assertTrue(kid.isNotEmpty(), "signing key kid must not be empty")
        }
}
