/*
 * © 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.did.resolver.impl

import com.sphereon.core.api.cache.CacheLocality
import com.sphereon.core.api.cache.CacheManager
import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.did.resolver.DidResolutionCache
import com.sphereon.did.resolver.DidResolutionResult
import com.sphereon.di.session.SessionScope
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Cache implementation for DID resolution results.
 *
 * Uses [CacheManager] to obtain a local cache backend for in-memory caching with TTL.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DidResolutionCache>())
class DidResolutionCacheImpl(
    private val cacheManager: CacheManager,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val defaultTtl: Duration = DEFAULT_TTL,
) : DidResolutionCache {
    companion object {
        val DEFAULT_TTL: Duration = 300.seconds
        private const val CACHE_NAMESPACE = "did-resolution"
        private val REQUIREMENTS = CacheRequirements(
            namespace = CACHE_NAMESPACE,
            locality = CacheLocality.LOCAL_PREFERRED
        )
    }

    private val backend by lazy {
        cacheManager.getBackends().firstOrNull()
            ?: error("No cache backend registered in CacheManager")
    }

    private fun cacheKey(did: String) = "$CACHE_NAMESPACE:$did"

    override suspend fun get(did: String): DidResolutionResult? {
        val bytes = backend.get(cacheKey(did)) ?: return null
        return json.decodeFromString<DidResolutionResult>(bytes.decodeToString())
    }

    override suspend fun put(did: String, result: DidResolutionResult, ttl: Duration?) {
        val effectiveTtl = ttl ?: defaultTtl
        val bytes = json.encodeToString(DidResolutionResult.serializer(), result).encodeToByteArray()
        backend.set(cacheKey(did), bytes, effectiveTtl.inWholeMilliseconds)
    }

    override suspend fun remove(did: String) {
        backend.delete(cacheKey(did))
    }

    override suspend fun clear() {
        backend.deleteByPattern("$CACHE_NAMESPACE:*")
    }

    override suspend fun size(): Long {
        return backend.keys("$CACHE_NAMESPACE:*").size.toLong()
    }

    override suspend fun contains(did: String): Boolean {
        return backend.exists(cacheKey(did))
    }
}
