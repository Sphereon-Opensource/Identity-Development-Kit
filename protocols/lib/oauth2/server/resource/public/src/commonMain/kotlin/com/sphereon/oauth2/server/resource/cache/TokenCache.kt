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

package com.sphereon.oauth2.server.resource.cache

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.oauth2.server.resource.model.TokenPayload
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Instant

/**
 * Token validation result cache
 *
 * Caches validated access tokens to reduce load on authorization server
 * and improve response time.
 *
 * **Cache strategy**:
 * - JWT tokens: Cache until expiration (long TTL)
 * - Introspection results: Cache with short TTL (e.g., 60 seconds) to allow revocation detection
 * - Cache key: Token string or hash
 * - MUST NOT cache invalid tokens
 *
 * **Implementation notes**:
 * - Implementations MUST be thread-safe for concurrent access
 * - Implementations SHOULD support distributed caching for multi-instance deployments
 * - Implementations SHOULD implement cache eviction (LRU, TTL)
 * - Cache size SHOULD be bounded to prevent memory exhaustion
 *
 * **Security considerations**:
 * - Cache MUST NOT persist tokens to disk (memory only)
 * - Cache TTL for introspection creates revocation detection delay
 * - Shorter TTL = more introspection requests but faster revocation detection
 * - Longer TTL = fewer introspection requests but slower revocation detection
 */

@OptIn(ExperimentalObjCName::class)
@ObjCName("TokenCache", exact = true)
@JsExportCompat
interface TokenCache {
    /**
     * Gets a cached token payload
     *
     * @param token The access token string
     * @return Cached payload if present and not expired, null otherwise
     */
    suspend fun get(token: String): TokenPayload?

    /**
     * Caches a validated token payload
     *
     * @param token The access token string
     * @param payload The validated payload
     * @param expiresAt Token expiration time (cache until this time)
     */
    suspend fun put(
        token: String,
        payload: TokenPayload,
        expiresAt: Instant,
    )

    /**
     * Removes a token from the cache
     *
     * Used for explicit invalidation (e.g., token revocation)
     *
     * @param token The access token string
     */
    suspend fun remove(token: String)

    /**
     * Clears all cached tokens
     *
     * Used for cache maintenance or testing
     */
    suspend fun clear()
}
