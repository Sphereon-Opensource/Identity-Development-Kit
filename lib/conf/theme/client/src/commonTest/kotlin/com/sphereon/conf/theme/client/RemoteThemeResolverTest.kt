/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.conf.theme.client

import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock

class RemoteThemeResolverTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun theme(variant: ThemeVariant): ResolvedTheme =
        ResolvedTheme(
            tokens =
                mapOf(
                    "color.primary" to if (variant == ThemeVariant.DARK) "#101820" else "#0055ff",
                    "branding.app.name" to "Acme",
                ),
            resolvedAt = Clock.System.now(),
            variant = variant,
            tenantId = "acme",
            etag = "\"etag-${variant.name.lowercase()}\"",
        )

    private fun resolver(
        engine: MockEngine,
        baseUrl: String? = "http://theme-service:8080",
        cache: ThemeClientCache = InMemoryThemeClientCache(),
    ): RemoteThemeResolver =
        RemoteThemeResolver(
            execution = TestSessionExecution(),
            httpClientFactory = TestHttpClientFactory(engine),
            configProvider = TestThemeClientConfigProvider(baseUrl),
            cache = cache,
        )

    @Test
    fun resolvesLightAndDarkVariantsRoundTrip() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("/api/theme/v1/acme/resolved", request.url.encodedPath)
                    assertEquals("app-1", request.url.parameters["applicationId"])
                    val variant = ThemeVariant.valueOf(request.url.parameters["variant"]!!)
                    respond(json.encodeToString(ResolvedTheme.serializer(), theme(variant)), headers = jsonHeaders)
                }
            val resolver = resolver(engine)

            val light = resolver.resolve(tenant = "acme", variant = ThemeVariant.LIGHT, applicationId = "app-1")
            val dark = resolver.resolve(tenant = "acme", variant = ThemeVariant.DARK, applicationId = "app-1")

            assertEquals(ThemeVariant.LIGHT, light.variant)
            assertEquals("#0055ff", light.tokens["color.primary"])
            assertEquals(ThemeVariant.DARK, dark.variant)
            assertEquals("#101820", dark.tokens["color.primary"])
            assertEquals("acme", light.tenantId)
        }

    @Test
    fun freshCacheEntryIsServedWithoutSecondHttpCall() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respond(
                        json.encodeToString(ResolvedTheme.serializer(), theme(ThemeVariant.LIGHT)),
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                                HttpHeaders.ETag to listOf("\"abc\""),
                            ),
                    )
                }
            val resolver = resolver(engine)

            val first = resolver.resolve(tenant = "acme", variant = ThemeVariant.LIGHT)
            val second = resolver.resolve(tenant = "acme", variant = ThemeVariant.LIGHT)

            assertEquals(1, engine.requestHistory.size)
            assertEquals(first.tokens, second.tokens)
        }

    @Test
    fun staleEntryRevalidatesWithIfNoneMatchAnd304ServesCachedBody() =
        runTest {
            val cachedTheme = theme(ThemeVariant.LIGHT)
            val cache = InMemoryThemeClientCache()
            cache.put(
                "theme|acme|LIGHT|",
                ThemeClientCacheEntry(
                    body = json.encodeToString(ResolvedTheme.serializer(), cachedTheme),
                    etag = "\"abc\"",
                    fetchedAtMs = 0L,
                ),
            )
            val engine =
                MockEngine { request ->
                    assertEquals("\"abc\"", request.headers[HttpHeaders.IfNoneMatch])
                    respond("", HttpStatusCode.NotModified)
                }
            val resolver = resolver(engine, cache = cache)

            val resolved = resolver.resolve(tenant = "acme", variant = ThemeVariant.LIGHT)

            assertEquals(1, engine.requestHistory.size)
            assertEquals(cachedTheme.tokens, resolved.tokens)
            assertEquals(ThemeVariant.LIGHT, resolved.variant)
        }

    @Test
    fun unknownTenant404PropagatesAsFailure() =
        runTest {
            val engine = MockEngine { _ -> respondError(HttpStatusCode.NotFound) }
            val resolver = resolver(engine)

            val failure =
                assertFailsWith<ThemeClientException> {
                    resolver.resolve(tenant = "nosuch", variant = ThemeVariant.LIGHT)
                }
            assertEquals(404, failure.statusCode)
        }

    @Test
    fun serverErrorPropagatesAsFailure() =
        runTest {
            val engine = MockEngine { _ -> respondError(HttpStatusCode.InternalServerError) }
            val resolver = resolver(engine)

            val failure =
                assertFailsWith<ThemeClientException> {
                    resolver.resolve(tenant = "acme")
                }
            assertEquals(500, failure.statusCode)
        }

    @Test
    fun unconfiguredBaseUrlFailsFastWithoutHttpCall() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respondError(HttpStatusCode.InternalServerError)
                }
            val resolver = resolver(engine, baseUrl = null)

            assertFailsWith<ThemeClientException> {
                resolver.resolve(tenant = "acme", variant = ThemeVariant.LIGHT)
            }
            assertTrue(engine.requestHistory.isEmpty())
        }
}
