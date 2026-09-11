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
 */

package com.sphereon.core.api.http.dispatch

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.response.errorResponse
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/** Session dispatcher that resolves only the adapter selected by the AppScope route catalog. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HttpAdapterDispatcher>())
class DefaultHttpAdapterDispatcher(
    private val adapters: Map<String, Lazy<HttpAdapter>>,
    private val execution: SessionExecution,
) : HttpAdapterDispatcher {
    override suspend fun dispatch(
        request: GenericHttpRequest,
        route: HttpAdapterRouteMatch,
    ): GenericHttpResponse {
        if (!request.method.equals(route.method, ignoreCase = true) || request.path != route.originalPath) {
            logFailure(
                reason = "preselected_route_mismatch",
                route = route,
                metadata = mapOf("actualMethod" to request.method),
            )
            return errorResponse(500, "Internal server error")
        }
        return try {
            val adapterProvider = adapters[route.adapterId]
            val alreadyInitialized = adapterProvider?.isInitialized()
            val resolutionStarted = TimeSource.Monotonic.markNow()
            val adapter = adapterProvider?.value
            execution.log.info(
                message = "VDX_HTTP_ROUTE_FIRST_RESOLUTION",
                metadata =
                    mapOf(
                        "stage" to "selected-adapter-resolution",
                        "adapterId" to route.adapterId,
                        "handlerCommandId" to route.handlerCommandId,
                        "outcome" to if (adapter == null) "missing" else "success",
                        "alreadyInitialized" to (alreadyInitialized?.toString() ?: "false"),
                        "durationMs" to resolutionStarted.elapsedNow().inWholeMilliseconds.coerceAtLeast(0L).toString(),
                    ),
            )
            if (adapter == null) {
                logFailure(reason = "runtime_adapter_missing", route = route)
                return errorResponse(500, "Internal server error")
            }
            if (adapter.id != route.adapterId) {
                logFailure(
                    reason = "runtime_adapter_identity_mismatch",
                    route = route,
                    metadata = mapOf("resolvedAdapterId" to adapter.id),
                )
                return errorResponse(500, "Internal server error")
            }
            adapter.handleResolvedRequest(route.applyTo(request), route)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            logFailure(reason = "runtime_dispatch_exception", route = route, failure = failure)
            errorResponse(500, "Internal server error")
        }
    }

    private fun logFailure(
        reason: String,
        route: HttpAdapterRouteMatch? = null,
        failure: Throwable? = null,
        metadata: Map<String, String> = emptyMap(),
    ) {
        val routeMetadata =
            route?.let {
                mapOf(
                    "adapterId" to it.adapterId,
                    "handlerCommandId" to it.handlerCommandId,
                    "method" to it.method,
                    "matchedPathPattern" to it.matchedPathPattern,
                )
            }.orEmpty()
        execution.log.error(
            message = "HTTP_DISPATCH_FAILED",
            exception = failure,
            metadata = routeMetadata + metadata + ("reason" to reason),
        )
    }
}
