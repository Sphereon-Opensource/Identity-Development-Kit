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

package com.sphereon.credential.claims.mapper.api.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Mapping definition for a single claim.
 *
 * Defines how to extract a claim from a source credential and map it
 * to an output claim, with optional transformation.
 *
 * @property sourceClaimPath Path to the claim in the source credential.
 *   Each element represents a property name to navigate into the credential structure.
 *
 *   **JSON-based credentials (SD-JWT, W3C VC):**
 *   - `["given_name"]` - top-level claim
 *   - `["address", "street"]` - nested claim
 *
 *   **mDoc/mDL (ISO 18013-5):** Uses `[namespace, elementIdentifier]` format.
 *   - `["org.iso.18013.5.1", "family_name"]` - standard mDL claim
 *   - `["org.iso.18013.5.1", "portrait"]` - portrait image
 *
 * @property targetClaimPath Path for the output claim in the result.
 *   Each element represents a property name in the output structure.
 *   - `["given_name"]` - top-level output claim
 *   - `["name", "first"]` - nested output (produces `{ "name": { "first": "..." } }`)
 *
 *   For OIDC usage, this would typically be a single-element path with a standard
 *   claim name like `["given_name"]`, `["email"]`, `["sub"]`, etc.
 *
 * @property transformation Optional transformation to apply to the extracted value.
 *   Defaults to null (identity transformation - value passed through as-is).
 *
 * @property priority Priority for merging when multiple credentials provide
 *   the same target claim. Higher values take precedence. Default is 0.
 *
 * @property required Whether this claim is required. If true and the claim
 *   cannot be extracted (and no default value is provided), an error will be returned.
 *   Default is false.
 *
 * @property defaultValue Optional default value to use when the source claim is not
 *   present in the credential. This is applied when the claim cannot be extracted
 *   and allows providing fallback values per mapping.
 */
@JsExportCompat
@Serializable
data class ClaimMapping(
    val sourceClaimPath: List<String>,
    val targetClaimPath: List<String>,
    val transformation: ClaimTransformation? = null,
    val priority: Int = 0,
    val required: Boolean = false,
    val defaultValue: JsonElement? = null,
) {
    /**
     * Returns the source claim path as a dot-separated string for logging/debugging.
     */
    fun sourcePathAsString(): String = pathAsString(sourceClaimPath)

    /**
     * Returns the target claim path as a dot-separated string for logging/debugging.
     */
    fun targetPathAsString(): String = pathAsString(targetClaimPath)

    companion object {
        /**
         * Converts a claim path to a dot-separated string representation.
         *
         * @param path The claim path segments
         * @return The path as a dot-separated string (e.g., "address.street")
         */
        fun pathAsString(path: List<String>): String = path.joinToString(".")

        /**
         * Converts a claim path to a dot-separated string representation with type context.
         *
         * @param path The claim path segments
         * @param type The type of path (SOURCE or TARGET) - useful for logging context
         * @return The path as a dot-separated string (e.g., "address.street")
         */
        @JsExportIgnoreCompat
        fun pathAsString(
            path: List<String>,
            type: ClaimPathType,
        ): String = path.joinToString(".")

        /**
         * Creates a simple 1:1 mapping with no transformation.
         *
         * @param sourceClaim The source claim name (single-segment path)
         * @param targetClaim The target claim name (defaults to same as source)
         */
        fun simple(
            sourceClaim: String,
            targetClaim: String = sourceClaim,
        ): ClaimMapping =
            ClaimMapping(
                sourceClaimPath = listOf(sourceClaim),
                targetClaimPath = listOf(targetClaim),
            )

        /**
         * Creates a mapping from a nested source path to a single target claim.
         *
         * @param path Nested source path segments
         * @param targetClaim The target claim name
         */
        fun nested(
            vararg path: String,
            targetClaim: String,
        ): ClaimMapping =
            ClaimMapping(
                sourceClaimPath = path.toList(),
                targetClaimPath = listOf(targetClaim),
            )

        /**
         * Creates a mapping for an mDoc/mDL claim using ISO 18013-5 namespace format.
         *
         * @param namespace The mDoc namespace (e.g., "org.iso.18013.5.1")
         * @param elementIdentifier The element identifier within the namespace (e.g., "family_name")
         * @param targetClaim The target claim name
         */
        fun mDoc(
            namespace: String,
            elementIdentifier: String,
            targetClaim: String,
        ): ClaimMapping =
            ClaimMapping(
                sourceClaimPath = listOf(namespace, elementIdentifier),
                targetClaimPath = listOf(targetClaim),
            )
    }
}
