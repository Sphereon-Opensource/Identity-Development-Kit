/*
 * Â© 2025 Sphereon International B.V.
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
import com.sphereon.core.api.http.errorResponse
import com.sphereon.core.api.session.Command
import com.sphereon.core.api.session.ExecutionScopedCommandAdapter
import com.sphereon.core.api.session.ICommandExecutionExtension
import com.sphereon.core.api.session.ICommandInitExtension
import com.sphereon.core.api.session.IEnhancedCommandExecutionExtension

import com.sphereon.core.api.session.MultiService

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
) : ExecutionScopedCommandAdapter<GenericHttpRequest, GenericHttpResponse, IdkError>(
    id = id,
    isEnabled = isEnabled,
    initExtensions = initExtensions,
    executionExtensions = executionExtensions,
    execution = execution,
    enhancedExecutionExtensions = enhancedExecutionExtensions
), HttpAdapter, RoutableHttpAdapter, MultiService<GenericHttpRequest, GenericHttpResponse, IdkError> {

    /**
     * The endpoint commands that this adapter delegates to.
     *
     * Override this to provide the list of endpoint commands. Use `lazy` initialization
     * to ensure commands are constructed after the adapter is fully initialized.
     */
    protected abstract val endpointCommands: List<HttpEndpointCommand>

    /**
     * Optional OpenAPI hints for this adapter.
     */
    protected open val openApiHints: OpenApiHints? = null

    // ========== HttpAdapter implementation ==========

    override fun describe(): HttpAdapterDescription = HttpAdapterDescription(
        id = id,
        mount = mount,
        endpoints = enabledEndpoints.map { endpoint ->
            // Prepend adapter base path for catalog/dispatcher matching.
            // Endpoint commands define patterns relative to the adapter's base path,
            // but the dispatcher expects full paths for candidate selection.
            val fullPathPattern = if (mount.adapterBasePath.isEmpty() || mount.adapterBasePath == "/") {
                endpoint.endpoint.pathPattern
            } else {
                mount.adapterBasePath + endpoint.endpoint.pathPattern
            }
            endpoint.endpoint.copy(pathPattern = fullPathPattern)
        },
        openApiHints = openApiHints
    )

    override suspend fun handleRequest(request: GenericHttpRequest): GenericHttpResponse {
        // Delegate to command execution, unwrapping the IdkResult
        val result = execute(request)
        return result.fold(
            success = { response -> response },
            failure = { error ->
                // Convert IdkError to HTTP error response
                errorResponse(
                    statusCode = mapErrorToStatusCode(error),
                    message = error.message.defaultMessage
                )
            }
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
        val requestFirstSegment = request.path.trimStart('/').split('/').firstOrNull() ?: ""
        return enabledEndpoints.any { endpoint ->
            val patternFirstSegment = endpoint.endpoint.pathPattern.trimStart('/').split('/').firstOrNull() ?: ""
            endpoint.endpoint.method.name.equals(request.method, ignoreCase = true) &&
                (patternFirstSegment.startsWith("{") || patternFirstSegment == requestFirstSegment)
        }
    }

    // ========== Command implementation ==========

    override suspend fun supports(args: Any): Boolean {
        if (!isEnabled) return false
        if (args !is GenericHttpRequest) return false
        val relativeRequest = stripAdapterBasePath(args)
        return enabledEndpoints.any { endpoint -> endpoint.supports(relativeRequest) }
    }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        // Strip adapter base path before routing to endpoint commands
        val relativeRequest = stripAdapterBasePath(request)

        // Find matching enabled endpoint commands
        val matchingEndpoints = enabledEndpoints.filter { endpoint ->
            endpoint.supports(relativeRequest)
        }

        return when (matchingEndpoints.size) {
            0 -> Ok(errorResponse(404, "Not found: ${request.method} ${request.path}"))
            1 -> {
                try {
                    matchingEndpoints.single().execute(relativeRequest)
                } catch (e: Exception) {
                    Ok(errorResponse(e))
                }
            }
            else -> {
                // Multiple matches: pick the most specific (most literal path segments)
                val best = matchingEndpoints.maxByOrNull { CompiledPathPattern.compile(it.endpoint.pathPattern).specificity }!!
                try {
                    best.execute(relativeRequest)
                } catch (e: Exception) {
                    Ok(errorResponse(e))
                }
            }
        }
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
        if (basePath.isEmpty() || basePath == "/") return request
        val path = request.path
        return if (path.startsWith(basePath)) {
            val relativePath = path.removePrefix(basePath).let { if (it.isEmpty()) "/" else it }
            request.copy(path = relativePath)
        } else {
            request
        }
    }

    // ========== MultiService implementation ==========

    @Suppress("UNCHECKED_CAST")
    override val commands: MutableList<Command<GenericHttpRequest, GenericHttpResponse, IdkError>>
        get() = enabledEndpoints.toMutableList() as MutableList<Command<GenericHttpRequest, GenericHttpResponse, IdkError>>

    override fun getById(commandId: String): Command<GenericHttpRequest, GenericHttpResponse, IdkError>? {
        @Suppress("UNCHECKED_CAST")
        return endpointCommands.firstOrNull { it.id == commandId } as? Command<GenericHttpRequest, GenericHttpResponse, IdkError>
    }

    @Suppress("UNCHECKED_CAST")
    override fun addCommand(filter: com.sphereon.core.api.session.BaseCommand<*, *, *>): com.sphereon.core.api.session.BasePipelineCommand<GenericHttpRequest, GenericHttpResponse, IdkError> {
        throw UnsupportedOperationException("CommandBackedHttpAdapter uses declarative endpoint commands; use endpointCommands property")
    }

    @Suppress("UNCHECKED_CAST")
    override fun removeCommand(filter: com.sphereon.core.api.session.BaseCommand<*, *, *>): com.sphereon.core.api.session.BasePipelineCommand<GenericHttpRequest, GenericHttpResponse, IdkError> {
        throw UnsupportedOperationException("CommandBackedHttpAdapter uses declarative endpoint commands; use endpointCommands property")
    }

    // ========== Helper methods ==========

    /**
     * Enabled endpoint commands (filtered by isEnabled).
     */
    private val enabledEndpoints: List<HttpEndpointCommand>
        get() = endpointCommands.filter { it.isEnabled }

    /**
     * Map IdkError to HTTP status code.
     */
    private fun mapErrorToStatusCode(error: IdkError): Int {
        return when {
            error.code?.contains("NOT_FOUND", ignoreCase = true) == true -> 404
            error.code?.contains("UNAUTHORIZED", ignoreCase = true) == true -> 401
            error.code?.contains("FORBIDDEN", ignoreCase = true) == true -> 403
            error.code?.contains("ILLEGAL_ARGUMENT", ignoreCase = true) == true -> 400
            error.code?.contains("INVALID", ignoreCase = true) == true -> 400
            else -> 500
        }
    }

    /**
     * Simplified constructor for common use cases.
     */
    constructor(
        id: String,
        execution: SessionExecution,
        mount: HttpAdapterMount
    ) : this(
        id = id,
        execution = execution,
        mount = mount,
        isEnabled = true,
        initExtensions = emptyArray(),
        executionExtensions = emptyArray(),
        enhancedExecutionExtensions = emptyArray(),
    )
}

