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

import com.sphereon.conf.theme.core.model.AssetElementValue
import com.sphereon.conf.theme.core.model.ElementOrigin
import com.sphereon.conf.theme.core.model.ProductType
import com.sphereon.conf.theme.core.model.TextElementValue
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.model.ThemeAssetReference
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteFeatureResolverTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    /**
     * The representation the theme service actually serves, written out literally rather than
     * re-encoded here. `ElementValue` is a sealed hierarchy and the theme API pins its
     * discriminator on `kind`; a fixture produced by a `Json` this test configures itself would
     * agree with the client by construction and could never catch the client reading a
     * different key than the server writes.
     */
    private fun loginFeatureWireFormat(applicationId: String? = null): String {
        val application = applicationId?.let { """"applicationId":"$it",""" } ?: ""
        return """
            {"productType":"AUTHORIZATION_SERVER","featureId":"login","tenantId":"acme",$application
             "elements":{
               "logo":{"value":{"kind":"asset","asset":{"uri":"/public/assets/acme/brand/logo.svg"}},"origin":"TENANT"},
               "tagline":{"value":{"kind":"text","text":"Sign in to Acme"},"origin":"ELEMENT_DEFAULT"}
             }}
        """.trimIndent()
    }

    private fun resolver(
        engine: MockEngine,
        baseUrl: String? = "http://theme-service:8080",
    ): RemoteFeatureResolver =
        RemoteFeatureResolver(
            execution = TestSessionExecution(),
            httpClientFactory = TestHttpClientFactory(engine),
            configProvider = TestThemeClientConfigProvider(baseUrl),
            cache = InMemoryThemeClientCache(),
        )

    @Test
    fun resolvesApplicationScopedFeature() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("/api/theme/v1/acme/applications/app-1/features/login/resolved", request.url.encodedPath)
                    assertNull(request.url.parameters["variant"])
                    respond(
                        loginFeatureWireFormat(applicationId = "app-1"),
                        headers = jsonHeaders,
                    )
                }

            val resolved =
                resolver(engine).resolve(
                    tenant = "acme",
                    productType = ProductType.AUTHORIZATION_SERVER,
                    featureId = "login",
                    applicationId = "app-1",
                )

            assertNotNull(resolved)
            assertEquals("login", resolved.featureId)
            assertEquals("app-1", resolved.applicationId)
            assertEquals(
                AssetElementValue(ThemeAssetReference(uri = "/public/assets/acme/brand/logo.svg")),
                resolved.elements["logo"]?.value,
            )
            assertEquals(TextElementValue("Sign in to Acme"), resolved.elements["tagline"]?.value)
            assertEquals(ElementOrigin.TENANT, resolved.elements["logo"]?.origin)
        }

    @Test
    fun resolvesProductLevelFeatureWithVariant() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(
                        "/api/theme/v1/acme/products/AUTHORIZATION_SERVER/features/login/resolved",
                        request.url.encodedPath,
                    )
                    assertEquals("DARK", request.url.parameters["variant"])
                    respond(loginFeatureWireFormat(), headers = jsonHeaders)
                }

            val resolved =
                resolver(engine).resolve(
                    tenant = "acme",
                    productType = ProductType.AUTHORIZATION_SERVER,
                    featureId = "login",
                    variant = ThemeVariant.DARK,
                )

            assertNotNull(resolved)
            assertEquals(ProductType.AUTHORIZATION_SERVER, resolved.productType)
        }

    @Test
    fun unknownFeature404ResolvesToNull() =
        runTest {
            val engine = MockEngine { _ -> respondError(HttpStatusCode.NotFound) }

            val resolved =
                resolver(engine).resolve(
                    tenant = "acme",
                    productType = ProductType.AUTHORIZATION_SERVER,
                    featureId = "nosuch",
                )

            assertNull(resolved)
        }

    @Test
    fun serverErrorPropagatesAsFailure() =
        runTest {
            val engine = MockEngine { _ -> respondError(HttpStatusCode.BadGateway) }

            val failure =
                assertFailsWith<ThemeClientException> {
                    resolver(engine).resolve(
                        tenant = "acme",
                        productType = ProductType.AUTHORIZATION_SERVER,
                        featureId = "login",
                    )
                }
            assertEquals(502, failure.statusCode)
        }

    @Test
    fun unconfiguredBaseUrlFailsFastWithoutHttpCall() =
        runTest {
            val engine = MockEngine { _ -> respondError(HttpStatusCode.InternalServerError) }
            val resolver = resolver(engine, baseUrl = null)

            assertFailsWith<ThemeClientException> {
                resolver.resolve(
                    tenant = "acme",
                    productType = ProductType.AUTHORIZATION_SERVER,
                    featureId = "login",
                )
            }
            assertTrue(engine.requestHistory.isEmpty())
        }
}
