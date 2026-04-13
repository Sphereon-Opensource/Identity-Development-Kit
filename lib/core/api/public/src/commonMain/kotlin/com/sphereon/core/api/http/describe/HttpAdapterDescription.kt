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

package com.sphereon.core.api.http.describe

/**
 * Minimal, framework-agnostic HTTP method model.
 */
enum class HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    OPTIONS,
    HEAD
}

/**
 * Minimal, KMP-friendly media type model.
 *
 * This intentionally avoids Java types and avoids depending on any specific HTTP framework.
 */
sealed class MediaType(open val value: String) {
    data object ApplicationJson : MediaType("application/json")
    data object ApplicationFormUrlEncoded : MediaType("application/x-www-form-urlencoded")
    data object TextPlain : MediaType("text/plain")
    data object ApplicationOctetStream : MediaType("application/octet-stream")

    data class Custom(override val value: String) : MediaType(value)

    /**
     * Checks if this media type matches another (ignoring parameters like charset).
     *
     * Examples:
     * - ApplicationJson matches Custom("application/json; charset=utf-8")
     * - ApplicationJson matches ApplicationJson
     */
    fun matches(other: MediaType): Boolean {
        val thisBase = this.value.substringBefore(";").trim().lowercase()
        val otherBase = other.value.substringBefore(";").trim().lowercase()
        return thisBase == otherBase
    }

    companion object {
        /**
         * Parse a Content-Type header value into a MediaType.
         *
         * Returns the appropriate built-in MediaType if it matches, or Custom for unrecognized types.
         * Returns null if the input is null or blank.
         */
        fun parse(contentType: String?): MediaType? {
            if (contentType.isNullOrBlank()) return null
            val base = contentType.substringBefore(";").trim().lowercase()
            return when (base) {
                "application/json" -> ApplicationJson
                "application/x-www-form-urlencoded" -> ApplicationFormUrlEncoded
                "text/plain" -> TextPlain
                "application/octet-stream" -> ApplicationOctetStream
                else -> Custom(contentType)
            }
        }
    }
}

/**
 * Defines whether (and where) a tenant segment may appear in the URL path for this adapter.
 *
 * Examples with:
 * - tenantSegmentPattern = /t/{tenantId}
 * - serverPrefix = /api/kms
 * - adapterBasePath = /providers
 *
 * BEFORE_SERVER_PREFIX:
 * - /t/{tenantId}/api/kms/providers/...
 *
 * AFTER_SERVER_PREFIX:
 * - /api/kms/t/{tenantId}/providers/...
 */
enum class TenantPathMode {
    OFF,
    BEFORE_SERVER_PREFIX,
    AFTER_SERVER_PREFIX,
    BOTH
}

/**
 * Defines how to resolve the tenant when both header/JWT and path-based tenant resolution are available.
 */
enum class TenantResolutionPriority {
    HEADER_THEN_PATH,
    PATH_THEN_HEADER
}

/**
 * Describes how an adapter is mounted on a host server.
 *
 * Notes:
 * - [serverPrefix] may be empty to indicate a root mount.
 * - [adapterBasePath] should start with / and is the prefix used by adapter routes (e.g. /keys, /providers).
 */
data class HttpAdapterMount(
    val serverPrefix: String,
    val adapterBasePath: String,
    val tenantPathMode: TenantPathMode = TenantPathMode.OFF,
    val tenantSegmentPattern: String = DEFAULT_TENANT_SEGMENT_PATTERN,
    val tenantResolutionPriority: TenantResolutionPriority = TenantResolutionPriority.HEADER_THEN_PATH
) {
    companion object {
        const val DEFAULT_TENANT_SEGMENT_PATTERN: String = "/t/{tenantId}"
    }
}

/**
 * Describes a single endpoint supported by an HttpAdapter.
 *
 * This is used for:
 * - generic server exposure (Ktor in OSS; Spring in EDK)
 * - collision detection (ambiguous routing)
 * - alignment checks with OpenAPI artifacts (EDK)
 */
data class HttpEndpointDescriptor(
    val method: HttpMethod,
    val pathPattern: String,
    val consumes: Set<MediaType> = emptySet(),
    val produces: Set<MediaType> = emptySet(),
    val operationId: String? = null,
    /** Transport routing command ID. Separate from [operationId] (which is for OpenAPI). */
    val commandId: String? = null,
    val tags: Set<String> = emptySet(),
    val summary: String? = null
)

/**
 * Lightweight hints for OpenAPI tooling. The open-source IDK must not depend on OpenAPI libraries; EDK provides the
 * concrete parsing/indexing and reconciliation.
 */
data class OpenApiHints(
    val tags: Set<String> = emptySet(),
    val operationIdPrefix: String? = null,
    val operationIds: Set<String> = emptySet()
)

/**
 * Full description of a contributed adapter: mount + endpoints.
 */
data class HttpAdapterDescription(
    val id: String,
    val mount: HttpAdapterMount,
    val endpoints: List<HttpEndpointDescriptor>,
    val openApiHints: OpenApiHints? = null
)
