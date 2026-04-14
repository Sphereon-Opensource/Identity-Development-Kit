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

import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Combines two backends with read-through and/or write-through semantics.
 *
 * Behavior:
 * - GET: Try primary first, read-through from secondary on miss
 * - SET: Write to primary, optionally write-through to secondary
 * - DELETE: Delete from both backends
 *
 * Typically used to combine:
 * - Local (fast) as primary + Distributed (shared) as secondary
 * - Memory (volatile) as primary + Persistent (durable) as secondary
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("LayeredCacheBackend", exact = true)
class LayeredCacheBackend(
    private val primary: CacheBackend,
    private val secondary: CacheBackend,
    private val writeThrough: Boolean = false,
) : CacheBackend {
    override val id: String = "${primary.id}+${secondary.id}"

    override val capabilities =
        BackendCapabilities(
            isLocal = primary.capabilities.isLocal,
            isDistributed = primary.capabilities.isDistributed || secondary.capabilities.isDistributed,
            supportsTtl = primary.capabilities.supportsTtl,
            supportsPatternDelete = primary.capabilities.supportsPatternDelete && secondary.capabilities.supportsPatternDelete,
            supportsBatchOps = primary.capabilities.supportsBatchOps,
            isPersistent = primary.capabilities.isPersistent || secondary.capabilities.isPersistent,
        )

    override suspend fun get(key: String): ByteArray? {
        // Try primary first
        primary.get(key)?.let { return it }

        // Read-through from secondary
        return secondary.get(key)?.also { value ->
            // Populate primary cache on read-through
            primary.set(key, value)
        }
    }

    override suspend fun set(
        key: String,
        value: ByteArray,
        ttlMs: Long?,
    ) {
        primary.set(key, value, ttlMs)
        if (writeThrough) {
            secondary.set(key, value, ttlMs)
        }
    }

    override suspend fun delete(key: String): Boolean {
        val primaryDeleted = primary.delete(key)
        // Always try to delete from secondary (even if not write-through for consistency)
        secondary.delete(key)
        return primaryDeleted
    }

    override suspend fun exists(key: String): Boolean = primary.exists(key) || secondary.exists(key)

    override suspend fun getMany(keys: Collection<String>): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()

        // Get from primary
        val primaryResults = primary.getMany(keys)
        result.putAll(primaryResults)

        // Get missing from secondary
        val missingKeys = keys - primaryResults.keys
        if (missingKeys.isNotEmpty()) {
            val secondaryResults = secondary.getMany(missingKeys)
            result.putAll(secondaryResults)

            // Populate primary with secondary results
            if (secondaryResults.isNotEmpty()) {
                primary.setMany(secondaryResults)
            }
        }

        return result
    }

    override suspend fun setMany(
        entries: Map<String, ByteArray>,
        ttlMs: Long?,
    ) {
        primary.setMany(entries, ttlMs)
        if (writeThrough) {
            secondary.setMany(entries, ttlMs)
        }
    }

    override suspend fun deleteByPattern(pattern: String): Int {
        val primaryCount = primary.deleteByPattern(pattern)
        secondary.deleteByPattern(pattern)
        return primaryCount
    }

    override suspend fun keys(pattern: String): List<String> {
        // Union of keys from both backends
        val primaryKeys = primary.keys(pattern).toSet()
        val secondaryKeys = secondary.keys(pattern).toSet()
        return (primaryKeys + secondaryKeys).toList()
    }

    override suspend fun clear() {
        primary.clear()
        if (writeThrough) {
            secondary.clear()
        }
    }

    override suspend fun size(): Long {
        // Return primary size (most relevant for local caching)
        return primary.size()
    }

    override suspend fun isHealthy(): Boolean = primary.isHealthy() && secondary.isHealthy()

    override suspend fun close() {
        primary.close()
        secondary.close()
    }
}
