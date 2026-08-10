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

package com.sphereon.oauth2.client.impl.token

import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.client.command.ExchangeTokenArgs
import com.sphereon.oauth2.client.testutil.createOAuth2ClientTestAppGraph
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.TokenRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies the on-the-wire shape of the OAuth 2.0 token request for each
 * `tokenEndpointAuthMethod` selection. OIDF basic conformance forces the RP to use
 * `client_secret_basic`; the suite's `OIDCCValidateClientAuthenticationWithClientSecretBasic`
 * assertion fails the run when credentials appear in the body or the Authorization header is
 * missing. These tests pin the contract.
 */
class ExchangeTokenCommandClientAuthShapeTest {
    private val app = createOAuth2ClientTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId("exchange-token-auth-shape-test", principalType = com.sphereon.di.context.PrincipalType.USER)
    private val execution = session.asCoreApiServiceGraph().serviceExecution

    private val tokenEndpoint = "https://as.example.com/token"
    private val clientId = "test-client"
    private val clientSecret = "test-secret"

    private fun successJson(): String =
        """
        {
          "access_token": "at-123",
          "token_type": "Bearer",
          "expires_in": 3600
        }
        """.trimIndent()

    private fun captureRequest(captured: MutableList<HttpRequestData>): HttpClientFactory {
        val mockEngine =
            MockEngine { request ->
                captured += request
                respond(
                    content = successJson(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        return object : HttpClientFactory {
            override fun createClient(options: HttpClientOptions): HttpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }

            override fun isSupportedOptions(options: HttpClientOptions): Boolean = true

            override fun getEngineTypesSupported(): List<HttpClientEngineType> = emptyList()

            override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
        }
    }

    private suspend fun bodyText(request: HttpRequestData): String =
        when (val content = request.body) {
            is TextContent -> {
                content.text
            }

            is OutgoingContent.ByteArrayContent -> {
                content.bytes().decodeToString()
            }

            is OutgoingContent.ReadChannelContent -> {
                val channel: ByteReadChannel = content.readFrom()
                channel.readRemaining().readByteArray().decodeToString()
            }

            else -> {
                error("unsupported OutgoingContent variant: ${content::class.simpleName}")
            }
        }

    private fun parseFormBody(body: String): Map<String, List<String>> {
        if (body.isEmpty()) return emptyMap()
        val pairs = body.split("&")
        val map = mutableMapOf<String, MutableList<String>>()
        for (pair in pairs) {
            val idx = pair.indexOf('=')
            val key = if (idx >= 0) pair.substring(0, idx) else pair
            val value = if (idx >= 0) urlDecode(pair.substring(idx + 1)) else ""
            map.getOrPut(key) { mutableListOf() }.add(value)
        }
        return map
    }

    private fun urlDecode(value: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '+' -> {
                    out.append(' ')
                    i++
                }

                c == '%' && i + 2 < value.length -> {
                    val hex = value.substring(i + 1, i + 3)
                    out.append(hex.toInt(HEX_RADIX).toChar())
                    i += 3
                }

                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    private suspend fun runExchange(authMethod: ClientAuthenticationMethod?): HttpRequestData {
        val captured = mutableListOf<HttpRequestData>()
        val command =
            ExchangeTokenCommandImpl(
                execution = execution,
                httpClientFactory = captureRequest(captured),
            )
        val result =
            command.execute(
                ExchangeTokenArgs(
                    tokenEndpoint = tokenEndpoint,
                    request =
                        TokenRequest(
                            grantType = "authorization_code",
                            code = "auth-code-123",
                            redirectUri = "https://rp.example.com/callback",
                            codeVerifier = "test-code-verifier-12345678901234567890123456789012",
                            clientId = clientId,
                            clientSecret = clientSecret,
                            tokenEndpointAuthMethod = authMethod,
                        ),
                ),
            )
        assertTrue(result.isOk, "exchange should succeed against mock; got ${if (result.isErr) result.error else ""}")
        assertEquals(1, captured.size, "exactly one HTTP request expected")
        return captured.single()
    }

    @Test
    fun clientSecretBasicEmitsAuthorizationHeaderAndOmitsBodyCredentials() =
        runTest {
            val request = runExchange(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)

            val authHeader = request.headers[HttpHeaders.Authorization]
            assertNotNull(authHeader, "Authorization header must be present for client_secret_basic")
            assertTrue(authHeader.startsWith("Basic "), "expected Basic scheme, got: $authHeader")

            val decoded = authHeader.removePrefix("Basic ").decodeFromBase64().decodeToString()
            assertEquals("$clientId:$clientSecret", decoded, "Basic credentials must be base64(URLEncode(id):URLEncode(secret))")

            val body = parseFormBody(bodyText(request))
            assertFalse(body.containsKey("client_id"), "client_id must not appear in body when using client_secret_basic")
            assertFalse(body.containsKey("client_secret"), "client_secret must not appear in body when using client_secret_basic")
            assertEquals("authorization_code", body["grant_type"]?.single())
            assertEquals("auth-code-123", body["code"]?.single())
        }

    @Test
    fun clientSecretPostKeepsCredentialsInBody() =
        runTest {
            val request = runExchange(ClientAuthenticationMethod.CLIENT_SECRET_POST)

            assertNull(request.headers[HttpHeaders.Authorization], "Authorization header must be absent for client_secret_post")

            val body = parseFormBody(bodyText(request))
            assertEquals(clientId, body["client_id"]?.single())
            assertEquals(clientSecret, body["client_secret"]?.single())
        }

    @Test
    fun nullAuthMethodDefaultsToPost() =
        runTest {
            val request = runExchange(authMethod = null)

            assertNull(request.headers[HttpHeaders.Authorization], "default behaviour must not emit Authorization header")

            val body = parseFormBody(bodyText(request))
            assertEquals(clientId, body["client_id"]?.single())
            assertEquals(clientSecret, body["client_secret"]?.single())
        }

    @Test
    fun noneAuthMethodSendsClientIdOnly() =
        runTest {
            val request = runExchange(ClientAuthenticationMethod.NONE)

            assertNull(request.headers[HttpHeaders.Authorization], "Authorization header must be absent for NONE")

            val body = parseFormBody(bodyText(request))
            assertEquals(clientId, body["client_id"]?.single())
            assertFalse(body.containsKey("client_secret"), "client_secret must not appear for NONE")
        }

    @Test
    fun badRequestUseDpopNonceReturnsDpopNonceRequired() =
        runTest {
            val dpopNonce = "nonce-from-as"
            val mockEngine =
                MockEngine {
                    respond(
                        content =
                            """
                            {
                              "error": "use_dpop_nonce",
                              "error_description": "Authorization server requires nonce in DPoP proof"
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.BadRequest,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                                "DPoP-Nonce" to listOf(dpopNonce),
                            ),
                    )
                }
            val command =
                ExchangeTokenCommandImpl(
                    execution = execution,
                    httpClientFactory =
                        object : HttpClientFactory {
                            override fun createClient(options: HttpClientOptions): HttpClient =
                                HttpClient(mockEngine) {
                                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                                }

                            override fun isSupportedOptions(options: HttpClientOptions): Boolean = true

                            override fun getEngineTypesSupported(): List<HttpClientEngineType> = emptyList()

                            override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
                        },
                )

            val result =
                command.execute(
                    ExchangeTokenArgs(
                        tokenEndpoint = tokenEndpoint,
                        request =
                            TokenRequest(
                                grantType = "urn:ietf:params:oauth:grant-type:pre-authorized_code",
                                preAuthorizedCode = "pre-auth-code",
                                dpop = "dpop-proof",
                            ),
                    ),
                )

            assertTrue(result.isErr, "nonce challenge must be returned as an error")
            assertEquals("use_dpop_nonce", result.error.code)
            assertEquals(dpopNonce, result.error.meta["dpop_nonce"])
        }

    @Test
    fun additionalHeadersAndParametersAreSent() =
        runTest {
            val captured = mutableListOf<HttpRequestData>()
            val command =
                ExchangeTokenCommandImpl(
                    execution = execution,
                    httpClientFactory = captureRequest(captured),
                )

            val result =
                command.execute(
                    ExchangeTokenArgs(
                        tokenEndpoint = tokenEndpoint,
                        request =
                            TokenRequest(
                                grantType = "urn:ietf:params:oauth:grant-type:pre-authorized_code",
                                preAuthorizedCode = "pre-auth-code",
                                additionalHeaders = mapOf("OAuth-Client-Attestation" to "attestation-jwt"),
                                additionalParameters = mapOf("custom_auth_param" to kotlinx.serialization.json.JsonPrimitive("custom-value")),
                            ),
                    ),
                )

            assertTrue(result.isOk, "exchange should succeed against mock; got ${if (result.isErr) result.error else ""}")
            val request = captured.single()
            assertEquals("attestation-jwt", request.headers["OAuth-Client-Attestation"])
            val body = parseFormBody(bodyText(request))
            assertEquals("custom-value", body["custom_auth_param"]?.single())
        }

    companion object {
        private const val HEX_RADIX = 16
    }
}
