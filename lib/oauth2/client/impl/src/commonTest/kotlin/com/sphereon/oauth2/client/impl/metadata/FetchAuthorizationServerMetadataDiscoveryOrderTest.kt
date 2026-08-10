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

package com.sphereon.oauth2.client.impl.metadata

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.client.command.DiscoveryMode
import com.sphereon.oauth2.client.command.FetchServerMetadataArgs
import com.sphereon.oauth2.client.testutil.createOAuth2ClientTestAppGraph
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies WP3 [DiscoveryMode] controls the order in which well-known metadata
 * endpoints are tried. We stub the HTTP client via Ktor MockEngine and count which URL is hit
 * first.
 */
class FetchAuthorizationServerMetadataDiscoveryOrderTest {
    private val app = createOAuth2ClientTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId("discovery-order-test", principalType = com.sphereon.di.context.PrincipalType.USER)
    private val execution = session.asCoreApiServiceGraph().serviceExecution

    private val issuer = "https://as.example.com"
    private val oidcUrl = "$issuer/.well-known/openid-configuration"
    private val rfc8414Url = "$issuer/.well-known/oauth-authorization-server"

    private fun validMetadataJson(): String =
        """
        {
          "issuer": "$issuer",
          "authorization_endpoint": "$issuer/authorize",
          "token_endpoint": "$issuer/token",
          "jwks_uri": "$issuer/.well-known/jwks.json"
        }
        """.trimIndent()

    private fun buildFactoryRecording(attempts: MutableList<String>): HttpClientFactory {
        val mockEngine =
            MockEngine { request ->
                attempts += request.url.toString()
                respond(
                    content = validMetadataJson(),
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

    @Test
    fun discovery_oidcFirst_fetchesOpenIdConfigurationFirst() =
        runTest {
            val attempts = mutableListOf<String>()
            val command =
                FetchAuthorizationServerMetadataCommandImpl(
                    execution = execution,
                    httpClientFactory = buildFactoryRecording(attempts),
                )

            val result =
                command.execute(
                    FetchServerMetadataArgs(issuer = issuer, discoveryMode = DiscoveryMode.OIDC_FIRST),
                )
            assertTrue(result.isOk, "expected success, got ${if (result.isErr) result.error else ""}")
            assertTrue(attempts.isNotEmpty(), "at least one URL should have been attempted")
            assertEquals(oidcUrl, attempts.first(), "OIDC_FIRST must hit openid-configuration first")
        }

    @Test
    fun discovery_oauth2First_fetchesOauthAuthorizationServerFirst() =
        runTest {
            val attempts = mutableListOf<String>()
            val command =
                FetchAuthorizationServerMetadataCommandImpl(
                    execution = execution,
                    httpClientFactory = buildFactoryRecording(attempts),
                )

            val result =
                command.execute(
                    FetchServerMetadataArgs(issuer = issuer, discoveryMode = DiscoveryMode.OAUTH2_FIRST),
                )
            assertTrue(result.isOk)
            assertEquals(
                rfc8414Url,
                attempts.first(),
                "OAUTH2_FIRST must hit oauth-authorization-server first",
            )
        }

    @Test
    fun discovery_oidcFirst_fallsBackWhenOpenIdConfigMissing() =
        runTest {
            val attempts = mutableListOf<String>()
            val mockEngine =
                MockEngine { request ->
                    attempts += request.url.toString()
                    if (request.url.toString().contains("openid-configuration")) {
                        respond("", HttpStatusCode.NotFound)
                    } else {
                        respond(
                            validMetadataJson(),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                }
            val factory =
                object : HttpClientFactory {
                    override fun createClient(options: HttpClientOptions): HttpClient =
                        HttpClient(mockEngine) {
                            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                        }

                    override fun isSupportedOptions(options: HttpClientOptions): Boolean = true

                    override fun getEngineTypesSupported(): List<HttpClientEngineType> = emptyList()

                    override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
                }
            val command =
                FetchAuthorizationServerMetadataCommandImpl(
                    execution = execution,
                    httpClientFactory = factory,
                )

            val result =
                command.execute(
                    FetchServerMetadataArgs(issuer = issuer, discoveryMode = DiscoveryMode.OIDC_FIRST),
                )
            assertTrue(result.isOk, "expected fallback to succeed")
            assertTrue(attempts.size >= 2, "must have attempted at least 2 URLs after OIDC miss")
            assertEquals(oidcUrl, attempts.first())
            assertTrue(
                attempts.any { it == rfc8414Url },
                "must have fallen through to RFC 8414 endpoint, got $attempts",
            )
        }

    @Test
    fun discovery_defaultIsOauth2First() =
        runTest {
            val attempts = mutableListOf<String>()
            val command =
                FetchAuthorizationServerMetadataCommandImpl(
                    execution = execution,
                    httpClientFactory = buildFactoryRecording(attempts),
                )

            // Caller doesn't pass discoveryMode — default is OAUTH2_FIRST (backward-compatible).
            val result = command.execute(FetchServerMetadataArgs(issuer = issuer))
            assertTrue(result.isOk)
            assertEquals(rfc8414Url, attempts.first())
        }

    @Test
    fun issuerMatch_acceptsTrailingSlashOnEitherSide() =
        runTest {
            // The OIDF conformance suite publishes its AS metadata issuer WITH a trailing slash
            // while credential offers carry the bare URL; the match must tolerate both directions.
            val mockEngine =
                MockEngine {
                    respond(
                        content =
                            """
                            {
                              "issuer": "$issuer/",
                              "authorization_endpoint": "$issuer/authorize",
                              "token_endpoint": "$issuer/token",
                              "jwks_uri": "$issuer/.well-known/jwks.json"
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val factory =
                object : HttpClientFactory {
                    override fun createClient(options: HttpClientOptions): HttpClient =
                        HttpClient(mockEngine) {
                            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                        }

                    override fun isSupportedOptions(options: HttpClientOptions): Boolean = true

                    override fun getEngineTypesSupported(): List<HttpClientEngineType> = emptyList()

                    override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
                }
            val command =
                FetchAuthorizationServerMetadataCommandImpl(
                    execution = execution,
                    httpClientFactory = factory,
                )

            val result = command.execute(FetchServerMetadataArgs(issuer = issuer))
            assertTrue(result.isOk, "trailing-slash issuer must not be rejected, got ${if (result.isErr) result.error else ""}")
            assertEquals("$issuer/", result.value.issuer)
        }
}
