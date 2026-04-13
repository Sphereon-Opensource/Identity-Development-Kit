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
 *
 */

package com.sphereon.credential.claims.mapper.api.dsl

import com.sphereon.credential.claims.mapper.api.model.ClaimMapping
import com.sphereon.credential.claims.mapper.api.model.ClaimMappingConfiguration
import com.sphereon.credential.claims.mapper.api.model.ClaimTransformation
import com.sphereon.credential.claims.mapper.api.model.CredentialMapping
import com.sphereon.credential.claims.mapper.api.model.DateInputFormat
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * DSL marker to prevent scope leakage in nested builders.
 */
@DslMarker
annotation class ClaimMappingDslMarker

/**
 * Creates a [ClaimMappingConfiguration] using the DSL.
 *
 * Example:
 * ```kotlin
 * val config = claimMappingConfig("login-config", "Login Claims") {
 *     description = "Maps PID claims to OIDC claims"
 *     queryId = "login-query"
 *
 *     credential("pid") {
 *         // Simple string-to-string mapping
 *         "given_name" mappedTo "first_name"
 *         "family_name" mappedTo "last_name"
 *
 *         // Nested source path to simple target
 *         path("address", "street") mappedTo "street_address"
 *
 *         // Simple source to nested target (using target() for symmetry)
 *         "given_name" mappedTo target("user", "firstName")
 *
 *         // Nested source to nested target
 *         path("address", "city") mappedTo target("location", "city")
 *
 *         // With transformation
 *         "given_name" mappedTo "full_name" using concatenate {
 *             separator = " "
 *             suffix("family_name")
 *         }
 *
 *         "email" mappedTo "email" using lowercase()
 *     }
 *
 *     credential("mdl", optional = true) {
 *         mDoc("org.iso.18013.5.1", "family_name") mappedTo "family_name"
 *     }
 * }
 * ```
 *
 * @param id Unique identifier for this configuration
 * @param name Human-readable name for this configuration
 * @param block DSL block to configure the mapping
 */
fun claimMappingConfig(
    id: String,
    name: String,
    block: ClaimMappingConfigBuilder.() -> Unit
): ClaimMappingConfiguration {
    return ClaimMappingConfigBuilder(id, name).apply(block).build()
}

/**
 * Builder for [ClaimMappingConfiguration].
 */
@ClaimMappingDslMarker
class ClaimMappingConfigBuilder(
    private val id: String,
    private val name: String
) {
    /** Optional description explaining the purpose of this mapping. */
    var description: String? = null

    /** Optional query ID this configuration is associated with. */
    var queryId: String? = null

    private val credentialMappings = mutableListOf<CredentialMapping>()

    /**
     * Adds a credential mapping to this configuration.
     *
     * @param credentialId The credential ID (e.g., "pid", "mdl")
     * @param optional Whether this credential is optional
     * @param block DSL block to configure claim mappings
     */
    fun credential(
        credentialId: String,
        optional: Boolean = false,
        block: CredentialMappingBuilder.() -> Unit
    ) {
        credentialMappings.add(
            CredentialMappingBuilder(credentialId, optional).apply(block).build()
        )
    }

    internal fun build(): ClaimMappingConfiguration {
        return ClaimMappingConfiguration(
            id = id,
            name = name,
            description = description,
            credentialMappings = credentialMappings.toList(),
            queryId = queryId
        )
    }
}

/**
 * Builder for [CredentialMapping].
 */
