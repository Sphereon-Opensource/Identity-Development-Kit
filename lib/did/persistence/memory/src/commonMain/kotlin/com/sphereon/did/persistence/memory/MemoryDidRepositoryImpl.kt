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

package com.sphereon.did.persistence.memory

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.did.persistence.DidKeyMappingRecord
import com.sphereon.did.persistence.DidRecord
import com.sphereon.did.persistence.DidRecordFilter
import com.sphereon.did.persistence.DidRepository
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory implementation of [DidRepository].
 *
 * Suitable for testing and development. Data is lost when the
 * application terminates.
 *
 * Thread-safe through Mutex access to internal maps.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DidRepository>())
class MemoryDidRepositoryImpl : DidRepository {
    private val mutex = Mutex()
    private val records = mutableMapOf<String, DidRecord>()
    private val recordsByDid = mutableMapOf<String, String>() // did -> id
    private val recordsByAlias = mutableMapOf<String, String>() // alias -> id
    private val keyMappings = mutableMapOf<String, MutableList<DidKeyMappingRecord>>() // didRecordId -> mappings

    override suspend fun save(record: DidRecord): IdkResult<Unit, IdkError> {
        return mutex.withLock {
            if (recordsByDid.containsKey(record.did)) {
                return@withLock Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "DID already exists: ${record.did}",
                    ),
                )
            }

            records[record.id] = record
            recordsByDid[record.did] = record.id
            record.alias?.let { recordsByAlias[it] = record.id }
            Ok(Unit)
        }
    }

    override suspend fun findByDid(did: String): IdkResult<DidRecord?, IdkError> =
        mutex.withLock {
            val id = recordsByDid[did]
            if (id == null) {
                Ok(null)
            } else {
                Ok(records[id])
            }
        }

    override suspend fun findByAlias(alias: String): IdkResult<DidRecord?, IdkError> =
        mutex.withLock {
            val id = recordsByAlias[alias]
            if (id == null) {
                Ok(null)
            } else {
                Ok(records[id])
            }
        }

    override suspend fun findAll(filter: DidRecordFilter?): IdkResult<List<DidRecord>, IdkError> =
        mutex.withLock {
            var result = records.values.toList()

            filter?.let { f ->
                f.method?.let { method ->
                    result = result.filter { it.method == method }
                }
                f.alias?.let { alias ->
                    result = result.filter { it.alias == alias }
                }
                f.role?.let { role ->
                    result = result.filter { it.role == role }
                }
                if (!f.includeDeactivated) {
                    result = result.filter { !it.deactivated }
                }
            }

            Ok(result)
        }

    override suspend fun update(record: DidRecord): IdkResult<Unit, IdkError> {
        return mutex.withLock {
            val existing = records[record.id]
            if (existing == null) {
                return@withLock Err(IdkError.NOT_FOUND_ERROR(message = "Record not found: ${record.id}"))
            }

            // Update alias mappings if changed
            if (existing.alias != record.alias) {
                existing.alias?.let { recordsByAlias.remove(it) }
                record.alias?.let { recordsByAlias[it] = record.id }
            }

            records[record.id] = record
            Ok(Unit)
        }
    }

    override suspend fun delete(did: String): IdkResult<Unit, IdkError> {
        return mutex.withLock {
            val id = recordsByDid[did]
            if (id == null) {
                return@withLock Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
            }

            val record = records.remove(id)
            recordsByDid.remove(did)
            record?.alias?.let { recordsByAlias.remove(it) }
            keyMappings.remove(id)
            Ok(Unit)
        }
    }

    override suspend fun saveKeyMapping(
        didRecordId: String,
        mapping: DidKeyMappingRecord,
    ): IdkResult<Unit, IdkError> {
        return mutex.withLock {
            val mappings = keyMappings.getOrPut(didRecordId) { mutableListOf() }

            // Check for duplicate
            if (mappings.any { it.id == mapping.id }) {
                return@withLock Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Key mapping already exists: ${mapping.id}",
                    ),
                )
            }

            mappings.add(mapping)
            Ok(Unit)
        }
    }

    override suspend fun getKeyMappings(didRecordId: String): IdkResult<List<DidKeyMappingRecord>, IdkError> =
        mutex.withLock {
            Ok(keyMappings[didRecordId]?.toList() ?: emptyList())
        }

    override suspend fun deleteKeyMapping(mappingId: String): IdkResult<Unit, IdkError> {
        return mutex.withLock {
            for ((_, mappings) in keyMappings) {
                val removed = mappings.removeAll { it.id == mappingId }
                if (removed) {
                    return@withLock Ok(Unit)
                }
            }
            Err(IdkError.NOT_FOUND_ERROR(message = "Key mapping not found: $mappingId"))
        }
    }

    override suspend fun deleteKeyMappingsForDid(didRecordId: String): IdkResult<Unit, IdkError> =
        mutex.withLock {
            keyMappings.remove(didRecordId)
            Ok(Unit)
        }

    /**
     * Clears all data from the repository.
     * Useful for testing.
     */
    suspend fun clear() {
        mutex.withLock {
            records.clear()
            recordsByDid.clear()
            recordsByAlias.clear()
            keyMappings.clear()
        }
    }

    /**
     * Returns the count of stored records.
     * Useful for testing.
     */
    suspend fun count(): Int =
        mutex.withLock {
            records.size
        }
}
