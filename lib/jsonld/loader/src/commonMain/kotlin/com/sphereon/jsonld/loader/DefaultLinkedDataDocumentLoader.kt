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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.CacheService
import com.sphereon.core.api.cache.CacheTtlConfig
import com.sphereon.di.session.SessionScope
import com.sphereon.jsonld.JsonLdError
import com.sphereon.jsonld.LinkedDataDocument
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Duration.Companion.hours

/**
 * The IDK default chain:
 *
 *     BuiltIn  →  Cached  →  IntegrityPinning  →  Http
 *
 * - **BuiltIn** short-circuits for canonical W3C and UNTP `@context`
 *   documents that are bundled at build time (no network, no cache).
 * - **Cached** stores fetched bodies in an app-scoped [CacheService] cache
 *   (`namespace = "jsonld.context"`) with a 1-hour default TTL. Cache scope
 *   is app, not tenant: canonical contexts are global.
 * - **IntegrityPinning** verifies a configured SHA-256 pin (over the JCS-
 *   canonicalized content) on documents arriving from the network. The
 *   default-bound [IntegrityPinResolver] is [NoOpIntegrityPinResolver],
 *   which pins nothing; an EDK or VDX layer can replace it via Metro's
 *   `replaces` semantics to introduce tenant-configured pinning.
 * - **Http** is the network terminator using the IDK
 *   [HttpClientFactory] with `enableHttpCache = true` and content
 *   negotiation enabled.
 *
 * Bound at [SessionScope] so tenant-aware [IntegrityPinResolver]
 * implementations see the right tenant via `SessionExecution`. The
 * underlying `BuiltInContextRegistry` and `CacheService` remain app-scoped;
 * each session reuses the shared bundle and cache state.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(scope = SessionScope::class, binding = binding<com.sphereon.jsonld.loader.LinkedDataDocumentLoader>())
class DefaultLinkedDataDocumentLoader(
    httpClientFactory: HttpClientFactory,
    cacheService: CacheService,
    builtInRegistry: BuiltInContextRegistry,
    pinResolver: IntegrityPinResolver,
) : LinkedDataDocumentLoader {
    private val chain: LinkedDataDocumentLoader =
        BuiltInContextLinkedDataDocumentLoader(
            registry = builtInRegistry,
            next =
                CachedLinkedDataDocumentLoader(
                    next =
                        IntegrityPinningLinkedDataDocumentLoader(
                            next =
                                HttpLinkedDataDocumentLoader(
                                    httpClient =
                                        httpClientFactory.createClient(
                                            HttpClientOptions.createDefault().copy(enableHttpCache = true),
                                        ),
                                ),
                            pins = pinResolver,
                        ),
                    cache =
                        cacheService.getCache(
                            CacheRequirements(
                                namespace = JSONLD_CONTEXT_CACHE_NAMESPACE,
                                ttlConfig = CacheTtlConfig(app = 1.hours),
                            ),
                        ),
                ),
        )

    override suspend fun loadDocument(iri: String): IdkResult<LinkedDataDocument, JsonLdError> = chain.loadDocument(iri)

    private companion object {
        const val JSONLD_CONTEXT_CACHE_NAMESPACE = "jsonld.context"
    }
}

/**
 * Default-bound no-op [IntegrityPinResolver]: no pin is configured for any
 * IRI, so [IntegrityPinningLinkedDataDocumentLoader] passes everything
 * through. Replace via Metro's `replaces` mechanism to introduce real
 * pinning, e.g. a `ConfigService`-backed resolver that reads
 * `jsonld.context.pins.*` entries.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(scope = SessionScope::class, binding = binding<IntegrityPinResolver>())
class NoOpIntegrityPinResolver : IntegrityPinResolver {
    override suspend fun pinFor(iri: String): String? = null
}
