/*
 * © 2025 Sphereon International B.V.
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

/**
 * Interface for HTTP adapters that handle REST API requests.
 *
 * This interface provides a universal pattern for implementing HTTP APIs:
 * - Framework-agnostic (works with Spring, Ktor, Lambda, Azure, etc.)
 * - Dependency injection via kotlin-inject
 * - Multiple implementations per application (KMS, mDoc, Signature, etc.)
 * - Easy testing and mocking
 *
 * Multiple Implementations:
 * Since this interface is generic, you MUST use Named qualifiers when implementing.
 *
 * Implementation Guidelines:
 * - Be annotated with Inject, SingleIn(SessionScope), and Named(YourAdapter.ID)
 * - Define a companion object with a const val ID
 * - Contain ALL routing logic for your API
 * - Delegate to handlers for business logic
 * - Map exceptions to HTTP status codes
 * - Return GenericHttpResponse with proper status and headers
 *
 * Example:
 * ```
 * @Inject
 * @SingleIn(SessionScope::class)
 * @Named(KmsHttpAdapter.ID)
 * class KmsHttpAdapter : HttpAdapter {
 *     companion object {
 *         const val ID = "kms-api"
 *     }
 *
 *     override val id: String = ID
 *
 *     override fun describe(): HttpAdapterDescription = TODO("Provide mount + endpoints")
 *
 *     override suspend fun handleRequest(request: GenericHttpRequest): GenericHttpResponse {
 *         // implementation
 *     }
 * }
 * ```
 */
interface HttpAdapter {

    /**
     * Stable identifier for this adapter.
     *
     * Notes:
     * - This is used for configuration overrides and for EDK extension replacement.
     * - When multiple implementations exist, continue to use @Named qualifiers in DI, but keep this id stable.
     */
    val id: String

    /**
     * Describe the adapter mount and supported endpoints.
     *
     * The open-source IDK uses this for collision detection and generic Ktor exposure.
     * EDK uses this for OpenAPI reconciliation and Spring exposure.
     */
    fun describe(): HttpAdapterDescription

    /**
     * Handle an HTTP request and return an HTTP response.
     *
     * This method contains ALL routing logic for the API.
     * Platform adapters just convert their native request/response to/from GenericHttp types.
     *
     * @param request Framework-agnostic HTTP request
     * @return Framework-agnostic HTTP response with status code, headers, and body
     */
    suspend fun handleRequest(request: GenericHttpRequest): GenericHttpResponse

}


