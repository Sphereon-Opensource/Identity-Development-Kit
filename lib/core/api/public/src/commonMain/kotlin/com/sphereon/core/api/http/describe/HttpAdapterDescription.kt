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

package com.sphereon.core.api.http.describe

import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.session.isValidCommandId
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlin.jvm.JvmStatic

/**
 * Minimal, framework-agnostic HTTP method model.
 */
@JsExportCompat
enum class HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    OPTIONS,
    HEAD,
}

/**
 * Declares whether an endpoint requires an accepted bearer token at the Layer-1
 * authentication gate ([TenantResolutionPlugin][com.sphereon.ktor.server.tenant]).
 *
 * This is the authentication posture only. Layer-2 command authorization (role
 * rules keyed by command id) always applies regardless of this value, so [PUBLIC]
 * means "no operator bearer required to reach the handler", never "unauthorized".
 */
@JsExportCompat
enum class EndpointAuthPolicy {
    /** Default. The Layer-1 gate rejects requests without an accepted bearer token. */
    PROTECTED,

    /**
     * Reachable without a bearer token. For protocol and discovery endpoints that
     * carry their own authentication (well-known metadata, OID4VCI / OID4VP flows).
     */
    PUBLIC,
}

/**
 * Minimal, KMP-friendly media type model.
 *
 * This intentionally avoids Java types and avoids depending on any specific HTTP framework.
 */
