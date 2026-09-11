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
 *
 */

package com.sphereon.core.api.http

import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteMatch
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat

/**
 * Interface for HTTP adapters that handle REST API requests.
 *
 * This interface provides a universal pattern for implementing HTTP APIs:
 * - Framework-agnostic (works with Spring, Ktor, Lambda, Azure, etc.)
 * - Dependency injection via kotlin-inject
 * - Multiple keyed adapters per application (KMS, mDoc, Signature, etc.)
 * - Easy testing and mocking
 *
 * Implementation Guidelines:
 * - Be annotated with Inject, SingleIn(SessionScope), ContributesIntoMap, and StringKey
 * - Define a companion object with a const val ID
 * - Execute only the route selected from application-scoped metadata
 * - Delegate the selected route to its handler
 * - Map exceptions to HTTP status codes
 * - Return GenericHttpResponse with proper status and headers
 *
 * Example:
 * ```
 * @Inject
 * @SingleIn(SessionScope::class)
 * @ContributesIntoMap(SessionScope::class, binding = binding<HttpAdapter>())
 * @StringKey(KmsHttpAdapter.ID)
 * class KmsHttpAdapter : HttpAdapter {
 *     companion object {
 *         const val ID = "kms-api"
 *     }
 *
 *     override val id: String = ID
 *
 *     override fun describe(): HttpAdapterDescription = TODO("Provide mount + endpoints")
 *
 *     override suspend fun handleResolvedRequest(
 *         request: GenericHttpRequest,
 *         route: HttpAdapterRouteMatch,
 *     ): GenericHttpResponse {
 *         // implementation
 *     }
 * }
 * ```
 */
@JsExportCompat
interface HttpAdapter {
    /**
     * Stable identifier for this adapter.
     *
     * This is the map key that binds application-scoped route metadata to the one selected
     * session-scoped adapter instance. It must be unique within an application graph.
     */
    val id: String

    /**
     * Describe the adapter mount and supported endpoints.
     *
     * Used for fail-fast collision detection, route selection, generic transport exposure,
     * and OpenAPI reconciliation.
     */
    fun describe(): HttpAdapterDescription

    /** Execute an AppScope-preselected route without rescanning unrelated handlers. */
    @JsExportIgnoreCompat
    suspend fun handleResolvedRequest(
        request: GenericHttpRequest,
        route: HttpAdapterRouteMatch,
    ): GenericHttpResponse
}
