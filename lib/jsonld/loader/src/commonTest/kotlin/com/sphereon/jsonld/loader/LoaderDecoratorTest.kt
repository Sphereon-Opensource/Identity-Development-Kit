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
import kotlin.test.assertIs

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
    fun allowlistedLoaderRejectsUntrustedContextBeforeDelegating() =
        runTest {
            val terminal =
                FakeLoader(
                    Ok(LinkedDataDocument(documentUrl = sampleIri, content = sampleContent)),
                )
            val loader =
                AllowlistedLinkedDataDocumentLoader(
                    next = terminal,
                    policy = JsonLdDocumentLoadingPolicy.allowOnly(setOf("https://trusted.test/context")),
                )

            val result = loader.loadDocument(sampleIri)

            assertTrue(result.isErr)
            assertIs<JsonLdError.DocumentNotAllowed>(result.error)
            assertEquals(0, terminal.callCount, "disallowed context must not reach the network loader")
        }

    @Test
    fun allowlistedLoaderRejectsUntrustedRedirectTarget() =
        runTest {
            val terminal =
                FakeLoader(
                    Ok(
                        LinkedDataDocument(
                            documentUrl = "https://untrusted.test/context",
                            content = sampleContent,
                        ),
                    ),
                )
            val loader =
                AllowlistedLinkedDataDocumentLoader(
                    next = terminal,
                    policy = JsonLdDocumentLoadingPolicy.allowOnly(setOf(sampleIri)),
                )

            val result = loader.loadDocument(sampleIri)

            assertTrue(result.isErr)
            assertIs<JsonLdError.DocumentNotAllowed>(result.error)
        }

    @Test
    fun allowlistedLoaderRejectsUntrustedHttpContextLink() =
        runTest {
            val terminal =
                FakeLoader(
                    Ok(
                        LinkedDataDocument(
                            documentUrl = sampleIri,
                            content = sampleContent,
                            contextUrl = "https://untrusted.test/context",
                        ),
                    ),
                )
            val loader =
                AllowlistedLinkedDataDocumentLoader(
                    next = terminal,
                    policy = JsonLdDocumentLoadingPolicy.allowOnly(setOf(sampleIri)),
                )

            val result = loader.loadDocument(sampleIri)

            assertTrue(result.isErr)
            assertIs<JsonLdError.DocumentNotAllowed>(result.error)
        }

    @Test
    fun allowOnlyNormalizesOnlyCaseInsensitiveIriPartsAndKeepsPortPathExact() =
        runTest {
            val policy = JsonLdDocumentLoadingPolicy.allowOnly(setOf("https://EXAMPLE.test/context"))

            assertTrue(policy.isAllowed("https://example.TEST/context"))
            assertTrue(!policy.isAllowed("https://example.test/context/child"))
            assertTrue(!policy.isAllowed("https://example.test:443/context"))
            assertTrue(!policy.isAllowed("https://example.test@evil.test/context"))
        }

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
    fun cachedHitPreservesRemoteDocumentMetadata() =
        runTest {
            val finalUrl = "https://example.com/documents/item.json"
            val terminal =
                FakeLoader(
                    Ok(
                        LinkedDataDocument(
                            documentUrl = finalUrl,
                            content = sampleContent,
                            contextUrl = "https://example.com/contexts/context.jsonld",
                            contentType = "application/json; charset=utf-8",
                            profile = "https://example.com/profile",
                        ),
                    ),
                )
            val cached = CachedLinkedDataDocumentLoader(next = terminal, cache = newCache())

            val first = cached.loadDocument(sampleIri)
            val second = cached.loadDocument(sampleIri)

            assertTrue(first.isOk)
            assertTrue(second.isOk)
            assertEquals(first.value, second.value)
            assertEquals(1, terminal.callCount)
        }

    @Test
    fun integrityPinningRemainsInForceOnCacheHits() =
        runTest {
            val expected = sha256HexOfJcs(sampleContent)
            var pinLookups = 0
            val terminal =
                FakeLoader(
                    Ok(LinkedDataDocument(documentUrl = sampleIri, content = sampleContent)),
                )
            val cached = CachedLinkedDataDocumentLoader(next = terminal, cache = newCache())
            val pinning =
                IntegrityPinningLinkedDataDocumentLoader(
                    next = cached,
                    pins = IntegrityPinResolver {
                        pinLookups++
                        expected
                    },
                )

            val first = pinning.loadDocument(sampleIri)
            val second = pinning.loadDocument(sampleIri)

            assertTrue(first.isOk)
            assertTrue(second.isOk)
            assertEquals(2, pinLookups, "pin policy must run for both live and cached documents")
            assertEquals(1, terminal.callCount)
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
    fun integrityPinningUsesResolvedAlternateDocumentUrl() =
        runTest {
            val alias = "https://example.com/alias"
            val alternate = "https://example.com/alternate.jsonld"
            val expected = sha256HexOfJcs(sampleContent)
            val terminal = FakeLoader(
                Ok(LinkedDataDocument(documentUrl = alternate, content = sampleContent)),
            )
            val pinning = IntegrityPinningLinkedDataDocumentLoader(
                next = terminal,
                pins = IntegrityPinResolver.of(mapOf(alternate to expected)),
            )

            val result = pinning.loadDocument(alias)

            assertTrue(result.isOk)
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
