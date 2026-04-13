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
 * Storage interface for claim mapping configurations.
 *
 * This is the base storage interface with no query coupling.
 * Implementations of this interface handle persistence of ClaimMappingConfiguration
 * objects. The default implementation is in-memory, but database-backed
 * implementations can be provided for production use.
 *
 * For query-specific lookup capabilities, use [QueryConfigurationStore].
 */
interface ClaimMappingConfigurationStore {

    /**
     * Save a claim mapping configuration.
     *
     * If a configuration with the same ID already exists, it will be replaced.
     *
     * @param config The configuration to save
     * @return The saved configuration, or error
     */
    suspend fun save(config: ClaimMappingConfiguration): IdkResult<ClaimMappingConfiguration, IdkError>

    /**
     * Find a configuration by its ID.
     *
     * @param id The configuration ID
     * @return The configuration if found, null if not found, or error
     */
    suspend fun findById(id: String): IdkResult<ClaimMappingConfiguration?, IdkError>

    /**
     * Find all configurations.
     *
     * @return List of all configurations, or error
     */
    suspend fun findAll(): IdkResult<List<ClaimMappingConfiguration>, IdkError>

    /**
     * Delete a configuration by its ID.
     *
     * @param id The configuration ID
     * @return True if deleted, false if not found, or error
     */
    suspend fun delete(id: String): IdkResult<Boolean, IdkError>

    /**
     * Check if a configuration exists with the given ID.
     *
     * @param id The configuration ID
     * @return True if exists, false otherwise, or error
     */
    suspend fun exists(id: String): IdkResult<Boolean, IdkError>

    /**
     * Delete all configurations.
     *
     * @return Number of configurations deleted, or error
     */
    suspend fun deleteAll(): IdkResult<Int, IdkError>
}
