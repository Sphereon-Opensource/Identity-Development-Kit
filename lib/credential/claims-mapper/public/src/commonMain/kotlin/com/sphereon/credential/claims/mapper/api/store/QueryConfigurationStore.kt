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

package com.sphereon.credential.claims.mapper.api.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.claims.mapper.api.model.ClaimMappingConfiguration

/**
 * Extension of [ClaimMappingConfigurationStore] with query lookup capabilities.
 *
 * This interface adds the ability to look up configurations by query ID,
 * which is useful when processing query responses.
 *
 * Not all configurations need a query ID - only those that are associated
 * with queries. Use the base [ClaimMappingConfigurationStore] when query
 * support is not needed.
 */
interface QueryConfigurationStore : ClaimMappingConfigurationStore {

    /**
     * Find a configuration by its query ID.
     *
     * @param queryId The query ID
     * @return The configuration if found, null if not found, or error
     */
    suspend fun findByQueryId(queryId: String): IdkResult<ClaimMappingConfiguration?, IdkError>
}
