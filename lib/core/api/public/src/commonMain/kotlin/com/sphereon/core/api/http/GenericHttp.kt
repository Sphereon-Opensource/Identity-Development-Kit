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

/**
 * Lazy map that only creates the underlying map when first accessed.
 * This avoids allocations for maps that are never used.
 *
 * Performance benefit: For requests where headers or query parameters are never accessed,
 * this completely avoids the map allocation and iteration cost.
 */
class LazyMap<K, V>(
    private val initializer: () -> Map<K, V>,
) : Map<K, V> {
    private val delegate: Map<K, V> by lazy(initializer)

    override val entries: Set<Map.Entry<K, V>> get() = delegate.entries
    override val keys: Set<K> get() = delegate.keys
    override val size: Int get() = delegate.size
    override val values: Collection<V> get() = delegate.values

    override fun containsKey(key: K): Boolean = delegate.containsKey(key)

    override fun containsValue(value: V): Boolean = delegate.containsValue(value)

    override fun get(key: K): V? = delegate.get(key)

    override fun isEmpty(): Boolean = delegate.isEmpty()
}

/**
 * Framework-agnostic HTTP request abstraction.
 *
 * Can be created from:
 * - Spring HttpServletRequest
 * - Ktor ApplicationCall
 * - AWS Lambda APIGatewayProxyRequestEvent
 * - Azure HttpRequestMessage
 * - Google Cloud HttpRequest
 *
 * This abstraction allows routing logic to be defined once in commonMain
 * and reused across all HTTP frameworks and serverless platforms.
 *
 * **Body handling:**
 * - Use [bodyContent] for the typed body model supporting text and binary
 * - Use [body] (String?) for backward compatibility with existing code
 *
 * Performance optimizations:
 * - Lazy body reading (only when accessed)
 * - Lazy map access (headers/query params)
 * - Cached path pattern matching
 */
