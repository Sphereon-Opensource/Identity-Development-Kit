/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.manager.rest.server.adapter

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Composite handle over the per-resource DID-manager HTTP adapters
 * ([DidLifecycleHttpAdapter], [VerificationMethodHttpAdapter], …).
 *
 * Each sub-adapter is its own first-class `HttpAdapter` and is auto-discovered by the
 * HTTP host through the `SessionScope` `Set<HttpAdapter>` multibinding — this class is
 * **not** an `HttpAdapter` itself and does not contribute to that set. Its role is to
 * give tests (and any in-process caller that just wants to dispatch a single request
 * against the DID-manager surface) a single injectable entry point that fans out to the
 * matching sub-adapter via [GenericHttpRequest]-based routing.
 *
 * [handleRequest] inspects the request against each sub-adapter's `supports(...)`
 * predicate (path + method match) and delegates to the first one that claims it; if
 * nothing matches, it falls back to invoking the lifecycle adapter so the standard
 * 404/405 error renderer produces a deterministic response.
 *
 * ### Tenant and principal forwarding (`X-Tenant-ID` / `X-User-ID`)
 *
 * Endpoint commands deliberately do NOT extract `X-Tenant-ID` / `X-User-ID` headers
 * themselves. Tenant and principal resolution is the host's responsibility — for the
 * bundled Ktor host (`DidManagerKtorServer`), `KotlinInjectPlugin` extracts both
 * headers via the configured `DefaultTenantResolver` / `DefaultPrincipalResolver`,
 * opens a session under the resolved tenant + principal, and runs the endpoint command
 * inside that session. Hosts mounting these adapters outside Ktor must perform the
 * equivalent extraction in their own request pipeline before invoking [handleRequest].
 */
@Inject
@SingleIn(SessionScope::class)
class DidManagerHttpAdapter(
    private val didLifecycle: DidLifecycleHttpAdapter,
    private val verificationMethod: VerificationMethodHttpAdapter,
    private val verificationRelationship: VerificationRelationshipHttpAdapter,
    private val didService: DidServiceHttpAdapter,
    private val keyMapping: KeyMappingHttpAdapter,
    private val controller: ControllerHttpAdapter,
    private val alsoKnownAs: AlsoKnownAsHttpAdapter,
    private val equivalentId: EquivalentIdHttpAdapter,
    private val documentCache: DocumentCacheHttpAdapter,
    private val capability: CapabilityHttpAdapter,
) {
    private val subAdapters =
        listOf(
            didLifecycle,
            verificationMethod,
            verificationRelationship,
            didService,
            keyMapping,
            controller,
            alsoKnownAs,
            equivalentId,
            documentCache,
            capability,
        )

    /**
     * Aggregated descriptors from every sub-adapter. Each sub-adapter's [describe]
     * prepends the adapter's `adapterBasePath` to every endpoint's `pathPattern`
     * (per [com.sphereon.core.api.http.command.CommandBackedHttpAdapter.describe]),
     * so the entries here are full, host-facing paths — the same shape the
     * catalog and dispatcher route against.
     */
    val endpointDescriptors: List<HttpEndpointDescriptor>
        get() = subAdapters.flatMap { it.describe().endpoints }

    /**
     * Dispatch a request to the sub-adapter that supports it. Falls back to the
     * lifecycle adapter for unmatched requests so the standard error renderer
     * produces a 404/405 response with the expected shape.
     */
    suspend fun handleRequest(request: GenericHttpRequest): GenericHttpResponse {
        val target = subAdapters.firstOrNull { it.supports(request) } ?: didLifecycle
        return target.handleRequest(request)
    }

    @ContributesTo(SessionScope::class)
    interface Graph {
        val didManagerHttpAdapter: DidManagerHttpAdapter
    }
}
