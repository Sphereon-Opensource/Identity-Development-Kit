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

package com.sphereon.openid.oid4vp.common.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat

/**
 * Base store abstraction for OpenID4VP RP data.
 *
 * Notes:
 * - TTL is mandatory at write-time (security requirement).
 * - Implementations must enforce expiration on reads and housekeeping.
 * - Implementations should be thread-safe.
 */
interface Oid4vpStore<K : Any, V : Any> {
    /**
     * Store [value] under [key] with a TTL (in seconds).
     *
     * @return metadata including createdAt and expiresAt (epoch millis).
     */
    suspend fun put(
        key: K,
        value: V,
        ttlSeconds: Long,
    ): IdkResult<StoreMetadata, IdkError>

    /**
     * Retrieve the stored value for [key], or null if not found or expired.
     */
    suspend fun get(key: K): IdkResult<V?, IdkError>

    /**
     * Retrieve a stored value plus metadata for [key], or null if not found or expired.
     */
    suspend fun getEntry(key: K): IdkResult<StoredEntry<V>?, IdkError>

    /**
     * Delete the entry for [key].
     *
     * @return true if deleted, false if not found.
     */
    suspend fun delete(key: K): IdkResult<Boolean, IdkError>

    /**
     * Check whether [key] exists and is not expired.
     */
    suspend fun exists(key: K): IdkResult<Boolean, IdkError>

    /**
     * Update the TTL for an existing entry (sliding expiration).
     *
     * @return true if updated, false if not found.
     */
    suspend fun touch(
        key: K,
        ttlSeconds: Long,
    ): IdkResult<Boolean, IdkError>

    /**
     * Remove expired entries.
     *
     * @return number of entries removed.
     */
    suspend fun cleanupExpired(): IdkResult<Int, IdkError>
}

/**
 * Metadata returned after storing an entry.
 *
 * Times are epoch milliseconds.
 */
@JsExportCompat
data class StoreMetadata(
    val createdAt: Long,
    val expiresAt: Long,
)

/**
 * A stored entry and its metadata.
 *
 * Times are epoch milliseconds.
 */
@JsExportCompat
data class StoredEntry<V : Any>(
    val value: V,
    val createdAt: Long,
    val expiresAt: Long,
)
