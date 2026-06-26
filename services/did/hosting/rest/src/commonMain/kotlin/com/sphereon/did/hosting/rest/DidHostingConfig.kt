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
 *
 */

package com.sphereon.did.hosting.rest

import com.sphereon.core.api.conf.AppConfigService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn

/**
 * Resolves the (app-level) mount + cache config for the public DID-document hosting surface:
 *
 * ```yaml
 * did:
 *   hosting:
 *     base-path: ""        # default "" (root) — did:web REQUIRES /.well-known/did.json at the host root
 *     cache-max-age-seconds: 300
 * ```
 *
 * [basePath] defaults to the host root because did:web resolution fixes the document at
 * `https://<host>/.well-known/did.json`; only override it when an upstream proxy strips a prefix.
 * [AppConfigService] is injected via an optional [Provider] so a deployment (or a stub test graph)
 * without it still resolves and falls back to the defaults.
 */
@Inject
@SingleIn(AppScope::class)
class DidHostingConfig(
    private val appConfigProvider: Provider<AppConfigService>? = null,
) {
    /** Configured mount, normalised to no trailing slash. Empty string = host root. */
    val basePath: String by lazy {
        val configured = appConfigProvider?.invoke()?.getPropertyAsString(BASE_PATH_KEY)
        normalizeBasePath(configured ?: DEFAULT_BASE_PATH)
    }

    /** Fallback `Cache-Control: max-age` when a hosting provider supplies no method TTL. */
    val defaultCacheMaxAgeSeconds: Long by lazy {
        appConfigProvider
            ?.invoke()
            ?.getPropertyAsString(CACHE_MAX_AGE_KEY)
            ?.toLongOrNull()
            ?.takeIf { it >= 0 }
            ?: DidHostingApiConstants.DEFAULT_CACHE_MAX_AGE_SECONDS
    }

    /**
     * Tenant used by public DID hosting when host/JWT tenant resolution did not stamp a concrete
     * tenant. SQL-backed repositories require this for single-tenant/public-host deployments.
     */
    val publicFallbackTenantId: String? by lazy {
        val appConfig = appConfigProvider?.invoke() ?: return@lazy null
        PUBLIC_FALLBACK_TENANT_KEYS
            .asSequence()
            .mapNotNull { key -> appConfig.getPropertyAsString(key)?.trim()?.takeIf { it.isNotEmpty() } }
            .firstOrNull()
    }

    companion object {
        const val BASE_PATH_KEY: String = "did.hosting.basePath"
        const val CACHE_MAX_AGE_KEY: String = "did.hosting.cacheMaxAgeSeconds"
        const val PUBLIC_FALLBACK_TENANT_ID_KEY: String = "did.hosting.publicFallbackTenantId"
        const val PUBLIC_FALLBACK_TENANT_ID_KEBAB_KEY: String = "did.hosting.public-fallback-tenant-id"
        const val TENANT_RESOLUTION_FALLBACK_TENANT_ID_KEY: String = "tenant.resolution.fallback.tenant-id"
        const val DEFAULT_BASE_PATH: String = ""

        private val PUBLIC_FALLBACK_TENANT_KEYS =
            listOf(
                PUBLIC_FALLBACK_TENANT_ID_KEY,
                PUBLIC_FALLBACK_TENANT_ID_KEBAB_KEY,
                TENANT_RESOLUTION_FALLBACK_TENANT_ID_KEY,
            )

        /** Empty stays empty (root mount); otherwise leading slash, no trailing slash. */
        internal fun normalizeBasePath(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            return when {
                trimmed.isEmpty() -> ""
                trimmed.startsWith("/") -> trimmed
                else -> "/$trimmed"
            }
        }
    }
}
