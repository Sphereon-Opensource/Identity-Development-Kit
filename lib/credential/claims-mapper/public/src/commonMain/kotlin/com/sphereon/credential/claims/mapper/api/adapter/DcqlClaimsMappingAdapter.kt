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

package com.sphereon.credential.claims.mapper.api.adapter

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.claims.mapper.api.model.CredentialWithId
import com.sphereon.credential.claims.mapper.api.model.MappedClaimsResult

/**
 * Adapter for DCQL-specific claim mapping operations.
 *
 * This adapter extends the claim mapping functionality with DCQL (Digital Credential
 * Query Language) support. It allows looking up configurations by DCQL query ID,
 * which is convenient when processing DCQL responses.
 *
 * DCQL is treated as a specialization/adapter on top of the core mapping service,
 * not as a core concept. This keeps the base mapping service domain-pure.
 *
 * Use this adapter when:
 * - You're processing DCQL responses and have the query ID available
 * - Your configurations are associated with DCQL queries
 *
 * For pure mapping without DCQL, use [com.sphereon.credential.claims.mapper.api.mapper.ClaimsMappingService].
 * For persistence without DCQL, use [com.sphereon.credential.claims.mapper.api.mapper.PersistenceClaimsMappingService].
 */
interface DcqlClaimsMappingAdapter {

    /**
     * Map claims looking up configuration by DCQL query ID.
     *
     * This is convenient when processing DCQL responses, as the query ID
     * is typically known from the request/response context.
     *
     * @param credentials List of credentials to extract claims from
     * @param dcqlQueryId The DCQL query ID to find the configuration for
     * @return Mapped claims result, or error if config not found or mapping fails
     */
    suspend fun mapClaimsByQueryId(
        credentials: List<CredentialWithId>,
        dcqlQueryId: String
    ): IdkResult<MappedClaimsResult, IdkError>
}
