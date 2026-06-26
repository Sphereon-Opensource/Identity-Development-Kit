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

package com.sphereon.core.api.http.response

import com.sphereon.core.api.http.GenericHttpBody
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.pagination.Page
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Canonical helper for building [GenericHttpResponse] instances from REST adapter command impls.
 *
 * **Response envelopes:**
 * - **Single resource:** the bare entity, JSON-encoded — see [okWithData], [createdWithData].
 * - **Paginated list:** `{ "data": [...], "pagination": { limit, offset, page, size, total, totalPages, hasMore } }` — see [paginated].
 * - **Non-paginated list (nested resources):** `{ "data": [...] }` — see [list].
 * - **Error:** `{ "error": { "code", "message", "details"? } }` — see [error] and the per-status helpers.
 * - **Binary content:** raw bytes with explicit content type, optional `ETag` / `Cache-Control` — see [bytesResponse].
 *
 * Usage example:
 * ```kotlin
 * return getServiceCommand.execute(args).fold(
 *     success = { Ok(ResponseBuilder.okWithData<MyEntity>(it)) },
 *     failure = { error ->
 *         when (error.code) {
 *             "NOT_FOUND_ERROR" -> Ok(ResponseBuilder.notFound(error.message.defaultMessage))
 *             else -> Ok(ResponseBuilder.internalError("Failed: ${error.message.defaultMessage}"))
 *         }
 *     },
 * )
 * ```
 */
object ResponseBuilder {
    val json: Json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @PublishedApi
    internal const val CONTENT_TYPE_JSON: String = "application/json"

    // ==================== SUCCESS RESPONSES ====================

    /** Build a 200 OK response with a raw JSON body. */
    fun ok(body: String): GenericHttpResponse =
        GenericHttpResponse(
            statusCode = 200,
            headers = mapOf("Content-Type" to CONTENT_TYPE_JSON),
            body = body,
        )

    /** Build a 200 OK response with a JSON-serialised entity. */
    inline fun <reified T> okWithData(data: T): GenericHttpResponse = ok(toJson(data))

    /** Build a 201 Created response with a raw JSON body. */
    fun created(body: String): GenericHttpResponse =
        GenericHttpResponse(
            statusCode = 201,
            headers = mapOf("Content-Type" to CONTENT_TYPE_JSON),
            body = body,
        )

    /** Build a 201 Created response with a JSON-serialised entity. */
    inline fun <reified T> createdWithData(data: T): GenericHttpResponse = created(toJson(data))

    /** Build a 201 Created response with a `Location` header and a JSON-serialised entity. */
    inline fun <reified T> createdAtLocation(
        location: String,
        data: T
    ): GenericHttpResponse =
        GenericHttpResponse(
            statusCode = 201,
            headers =
                mapOf(
                    "Content-Type" to CONTENT_TYPE_JSON,
                    "Location" to location,
                ),
            body = toJson(data),
        )

    /** Build a 204 No Content response. */
    fun noContent(): GenericHttpResponse =
        GenericHttpResponse(
            statusCode = 204,
            headers = emptyMap(),
            body = null,
        )

    /** Build a 304 Not Modified response (used with conditional-GET endpoints). */
    fun notModified(): GenericHttpResponse =
        GenericHttpResponse(
            statusCode = 304,
            headers = emptyMap(),
            body = null,
        )

    /**
     * Build a 200 OK response carrying raw binary content.
     *
     * @param data the binary payload
     * @param contentType the MIME type (e.g. `application/pdf`, `image/png`)
     * @param etag optional entity tag — emitted as `ETag: "<value>"` (quoted per RFC 7232)
     * @param cacheControl optional `Cache-Control` directive (e.g. `max-age=3600`)
     * @param additionalHeaders extra response headers merged after the standard ones; later entries
     *   win on key collision (case-sensitive). Intended for security headers (e.g.
     *   `X-Content-Type-Options`, `Content-Security-Policy`) on endpoints that serve
     *   active content such as SVG.
     */
    fun bytesResponse(
        data: ByteArray,
        contentType: String,
        etag: String? = null,
        cacheControl: String? = null,
        additionalHeaders: Map<String, String> = emptyMap(),
    ): GenericHttpResponse {
        val headers =
            buildMap {
                put("Content-Type", contentType)
                etag?.let { put("ETag", "\"$it\"") }
                cacheControl?.let { put("Cache-Control", it) }
                putAll(additionalHeaders)
            }
        return GenericHttpResponse(
            statusCode = 200,
            headers = headers,
            bodyContent = GenericHttpBody.Bytes(data),
        )
    }

