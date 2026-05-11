/*
 * Â© 2026 Sphereon International B.V.
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

package com.sphereon.core.api.http.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.CompiledPathPattern
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.RoutableHttpAdapter
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.OpenApiHints
import com.sphereon.core.api.http.error.DefaultRestErrorRenderer
import com.sphereon.core.api.http.error.HttpErrorRenderer
import com.sphereon.core.api.http.response.errorResponse
import com.sphereon.core.api.session.Command
import com.sphereon.core.api.session.ExecutionScopedCommandAdapter
import com.sphereon.core.api.session.ICommandExecutionExtension
import com.sphereon.core.api.session.ICommandInitExtension
import com.sphereon.core.api.session.IEnhancedCommandExecutionExtension
import com.sphereon.core.api.session.MultiService
import com.sphereon.di.context.MutableResolvedTenantIdProvider

/**
 * Base class for HTTP adapters backed by the IDK command infrastructure.
 *
 * This adapter integrates [HttpAdapter] with [ExecutionScopedCommandAdapter], enabling:
 * - **Lifecycle integration**: Automatic session registration via `onEnterScope`
 * - **Enablement/feature gating**: Commands and endpoints can be toggled via `isEnabled`
 * - **Extension hooks**: Before/during/after execution callbacks
 * - **Introspectable routing**: Endpoint commands expose their descriptors for catalog/dispatcher use
 *
 * ## Architecture
 *
 * A `CommandBackedHttpAdapter` is itself a command that:
 * - Takes a `GenericHttpRequest` as input
 * - Returns a `GenericHttpResponse` as output
 * - Delegates to child [HttpEndpointCommand]s based on request matching
 *
 * This follows the `MultiService` pattern from the command infrastructure, where the adapter
 * acts as an aggregator that selects and executes the appropriate endpoint command.
 *
 * ## Usage
 *
 * ```kotlin
 * class MyHttpAdapter(
 *     execution: SessionExecution,
 *     private val myService: MyService
 * ) : CommandBackedHttpAdapter(
 *     id = "my-adapter",
 *     execution = execution,
 *     mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/my")
 * ) {
 *     override val endpointCommands: List<HttpEndpointCommand> by lazy {
 *         listOf(
 *             GetItemEndpointCommand(execution, myService),
 *             CreateItemEndpointCommand(execution, myService)
 *         )
 *     }
 * }
 * ```
 *
 * ## Comparison with RoutedHttpAdapter
 *
 * - **RoutedHttpAdapter**: Lighter weight, routes are inline lambdas, no command lifecycle
 * - **CommandBackedHttpAdapter**: Full command integration, endpoints are standalone commands,
 *   supports enablement, extensions, and session lifecycle hooks
 *
 * Choose `RoutedHttpAdapter` for simple cases; use `CommandBackedHttpAdapter` when you need
 * command infrastructure features or want to expose individual endpoints as injectable commands.
 *
 * @param id Unique identifier for this adapter
 * @param execution The session execution context
 * @param mount The mount configuration for this adapter
 * @param isEnabled Whether this adapter is enabled (default: true)
 * @param initExtensions Initialization lifecycle hooks
 * @param executionExtensions Execution lifecycle hooks (before/during/after)
 * @param enhancedExecutionExtensions Enhanced execution extensions with suspend support and short-circuit capability
 */
