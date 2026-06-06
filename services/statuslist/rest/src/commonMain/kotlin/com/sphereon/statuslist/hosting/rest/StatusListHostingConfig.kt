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
 * Resolves the configurable mount path for the status-list hosting + admin REST surfaces from
 * deployment (app-level) config:
 *
 * ```yaml
 * statuslists:
 *   hosting:
 *     base-path: /public/statuslists          # default: /statuslists
 *     external-base-url: https://issuer.example  # for the REST publisher to derive statusListUri
 * ```
 *
 * The base path must be known at server startup (the AppScope route-descriptor catalog), so it is an
 * app-level setting — not per-tenant/per-principal. It is the stable, externally referenced root that
 * a hosted `statusListUri` is built on, so issued credentials and the served route never drift.
 *
 * [AppConfigService] is injected via an optional [Provider] so a deployment (or a stub test graph)
 * without it still resolves the graph and falls back to the default
 * [StatusListHostingApiConstants.BASE_PATH].
 */
@Inject
@SingleIn(AppScope::class)
class StatusListHostingConfig(
    private val appConfigProvider: Provider<AppConfigService>? = null,
) {
    /** Configured hosting mount, normalised to a leading slash with no trailing slash. */
    val basePath: String by lazy {
        val configured = appConfigProvider?.invoke()?.getPropertyAsString(BASE_PATH_KEY)?.takeIf { it.isNotBlank() }
        normalizeBasePath(configured ?: StatusListHostingApiConstants.BASE_PATH)
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

    companion object {
        // Same root, protocol-neutral `statuslists` namespace the definitions provider reads (no
        // `sphereon.` prefix — config keys map to the top-level YAML block).
        const val BASE_PATH_KEY = "statuslists.hosting.basePath"
        const val EXTERNAL_BASE_URL_KEY = "statuslists.hosting.externalBaseUrl"

        /** Leading slash, no trailing slash, internal slashes preserved (e.g. `/public/statuslists`). */
        internal fun normalizeBasePath(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        }
    }
}
