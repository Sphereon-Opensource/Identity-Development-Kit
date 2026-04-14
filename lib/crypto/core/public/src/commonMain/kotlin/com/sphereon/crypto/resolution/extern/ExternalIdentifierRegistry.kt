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

package com.sphereon.crypto.resolution.extern

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.native.ObjCName

/**
 * Marker interface for all external identifier types.
 *
 * External libraries can implement this interface to define custom identifier types
 * that can be resolved by the identifier resolution system.
 *
 * This follows the marker interface + registry pattern for JS compatibility,
 * avoiding sealed classes and enums which don't export well to JavaScript.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("ExternalIdentifierBase", exact = true)
interface ExternalIdentifierBase

/**
 * Interface for resolving external identifiers to key information.
 *
 * Implementations handle specific identifier types (DID, JWK, X5C, etc.).
 * Register resolvers with [ExternalIdentifierResolverRegistry] to enable resolution.
 *
 * Example implementation:
 * ```kotlin
 * class DidResolver : ExternalIdentifierResolver {
 *     override val priority = 100
 *
 *     override fun supports(identifier: ExternalIdentifierBase): Boolean {
 *         return identifier is ExternalIdentifierDidOpts
 *     }
 *
 *     override suspend fun resolve(identifier: ExternalIdentifierBase): IdkResult<ExternalIdentifierResult, IdkError> {
 *         val didOpts = identifier as ExternalIdentifierDidOpts
 *         // ... resolve DID and return result
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("ExternalIdentifierResolver", exact = true)
interface ExternalIdentifierResolver {
    /**
     * Priority for resolver selection. Higher values take precedence.
     * When multiple resolvers support an identifier, the one with highest priority wins.
     */
    val priority: Int

    /**
     * Name of this resolver for diagnostics and logging.
     */
    val name: String

    /**
     * Check if this resolver can handle the given identifier.
     *
     * Implementations should use exact type matching where possible:
     * ```kotlin
     * override fun supports(identifier: ExternalIdentifierBase) = identifier is MyIdentifierType
     * ```
     *
     * @param identifier The identifier to check
     * @return true if this resolver can resolve the identifier
     */
    fun supports(identifier: ExternalIdentifierBase): Boolean

    /**
     * Resolve an identifier to key information.
     *
     * @param identifier The identifier to resolve (guaranteed to pass [supports] check)
     * @return Result containing the resolved key information or an error
     */
    suspend fun resolve(identifier: ExternalIdentifierBase): IdkResult<ExternalIdentifierResult, IdkError>
}

/**
 * Registry for external identifier resolvers.
 *
 * Provides deterministic resolver selection based on priority.
 * When multiple resolvers support an identifier, the highest priority resolver is used.
 *
 * Usage:
 * ```kotlin
 * val registry = ExternalIdentifierResolverRegistry(
 *     listOf(didResolver, jwkResolver, x5cResolver)
 * )
 *
 * val identifier = ExternalIdentifierDidOpts(identifier = "did:example:123")
 * val result = registry.resolve(identifier)
 * ```
 *
 * @param resolvers List of resolvers to register. Order doesn't matter; priority determines selection.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("ExternalIdentifierResolverRegistry", exact = true)
class ExternalIdentifierResolverRegistry(
    private val resolvers: List<ExternalIdentifierResolver>,
) {
    // Sort by priority descending for deterministic selection
    private val sortedResolvers = resolvers.sortedByDescending { it.priority }

    /**
     * Find all resolvers that support the given identifier, sorted by priority.
     */
    fun findResolvers(identifier: ExternalIdentifierBase): List<ExternalIdentifierResolver> = sortedResolvers.filter { it.supports(identifier) }

    /**
     * Find the best resolver for the given identifier.
     *
     * @param identifier The identifier to find a resolver for
     * @return The highest-priority resolver that supports the identifier, or null if none found
     */
    fun findResolver(identifier: ExternalIdentifierBase): ExternalIdentifierResolver? = sortedResolvers.firstOrNull { it.supports(identifier) }

    /**
     * Resolve an identifier to key information using the best available resolver.
     *
     * @param identifier The identifier to resolve
     * @return Result containing the resolved key information or an error
     */
    suspend fun resolve(identifier: ExternalIdentifierBase): IdkResult<ExternalIdentifierResult, IdkError> {
        val resolver =
            findResolver(identifier)
                ?: return Err(
                    IdkError.NOT_FOUND_ERROR(
                        resource = "ExternalIdentifierResolver",
                        message =
                            "No resolver found for identifier type: ${identifier::class.simpleName}. " +
                                "Available resolvers: ${sortedResolvers.map { it.name }}",
                    ),
                )

        return resolver.resolve(identifier)
    }

    /**
     * Get diagnostics about registered resolvers.
     */
    fun getDiagnostics(): ExternalIdentifierRegistryDiagnostics =
        ExternalIdentifierRegistryDiagnostics(
            resolverCount = resolvers.size,
            resolvers =
                sortedResolvers.map {
                    ResolverInfo(name = it.name, priority = it.priority)
                },
        )
}

/**
 * Diagnostics information about the resolver registry.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("ExternalIdentifierRegistryDiagnostics", exact = true)
data class ExternalIdentifierRegistryDiagnostics(
    val resolverCount: Int,
    val resolvers: List<ResolverInfo>,
)

/**
 * Information about a single resolver.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("ResolverInfo", exact = true)
data class ResolverInfo(
    val name: String,
    val priority: Int,
)

/**
 * Error details when resolution fails.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("ExternalIdentifierResolutionError", exact = true)
data class
ExternalIdentifierResolutionError
    @JvmOverloads
    constructor(
        val message: String,
        val resolverCandidates: List<String> = emptyList(),
        val identifierType: String? = null,
    )
