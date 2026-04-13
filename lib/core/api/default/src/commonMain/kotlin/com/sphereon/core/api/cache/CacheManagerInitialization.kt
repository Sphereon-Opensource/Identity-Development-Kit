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

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Initializes the cache manager with all contributed backends.
 *
 * This ensures that backends registered via @IntoSet are properly
 * added to the CacheManager on startup.
 */
@ContributesTo(AppScope::class)
interface CacheManagerInitialization {
    @Provides
    @SingleIn(AppScope::class)
    fun initializeCacheManager(
        cacheManager: DefaultCacheManager,
        backends: Set<CacheBackend>,
    ): CacheManager {
        backends.forEach { cacheManager.registerBackend(it) }
        return cacheManager
    }
}
