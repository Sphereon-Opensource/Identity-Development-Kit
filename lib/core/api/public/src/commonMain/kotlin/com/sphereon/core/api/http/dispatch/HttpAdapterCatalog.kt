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

import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.HasOrder
import kotlin.jvm.JvmStatic

/**
 * Startup, metadata-only index of contributed HTTP adapters.
 *
 * This is intended to live in App scope and must not require instantiating Session-scoped adapters.
 *
 * **Replacement via DI:**
 * This interface extends [HasOrder] so multiple implementations can be contributed,
 * and the highest-priority one (lowest [getOrder] value) wins. Use [com.sphereon.di.selectByOrder]
 * to select the winning implementation from a `Set<HttpAdapterCatalog>`.
 *
 * The codebase standard is to depend on interfaces (not concrete implementations).
 */
@JsExportCompat
interface HttpAdapterCatalog : HasOrder {
    val descriptions: List<HttpAdapterDescription>
    val diagnostics: HttpAdapterCatalogDiagnostics

    fun describeAll(): List<HttpAdapterDescription>

    fun descriptionById(id: String): HttpAdapterDescription?

    /**
     * Throws when collisions/ambiguities are detected.
     */
    fun requireNoCollisions()
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

            // Overlapping mounts (serverPrefix + tenant mode + base path) with overlapping endpoints by method+pattern
            descriptions
                .groupBy { it.mountKey() }
                .filter { (_, group) -> group.size > 1 }
                .forEach { (mountKey, group) ->
                    val endpointToAdapters = linkedMapOf<EndpointKey, MutableList<String>>()
                    group.forEach { desc ->
                        desc.endpoints.forEach { ep ->
                            endpointToAdapters
                                .getOrPut(EndpointKey(ep.method.name, ep.pathPattern)) { mutableListOf() }
                                .add(desc.id)
                        }
                    }

                    endpointToAdapters
                        .filter { (_, ids) -> ids.distinct().size > 1 }
                        .forEach { (endpointKey, ids) ->
                            collisions +=
                                HttpAdapterCatalogCollision(
                                    type = HttpAdapterCatalogCollisionType.OVERLAPPING_ENDPOINT,
                                    message = "Overlapping endpoint ${endpointKey.method} ${endpointKey.pathPattern} for mount $mountKey in adapters: ${ids.distinct().sorted().joinToString(", ")}",
                                )
                        }
                }

            // Duplicate endpoints within a single adapter (almost always a bug)
            descriptions.forEach { desc ->
                val seen = linkedSetOf<EndpointKey>()
                desc.endpoints.forEach { ep ->
                    val key = EndpointKey(ep.method.name, ep.pathPattern)
                    if (!seen.add(key)) {
                        collisions +=
                            HttpAdapterCatalogCollision(
                                type = HttpAdapterCatalogCollisionType.DUPLICATE_ENDPOINT_IN_ADAPTER,
                                message = "Adapter '${desc.id}' declares duplicate endpoint ${key.method} ${key.pathPattern}",
                            )
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
