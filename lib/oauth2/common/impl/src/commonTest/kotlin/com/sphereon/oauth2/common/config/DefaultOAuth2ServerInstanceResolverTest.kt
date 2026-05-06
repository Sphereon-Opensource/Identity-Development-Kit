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

package com.sphereon.oauth2.common.config

import com.sphereon.core.api.http.GenericHttpRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultOAuth2ServerInstanceResolverTest {
    private fun request(
        path: String = "/authorize",
        host: String = "as.example.com",
        scheme: String = "https",
    ): GenericHttpRequest =
        GenericHttpRequest(
            method = "GET",
            path = path,
            headers =
                mapOf(
                    "Host" to host,
                    "X-Forwarded-Proto" to scheme,
                ),
        )

    private fun providerOf(config: OAuth2ServersConfig): OAuth2ServersConfigProvider =
        object : OAuth2ServersConfigProvider {
            override fun getConfig(): OAuth2ServersConfig = config

            override fun getServer(id: String): OAuth2ServerInstanceConfig? = config.getServer(id)

            override fun getDefaultServer(): OAuth2ServerInstanceConfig = config.getDefaultServer()

            override fun resolveIssuer(
                serverId: String,
                tenantId: String,
            ): String =
                config.getServer(serverId)?.let { server ->
                    server.issuer
                        ?: server.issuerTemplate?.replace("{tenant-id}", tenantId)
                        ?: error("server '$serverId' has no issuer or issuerTemplate")
                } ?: error("server '$serverId' not found")
        }

    @Test
    fun singleAsReturnsThatIdRegardlessOfRequestUrl() =
        runTest {
            val provider =
                providerOf(
                    OAuth2ServersConfig(
                        defaultServer = "only",
                        servers =
                            mapOf(
                                "only" to OAuth2ServerInstanceConfig(issuer = "https://unrelated.example.com"),
                            ),
                    ),
                )
            val resolver = DefaultOAuth2ServerInstanceResolver(provider)

            val result = resolver.resolve(request(host = "totally-different.example.com"))

            assertTrue(result.isOk)
            assertEquals("only", result.value)
        }

    @Test
    fun multiAsMatchesByIssuerPrefix() =
        runTest {
            val provider =
                providerOf(
                    OAuth2ServersConfig(
                        defaultServer = "primary",
                        servers =
                            mapOf(
                                "primary" to
                                    OAuth2ServerInstanceConfig(
                                        issuer = "https://primary.example.com",
                                    ),
                                "secondary" to
                                    OAuth2ServerInstanceConfig(
                                        issuer = "https://secondary.example.com",
                                    ),
                            ),
                    ),
                )
            val resolver = DefaultOAuth2ServerInstanceResolver(provider)

            val result = resolver.resolve(request(host = "secondary.example.com", path = "/authorize"))

            assertTrue(result.isOk)
            assertEquals("secondary", result.value)
        }

    @Test
    fun multiAsMatchesByIssuerTemplateAfterSubstitution() =
        runTest {
            val provider =
                providerOf(
                    OAuth2ServersConfig(
                        defaultServer = "tenanted",
                        servers =
                            mapOf(
                                "tenanted" to
                                    OAuth2ServerInstanceConfig(
                                        issuerTemplate = "https://{host}/auth/{tenant-id}",
                                    ),
                                "other" to
                                    OAuth2ServerInstanceConfig(
                                        issuer = "https://other.example.com",
                                    ),
                            ),
                    ),
                )
            val resolver = DefaultOAuth2ServerInstanceResolver(provider)

            val result =
                resolver.resolve(
                    request(host = "auth.example.com", path = "/auth/tenant42/.well-known/openid-configuration"),
                )

            assertTrue(result.isOk)
            assertEquals("tenanted", result.value)
        }

    @Test
    fun multiAsFallsBackToDefaultServerWhenNothingMatches() =
        runTest {
            val provider =
                providerOf(
                    OAuth2ServersConfig(
                        defaultServer = "primary",
                        servers =
                            mapOf(
                                "primary" to
                                    OAuth2ServerInstanceConfig(
                                        issuer = "https://primary.example.com",
                                    ),
                                "secondary" to
                                    OAuth2ServerInstanceConfig(
                                        issuer = "https://secondary.example.com",
                                    ),
                            ),
                    ),
                )
            val resolver = DefaultOAuth2ServerInstanceResolver(provider)

            val result = resolver.resolve(request(host = "totally-unrelated.example.com"))

            assertTrue(result.isOk)
            assertEquals("primary", result.value)
        }

    @Test
    fun multiAsErrorsWhenNoMatchAndDefaultIsNotInServersMap() =
        runTest {
            val provider =
                providerOf(
                    OAuth2ServersConfig(
                        defaultServer = "missing",
                        servers =
                            mapOf(
                                "primary" to
                                    OAuth2ServerInstanceConfig(
                                        issuer = "https://primary.example.com",
                                    ),
                                "secondary" to
                                    OAuth2ServerInstanceConfig(
                                        issuer = "https://secondary.example.com",
                                    ),
                            ),
                    ),
                )
            val resolver = DefaultOAuth2ServerInstanceResolver(provider)

            val result = resolver.resolve(request(host = "totally-unrelated.example.com"))

            assertTrue(result.isErr)
            assertEquals("NOT_FOUND_ERROR", result.error.code)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("totally-unrelated.example.com"),
                "error should mention the request URL",
            )
            assertTrue(
                result.error.message.defaultMessage
                    .contains("primary") &&
                    result.error.message.defaultMessage
                        .contains("secondary"),
                "error should mention configured server ids",
            )
        }
}
