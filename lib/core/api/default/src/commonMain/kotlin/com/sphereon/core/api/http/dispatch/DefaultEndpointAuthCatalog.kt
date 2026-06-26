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

import com.sphereon.core.api.http.CompiledPathPattern
import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.config.UniversalHttpConfig
import com.sphereon.core.api.http.describe.EndpointAuthPolicy
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.di.HasOrder
import com.sphereon.di.Order
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default OSS implementation of [EndpointAuthCatalog].
 *
 * Built at AppScope from the same `Set<HttpAdapterDescriptorProvider>` the
 * [DefaultHttpAdapterCatalog] consumes, applying the same [UniversalHttpConfig]
 * enablement + mount overrides so the auth view and the routing view agree on which
 * descriptor a request maps to.
 *
 * Endpoint path patterns in a description already include the adapter base path, so the
 * full pattern is `serverPrefix + endpointPattern`. Tenant-slug peeling
 * ([TenantPathPolicy.LeadingSlug] / [TenantPathPolicy.WellKnownSuffix]) is mirrored from
 * the dispatcher: the request path is peeled the same way before matching so a public
 * protocol endpoint stays public under its tenant-scoped URL.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<EndpointAuthCatalog>())
class DefaultEndpointAuthCatalog(
    descriptorProviders: Set<HttpAdapterDescriptorProvider>,
    private val httpConfig: UniversalHttpConfig,
) : EndpointAuthCatalog,
    HasOrder {
    private data class PublicRoute(
        val method: String,
        val pattern: CompiledPathPattern,
        val tenantPathPolicy: TenantPathPolicy,
    )

    private val publicRoutes: List<PublicRoute> =
        descriptorProviders
            .filter { httpConfig.isAdapterEnabled(it.id) }
            .flatMap { provider ->
                val desc = provider.describe()
                val mount = httpConfig.resolveMount(desc.id, desc.mount)
                desc.endpoints
                    .filter { it.authPolicy == EndpointAuthPolicy.PUBLIC }
                    .flatMap { endpoint ->
                        endpoint.pathPatterns.map { pattern ->
                            PublicRoute(
                                method = endpoint.method.name,
                                pattern = CompiledPathPattern.compile(joinPath(mount.serverPrefix, pattern)),
                                tenantPathPolicy = mount.tenantPathPolicy,
                            )
                        }
                    }
            }

    override fun getOrder(): Int = Order.MEDIUM.orderValue

    override fun isPublic(
        method: String,
        resolvedPath: String,
    ): Boolean =
        publicRoutes.any { route ->
            route.method.equals(method, ignoreCase = true) &&
                candidatePaths(resolvedPath, route.tenantPathPolicy).any { route.pattern.matches(it) }
        }

    /**
     * Candidate paths to test against a route's (slug-stripped) pattern: the path as-is
     * plus, under a peeling policy, the path with up to `maxDepth` leading
     * ([TenantPathPolicy.LeadingSlug]) or trailing ([TenantPathPolicy.WellKnownSuffix])
     * segments removed — the same peel directions the dispatcher applies.
     */
    private fun candidatePaths(
        path: String,
        policy: TenantPathPolicy,
    ): List<String> {
        val segments = path.split('/').filter { it.isNotEmpty() }

        fun pathOf(segs: List<String>): String = if (segs.isEmpty()) "/" else "/" + segs.joinToString("/")
        return when (policy) {
            TenantPathPolicy.None -> {
                listOf(path)
            }

            is TenantPathPolicy.LeadingSlug -> {
                listOf(path) +
                    (1..minOf(policy.maxDepth, segments.size)).map { depth -> pathOf(segments.drop(depth)) }
            }

            is TenantPathPolicy.WellKnownSuffix -> {
                listOf(path) +
                    (1..minOf(policy.maxDepth, segments.size)).map { depth -> pathOf(segments.dropLast(depth)) }
            }
        }
    }

    private fun joinPath(
        serverPrefix: String,
        pattern: String,
    ): String {
        val prefix = normalizePathPrefix(serverPrefix)
        val pat = if (pattern.startsWith("/")) pattern else "/$pattern"
        return if (prefix.isEmpty()) pat else prefix + pat
    }
}
