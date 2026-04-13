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

package com.sphereon.core.api.http.dispatch

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.errorResponse
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.TenantPathMode
import com.sphereon.core.api.http.describe.TenantResolutionPriority
import com.sphereon.di.Order
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default OSS implementation of [HttpAdapterDispatcher].
 *
 * EDK can replace this by providing an implementation with lower [getOrder] value
 * (e.g., [Order.HIGH] vs this class's [Order.MEDIUM]).
 *
 * **Replacement pattern:**
 * ```kotlin
 * @Inject
 * @SingleIn(SessionScope::class)
 * @ContributesBinding(SessionScope::class, binding = binding<HttpAdapterDispatcher>())
 * class EdkHttpAdapterDispatcher(...) : HttpAdapterDispatcher {
 *     override fun getOrder(): Int = Order.HIGH.orderValue // Wins over OSS default
 *     // ...
 * }
 * ```
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HttpAdapterDispatcher>())
class DefaultHttpAdapterDispatcher(
    private val catalog: HttpAdapterCatalog,
    private val adapters: Set<HttpAdapter>
) : HttpAdapterDispatcher {

    override fun getOrder(): Int = Order.MEDIUM.orderValue

    /**
     * Component interface for individual access via [getSessionService].
     *
     * This @ContributesTo interface is required to make [HttpAdapterDispatcher] accessible
     * as a property in the kotlin-inject SessionComponent. While @ContributesBinding generates
     * provider methods, it doesn't create a property getter for the interface.
     *
     * This pattern enables both:
     * - Multibinding replacement (EDK can provide a higher-priority implementation)
     * - Service lookup (via `call.getSessionService<HttpAdapterDispatcher>()`)
     */
    @ContributesTo(SessionScope::class)
    interface Component {
        val httpAdapterDispatcher: HttpAdapterDispatcher
    }

    override suspend fun dispatch(request: GenericHttpRequest): GenericHttpResponse {
        val adapterById = adapters.groupBy { it.id }
        val duplicateRuntimeAdapters = adapterById.filterValues { it.size > 1 }.keys.sorted()
        if (duplicateRuntimeAdapters.isNotEmpty()) {
            return errorResponse(500, "Multiple runtime HttpAdapter instances found for ids: ${duplicateRuntimeAdapters.joinToString(", ")}")
        }

        val candidates = catalog.descriptions
            .flatMap { desc -> candidatesFor(request, desc) }

        if (candidates.isEmpty()) {
            return errorResponse(404, "Not found: ${request.method} ${request.path}")
        }

        val sorted = candidates.sortedWith(compareByDescending<Candidate> { it.score.serverPrefixSegments }
            .thenByDescending { it.score.basePathSegments }
            .thenByDescending { it.score.bestEndpointLiteralSegments }
            .thenByDescending { it.score.bestEndpointTotalSegments }
            .thenByDescending { it.score.tenantMatchRank })

        val best = sorted.first()
        val second = sorted.getOrNull(1)
        if (second != null && best.score == second.score) {
            val message = buildString {
                append("Ambiguous adapter match for ${request.method} ${request.path}. Candidates:\n")
                sorted.take(10).forEach { c ->
                    append("- id=${c.description.id}, serverPrefix='${c.description.mount.serverPrefix}', basePath='${c.description.mount.adapterBasePath}', tenantMatch=${c.tenantMatch}, normalizedPath='${c.normalizedPath}'\n")
                }
            }
            return errorResponse(500, message.trimEnd())
        }

        val adapter = adapterById[best.description.id]?.singleOrNull()
            ?: return errorResponse(500, "No runtime adapter instance found for id '${best.description.id}'")

        val normalizedRequest = best.applyTo(request)
        return adapter.handleRequest(normalizedRequest)
    }
}

private fun candidatesFor(request: GenericHttpRequest, description: HttpAdapterDescription): List<Candidate> {
    val mount = description.mount
    val serverPrefix = normalizePathPrefix(mount.serverPrefix)
    val basePath = normalizePathPrefix(mount.adapterBasePath).ifEmpty { "/" }
    val requestSegments = splitSegments(request.path)
    val baseSegments = splitSegments(basePath)
    val serverSegments = splitSegments(serverPrefix)

    val mountMatches = mountMatches(requestSegments, serverSegments, mount)
    return mountMatches.mapNotNull { match ->
        val remainingSegments = match.remainingSegments
        if (!startsWithSegments(remainingSegments, baseSegments)) return@mapNotNull null

        val normalizedPath = "/" + remainingSegments.joinToString("/")
        val normalizedReqForMatching = request.copy(path = normalizedPath)

        val matchingEndpoints = description.endpoints
            .asSequence()
            .filter { it.method.name.equals(request.method, ignoreCase = true) }
            .filter { normalizedReqForMatching.matches(request.method, it.pathPattern) }
            .toList()

        if (matchingEndpoints.isEmpty()) return@mapNotNull null

        val bestEndpointScore = matchingEndpoints
            .map { endpointSpecificity(it.pathPattern) }
            .maxWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })

        Candidate(
            description = description,
            tenantMatch = match.tenantMatch,
            tenantIdFromPath = match.tenantIdFromPath,
            normalizedPath = normalizedPath,
            score = CandidateScore(
                serverPrefixSegments = serverSegments.size,
                basePathSegments = baseSegments.size,
                bestEndpointLiteralSegments = bestEndpointScore.first,
                bestEndpointTotalSegments = bestEndpointScore.second,
                tenantMatchRank = when (match.tenantMatch) {
                    TenantMatch.NONE -> 0
                    TenantMatch.AFTER_SERVER_PREFIX -> 1
                    TenantMatch.BEFORE_SERVER_PREFIX -> 1
                }
            )
        )
    }
}

