/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.did.manager.rest.server

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.dispatch.HttpAdapterDispatcher
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteSelection
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteSelector
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionInstance

/** Test client that exercises the same AppScope selection and SessionScope dispatch split as the host. */
internal class DidManagerTestHttpClient(
    appGraph: AppGraph,
    session: SessionInstance,
) {
    private val selector = (appGraph as HttpAdapterRouteSelector.Graph).httpAdapterRouteSelector
    private val dispatcher = (session.graph as HttpAdapterDispatcher.Graph).httpAdapterDispatcher

    suspend fun dispatch(request: GenericHttpRequest): GenericHttpResponse {
        return when (val selection = selector.select(request.method, request.path)) {
            is HttpAdapterRouteSelection.Selected -> dispatcher.dispatch(request, selection.match)
            is HttpAdapterRouteSelection.NotFound -> GenericHttpResponse(404, body = "Not found")
            is HttpAdapterRouteSelection.Ambiguous,
            is HttpAdapterRouteSelection.Misconfigured -> GenericHttpResponse(500, body = "Internal server error")
        }
    }
}
