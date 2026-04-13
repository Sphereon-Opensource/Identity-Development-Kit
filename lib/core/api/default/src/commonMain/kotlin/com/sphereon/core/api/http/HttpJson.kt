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

import kotlinx.serialization.json.Json

/**
 * Standard JSON configurations for HTTP request/response processing.
 *
 * This object provides pre-configured [Json] instances optimized for different
 * use cases in REST API implementations.
 *
 * **Usage in HTTP endpoint commands:**
 * ```kotlin
 * class MyEndpointCommand(...) : HttpEndpointCommandAdapter(...) {
 *     override suspend fun doExecute(...): IdkResult<GenericHttpResponse, IdkError> {
 *         val request = HttpJson.restApi.decodeFromString<MyRequest>(args.body!!)
 *         val responseJson = HttpJson.restApi.encodeToString(MyResponse.serializer(), response)
 *         return Ok(jsonResponse(200, responseJson))
 *     }
 * }
 * ```
 */
object HttpJson {

    /**
     * JSON configuration for REST API request/response serialization.
     *
     * **Configuration:**
     * - `ignoreUnknownKeys = true` - Tolerant of extra fields in requests for forward compatibility
     * - `encodeDefaults = true` - Always includes all fields in responses for predictable API contracts
     * - `explicitNulls = false` - Omits null-valued properties from serialized output
     * - `prettyPrint = false` - Compact output for network efficiency
     *
     * **Why `encodeDefaults = true`:**
     * REST API responses should include all fields to provide predictable schemas.
     * This prevents ambiguity between "missing" and "default" values,
     * making the API easier to consume for clients.
     *
     * **Why `explicitNulls = false`:**
     * Null-valued properties are omitted rather than serialized as `"field": null`.
     * This produces cleaner output and avoids issues with external APIs that
     * reject explicit null values for optional fields.
     */
    val restApi: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        explicitNulls = false
    }
}
