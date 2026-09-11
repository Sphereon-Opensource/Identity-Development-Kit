/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.statuslist.hosting.rest

import com.sphereon.core.api.conf.AppConfigService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn

/**
 * Resolves the configurable mount paths for the status-list PUBLIC hosting and SIMPLE management
 * (admin) REST surfaces from deployment (app-level) config:
 *
 * ```yaml
 * statuslists:
 *   hosting:
 *     base-path: /public/statuslists             # default: /public/statuslists (public, cacheable token)
 *     management-base-path: /api/statuslist/v1   # default: /api/statuslist/v1 (by-index admin)
 *     external-base-url: https://issuer.example  # for the REST publisher to derive statusListUri
 *     cache-max-age-seconds: 0                    # optional public response override; unset uses token TTL
 * ```
 *
 * The hosting + management base paths are decoupled: hosting is the unauthenticated, cacheable
 * token surface; management is the (production-guarded) by-index admin surface. Both must be known
 * at server startup (the AppScope route-descriptor catalog), so they are app-level settings — not
 * per-tenant/per-principal. The hosting path is the stable, externally referenced root that a hosted
 * `statusListUri` is built on, so issued credentials and the served route never drift.
 *
 * [AppConfigService] is injected via an optional [Provider] so a deployment (or a stub test graph)
 * without it still resolves the graph and falls back to the defaults
 * [StatusListHostingApiConstants.BASE_PATH] / [StatusListHostingApiConstants.MANAGEMENT_BASE_PATH].
 */
@Inject
@SingleIn(AppScope::class)
class StatusListHostingConfig(
    private val appConfigProvider: Provider<AppConfigService>? = null,
) {
    /** Configured PUBLIC hosting mount, normalised to a leading slash with no trailing slash. */
    val basePath: String by lazy {
        val configured = appConfigProvider?.invoke()?.getPropertyAsString(BASE_PATH_KEY)?.takeIf { it.isNotBlank() }
        normalizeBasePath(configured ?: StatusListHostingApiConstants.BASE_PATH)
    }

    /**
     * Configured SIMPLE, by-index management (admin) mount, normalised to a leading slash with no
     * trailing slash. Kept separate from [basePath] so the mutating admin routes never share a prefix
     * with the cacheable public hosting surface. Defaults to
     * [StatusListHostingApiConstants.MANAGEMENT_BASE_PATH].
     */
    val managementBasePath: String by lazy {
        val configured = appConfigProvider?.invoke()?.getPropertyAsString(MANAGEMENT_BASE_PATH_KEY)?.takeIf { it.isNotBlank() }
        normalizeBasePath(configured ?: StatusListHostingApiConstants.MANAGEMENT_BASE_PATH)
    }

    /**
     * Public origin this deployment is reachable at (e.g. `https://issuer.example.com`), used to build
     * the absolute `statusListUri` for the REST publisher. Null when unset, in which case a list must
     * configure its `uri` explicitly. Trailing slash trimmed.
     */
    val externalBaseUrl: String? by lazy {
        appConfigProvider
            ?.invoke()
            ?.getPropertyAsString(EXTERNAL_BASE_URL_KEY)
            ?.takeIf { it.isNotBlank() }
            ?.trimEnd('/')
    }

    /**
     * Optional deployment-wide cache freshness override for public status-list responses.
     *
     * When unset, hosting preserves the existing behavior: use the signed token's TTL hint and
     * fall back to [StatusListHostingApiConstants.DEFAULT_CACHE_MAX_AGE_SECONDS]. A value of zero
     * emits `public, max-age=0`, which makes resolvers fetch the current signed publication for
     * each decision without changing (or invalidating) the positive TTL carried by an mdoc CWT.
     * Negative or malformed values fail closed during configuration resolution.
     */
    val cacheMaxAgeSeconds: Long? by lazy {
        val configured =
            appConfigProvider
                ?.invoke()
                ?.getPropertyAsString(CACHE_MAX_AGE_SECONDS_KEY)
                ?: return@lazy null
        val seconds = configured.trim().toLongOrNull()
            ?: throw IllegalArgumentException("$CACHE_MAX_AGE_SECONDS_KEY must be a non-negative integer")
        require(seconds >= 0L) { "$CACHE_MAX_AGE_SECONDS_KEY must be a non-negative integer" }
        seconds
    }

    companion object {
        // Same root, protocol-neutral `statuslists` namespace the definitions provider reads (no
        // `sphereon.` prefix — config keys map to the top-level YAML block).
        const val BASE_PATH_KEY = "statuslists.hosting.basePath"
        const val MANAGEMENT_BASE_PATH_KEY = "statuslists.hosting.managementBasePath"
        const val EXTERNAL_BASE_URL_KEY = "statuslists.hosting.externalBaseUrl"
        const val CACHE_MAX_AGE_SECONDS_KEY = "statuslists.hosting.cacheMaxAgeSeconds"

        /** Leading slash, no trailing slash, internal slashes preserved (e.g. `/public/statuslists`). */
        internal fun normalizeBasePath(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        }
    }
}
