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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Result of a claim mapping operation.
 *
 * Contains the mapped claims along with metadata about how the mapping
 * was performed.
 *
 * @property claims The mapped claims as a JSON-compatible map.
 *   Keys are the target claim names, values are the extracted/transformed values.
 *
 * @property sourceCredentialIds The credential IDs that contributed claims
 *   to this result.
 *
 * @property appliedDefaults Claim names that were populated from default values
 *   (because the source credential or claim was not available).
 *
 * @property skippedOptionalCredentials Credential IDs for optional credentials
 *   that were not provided.
 */
@Serializable
data class MappedClaimsResult(
    val claims: Map<String, JsonElement>,
    val sourceCredentialIds: Set<String> = emptySet(),
    val appliedDefaults: Set<String> = emptySet(),
    val skippedOptionalCredentials: Set<String> = emptySet(),
) {
    /**
     * Returns just the claims map for simple usage.
     */
    fun toClaimsMap(): Map<String, JsonElement> = claims

    /**
     * Returns true if any default values were applied.
     */
    fun hasAppliedDefaults(): Boolean = appliedDefaults.isNotEmpty()

    /**
     * Returns true if any optional credentials were skipped.
     */
    fun hasSkippedCredentials(): Boolean = skippedOptionalCredentials.isNotEmpty()

    companion object {
        /**
         * Creates an empty result with no claims.
         */
        fun empty(): MappedClaimsResult = MappedClaimsResult(emptyMap())

        /**
         * Creates a result from just a claims map.
         */
        fun fromClaims(claims: Map<String, JsonElement>): MappedClaimsResult = MappedClaimsResult(claims)
    }
}
