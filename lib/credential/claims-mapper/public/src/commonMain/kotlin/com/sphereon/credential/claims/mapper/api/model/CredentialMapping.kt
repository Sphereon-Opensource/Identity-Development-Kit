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

/**
 * Mapping definition for a single credential within a multi-credential configuration.
 *
 * Each CredentialMapping corresponds to one credential type in a DCQL query
 * and defines how claims from that credential should be mapped to output claims.
 *
 * @property credentialId The DCQL credential ID that this mapping applies to.
 *   This must match the credential ID in the DCQL query (e.g., "pid", "mdl").
 *
 * @property optional Whether this credential is optional. If true and the
 *   credential is not provided, the mapping will be skipped. If false and
 *   the credential is missing, an error will be returned. Default is false.
 *
 * @property claimMappings List of claim mappings for this credential.
 *   Each mapping defines how to extract and transform one claim.
 */
@Serializable
data class CredentialMapping(
    val credentialId: String,
    val optional: Boolean = false,
    val claimMappings: List<ClaimMapping>,
) {
    /**
     * Returns the required claim mappings.
     */
    fun requiredMappings(): List<ClaimMapping> = claimMappings.filter { it.required }

    /**
     * Returns all target claim paths in this mapping as dot-separated strings.
     */
    fun targetClaimPaths(): Set<String> = claimMappings.map { it.targetPathAsString() }.toSet()

    companion object {
        /**
         * Creates a credential mapping for simple 1:1 claim mappings.
         *
         * @param credentialId The DCQL credential ID
         * @param claims Pairs of (sourceClaim, targetClaim) - if target is null, source is used
         * @param optional Whether the credential is optional
         */
        fun simple(
            credentialId: String,
            vararg claims: Pair<String, String?>,
            optional: Boolean = false,
        ): CredentialMapping =
            CredentialMapping(
                credentialId = credentialId,
                optional = optional,
                claimMappings =
                    claims.map { (source, target) ->
                        ClaimMapping.simple(source, target ?: source)
                    },
            )
    }
}
