/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.mdoc.vical

import com.sphereon.core.api.Ok
import com.sphereon.core.api.cache.CacheService
import com.sphereon.core.api.cache.ScopedCache
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VicalFetcherImplTest {
    private val url = "https://vical.example.test/current.cwt"
    private val body = byteArrayOf(0x01, 0x02, 0x03)
    private val validationPolicy = VicalValidationPolicy(trustedCerts = arrayOf("test-trust-anchor"))
    private val validationResult =
        VicalValidationResult(
            vical = Vical(
                vicalProvider = "https://vical.example.test/provider",
                date = "2026-08-27T00:00:00Z",
                nextUpdate = "2026-08-28T00:00:00Z",
                certificateInfos = emptyList(),
            ),
            signerCertificateChain = emptyList(),
            matchingCertificateInfos = emptyList(),
        )

    @Test
    fun legacyApplicationCwtResponseIsAcceptedAndValidated() = runTest {
        val engine = MockEngine {
            respond(body, headers = cwtHeaders(body.size))
        }
        val validator = mockk<VicalValidator>()
        coEvery { validator.validate(any(), validationPolicy) } returns Ok(validationResult)

        val result = fetcher(engine, validator).fetch(url, policy())

        assertTrue(result.isOk, "legacy application/cwt compatibility response should be accepted")
        assertEquals(url, result.value.sourceUrl)
        coVerify(exactly = 1) { validator.validate(any(), validationPolicy) }
    }

    @Test
    fun canonicalApplicationCborResponseWithParametersPreservesBytesAndAdvertisesBothMediaTypes() = runTest {
        val engine = MockEngine {
            respond(body, headers = cborHeaders(body.size, withParameters = true))
        }
        val validator = mockk<VicalValidator>()
        coEvery { validator.validate(any(), validationPolicy) } returns Ok(validationResult)

        val result = fetcher(engine, validator).fetch("https://vical.example.test/current.cbor", policy())

        assertTrue(result.isOk, "canonical application/cbor VICAL response should be accepted")
        assertEquals(
            listOf("application/cbor", "application/cwt"),
            engine.requestHistory.single().headers[HttpHeaders.Accept]
                ?.split(',')
                ?.map(String::trim),
        )
        coVerify(exactly = 1) {
            validator.validate(match { it.contentEquals(body) }, validationPolicy)
        }
    }

    @Test
    fun jsonResponseIsRejectedBeforeValidation() = runTest {
        val engine = MockEngine {
            respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val validator = mockk<VicalValidator>()

        val result = fetcher(engine, validator).fetch(url, policy())

        assertTrue(result.isErr)
        coVerify(exactly = 0) { validator.validate(any(), any()) }
    }

    @Test
    fun declaredOversizedResponseIsRejectedWithoutReadingOrValidating() = runTest {
        val engine = MockEngine {
            respond(
                body,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/cwt"),
                    HttpHeaders.ContentLength to listOf("4"),
                ),
            )
        }
        val validator = mockk<VicalValidator>()

        val result = fetcher(engine, validator).fetch(url, policy(maxBodyBytes = 3))

        assertTrue(result.isErr)
        coVerify(exactly = 0) { validator.validate(any(), any()) }
    }

    @Test
    fun streamedBodyThatExceedsLimitIsRejected() = runTest {
        val oversizedBody = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val engine = MockEngine {
            respond(oversizedBody, headers = cwtHeaders(null))
        }
        val validator = mockk<VicalValidator>()

        val result = fetcher(engine, validator).fetch(url, policy(maxBodyBytes = 3))

        assertTrue(result.isErr)
        coVerify(exactly = 0) { validator.validate(any(), any()) }
    }

    @Test
    fun redirectToHttpIsRejectedWhenHttpsIsRequired() = runTest {
        val engine = MockEngine {
            respond(
                content = byteArrayOf(),
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location, "http://vical.example.test/redirected.cwt"),
            )
        }
        val validator = mockk<VicalValidator>()

        val result = fetcher(engine, validator).fetch(url, policy())

        assertTrue(result.isErr)
        assertEquals(1, engine.requestHistory.size)
        coVerify(exactly = 0) { validator.validate(any(), any()) }
    }

    @Test
    fun redirectToPrivateTargetIsRejected() = runTest {
        val engine = MockEngine {
            respond(
                content = byteArrayOf(),
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location, "https://127.0.0.1/private.cwt"),
            )
        }
        val validator = mockk<VicalValidator>()

        val result = fetcher(engine, validator).fetch(url, policy())

        assertTrue(result.isErr)
        assertEquals(1, engine.requestHistory.size)
        coVerify(exactly = 0) { validator.validate(any(), any()) }
    }

    @Test
    fun validatedResponseIsReusedFromTenantCache() = runTest {
        val engine = MockEngine { respond(body, headers = cwtHeaders(body.size)) }
        val validator = mockk<VicalValidator>()
        coEvery { validator.validate(any(), validationPolicy) } returns Ok(validationResult)
        var cached: String? = null
        val cache = mockk<ScopedCache<String, String>>()
        coEvery { cache.getTenant("tenant-1", url) } answers { cached }
        coEvery { cache.putTenant("tenant-1", url, any(), any()) } answers { cached = thirdArg() }

        val fetcher = fetcher(engine, validator, cache)
        val first = fetcher.fetch(url, policy(useCache = true))
        val second = fetcher.fetch(url, policy(useCache = true))

        assertTrue(first.isOk)
        assertTrue(second.isOk)
        assertFalse(first.value.fromCache)
        assertTrue(second.value.fromCache)
        assertEquals(1, engine.requestHistory.size)
        coVerify(exactly = 2) { validator.validate(any(), validationPolicy) }
    }

    private fun policy(maxBodyBytes: Long = 1024, useCache: Boolean = false) =
        VicalFetchPolicy(
            validation = validationPolicy,
            maxBodyBytes = maxBodyBytes,
            useCache = useCache,
        )

    private fun fetcher(
        engine: MockEngine,
        validator: VicalValidator,
        cache: ScopedCache<String, String> = mockk(relaxed = true),
    ): VicalFetcherImpl {
        val cacheService = mockk<CacheService>()
        every { cacheService.getCache(any()) } returns cache
        val execution = mockk<SessionExecution>(relaxed = true)
        every { execution.tenantId } returns "tenant-1"
        return VicalFetcherImpl(
            httpClientFactory = mockHttpClientFactory(engine),
            cacheService = cacheService,
            execution = execution,
            validator = validator,
        )
    }

    private fun mockHttpClientFactory(engine: MockEngine): HttpClientFactory =
        object : HttpClientFactory {
            override fun createClient(options: HttpClientOptions): HttpClient {
                val client = io.ktor.client.HttpClient(engine) {
                    followRedirects = false
                }
                options.urlValidation?.let { validationPolicy ->
                    client.requestPipeline.intercept(HttpRequestPipeline.Before) {
                        validationPolicy.validate(context.url.build())
                    }
                }
                return client
            }

            override fun isSupportedOptions(options: HttpClientOptions): Boolean = true

            override fun getEngineTypesSupported(): List<HttpClientEngineType> = emptyList()

            override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
        }

    private fun cwtHeaders(length: Int?): io.ktor.http.Headers =
        if (length == null) {
            headersOf(HttpHeaders.ContentType, "application/cwt")
        } else {
            headersOf(
                HttpHeaders.ContentType to listOf("application/cwt"),
                HttpHeaders.ContentLength to listOf(length.toString()),
            )
        }

    private fun cborHeaders(length: Int?, withParameters: Boolean = false): io.ktor.http.Headers {
        val contentType = if (withParameters) "application/cbor; charset=binary" else "application/cbor"
        return if (length == null) {
            headersOf(HttpHeaders.ContentType, contentType)
        } else {
            headersOf(
                HttpHeaders.ContentType to listOf(contentType),
                HttpHeaders.ContentLength to listOf(length.toString()),
            )
        }
    }
}
