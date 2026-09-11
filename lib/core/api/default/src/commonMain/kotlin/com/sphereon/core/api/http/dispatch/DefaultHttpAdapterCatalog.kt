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

import com.sphereon.core.api.http.config.UniversalHttpConfig
import com.sphereon.core.api.http.config.UniversalHttpConfigContribution
import com.sphereon.core.api.http.config.DefaultUniversalHttpConfigAggregator
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.log.Log
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default OSS implementation of [HttpAdapterCatalog].
 *
 * Applies [UniversalHttpConfig] overrides to adapter descriptions:
 * - Per-adapter mount overrides (serverPrefix, adapterBasePath, tenant settings)
 * - Adapter enablement (disabled adapters are excluded from the catalog)
 *
 * Construction fails closed when provider identities collide or a route lacks the stable
 * endpoint-handler identity required by route-first dispatch.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<HttpAdapterCatalog>())
class DefaultHttpAdapterCatalog(
    descriptorProviders: Set<HttpAdapterDescriptorProvider>,
    private val httpConfig: UniversalHttpConfig,
) : HttpAdapterCatalog {
    override val descriptions: List<HttpAdapterDescription> =
        buildDescriptions(descriptorProviders, httpConfig)

    override val diagnostics: HttpAdapterCatalogDiagnostics = HttpAdapterCatalogDiagnostics.from(descriptions)

    init {
        requireNoCollisions()
        // The catalog decides every dispatchable route; one line at construction states what
        // this process actually serves, so a route-not-found never has to be guessed at.
        Log.app().withTag(LOG_TAG).info(
            "HTTP adapter catalog built: ${descriptions.size} adapters " +
                descriptions.joinToString(", ", prefix = "[", postfix = "]") { description ->
                    "${description.id}(${description.endpoints.size})"
                },
        )
    }

    override fun describeAll(): List<HttpAdapterDescription> = descriptions

    override fun descriptionById(id: String): HttpAdapterDescription? = descriptions.firstOrNull { it.id == id }

    override fun requireNoCollisions() {
        val collisions = diagnostics.collisions
        require(collisions.isEmpty()) {
            "HttpAdapterCatalog has collisions:\n" + collisions.joinToString("\n") { it.message }
        }
    }

    /**
     * Canonical AppScope binding for [UniversalHttpConfig]. An empty contribution set produces
     * the fail-closed defaults; deployment components add disjoint configuration contributions
     * instead of replacing this graph.
     */
    @ContributesTo(AppScope::class)
    interface DefaultConfigGraph {
        @Provides
        fun provideUniversalHttpConfig(
            contributions: Set<UniversalHttpConfigContribution>,
        ): UniversalHttpConfig = DefaultUniversalHttpConfigAggregator.aggregate(contributions)
    }

    private companion object {
        const val LOG_TAG = "HttpAdapterCatalog"

        fun buildDescriptions(
            descriptorProviders: Set<HttpAdapterDescriptorProvider>,
            httpConfig: UniversalHttpConfig,
        ): List<HttpAdapterDescription> {
            val duplicateProviderIds =
                descriptorProviders
                    .groupBy(HttpAdapterDescriptorProvider::id)
                    .filterValues { it.size > 1 }
                    .keys
                    .sorted()
            require(duplicateProviderIds.isEmpty()) {
                "Duplicate HTTP adapter descriptor provider ids: ${duplicateProviderIds.joinToString()}"
            }

            val contributedDescriptions =
                descriptorProviders.map { provider ->
                    val description = provider.describe()
                    require(description.id == provider.id) {
                        "HTTP adapter descriptor provider '${provider.id}' described '${description.id}'"
                    }
                    description
                }

            val enabledDescriptions = contributedDescriptions.filter { httpConfig.isAdapterEnabled(it.id) }
            val missingHandlerByAdapter =
                enabledDescriptions.mapNotNull { description ->
                    val missing =
                        description.endpoints
                            .filter { it.handlerCommandId.isNullOrBlank() }
                            .map { endpoint -> endpoint.operationId ?: "${endpoint.method} ${endpoint.pathPattern}" }
                    if (missing.isEmpty()) {
                        null
                    } else {
                        "'${description.id}' (${missing.joinToString()})"
                    }
                }
            require(missingHandlerByAdapter.isEmpty()) {
                "HTTP adapters have endpoints without handlerCommandId: " +
                    missingHandlerByAdapter.joinToString(separator = "; ")
            }

            return enabledDescriptions
                .map { description ->
                    validateDescription(description)
                    val resolvedMount = httpConfig.resolveMount(description.id, description.mount)
                    val resolvedDescription =
                        description.copy(
                            mount = resolvedMount,
                            endpoints =
                                description.endpoints.map { endpoint ->
                                    endpoint.copy(
                                        pathPatterns =
                                            endpoint.pathPatterns.map { pattern ->
                                                remountPathPattern(
                                                    pattern = pattern,
                                                    declaredBasePath = description.mount.adapterBasePath,
                                                    resolvedBasePath = resolvedMount.adapterBasePath,
                                                )
                                            },
                                    )
                                },
                        )
                    validateDescription(resolvedDescription)
                    resolvedDescription
                }.sortedBy { it.id }
        }

        fun validateDescription(description: HttpAdapterDescription) {
            val missingHandlerOperations =
                description.endpoints
                    .filter { it.handlerCommandId.isNullOrBlank() }
                    .map { endpoint -> endpoint.operationId ?: "${endpoint.method} ${endpoint.pathPattern}" }
            require(missingHandlerOperations.isEmpty()) {
                "HTTP adapter '${description.id}' has endpoints without handlerCommandId: " +
                    missingHandlerOperations.joinToString()
            }

            val basePath = normalizePathPrefix(description.mount.adapterBasePath)
            description.endpoints.forEach { endpoint ->
                endpoint.pathPatterns.forEach { pattern ->
                    require(pattern.startsWith('/')) {
                        "HTTP adapter '${description.id}' endpoint pathPattern '$pattern' must be absolute"
                    }
                    require(basePath.isEmpty() || pattern == basePath || pattern.startsWith("$basePath/")) {
                        "HTTP adapter '${description.id}' endpoint pathPattern '$pattern' is outside adapterBasePath '$basePath'"
                    }
                }
            }
        }

        fun remountPathPattern(
            pattern: String,
            declaredBasePath: String,
            resolvedBasePath: String,
        ): String {
            val declaredBase = normalizePathPrefix(declaredBasePath)
            val resolvedBase = normalizePathPrefix(resolvedBasePath)
            val suffix = if (declaredBase.isEmpty()) pattern else pattern.removePrefix(declaredBase)
            return when {
                resolvedBase.isEmpty() -> suffix.ifEmpty { "/" }
                suffix.isEmpty() -> resolvedBase
                // A relative root endpoint belongs to the adapter base itself. Keep one canonical
                // identity for route selection and SessionScope handler validation instead of
                // advertising a second trailing-slash spelling of the same route.
                suffix == "/" -> resolvedBase
                else -> resolvedBase + "/" + suffix.trimStart('/')
            }
        }
    }
}