data class GenericHttpRequest(
    val method: String,
    val path: String,
    val pathParameters: Map<String, String> = emptyMap(),
    val queryParameters: Map<String, String?> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    /**
     * Lazy body supplier - only invoked when body is accessed.
     * This avoids reading the request body for GET/DELETE requests.
     *
     * @see bodyContent for the typed body model
     */
    private val bodySupplier: (() -> String?)? = null,
    /**
     * The typed body content supporting both text and binary payloads.
     *
     * When [bodySupplier] is provided, this defaults to a [GenericHttpBody.LazyText].
     * For binary payloads, construct with an explicit [GenericHttpBody.Bytes] or [GenericHttpBody.LazyBytes].
     */
    val bodyContent: GenericHttpBody = bodySupplier?.let { GenericHttpBody.ofLazyText(it) } ?: GenericHttpBody.Empty,
) {
    /**
     * Request body as String - lazily loaded only when accessed.
     * For GET/DELETE requests where body is never accessed, this saves memory and I/O.
     *
     * This is a convenience accessor for backward compatibility.
     * For typed access (including binary), use [bodyContent].
     */
    val body: String? by lazy {
        bodyContent.asTextOrNull()
    }

    /**
     * Request body as ByteArray - lazily loaded only when accessed.
     * Useful for binary payloads (Protobuf, CBOR, etc.).
     */
    val bodyBytes: ByteArray? by lazy {
        bodyContent.asBytesOrNull()
    }

    /**
     * Content-Type header value, if present.
     */
    val contentType: String?
        get() = headers["Content-Type"] ?: headers["content-type"]

    /**
     * Accept header values, parsed into a list.
     */
    val accept: List<String>
        get() =
            (headers["Accept"] ?: headers["accept"])
                ?.split(",")
                ?.map { it.trim().substringBefore(";") }
                ?: emptyList()

    /**
     * Convenience accessors
     */
    val pathParams: Map<String, String> get() = pathParameters
    val queryParams: Map<String, String?> get() = queryParameters

    /**
     * Check if this request matches a method and path pattern.
     *
     * Pattern syntax:
     * - `/keys` - exact match
     * - `/keys/{id}` - path parameter
     * - `/keys/{id}/subresource` - multiple segments with parameter
     *
     * @param method HTTP method (case-insensitive)
     * @param pattern Path pattern with optional {param} placeholders
     * @return true if request matches the pattern
     */
    fun matches(
        method: String,
        pattern: String,
    ): Boolean =
        this.method.equals(method, ignoreCase = true) &&
            matchesPathPattern(this.path, pattern)

    /**
     * Extract path parameters from the request path based on a pattern.
     * Returns a new request with extracted parameters merged into pathParameters.
     *
     * Example:
     * ```
     * val request = GenericHttpRequest(method = "GET", path = "/keys/key123")
     * val withParams = request.withExtractedParams("/keys/{aliasOrKid}")
     * // withParams.pathParams["aliasOrKid"] == "key123"
     * ```
     */
    fun withExtractedParams(pattern: String): GenericHttpRequest {
        val params = extractPathParams(this.path, pattern)
        return copy(pathParameters = pathParameters + params)
    }

    /**
     * Copy method that preserves the bodySupplier and bodyContent for lazy loading.
     */
    fun copy(
        method: String = this.method,
        path: String = this.path,
        pathParameters: Map<String, String> = this.pathParameters,
        queryParameters: Map<String, String?> = this.queryParameters,
        headers: Map<String, String> = this.headers,
    ): GenericHttpRequest =
        GenericHttpRequest(
            method = method,
            path = path,
            pathParameters = pathParameters,
            queryParameters = queryParameters,
            headers = headers,
            bodySupplier = bodySupplier,
            bodyContent = bodyContent,
        )

    companion object {
        /**
         * Creates a request with text body content.
         */
        fun withTextBody(
            method: String,
            path: String,
            body: String?,
            pathParameters: Map<String, String> = emptyMap(),
            queryParameters: Map<String, String?> = emptyMap(),
            headers: Map<String, String> = emptyMap(),
        ): GenericHttpRequest =
            GenericHttpRequest(
                method = method,
                path = path,
                pathParameters = pathParameters,
                queryParameters = queryParameters,
                headers = headers,
                bodyContent = GenericHttpBody.ofText(body),
            )

        /**
         * Creates a request with binary body content.
         */
        fun withBinaryBody(
            method: String,
            path: String,
            body: ByteArray?,
            pathParameters: Map<String, String> = emptyMap(),
            queryParameters: Map<String, String?> = emptyMap(),
            headers: Map<String, String> = emptyMap(),
        ): GenericHttpRequest =
            GenericHttpRequest(
                method = method,
                path = path,
                pathParameters = pathParameters,
                queryParameters = queryParameters,
                headers = headers,
                bodyContent = GenericHttpBody.ofBytes(body),
            )
    }
}

/**
 * Framework-agnostic HTTP response abstraction.
 *
 * Can be converted to:
 * - Spring ResponseEntity
 * - Ktor ApplicationCall.respond()
 * - AWS Lambda APIGatewayProxyResponseEvent
 * - Azure HttpResponseMessage
 * - Google Cloud HttpResponse
 *
 * **Body handling:**
 * - Use [bodyContent] for the typed body model supporting text and binary
 * - Use [body] (String?) for backward compatibility with existing code
 */
data class GenericHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    /**
     * Response body as String (backward compatible).
     *
     * For typed access including binary, use [bodyContent].
     */
    val body: String? = null,
    /**
     * The typed body content supporting both text and binary payloads.
     *
     * When [body] is provided, this defaults to [GenericHttpBody.Text].
     * For binary responses, construct with explicit [GenericHttpBody.Bytes].
     */
    val bodyContent: GenericHttpBody = body?.let { GenericHttpBody.Text(it) } ?: GenericHttpBody.Empty,
) {
    /**
     * Response body as ByteArray.
     * Useful for binary payloads (Protobuf, CBOR, etc.).
     */
    val bodyBytes: ByteArray?
        get() = bodyContent.asBytesOrNull()

    /**
     * Content-Type header value, if present.
     */
    val contentType: String?
        get() = headers["Content-Type"] ?: headers["content-type"]

    companion object {
        /**
         * Creates a response with text body content.
         */
        fun withTextBody(
            statusCode: Int,
            body: String?,
            headers: Map<String, String> = emptyMap(),
        ): GenericHttpResponse =
            GenericHttpResponse(
                statusCode = statusCode,
                headers = headers,
                body = body,
                bodyContent = GenericHttpBody.ofText(body),
            )

        /**
         * Creates a response with binary body content.
         */
        fun withBinaryBody(
            statusCode: Int,
            body: ByteArray?,
            headers: Map<String, String> = emptyMap(),
        ): GenericHttpResponse =
            GenericHttpResponse(
                statusCode = statusCode,
                headers = headers,
                body = body?.decodeToString(), // Fallback for backward compat
                bodyContent = GenericHttpBody.ofBytes(body),
            )
    }
}

