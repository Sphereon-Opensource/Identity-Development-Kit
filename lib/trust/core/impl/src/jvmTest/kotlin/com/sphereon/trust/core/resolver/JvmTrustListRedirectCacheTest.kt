/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.trust.core.resolver

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class JvmTrustListRedirectCacheTest {
    @Test
    fun redirectedResponseIsCachedByTheOriginalFragmentFreeRequestKey() = runBlocking {
        val redirectHits = AtomicInteger()
        val finalHits = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/start") { exchange ->
            redirectHits.incrementAndGet()
            exchange.responseHeaders.add("Location", "/final#ignored")
            exchange.sendResponseHeaders(302, 0)
            exchange.responseBody.close()
        }
        server.createContext("/final") { exchange ->
            finalHits.incrementAndGet()
            val bytes = "cached-through-redirect".encodeToByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val resolver = resolverFor()
            val options =
                ResolutionOptions(
                    requireHttps = false,
                    maxRedirects = 1,
                    maxCacheAgeMs = 60_000,
                    maxBodyBytes = 1_024,
                )

            resolver.resolve("http://example.com:${server.address.port}/start#one", options)
            val second = resolver.resolve("HTTP://EXAMPLE.COM:${server.address.port}/start#two", options)

            assertEquals(1, redirectHits.get())
            assertEquals(1, finalHits.get())
            assertEquals("cached-through-redirect", second.data.decodeToString())
            assertEquals("http://example.com:${server.address.port}/final", second.sourceUri)
        } finally {
            server.stop(0)
        }
    }

    private fun resolverFor(): HttpTrustListResolver {
        val transport =
            JvmTrustListHttpTransport(
                resolveAddresses = { listOf(java.net.InetAddress.getLoopbackAddress()) },
                addressPolicy = {},
                dialAddressPolicy = { _, _ -> },
            )
        val cacheManager = com.sphereon.core.api.cache.DefaultCacheManager()
        cacheManager.registerBackend(com.sphereon.core.api.cache.MapCacheBackend())
        return HttpTrustListResolver(
            transport,
            com.sphereon.core.api.cache.DefaultCacheService(cacheManager),
        )
    }
}
