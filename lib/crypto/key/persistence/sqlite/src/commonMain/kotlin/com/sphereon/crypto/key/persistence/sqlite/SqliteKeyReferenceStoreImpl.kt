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

package com.sphereon.crypto.key.persistence.sqlite

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyReferenceFilter
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.crypto.key.persistence.NoOpKeyReferenceStore
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * SQLite implementation of [KeyReferenceStore].
 *
 * Uses SQLDelight for type-safe queries. Soft-deletes records (sets `deleted_at`)
 * rather than removing rows, preserving audit history. All queries filter out
 * soft-deleted rows via `WHERE deleted_at IS NULL`.
 *
 * Replaces [NoOpKeyReferenceStore] when this module is on the classpath,
 * enabling persistent mode for the [ManagedKeyStoreSelector][com.sphereon.crypto.key.persistence.impl.ManagedKeyStoreSelector].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<KeyReferenceStore>(), replaces = [NoOpKeyReferenceStore::class])
class SqliteKeyReferenceStoreImpl(
    private val database: KeyReferenceDatabaseSqlite,
) : KeyReferenceStore {
    private val queries get() = database.keyReferenceQueries

    override suspend fun save(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> =
        withContext(IO) {
            try {
                queries.insertRecord(
                    id = record.id,
                    tenantId = record.tenantId,
                    alias = record.alias,
                    kid = record.kid,
                    providerId = record.providerId,
                    origin = record.origin.name.lowercase(),
                    keyType = record.keyType?.let { it::class.simpleName },
                    signatureAlgorithm = record.signatureAlgorithm?.let { it::class.simpleName },
                    keyVisibility = record.keyVisibility?.name,
                    keyEncoding = record.keyEncoding?.name,
                    createdAt = record.createdAt.toString(),
                    createdById = record.createdById,
                    updatedAt = record.updatedAt.toString(),
                    updatedById = record.updatedById,
                )
                Ok(record)
            } catch (expected: Exception) {
                Err(IdkError.UNKNOWN_ERROR(message = "Failed to save key reference: ${expected.message}", exception = expected))
            }
        }

    override suspend fun upsert(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> =
        withContext(IO) {
            try {
                val existing =
                    queries
                        .findByAlias(
                            tenantId = record.tenantId,
                            alias = record.alias,
                            providerId = record.providerId,
                        ).executeAsOneOrNull()

                if (existing != null) {
                    queries.updateRecord(
                        id = existing.id,
                        kid = record.kid,
                        origin = record.origin.name.lowercase(),
                        keyType = record.keyType?.let { it::class.simpleName },
                        signatureAlgorithm = record.signatureAlgorithm?.let { it::class.simpleName },
                        keyVisibility = record.keyVisibility?.name,
                        keyEncoding = record.keyEncoding?.name,
                        updatedAt = record.updatedAt.toString(),
                        updatedById = record.updatedById,
                    )
                    Ok(record.copy(id = existing.id))
                } else {
                    save(record)
                }
            } catch (expected: Exception) {
                Err(IdkError.UNKNOWN_ERROR(message = "Failed to upsert key reference: ${expected.message}", exception = expected))
            }
        }

    override suspend fun findById(
        tenantId: String,
        id: String,
    ): IdkResult<KeyReferenceRecord?, IdkError> =
        withContext(IO) {
            try {
                val row = queries.findById(tenantId = tenantId, id = id).executeAsOneOrNull()
                Ok(row?.toKeyReferenceRecord())
            } catch (expected: Exception) {
                Err(IdkError.UNKNOWN_ERROR(message = "Failed to find key reference by id: ${expected.message}", exception = expected))
            }
        }

    override suspend fun findByKid(
        tenantId: String,
        kid: String,
        providerId: String?,
    ): IdkResult<KeyReferenceRecord?, IdkError> =
        withContext(IO) {
            try {
                val row = queries.findByKid(tenantId = tenantId, kid = kid, providerId = providerId).executeAsOneOrNull()
                Ok(row?.toKeyReferenceRecord())
            } catch (expected: Exception) {
                Err(IdkError.UNKNOWN_ERROR(message = "Failed to find key reference by kid: ${expected.message}", exception = expected))
            }
        }

    override suspend fun findByAlias(
        tenantId: String,
        alias: String,
        providerId: String?,
    ): IdkResult<KeyReferenceRecord?, IdkError> =
        withContext(IO) {
            try {
                val row =
                    queries
                        .findByAlias(
                            tenantId = tenantId,
                            alias = alias,
                            providerId = providerId,
                        ).executeAsOneOrNull()
                Ok(row?.toKeyReferenceRecord())
            } catch (expected: Exception) {
                Err(IdkError.UNKNOWN_ERROR(message = "Failed to find key reference by alias: ${expected.message}", exception = expected))
            }
        }

    override suspend fun findAll(
        tenantId: String,
        filter: ManagedKeyReferenceFilter?,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> =
        withContext(IO) {
            try {
                val rows =
                    if (filter != null) {
                        queries
                            .findAllFiltered(
                                tenantId = tenantId,
                                providerId = filter.providerId,
                                alias = filter.alias,
                                kid = filter.kid,
                                origin = filter.origin?.name?.lowercase(),
                                keyType = filter.keyType?.let { it::class.simpleName },
                            ).executeAsList()
                    } else {
                        queries.findAll(tenantId = tenantId).executeAsList()
                    }
                Ok(rows.map { it.toKeyReferenceRecord() })
            } catch (expected: Exception) {
                Err(IdkError.UNKNOWN_ERROR(message = "Failed to list key references: ${expected.message}", exception = expected))
            }
        }

    override suspend fun delete(
        tenantId: String,
        alias: String,
        providerId: String,
    ): IdkResult<Boolean, IdkError> =
        withContext(IO) {
            try {
                val now =
                    kotlin.time.Clock.System
                        .now()
                        .toString()
                queries.softDelete(
                    tenantId = tenantId,
                    alias = alias,
                    providerId = providerId,
                    deletedAt = now,
                    deletedById = null,
                )
                Ok(true)
            } catch (expected: Exception) {
                Err(IdkError.UNKNOWN_ERROR(message = "Failed to delete key reference: ${expected.message}", exception = expected))
            }
        }

    override suspend fun deleteByKid(
        tenantId: String,
        kid: String,
        providerId: String?,
    ): IdkResult<Boolean, IdkError> =
        withContext(IO) {
            try {
                val now =
                    kotlin.time.Clock.System
                        .now()
                        .toString()
                queries.softDeleteByKid(
                    tenantId = tenantId,
                    kid = kid,
                    providerId = providerId,
                    deletedAt = now,
                    deletedById = null,
                )
                Ok(true)
            } catch (expected: Exception) {
                Err(IdkError.UNKNOWN_ERROR(message = "Failed to delete key reference by kid: ${expected.message}", exception = expected))
            }
        }

    override suspend fun exists(
        tenantId: String,
        alias: String,
        providerId: String,
    ): IdkResult<Boolean, IdkError> =
        withContext(IO) {
            try {
                val count =
                    queries
                        .existsByAliasAndProvider(
                            tenantId = tenantId,
                            alias = alias,
                            providerId = providerId,
                        ).executeAsOne()
                Ok(count > 0)
            } catch (expected: Exception) {
                Err(IdkError.UNKNOWN_ERROR(message = "Failed to check key reference existence: ${expected.message}", exception = expected))
            }
        }

    /**
     * Maps a raw DB row to a [KeyReferenceRecord].
     *
     * Parsing strategy: every enum-backed column uses a throwing parser. Unrecognised values
     * indicate either data corruption or a schema drift, and propagate to the enclosing
     * `try/catch` as `Err(UNKNOWN_ERROR)` — the caller gets a loud failure rather than a
     * record with silently-nulled fields that can't be distinguished from a real NULL.
     *
     * Trade-off: rolling out a new enum value in the DB before the consuming code can parse
     * it will surface as `Err` on read. Accepted: incorrect data is worse than visible
     * incompatibility, and forward-compat is better addressed by schema/migration discipline.
     */
    private fun Key_reference.toKeyReferenceRecord(): KeyReferenceRecord =
        KeyReferenceRecord(
            id = id,
            tenantId = tenant_id,
            alias = alias,
            kid = kid,
            providerId = provider_id,
            origin = Origin.fromValue(origin),
            keyType = key_type?.let { KeyTypeMapping.fromValue(it) },
            signatureAlgorithm = signature_algorithm?.let { SignatureAlgorithm.fromValue(it) },
            keyVisibility = key_visibility?.let { KeyVisibility.fromValue(it) },
            keyEncoding = key_encoding?.let { KeyEncoding.fromValue(it) },
            createdAt = Instant.parse(created_at),
            createdById = created_by_id,
            updatedAt = Instant.parse(updated_at),
            updatedById = updated_by_id,
            deletedAt = deleted_at?.let { Instant.parse(it) },
            deletedById = deleted_by_id,
        )
}
