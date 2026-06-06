/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.manager.rest.server

import com.sphereon.core.api.conf.AppConfigService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface DidManagerRestConfigModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideDidManagerRestConfig(appConfig: AppConfigService): DidManagerRestConfig =
        DidManagerRestConfig(
            adapterBasePath =
                appConfig
                    .getPropertyAsString(DidManagerRestPaths.BASE_PATH_CONFIG_KEY)
                    ?.takeIf { it.isNotBlank() }
                    ?: DidManagerRestPaths.BASE_PATH,
            // Typed Int read so non-numeric and zero/negative overrides fall back to the
            // default instead of poisoning every list request with a 400.
            maxPageSize =
                appConfig
                    .getProperty(DidManagerRestPaths.MAX_PAGE_SIZE_CONFIG_KEY, Int::class, DidManagerRestPaths.MAX_PAGE_SIZE)
                    ?.takeIf { it > 0 }
                    ?: DidManagerRestPaths.MAX_PAGE_SIZE,
        )
}
