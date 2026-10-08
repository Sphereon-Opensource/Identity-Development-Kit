/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.trust.core.resolver

import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.CacheTtlConfig
import com.sphereon.core.api.cache.DefaultCacheManager
import com.sphereon.core.api.cache.DefaultCacheService
import com.sphereon.core.api.cache.MapCacheBackend
import com.sphereon.core.api.cache.ScopedCache
import com.sphereon.core.api.encodeToBase64
import com.sphereon.trust.core.TrustDiagnosticReasonCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class TrustListResolutionCachePolicyTest {
    @Test
    fun stricterCallerBodyLimitCannotReuseCachedBytes() = runBlocking {
        val transport = QueueTrustListHttpTransport()
        transport.enqueue(response(body = "cached"))
        transport.enqueue(response(body = "fresh"))
        val fixture = fixtureFor(transport)
        val uri = "https://example.com/current-body-limit.xml"

        fixture.resolver.resolve(uri, ResolutionOptions(maxBodyBytes = 128))
        val refreshed = fixture.resolver.resolve(uri, ResolutionOptions(maxBodyBytes = 5))

        assertEquals("fresh", refreshed.data.decodeToString())
        assertEquals(2, transport.calls.size)
    }

    @Test
    fun stricterCallerCacheAgeCannotReuseCachedBytes() = runBlocking {
        val transport = QueueTrustListHttpTransport()
        transport.enqueue(response(body = "cached"))
        transport.enqueue(response(body = "fresh"))
        val fixture = fixtureFor(transport)
        val uri = "https://example.com/current-cache-age.xml"

        fixture.resolver.resolve(uri, ResolutionOptions(maxCacheAgeMs = 60_000, maxBodyBytes = 128))
        val refreshed =
            fixture.resolver.resolve(
                uri,
                ResolutionOptions(maxCacheAgeMs = 0, maxBodyBytes = 128),
            )

        assertEquals("fresh", refreshed.data.decodeToString())
        assertEquals(2, transport.calls.size)
    }

    @Test
    fun stricterCallerSignedNextUpdateCannotReuseCachedBytes() = runBlocking {
        val now = Clock.System.now().toEpochMilliseconds()
        val transport = QueueTrustListHttpTransport()
        transport.enqueue(response(body = "cached"))
        transport.enqueue(response(body = "fresh"))
        val fixture = fixtureFor(transport)
        val uri = "https://example.com/current-signed-bound.xml"

        fixture.resolver.resolve(
            uri,
            ResolutionOptions(
                maxCacheAgeMs = 120_000,
                signedNextUpdateEpochMillis = now + 60_000,
                maxBodyBytes = 128,
            ),
        )
        val refreshed =
            fixture.resolver.resolve(
                uri,
                ResolutionOptions(
                    maxCacheAgeMs = 120_000,
                    signedNextUpdateEpochMillis = now - 1,
                    maxBodyBytes = 128,
                ),
            )

        assertEquals("fresh", refreshed.data.decodeToString())
        assertEquals(2, transport.calls.size)
    }

    @Test
    fun legacyRawBase64CacheEntryIsFetchedBeforeReuse() = runBlocking {
        val transport = QueueTrustListHttpTransport()
        transport.enqueue(response(body = "fresh"))
        val fixture = fixtureFor(transport)
        val uri = "https://example.com/legacy-cache.xml"
        fixture.cache.putApp(uri, "legacy".encodeToByteArray().encodeToBase64(), null)

        val result = fixture.resolver.resolve(uri, ResolutionOptions(maxBodyBytes = 128))

        assertEquals("fresh", result.data.decodeToString())
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun incompleteFreshnessMetadataIsFetchedBeforeReuse() = runBlocking {
        val transport = QueueTrustListHttpTransport()
        transport.enqueue(response(body = "fresh"))
        val fixture = fixtureFor(transport)
        val uri = "https://example.com/incomplete-cache.xml"
        fixture.cache.putApp(uri, cacheEnvelope("legacy", uri, Clock.System.now().toEpochMilliseconds(), "null"), null)

        val result = fixture.resolver.resolve(uri, ResolutionOptions(maxBodyBytes = 128))

        assertEquals("fresh", result.data.decodeToString())
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun futureRetrievedAtMetadataIsFetchedBeforeReuse() = runBlocking {
        val now = Clock.System.now().toEpochMilliseconds()
        val transport = QueueTrustListHttpTransport()
        transport.enqueue(response(body = "fresh"))
        val fixture = fixtureFor(transport)
        val uri = "https://example.com/future-cache.xml"
        fixture.cache.putApp(uri, cacheEnvelope("future", uri, now + 60_000, "60000"), null)

        val result = fixture.resolver.resolve(uri, ResolutionOptions(maxBodyBytes = 128))

        assertEquals("fresh", result.data.decodeToString())
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun expiredCachedEntryIsReplacedAndReportsTheExpiryReason() = runBlocking {
        val transport = QueueTrustListHttpTransport()
        transport.enqueue(response(body = "first"))
        transport.enqueue(response(body = "second"))
        val resolver = resolverFor(transport)
        val uri = "https://example.com/cache-expiry.xml"

        resolver.resolve(uri, ResolutionOptions(maxCacheAgeMs = 1, maxBodyBytes = 128))
        delay(25)
        val refreshed = resolver.resolve(uri, ResolutionOptions(maxCacheAgeMs = 1, maxBodyBytes = 128))

        assertEquals("second", refreshed.data.decodeToString())
        assertEquals(2, transport.calls.size)
        assertEquals(
            TrustDiagnosticReasonCodes.TRUST_LIST_CACHE_EXPIRED,
            refreshed.cacheMetadata?.diagnosticReasonCode,
        )
    }

    @Test
    fun noCacheResponseIsReturnedButNeverReusedAndReportsRevalidation() = runBlocking {
        val transport = QueueTrustListHttpTransport()
        transport.enqueue(response(body = "first", headers = mapOf("Cache-Control" to "no-cache")))
        transport.enqueue(response(body = "second", headers = mapOf("Cache-Control" to "no-cache")))
        val resolver = resolverFor(transport)
        val options = ResolutionOptions(maxCacheAgeMs = 60_000, maxBodyBytes = 128)

        val first = resolver.resolve("https://example.com/no-cache.xml", options)
        val second = resolver.resolve("https://example.com/no-cache.xml", options)

        assertTrue(!first.fromCache)
        assertTrue(!second.fromCache)
        assertEquals(2, transport.calls.size)
        assertEquals(
            TrustDiagnosticReasonCodes.TRUST_LIST_CACHE_REVALIDATION_REQUIRED,
            first.cacheMetadata?.diagnosticReasonCode,
        )
    }

    @Test
    fun signedNextUpdateAndHttpExpiresAreExposedAndCapReuse() = runBlocking {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val transport = QueueTrustListHttpTransport()
        transport.enqueue(
            response(
                body = "bounded",
                expiresAtEpochMillis = now + 60_000,
            ),
        )
        val resolver = resolverFor(transport)
        val result =
            resolver.resolve(
                "https://example.com/signed-bound.xml",
                ResolutionOptions(
                    maxCacheAgeMs = 120_000,
                    signedNextUpdateEpochMillis = now + 10_000,
                    maxBodyBytes = 128,
                ),
            )

        assertEquals(now + 10_000, result.cacheMetadata?.signedNextUpdateEpochMillis)
        assertEquals(now + 60_000, result.cacheMetadata?.expiresAtEpochMillis)
        assertTrue((result.cacheMetadata?.effectiveTtlMs ?: Long.MAX_VALUE) in 0..10_000)
    }

    private data class ResolverFixture(
        val resolver: HttpTrustListResolver,
        val cache: ScopedCache<String, String>,
    )

    private fun fixtureFor(transport: QueueTrustListHttpTransport): ResolverFixture {
        val cacheManager = DefaultCacheManager()
        cacheManager.registerBackend(MapCacheBackend())
        val cacheService = DefaultCacheService(cacheManager)
        return ResolverFixture(
            resolver = HttpTrustListResolver(transport, cacheService),
            cache =
                cacheService.getCache(
                    CacheRequirements(
                        namespace = "trust.resolver.http",
                        ttlConfig = CacheTtlConfig(app = 60.minutes),
                    ),
                ),
        )
    }

    private fun resolverFor(transport: QueueTrustListHttpTransport): HttpTrustListResolver = fixtureFor(transport).resolver

    private fun cacheEnvelope(
        data: String,
        sourceUri: String,
        retrievedAt: Long,
        effectiveTtlMs: String,
    ): String =
        """
        {"dataBase64":"${data.encodeToByteArray().encodeToBase64()}","sourceUri":"$sourceUri","contentType":null,"fromCache":false,"retrievedAt":$retrievedAt,"cacheToken":null,"cacheMetadata":{"cacheControl":null,"expiresAtEpochMillis":null,"signedNextUpdateEpochMillis":null,"effectiveTtlMs":$effectiveTtlMs,"noStore":false,"revalidationRequired":false,"diagnosticReasonCode":null}}
        """.trimIndent()

    private fun response(
        body: String,
        headers: Map<String, String> = emptyMap(),
        expiresAtEpochMillis: Long? = null,
    ): TrustListHttpResponse =
        TrustListHttpResponse(
            statusCode = 200,
            statusDescription = "OK",
            headers = headers,
            body = OneChunkBody(body.encodeToByteArray()),
            expiresAtEpochMillis = expiresAtEpochMillis,
            closeAction = {},
        )

    private class QueueTrustListHttpTransport : TrustListHttpTransport {
        val calls = mutableListOf<String>()
        private val responses = ArrayDeque<TrustListHttpResponse>()

        fun enqueue(response: TrustListHttpResponse) {
            responses.addLast(response)
        }

        override suspend fun execute(uri: String, timeoutMs: Long): TrustListHttpResponse {
            calls += uri
            return responses.removeFirst()
        }
    }

    private class OneChunkBody(
        private val bytes: ByteArray,
    ) : TrustListResponseBody {
        private var consumed = false

        override suspend fun readChunk(): ByteArray? =
            if (consumed) {
                null
            } else {
                consumed = true
                bytes
            }

        override suspend fun cancel() = Unit
    }
}