@ClaimMappingDslMarker
class CredentialMappingBuilder(
    private val credentialId: String,
    private val optional: Boolean
) {
    private val claimMappings = mutableListOf<ClaimMapping>()

    /**
     * Creates a claim mapping from a source claim path.
     *
     * @param sourcePath Source claim as a single string (e.g., "given_name")
     * @return A [ClaimMappingSpec] to continue building the mapping
     */
    infix fun String.mappedTo(targetPath: String): ClaimMappingSpec {
        return ClaimMappingSpec(
            sourceClaimPath = listOf(this),
            targetClaimPath = listOf(targetPath)
        ).also { claimMappings.add(it.build()) }
    }

    /**
     * Creates a claim mapping from a source claim path to a nested target path.
     *
     * Example:
     * ```kotlin
     * "given_name" mappedTo target("user", "firstName")
     * ```
     *
     * @param targetPath Target path created with [target]
     * @return A [ClaimMappingSpec] to continue building the mapping
     */
    infix fun String.mappedTo(targetPath: TargetPath): ClaimMappingSpec {
        return ClaimMappingSpec(
            sourceClaimPath = listOf(this),
            targetClaimPath = targetPath.segments
        ).also { claimMappings.add(it.build()) }
    }

    /**
     * Creates a claim mapping with the same source and target name.
     *
     * @param claimName The claim name to map (identity mapping)
     */
    fun map(claimName: String): ClaimMappingSpec {
        return ClaimMappingSpec(
            sourceClaimPath = listOf(claimName),
            targetClaimPath = listOf(claimName)
        ).also { claimMappings.add(it.build()) }
    }

    /**
     * Creates a claim mapping from a nested source path.
     *
     * Example:
     * ```kotlin
     * path("address", "street") mappedTo "street_address"
     * ```
     */
    fun path(vararg segments: String): SourcePath {
        return SourcePath(segments.toList())
    }

    /**
     * Creates a claim mapping for an mDoc/mDL claim.
     *
     * Example:
     * ```kotlin
     * mDoc("org.iso.18013.5.1", "family_name") mappedTo "family_name"
     * ```
     */
    fun mDoc(namespace: String, elementIdentifier: String): SourcePath {
        return SourcePath(listOf(namespace, elementIdentifier))
    }

    /**
     * Creates a target path for mapping to a nested structure.
     *
     * Example:
     * ```kotlin
     * path("address", "street") mappedTo target("output", "streetAddress")
     * "given_name" mappedTo target("user", "firstName")
     * ```
     */
    fun target(vararg segments: String): TargetPath {
        return TargetPath(segments.toList())
    }

    /**
     * Creates a detailed claim mapping with full control over all properties.
     *
     * Example:
     * ```kotlin
     * mapping {
     *     from("given_name")
     *     to("name")
     *     priority = 10
     *     required = true
     *     defaultValue = JsonPrimitive("Unknown")
     *     transform { uppercase() }
     * }
     * ```
     */
    fun mapping(block: DetailedClaimMappingBuilder.() -> Unit) {
        claimMappings.add(
            DetailedClaimMappingBuilder().apply(block).build()
        )
    }

    internal fun build(): CredentialMapping {
        return CredentialMapping(
            credentialId = credentialId,
            optional = optional,
            claimMappings = claimMappings.toList()
        )
    }

    /**
     * Represents a source path that can be mapped to a target.
     */
    inner class SourcePath(private val segments: List<String>) {
        infix fun mappedTo(targetPath: String): ClaimMappingSpec {
            return ClaimMappingSpec(
                sourceClaimPath = segments,
                targetClaimPath = listOf(targetPath)
            ).also { claimMappings.add(it.build()) }
        }

        infix fun mappedTo(targetPath: TargetPath): ClaimMappingSpec {
            return ClaimMappingSpec(
                sourceClaimPath = segments,
                targetClaimPath = targetPath.segments
            ).also { claimMappings.add(it.build()) }
        }
    }

    /**
     * Represents a target path for the output claim structure.
     *
     * This provides symmetry with [SourcePath] and ensures proper
     * interoperability with Objective-C and JavaScript, where function
     * overloading on parameter types is not well-supported.
     */
    inner class TargetPath(internal val segments: List<String>)

    /**
     * Intermediate builder for claim mappings that allows adding transformations.
     */
    inner class ClaimMappingSpec(
        private val sourceClaimPath: List<String>,
        private val targetClaimPath: List<String>,
        private var transformation: ClaimTransformation? = null,
        private var priority: Int = 0,
        private var required: Boolean = false,
        private var defaultValue: JsonElement? = null
    ) {
        /**
         * Applies a transformation to this mapping.
         */
        infix fun using(transformation: ClaimTransformation): ClaimMappingSpec {
            this.transformation = transformation
            updateMapping()
            return this
        }

        /**
         * Sets the priority for this mapping.
         */
        infix fun priority(value: Int): ClaimMappingSpec {
            this.priority = value
            updateMapping()
            return this
        }

        /**
         * Marks this mapping as required.
         */
        fun required(): ClaimMappingSpec {
            this.required = true
            updateMapping()
            return this
        }

        /**
         * Sets a default value for this mapping.
         */
        infix fun default(value: JsonElement): ClaimMappingSpec {
            this.defaultValue = value
            updateMapping()
            return this
        }

        /**
         * Sets a default string value for this mapping.
         */
        infix fun default(value: String): ClaimMappingSpec {
            this.defaultValue = JsonPrimitive(value)
            updateMapping()
            return this
        }

        private fun updateMapping() {
            val index = claimMappings.indexOfLast {
                it.sourceClaimPath == sourceClaimPath && it.targetClaimPath == targetClaimPath
            }
            if (index >= 0) {
                claimMappings[index] = build()
            }
        }

        internal fun build(): ClaimMapping {
            return ClaimMapping(
                sourceClaimPath = sourceClaimPath,
                targetClaimPath = targetClaimPath,
                transformation = transformation,
                priority = priority,
                required = required,
                defaultValue = defaultValue
            )
        }
    }
}

/**
 * Builder for detailed claim mappings with full control.
 */
@ClaimMappingDslMarker
class DetailedClaimMappingBuilder {
    private var sourceClaimPath: List<String> = emptyList()
    private var targetClaimPath: List<String> = emptyList()
    private var transformation: ClaimTransformation? = null

    /** Priority for merging when multiple credentials provide the same claim. */
    var priority: Int = 0

