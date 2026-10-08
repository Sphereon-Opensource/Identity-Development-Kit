/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.did.methods.webvh.resolver

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.did.methods.webvh.model.WebvhLogEntry
import com.sphereon.did.methods.webvh.model.WebvhWitnessFile
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.ktor.http.client.provider.UrlValidationPolicy
import com.sphereon.ktor.http.client.provider.withCounterpartyEgress
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The did:webvh log and witness fetches run under the counterparty egress rule for a holder call. */
class WebvhEgressTest {
    private class RecordingFactory : HttpClientFactory {
        val requested = mutableListOf<HttpClientOptions>()
        val urls = mutableListOf<String>()

        override fun createClient(options: HttpClientOptions): HttpClient {
            requested += options
            val client = HttpClient(MockEngine { request -> urls += request.url.toString(); respond("", HttpStatusCode.NotFound) })
            options.urlValidation?.let { policy ->
                client.plugin(HttpSend).intercept { request ->
                    policy.validate(request.url.build())
                    execute(request)
                }
            }
            return client
        }

        override fun isSupportedOptions(options: HttpClientOptions): Boolean = true

        override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.CIO)

        override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
    }

    private object NoReplayer : WebvhLogReplayer {
        override suspend fun replay(
            did: String,
            entries: List<WebvhLogEntry>,
            witnessFile: WebvhWitnessFile?,
            selector: ReplaySelector,
        ): IdkResult<ReplayResult, IdkError> = error("the log must not be fetched, so there is nothing to replay")
    }

    @Test
    fun aHolderCallRefusesALoopbackWebvhHostBeforeAnyRequestIsSent() = runTest {
        val factory = RecordingFactory()
        val resolver = WebvhDidResolverImpl(factory, NoReplayer)

        for (host in listOf("127.0.0.1", "10.0.0.5", "localhost", "169.254.169.254")) {
            val result = withCounterpartyEgress { resolver.resolve("did:webvh:QmScid:$host", DidResolutionOptions()) }
            assertTrue(result.isErr, "$host must be refused")
        }
        assertTrue(factory.urls.isEmpty(), "no request may reach the engine for a refused host")
        assertTrue(factory.requested.all { it.urlValidation == UrlValidationPolicy.COUNTERPARTY_EGRESS })
    }

    @Test
    fun theWebvhFetchOptionsNeverFollowRedirects() = runTest {
        val factory = RecordingFactory()
        WebvhDidResolverImpl(factory, NoReplayer).resolve("did:webvh:QmScid:issuer.example.com", DidResolutionOptions())

        assertEquals(1, factory.requested.size)
        assertFalse(factory.requested.single().followRedirects)
    }

    @Test
    fun aServerCallKeepsItsOwnPolicy() = runTest {
        val factory = RecordingFactory()
        WebvhDidResolverImpl(factory, NoReplayer).resolve("did:webvh:QmScid:issuer.example.com", DidResolutionOptions())

        assertEquals(listOf<UrlValidationPolicy?>(null), factory.requested.map { it.urlValidation })
    }
}
