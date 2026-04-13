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

package com.sphereon.core.api.cache

import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Statistics for a cache instance.
 *
 * Provides visibility into cache performance and behavior.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheStatistics", exact = true)
data class CacheStatistics(
    /** Number of successful cache lookups */
    val hits: Long = 0L,
    /** Number of cache misses */
    val misses: Long = 0L,
    /** Number of entries evicted due to capacity constraints */
    val evictions: Long = 0L,
    /** Current number of entries in the cache */
    val size: Long = 0L,
    /** Maximum number of entries the cache can hold (0 if unlimited) */
    val maxSize: Long = 0L,
    /** Number of entries expired due to TTL */
    val expirations: Long = 0L,
) {
    /**
     * Hit rate as a percentage (0.0 to 1.0).
     * Returns 0.0 if no requests have been made.
     */
    val hitRate: Double
        get() {
            val total = hits + misses
            return if (total == 0L) {
                0.0
            } else {
                hits.toDouble() / total
            }
        }

    /**
     * Miss rate as a percentage (0.0 to 1.0).
     * Returns 0.0 if no requests have been made.
     */
    val missRate: Double
        get() = 1.0 - hitRate

    /**
     * Total number of cache requests (hits + misses).
     */
    val totalRequests: Long
        get() = hits + misses

    /**
     * Capacity utilization as a percentage (0.0 to 1.0).
     * Returns 0.0 if maxSize is 0 (unlimited).
     */
    val utilization: Double
        get() =
            if (maxSize == 0L) {
                0.0
            } else {
                size.toDouble() / maxSize
            }

    companion object {
        val EMPTY = CacheStatistics()
    }
}
