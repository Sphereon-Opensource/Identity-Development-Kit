/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.defaults.http

import com.sphereon.core.api.http.command.RoutableSlugLookup
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Default IDK implementation of [RoutableSlugLookup] that returns null for every
 * lookup.
 *
 * This default exists so the dispatcher can always inject [RoutableSlugLookup]
 * cleanly via Metro, even in pure-IDK deployments that don't have AS routing or
 * a tenant database. With this default in place, adapters using
 * [com.sphereon.core.api.http.command.TenantPathPolicy.LeadingSlug] or
 * [com.sphereon.core.api.http.command.TenantPathPolicy.WellKnownSuffix] simply
 * fail every peel and dispatch the path as-is — exactly what a no-routing-aware
 * IDK deployment expects.
 *
 * Higher layers contribute real implementations via Metro `replaces`:
 * - The IDK `lib-oauth2-common-impl` module contributes a config-backed lookup
 *   that reads AS instance slugs from `oauth2.servers.<slug>.*` config keys.
 * - The EDK `lib-tenant-resolution-impl` module contributes a postgres-backed
 *   lookup against `tenant_routing` with parent/child hierarchy support.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class NoOpRoutableSlugLookup : RoutableSlugLookup {
    override suspend fun findRootBySlug(slug: String): RoutableSlugLookup.Resolved? = null

    override suspend fun findChildBySlug(
        parentTenantId: String,
        slug: String
    ): RoutableSlugLookup.Resolved? = null
}
