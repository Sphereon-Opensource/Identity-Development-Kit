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

package com.sphereon.core.api.http.dispatch

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.TenantPathMode
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<HttpAdapterRouteSelector>())
class DefaultHttpAdapterRouteSelector(
    private val catalog: HttpAdapterCatalog,
) : HttpAdapterRouteSelector {
    override fun select(
        method: String,
        path: String,
        allowedAdapterIds: Set<String>?,
    ): HttpAdapterRouteSelection {
        val request = GenericHttpRequest(method = method, path = path)
        val candidates =
            catalog.descriptions
                .asSequence()
                .filter { description -> allowedAdapterIds == null || description.id in allowedAdapterIds }
                .flatMap { description -> candidatesFor(request, description).asSequence() }
                .toList()
        if (candidates.isEmpty()) {
            return HttpAdapterRouteSelection.NotFound(method, path)
        }

        val sorted =
            candidates.sortedWith(
                compareByDescending<Candidate> { it.score.serverPrefixSegments }
                    .thenByDescending { it.score.basePathSegments }
                    .thenByDescending { it.score.endpointLiteralSegments }
                    .thenByDescending { it.score.endpointTotalSegments }
                    .thenByDescending { it.score.tenantMatchRank }
                    .thenBy { it.score.tenantPolicyPeelDepth },
            )
        val best = sorted.first()
        val equallySpecificHandlers =
            sorted
                .takeWhile { it.score == best.score }
                .distinctBy { candidate ->
                    listOf(
                        candidate.description.id,
                        candidate.endpoint.handlerCommandId.orEmpty(),
                        candidate.tenantIdFromPath.orEmpty(),
                        candidate.normalizedPath,
                    )
                }
        if (equallySpecificHandlers.size > 1) {
            return HttpAdapterRouteSelection.Ambiguous(
                method = method,
                path = path,
                candidates = equallySpecificHandlers.take(10).map(Candidate::summary),
            )
        }

        val handlerCommandId =
            best.endpoint.handlerCommandId
                ?: return HttpAdapterRouteSelection.Misconfigured(
                    "HTTP descriptor '${best.description.id}' route ${best.endpoint.method} " +
                        "${best.matchedPathPattern} has no handlerCommandId",
                )
        return HttpAdapterRouteSelection.Selected(
            HttpAdapterRouteMatch(
                adapterId = best.description.id,
                method = method,
                originalPath = path,
                normalizedPath = best.normalizedPath,
                matchedPathPattern = best.matchedPathPattern,
                handlerCommandId = handlerCommandId,
                tenantIdFromPath = best.tenantIdFromPath,
                commandId = best.endpoint.commandId,
                pathParameters = best.pathParameters,
                maxRequestBodyBytes = best.endpoint.maxRequestBodyBytes,
            ),
        )
    }
}

private fun candidatesFor(
    request: GenericHttpRequest,
    description: HttpAdapterDescription,
): List<Candidate> {
    val mount = description.mount
    val serverPrefix = normalizePathPrefix(mount.serverPrefix)
    val basePath = normalizePathPrefix(mount.adapterBasePath).ifEmpty { "/" }
    val requestSegments = splitSegments(request.path)
    val baseSegments = splitSegments(basePath)
    val serverSegments = splitSegments(serverPrefix)

    return mountMatches(requestSegments, serverSegments, mount).flatMap { match ->
        routePolicyMatches(match.remainingSegments, baseSegments, mount.tenantPathPolicy).flatMap { policyMatch ->
            val normalizedRequest = request.copy(path = policyMatch.matchingPath)
            val matches =
                description.endpoints.flatMap { endpoint ->
                    if (!endpoint.method.name.equals(request.method, ignoreCase = true)) {
                        emptyList()
                    } else {
                        endpoint.pathPatterns
                            .filter { normalizedRequest.matches(request.method, it) }
                            .map { pattern -> EndpointMatch(endpoint, pattern) }
                    }
                }
            matches.map { endpointMatch ->
                val specificity = endpointSpecificity(endpointMatch.pattern)
                Candidate(
                    description = description,
                    endpoint = endpointMatch.endpoint,
                    matchedPathPattern = endpointMatch.pattern,
                    tenantIdFromPath = match.tenantIdFromPath,
                    normalizedPath = policyMatch.dispatchPath,
                    pathParameters =
                        normalizedRequest
                            .withExtractedParams(endpointMatch.pattern)
                            .pathParameters,
                    score =
                        CandidateScore(
                            serverPrefixSegments = serverSegments.size,
                            basePathSegments = baseSegments.size,
                            endpointLiteralSegments = specificity.first,
                            endpointTotalSegments = specificity.second,
                            tenantMatchRank = if (match.tenantMatch == TenantMatch.NONE) 0 else 1,
                            tenantPolicyPeelDepth = policyMatch.peelDepth,
                        ),
                )
            }
        }
    }
}

private data class EndpointMatch(
    val endpoint: HttpEndpointDescriptor,
    val pattern: String,
)

private data class Candidate(
    val description: HttpAdapterDescription,
    val endpoint: HttpEndpointDescriptor,
    val matchedPathPattern: String,
    val tenantIdFromPath: String?,
    val normalizedPath: String,
    val pathParameters: Map<String, String>,
    val score: CandidateScore,
) {
    fun summary(): HttpAdapterRouteCandidateSummary =
        HttpAdapterRouteCandidateSummary(
            adapterId = description.id,
            normalizedPath = normalizedPath,
            serverPrefix = description.mount.serverPrefix,
            adapterBasePath = description.mount.adapterBasePath,
        )
}

