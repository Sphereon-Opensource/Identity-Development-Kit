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
import com.sphereon.core.api.http.dispatch.HttpAdapterCatalog
import com.sphereon.core.api.http.dispatch.HttpAdapterDispatcher
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteSelection
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteSelector
import com.sphereon.core.api.http.response.errorResponse
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Composite handle over the per-resource DID-manager HTTP adapters
 * ([DidLifecycleHttpAdapter], [VerificationMethodHttpAdapter], …).
 *
 * Each sub-adapter is its own first-class `HttpAdapter` and is auto-discovered by the
 * HTTP host through a keyed lazy SessionScope map. This class is not an `HttpAdapter`; it gives
 * tests and in-process callers a facade over AppScope route selection plus resolved dispatch.
 *
 * [handleRequest] resolves only the selected sub-adapter and returns 404 when the selected route
 * does not belong to the DID-manager surface.
 *
 * ### Tenant and principal resolution
 *
 * Endpoint commands do not inspect caller-controlled identity headers. The host must
 * validate the bearer JWT and establish tenant and principal context before invoking
 * [handleRequest]. Hosts mounting these adapters outside Ktor have the same obligation.
 */
@Inject
@SingleIn(SessionScope::class)
class DidManagerHttpAdapter(
    private val catalog: HttpAdapterCatalog,
    private val routeSelector: HttpAdapterRouteSelector,
    private val dispatcher: HttpAdapterDispatcher,
) {
    /**
     * Aggregated descriptors from every sub-adapter. Each sub-adapter's [describe]
     * prepends the adapter's `adapterBasePath` to every endpoint's `pathPattern`
     * (per [com.sphereon.core.api.http.command.CommandBackedHttpAdapter.describe]),
     * so the entries here are full, host-facing paths — the same shape the
     * catalog and dispatcher route against.
     */
    val endpointDescriptors: List<HttpEndpointDescriptor>
        get() = catalog.descriptions.filter { it.id in ADAPTER_IDS }.flatMap { it.endpoints }

    /**
     * Dispatch a request only when the selected adapter belongs to this DID-manager surface.
     */
    suspend fun handleRequest(request: GenericHttpRequest): GenericHttpResponse =
        when (val selection = routeSelector.select(request.method, request.path)) {
            is HttpAdapterRouteSelection.Selected ->
                if (selection.match.adapterId in ADAPTER_IDS) {
                    dispatcher.dispatch(request, selection.match)
                } else {
                    errorResponse(404, "Not found")
                }
            is HttpAdapterRouteSelection.NotFound -> errorResponse(404, "Not found")
            is HttpAdapterRouteSelection.Ambiguous,
            is HttpAdapterRouteSelection.Misconfigured,
            -> errorResponse(500, "Internal server error")
        }

    @ContributesTo(SessionScope::class)
    interface Graph {
        val didManagerHttpAdapter: DidManagerHttpAdapter
    }

    private companion object {
        val ADAPTER_IDS =
            setOf(
                DidLifecycleHttpAdapter.ID,
                VerificationMethodHttpAdapter.ID,
                VerificationRelationshipHttpAdapter.ID,
                DidServiceHttpAdapter.ID,
                KeyMappingHttpAdapter.ID,
                ControllerHttpAdapter.ID,
                AlsoKnownAsHttpAdapter.ID,
                EquivalentIdHttpAdapter.ID,
                DocumentCacheHttpAdapter.ID,
                CapabilityHttpAdapter.ID,
            )
    }
}
