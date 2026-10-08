/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.di.context

import com.sphereon.core.compat.JsExportCompat

/**
 * Per-request seam exposing a tenant id that overrides the base session tenant.
 *
 * The HTTP adapter dispatcher writes here when a request peels into a child
 * tenant via [com.sphereon.core.api.http.command.TenantPathPolicy.LeadingSlug] or
 * [com.sphereon.core.api.http.command.TenantPathPolicy.WellKnownSuffix] — the
 * peeled tenant is the one the endpoint should serve, even though the base
 * tenant from JWT/host might be the parent.
 *
 * `currentTenantId()` returns null when no peel has occurred for this request;
 * downstream consumers fall back to the base tenant from `SessionExecution`. The
 * `SessionExecution` default implementation already does that fallback so most
 * code reads `sessionExecution.tenantId` and gets the right value transparently.
 */
@JsExportCompat
interface ResolvedTenantIdProvider {
    /** Returns the descended tenant id, or null when no path peel has occurred. */
    fun currentTenantId(): String?
}

/**
 * Mutable counterpart used by the HTTP adapter dispatcher. Set on a successful
 * path peel; cleared in a `finally` block once the matched endpoint returns so
 * the holder doesn't leak across unrelated dispatches that share the session.
 */
@JsExportCompat
interface MutableResolvedTenantIdProvider : ResolvedTenantIdProvider {
    fun setCurrentTenantId(tenantId: String)

    fun clearCurrentTenantId()
}
