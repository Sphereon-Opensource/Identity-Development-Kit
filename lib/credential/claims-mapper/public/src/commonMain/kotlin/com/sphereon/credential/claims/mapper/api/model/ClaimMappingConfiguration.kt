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
 * Configuration for mapping claims from multiple verifiable credentials
 * to a unified claims map.
 *
 * Each configuration defines how the claims from credentials should be
 * mapped to output claims (e.g., OIDC claims for authentication).
 *
 * Configurations can optionally be associated with a DCQL query ID for
 * lookup when processing DCQL responses. This is not required for
 * basic mapping operations.
 *
 * @property id Unique identifier for this configuration
 *
 * @property name Human-readable name for this configuration
 *
 * @property description Optional description explaining the purpose of this mapping
 *
 * @property credentialMappings List of credential mappings. Each entry defines
 *   how to map claims from one credential type. The order may affect processing
 *   when resolving priority conflicts.
 *
 * @property queryId Optional DCQL query ID this configuration is associated with.
 *   This allows looking up the configuration when processing a DCQL response.
 *   Only needed when using [DcqlClaimsMappingAdapter].
 */
@Serializable
data class ClaimMappingConfiguration(
    val id: String,
    val name: String,
    val description: String? = null,
    val credentialMappings: List<CredentialMapping>,
    val queryId: String? = null,
) {
    /**
     * Returns all required credential IDs (non-optional credentials).
     */
    fun requiredCredentialIds(): Set<String> =
        credentialMappings
            .filter { !it.optional }
            .map { it.credentialId }
            .toSet()

    /**
     * Returns all credential IDs referenced in this configuration.
     */
    fun allCredentialIds(): Set<String> = credentialMappings.map { it.credentialId }.toSet()

    /**
     * Returns all target claim paths across all credential mappings as dot-separated strings.
     */
    fun allTargetClaimPaths(): Set<String> = credentialMappings.flatMap { it.targetClaimPaths() }.toSet()

    /**
     * Finds the credential mapping for a given credential ID.
     */
    fun findCredentialMapping(credentialId: String): CredentialMapping? = credentialMappings.find { it.credentialId == credentialId }

    companion object {
        /**
         * Creates a simple configuration for a single credential.
         *
         * @param id Configuration ID
         * @param name Configuration name
         * @param credentialId Credential ID
         * @param claims Claim mappings as source-to-target pairs
         * @param dcqlQueryId Optional DCQL query ID (only needed for DCQL lookups)
         */
        fun single(
            id: String,
            name: String,
            credentialId: String,
            vararg claims: Pair<String, String?>,
            dcqlQueryId: String? = null,
        ): ClaimMappingConfiguration =
            ClaimMappingConfiguration(
                id = id,
                name = name,
                credentialMappings =
                    listOf(
                        CredentialMapping.simple(credentialId, *claims),
                    ),
                queryId = dcqlQueryId,
            )
    }
}
