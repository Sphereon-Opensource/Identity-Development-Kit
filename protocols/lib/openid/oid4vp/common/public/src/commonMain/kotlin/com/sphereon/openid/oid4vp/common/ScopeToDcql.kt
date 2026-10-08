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

package com.sphereon.openid.oid4vp.common

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialSetQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import io.konform.validation.Validation
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/*
 * Scope-to-DCQL Resolution for OpenID4VP
 *
 * OpenID4VP 1.0 Final Section 5.5:
 * "Wallets MAY support requesting Presentations using OAuth 2.0 scope values.
 * Such a scope parameter value MUST be an alias for a well-defined DCQL query.
 * Since multiple scope values can be used at the same time, the identifiers for
 * Credentials and claims within the DCQL queries associated with scope values
 * MUST be unique."
 *
 * This file provides the infrastructure for mapping scope values to DCQL queries.
 * The specific scope-to-DCQL mappings are out of scope of the OpenID4VP specification
 * and should be defined by:
 * - Normative text in separate specifications
 * - Machine-readable definitions in Wallet's server metadata
 * - Profile-specific configurations
 *
 * It is RECOMMENDED to use collision-resistant scope values.
 */

// =============================================================================
// Scope Definition Model
// =============================================================================

/**
 * A scope definition that maps a scope value to a DCQL query.
 *
 * Per OpenID4VP 1.0 Section 5.5, a scope value is an alias for a well-defined DCQL query.
 *
 * Example:
 * ```kotlin
 * val idCardScope = ScopeDefinition(
 *     scopeValue = "com.example.id_card_presentation",
 *     description = "Requests ID card credential with name and photo",
 *     dcqlQuery = DcqlQuery(
 *         credentials = listOf(
 *             DcqlCredentialQuery(
 *                 id = "id_card",
 *                 format = "dc+sd-jwt",
 *                 meta = JsonObject(emptyMap()),
 *                 claims = listOf(
 *                     DcqlClaimQuery(path = claimsPathPointer("given_name")),
 *                     DcqlClaimQuery(path = claimsPathPointer("family_name")),
 *                     DcqlClaimQuery(path = claimsPathPointer("portrait"))
 *                 )
 *             )
 *         )
 *     )
 * )
 * ```
 *
 * @property scopeValue The scope value string (e.g., "com.example.identity_credential")
 * @property description Optional human-readable description of what this scope requests
 * @property dcqlQuery The DCQL query that this scope resolves to
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopeDefinition", exact = true)
@Serializable
@JsExportCompat
data class ScopeDefinition(
    val scopeValue: String,
    val description: String? = null,
    val dcqlQuery: DcqlQuery,
) {
    init {
        require(scopeValue.isNotBlank()) { "Scope value cannot be blank" }
    }
}

/**
 * A registry of scope definitions.
 *
 * Per OpenID4VP 1.0 Section 5.5:
 * "The specific scope values, and the mapping between a certain scope value and the
 * respective DCQL query, are out of scope of this specification."
 *
 * This registry provides a way to configure scope-to-DCQL mappings.
 *
 * @property definitions Map of scope value to its definition
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopeRegistry", exact = true)
@JsExportCompat
class ScopeRegistry(
    definitions: List<ScopeDefinition> = emptyList(),
) {
    private val registry: MutableMap<String, ScopeDefinition> =
        definitions
            .associateBy { it.scopeValue }
            .toMutableMap()

    /**
     * Get all registered scope values.
     */
    val scopeValues: Set<String> get() = registry.keys.toSet()

    /**
     * Get all registered definitions.
     */
    val definitions: Collection<ScopeDefinition> get() = registry.values

    /**
     * Number of registered scopes.
     */
    val size: Int get() = registry.size

    /**
     * Register a scope definition.
     *
     * @param definition The scope definition to register
     * @return This registry for chaining
     */
    fun register(definition: ScopeDefinition): ScopeRegistry =
        apply {
            registry[definition.scopeValue] = definition
        }

    /**
     * Register multiple scope definitions.
     *
     * @param definitions The scope definitions to register
     * @return This registry for chaining
     */
    fun registerAll(definitions: List<ScopeDefinition>): ScopeRegistry =
        apply {
            definitions.forEach { register(it) }
        }

    /**
     * Unregister a scope definition.
     *
     * @param scopeValue The scope value to unregister
     * @return This registry for chaining
     */
    fun unregister(scopeValue: String): ScopeRegistry =
        apply {
            registry.remove(scopeValue)
        }

    /**
     * Get a scope definition by its scope value.
     *
     * @param scopeValue The scope value to look up
     * @return The scope definition, or null if not found
     */
    fun get(scopeValue: String): ScopeDefinition? = registry[scopeValue]

    /**
     * Check if a scope value is registered.
     *
     * @param scopeValue The scope value to check
     * @return true if the scope is registered
     */
    fun contains(scopeValue: String): Boolean = scopeValue in registry

    /**
     * Check if the registry is empty.
     */
    fun isEmpty(): Boolean = registry.isEmpty()

    companion object {
        /**
         * Create an empty scope registry.
         */
        @JvmStatic
        fun empty(): ScopeRegistry = ScopeRegistry()
    }
}