    /** Whether this claim is required. */
    var required: Boolean = false

    /** Default value when the source claim is not present. */
    var defaultValue: JsonElement? = null

    /**
     * Sets the source claim path.
     */
    fun from(vararg path: String) {
        sourceClaimPath = path.toList()
    }

    /**
     * Sets the target claim path.
     */
    fun to(vararg path: String) {
        targetClaimPath = path.toList()
    }

    /**
     * Sets the transformation using a builder.
     */
    fun transform(block: TransformationBuilder.() -> ClaimTransformation) {
        transformation = TransformationBuilder().block()
    }

    internal fun build(): ClaimMapping {
        require(sourceClaimPath.isNotEmpty()) { "Source claim path must be specified using from()" }
        require(targetClaimPath.isNotEmpty()) { "Target claim path must be specified using to()" }

        return ClaimMapping(
            sourceClaimPath = sourceClaimPath,
            targetClaimPath = targetClaimPath,
            transformation = transformation,
            priority = priority,
            required = required,
            defaultValue = defaultValue
        )
    }
}

// =============================================================================
// Transformation DSL Functions
// =============================================================================

/**
 * Builder for creating transformations with a fluent API.
 */
@ClaimMappingDslMarker
class TransformationBuilder {
    /** Creates an uppercase transformation. */
    fun uppercase(): ClaimTransformation = ClaimTransformation.Uppercase

    /** Creates a lowercase transformation. */
    fun lowercase(): ClaimTransformation = ClaimTransformation.Lowercase

    /** Creates a canonical (pass-through) transformation. */
    fun canonical(): ClaimTransformation = ClaimTransformation.Canonical

    /** Creates a toString transformation. */
    fun asString(format: String? = null): ClaimTransformation = ClaimTransformation.ToString(format)

    /** Creates a substring transformation. */
    fun substring(start: Int, end: Int? = null): ClaimTransformation =
        ClaimTransformation.Substring(start, end)

    /** Creates a regex replace transformation. */
    fun regexReplace(pattern: String, replacement: String): ClaimTransformation =
        ClaimTransformation.RegexReplace(pattern, replacement)

    /** Creates a toIsoDate transformation. */
    fun toIsoDate(inputFormat: DateInputFormat = DateInputFormat.AUTO): ClaimTransformation =
        ClaimTransformation.ToIsoDate(inputFormat)

    /** Creates a concatenate transformation. */
    fun concatenate(separator: String = " ", block: ConcatenateBuilder.() -> Unit = {}): ClaimTransformation {
        return ConcatenateBuilder(separator).apply(block).build()
    }
}

/**
 * Builder for concatenate transformations.
 */
@ClaimMappingDslMarker
class ConcatenateBuilder(private var separator: String) {
    private val prefixPaths = mutableListOf<List<String>>()
    private val suffixPaths = mutableListOf<List<String>>()

    /**
     * Adds a prefix claim path (values prepended before the primary value).
     */
    fun prefix(vararg path: String) {
        prefixPaths.add(path.toList())
    }

    /**
     * Adds a suffix claim path (values appended after the primary value).
     */
    fun suffix(vararg path: String) {
        suffixPaths.add(path.toList())
    }

    internal fun build(): ClaimTransformation.Concatenate {
        return ClaimTransformation.Concatenate(
            separator = separator,
            prefixPaths = prefixPaths.toList(),
            suffixPaths = suffixPaths.toList()
        )
    }
}

// =============================================================================
// Top-level Transformation Factory Functions
// =============================================================================

/** Creates an uppercase transformation. */
fun uppercase(): ClaimTransformation = ClaimTransformation.Uppercase

/** Creates a lowercase transformation. */
fun lowercase(): ClaimTransformation = ClaimTransformation.Lowercase

/** Creates a canonical (pass-through) transformation. */
fun canonical(): ClaimTransformation = ClaimTransformation.Canonical

/** Creates a toString transformation. */
fun asString(format: String? = null): ClaimTransformation = ClaimTransformation.ToString(format)

/** Creates a substring transformation. */
fun substring(start: Int, end: Int? = null): ClaimTransformation =
    ClaimTransformation.Substring(start, end)

/** Creates a regex replace transformation. */
fun regexReplace(pattern: String, replacement: String): ClaimTransformation =
    ClaimTransformation.RegexReplace(pattern, replacement)

/** Creates a toIsoDate transformation. */
fun toIsoDate(inputFormat: DateInputFormat = DateInputFormat.AUTO): ClaimTransformation =
    ClaimTransformation.ToIsoDate(inputFormat)

/**
 * Creates a concatenate transformation.
 *
 * Example:
 * ```kotlin
 * "given_name" mappedTo "full_name" using concatenate(" ") {
 *     suffix("family_name")
 * }
 * ```
 */
fun concatenate(separator: String = " ", block: ConcatenateBuilder.() -> Unit = {}): ClaimTransformation {
    return ConcatenateBuilder(separator).apply(block).build()
}
