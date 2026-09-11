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

import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import kotlin.jvm.JvmStatic

/**
 * Startup, metadata-only index of contributed HTTP adapters.
 *
 * This lives in App scope and must not require instantiating Session-scoped adapters. There is one
 * canonical catalog per application graph; deployments constrain exposure through route-mount policy.
 */
@JsExportCompat
interface HttpAdapterCatalog {
    val descriptions: List<HttpAdapterDescription>
    val diagnostics: HttpAdapterCatalogDiagnostics

    fun describeAll(): List<HttpAdapterDescription>

    fun descriptionById(id: String): HttpAdapterDescription?

    /**
     * Throws when collisions/ambiguities are detected.
     */
    fun requireNoCollisions()

    /**
     * Session-boundary access to the already-validated application route catalog.
     *
     * Transport authorization resolves only a handler identity that the catalog
     * exposes. This is required for legacy [com.sphereon.core.api.http.RoutedHttpAdapter]
     * endpoints, whose handlers are inline rather than standalone endpoint commands.
     */
    @JsExportIgnoreCompat
    @ContributesTo(SessionScope::class)
    interface Graph {
        val httpAdapterCatalog: HttpAdapterCatalog
    }
}

@JsExportCompat
data class HttpAdapterCatalogDiagnostics(
    val collisions: List<HttpAdapterCatalogCollision>,
) {
    companion object {
        @JvmStatic
        fun from(descriptions: List<HttpAdapterDescription>): HttpAdapterCatalogDiagnostics {
            val collisions = mutableListOf<HttpAdapterCatalogCollision>()

            // Duplicate adapter ids
            descriptions
                .groupBy { it.id }
                .filter { (_, group) -> group.size > 1 }
                .forEach { (id, group) ->
                    collisions +=
                        HttpAdapterCatalogCollision(
                            type = HttpAdapterCatalogCollisionType.DUPLICATE_ADAPTER_ID,
                            message = "Duplicate adapter id '$id' contributed ${group.size} times",
                        )
                }

            // Overlapping physical mounts with overlapping endpoint patterns and tenant-path policies.
            // A required leading slug and a required suffix slug are disjoint route spaces even
            // when their normalized handler patterns are identical.
            descriptions
                .groupBy { it.mountKey() }
                .filter { (_, group) -> group.size > 1 }
                .forEach { (mountKey, group) ->
                    val endpointToAdapters = linkedMapOf<EndpointKey, MutableList<HttpAdapterDescription>>()
                    group.forEach { desc ->
                        desc.endpoints.forEach { ep ->
                            ep.pathPatterns.forEach { pattern ->
                                endpointToAdapters
                                    .getOrPut(EndpointKey(ep.method.name, pattern)) { mutableListOf() }
                                    .add(desc)
                            }
                        }
                    }

                    endpointToAdapters
                        .forEach { (endpointKey, adapters) ->
                            val overlappingIds = linkedSetOf<String>()
                            adapters.indices.forEach { firstIndex ->
                                for (secondIndex in firstIndex + 1 until adapters.size) {
                                    val first = adapters[firstIndex]
                                    val second = adapters[secondIndex]
                                    if (
                                        first.id != second.id &&
                                        tenantPathPoliciesMayOverlap(
                                            first.mount.tenantPathPolicy,
                                            second.mount.tenantPathPolicy,
                                        )
                                    ) {
                                        overlappingIds += first.id
                                        overlappingIds += second.id
                                    }
                                }
                            }
                            if (overlappingIds.size <= 1) return@forEach
                            collisions +=
                                HttpAdapterCatalogCollision(
                                    type = HttpAdapterCatalogCollisionType.OVERLAPPING_ENDPOINT,
                                    message = "Overlapping endpoint ${endpointKey.method} ${endpointKey.pathPattern} for mount $mountKey in adapters: ${overlappingIds.sorted().joinToString(", ")}",
                                )
                        }
                }

            // Duplicate endpoints within a single adapter (almost always a bug)
            descriptions.forEach { desc ->
                val seen = linkedSetOf<EndpointKey>()
                desc.endpoints.forEach { ep ->
                    ep.pathPatterns.forEach { pattern ->
                        val key = EndpointKey(ep.method.name, pattern)
                        if (!seen.add(key)) {
                            collisions +=
                                HttpAdapterCatalogCollision(
                                    type = HttpAdapterCatalogCollisionType.DUPLICATE_ENDPOINT_IN_ADAPTER,
                                    message = "Adapter '${desc.id}' declares duplicate endpoint ${key.method} ${key.pathPattern}",
                                )
                        }
                    }
                }
            }

            return HttpAdapterCatalogDiagnostics(collisions = collisions.toList())
        }
    }
}

@JsExportCompat
enum class HttpAdapterCatalogCollisionType {
    DUPLICATE_ADAPTER_ID,
    OVERLAPPING_ENDPOINT,
    DUPLICATE_ENDPOINT_IN_ADAPTER,
}

@JsExportCompat
data class HttpAdapterCatalogCollision(
    val type: HttpAdapterCatalogCollisionType,
    val message: String,
)

private data class EndpointKey(
    val method: String,
    val pathPattern: String,
)

private data class MountKey(
    val serverPrefix: String,
    val tenantPathMode: String,
    val adapterBasePath: String,
)

private fun HttpAdapterDescription.mountKey(): MountKey =
    MountKey(
        serverPrefix = normalizePathPrefix(mount.serverPrefix),
        tenantPathMode = mount.tenantPathMode.name,
        adapterBasePath = normalizePathPrefix(mount.adapterBasePath),
    )

private fun tenantPathPoliciesMayOverlap(
    first: TenantPathPolicy,
    second: TenantPathPolicy,
): Boolean =
    (!first.required && !second.required) ||
        (first is TenantPathPolicy.LeadingSlug && second is TenantPathPolicy.LeadingSlug) ||
        (first is TenantPathPolicy.WellKnownSuffix && second is TenantPathPolicy.WellKnownSuffix)

fun normalizePathPrefix(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        return ""
    }
    val withLeading =
        if (trimmed.startsWith("/")) {
            trimmed
        } else {
            "/$trimmed"
        }
    return withLeading.removeSuffix("/").ifEmpty { "" }
}
