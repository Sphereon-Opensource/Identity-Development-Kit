/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Resolves the BASE URL the [HttpAsBridge] uses to reach the OAuth2 authorization server for the
 * current session's tenant (its internal east-west surfaces: `/internal/preauth/register`,
 * `/introspect`, `/userinfo`).
 *
 * The default IDK implementation returns `null`, so the bridge falls back to the statically
 * configured `oid4vci.issuer.as-bridge.internal-url`. That single static in-container URL only
 * works when one AS serves one tenant.
 *
 * In a single-port, multi-tenant EDK deployment the tenant AS serves MANY tenants and resolves the
 * tenant from the request HOST (the public `{tenant}.{base}` gateway host) — an in-container service
 * name (`enterprise-tenant-as`) carries no tenant and is rejected by the domain-first resolver. EDK
 * therefore binds a tenant-aware implementation (in the issuer `rest-tenant` module) that returns the
 * per-tenant AS PUBLIC base URL from the active `OAUTH2_AUTHORIZATION_SERVER` tenant_public_endpoint
 * binding, so the bridge reaches the AS on a host the AS can map back to the right tenant.
 *
 * Returning the SCHEME+HOST only (no path prefix) is intentional: the bridge's targets are AS-root
 * endpoints, not paths under the AS public path prefix.
 */
interface Oid4vciAsBridgeBaseUrlResolver {
    /**
     * The AS base URL (`scheme://host[:port]`, no trailing slash) for the current tenant, or `null`
     * to use the statically configured `oid4vci.issuer.as-bridge.internal-url`.
     */
    suspend fun resolveAsBaseUrl(): String?
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultOid4vciAsBridgeBaseUrlResolver : Oid4vciAsBridgeBaseUrlResolver {
    override suspend fun resolveAsBaseUrl(): String? = null
}