// =============================================================================
// Scope Resolution Result
// =============================================================================

/**
 * Result of resolving scope values to DCQL queries.
 *
 * @property dcqlQuery The merged DCQL query from all resolved scopes
 * @property resolvedScopes The scope values that were successfully resolved
 * @property unresolvedScopes The scope values that could not be resolved
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopeResolutionResult", exact = true)
@Serializable
@JsExportCompat
data class ScopeResolutionResult(
    val dcqlQuery: DcqlQuery?,
    val resolvedScopes: List<String>,
    val unresolvedScopes: List<String>,
) {
    /**
     * Whether all scopes were successfully resolved.
     */
    val fullyResolved: Boolean get() = unresolvedScopes.isEmpty() && resolvedScopes.isNotEmpty()

    /**
     * Whether any scopes were resolved.
     */
    val partiallyResolved: Boolean get() = resolvedScopes.isNotEmpty()
}

// =============================================================================
// Scope Resolution Error
// =============================================================================

/**
 * Error that can occur during scope resolution.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopeResolutionError", exact = true)
sealed interface ScopeResolutionError {
    val message: String

    /**
     * Scope value was not found in the registry.
     */
    data class ScopeNotFound(
        val scopeValue: String,
        override val message: String = "Scope '$scopeValue' not found in registry",
    ) : ScopeResolutionError

    /**
     * Duplicate credential query IDs found across scopes.
     *
     * Per OpenID4VP 1.0 Section 5.5:
     * "Since multiple scope values can be used at the same time, the identifiers for
     * Credentials within the DCQL queries associated with scope values MUST be unique."
     */
    data class DuplicateCredentialId(
        val credentialId: String,
        val scopes: List<String>,
        override val message: String = "Duplicate credential ID '$credentialId' found in scopes: ${scopes.joinToString()}",
    ) : ScopeResolutionError

    /**
     * Both dcql_query and scope-referencing-DCQL present in request.
     *
     * Per OpenID4VP 1.0 Section 5.1:
     * "Either a dcql_query or a scope parameter representing a DCQL Query MUST be
     * present in the Authorization Request, but not both."
     */
    data class BothDcqlAndScopePresent(
        override val message: String = "Both dcql_query and scope referencing DCQL are present",
    ) : ScopeResolutionError
}

// =============================================================================
// Scope Resolver
// =============================================================================

