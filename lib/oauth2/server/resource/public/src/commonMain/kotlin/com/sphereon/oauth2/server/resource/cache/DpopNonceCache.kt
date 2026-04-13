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
 */

package com.sphereon.oauth2.server.resource.cache

import kotlinx.datetime.Instant
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * DPoP proof replay protection cache
 *
 * Tracks DPoP proof JTI (unique identifiers) to prevent replay attacks.
 *
 * **Replay protection strategy**:
 * - Store jti values for time window (e.g., iat claim ± 60 seconds)
 * - Reject DPoP proofs with duplicate jti within time window
 * - Automatically evict expired jti values
 *
 * **Implementation notes**:
 * - Implementations MUST be thread-safe for concurrent access
 * - Implementations SHOULD support distributed caching for multi-instance deployments
 * - Implementations SHOULD implement automatic eviction (TTL)
 * - Cache size SHOULD be bounded to prevent memory exhaustion
 *
 * **Security considerations**:
 * - Time window must match DPoP proof iat validation window
 * - Larger window = more memory usage but better protection
 * - MUST use distributed cache for multi-instance deployments
 * - Single-instance cache creates replay window across instances
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DpopNonceCache", exact = true)
interface DpopNonceCache {

    /**
     * Checks if a DPoP proof jti has been used
     *
     * @param jti The DPoP proof unique identifier (jti claim)
     * @return true if jti has been used, false if new
     */
    suspend fun hasBeenUsed(jti: String): Boolean

    /**
     * Marks a DPoP proof jti as used
     *
     * @param jti The DPoP proof unique identifier (jti claim)
     * @param expiresAt Time when this jti can be removed from cache
     */
    suspend fun markAsUsed(jti: String, expiresAt: Instant)

    /**
     * Clears all tracked jti values
     *
     * Used for cache maintenance or testing
     */
    suspend fun clear()
}
