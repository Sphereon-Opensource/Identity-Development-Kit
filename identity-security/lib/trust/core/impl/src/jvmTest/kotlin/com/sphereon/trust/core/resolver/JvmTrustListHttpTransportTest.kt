/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.trust.core.resolver

import com.sphereon.core.api.cache.DefaultCacheManager
import com.sphereon.core.api.cache.DefaultCacheService
import com.sphereon.core.api.cache.MapCacheBackend
import com.sphereon.trust.core.TrustDiagnosticReasonCodes
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class JvmTrustListHttpTransportTest {
    @Test
    fun streamingBodyLimitCancelsTheRealResponseBeforeMaterialization() = runBlocking {
        val declaredContentLength = 16L * 1024 * 1024
        val responseChunkSize = 64 * 1024
        val responseChunkCount = 256
        val attemptedBodyBytes = responseChunkCount.toLong() * responseChunkSize
        assertTrue(
            attemptedBodyBytes <= declaredContentLength,
            "fixed-length fixture attempted $attemptedBodyBytes bytes but declared $declaredContentLength",
        )

        val serverFinished = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/oversized") { exchange ->
            exchange.sendResponseHeaders(200, declaredContentLength)
            try {
                repeat(responseChunkCount) {
                    exchange.responseBody.write(ByteArray(responseChunkSize) { 7 })
                    exchange.responseBody.flush()
                }
            } catch (_: Exception) {
                // A legal fixed-length response may still observe client cancellation as a write failure.
            } finally {
                exchange.responseBody.close()
                serverFinished.countDown()
            }
        }
        server.start()

        try {
            val transport =
                JvmTrustListHttpTransport(
                    resolveAddresses = { listOf(InetAddress.getByName(it)) },
                    addressPolicy = {},
                    dialAddressPolicy = { _, _ -> },
                )
            val response = transport.execute("http://127.0.0.1:${server.address.port}/oversized", 5_000)

            val failure = assertFailsWith<TrustListResolutionException> { response.body.readAll(3) }
            assertEquals(TrustDiagnosticReasonCodes.TRUST_LIST_BODY_TOO_LARGE, failure.reasonCode)
            response.close()
            assertTrue(serverFinished.await(5, TimeUnit.SECONDS))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun realServerRedirectsAreResolvedPerHopAndResponsesAreClosed() = runBlocking {
        val finalHits = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/path/start") { exchange ->
            exchange.responseHeaders.add("Location", "../final?via=redirect#not-a-cache-key")
            exchange.respond(302, "redirect-body")
        }
        server.createContext("/final") { exchange ->
            finalHits.incrementAndGet()
            exchange.respond(200, "final-body")
        }
        server.start()

        try {
            val resolver = resolverFor()
            val result =
                resolver.resolve(
                    "HTTP://EXAMPLE.COM:${server.address.port}/path/start#ignored",
                    ResolutionOptions(
                        requireHttps = false,
                        useCache = false,
                        maxRedirects = 1,
                        maxBodyBytes = 1_024,
                    ),
                )

            assertEquals("final-body", result.data.decodeToString())
            assertEquals(
                "http://example.com:${server.address.port}/final?via=redirect",
                result.sourceUri,
            )
            assertEquals(1, finalHits.get())
            assertTrue(result.sourceUri.none { it == '#' })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun realServerCacheHeadersBoundTtlAndFragmentFreeKeys() = runBlocking {
        val hits = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/cached") { exchange ->
            hits.incrementAndGet()
            exchange.responseHeaders.add("Cache-Control", "public, max-age=60")
            exchange.responseHeaders.add("ETag", "\"cache-v1\"")
            exchange.respond(200, "cached-body")
        }
        server.start()

        try {
            val resolver = resolverFor()
            val first =
                resolver.resolve(
                    "http://Example.com:${server.address.port}/cached#first-fragment",
                    ResolutionOptions(
                        requireHttps = false,
                        maxCacheAgeMs = 120_000,
                        maxBodyBytes = 1_024,
                    ),
                )
            val second =
                resolver.resolve(
                    "HTTP://EXAMPLE.COM:${server.address.port}/cached#second-fragment",
                    ResolutionOptions(
                        requireHttps = false,
                        maxCacheAgeMs = 120_000,
                        maxBodyBytes = 1_024,
                    ),
                )

            assertEquals(1, hits.get())
            assertEquals("cached-body", first.data.decodeToString())
            assertTrue(!first.fromCache)
            assertTrue(second.fromCache)
            assertEquals(60_000L, first.cacheMetadata?.effectiveTtlMs)
            assertEquals("\"cache-v1\"", first.cacheToken)
            assertTrue(second.sourceUri.none { it == '#' })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun realServerNoStoreIsNotCachedAndRedirectLimitClosesEachResponse() = runBlocking {
        val noStoreHits = AtomicInteger()
        val loopHits = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/no-store") { exchange ->
            noStoreHits.incrementAndGet()
            exchange.responseHeaders.add("Cache-Control", "no-store")
            exchange.respond(200, "uncached")
        }
        server.createContext("/loop") { exchange ->
            loopHits.incrementAndGet()
            exchange.responseHeaders.add("Location", "/loop")
            exchange.respond(302, "loop-body")
        }
        server.start()

        try {
            val resolver = resolverFor()
            val options = ResolutionOptions(requireHttps = false, maxCacheAgeMs = 120_000, maxRedirects = 1)
            resolver.resolve("http://example.com:${server.address.port}/no-store", options)
            resolver.resolve("http://example.com:${server.address.port}/no-store", options)
            assertEquals(2, noStoreHits.get())

            val failure =
                assertFailsWith<TrustListResolutionException> {
                    resolver.resolve("http://example.com:${server.address.port}/loop", options)
                }
            assertEquals(TrustDiagnosticReasonCodes.TRUST_LIST_REDIRECT_LIMIT, failure.reasonCode)
            assertEquals(2, loopHits.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun dialTargetMustBeOneOfTheApprovedAddresses() = runBlocking {
        val failure =
            assertFailsWith<TrustListResolutionException> {
                JvmTrustListHttpTransport(
                    resolveAddresses = { listOf(InetAddress.getByName("8.8.8.8")) },
                    addressPolicy = {},
                    dialAddresses = { _, _ -> listOf(InetAddress.getByName("1.1.1.1")) },
                    dialAddressPolicy = { approved, dial ->
                        TrustListAddressPolicy.requireApprovedDialAddress(approved, dial)
                    },
                ).execute("http://example.com/", 5_000)
            }

        assertEquals(TrustDiagnosticReasonCodes.TRUST_LIST_DNS_REBINDING, failure.reasonCode)
    }

    private fun resolverFor(): HttpTrustListResolver {
        val transport =
            JvmTrustListHttpTransport(
                resolveAddresses = { listOf(InetAddress.getLoopbackAddress()) },
                addressPolicy = {},
                dialAddressPolicy = { _, _ -> },
            )
        val cacheManager = DefaultCacheManager()
        cacheManager.registerBackend(MapCacheBackend())
        return HttpTrustListResolver(transport, DefaultCacheService(cacheManager))
    }

    private fun com.sun.net.httpserver.HttpExchange.respond(
        status: Int,
        body: String,
    ) {
        val bytes = body.encodeToByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