private data class Candidate(
    val description: HttpAdapterDescription,
    val tenantMatch: TenantMatch,
    val tenantIdFromPath: String?,
    val normalizedPath: String,
    val score: CandidateScore
) {
    fun applyTo(request: GenericHttpRequest): GenericHttpRequest {
        val effectiveTenantId = resolveTenantId(
            existingTenantId = request.pathParameters["tenantId"],
            pathTenantId = tenantIdFromPath,
            priority = description.mount.tenantResolutionPriority
        )

        val newPathParams = if (effectiveTenantId != null) request.pathParameters + mapOf("tenantId" to effectiveTenantId) else request.pathParameters
        return request.copy(path = normalizedPath, pathParameters = newPathParams)
    }
}

private data class CandidateScore(
    val serverPrefixSegments: Int,
    val basePathSegments: Int,
    val bestEndpointLiteralSegments: Int,
    val bestEndpointTotalSegments: Int,
    val tenantMatchRank: Int
)

private enum class TenantMatch {
    NONE,
    BEFORE_SERVER_PREFIX,
    AFTER_SERVER_PREFIX
}

private data class MountMatch(
    val tenantMatch: TenantMatch,
    val tenantIdFromPath: String?,
    val remainingSegments: List<String>
)

private fun mountMatches(
    requestSegments: List<String>,
    serverPrefixSegments: List<String>,
    mount: HttpAdapterMount
): List<MountMatch> {
    val tenantPatternSegments = splitSegments(normalizePathPrefix(mount.tenantSegmentPattern))

    fun matchTenantAt(offset: Int): Pair<String?, Int>? {
        if (tenantPatternSegments.isEmpty()) return null
        if (requestSegments.size < offset + tenantPatternSegments.size) return null

        var tenantId: String? = null
        tenantPatternSegments.forEachIndexed { i, pat ->
            val actual = requestSegments[offset + i]
            if (pat.startsWith("{") && pat.endsWith("}")) {
                val name = pat.removeSurrounding("{", "}")
                if (name == "tenantId") tenantId = actual
            } else if (pat != actual) {
                return null
            }
        }
        return tenantId to (offset + tenantPatternSegments.size)
    }

    fun matchServerAt(offset: Int): Int? {
        if (requestSegments.size < offset + serverPrefixSegments.size) return null
        if (serverPrefixSegments.isEmpty()) return offset
        return if (serverPrefixSegments.indices.all { i -> requestSegments[offset + i] == serverPrefixSegments[i] }) {
            offset + serverPrefixSegments.size
        } else {
            null
        }
    }

    val results = mutableListOf<MountMatch>()

    when (mount.tenantPathMode) {
        TenantPathMode.OFF -> {
            val afterServer = matchServerAt(0) ?: return emptyList()
            results += MountMatch(
                tenantMatch = TenantMatch.NONE,
                tenantIdFromPath = null,
                remainingSegments = requestSegments.drop(afterServer)
            )
        }

        TenantPathMode.BEFORE_SERVER_PREFIX -> {
            val (tenantId, afterTenant) = matchTenantAt(0) ?: return emptyList()
            val afterServer = matchServerAt(afterTenant) ?: return emptyList()
            results += MountMatch(
                tenantMatch = TenantMatch.BEFORE_SERVER_PREFIX,
                tenantIdFromPath = tenantId,
                remainingSegments = requestSegments.drop(afterServer)
            )
        }

        TenantPathMode.AFTER_SERVER_PREFIX -> {
            val afterServer = matchServerAt(0) ?: return emptyList()
            val (tenantId, afterTenant) = matchTenantAt(afterServer) ?: return emptyList()
            results += MountMatch(
                tenantMatch = TenantMatch.AFTER_SERVER_PREFIX,
                tenantIdFromPath = tenantId,
                remainingSegments = requestSegments.drop(afterTenant)
            )
        }

        TenantPathMode.BOTH -> {
            // Try both placements; later selection is deterministic via endpoint/basePath specificity scoring.
            run {
                val tenant = matchTenantAt(0)
                val afterServer = tenant?.second?.let { matchServerAt(it) }
                if (tenant != null && afterServer != null) {
                    results += MountMatch(
                        tenantMatch = TenantMatch.BEFORE_SERVER_PREFIX,
                        tenantIdFromPath = tenant.first,
                        remainingSegments = requestSegments.drop(afterServer)
                    )
                }
            }
            run {
                val afterServer = matchServerAt(0)
                val tenant = afterServer?.let { matchTenantAt(it) }
                if (afterServer != null && tenant != null) {
                    results += MountMatch(
                        tenantMatch = TenantMatch.AFTER_SERVER_PREFIX,
                        tenantIdFromPath = tenant.first,
                        remainingSegments = requestSegments.drop(tenant.second)
                    )
                }
            }
        }
    }

    return results
}

private fun resolveTenantId(
    existingTenantId: String?,
    pathTenantId: String?,
    priority: TenantResolutionPriority
): String? {
    return when (priority) {
        TenantResolutionPriority.HEADER_THEN_PATH -> existingTenantId ?: pathTenantId
        TenantResolutionPriority.PATH_THEN_HEADER -> pathTenantId ?: existingTenantId
    }
}

private fun splitSegments(path: String): List<String> =
    path.split('/').filter { it.isNotBlank() }

private fun startsWithSegments(value: List<String>, prefix: List<String>): Boolean {
    if (prefix.isEmpty()) return true
    if (value.size < prefix.size) return false
    return prefix.indices.all { i -> value[i] == prefix[i] }
}

private fun endpointSpecificity(pathPattern: String): Pair<Int, Int> {
    val segments = splitSegments(pathPattern)
    val literalCount = segments.count { !(it.startsWith("{") && it.endsWith("}")) }
    return literalCount to segments.size
}
