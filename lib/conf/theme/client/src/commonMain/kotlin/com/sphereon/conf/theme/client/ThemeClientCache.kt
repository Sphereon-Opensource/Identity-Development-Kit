/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.conf.theme.client

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One cached theme service representation.
 *
 * @property body The raw JSON body as served by the theme service
 * @property etag The strong ETag the theme service returned for [body], sent back as
 *   `If-None-Match` on revalidation (null when the response carried none)
 * @property fetchedAtMs Epoch milliseconds of the fetch or the last successful revalidation
 */
data class ThemeClientCacheEntry(
    val body: String,
    val etag: String?,
    val fetchedAtMs: Long,
)

/**
 * Process-wide cache for theme service responses, keyed by the full resolution coordinates
 * (endpoint, tenant, variant, applicationId, ...). The resolvers are session scoped; this cache
 * is held at app scope so every session shares one set of cached representations.
 */
interface ThemeClientCache {
    suspend fun get(key: String): ThemeClientCacheEntry?

    suspend fun put(
        key: String,
        entry: ThemeClientCacheEntry,
    )
}

/**
 * Bounded in-memory LRU implementation of [ThemeClientCache]. Mutex guarded so concurrent
 * sessions can read and revalidate safely; least recently used entries are evicted beyond
 * [MAX_ENTRIES].
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ThemeClientCache>())
class InMemoryThemeClientCache : ThemeClientCache {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, ThemeClientCacheEntry>()

    override suspend fun get(key: String): ThemeClientCacheEntry? =
        mutex.withLock {
            val entry = entries.remove(key) ?: return@withLock null
            entries[key] = entry
            entry
        }

    override suspend fun put(
        key: String,
        entry: ThemeClientCacheEntry,
    ) {
        mutex.withLock {
            entries.remove(key)
            entries[key] = entry
            while (entries.size > MAX_ENTRIES) {
                entries.remove(entries.keys.first())
            }
        }
    }

    companion object {
        const val MAX_ENTRIES: Int = 256
    }
}
