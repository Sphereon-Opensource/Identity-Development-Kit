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
 *
 */

package com.sphereon.did.resolver

import com.sphereon.core.compat.JsExportCompat
import kotlin.time.Duration

/**
 * Cache interface for DID resolution results.
 *
 * Implementations provide caching of resolved DID documents to reduce
 * network calls for methods like did:web that fetch from remote servers.
 */
@JsExportCompat
interface DidResolutionCache {
    /**
     * Gets a cached resolution result.
     *
     * @param did The DID to look up
     * @return The cached result, or null if not cached or expired
     */
    suspend fun get(did: String): DidResolutionResult?

    /**
     * Puts a resolution result in the cache.
     *
     * @param did The DID
     * @param result The resolution result
     * @param ttl Optional custom TTL (uses default if not specified)
     */
    suspend fun put(
        did: String,
        result: DidResolutionResult,
        ttl: Duration? = null,
    )

    /**
     * Removes a cached result.
     *
     * @param did The DID to remove
     */
    suspend fun remove(did: String)

    /**
     * Clears all cached results.
     */
    suspend fun clear()

    /**
     * Gets the current cache size.
     */
    suspend fun size(): Long

    /**
     * Checks if a DID is cached (and not expired).
     */
    suspend fun contains(did: String): Boolean
}
