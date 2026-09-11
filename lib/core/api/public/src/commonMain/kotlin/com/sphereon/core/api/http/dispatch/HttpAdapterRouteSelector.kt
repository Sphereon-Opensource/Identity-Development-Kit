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

import com.sphereon.core.api.http.GenericHttpRequest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo

/**
 * App-scoped, metadata-only HTTP route selector.
 *
 * Selection is deliberately limited to the request method and path. It can therefore reject an
 * unserved route before tenant, principal, SessionScope, adapters, or endpoint commands are
 * constructed. Authenticated tenant authority is applied later by [HttpAdapterRouteMatch.applyTo]
 * after the normal ingress and session pipeline has validated it.
 */
interface HttpAdapterRouteSelector {
    fun select(
        method: String,
        path: String,
        allowedAdapterIds: Set<String>? = null,
    ): HttpAdapterRouteSelection

    @ContributesTo(AppScope::class)
    interface Graph {
        val httpAdapterRouteSelector: HttpAdapterRouteSelector
    }
}

sealed interface HttpAdapterRouteSelection {
    data class Selected(
        val match: HttpAdapterRouteMatch,
    ) : HttpAdapterRouteSelection

    data class NotFound(
        val method: String,
        val path: String,
    ) : HttpAdapterRouteSelection

    data class Ambiguous(
        val method: String,
        val path: String,
        val candidates: List<HttpAdapterRouteCandidateSummary>,
    ) : HttpAdapterRouteSelection

    data class Misconfigured(
        val message: String,
    ) : HttpAdapterRouteSelection
}

/** Immutable identity of the descriptor route selected before SessionScope creation. */
data class HttpAdapterRouteMatch(
    val adapterId: String,
    val method: String,
    val originalPath: String,
    val normalizedPath: String,
    val matchedPathPattern: String,
    val handlerCommandId: String,
    val tenantIdFromPath: String?,
    val commandId: String? = null,
    val pathParameters: Map<String, String> = emptyMap(),
    val maxRequestBodyBytes: Int? = null,
) {
    /**
     * Applies only transport normalization. A tenant established by validated ingress always wins
     * over the public path fallback, preserving the existing tenant-authority rule.
     */
    fun applyTo(request: GenericHttpRequest): GenericHttpRequest {
        require(request.method.equals(method, ignoreCase = true) && request.path == originalPath) {
            "Preselected HTTP route does not belong to ${request.method} ${request.path}"
        }
        val effectiveTenantId = request.resolvedTenantId ?: tenantIdFromPath
        val effectivePathParameters =
            if (effectiveTenantId == null) {
                request.pathParameters + pathParameters
            } else {
                request.pathParameters + pathParameters + mapOf("tenantId" to effectiveTenantId)
            }
        return request.copy(path = normalizedPath, pathParameters = effectivePathParameters)
    }
}

data class HttpAdapterRouteCandidateSummary(
    val adapterId: String,
    val normalizedPath: String,
    val serverPrefix: String,
    val adapterBasePath: String,
)
