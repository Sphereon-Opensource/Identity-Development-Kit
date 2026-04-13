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

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.context.createAnonymousSessionContext
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
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
    private val mockSessionContext = createAnonymousSessionContext("mock-jwks-url-test")

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
    fun testResolveSucceedsWithoutKidSelectsFirstKey() =
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

            assertTrue(result.isOk, "Should succeed without kid, selecting first key")
            assertEquals(null, result.value.selectedKid, "selectedKid should be null when no kid was requested")
            assertEquals("first-key", result.value.keyInfo.kid, "Should select the first key from JWKS")
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
