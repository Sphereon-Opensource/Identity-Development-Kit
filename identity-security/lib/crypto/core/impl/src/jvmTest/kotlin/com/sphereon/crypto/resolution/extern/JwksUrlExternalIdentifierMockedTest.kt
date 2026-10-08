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

package com.sphereon.crypto.resolution.extern

import com.sphereon.core.api.cache.CacheManager
import com.sphereon.core.api.cache.ScopedCache
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.di.context.createAnonymousSessionContext
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.ktor.http.client.provider.UrlValidationPolicy
import com.sphereon.ktor.http.client.provider.withCounterpartyEgress
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for JwksUrlExternalIdentifierResolutionServiceImpl using mocks
 * to cover error path branches that are hard to reach with integration tests.
 */
class JwksUrlExternalIdentifierMockedTest {
    private val mockSessionContext = createAnonymousSessionContext("mock-jwks-url-test", "mock-jwks-url-test-correlation")

    // ========================================================================
    // Branch Coverage Tests for HTTP Client Creation Failure
    // ========================================================================

    @Test
    fun testResolveFailsWhenHttpClientCreationThrows() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockHttpClientFactory = mockk<HttpClientFactory>()
            every { mockHttpClientFactory.createClient(any<HttpClientOptions>()) } throws RuntimeException("Failed to create client")

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = mockHttpClientFactory,
                )

            val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json")
            val result = service.resolve(opts)

            assertTrue(result.isErr, "Should fail when HTTP client creation throws")
            assertTrue(
                result.error.message.defaultMessage
                    .contains("Failed to create HTTP client"),
                "Error message should mention HTTP client creation failure",
            )
        }

    @Test
    fun testHolderCallFetchesJwksUnderTheCounterpartyEgressRuleAndServerCallDoesNot() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext
            val policies = mutableListOf<UrlValidationPolicy?>()
            val mockHttpClientFactory = mockk<HttpClientFactory>()
            every { mockHttpClientFactory.createClient(any<HttpClientOptions>()) } answers {
                val options = firstArg<HttpClientOptions>()
                policies += options.urlValidation
                val client = HttpClient(MockEngine { _ -> respond(content = "Not Found", status = HttpStatusCode.NotFound) })
                options.urlValidation?.let { policy ->
                    client.plugin(HttpSend).intercept { request ->
                        policy.validate(request.url.build())
                        execute(request)
                    }
                }
                client
            }
            val service = JwksUrlExternalIdentifierResolutionServiceImpl(execution = mockExecution, httpClientFactory = mockHttpClientFactory)

            val holderResult = withCounterpartyEgress { service.resolve(ExternalIdentifierJwksUrlOpts(identifier = "https://127.0.0.1/jwks.json")) }
            service.resolve(ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json"))

            assertTrue(holderResult.isErr, "a holder call must refuse a loopback jwks_uri")
            assertEquals(listOf<UrlValidationPolicy?>(UrlValidationPolicy.COUNTERPARTY_EGRESS, null), policies.take(2))
        }

    // ========================================================================
    // Branch Coverage Tests for HTTP Response Status Codes
    // ========================================================================

    @Test
    fun testResolveFailsWhenHttpResponseIsNotOk() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = "Not Found",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                    )
                }

            val mockHttpClientFactory = mockk<HttpClientFactory>()
            every { mockHttpClientFactory.createClient(any<HttpClientOptions>()) } answers {
                val options = firstArg<HttpClientOptions>()
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                    options.additionalConfig?.invoke(this)
                }
            }

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = mockHttpClientFactory,
                )

            val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json")
            val result = service.resolve(opts)

            assertTrue(result.isErr, "Should fail when HTTP response is not OK")
            assertTrue(
                result.error.message.defaultMessage
                    .contains("HTTP 404"),
                "Error message should mention HTTP status code",
            )
        }

    @Test
    fun testResolveFailsWhenHttpResponseIs500() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = "Internal Server Error",
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                    )
                }

            val mockHttpClientFactory = mockk<HttpClientFactory>()
            every { mockHttpClientFactory.createClient(any<HttpClientOptions>()) } answers {
                val options = firstArg<HttpClientOptions>()
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                    options.additionalConfig?.invoke(this)
                }
            }

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = mockHttpClientFactory,
                )

            val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json")
            val result = service.resolve(opts)

            assertTrue(result.isErr, "Should fail when HTTP response is 500")
            assertTrue(
                result.error.message.defaultMessage
                    .contains("HTTP 500"),
                "Error message should mention HTTP status code 500",
            )
        }

    // ========================================================================
    // Branch Coverage Tests for Invalid JSON Response
    // ========================================================================

    @Test
    fun testResolveFailsWhenJwksJsonIsInvalid() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = "not valid json {{{",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val mockHttpClientFactory = mockk<HttpClientFactory>()
            every { mockHttpClientFactory.createClient(any<HttpClientOptions>()) } answers {
                val options = firstArg<HttpClientOptions>()
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                    options.additionalConfig?.invoke(this)
                }
            }

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = mockHttpClientFactory,
                )

            val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json")
            val result = service.resolve(opts)

            assertTrue(result.isErr, "Should fail when JWKS JSON is invalid")
            assertTrue(
                result.error.message.defaultMessage
                    .contains("Invalid JWKS JSON"),
                "Error message should mention invalid JWKS JSON",
            )
        }

    // ========================================================================
    // Branch Coverage Tests for Empty Keys Array
    // ========================================================================

    @Test
    fun testResolveFailsWhenJwksHasNoKeys() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val emptyJwks = """{"keys":[]}"""

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = emptyJwks,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val mockHttpClientFactory = mockk<HttpClientFactory>()
            every { mockHttpClientFactory.createClient(any<HttpClientOptions>()) } answers {
                val options = firstArg<HttpClientOptions>()
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                    options.additionalConfig?.invoke(this)
                }
            }

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = mockHttpClientFactory,
                )

            val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json")
            val result = service.resolve(opts)

            assertTrue(result.isErr, "Should fail when JWKS has no keys")
            assertTrue(
                result.error.message.defaultMessage
                    .contains("contains no keys"),
                "Error message should mention no keys",
            )
        }

    // ========================================================================
    // Branch Coverage Tests for Kid Not Found
    // ========================================================================

    @Test
    fun testResolveFailsWhenRequestedKidNotFound() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val jwksWithKeys = """{
            "keys": [
                {
                    "kty": "EC",
                    "crv": "P-256",
                    "x": "WbbFpp0eS8_rJlvpuX_qEyU1J2PNmXYnqPCBJTqqiBA",
                    "y": "F8kbfVPRQc5M9kJA1fy3c_0Q6vCqHy1X7CZQC6XQy9I",
                    "kid": "key-1"
                }
            ]
        }"""

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = jwksWithKeys,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val mockHttpClientFactory = mockk<HttpClientFactory>()
            every { mockHttpClientFactory.createClient(any<HttpClientOptions>()) } returns httpClient

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = mockHttpClientFactory,
                )

            val opts =
                ExternalIdentifierJwksUrlOpts(
                    identifier = "https://example.com/.well-known/jwks.json",
                    lookup =
                        com.sphereon.crypto.resolution
                            .AdditionalIdentifierLookup(kid = "non-existent-kid"),
                )
            val result = service.resolve(opts)

            assertTrue(result.isErr, "Should fail when requested kid is not found")
            assertTrue(
                result.error.message.defaultMessage
                    .contains("No key with kid 'non-existent-kid'"),
                "Error message should mention the missing kid",
            )
        }

    // ========================================================================
    // Branch Coverage Tests for Success Paths
    // ========================================================================

    @Test
    fun testResolveSucceedsWithMatchingKid() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val jwksWithKeys = """{
            "keys": [
                {
                    "kty": "EC",
                    "crv": "P-256",
                    "x": "WbbFpp0eS8_rJlvpuX_qEyU1J2PNmXYnqPCBJTqqiBA",
                    "y": "F8kbfVPRQc5M9kJA1fy3c_0Q6vCqHy1X7CZQC6XQy9I",
                    "kid": "key-1"
                },
                {
                    "kty": "EC",
                    "crv": "P-256",
                    "x": "AHzxbLBCZH-aMj_JgJlv9HRJVMcdl2dPB3aQl8wANK8",
                    "y": "a0lfVhFX8JRrR7bG_ZZaC8I6XjH3VPYJ5Qj9r5-eVLc",
                    "kid": "key-2"
                }
            ]
        }"""

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = jwksWithKeys,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val mockHttpClientFactory = mockk<HttpClientFactory>()
            every { mockHttpClientFactory.createClient(any<HttpClientOptions>()) } returns httpClient

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = mockHttpClientFactory,
                )

            val opts =
                ExternalIdentifierJwksUrlOpts(
                    identifier = "https://example.com/.well-known/jwks.json",
                    lookup =
                        com.sphereon.crypto.resolution
                            .AdditionalIdentifierLookup(kid = "key-2"),
                )
            val result = service.resolve(opts)

            assertTrue(result.isOk, "Should succeed when matching kid is found")
            assertEquals("key-2", result.value.selectedKid)
            assertEquals(2, result.value.jwks.size, "Should return all keys in JWKS")
        }

    @Test
    fun testResolveRejectsAmbiguousJwksWithoutKid() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val jwksWithKeys = """{
            "keys": [
                {
                    "kty": "EC",
                    "crv": "P-256",
                    "x": "WbbFpp0eS8_rJlvpuX_qEyU1J2PNmXYnqPCBJTqqiBA",
                    "y": "F8kbfVPRQc5M9kJA1fy3c_0Q6vCqHy1X7CZQC6XQy9I",
                    "kid": "first-key"
                },
                {
                    "kty": "EC",
                    "crv": "P-256",
                    "x": "AHzxbLBCZH-aMj_JgJlv9HRJVMcdl2dPB3aQl8wANK8",
                    "y": "a0lfVhFX8JRrR7bG_ZZaC8I6XjH3VPYJ5Qj9r5-eVLc",
                    "kid": "second-key"
                }
            ]
        }"""

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = jwksWithKeys,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val mockHttpClientFactory = mockk<HttpClientFactory>()
            every { mockHttpClientFactory.createClient(any<HttpClientOptions>()) } returns httpClient

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = mockHttpClientFactory,
                )

            // No kid specified in lookup
            val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json")
            val result = service.resolve(opts)

            assertTrue(result.isErr, "Should reject multiple usable keys when kid is omitted")
            assertTrue(result.error.message.defaultMessage.contains("exactly one usable key"))
        }

    @Test
    fun testResolveWithoutKidIgnoresEncryptionOnlyKeys() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext
            val signingAndEncryptionJwks = """{"keys":[
                {"kty":"EC","crv":"P-256","use":"enc","x":"AHzxbLBCZH-aMj_JgJlv9HRJVMcdl2dPB3aQl8wANK8","y":"a0lfVhFX8JRrR7bG_ZZaC8I6XjH3VPYJ5Qj9r5-eVLc","kid":"enc-key"},
                {"kty":"EC","crv":"P-256","use":"sig","x":"WbbFpp0eS8_rJlvpuX_qEyU1J2PNmXYnqPCBJTqqiBA","y":"F8kbfVPRQc5M9kJA1fy3c_0Q6vCqHy1X7CZQC6XQy9I","kid":"sig-key"}
            ]}"""
            val httpClient = HttpClient(MockEngine { _ ->
                respond(signingAndEncryptionJwks, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
            val factory = mockk<HttpClientFactory>()
            every { factory.createClient(any<HttpClientOptions>()) } returns httpClient
            val service = JwksUrlExternalIdentifierResolutionServiceImpl(mockExecution, factory)

            val result = service.resolve(ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json"))

            assertTrue(result.isOk, "An encryption-only key is not a signature candidate")
            assertEquals("sig-key", result.value.keyInfo.kid)
            assertEquals(2, result.value.jwks.size, "The full set is still returned for kid-based verification")
        }

    @Test
    fun testResolveWithoutKidSelectsTheOnlyUsableKey() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext
            val singleKeyJwks = """{"keys":[{"kty":"EC","crv":"P-256","x":"WbbFpp0eS8_rJlvpuX_qEyU1J2PNmXYnqPCBJTqqiBA","y":"F8kbfVPRQc5M9kJA1fy3c_0Q6vCqHy1X7CZQC6XQy9I","kid":"only-key"}]}"""
            val httpClient = HttpClient(MockEngine { _ ->
                respond(singleKeyJwks, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
            val factory = mockk<HttpClientFactory>()
            every { factory.createClient(any<HttpClientOptions>()) } returns httpClient
            val service = JwksUrlExternalIdentifierResolutionServiceImpl(mockExecution, factory)

            val result = service.resolve(ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json"))

            assertTrue(result.isOk, "Should select the only usable key when kid is omitted")
            assertEquals("only-key", result.value.keyInfo.kid)
        }

    // ========================================================================
    // Retry on transient failures (peer briefly unavailable / not-yet-ready)
    // ========================================================================

    @Test
    fun testResolveRetriesOnTransient5xxThenSucceeds() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val validJwks = """{"keys":[{"kty":"EC","crv":"P-256","x":"WbbFpp0eS8_rJlvpuX_qEyU1J2PNmXYnqPCBJTqqiBA","y":"F8kbfVPRQc5M9kJA1fy3c_0Q6vCqHy1X7CZQC6XQy9I","kid":"key-1"}]}"""
            var callCount = 0
            val mockEngine =
                MockEngine { _ ->
                    callCount++
                    if (callCount == 1) {
                        respond(
                            content = "Service Unavailable",
                            status = HttpStatusCode.ServiceUnavailable,
                            headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                        )
                    } else {
                        respond(
                            content = validJwks,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            val mockHttpClientFactory = mockk<HttpClientFactory>()
            every { mockHttpClientFactory.createClient(any<HttpClientOptions>()) } answers {
                val options = firstArg<HttpClientOptions>()
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                    options.additionalConfig?.invoke(this)
                }
            }

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = mockHttpClientFactory,
                )

            val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json")
            val result = service.resolve(opts)

            assertTrue(result.isOk, "Should recover after a transient 5xx via retry")
            assertEquals(2, callCount, "Should have retried once (2 attempts) after the transient 5xx")
        }

    @Test
    fun testResolveRetriesOnTransportErrorThenSucceeds() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val validJwks = """{"keys":[{"kty":"EC","crv":"P-256","x":"WbbFpp0eS8_rJlvpuX_qEyU1J2PNmXYnqPCBJTqqiBA","y":"F8kbfVPRQc5M9kJA1fy3c_0Q6vCqHy1X7CZQC6XQy9I","kid":"key-1"}]}"""
            var callCount = 0
            val mockEngine =
                MockEngine { _ ->
                    callCount++
                    if (callCount == 1) {
                        throw RuntimeException("connection refused")
                    } else {
                        respond(
                            content = validJwks,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }

            val mockHttpClientFactory = mockk<HttpClientFactory>()
            every { mockHttpClientFactory.createClient(any<HttpClientOptions>()) } answers {
                val options = firstArg<HttpClientOptions>()
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                    options.additionalConfig?.invoke(this)
                }
            }

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = mockHttpClientFactory,
                )

            val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json")
            val result = service.resolve(opts)

            assertTrue(result.isOk, "Should recover after a transient transport error via retry")
            assertEquals(2, callCount, "Should have retried once (2 attempts) after the transport error")
        }

    @Test
    fun testResolveDoesNotRetryOn4xx() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            var callCount = 0
            val mockEngine =
                MockEngine { _ ->
                    callCount++
                    respond(
                        content = "Forbidden",
                        status = HttpStatusCode.Forbidden,
                        headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                    )
                }

            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val mockHttpClientFactory = mockk<HttpClientFactory>()
            every { mockHttpClientFactory.createClient(any<HttpClientOptions>()) } returns httpClient

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = mockHttpClientFactory,
                )

            val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json")
            val result = service.resolve(opts)

            assertTrue(result.isErr, "A 4xx is a definite error")
            assertEquals(1, callCount, "A 4xx must NOT be retried")
        }

    // ========================================================================
    // Positive JWKS cache (via the CacheManager abstraction)
    // ========================================================================

    /** Minimal stateful CacheManager backing a single JwkSet cache with a real map. */
    private fun statefulCacheManager(): CacheManager {
        val store = mutableMapOf<String, JwkSet>()
        val cache = mockk<ScopedCache<String, JwkSet>>(relaxed = true)
        coEvery { cache.getApp(any()) } answers { store[firstArg()] }
        coEvery { cache.putApp(any(), any(), any()) } answers { store[firstArg<String>()] = secondArg<JwkSet>() }
        val cm = mockk<CacheManager>(relaxed = true)
        every { cm.getCache<String, JwkSet>(any()) } returns cache
        every { cm.createStringCache<JwkSet>(any(), any()) } returns cache
        return cm
    }

    private val ecKey1 =
        """{"kty":"EC","crv":"P-256","x":"WbbFpp0eS8_rJlvpuX_qEyU1J2PNmXYnqPCBJTqqiBA","y":"F8kbfVPRQc5M9kJA1fy3c_0Q6vCqHy1X7CZQC6XQy9I","kid":"key-1"}"""
    private val ecKey2 =
        """{"kty":"EC","crv":"P-256","x":"AHzxbLBCZH-aMj_JgJlv9HRJVMcdl2dPB3aQl8wANK8","y":"a0lfVhFX8JRrR7bG_ZZaC8I6XjH3VPYJ5Qj9r5-eVLc","kid":"key-2"}"""

    @Test
    fun encryptionPurposeSelectsIndependentlyFromCachedSigningSelection() = runTest {
        val execution = mockk<SessionExecution>(relaxed = true)
        every { execution.sessionContext } returns mockSessionContext
        val enc = ecKey1.dropLast(1) + ",\"use\":\"enc\"}"
        val sig = ecKey2.dropLast(1) + ",\"use\":\"sig\"}"
        var fetchCount = 0
        val factory = mockk<HttpClientFactory>()
        every { factory.createClient(any<HttpClientOptions>()) } answers {
            fetchCount++
            HttpClient(MockEngine { respond("""{"keys":[$enc,$sig]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        }
        val service = JwksUrlExternalIdentifierResolutionServiceImpl(execution, factory, statefulCacheManager())
        val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/jwks")
        assertEquals(JwksKeySelectionPurpose.SIGNATURE, opts.purpose)
        assertEquals("key-2", service.resolve(opts).value.keyInfo.kid)
        val encrypted = service.resolve(opts.copy(purpose = JwksKeySelectionPurpose.ENCRYPTION))
        assertTrue(encrypted.isOk)
        assertEquals("key-1", encrypted.value.keyInfo.kid)
        assertEquals(2, encrypted.value.jwks.size)
        assertEquals("key-2", service.resolve(opts).value.keyInfo.kid)
        assertEquals(1, fetchCount, "The raw set is cached; purpose-specific selection is recomputed")
    }

    @Test
    fun encryptionPurposePreservesAmbiguityAndRejectsPrivateOrIncompatibleKeys() = runTest {
        suspend fun resolve(keys: String, kid: String? = null, purpose: JwksKeySelectionPurpose = JwksKeySelectionPurpose.ENCRYPTION) = run {
            val execution = mockk<SessionExecution>(relaxed = true)
            every { execution.sessionContext } returns mockSessionContext
            val factory = mockk<HttpClientFactory>()
            every { factory.createClient(any<HttpClientOptions>()) } answers {
                HttpClient(MockEngine { respond("""{"keys":[$keys]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            }
            JwksUrlExternalIdentifierResolutionServiceImpl(execution, factory).resolve(
                ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/jwks", lookup = com.sphereon.crypto.resolution.AdditionalIdentifierLookup(kid = kid), purpose = purpose),
            )
        }
        val enc = ecKey1.dropLast(1) + ",\"use\":\"enc\"}"
        val secondEnc = ecKey2.dropLast(1) + ",\"use\":\"enc\"}"
        assertTrue(resolve(enc, purpose = JwksKeySelectionPurpose.SIGNATURE).isErr)
        assertEquals("key-1", resolve(enc).value.keyInfo.kid)
        assertTrue(resolve("$enc,$secondEnc").isErr)
        assertEquals("key-2", resolve("$enc,$secondEnc", kid = "key-2").value.keyInfo.kid)
        // Explicit signing kid behavior remains unchanged.
        assertEquals("key-1", resolve(enc, kid = "key-1", purpose = JwksKeySelectionPurpose.SIGNATURE).value.keyInfo.kid)
        val invalidFields = listOf(
            "\"use\":\"enc\",\"d\":\"cHJpdmF0ZQ\"",
            "\"use\":\"enc\",\"key_ops\":[\"decrypt\"]",
            "\"use\":\"sig\",\"key_ops\":[\"encrypt\"]",
            "\"use\":\"enc\",\"key_ops\":[\"encrypt\",\"verify\"]",
            "\"key_ops\":[\"wrap key\",\"sign\"]",
        )
        invalidFields.forEach { fields ->
            assertTrue(resolve(ecKey1.dropLast(1) + ",$fields}", kid = "key-1").isErr, fields)
        }
        assertTrue(resolve("""{"kty":"oct","k":"c2VjcmV0","kid":"secret","use":"enc"}""", kid = "secret").isErr)
        assertEquals("key-1", resolve(ecKey1.dropLast(1) + ",\"key_ops\":[\"wrap key\"]}").value.keyInfo.kid)
        assertEquals("key-1", resolve(ecKey1.dropLast(1) + ",\"use\":\"enc\",\"alg\":\"ECDH-ES\",\"key_ops\":[\"derive key\"]}").value.keyInfo.kid)
    }

    private fun kidLookup(kid: String) =
        com.sphereon.crypto.resolution
            .AdditionalIdentifierLookup(kid = kid)

    @Test
    fun testResolveCacheHitDoesNotRefetch() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            var fetchCount = 0
            val factory = mockk<HttpClientFactory>()
            every { factory.createClient(any<HttpClientOptions>()) } answers {
                fetchCount++
                HttpClient(MockEngine { _ -> respond("""{"keys":[$ecKey1]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            }

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = factory,
                    cacheManager = statefulCacheManager(),
                )
            val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json", lookup = kidLookup("key-1"))

            val first = service.resolve(opts)
            val second = service.resolve(opts)

            assertTrue(first.isOk, "first resolve should succeed")
            assertTrue(second.isOk, "second resolve should succeed")
            assertEquals(1, fetchCount, "second resolve (same url+kid) must hit the cache and NOT re-fetch")
        }

    @Test
    fun testResolveKidMissRefetches() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            var fetchCount = 0
            val factory = mockk<HttpClientFactory>()
            every { factory.createClient(any<HttpClientOptions>()) } answers {
                fetchCount++
                val body = if (fetchCount == 1) """{"keys":[$ecKey1]}""" else """{"keys":[$ecKey1,$ecKey2]}"""
                HttpClient(MockEngine { _ -> respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            }

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = factory,
                    cacheManager = statefulCacheManager(),
                )
            val url = "https://example.com/.well-known/jwks.json"

            val r1 = service.resolve(ExternalIdentifierJwksUrlOpts(identifier = url, lookup = kidLookup("key-1")))
            val r2 = service.resolve(ExternalIdentifierJwksUrlOpts(identifier = url, lookup = kidLookup("key-2")))

            assertTrue(r1.isOk, "first resolve (key-1) should succeed")
            assertTrue(r2.isOk, "second resolve (key-2) must re-resolve and find the rotated-in key")
            assertEquals(2, fetchCount, "a kid absent from the cached JWKS must trigger a fresh fetch (rotation)")
        }

    @Test
    fun testResolveNeverCachesFailure() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            var fetchCount = 0
            val factory = mockk<HttpClientFactory>()
            every { factory.createClient(any<HttpClientOptions>()) } answers {
                fetchCount++
                val n = fetchCount
                HttpClient(
                    MockEngine { _ ->
                        if (n == 1) {
                            respond("Service Unavailable", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.ContentType, "text/plain"))
                        } else {
                            respond("""{"keys":[$ecKey1]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                        }
                    },
                ) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            }

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = factory,
                    cacheManager = statefulCacheManager(),
                )
            val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json", lookup = kidLookup("key-1"))

            val firstFail = service.resolve(opts)
            val secondOk = service.resolve(opts)

            assertTrue(firstFail.isErr, "first resolve fails (peer down)")
            assertTrue(secondOk.isOk, "after recovery the resolve succeeds — the failure was not cached")
            assertEquals(2, fetchCount, "a failed fetch must NOT be cached; the next resolve must re-fetch")
        }

    // ========================================================================
    // Branch Coverage Tests for supports() method
    // ========================================================================

    @Test
    fun testSupportsReturnsFalseForNonSupportedOpts() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockHttpClientFactory = mockk<HttpClientFactory>()

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = mockHttpClientFactory,
                )

            // DID opts should not be supported
            val didOpts = ExternalIdentifierDidOpts(identifier = "did:example:123")
            assertFalse(service.supports(didOpts), "Should not support DID opts")

            // X5C opts should not be supported
            val x5cOpts = ExternalIdentifierX5cOpts(identifier = listOf("MII..."))
            assertFalse(service.supports(x5cOpts), "Should not support X5C opts")

            val withContext = service.supports(didOpts)
            assertEquals(service.supports(didOpts), withContext, "Context-bearing supports should delegate to supports")
        }

    @Test
    fun testSupportsReturnsTrueForJwksUrlOpts() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockHttpClientFactory = mockk<HttpClientFactory>()

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = mockHttpClientFactory,
                )

            val jwksUrlOpts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json")
            assertTrue(service.supports(jwksUrlOpts), "Should support JWKS URL opts")
            assertEquals(
                service.supports(jwksUrlOpts),
                service.supports(jwksUrlOpts),
                "Context-bearing supports should delegate to supports",
            )
        }

    // ========================================================================
    // Branch Coverage Tests for isSupportedIdentifier() method
    // ========================================================================

    @Test
    fun testIsSupportedIdentifierReturnsFalseForNonString() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockHttpClientFactory = mockk<HttpClientFactory>()

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = mockHttpClientFactory,
                )

            assertFalse(service.isSupportedIdentifier(12345), "Should not support non-string identifier")
            assertFalse(service.isSupportedIdentifier(listOf("https://example.com")), "Should not support list identifier")
        }

    @Test
    fun testIsSupportedIdentifierReturnsFalseForNonUrlString() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockHttpClientFactory = mockk<HttpClientFactory>()

            val service =
                JwksUrlExternalIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    httpClientFactory = mockHttpClientFactory,
                )

            assertFalse(service.isSupportedIdentifier("not-a-url"), "Should not support non-URL string")
            assertFalse(service.isSupportedIdentifier("ftp://example.com"), "Should not support FTP URL")
            assertFalse(service.isSupportedIdentifier("did:example:123"), "Should not support DID")
        }
}
