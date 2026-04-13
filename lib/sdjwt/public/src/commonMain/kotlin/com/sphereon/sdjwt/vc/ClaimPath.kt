/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.sdjwt.vc

import com.sphereon.sdjwt.Disclosure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/*
 * Claim path utilities for SD-JWT-VC
 *
 * Claim paths are used in type metadata to identify claims within
 * the credential structure. Paths support:
 * - Object property access: ["address", "street"]
 * - Array indices: ["licenses", null] (null = any array element)
 * - Nested structures: ["person", "addresses", null, "city"]
 *
 * Per draft-13, array indices are represented as null in paths
 */

/**
 * Represents a claim path as a sequence of path segments
 *
 * @property segments Path segments (property names or null for array indices)
 */
data class ClaimPath(
    val segments: List<String?>,
) {
    /**
     * Check if path is empty (root level)
     */
    val isEmpty: Boolean get() = segments.isEmpty()

    /**
     * Get first segment
     */
    val first: String? get() = segments.firstOrNull()

    /**
     * Get remaining path after first segment
     */
    fun tail(): ClaimPath = ClaimPath(segments.drop(1))

    /**
     * Append segment to path
     */
    fun append(segment: String?): ClaimPath = ClaimPath(segments + segment)

    /**
     * Convert to string representation for debugging
     */
    override fun toString(): String = segments.joinToString(".") { it ?: "[*]" }

    companion object {
        /**
         * Create path from string segments
         */
        fun of(vararg segments: String?): ClaimPath = ClaimPath(segments.toList())

        /**
         * Empty path (root)
         */
        val EMPTY = ClaimPath(emptyList())
    }
}

/**
 * Result of claim path lookup
 */
sealed interface ClaimPathLookupResult {
    /** Claim found at path */
    data class Found(
        val value: JsonElement,
    ) : ClaimPathLookupResult

    /** Claim not found at path */
    data object NotFound : ClaimPathLookupResult

    /** Path traversal error (e.g., accessing property on non-object) */
    data class Error(
        val message: String,
    ) : ClaimPathLookupResult
}

/**
 * Utilities for working with claim paths
 */
object ClaimPathUtils {
    /**
     * Lookup value at claim path in JSON object
     *
     * Supports:
     * - Object property access: path = ["address", "street"]
     * - Array element access: path = ["licenses", null] matches all array elements
     * - Nested structures
     *
     * @param json JSON object to search
     * @param path Claim path
     * @return Lookup result (found, not found, or error)
     */
    fun lookup(
        json: JsonObject,
        path: ClaimPath,
    ): ClaimPathLookupResult {
        if (path.isEmpty) {
            return ClaimPathLookupResult.Found(json)
        }

        return lookupRecursive(json, path)
    }

    /**
     * Lookup multiple values at claim path (handles arrays)
     *
     * If path contains null segments (array wildcards), returns all matching values
     *
     * @param json JSON object to search
     * @param path Claim path
     * @return List of found values (may be empty)
     */
    fun lookupAll(
        json: JsonObject,
        path: ClaimPath,
    ): List<JsonElement> {
        if (path.isEmpty) {
            return listOf(json)
        }

        return lookupAllRecursive(json, path)
    }

    /**
     * Check if claim exists at path
     *
     * @param json JSON object to search
     * @param path Claim path
     * @return True if at least one value exists at path
     */
    fun exists(
        json: JsonObject,
        path: ClaimPath,
    ): Boolean = lookup(json, path) is ClaimPathLookupResult.Found

    /**
     * Extract all claim paths from JSON object
     *
     * Useful for discovering all claims in a credential
     *
     * @param json JSON object
     * @param includeArrayIndices If true, creates separate paths for each array element
     * @return List of claim paths
     */
    fun extractPaths(
        json: JsonObject,
        includeArrayIndices: Boolean = false,
    ): List<ClaimPath> {
        val paths = mutableListOf<ClaimPath>()
        extractPathsRecursive(json, ClaimPath.EMPTY, paths, includeArrayIndices)
        return paths
    }

    // Private implementation methods

    private fun lookupRecursive(
        element: JsonElement,
        path: ClaimPath,
    ): ClaimPathLookupResult {
        if (path.isEmpty) {
            return ClaimPathLookupResult.Found(element)
        }

        val segment = path.first
        val remaining = path.tail()

        return when (element) {
            is JsonObject -> {
                if (segment == null) {
                    ClaimPathLookupResult.Error("Cannot use array wildcard on object")
                } else {
                    val value = element[segment]
                    if (value == null) {
                        ClaimPathLookupResult.NotFound
                    } else {
                        lookupRecursive(value, remaining)
                    }
                }
            }

            is JsonArray -> {
                if (segment != null) {
                    ClaimPathLookupResult.Error("Expected array wildcard (null), got property name: $segment")
                } else {
                    // Return first array element (or NotFound if empty)
                    val first = element.firstOrNull()
                    if (first == null) {
                        ClaimPathLookupResult.NotFound
                    } else {
                        lookupRecursive(first, remaining)
                    }
                }
            }

            else -> {
                ClaimPathLookupResult.Error("Cannot traverse path through primitive value")
            }
        }
    }

