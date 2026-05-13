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

package com.sphereon.jsonld.loader

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.cache.CacheSerializers
import com.sphereon.core.api.cache.CacheTtlConfig
import com.sphereon.core.api.cache.MapCacheBackend
import com.sphereon.core.api.cache.ScopedCache
import com.sphereon.core.api.cache.ScopedCacheImpl
import com.sphereon.core.api.json.jcs.Jcs
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.jsonld.JsonLdError
import com.sphereon.jsonld.LinkedDataDocument
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the four decorator loaders in isolation and composed:
 * BuiltIn → Cached → IntegrityPinning → terminator.
 *
 * Network and cache backends are stand-ins; the production composer
 * [DefaultLinkedDataDocumentLoader] wires `HttpClientFactory` and
 * `CacheService` instead.
 */
class LoaderDecoratorTest {
    private val sampleIri = "https://example.com/ctx"
    private val sampleContent: JsonObject =
        buildJsonObject {
            put("@context", buildJsonObject { put("name", "https://schema.org/name") })
        }

    /** A pure-recording loader that returns a fixed result and counts calls. */
    private class FakeLoader(
        private val fixed: IdkResult<LinkedDataDocument, JsonLdError>,
    ) : LinkedDataDocumentLoader {
        var callCount: Int = 0
            private set

        override suspend fun loadDocument(iri: String): IdkResult<LinkedDataDocument, JsonLdError> {
            callCount++
            return fixed
        }
    }

    private fun newCache(): ScopedCache<String, String> =
        ScopedCacheImpl(
            namespace = "jsonld.context.test",
            backend = MapCacheBackend(),
            keySerializer = CacheSerializers.string,
            valueSerializer = CacheSerializers.string,
            ttlConfig = CacheTtlConfig.DEFAULT,
        )

    @Test
    fun builtInDelegatesToNextOnMiss() =
        runTest {
            val terminal =
                FakeLoader(
                    Ok(LinkedDataDocument(documentUrl = sampleIri, content = sampleContent, contentType = "application/ld+json")),
                )
            val builtIn =
                BuiltInContextLinkedDataDocumentLoader(
                    registry = MapBackedBuiltInContextRegistry(emptyMap()),
                    next = terminal,
                )
            val result = builtIn.loadDocument(sampleIri)
            assertTrue(result.isOk)
            assertEquals(1, terminal.callCount)
        }

    @Test
    fun builtInShortCircuitsOnHit() =
        runTest {
            val terminal =
                FakeLoader(
                    Err(JsonLdError.LoadingDocumentFailed(iri = sampleIri, reason = "should not be called")),
                )
            val builtIn =
                BuiltInContextLinkedDataDocumentLoader(
                    registry = MapBackedBuiltInContextRegistry(mapOf(sampleIri to sampleContent)),
                    next = terminal,
                )
            val result = builtIn.loadDocument(sampleIri)
            assertTrue(result.isOk)
            assertEquals(0, terminal.callCount, "registry hit must not delegate")
        }

    @Test
    fun cachedStoresOnMissAndReusesOnHit() =
        runTest {
            val terminal =
                FakeLoader(
                    Ok(LinkedDataDocument(documentUrl = sampleIri, content = sampleContent, contentType = "application/ld+json")),
                )
            val cache = newCache()
            val cached = CachedLinkedDataDocumentLoader(next = terminal, cache = cache)

            val first = cached.loadDocument(sampleIri)
            assertTrue(first.isOk)
            assertEquals(1, terminal.callCount, "first call must hit terminal")

            val second = cached.loadDocument(sampleIri)
            assertTrue(second.isOk)
            assertEquals(1, terminal.callCount, "second call must be served from cache")
            assertEquals(sampleContent, second.value.content)
        }

    @Test
    fun cachedFallsThroughOnTerminalError() =
        runTest {
            val terminal =
                FakeLoader(
                    Err(JsonLdError.LoadingDocumentFailed(iri = sampleIri, reason = "404")),
                )
            val cache = newCache()
            val cached = CachedLinkedDataDocumentLoader(next = terminal, cache = cache)

            val result = cached.loadDocument(sampleIri)
            assertTrue(result.isErr)
            assertNull(cache.getApp(sampleIri), "errors must not be cached")
        }

    @Test
    fun integrityPinningPassesThroughWhenUnpinned() =
        runTest {
            val terminal =
                FakeLoader(
                    Ok(LinkedDataDocument(documentUrl = sampleIri, content = sampleContent, contentType = "application/ld+json")),
                )
            val pinning =
                IntegrityPinningLinkedDataDocumentLoader(
                    next = terminal,
                    pins = IntegrityPinResolver.NONE,
                )
            val result = pinning.loadDocument(sampleIri)
            assertTrue(result.isOk)
        }

    @Test
    fun integrityPinningPassesThroughOnMatchingHash() =
        runTest {
            val expected = sha256HexOfJcs(sampleContent)
            val terminal =
                FakeLoader(
                    Ok(LinkedDataDocument(documentUrl = sampleIri, content = sampleContent, contentType = "application/ld+json")),
                )
            val pinning =
                IntegrityPinningLinkedDataDocumentLoader(
                    next = terminal,
                    pins = IntegrityPinResolver.of(mapOf(sampleIri to expected)),
                )
            val result = pinning.loadDocument(sampleIri)
            assertTrue(result.isOk, "matching pin must not error")
        }

    @Test
    fun integrityPinningFailsOnMismatch() =
        runTest {
            val wrongPin = "0".repeat(64)
            val terminal =
                FakeLoader(
                    Ok(LinkedDataDocument(documentUrl = sampleIri, content = sampleContent, contentType = "application/ld+json")),
                )
            val pinning =
                IntegrityPinningLinkedDataDocumentLoader(
                    next = terminal,
                    pins = IntegrityPinResolver.of(mapOf(sampleIri to wrongPin)),
                )
            val result = pinning.loadDocument(sampleIri)
            assertTrue(result.isErr)
            val mismatch = assertNotNull(result.error as? JsonLdError.IntegrityPinMismatch)
            assertEquals(wrongPin, mismatch.expectedSha256)
            assertEquals(sha256HexOfJcs(sampleContent), mismatch.actualSha256)
        }

    @Test
    fun chainResolvesBuiltInWithoutTouchingNetwork() =
        runTest {
            val networkSpy =
                FakeLoader(
                    Err(JsonLdError.LoadingDocumentFailed(iri = sampleIri, reason = "should not be called")),
                )
            val cache = newCache()
            val chain =
                BuiltInContextLinkedDataDocumentLoader(
                    registry = MapBackedBuiltInContextRegistry(mapOf(sampleIri to sampleContent)),
                    next =
                        CachedLinkedDataDocumentLoader(
                            next =
                                IntegrityPinningLinkedDataDocumentLoader(
                                    next = networkSpy,
                                    pins = IntegrityPinResolver.NONE,
                                ),
                            cache = cache,
                        ),
                )
            val result = chain.loadDocument(sampleIri)
            assertTrue(result.isOk)
            assertEquals(0, networkSpy.callCount, "built-in hit must not reach the network spy")
            assertNull(cache.getApp(sampleIri), "built-in hit must not populate the network cache")
        }

    private fun sha256HexOfJcs(obj: JsonObject): String {
        val canonical = Jcs.canonicalize(obj)
        return hash(canonical, DigestAlg.SHA256)
            .joinToString("") { byte -> ((byte.toInt() and 0xff)).toString(16).padStart(2, '0') }
    }
}
