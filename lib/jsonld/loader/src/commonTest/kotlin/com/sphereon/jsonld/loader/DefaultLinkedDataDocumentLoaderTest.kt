/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.jsonld.loader

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.cache.CacheGetArgs
import com.sphereon.core.api.cache.CacheGetResult
import com.sphereon.core.api.cache.CacheInvalidateArgs
import com.sphereon.core.api.cache.CacheInvalidateResult
import com.sphereon.core.api.cache.CachePutArgs
import com.sphereon.core.api.cache.CachePutResult
import com.sphereon.core.api.cache.CacheRemoveArgs
import com.sphereon.core.api.cache.CacheRemoveResult
import com.sphereon.core.api.cache.CacheSerializers
import com.sphereon.core.api.cache.CacheService
import com.sphereon.core.api.cache.CacheStatistics
import com.sphereon.core.api.cache.CacheTtlConfig
import com.sphereon.core.api.cache.MapCacheBackend
import com.sphereon.core.api.cache.ScopedCache
import com.sphereon.core.api.cache.ScopedCacheImpl
import com.sphereon.core.api.error.IdkError
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.jsonld.JsonLdError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultLinkedDataDocumentLoaderTest {
    private val bundledIri = "https://example.test/bundled"
    private val remoteIri = "https://example.test/remote"

    @Test
    fun bundledContextResolutionDoesNotConstructHttpClient() = runTest {
        val factory = RecordingFactory()
        val loader = newLoader(factory)

        val result = loader.loadDocument(bundledIri)

        assertTrue(result.isOk, "bundled context should resolve: ${result.errorOrNull()}")
        assertEquals(0, factory.createCount, "bundled resolution must short-circuit before HTTP client construction")
    }

    @Test
    fun remoteResolutionConvertsLazyHttpClientConstructionFailureToTypedError() = runTest {
        val factory = RecordingFactory()
        val loader = newLoader(factory)
        factory.failConstruction = true

        val result = loader.loadDocument(remoteIri)

        assertTrue(result.isErr)
        val error = assertIs<JsonLdError.LoadingDocumentFailed>(result.error)
        assertTrue(error.reason.contains("HTTP client construction failed"), error.reason)
        assertEquals(1, factory.createCount)
    }

    private fun newLoader(factory: RecordingFactory): DefaultLinkedDataDocumentLoader =
        DefaultLinkedDataDocumentLoader(
            httpClientFactory = factory,
            cacheService = TestCacheService(),
            builtInRegistry = MapBackedBuiltInContextRegistry(
                mapOf(
                    bundledIri to buildJsonObject {
                        put("@context", buildJsonObject { put("name", "https://schema.org/name") })
                    },
                ),
            ),
            pinResolver = NoOpIntegrityPinResolver(),
            loadingPolicy = JsonLdDocumentLoadingPolicy.ALLOW_ALL,
        )

    private class RecordingFactory : HttpClientFactory {
        var createCount = 0
        var failConstruction = false

        override fun createClient(options: HttpClientOptions): HttpClient {
            createCount++
            check(!failConstruction) { "construction deliberately failed" }
            return HttpClient(MockEngine { error("request must not be reached in this test") })
        }

        override fun isSupportedOptions(options: HttpClientOptions): Boolean = true

        override fun getEngineTypesSupported(): List<HttpClientEngineType> = emptyList()

        override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.OKHTTP
    }

    private class TestCacheService : CacheService {
        private val cache: ScopedCache<String, String> =
            ScopedCacheImpl(
                namespace = "jsonld.context.test",
                backend = MapCacheBackend(),
                keySerializer = CacheSerializers.string,
                valueSerializer = CacheSerializers.string,
                ttlConfig = CacheTtlConfig.DEFAULT,
            )

        override fun getCache(requirements: com.sphereon.core.api.cache.CacheRequirements): ScopedCache<String, String> = cache

        override suspend fun get(args: CacheGetArgs): IdkResult<CacheGetResult, IdkError> = error("unused")

        override suspend fun put(args: CachePutArgs): IdkResult<CachePutResult, IdkError> = error("unused")

        override suspend fun remove(args: CacheRemoveArgs): IdkResult<CacheRemoveResult, IdkError> = error("unused")

        override suspend fun invalidate(args: CacheInvalidateArgs): IdkResult<CacheInvalidateResult, IdkError> = error("unused")

        override suspend fun invalidateTenant(tenantId: String): IdkResult<CacheInvalidateResult, IdkError> = error("unused")

        override suspend fun invalidatePrincipal(tenantId: String, principalId: String): IdkResult<CacheInvalidateResult, IdkError> = error("unused")

        override fun stats(): Map<String, CacheStatistics> = emptyMap()

        override suspend fun isHealthy(): Boolean = true
    }
}
