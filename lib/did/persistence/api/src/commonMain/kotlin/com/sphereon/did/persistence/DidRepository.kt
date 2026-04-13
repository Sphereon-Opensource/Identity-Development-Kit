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

package com.sphereon.did.persistence

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Repository interface for DID persistence operations.
 *
 * Implementations handle storage and retrieval of DID records.
 * This interface is platform-agnostic - implementations may use
 * SQLite (IDK), PostgreSQL/MySQL (EDK), or in-memory storage.
 *
 * No `I` prefix - implementations are suffixed with `Impl`.
 */
interface DidRepository {
    /**
     * Saves a new DID record.
     *
     * @param record The record to save
     * @return Success or error (e.g., if DID already exists)
     */
    suspend fun save(record: DidRecord): IdkResult<Unit, IdkError>

    /**
     * Finds a DID record by its DID string.
     *
     * @param did The DID to find
     * @return The record if found, null otherwise
     */
    suspend fun findByDid(did: String): IdkResult<DidRecord?, IdkError>

    /**
     * Finds a DID record by its alias.
     *
     * @param alias The alias to search for
     * @return The record if found, null otherwise
     */
    suspend fun findByAlias(alias: String): IdkResult<DidRecord?, IdkError>

    /**
     * Finds all DID records matching the optional filter.
     *
     * @param filter Optional filter criteria
     * @return List of matching records
     */
    suspend fun findAll(filter: DidRecordFilter? = null): IdkResult<List<DidRecord>, IdkError>

    /**
     * Updates an existing DID record.
     *
     * @param record The record to update (matched by id)
     * @return Success or error (e.g., if record not found)
     */
    suspend fun update(record: DidRecord): IdkResult<Unit, IdkError>

    /**
     * Deletes a DID record.
     *
     * @param did The DID to delete
     * @return Success or error (e.g., if not found)
     */
    suspend fun delete(did: String): IdkResult<Unit, IdkError>

    /**
     * Saves a key mapping for a DID.
     *
     * @param didRecordId The ID of the DID record
     * @param mapping The key mapping to save
     * @return Success or error
     */
    suspend fun saveKeyMapping(
        didRecordId: String,
        mapping: DidKeyMappingRecord,
    ): IdkResult<Unit, IdkError>

    /**
     * Gets all key mappings for a DID record.
     *
     * @param didRecordId The ID of the DID record
     * @return List of key mappings
     */
    suspend fun getKeyMappings(didRecordId: String): IdkResult<List<DidKeyMappingRecord>, IdkError>

    /**
     * Deletes a key mapping.
     *
     * @param mappingId The ID of the mapping to delete
     * @return Success or error
     */
    suspend fun deleteKeyMapping(mappingId: String): IdkResult<Unit, IdkError>

    /**
     * Deletes all key mappings for a DID record.
     *
     * @param didRecordId The ID of the DID record
     * @return Success or error
     */
    suspend fun deleteKeyMappingsForDid(didRecordId: String): IdkResult<Unit, IdkError>
}