abstract class CommandBackedHttpAdapter(
    override val id: String,
    execution: SessionExecution,
    protected val mount: HttpAdapterMount,
    isEnabled: Boolean = true,
    initExtensions: Array<ICommandInitExtension<GenericHttpRequest, GenericHttpResponse, IdkError>> = emptyArray(),
    executionExtensions: Array<ICommandExecutionExtension<GenericHttpRequest, GenericHttpResponse, IdkError>> = emptyArray(),
    enhancedExecutionExtensions: Array<IEnhancedCommandExecutionExtension<GenericHttpRequest, GenericHttpResponse, IdkError>> = emptyArray(),
    protected open val errorRenderer: HttpErrorRenderer = DefaultRestErrorRenderer(),
    /**
     * Per-adapter routable-slug peel policy. Defaults to [TenantPathPolicy.None] —
     * the path is matched as-is and no peel is attempted. Adapters that want
     * spec-correct OAuth2/OIDC URL routing override this:
     * - Discovery adapters: [TenantPathPolicy.WellKnownSuffix]
     * - Authorization / token / par / callback adapters: [TenantPathPolicy.LeadingSlug]
     */
    open val tenantPathPolicy: TenantPathPolicy = TenantPathPolicy.None,
) : ExecutionScopedCommandAdapter<GenericHttpRequest, GenericHttpResponse, IdkError>(
        id = id,
        isEnabled = isEnabled,
        initExtensions = initExtensions,
        executionExtensions = executionExtensions,
        execution = execution,
        enhancedExecutionExtensions = enhancedExecutionExtensions,
    ),
    HttpAdapter,
    RoutableHttpAdapter,
    MultiService<GenericHttpRequest, GenericHttpResponse, IdkError> {
    /**
     * The endpoint commands that this adapter delegates to.
     *
     * Override this to provide the list of endpoint commands. Use `lazy` initialization
     * to ensure commands are constructed after the adapter is fully initialized.
     */
    protected abstract val endpointCommands: List<HttpEndpointCommand>

    /**
     * Slug lookup used by [TenantPathPolicy.LeadingSlug] / [TenantPathPolicy.WellKnownSuffix]
     * peeling. Defaults to a no-op implementation that returns null for every
     * lookup so legacy adapters compile and run unchanged. Adapters with a
     * non-`None` [tenantPathPolicy] MUST override this with the AppScope-injected
     * [RoutableSlugLookup] binding (the EDK `lib-tenant-resolution-impl` module
     * contributes a postgres-backed implementation; without it, peels never
     * succeed and as-is matching applies).
     */
    protected open val routableSlugLookup: RoutableSlugLookup = NoOpRoutableSlugLookupSingleton

    /**
     * Mutable session-scoped tenant override. The dispatcher writes the descended
     * tenant id here on a successful peel and clears it after the matched
     * endpoint returns. Defaults to a no-op so legacy adapters compile; adapters
     * with a non-`None` [tenantPathPolicy] should override this with the
     * SessionScope-injected [MutableResolvedTenantIdProvider] binding so
     * `SessionExecution.tenantId` reflects the descended value during dispatch.
     */
    protected open val resolvedTenantIdProvider: MutableResolvedTenantIdProvider = NoopMutableResolvedTenantIdProvider

    /**
     * Optional OpenAPI hints for this adapter.
     */
    protected open val openApiHints: OpenApiHints? = null

    // ========== MultiService implementation ==========

    @Suppress("UNCHECKED_CAST")
    override val commands: MutableList<Command<GenericHttpRequest, GenericHttpResponse, IdkError>>
        get() = enabledEndpoints.toMutableList() as MutableList<Command<GenericHttpRequest, GenericHttpResponse, IdkError>>

    /**
     * Enabled endpoint commands (filtered by isEnabled).
     */
    private val enabledEndpoints: List<HttpEndpointCommand>
        get() = endpointCommands.filter { it.isEnabled }

    /**
     * Simplified constructor for common use cases.
     */
    constructor(
        id: String,
        execution: SessionExecution,
        mount: HttpAdapterMount,
        errorRenderer: HttpErrorRenderer = DefaultRestErrorRenderer(),
    ) : this(
        id = id,
        execution = execution,
        mount = mount,
        isEnabled = true,
        initExtensions = emptyArray(),
        executionExtensions = emptyArray(),
        enhancedExecutionExtensions = emptyArray(),
        errorRenderer = errorRenderer,
    )

    // ========== HttpAdapter implementation ==========

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = mount,
            endpoints =
                enabledEndpoints.map { endpoint ->
                    // Prepend adapter base path for catalog/dispatcher matching.
                    // Endpoint commands define patterns relative to the adapter's base path,
                    // but the dispatcher expects full paths for candidate selection.
                    val fullPathPattern =
                        if (mount.adapterBasePath.isEmpty() || mount.adapterBasePath == "/") {
                            endpoint.endpoint.pathPattern
                        } else {
                            mount.adapterBasePath + endpoint.endpoint.pathPattern
                        }
                    endpoint.endpoint.copy(pathPattern = fullPathPattern)
                },
            openApiHints = openApiHints,
        )

    override suspend fun handleRequest(request: GenericHttpRequest): GenericHttpResponse {
        val result = execute(request)
        return result.fold(
            success = { response -> response },
            failure = { error -> errorRenderer.render(error, request) },
        )
    }

    // ========== RoutableHttpAdapter implementation ==========

    override fun canHandle(request: GenericHttpRequest): Boolean {
        val basePath = mount.adapterBasePath
        // If a base path is configured, check if the request path starts with it
        if (basePath.isNotEmpty() && basePath != "/") {
            return request.path.startsWith(basePath)
        }
        // No base path - check if any endpoint pattern's first segment matches
        // This is a heuristic for routing; the actual matching happens in supports()
        val requestFirstSegment =
            request.path
                .trimStart('/')
                .split('/')
                .firstOrNull() ?: ""
        return enabledEndpoints.any { endpoint ->
            val patternFirstSegment =
                endpoint.endpoint.pathPattern
                    .trimStart('/')
                    .split('/')
                    .firstOrNull() ?: ""
            endpoint.endpoint.method.name
                .equals(request.method, ignoreCase = true) &&
                (patternFirstSegment.startsWith("{") || patternFirstSegment == requestFirstSegment)
        }
    }

    // ========== Command implementation ==========

    override suspend fun supports(args: Any): Boolean {
        if (!isEnabled) {
            return false
        }
        if (args !is GenericHttpRequest) {
            return false
        }
        val relativeRequest = stripAdapterBasePath(args)
        if (enabledEndpoints.any { endpoint -> endpoint.supports(relativeRequest) }) {
            return true
        }
        // No as-is match. If the policy permits peeling, see whether ANY peel
        // could plausibly match an endpoint pattern. We only check the raw
        // pattern shape here (length / segment count) to avoid hitting
        // [routableSlugLookup] from supports — that's an I/O call we save for
        // doExecute. The conservative check is: under LeadingSlug(maxDepth=N),
        // pattern can match if path has at most N more leading segments than
        // the longest endpoint pattern. Symmetrically for WellKnownSuffix.
        return when (val policy = tenantPathPolicy) {
            TenantPathPolicy.None -> false
            is TenantPathPolicy.LeadingSlug -> couldPeelLeading(relativeRequest, policy.maxDepth)
            is TenantPathPolicy.WellKnownSuffix -> couldPeelTrailing(relativeRequest, policy.maxDepth)
        }
    }

    private fun couldPeelLeading(
        request: GenericHttpRequest,
        maxDepth: Int
    ): Boolean {
        val reqSegments = request.path.split('/').filter { it.isNotEmpty() }
        return enabledEndpoints.any { endpoint ->
            if (!endpoint.endpoint.method.name
                    .equals(request.method, ignoreCase = true)
            ) {
                return@any false
            }
            val patternSegments =
                endpoint.endpoint.pathPattern
                    .split('/')
                    .filter { it.isNotEmpty() }
            // Peel up to `maxDepth` from the front: synthesize candidate paths and
            // try to match.
            (1..minOf(maxDepth, reqSegments.size)).any { peel ->
                val remaining = "/" + reqSegments.drop(peel).joinToString("/")
                request.copy(path = remaining).let { peeled ->
                    peeled.matches(endpoint.endpoint.method.name, endpoint.endpoint.pathPattern)
                }
            }
        }
    }

    private fun couldPeelTrailing(
        request: GenericHttpRequest,
        maxDepth: Int
    ): Boolean {
        val reqSegments = request.path.split('/').filter { it.isNotEmpty() }
        return enabledEndpoints.any { endpoint ->
            if (!endpoint.endpoint.method.name
                    .equals(request.method, ignoreCase = true)
            ) {
                return@any false
            }
            (1..minOf(maxDepth, reqSegments.size)).any { peel ->
                val remaining =
                    if (reqSegments.size - peel <= 0) "/" else "/" + reqSegments.dropLast(peel).joinToString("/")
                request.copy(path = remaining).let { peeled ->
                    peeled.matches(endpoint.endpoint.method.name, endpoint.endpoint.pathPattern)
                }
            }
        }
    }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        // Defense-in-depth: refuse paths that contain traversal or encoded-slash
        // tokens BEFORE peel evaluation. Endpoint pattern matchers below also
        // reject these (no real route uses `..` or `%2F`), but failing early keeps
        // the routableSlugLookup from being asked to resolve attack-shaped tokens.
        if (pathHasUnsafeTokens(request.path)) {
            log.debug("[$id] Refusing unsafe path: ${request.method} ${request.path}")
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "request path contains forbidden tokens (path traversal or encoded slash)",
                ),
            )
        }
        log.debug("[$id] Processing request: ${request.method} ${request.path}")

        // Strip adapter base path before routing to endpoint commands
        val relativeRequest = stripAdapterBasePath(request)

        // Build the candidate set: 0-peel match + any successful peels under the
        // declared tenantPathPolicy. The longest peel that produces a matching
        // endpoint wins (ties broken by endpoint pattern specificity).
        val candidates = collectPeelCandidates(relativeRequest)

        if (candidates.isEmpty()) {
            // RequiredSlug failed-to-peel and as-is also misses → distinct error
            // so the resolver layer above can tell "no tenant" from "no route".
            return when (val policy = tenantPathPolicy) {
                is TenantPathPolicy.LeadingSlug, is TenantPathPolicy.WellKnownSuffix -> {
                    if (policy.required) {
                        log.debug("[$id] tenant_unresolved: required peel failed for ${request.method} ${request.path}")
                        Err(
                            IdkError.NOT_FOUND_ERROR(
                                message = "tenant_unresolved: ${request.method} ${request.path}",
                            ),
                        )
                    } else {
                        log.debug("[$id] No matching endpoint for: ${request.method} ${request.path}")
                        Err(IdkError.NOT_FOUND_ERROR(message = "Not found: ${request.method} ${request.path}"))
                    }
                }

                TenantPathPolicy.None -> {
                    log.debug("[$id] No matching endpoint for: ${request.method} ${request.path}")
                    Err(IdkError.NOT_FOUND_ERROR(message = "Not found: ${request.method} ${request.path}"))
                }
            }
        }

        // Pick longest peel; tie-break by endpoint pattern specificity (most literal segments wins).
        val winner =
            candidates.maxWithOrNull(
                compareBy<PeelCandidate> { it.peelDepth }
                    .thenBy { CompiledPathPattern.compile(it.endpoint.endpoint.pathPattern).specificity },
            )!!

        // Advance the session-scope tenant for the duration of this dispatch when a
        // peel changed it. Cleared in finally so the override doesn't leak.
        val previousOverride = resolvedTenantIdProvider.currentTenantId()
        val needsOverride = winner.descendedTenantId != null
        if (needsOverride) {
            resolvedTenantIdProvider.setCurrentTenantId(winner.descendedTenantId!!)
        }

        return try {
            winner.endpoint.execute(winner.strippedRequest)
        } catch (expected: Exception) {
            log.error("[$id] Error executing endpoint: ${expected.message}", expected)
            Err(IdkError.UNKNOWN_ERROR(message = expected.message ?: "Internal error", exception = expected))
        } finally {
            if (needsOverride) {
                if (previousOverride != null) {
                    resolvedTenantIdProvider.setCurrentTenantId(previousOverride)
                } else {
                    resolvedTenantIdProvider.clearCurrentTenantId()
                }
            }
        }
    }

    /**
     * Internal record describing one peel candidate considered by the dispatcher.
     */
    private data class PeelCandidate(
        val peelDepth: Int,
        val endpoint: HttpEndpointCommand,
        val strippedRequest: GenericHttpRequest,
        /** Null when this candidate didn't change the session tenant. */
        val descendedTenantId: String?,
    )

    /**
     * Collect every (peel, endpoint) pair that produces a successful match. Always
     * considers the 0-peel as-is match first; then, if the policy allows, walks
     * up to `maxDepth` segments off the front (LeadingSlug) or off the tail
     * (WellKnownSuffix), validating each via [routableSlugLookup].
     *
     * Path peeling is short-circuit: as soon as a slug fails to resolve, we stop
     * descending in that direction (a slug is a prefix of further descent — if
     * the closer one isn't valid, the further one can't be either).
     */
    private suspend fun collectPeelCandidates(relativeRequest: GenericHttpRequest): List<PeelCandidate> {
        val candidates = mutableListOf<PeelCandidate>()

        // 0-peel: try as-is.
        enabledEndpoints
            .filter { endpoint -> endpoint.supports(relativeRequest) }
            .forEach { endpoint ->
                candidates +=
                    PeelCandidate(
                        peelDepth = 0,
                        endpoint = endpoint,
                        strippedRequest = relativeRequest,
                        descendedTenantId = null,
                    )
            }

        when (val policy = tenantPathPolicy) {
            TenantPathPolicy.None -> Unit
            is TenantPathPolicy.LeadingSlug -> peelLeading(relativeRequest, policy.maxDepth, candidates)
            is TenantPathPolicy.WellKnownSuffix -> peelTrailing(relativeRequest, policy.maxDepth, candidates)
        }

        return candidates
    }

    private suspend fun peelLeading(
        relativeRequest: GenericHttpRequest,
        maxDepth: Int,
        out: MutableList<PeelCandidate>,
    ) {
        val segments = relativeRequest.path.split('/').filter { it.isNotEmpty() }
        var currentTenantId: String? = null
        val baseTenantId = relativeRequest.headers[INTERNAL_BASE_TENANT_HEADER]
        var parent: String? = baseTenantId
        for (i in 1..minOf(maxDepth, segments.size)) {
            val seg = segments[i - 1]
            // Slug-shape gate before the DB hit. Stops peel at the first
            // non-slug segment — the remaining segments stay as-is for the
            // endpoint matcher.
            if (!isSafeSlugSegment(seg)) break
            val resolved =
                if (parent == null) {
                    routableSlugLookup.findRootBySlug(seg)
                } else {
                    routableSlugLookup.findChildBySlug(parent, seg)
                } ?: break
            currentTenantId = resolved.tenantId
            parent = resolved.tenantId

            val remainingPath = "/" + segments.drop(i).joinToString("/")
            val stripped = relativeRequest.copy(path = if (remainingPath == "/") "/" else remainingPath)
            enabledEndpoints
                .filter { endpoint -> endpoint.supports(stripped) }
                .forEach { endpoint ->
                    out +=
                        PeelCandidate(
                            peelDepth = i,
                            endpoint = endpoint,
                            strippedRequest = stripped,
                            descendedTenantId = currentTenantId,
                        )
                }
        }
    }

    private suspend fun peelTrailing(
        relativeRequest: GenericHttpRequest,
        maxDepth: Int,
        out: MutableList<PeelCandidate>,
    ) {
        val segments = relativeRequest.path.split('/').filter { it.isNotEmpty() }
        // For trailing peel, the URL semantic order is parent-first, child-last:
        //   /.well-known/X/<parent>/<child>
        // The peel walks the tail right-to-left (child first), but the validation
        // chain is parent → child. We collect peeled candidates as we walk, then
        // descend the chain in URL order to validate.
        var currentTenantId: String? = null
        val baseTenantId = relativeRequest.headers[INTERNAL_BASE_TENANT_HEADER]
        for (peelCount in 1..minOf(maxDepth, segments.size)) {
            val peeled = segments.takeLast(peelCount)
            val remaining = segments.dropLast(peelCount)

            // Validate the peeled chain in URL order: outermost (left) first.
            // Each segment must be slug-shaped before we hit the DB; if any peeled
            // segment isn't a valid slug, this peelCount cannot resolve.
            if (peeled.any { !isSafeSlugSegment(it) }) continue
            var parent: String? = baseTenantId
            var lastResolved: String? = null
            var allResolved = true
            for (seg in peeled) {
                val resolved =
                    if (parent == null) {
                        routableSlugLookup.findRootBySlug(seg)
                    } else {
                        routableSlugLookup.findChildBySlug(parent, seg)
                    }
                if (resolved == null) {
                    allResolved = false
                    break
                }
                parent = resolved.tenantId
                lastResolved = resolved.tenantId
            }
            if (!allResolved) {
                // This peel depth doesn't produce a valid chain — but a deeper
                // peel might (when the outer parent appears at peelCount+1). We
                // do NOT short-circuit here; peelTrailing keeps walking up to
                // maxDepth.
                continue
            }
            currentTenantId = lastResolved

            val remainingPath = if (remaining.isEmpty()) "/" else "/" + remaining.joinToString("/")
            val stripped = relativeRequest.copy(path = remainingPath)
            enabledEndpoints
                .filter { endpoint -> endpoint.supports(stripped) }
                .forEach { endpoint ->
                    out +=
                        PeelCandidate(
                            peelDepth = peelCount,
                            endpoint = endpoint,
                            strippedRequest = stripped,
                            descendedTenantId = currentTenantId,
                        )
                }
        }
    }

    /**
     * DNS-label-shaped (RFC 1123) plus the same `--`/trailing-`-`/`xn--` checks
     * applied at registration. Inlined here because the IDK dispatcher cannot
     * depend on the EDK `TenantSlug` validator; the rule must stay in lockstep
     * with the registration-side regex.
     */
    private fun isSafeSlugSegment(seg: String): Boolean =
        SAFE_SLUG_RE.matches(seg) &&
            !seg.contains("--") &&
            !seg.endsWith("-") &&
            !seg.startsWith("xn--")

    /**
     * Cheap pre-filter for path traversal / encoded-slash tokens. Returns true
     * for any path that would be unsafe to feed into the peel/route layer.
     */
    private fun pathHasUnsafeTokens(path: String): Boolean = FORBIDDEN_PATH_TOKENS.any { token -> path.contains(token, ignoreCase = true) }

    companion object {
        /**
         * Internal request header set by the Ktor tenant-resolution plugin to pass
         * the Layer 1 (host/JWT) base tenant id into the dispatcher. The header
         * name is intentionally a name no client could send through a normal HTTP
         * request — it lives only in the in-process [GenericHttpRequest] copy and
         * is stripped by the plugin if it appears on inbound traffic. Used so the
         * dispatcher can validate path peels relative to the host-resolved parent
         * tenant without needing to read SessionExecution mid-flight.
         */
        const val INTERNAL_BASE_TENANT_HEADER: String = "__sphereon_internal_base_tenant__"

        private val SAFE_SLUG_RE = Regex("^[a-z][a-z0-9-]{0,62}$")

        private val FORBIDDEN_PATH_TOKENS = listOf("..", "//", "%2f", "%5c", "\\")
    }

    /**
     * Strip the adapter's base path from the request path.
     *
     * Endpoint commands define paths relative to the adapter's base path.
     * For example, if the adapter has base path "/oid4vp" and receives a request
     * for "/oid4vp/request-uri/123", the endpoint command pattern is "/request-uri/{id}".
     */
    private fun stripAdapterBasePath(request: GenericHttpRequest): GenericHttpRequest {
        val basePath = mount.adapterBasePath
        if (basePath.isEmpty() || basePath == "/") {
            return request
        }
        val path = request.path
        return if (path.startsWith(basePath)) {
            val relativePath =
                path.removePrefix(basePath).let {
                    if (it.isEmpty()) {
                        "/"
                    } else {
                        it
                    }
                }
            request.copy(path = relativePath)
        } else {
            request
        }
    }

    override fun getById(commandId: String): Command<GenericHttpRequest, GenericHttpResponse, IdkError>? {
        @Suppress("UNCHECKED_CAST")
        return endpointCommands.firstOrNull { it.id == commandId } as? Command<GenericHttpRequest, GenericHttpResponse, IdkError>
    }

    @Suppress("UNCHECKED_CAST")
    override fun addCommand(filter: com.sphereon.core.api.session.BaseCommand<*, *, *>): com.sphereon.core.api.session.BasePipelineCommand<GenericHttpRequest, GenericHttpResponse, IdkError> =
        throw UnsupportedOperationException("CommandBackedHttpAdapter uses declarative endpoint commands; use endpointCommands property")

    @Suppress("UNCHECKED_CAST")
    override fun removeCommand(filter: com.sphereon.core.api.session.BaseCommand<*, *, *>): com.sphereon.core.api.session.BasePipelineCommand<GenericHttpRequest, GenericHttpResponse, IdkError> =
        throw UnsupportedOperationException("CommandBackedHttpAdapter uses declarative endpoint commands; use endpointCommands property")
}

/**
 * No-op [RoutableSlugLookup] used as the dispatcher's default when a subclass
 * with `tenantPathPolicy = None` declines to inject a real implementation.
 * Always returns null so adapters compile and dispatch without slug routing —
 * the safe default for legacy adapters that never peel.
 */
private object NoOpRoutableSlugLookupSingleton : RoutableSlugLookup {
    override suspend fun findRootBySlug(slug: String): RoutableSlugLookup.Resolved? = null

    override suspend fun findChildBySlug(
        parentTenantId: String,
        slug: String
    ): RoutableSlugLookup.Resolved? = null
}

/**
 * No-op [MutableResolvedTenantIdProvider] used by the dispatcher when a subclass
 * with `tenantPathPolicy = None` declines to inject a real provider. Set/clear
 * are silently dropped; a peel that would otherwise advance the session tenant
 * never fires for None-policy adapters anyway.
 */
private object NoopMutableResolvedTenantIdProvider : MutableResolvedTenantIdProvider {
    override fun currentTenantId(): String? = null

    override fun setCurrentTenantId(tenantId: String) { /* no-op */ }

    override fun clearCurrentTenantId() { /* no-op */ }
}