    // ==================== COLLECTION RESPONSES ====================

    /**
     * Build a paginated list response with the canonical envelope:
     * `{ "data": [...], "pagination": { "limit", "offset", "page", "size", "total", "totalPages", "hasMore" } }`.
     *
     * The `pagination` object is the unified superset matching the `PageMeta` schema in the
     * canonical shared `common-components.yml`. The legacy fields (`limit`, `offset`, `total`,
     * `hasMore`) are preserved exactly; `page`, `size`, and `totalPages` are additive and
     * derived from [Page].
     */
    inline fun <reified T> paginated(page: Page<T>): GenericHttpResponse {
        val body =
            buildJsonObject {
                putJsonArray("data") {
                    page.items.forEach { item ->
                        add(json.encodeToJsonElement(kotlinx.serialization.serializer<T>(), item))
                    }
                }
                putJsonObject("pagination") {
                    put("limit", page.limit)
                    put("offset", page.offset)
                    put("page", page.pageNumber)
                    put("size", page.limit)
                    put("total", page.totalCount)
                    put("totalPages", page.totalPages)
                    put("hasMore", page.hasMore)
                }
            }
        return ok(json.encodeToString(body))
    }

    /**
     * Build a non-paginated list response with `{ "data": [...] }`.
     *
     * Use for nested resources (e.g. terms within a vocabulary) where the underlying service
     * command returns `List<T>` rather than `Page<T>`. For top-level pageable collections,
     * prefer [paginated] instead.
     */
    inline fun <reified T> list(items: List<T>): GenericHttpResponse {
        val body =
            buildJsonObject {
                putJsonArray("data") {
                    items.forEach { item ->
                        add(json.encodeToJsonElement(kotlinx.serialization.serializer<T>(), item))
                    }
                }
            }
        return ok(json.encodeToString(body))
    }

    // ==================== ERROR RESPONSES ====================

    /** Build a 400 Bad Request response with optional field-level [details]. */
    fun badRequest(
        message: String,
        details: Map<String, String>? = null
    ): GenericHttpResponse = error(400, "BAD_REQUEST", message, details)

    /** Build a 401 Unauthorized response. */
    fun unauthorized(message: String = "Authentication required"): GenericHttpResponse = error(401, "UNAUTHORIZED", message)

    /** Build a 403 Forbidden response. */
    fun forbidden(message: String = "Insufficient permissions"): GenericHttpResponse = error(403, "FORBIDDEN", message)

    /** Build a 404 Not Found response with a `"<entityType> not found: <id>"` message. */
    fun notFound(
        entityType: String,
        id: String
    ): GenericHttpResponse = error(404, "NOT_FOUND", "$entityType not found: $id")

    /** Build a 404 Not Found response with a custom message. */
    fun notFound(message: String): GenericHttpResponse = error(404, "NOT_FOUND", message)

    /** Build a 409 Conflict response. */
    fun conflict(message: String): GenericHttpResponse = error(409, "CONFLICT", message)

    /** Build a 500 Internal Server Error response. */
    fun internalError(message: String = "An unexpected error occurred"): GenericHttpResponse = error(500, "INTERNAL_ERROR", message)

    /**
     * Build a generic error response with the canonical envelope:
     * `{ "error": { "code", "message", "details"? } }`.
     *
     * @param statusCode the HTTP status code
     * @param code stable, machine-readable error category (e.g. `NOT_FOUND`, `VALIDATION_ERROR`)
     * @param message human-readable error message
     * @param details optional field-level error detail (key → message)
     */
    fun error(
        statusCode: Int,
        code: String,
        message: String,
        details: Map<String, String>? = null,
    ): GenericHttpResponse {
        val body =
            buildJsonObject {
                putJsonObject("error") {
                    put("code", code)
                    put("message", message)
                    if (details != null) {
                        putJsonObject("details") {
                            details.forEach { (key, value) -> put(key, value) }
                        }
                    }
                }
            }
        return GenericHttpResponse(
            statusCode = statusCode,
            headers = mapOf("Content-Type" to CONTENT_TYPE_JSON),
            body = json.encodeToString(body),
        )
    }

    // ==================== JSON HELPERS ====================

    /** Serialise a value to a JSON string using the shared [json] instance. */
    inline fun <reified T> toJson(value: T): String = json.encodeToString(value)

    /** Deserialise a JSON string to a value of [T] using the shared [json] instance. */
    inline fun <reified T> fromJson(jsonString: String): T = json.decodeFromString(jsonString)
}