    private fun lookupAllRecursive(
        element: JsonElement,
        path: ClaimPath,
    ): List<JsonElement> {
        if (path.isEmpty) {
            return listOf(element)
        }

        val segment = path.first
        val remaining = path.tail()

        return when (element) {
            is JsonObject -> {
                if (segment == null) {
                    emptyList() // Cannot use array wildcard on object
                } else {
                    val value = element[segment]
                    if (value == null) {
                        emptyList()
                    } else {
                        lookupAllRecursive(value, remaining)
                    }
                }
            }

            is JsonArray -> {
                if (segment != null) {
                    emptyList() // Expected array wildcard
                } else {
                    // Recursively lookup in all array elements
                    element.flatMap { lookupAllRecursive(it, remaining) }
                }
            }

            else -> {
                emptyList()
            }
        }
    }

    private fun extractPathsRecursive(
        element: JsonElement,
        currentPath: ClaimPath,
        paths: MutableList<ClaimPath>,
        includeArrayIndices: Boolean,
    ) {
        when (element) {
            is JsonObject -> {
                if (currentPath.segments.isNotEmpty()) {
                    paths.add(currentPath)
                }
                element.forEach { (key, value) ->
                    extractPathsRecursive(value, currentPath.append(key), paths, includeArrayIndices)
                }
            }

            is JsonArray -> {
                if (currentPath.segments.isNotEmpty()) {
                    paths.add(currentPath)
                }
                if (includeArrayIndices) {
                    element.forEachIndexed { index, value ->
                        extractPathsRecursive(value, currentPath.append(index.toString()), paths, includeArrayIndices)
                    }
                } else {
                    // Use null wildcard for array elements
                    element.forEach { value ->
                        extractPathsRecursive(value, currentPath.append(null), paths, includeArrayIndices)
                    }
                }
            }

            else -> {
                // Primitive value - add path
                if (currentPath.segments.isNotEmpty()) {
                    paths.add(currentPath)
                }
            }
        }
    }
}

/**
 * Check if a claim was selectively disclosed (was in _sd array)
 *
 * A claim is selectively disclosed if it appears in the digestedDisclosures map,
 * meaning it was reconstructed from a disclosure rather than being plain text
 * in the JWT payload.
 *
 * @param claimName Top-level claim name
 * @param disclosures Map of digest to Disclosure
 * @return True if claim was selectively disclosed
 */
private fun isClaimSelectivelyDisclosed(
    claimName: String,
    disclosures: Map<String, Disclosure>,
): Boolean {
    // Check if any disclosure has this claim name
    return disclosures.values.any { it.key == claimName }
}

/**
 * Validate credential against type metadata claim requirements
 *
 * Checks:
 * - Mandatory claims are present
 * - Selective disclosure constraints are satisfied
 *
 * @param credential Full credential payload (after disclosure reconstruction)
 * @param metadata Type metadata with claim definitions
 * @param disclosures Map of digest to Disclosure (for SD constraint validation)
 * @return List of validation errors (empty if valid)
 */
fun validateClaimRequirements(
    credential: JsonObject,
    metadata: SdJwtVcTypeMetadata,
    disclosures: Map<String, Disclosure> = emptyMap(),
): List<ClaimValidationError> {
    val errors = mutableListOf<ClaimValidationError>()

    metadata.claims?.forEach { claimInfo ->
        val path = ClaimPath(claimInfo.path)

        // Check mandatory claims
        if (claimInfo.mandatory) {
            val result = ClaimPathUtils.lookup(credential, path)
            if (result !is ClaimPathLookupResult.Found) {
                errors.add(
                    ClaimValidationError.MandatoryClaimMissing(
                        path = path,
                        claimInfo = claimInfo,
                    ),
                )
            }
        }

        // Check SD constraints (always/never/allowed)
        // Note: This validates the SD constraint for top-level claims only
        // Nested claim SD validation would require more complex tracking
        if (claimInfo.sd != ClaimSdMetadata.ALLOWED && path.segments.size == 1) {
            val claimName = path.segments[0]
            if (claimName != null) {
                val isSelectivelyDisclosed = isClaimSelectivelyDisclosed(claimName, disclosures)

                when (claimInfo.sd) {
                    ClaimSdMetadata.ALWAYS -> {
                        if (!isSelectivelyDisclosed) {
                            errors.add(
                                ClaimValidationError.SdConstraintViolation(
                                    path = path,
                                    claimInfo = claimInfo,
                                    expected = ClaimSdMetadata.ALWAYS,
                                    actual = ClaimSdMetadata.NEVER,
                                ),
                            )
                        }
                    }

                    ClaimSdMetadata.NEVER -> {
                        if (isSelectivelyDisclosed) {
                            errors.add(
                                ClaimValidationError.SdConstraintViolation(
                                    path = path,
                                    claimInfo = claimInfo,
                                    expected = ClaimSdMetadata.NEVER,
                                    actual = ClaimSdMetadata.ALWAYS,
                                ),
                            )
                        }
                    }

                    ClaimSdMetadata.ALLOWED -> {
                        // No constraint - both SD and plain text are acceptable
                    }
                }
            }
        }
    }

    return errors
}

/**
 * Claim validation errors
 */
sealed interface ClaimValidationError {
    /** Mandatory claim is missing */
    data class MandatoryClaimMissing(
        val path: ClaimPath,
        val claimInfo: ClaimInformation,
    ) : ClaimValidationError

    /** Claim has wrong SD constraint */
    data class SdConstraintViolation(
        val path: ClaimPath,
        val claimInfo: ClaimInformation,
        val expected: ClaimSdMetadata,
        val actual: ClaimSdMetadata,
    ) : ClaimValidationError

    /** Claim has invalid value */
    data class InvalidClaimValue(
        val path: ClaimPath,
        val message: String,
    ) : ClaimValidationError
}
