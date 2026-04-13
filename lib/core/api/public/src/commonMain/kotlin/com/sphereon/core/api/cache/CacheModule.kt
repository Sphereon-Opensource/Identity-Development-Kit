/*
 * Copyright 2025 Sphereon International B.V.
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

import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * DI module for cache infrastructure.
 *
 * Provides:
 * - CacheManager singleton (initialized with contributed backends)
 * - CacheService for high-level access
 * - CacheConfigLoader for config-driven requirements
 *
 * The default KacheCacheBackend is provided by lib-core-api-default module.
 * Additional backends (REST, Redis, etc.) can be contributed from other modules
 * using @IntoSet with the CacheBackend interface.
 */
@ContributesTo(AppScope::class)
interface CacheModule {

    /**
     * Expose the cache manager's component interface.
     * Allows accessing the cache manager from component hierarchy.
     */
    @ContributesTo(AppScope::class)
    interface Component {
        val cacheManager: CacheManager
        val cacheService: CacheService
        val cacheConfigLoader: CacheConfigLoader
    }
}

/**
 * Extension point for registering custom cache backends.
 *
 * Backend implementations can be contributed using @ContributesBinding:
 *
 * ```kotlin
 * @Inject
 * @SingleIn(AppScope::class)
 * @ContributesBinding(AppScope::class, binding = binding<CacheBackend>())
 * class MyCustomBackend : CacheBackend { ... }
 * ```
 *
 * Or using @IntoSet for multi-binding:
 *
 * ```kotlin
 * @ContributesTo(AppScope::class)
 * interface MyBackendModule {
 *     @Provides
 *     @SingleIn(AppScope::class)
 *     @IntoSet
 *     fun provideMyBackend(): CacheBackend = MyCustomBackend()
 * }
 * ```
 *
 * The DefaultCacheManager will automatically use all contributed backends.
 * The default KacheCacheBackend is provided by lib-core-api-default module.
 */

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
        backends: Set<CacheBackend>
    ): CacheManager {
        backends.forEach { cacheManager.registerBackend(it) }
        return cacheManager
    }
}
