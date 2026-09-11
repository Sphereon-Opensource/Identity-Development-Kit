package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandRegistry
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteMatch

internal class TestHttpEndpointCommandRegistry(
    vararg commands: HttpEndpointCommand,
) : HttpEndpointCommandRegistry {
    private val commandsById = commands.associateBy { it.id }

    override fun get(handlerCommandId: String): HttpEndpointCommand? = commandsById[handlerCommandId]

    override fun listHandlerCommandIds(): Set<String> = commandsById.keys

    internal fun commands(): Collection<HttpEndpointCommand> = commandsById.values
}

internal data class TestHttpAdapterRoute(
    val adapter: HttpAdapter,
    val registry: TestHttpEndpointCommandRegistry,
)

internal suspend fun dispatchForTest(
    routes: List<TestHttpAdapterRoute>,
    request: GenericHttpRequest,
): GenericHttpResponse {
    val resolved = routes.mapNotNull { it.resolve(request) }
    if (resolved.isEmpty()) {
        return GenericHttpResponse(404, emptyMap(), "Not found")
    }
    require(resolved.size == 1) {
        "Expected exactly one test HTTP route for ${request.method} ${request.path}; " +
            "matched=${resolved.map { it.route.adapterId + ":" + it.route.handlerCommandId }}"
    }
    val match = resolved.single()
    return match.adapter.handleResolvedRequest(match.route.applyTo(request), match.route)
}

private data class ResolvedTestRoute(
    val adapter: HttpAdapter,
    val route: HttpAdapterRouteMatch,
)

private fun TestHttpAdapterRoute.resolve(request: GenericHttpRequest): ResolvedTestRoute? {
    val mount = adapter.describe().mount
    val segments = request.path.trim('/').split('/').filter { it.isNotEmpty() }
    val normalizedCandidates =
        buildList {
            add(request.path)
            for (dropCount in 1 until segments.size) {
                add("/" + segments.drop(dropCount).joinToString("/"))
            }
        }.distinct()
    for (normalizedPath in normalizedCandidates) {
        val relativePath =
            when {
                mount.adapterBasePath.isEmpty() || mount.adapterBasePath == "/" -> normalizedPath
                normalizedPath == mount.adapterBasePath -> "/"
                normalizedPath.startsWith(mount.adapterBasePath.trimEnd('/') + "/") ->
                    normalizedPath.removePrefix(mount.adapterBasePath).ifEmpty { "/" }
                else -> continue
            }
        val relativeRequest = request.copy(path = relativePath)
        for (command in registry.commands()) {
            val endpointPattern =
                command.endpoint.pathPatterns.firstOrNull {
                    relativeRequest.matches(command.endpoint.method.name, it)
                } ?: continue
            val catalogPattern =
                when {
                    mount.adapterBasePath.isEmpty() || mount.adapterBasePath == "/" -> endpointPattern
                    endpointPattern == "/" -> mount.adapterBasePath
                    else -> mount.adapterBasePath.trimEnd('/') + "/" + endpointPattern.trimStart('/')
                }
            return ResolvedTestRoute(
                adapter,
                HttpAdapterRouteMatch(
                    adapterId = adapter.id,
                    method = request.method,
                    originalPath = request.path,
                    normalizedPath = normalizedPath,
                    matchedPathPattern = catalogPattern,
                    handlerCommandId = command.id,
                    tenantIdFromPath = null,
                ),
            )
        }
    }
    return null
}
