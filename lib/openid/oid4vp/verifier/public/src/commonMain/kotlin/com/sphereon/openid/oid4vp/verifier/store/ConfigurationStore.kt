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

package com.sphereon.openid.oid4vp.verifier.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Store for configuration data used by the RP.
 *
 * Configuration and metadata should be persistent by default (no expiration).
 *
 * Implementations MAY support an explicit TTL to force refresh/rotation behavior in distributed deployments.
 */
interface ConfigurationStore<K : Any, V : Any> : Oid4vpStore<K, V> {
    companion object {
        /**
         * Optional TTL for configurations when you explicitly want periodic refresh.
         */
        const val OPTIONAL_REFRESH_TTL_SECONDS: Long = 60L * 60L * 24L
    }

    /**
     * Store a configuration persistently (no expiration).
     *
     * This is the default desired behavior for configuration and metadata stores.
     */
    suspend fun putPersistent(
        key: K,
        value: V,
    ): IdkResult<StoreMetadata, IdkError>

    /**
     * Upsert a configuration persistently (no expiration).
     */
    suspend fun upsert(
        key: K,
        value: V,
    ): IdkResult<StoreMetadata, IdkError> = putPersistent(key, value)

    /**
     * List known (non-expired) configuration IDs.
     */
    suspend fun listIds(): IdkResult<List<K>, IdkError>

    /**
     * Get all known (non-expired) configuration values.
     */
    suspend fun getAll(): IdkResult<Map<K, V>, IdkError>
}
