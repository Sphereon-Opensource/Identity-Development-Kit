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
 */
package com.sphereon.core.api.http

/**
 * Optional capability for [HttpAdapter]s that want to participate in generic adapter dispatching.
 *
 * Host applications often need to expose multiple adapters (KMS, OAuth2, OID4VP, etc.) on a single HTTP server.
 * To keep host wiring minimal and framework-agnostic, adapters can implement this interface to declare whether
 * they can handle a specific [GenericHttpRequest].
 *
 * Notes:
 * - This is intentionally minimal: it does not impose routing structure on adapters.
 * - The adapter remains responsible for implementing its internal routing in [HttpAdapter.handleRequest].
 */
interface RoutableHttpAdapter : HttpAdapter {
    /**
     * @return true if this adapter is responsible for handling the request (method + path + headers as needed).
     */
    fun canHandle(request: GenericHttpRequest): Boolean
}

@Deprecated(
    message = "Use com.sphereon.core.api.http.dispatch.HttpAdapterDispatcher (catalog-driven, tenant-normalizing).",
    replaceWith = ReplaceWith("com.sphereon.core.api.http.dispatch.HttpAdapterDispatcher")
)
class HttpAdapterDispatcher(
    private val adapters: Set<HttpAdapter>
) {
    suspend fun dispatch(request: GenericHttpRequest): GenericHttpResponse {
        val routableAdapters = adapters.filterIsInstance<RoutableHttpAdapter>()
        val matches = routableAdapters.filter { it.canHandle(request) }

        return when (matches.size) {
            0 -> errorResponse(404, "Not found: ${request.method} ${request.path}")
            1 -> matches.single().handleRequest(request)
            else -> errorResponse(500, "Multiple HTTP adapters match request: ${request.method} ${request.path}")
        }
    }
}

