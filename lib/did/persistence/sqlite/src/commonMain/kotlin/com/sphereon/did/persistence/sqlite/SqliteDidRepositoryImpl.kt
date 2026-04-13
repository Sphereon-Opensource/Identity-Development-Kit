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

package com.sphereon.did.persistence.sqlite

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidRole
import com.sphereon.did.persistence.DidKeyMappingRecord
import com.sphereon.did.persistence.DidRecord
import com.sphereon.did.persistence.DidRecordFilter
import com.sphereon.did.persistence.DidRepository
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SQLite implementation of [DidRepository].
 *
 * Uses SQLDelight for type-safe database queries.
 * Suitable for mobile applications, desktop apps, and testing.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DidRepository>())
class SqliteDidRepositoryImpl(
    private val database: DidDatabaseSqlite,
) : DidRepository {
    private val queries get() = database.didQueries

    override suspend fun save(record: DidRecord): IdkResult<Unit, IdkError> =
        withContext(Dispatchers.Default) {
            try {
                // Check if DID already exists
                val existing = queries.findByDid(record.did).executeAsOneOrNull()
                if (existing != null) {
                    return@withContext Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "DID already exists: ${record.did}",
                        ),
                    )
                }

                queries.insertRecord(
                    id = record.id,
                    did = record.did,
                    method = record.method,
                    alias = record.alias,
                    documentJson = record.documentJson,
                    role = record.role.name,
                    deactivated = record.deactivated,
                    createdAt = record.createdAt,
                    updatedAt = record.updatedAt,
                )
                Ok(Unit)
            } catch (expected: Exception) {
                Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to save DID record: ${expected.message}",
                        exception = expected,
                    ),
                )
            }
        }

    override suspend fun findByDid(did: String): IdkResult<DidRecord?, IdkError> =
        withContext(Dispatchers.Default) {
            try {
                val row = queries.findByDid(did).executeAsOneOrNull()
                Ok(row?.toDidRecord())
            } catch (expected: Exception) {
                Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to find DID record: ${expected.message}",
                        exception = expected,
                    ),
                )
            }
        }

    override suspend fun findByAlias(alias: String): IdkResult<DidRecord?, IdkError> =
        withContext(Dispatchers.Default) {
            try {
                val row = queries.findByAlias(alias).executeAsOneOrNull()
                Ok(row?.toDidRecord())
            } catch (expected: Exception) {
                Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to find DID record by alias: ${expected.message}",
                        exception = expected,
                    ),
                )
            }
        }

    override suspend fun findAll(filter: DidRecordFilter?): IdkResult<List<DidRecord>, IdkError> =
        withContext(Dispatchers.Default) {
            try {
                val rows =
                    if (filter != null) {
                        queries
                            .findAllFiltered(
                                method = filter.method,
                                alias = filter.alias,
                                role = filter.role?.name,
                                includeDeactivated = if (filter.includeDeactivated) 1L else 0L,
                            ).executeAsList()
                    } else {
                        queries.findAll().executeAsList()
                    }
                Ok(rows.map { it.toDidRecord() })
            } catch (expected: Exception) {
                Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to list DID records: ${expected.message}",
                        exception = expected,
                    ),
                )
            }
        }

    override suspend fun update(record: DidRecord): IdkResult<Unit, IdkError> =
        withContext(Dispatchers.Default) {
            try {
                // Check if record exists
                val existing = queries.findById(record.id).executeAsOneOrNull()
                if (existing == null) {
                    return@withContext Err(
                        IdkError.NOT_FOUND_ERROR(
                            message = "Record not found: ${record.id}",
                        ),
                    )
                }

                queries.updateRecord(
                    id = record.id,
                    alias = record.alias,
                    documentJson = record.documentJson,
                    deactivated = record.deactivated,
                    updatedAt = record.updatedAt,
                )
                Ok(Unit)
            } catch (expected: Exception) {
                Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to update DID record: ${expected.message}",
                        exception = expected,
                    ),
                )
            }
        }

    override suspend fun delete(did: String): IdkResult<Unit, IdkError> =
        withContext(Dispatchers.Default) {
            try {
                // Check if DID exists
                val existing = queries.findByDid(did).executeAsOneOrNull()
                if (existing == null) {
                    return@withContext Err(
                        IdkError.NOT_FOUND_ERROR(
                            message = "DID not found: $did",
                        ),
                    )
                }

                // Delete key mappings first (cascade should handle this, but be explicit)
                queries.deleteKeyMappingsByDidRecordId(existing.id)
                queries.deleteRecord(did)
                Ok(Unit)
            } catch (expected: Exception) {
                Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to delete DID record: ${expected.message}",
                        exception = expected,
                    ),
                )
            }
        }

    override suspend fun saveKeyMapping(
        didRecordId: String,
        mapping: DidKeyMappingRecord,
    ): IdkResult<Unit, IdkError> =
        withContext(Dispatchers.Default) {
            try {
                // Check for duplicate
                val existing = queries.findKeyMappingById(mapping.id).executeAsOneOrNull()
                if (existing != null) {
                    return@withContext Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Key mapping already exists: ${mapping.id}",
                        ),
                    )
                }

                queries.insertKeyMapping(
                    id = mapping.id,
                    didRecordId = didRecordId,
                    verificationMethodId = mapping.verificationMethodId,
                    kmsKeyAlias = mapping.kmsKeyAlias,
                    kmsProviderId = mapping.kmsProviderId,
                    purposesJson = mapping.purposesJson,
                )
                Ok(Unit)
            } catch (expected: Exception) {
                Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to save key mapping: ${expected.message}",
                        exception = expected,
                    ),
                )
            }
        }

    override suspend fun getKeyMappings(didRecordId: String): IdkResult<List<DidKeyMappingRecord>, IdkError> =
        withContext(Dispatchers.Default) {
            try {
                val rows = queries.findKeyMappingsByDidRecordId(didRecordId).executeAsList()
                Ok(rows.map { it.toDidKeyMappingRecord() })
            } catch (expected: Exception) {
                Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to get key mappings: ${expected.message}",
                        exception = expected,
                    ),
                )
            }
        }

    override suspend fun deleteKeyMapping(mappingId: String): IdkResult<Unit, IdkError> =
        withContext(Dispatchers.Default) {
            try {
                val existing = queries.findKeyMappingById(mappingId).executeAsOneOrNull()
                if (existing == null) {
                    return@withContext Err(
                        IdkError.NOT_FOUND_ERROR(
                            message = "Key mapping not found: $mappingId",
                        ),
                    )
                }

                queries.deleteKeyMapping(mappingId)
                Ok(Unit)
            } catch (expected: Exception) {
                Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to delete key mapping: ${expected.message}",
                        exception = expected,
                    ),
                )
            }
        }

    override suspend fun deleteKeyMappingsForDid(didRecordId: String): IdkResult<Unit, IdkError> =
        withContext(Dispatchers.Default) {
            try {
                queries.deleteKeyMappingsByDidRecordId(didRecordId)
                Ok(Unit)
            } catch (expected: Exception) {
                Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to delete key mappings: ${expected.message}",
                        exception = expected,
                    ),
                )
            }
        }

    /**
     * Convert SQLDelight-generated row to DidRecord.
     */
    private fun Did_record.toDidRecord(): DidRecord =
        DidRecord(
            id = id,
            did = did,
            method = method,
            alias = alias,
            documentJson = document_json,
            role = DidRole.valueOf(role),
            deactivated = deactivated,
            createdAt = created_at,
            updatedAt = updated_at,
        )

    /**
     * Convert SQLDelight-generated row to DidKeyMappingRecord.
     */
    private fun Did_key_mapping.toDidKeyMappingRecord(): DidKeyMappingRecord =
        DidKeyMappingRecord(
            id = id,
            didRecordId = did_record_id,
            verificationMethodId = verification_method_id,
            kmsKeyAlias = kms_key_alias,
            kmsProviderId = kms_provider_id,
            purposesJson = purposes_json,
        )
}