@JsExportCompat
sealed class MediaType(
    open val value: String,
) {
    data object ApplicationJson : MediaType("application/json")

    data object ApplicationFormUrlEncoded : MediaType("application/x-www-form-urlencoded")

    data object TextPlain : MediaType("text/plain")

    data object ApplicationOctetStream : MediaType("application/octet-stream")

    data class Custom(
        override val value: String,
    ) : MediaType(value)

    /**
     * Checks if this media type matches another (ignoring parameters like charset).
     *
     * Examples:
     * - ApplicationJson matches Custom("application/json; charset=utf-8")
     * - ApplicationJson matches ApplicationJson
     */
    fun matches(other: MediaType): Boolean {
        val thisBase =
            this.value
                .substringBefore(";")
                .trim()
                .lowercase()
        val otherBase =
            other.value
                .substringBefore(";")
                .trim()
                .lowercase()
        return thisBase == otherBase
    }

    companion object {
        /**
         * Parse a Content-Type header value into a MediaType.
         *
         * Returns the appropriate built-in MediaType if it matches, or Custom for unrecognized types.
         * Returns null if the input is null or blank.
         */
        @JvmStatic
        fun parse(contentType: String?): MediaType? {
            if (contentType.isNullOrBlank()) {
                return null
            }
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
@JsExportCompat
enum class TenantPathMode {
    OFF,
    BEFORE_SERVER_PREFIX,
    AFTER_SERVER_PREFIX,
    BOTH,
}

/**
 * Describes how an adapter is mounted on a host server.
 *
 * Notes:
 * - [serverPrefix] may be empty to indicate a root mount.
 * - [adapterBasePath] should start with / and is the prefix used by adapter routes (e.g. /keys, /providers).
 */
@JsExportCompat
data class HttpAdapterMount(
    val serverPrefix: String,
    val adapterBasePath: String,
    val tenantPathMode: TenantPathMode = TenantPathMode.OFF,
    val tenantPathPolicy: TenantPathPolicy = TenantPathPolicy.None,
    val tenantSegmentPattern: String = DEFAULT_TENANT_SEGMENT_PATTERN,
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
@JsExportCompat
data class HttpEndpointDescriptor(
    val method: HttpMethod,
    /**
     * One or more URL patterns this endpoint serves. Multiple entries let a single
     * handler answer at several spec-defined URLs without needing parallel command
     * instances — e.g. a metadata endpoint that exposes both the RFC 8414 §3
     * well-known suffix form and a legacy issuer-path-prefix form.
     *
     * Most descriptors carry a single pattern; for that case use the
     * [pathPattern]-named secondary constructor or the [pathPattern] back-compat
     * accessor. The list is non-empty by construction.
     */
    val pathPatterns: List<String>,
    val consumes: Set<MediaType> = emptySet(),
    val produces: Set<MediaType> = emptySet(),
    val operationId: String? = null,
    /** Transport routing command ID. Separate from [operationId] (which is for OpenAPI). */
    val commandId: String? = null,
    /**
     * Stable identity of the SessionScope [com.sphereon.core.api.http.command.HttpEndpointCommand]
     * that handles this route. This is deliberately separate from [commandId], which identifies
     * the service/transport authorization operation. Route-first dispatch uses this key to resolve
     * exactly one lazy HTTP endpoint command after the request session has been established.
     */
    val handlerCommandId: String? = null,
    val tags: Set<String> = emptySet(),
    val summary: String? = null,
    /**
     * Layer-1 authentication posture for this endpoint. Defaults to
     * [EndpointAuthPolicy.PROTECTED] so a forgotten declaration fails closed
     * (a bearer token is required).
     */
    val authPolicy: EndpointAuthPolicy = EndpointAuthPolicy.PROTECTED,
    /** Optional maximum bytes consumed from the request stream before dispatch. */
    val maxRequestBodyBytes: Int? = null,
) {
    init {
        require(maxRequestBodyBytes == null || maxRequestBodyBytes >= 0)
        require(pathPatterns.isNotEmpty()) {
            "HttpEndpointDescriptor requires at least one path pattern"
        }
        require(commandId == null || isValidCommandId(commandId)) {
            "Invalid command ID format: $commandId. Format: module.service.command"
        }
        require(handlerCommandId == null || isValidCommandId(handlerCommandId)) {
            "Invalid HTTP handler command ID format: $handlerCommandId. Format: module.service.command"
        }
    }

    /**
     * The first (primary) URL pattern. Most descriptors only carry one entry, so
     * this is what consumers reading "the path" want for display, OpenAPI
     * generation, audit metadata, etc. Routing decisions MUST iterate
     * [pathPatterns] instead of using this accessor so multi-pattern descriptors
     * route correctly under all their alias URLs.
     */
    val pathPattern: String get() = pathPatterns.first()

    /**
     * Convenience constructor for the common single-pattern descriptor — keeps
     * existing call sites (`HttpEndpointDescriptor(method, pathPattern = "...")`)
     * source-compatible. Excluded from JS export because Kotlin/JS rejects
     * unnamed secondary constructors on @JsExport classes; JS callers use the
     * primary constructor with `pathPatterns = listOf("/path")`.
     */
    @JsExportIgnoreCompat
    constructor(
        method: HttpMethod,
        pathPattern: String,
        consumes: Set<MediaType> = emptySet(),
        produces: Set<MediaType> = emptySet(),
        operationId: String? = null,
        commandId: String? = null,
        handlerCommandId: String? = null,
        tags: Set<String> = emptySet(),
        summary: String? = null,
        authPolicy: EndpointAuthPolicy = EndpointAuthPolicy.PROTECTED,
        maxRequestBodyBytes: Int? = null,
    ) : this(
        method = method,
        pathPatterns = listOf(pathPattern),
        consumes = consumes,
        produces = produces,
        operationId = operationId,
        commandId = commandId,
        handlerCommandId = handlerCommandId,
        tags = tags,
        summary = summary,
        authPolicy = authPolicy,
        maxRequestBodyBytes = maxRequestBodyBytes,
    )
}

/**
 * Lightweight hints for OpenAPI tooling. The open-source IDK must not depend on OpenAPI libraries; EDK provides the
 * concrete parsing/indexing and reconciliation.
 */
@JsExportCompat
data class OpenApiHints(
    val tags: Set<String> = emptySet(),
    val operationIdPrefix: String? = null,
    val operationIds: Set<String> = emptySet(),
)

/**
 * Full description of a contributed adapter: mount + endpoints.
 */
@JsExportCompat
data class HttpAdapterDescription(
    val id: String,
    val mount: HttpAdapterMount,
    val endpoints: List<HttpEndpointDescriptor>,
    val openApiHints: OpenApiHints? = null,
)