/**
 * Resolves scope values to DCQL queries.
 *
 * Per OpenID4VP 1.0 Section 5.5:
 * "Wallets MAY support requesting Presentations using OAuth 2.0 scope values."
 *
 * Usage:
 * ```kotlin
 * val resolver = ScopeResolver(registry)
 * val result = resolver.resolve("com.example.identity com.example.employment")
 *
 * if (result.fullyResolved) {
 *     val dcqlQuery = result.dcqlQuery!!
 *     // Use merged DCQL query
 * }
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopeResolver", exact = true)
@JsExportCompat
class ScopeResolver(
    private val registry: ScopeRegistry,
) {
    /**
     * Resolve scope values to a merged DCQL query.
     *
     * Per OpenID4VP 1.0 Section 5.5:
     * - Each scope value is an alias for a DCQL query
     * - Multiple scope values can be combined (space-separated)
     * - Credential and claim IDs must be unique across all scope DCQL queries
     *
     * @param scopeString Space-separated scope values (e.g., "openid com.example.scope1 com.example.scope2")
     * @return Resolution result with merged DCQL query and any errors
     */
    fun resolve(scopeString: String?): ScopeResolutionResult {
        if (scopeString.isNullOrBlank()) {
            return ScopeResolutionResult(
                dcqlQuery = null,
                resolvedScopes = emptyList(),
                unresolvedScopes = emptyList(),
            )
        }

        val scopeValues = scopeString.split(" ").filter { it.isNotBlank() }
        return resolveScopes(scopeValues)
    }

    /**
     * Resolve a list of scope values to a merged DCQL query.
     *
     * @param scopeValues List of scope values
     * @return Resolution result with merged DCQL query and any errors
     */
    fun resolveScopes(scopeValues: List<String>): ScopeResolutionResult {
        if (scopeValues.isEmpty()) {
            return ScopeResolutionResult(
                dcqlQuery = null,
                resolvedScopes = emptyList(),
                unresolvedScopes = emptyList(),
            )
        }

        val resolvedScopes = mutableListOf<String>()
        val unresolvedScopes = mutableListOf<String>()
        val dcqlQueries = mutableListOf<DcqlQuery>()

        for (scopeValue in scopeValues) {
            val definition = registry.get(scopeValue)
            if (definition != null) {
                resolvedScopes.add(scopeValue)
                dcqlQueries.add(definition.dcqlQuery)
            } else {
                // Scope not in registry - may be a standard OAuth scope like "openid"
                // that doesn't map to a DCQL query
                unresolvedScopes.add(scopeValue)
            }
        }

        // Merge all resolved DCQL queries
        val mergedQuery =
            if (dcqlQueries.isNotEmpty()) {
                mergeDcqlQueries(dcqlQueries)
            } else {
                null
            }

        return ScopeResolutionResult(
            dcqlQuery = mergedQuery,
            resolvedScopes = resolvedScopes,
            unresolvedScopes = unresolvedScopes,
        )
    }

    /**
     * Check if a scope value can be resolved to a DCQL query.
     *
     * @param scopeValue The scope value to check
     * @return true if the scope is registered and can be resolved
     */
    fun canResolve(scopeValue: String): Boolean = registry.contains(scopeValue)

    /**
     * Validate that multiple scopes can be combined (no duplicate credential IDs).
     *
     * @param scopeValues The scope values to validate
     * @return List of validation errors, empty if valid
     */
    fun validateScopeCombination(scopeValues: List<String>): List<ScopeResolutionError> {
        val errors = mutableListOf<ScopeResolutionError>()
        val credentialIdToScopes = mutableMapOf<String, MutableList<String>>()

        for (scopeValue in scopeValues) {
            val definition = registry.get(scopeValue) ?: continue

            // Collect credential IDs from this scope's DCQL query
            definition.dcqlQuery.credentials.forEach { credential ->
                credentialIdToScopes.getOrPut(credential.id) { mutableListOf() }.add(scopeValue)
            }
        }

        // Check for duplicates
        for ((credentialId, scopes) in credentialIdToScopes) {
            if (scopes.size > 1) {
                errors.add(ScopeResolutionError.DuplicateCredentialId(credentialId, scopes))
            }
        }

        return errors
    }

    companion object {
        /**
         * Merge multiple DCQL queries into one.
         *
         * Per OpenID4VP 1.0 Section 5.5:
         * "Since multiple scope values can be used at the same time, the identifiers
         * for Credentials and claims within the DCQL queries associated with scope
         * values MUST be unique."
         *
         * This function assumes the caller has validated uniqueness.
         */
        @JvmStatic
        fun mergeDcqlQueries(queries: List<DcqlQuery>): DcqlQuery {
            require(queries.isNotEmpty()) { "Cannot merge empty list of DCQL queries" }
            if (queries.size == 1) {
                return queries.first()
            }

            // Merge credentials
            val allCredentials = mutableListOf<DcqlCredentialQuery>()
            queries.forEach { query ->
                allCredentials.addAll(query.credentials)
            }

            // Merge credential_sets
            val allCredentialSets = mutableListOf<DcqlCredentialSetQuery>()
            queries.forEach { query ->
                query.credential_sets?.let { allCredentialSets.addAll(it) }
            }

            return DcqlQuery(
                credentials = allCredentials,
                credential_sets = allCredentialSets.takeIf { it.isNotEmpty() },
            )
        }
    }
}

