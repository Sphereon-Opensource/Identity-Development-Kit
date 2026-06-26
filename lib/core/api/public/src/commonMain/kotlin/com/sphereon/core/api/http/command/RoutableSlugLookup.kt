/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.http.command

/**
 * SPI used by the IDK adapter dispatcher to validate slugs that get peeled from
 * a request path under [TenantPathPolicy.LeadingSlug] or
 * [TenantPathPolicy.WellKnownSuffix].
 *
 * Two implementations exist:
 *
 * - The IDK default ([ConfigBackedRoutableSlugLookup]) reads slugs from
 *   `ConfigService` — every `oauth2.servers.<slug>.issuer` config key
 *   declares a valid root slug. This keeps IDK's existing config-driven multi-AS
 *   routing first-class without requiring any tenant database.
 *
 * - EDK contributes a postgres-backed implementation (in `lib-tenant-resolution-impl`)
 *   that REPLACES the IDK default via Metro `replaces`. It validates against the
 *   `tenant_routing` registry and supports parent/child hierarchy — `findChildBySlug`
 *   returns non-null only when there's a real `parent_tenant_id` edge in the data.
 *
 * The lookup must NOT itself perform tenant resolution; it ONLY answers the
 * question "is this slug a valid routable entity, and if so, what's its id?". The
 * caller (the dispatcher) is responsible for advancing the session-scoped tenant
 * once a peel has been accepted.
 */
interface RoutableSlugLookup {
    /**
     * Returns a [Resolved] when [slug] names a root routable entity (a top-level
     * tenant or, in IDK's config-driven mode, an AS instance with no parent).
     * Returns null otherwise.
     *
     * Used by the dispatcher's first peel when there is no host-derived base
     * tenant — e.g. `https://saas.com/tenanta/authorize` peels `tenanta` as a
     * root.
     */
    suspend fun findRootBySlug(slug: String): Resolved?

    /**
     * Returns a [Resolved] when [slug] names a child of [parentTenantId]. Returns
     * null if no such child exists. Used by every peel after the first when the
     * dispatcher has already advanced into a tenant.
     *
     * For the IDK default, "child" maps to AS instances declared under the
     * config-resolved parent tenant. For the EDK implementation, "child" maps
     * directly to the `tenant_routing` parent_tenant_id edge.
     */
    suspend fun findChildBySlug(
        parentTenantId: String,
        slug: String
    ): Resolved?

    /**
     * Successful lookup result.
     *
     * [tenantId] is the session-scope tenant id the dispatcher should advance to
     * — for an AS instance peel under the IDK config-driven default this is the
     * already-resolved parent tenant id (the AS instance itself does not become
     * a session tenant; it just narrows the AS instance scope inside an existing
     * tenant). The dispatcher only updates the session tenant when [tenantId]
     * differs from the current session tenant.
     */
    data class Resolved(
        val tenantId: String,
        val slug: String,
    )
}