private data class CandidateScore(
    val serverPrefixSegments: Int,
    val basePathSegments: Int,
    val endpointLiteralSegments: Int,
    val endpointTotalSegments: Int,
    val tenantMatchRank: Int,
    val tenantPolicyPeelDepth: Int,
)

private data class RoutePolicyMatch(
    val dispatchPath: String,
    val matchingPath: String,
    val peelDepth: Int,
)

private fun routePolicyMatches(
    remainingSegments: List<String>,
    baseSegments: List<String>,
    tenantPathPolicy: TenantPathPolicy,
): List<RoutePolicyMatch> {
    fun pathOf(segments: List<String>): String = if (segments.isEmpty()) "/" else "/" + segments.joinToString("/")
    val matches = mutableListOf<RoutePolicyMatch>()

    fun addIfBaseMatches(
        candidateSegments: List<String>,
        dispatchSegments: List<String>,
        peelDepth: Int,
    ) {
        if (startsWithSegments(candidateSegments, baseSegments)) {
            matches += RoutePolicyMatch(pathOf(dispatchSegments), pathOf(candidateSegments), peelDepth)
        }
    }

    when (tenantPathPolicy) {
        TenantPathPolicy.None -> addIfBaseMatches(remainingSegments, remainingSegments, 0)
        is TenantPathPolicy.LeadingSlug -> {
            if (!tenantPathPolicy.required) addIfBaseMatches(remainingSegments, remainingSegments, 0)
            for (depth in 1..minOf(tenantPathPolicy.maxDepth, remainingSegments.size)) {
                addIfBaseMatches(remainingSegments.drop(depth), remainingSegments, depth)
            }
        }
        is TenantPathPolicy.WellKnownSuffix -> {
            if (!tenantPathPolicy.required) addIfBaseMatches(remainingSegments, remainingSegments, 0)
            for (depth in 1..minOf(tenantPathPolicy.maxDepth, remainingSegments.size)) {
                addIfBaseMatches(remainingSegments.dropLast(depth), remainingSegments, depth)
            }
        }
    }
    return matches
}

private enum class TenantMatch { NONE, BEFORE_SERVER_PREFIX, AFTER_SERVER_PREFIX }

private data class MountMatch(
    val tenantMatch: TenantMatch,
    val tenantIdFromPath: String?,
    val remainingSegments: List<String>,
)

private fun mountMatches(
    requestSegments: List<String>,
    serverPrefixSegments: List<String>,
    mount: HttpAdapterMount,
): List<MountMatch> {
    val tenantPatternSegments = splitSegments(normalizePathPrefix(mount.tenantSegmentPattern))

    fun matchTenantAt(offset: Int): Pair<String?, Int>? {
        if (tenantPatternSegments.isEmpty() || requestSegments.size < offset + tenantPatternSegments.size) return null
        var tenantId: String? = null
        tenantPatternSegments.forEachIndexed { index, pattern ->
            val actual = requestSegments[offset + index]
            if (pattern.startsWith("{") && pattern.endsWith("}")) {
                if (pattern.removeSurrounding("{", "}") == "tenantId") tenantId = actual
            } else if (pattern != actual) {
                return null
            }
        }
        return tenantId to (offset + tenantPatternSegments.size)
    }

    fun matchServerAt(offset: Int): Int? {
        if (requestSegments.size < offset + serverPrefixSegments.size) return null
        if (serverPrefixSegments.indices.any { requestSegments[offset + it] != serverPrefixSegments[it] }) return null
        return offset + serverPrefixSegments.size
    }

    val results = mutableListOf<MountMatch>()
    when (mount.tenantPathMode) {
        TenantPathMode.OFF -> {
            val afterServer = matchServerAt(0) ?: return emptyList()
            results += MountMatch(TenantMatch.NONE, null, requestSegments.drop(afterServer))
        }
        TenantPathMode.BEFORE_SERVER_PREFIX -> {
            val tenant = matchTenantAt(0) ?: return emptyList()
            val afterServer = matchServerAt(tenant.second) ?: return emptyList()
            results += MountMatch(TenantMatch.BEFORE_SERVER_PREFIX, tenant.first, requestSegments.drop(afterServer))
        }
        TenantPathMode.AFTER_SERVER_PREFIX -> {
            val afterServer = matchServerAt(0) ?: return emptyList()
            val tenant = matchTenantAt(afterServer) ?: return emptyList()
            results += MountMatch(TenantMatch.AFTER_SERVER_PREFIX, tenant.first, requestSegments.drop(tenant.second))
        }
        TenantPathMode.BOTH -> {
            matchTenantAt(0)?.let { tenant ->
                matchServerAt(tenant.second)?.let { afterServer ->
                    results += MountMatch(TenantMatch.BEFORE_SERVER_PREFIX, tenant.first, requestSegments.drop(afterServer))
                }
            }
            matchServerAt(0)?.let { afterServer ->
                matchTenantAt(afterServer)?.let { tenant ->
                    results += MountMatch(TenantMatch.AFTER_SERVER_PREFIX, tenant.first, requestSegments.drop(tenant.second))
                }
            }
        }
    }
    return results
}

private fun splitSegments(path: String): List<String> = path.split('/').filter { it.isNotBlank() }

private fun startsWithSegments(
    value: List<String>,
    prefix: List<String>,
): Boolean = value.size >= prefix.size && prefix.indices.all { value[it] == prefix[it] }

private fun endpointSpecificity(pathPattern: String): Pair<Int, Int> {
    val segments = splitSegments(pathPattern)
    return segments.count { !(it.startsWith("{") && it.endsWith("}")) } to segments.size
}