// =============================================================================
// Validation
// =============================================================================

/**
 * Konform validator for ScopeDefinition.
 */
val validateScopeDefinition =
    Validation<ScopeDefinition> {
        ScopeDefinition::scopeValue {
            constrain("Scope value must not be blank") { it.isNotBlank() }
            constrain("Scope value should be collision-resistant (recommended to use reverse domain notation)") {
                // Just a warning hint - not enforced strictly
                true
            }
        }
    }

// =============================================================================
// Builder DSL
// =============================================================================

/**
 * Builder for creating ScopeDefinition instances.
 */
@JsExportCompat
class ScopeDefinitionBuilder {
    private var scopeValue: String = ""
    private var description: String? = null
    private var dcqlQuery: DcqlQuery? = null

    /**
     * Set the scope value.
     */
    fun scopeValue(value: String) = apply { this.scopeValue = value }

    /**
     * Set the description.
     */
    fun description(desc: String) = apply { this.description = desc }

    /**
     * Set the DCQL query.
     */
    fun dcqlQuery(query: DcqlQuery) = apply { this.dcqlQuery = query }

    /**
     * Build the ScopeDefinition.
     */
    fun build(): ScopeDefinition {
        require(scopeValue.isNotBlank()) { "Scope value must be set" }
        requireNotNull(dcqlQuery) { "DCQL query must be set" }

        return ScopeDefinition(
            scopeValue = scopeValue,
            description = description,
            dcqlQuery = dcqlQuery!!,
        )
    }
}

/**
 * Build a ScopeDefinition using a type-safe builder DSL.
 *
 * Example:
 * ```kotlin
 * val scope = buildScopeDefinition {
 *     scopeValue("com.example.identity_credential")
 *     description("Requests identity credential with basic claims")
 *     dcqlQuery(DcqlQuery(
 *         credentials = listOf(
 *             DcqlCredentialQuery(
 *                 id = "identity",
 *                 format = "dc+sd-jwt",
 *                 meta = JsonObject(emptyMap()),
 *                 claims = listOf(DcqlClaimQuery(path = claimsPathPointer("given_name")))
 *             )
 *         )
 *     ))
 * }
 * ```
 */
inline fun buildScopeDefinition(block: ScopeDefinitionBuilder.() -> Unit): ScopeDefinition = ScopeDefinitionBuilder().apply(block).build()

/**
 * Builder for creating ScopeRegistry instances.
 */
@JsExportCompat
class ScopeRegistryBuilder {
    private val definitions = mutableListOf<ScopeDefinition>()

    /**
     * Add a scope definition.
     */
    fun scope(definition: ScopeDefinition) =
        apply {
            definitions.add(definition)
        }

    /**
     * Add a scope definition using the builder DSL.
     */
    @JsExportIgnoreCompat
    fun scope(block: ScopeDefinitionBuilder.() -> Unit) =
        apply {
            definitions.add(buildScopeDefinition(block))
        }

    /**
     * Build the ScopeRegistry.
     */
    fun build(): ScopeRegistry = ScopeRegistry(definitions)
}

/**
 * Build a ScopeRegistry using a type-safe builder DSL.
 *
 * Example:
 * ```kotlin
 * val registry = buildScopeRegistry {
 *     scope {
 *         scopeValue("com.example.identity")
 *         dcqlQuery(identityQuery)
 *     }
 *     scope {
 *         scopeValue("com.example.diploma")
 *         dcqlQuery(diplomaQuery)
 *     }
 * }
 * ```
 */
inline fun buildScopeRegistry(block: ScopeRegistryBuilder.() -> Unit): ScopeRegistry = ScopeRegistryBuilder().apply(block).build()
