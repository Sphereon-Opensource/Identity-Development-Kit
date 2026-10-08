/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.jsonld.loader

import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Job
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HttpLinkedDataDocumentLoaderTest {
    private val documentUrl = "https://example.test/alias"
    private val finalUrl = "https://example.test/documents/item.json"
    private val contextUrl = "https://example.test/contexts/context.jsonld"

    @Test
    fun requestsManualRedirectConfigurationFromFactory() = runTest {
        val factory = RecordingFactory(
            HttpClient(MockEngine {
                respond(
                    content = "{}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }) {
                followRedirects = false
            },
        )

        val result = HttpLinkedDataDocumentLoader(
            httpClientFactory = factory,
            options = HttpClientOptions.createDefault().copy(
                followRedirects = true,
                additionalConfig = { followRedirects = true },
            ),
            policy = JsonLdDocumentLoadingPolicy.ALLOW_ALL,
        ).loadDocument(documentUrl)

        assertTrue(result.isOk, "factory-created loader should load: ${result.errorOrNull()}")
        assertEquals(false, factory.capturedOptions?.followRedirects)
    }

    @Test
    fun factoryReturnedClientIsUsedBeforeItIsClosedAndClosedAfterLoad() = runTest {
        var returnedClient: HttpClient? = null
        var activeDuringRequest = false
        returnedClient = HttpClient(MockEngine {
            activeDuringRequest = returnedClient?.coroutineContext?.get(Job)?.isActive == true
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })
        val factory = RecordingFactory(returnedClient!!)

        val result = HttpLinkedDataDocumentLoader(
            httpClientFactory = factory,
            options = HttpClientOptions.createDefault(),
            policy = JsonLdDocumentLoadingPolicy.ALLOW_ALL,
        ).loadDocument(documentUrl)

        assertTrue(result.isOk, "factory-created loader should load: ${result.errorOrNull()}")
        assertTrue(activeDuringRequest, "the returned client must not be closed before the request")
        assertFalse(returnedClient!!.coroutineContext[Job]!!.isActive, "the loader must close its owned client")
    }

    @Test
    fun factoryReturnedClientIsClosedWhenRequestIsCancelled() = runTest {
        val cancellation = CancellationException("cancelled")
        val client = HttpClient(MockEngine { throw cancellation })
        val factory = RecordingFactory(client)
        val loader = HttpLinkedDataDocumentLoader(
            httpClientFactory = factory,
            options = HttpClientOptions.createDefault(),
            policy = JsonLdDocumentLoadingPolicy.ALLOW_ALL,
        )

        assertFailsWith<CancellationException> { loader.loadDocument(documentUrl) }
        assertFalse(client.coroutineContext[Job]!!.isActive, "cancellation must still close the owned client")
    }

    @Test
    fun unsupportedManualRedirectFactoryReturnsTypedLoadingFailure() = runTest {
        val factory = RecordingFactory(
            client = HttpClient(MockEngine { error("request must not be reached") }),
            supportsOptions = false,
        )

        val result = HttpLinkedDataDocumentLoader(
            httpClientFactory = factory,
            options = HttpClientOptions.createDefault(),
            policy = JsonLdDocumentLoadingPolicy.ALLOW_ALL,
        ).loadDocument(documentUrl)

        val error = assertIs<com.sphereon.jsonld.JsonLdError.LoadingDocumentFailed>(result.error)
        assertTrue(error.reason.contains("manual redirect"), error.reason)
        assertEquals(0, factory.createCount)
    }

    @Test
    fun clientConstructionPropagatesCoroutineCancellation() = runTest {
        val cancellation = CancellationException("cancelled")
        val factory = object : HttpClientFactory {
            override fun createClient(options: HttpClientOptions): HttpClient = throw cancellation

            override fun isSupportedOptions(options: HttpClientOptions): Boolean = true

            override fun getEngineTypesSupported(): List<HttpClientEngineType> = emptyList()

            override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.OKHTTP
        }

        val loader = HttpLinkedDataDocumentLoader(
            httpClientFactory = factory,
            options = HttpClientOptions.createDefault(),
            policy = JsonLdDocumentLoadingPolicy.ALLOW_ALL,
        )

        assertFailsWith<CancellationException> { loader.loadDocument(documentUrl) }
    }

    @Test
    fun followsRedirectsAndPropagatesFinalUrlContentTypeAndProfile() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine { request ->
            requests += request
            when (request.url.toString()) {
                documentUrl -> respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "/documents/item.json"),
                )

                finalUrl -> respond(
                    content = "{\"name\":\"Alice\"}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        "application/ld+json; profile=\"https://example.test/profile\"",
                    ),
                )

                else -> error("unexpected request ${request.url}")
            }
        }) {
            followRedirects = false
        }

        val result = loader(client, JsonLdDocumentLoadingPolicy.ALLOW_ALL).loadDocument(documentUrl)

        assertTrue(result.isOk, "redirected JSON-LD should load: ${result.errorOrNull()}")
        assertEquals(finalUrl, result.value.documentUrl)
        assertEquals(
            "application/ld+json; profile=\"https://example.test/profile\"",
            result.value.contentType,
        )
        assertEquals("https://example.test/profile", result.value.profile)
        assertEquals(2, requests.size)
        assertTrue(
            requests.first().headers[HttpHeaders.Accept].orEmpty().contains("application/ld+json"),
        )
        assertTrue(
            requests.first().headers[HttpHeaders.Accept].orEmpty().contains("application/json"),
        )
    }

    @Test
    fun rejectsRedirectBeforeRequestingAnUntrustedTarget() = runTest {
        val trusted = "https://example.test/start"
        val untrusted = "https://evil.test/context"
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location, untrusted),
            )
        }) {
            followRedirects = false
        }

        val result = loader(
            client = client,
            policy = JsonLdDocumentLoadingPolicy.allowOnly(setOf(trusted)),
        ).loadDocument(trusted)

        assertTrue(result.isErr)
        assertIs<com.sphereon.jsonld.JsonLdError.DocumentNotAllowed>(result.error)
        assertEquals(listOf(trusted), requests)
    }

    @Test
    fun rejectsContextLinkBeforeReturningRemoteDocument() = runTest {
        val trusted = "https://example.test/context.json"
        val untrusted = "https://evil.test/context.jsonld"
        val client = HttpClient(MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                    HttpHeaders.Link to listOf(
                        "<$untrusted>; rel=\"http://www.w3.org/ns/json-ld#context\"",
                    ),
                ),
            )
        })

        val result = loader(
            client = client,
            policy = JsonLdDocumentLoadingPolicy.allowOnly(setOf(trusted)),
        ).loadDocument(trusted)

        assertTrue(result.isErr)
        assertIs<com.sphereon.jsonld.JsonLdError.DocumentNotAllowed>(result.error)
    }

    @Test
    fun resolvesSingleRelativeContextLinkAgainstFinalDocumentUrlForJsonMediaType() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                content = "{\"name\":\"Alice\"}",
                status = HttpStatusCode.OK,
                    headers = headersOf(
                    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                    HttpHeaders.Link to listOf(
                        "<../contexts/context.jsonld>; rel=\"http://www.w3.org/ns/json-ld#context\"",
                    ),
                ),
            )
        })

        val result = loader(client, JsonLdDocumentLoadingPolicy.ALLOW_ALL).loadDocument(finalUrl)

        assertTrue(result.isOk, "JSON with one context link should load: ${result.errorOrNull()}")
        assertEquals(contextUrl, result.value.contextUrl)
        assertEquals("application/json", result.value.contentType)
        assertEquals(null, result.value.profile)
    }

    @Test
    fun ignoresContextLinkForApplicationLdJson() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                    headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/ld+json"),
                    HttpHeaders.Link to listOf(
                        "<context.jsonld>; rel=\"http://www.w3.org/ns/json-ld#context\"",
                    ),
                ),
            )
        })

        val result = loader(client, JsonLdDocumentLoadingPolicy.ALLOW_ALL).loadDocument(finalUrl)

        assertTrue(result.isOk)
        assertEquals(null, result.value.contextUrl)
    }

    @Test
    fun followsJsonLdAlternateForNonJsonResponse() = runTest {
        val alternateUrl = "https://example.test/documents/item.jsonld"
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            when (request.url.toString()) {
                finalUrl -> respond(
                    content = "<html>This is only an alternate representation.</html>",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf(ContentType.Text.Html.toString()),
                        HttpHeaders.Link to listOf(
                            "<item.jsonld>; rel=\"alternate\"; type=\"application/ld+json\"",
                        ),
                    ),
                )

                alternateUrl -> respond(
                    content = "{\"name\":\"Alice\"}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/ld+json"),
                )

                else -> error("unexpected request ${request.url}")
            }
        })

        val result = loader(client, JsonLdDocumentLoadingPolicy.ALLOW_ALL).loadDocument(finalUrl)

        assertTrue(result.isOk, "JSON-LD alternate should load: ${result.errorOrNull()}")
        assertEquals(alternateUrl, result.value.documentUrl)
        assertEquals(listOf(finalUrl, alternateUrl), requests)
    }

    @Test
    fun acceptsApplicationJsonExtensionMediaType() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/jldTest+json"),
            )
        })

        val result = loader(client, JsonLdDocumentLoadingPolicy.ALLOW_ALL).loadDocument(finalUrl)

        assertTrue(result.isOk, "application/*+json is a JSON media type")
    }

    @Test
    fun rejectsMoreThanOneContextLink() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                    headers = headersOf(
                    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                    HttpHeaders.Link to listOf(
                        "<one.jsonld>; rel=\"http://www.w3.org/ns/json-ld#context\", " +
                            "<two.jsonld>; rel=\"http://www.w3.org/ns/json-ld#context\"",
                    ),
                ),
            )
        })

        val result = loader(client, JsonLdDocumentLoadingPolicy.ALLOW_ALL).loadDocument(finalUrl)

        assertTrue(result.isErr)
        assertIs<com.sphereon.jsonld.JsonLdError.LoadingDocumentFailed>(result.error)
    }

    @Test
    fun rejectsMalformedJsonAndNonJsonMediaType() = runTest {
        val malformed = HttpClient(MockEngine {
            respond(
                content = "not-json",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })
        val html = HttpClient(MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
            )
        })

        val malformedResult = loader(malformed, JsonLdDocumentLoadingPolicy.ALLOW_ALL).loadDocument(finalUrl)
        val htmlResult = loader(html, JsonLdDocumentLoadingPolicy.ALLOW_ALL).loadDocument(finalUrl)

        assertTrue(malformedResult.isErr)
        assertTrue(htmlResult.isErr)
    }

    @Test
    fun acceptsJsonLdArraysAsRemoteDocuments() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                content = "[{\"name\":\"Alice\"}]",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/ld+json"),
            )
        })

        val result = loader(client, JsonLdDocumentLoadingPolicy.ALLOW_ALL).loadDocument(finalUrl)

        assertTrue(result.isOk, "JSON-LD arrays are valid remote documents")
        assertTrue(result.value.content is kotlinx.serialization.json.JsonArray)
    }

    @Test
    fun rejectsMalformedContextLink() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                    headers = headersOf(
                    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                    HttpHeaders.Link to listOf("not-a-link; rel=\"http://www.w3.org/ns/json-ld#context\""),
                ),
            )
        })

        val result = loader(client, JsonLdDocumentLoadingPolicy.ALLOW_ALL).loadDocument(finalUrl)

        assertTrue(result.isErr)
    }

    private fun loader(
        client: HttpClient,
        policy: JsonLdDocumentLoadingPolicy,
    ): HttpLinkedDataDocumentLoader =
        HttpLinkedDataDocumentLoader(
            httpClientFactory = RecordingFactory(client),
            options = HttpClientOptions.createDefault(),
            policy = policy,
        )

    private class RecordingFactory(
        private val client: HttpClient,
        private val supportsOptions: Boolean = true,
    ) : HttpClientFactory {
        var createCount: Int = 0
            private set
        var capturedOptions: HttpClientOptions? = null
            private set

        override fun createClient(options: HttpClientOptions): HttpClient {
            createCount++
            capturedOptions = options
            return client
        }

        override fun isSupportedOptions(options: HttpClientOptions): Boolean = supportsOptions

        override fun getEngineTypesSupported(): List<HttpClientEngineType> = emptyList()

        override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.OKHTTP
    }
}