/**
 * Compiled path pattern for efficient matching.
 * Pre-splits the pattern to avoid repeated string operations.
 */
class CompiledPathPattern private constructor(
    val pattern: String,
    private val segments: List<Segment>,
) {
    sealed class Segment {
        data class Literal(
            val value: String,
        ) : Segment()

        data class Parameter(
            val name: String,
        ) : Segment()
    }

    /**
     * Specificity score: number of literal segments.
     * Higher means more specific (e.g., "/default" beats "/{id}").
     * Use this to resolve ambiguity when multiple patterns match the same path.
     */
    val specificity: Int = segments.count { it is Segment.Literal }

    /**
     * Check if a path matches this compiled pattern.
     */
    fun matches(path: String): Boolean {
        val pathSegments = splitPath(path)

        if (pathSegments.size != segments.size) {
            return false
        }

        return pathSegments.zip(segments).all { (pathSeg, patternSeg) ->
            when (patternSeg) {
                is Segment.Parameter -> true
                is Segment.Literal -> pathSeg == patternSeg.value
            }
        }
    }

    /**
     * Extract path parameters from a path.
     */
    fun extractParams(path: String): Map<String, String> {
        val pathSegments = splitPath(path)

        if (pathSegments.size != segments.size) {
            return emptyMap()
        }

        return pathSegments
            .zip(segments)
            .mapNotNull { (pathSeg, patternSeg) ->
                when (patternSeg) {
                    is Segment.Parameter -> patternSeg.name to pathSeg
                    is Segment.Literal -> null
                }
            }.toMap()
    }

    companion object {
        // Cache compiled patterns to avoid repeated parsing
        private val cache = mutableMapOf<String, CompiledPathPattern>()

        /**
         * Compile a path pattern for efficient matching.
         * Results are cached to avoid recompiling the same pattern.
         */
        fun compile(pattern: String): CompiledPathPattern =
            cache.getOrPut(pattern) {
                val segments =
                    splitPath(pattern).map { segment ->
                        if (segment.startsWith("{") && segment.endsWith("}")) {
                            Segment.Parameter(segment.removeSurrounding("{", "}"))
                        } else {
                            Segment.Literal(segment)
                        }
                    }
                CompiledPathPattern(pattern, segments)
            }
    }
}

/**
 * Split path into non-empty segments (cached per path).
 */
private fun splitPath(path: String): List<String> = path.split('/').filter { it.isNotEmpty() }

/**
 * Match a request path against a pattern with placeholders.
 *
 * This function uses cached compiled patterns for efficiency.
 *
 * Examples:
 * - matchesPathPattern("/keys", "/keys") -> true
 * - matchesPathPattern("/keys/abc", "/keys/{id}") -> true
 * - matchesPathPattern("/keys/abc/def", "/keys/{id}") -> false
 * - matchesPathPattern("/keys", "/providers") -> false
 */
internal fun matchesPathPattern(
    path: String,
    pattern: String,
): Boolean = CompiledPathPattern.compile(pattern).matches(path)

/**
 * Extract path parameters from a request path based on a pattern.
 *
 * This function uses cached compiled patterns for efficiency.
 *
 * Examples:
 * - extractPathParams("/keys/abc", "/keys/{id}") -> {"id": "abc"}
 * - extractPathParams("/providers/p1/keys/k1", "/providers/{providerId}/keys/{keyId}")
 *   -> {"providerId": "p1", "keyId": "k1"}
 */
internal fun extractPathParams(
    path: String,
    pattern: String,
): Map<String, String> = CompiledPathPattern.compile(pattern).extractParams(path)
