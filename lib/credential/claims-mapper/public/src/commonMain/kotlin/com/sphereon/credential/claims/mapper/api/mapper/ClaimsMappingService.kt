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

package com.sphereon.credential.claims.mapper.api.mapper

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.claims.mapper.api.model.ClaimMappingConfiguration
import com.sphereon.credential.claims.mapper.api.model.CredentialWithId
import com.sphereon.credential.claims.mapper.api.model.MappedClaimsResult
import kotlinx.serialization.json.JsonElement

/**
 * Low-level service for mapping claims from verifiable credentials to a unified claims map.
 *
 * This is a pure mapping service with no knowledge of persistence, storage, or DCQL.
 * It operates only on explicitly passed-in objects and is responsible solely for
 * mappings and data extraction.
 *
 * Use this service when:
 * - You have the configuration object already available
 * - You want to perform simple mapping in a frontend or mobile app
 * - You don't need persistence support
 *
 * For persistence support, use [PersistenceClaimsMappingService].
 * For DCQL-specific lookups, use [com.sphereon.credential.claims.mapper.api.adapter.DcqlClaimsMappingAdapter].
 */
interface ClaimsMappingService {
    /**
     * Map claims from credentials using an inline configuration.
     *
     * This method uses the provided configuration directly to extract and transform
     * claims from the credentials. It:
     * - Uses the appropriate CredentialClaimResolver for each credential format
     * - Extracts claims according to the mapping configuration
     * - Applies transformations
     * - Merges claims from multiple credentials based on priority
     * - Applies default values for missing optional claims
     *
     * @param credentials List of credentials to extract claims from.
     *   Each credential must have a credentialId that matches a credential
     *   mapping in the configuration.
     * @param config The ClaimMappingConfiguration to use
     * @return Mapped claims result containing the claims map and metadata, or error
     */
    suspend fun mapClaimsWithConfig(
        credentials: List<CredentialWithId>,
        config: ClaimMappingConfiguration,
    ): IdkResult<MappedClaimsResult, IdkError>

    /**
     * Extract claims from a single credential without applying a configuration.
     *
     * This is a lower-level method for extracting all claims from a credential
     * using the appropriate resolver. No mapping or transformation is applied.
     *
     * Useful for:
     * - Debugging and inspection
     * - Building dynamic mappings based on available claims
     * - Displaying raw credential contents
     *
     * @param credential The credential to extract claims from
     * @return Map of all claims in the credential, or error
     */
    suspend fun extractAllClaims(credential: CredentialWithId): IdkResult<Map<String, JsonElement>, IdkError>
}
