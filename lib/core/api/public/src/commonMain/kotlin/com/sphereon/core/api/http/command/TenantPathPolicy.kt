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

import com.sphereon.core.compat.JsExportCompat

/**
 * Per-adapter / per-endpoint policy controlling whether the dispatcher peels path
 * segments as routable slugs (tenant or AS instance) before matching endpoint
 * patterns.
 *
 * The policy is opt-in. Adapters that don't need slug routing (admin APIs, health
 * probes, anything where the JWT or host already supplies the routing context)
 * default to [None] and see request paths exactly as the client sent them.
 *
 * The two opt-in shapes correspond to the two URL patterns OAuth2/OIDC/OID4VCI/
 * OpenID-Federation specs use:
 *
 * - [LeadingSlug] — the issuer's path component prefixes the endpoint URL.
 *   `https://platform/<issuer-path>/authorize`, `/token`, `/par`, `/jwks` (when
 *   placed at the issuer path), `/authorize/callback`, `/federation/authorize`.
 *
 * - [WellKnownSuffix] — per RFC 8414 §3 / RFC 8615, the well-known URI is inserted
 *   immediately after the host and the issuer path is appended at the end:
 *   `https://platform/.well-known/openid-configuration/<issuer-path>`,
 *   `/.well-known/oauth-authorization-server/<issuer-path>`,
 *   `/.well-known/openid-credential-issuer/<issuer-path>`,
 *   `/.well-known/openid-federation/<issuer-path>`.
 *
 * Each peeled segment must validate as a routable slug — either a root entity
 * (when no parent context exists yet) or a child of the currently-resolved
 * parent. The dispatcher consults [RoutableSlugLookup] for each peel.
 */
@JsExportCompat
sealed class TenantPathPolicy {
    abstract val required: Boolean

    /**
     * Path is matched as-is. No peel. Default for admin APIs and internal endpoints.
     * The session tenant comes from JWT or host (Layer 1) only.
     */
    object None : TenantPathPolicy() {
        override val required: Boolean = false
    }

    /**
     * Try matching the path as-is first. If no endpoint matches, peel up to
     * [maxDepth] LEADING segments as routable slugs and retry.
     *
     * Pseudo: `/tenantb/authorize` peels `tenantb`, retries `/authorize`. Each
     * peeled segment is validated as a child slug of the currently-resolved
     * parent (or as a root slug when there is no parent yet).
     */
    @JsExportCompat
    data class LeadingSlug(
        val maxDepth: Int = 1,
        override val required: Boolean = false,
    ) : TenantPathPolicy() {
        init {
            require(maxDepth in 1..MAX_DEPTH) { "maxDepth out of range [1, $MAX_DEPTH]: $maxDepth" }
        }
    }

    /**
     * Try matching the path as-is first. If no endpoint matches, peel up to
     * [maxDepth] TRAILING segments as routable slugs and retry on the stripped
     * path. Spec-correct for OAuth2/OIDC/OID4VCI/OpenID-Federation well-known
     * metadata endpoints when the issuer has a path component.
     *
     * Pseudo: `/.well-known/openid-configuration/tenantb` peels `tenantb`,
     * retries `/.well-known/openid-configuration`. Multi-segment peels descend
     * the tenant tree right-to-left along the URL but the parent→child
     * relationship still goes left-to-right (URL semantic order).
     */
    @JsExportCompat
    data class WellKnownSuffix(
        val maxDepth: Int = 1,
        override val required: Boolean = false,
    ) : TenantPathPolicy() {
        init {
            require(maxDepth in 1..MAX_DEPTH) { "maxDepth out of range [1, $MAX_DEPTH]: $maxDepth" }
        }
    }

    companion object {
        /** Hard cap matching the maximum tenant depth the registry allows. */
        const val MAX_DEPTH: Int = 10
    }
}
