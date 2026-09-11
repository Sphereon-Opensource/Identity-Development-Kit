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
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceHistoryCapability
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.crypto.key.persistence.KeyReferenceStoreErrorCodes
import dev.zacsweers.metro.Inject
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
 * Not bound directly: [SqliteKeyReferenceStoreFactory] contributes this dialect
 * into the database-dialect selection map consumed by
 * [SelectingKeyReferenceStore][com.sphereon.crypto.key.persistence.SelectingKeyReferenceStore],
 * so the dialect is a runtime configuration decision and may coexist on the
 * classpath with the PostgreSQL/MySQL dialects.
 */
@Inject
class SqliteKeyReferenceStoreImpl(
    private val database: KeyReferenceDatabaseSqlite,
    override val ownershipHistoryCapability: KeyReferenceHistoryCapability = KeyReferenceHistoryCapability.UNSUPPORTED,
) : KeyReferenceStore {
    private val queries get() = database.keyReferenceQueries

    override suspend fun save(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> =
        withContext(IO) {
            try {
                insertRecord(record)
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
                    require(!(record.origin == com.sphereon.core.api.model.Origin.EXTERNAL &&
                        record.controlMode == ResourceControlMode.PLATFORM_MANAGED &&
                        record.keyVisibility == com.sphereon.crypto.core.KeyVisibility.PUBLIC) || existing.id == record.id) {
                        "Public import cannot replace an existing key reference"
                    }

                    queries.updateRecord(
                        id = existing.id,
                        kid = record.kid,
                        origin = record.origin.name.lowercase(),
                        controlMode = record.controlMode.toStorageValue(),
                        keyType = record.keyType?.let { it::class.simpleName },
                        signatureAlgorithm = record.signatureAlgorithm?.let { it::class.simpleName },
                        keyVisibility = record.keyVisibility?.name,
                        keyEncoding = record.keyEncoding?.name,
                        publicKeyJwk = record.publicKeyJwk,
                        updatedAt = record.updatedAt.toString(),
                        updatedById = record.updatedById,
                    )
                    // Ownership is immutable in the SQL upsert. Return the authoritative owner
                    // rather than echoing caller input, so callers cannot mistake a rejected
                    // relabel attempt for a successful ownership change.
                    Ok(record.copy(id = existing.id, walletUnitId = existing.wallet_unit_id))
                } else {
                    insertRecord(record)
                    Ok(record)
                }
            } catch (expected: Exception) {
                Err(upsertError(expected))
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

    override suspend fun findAllActiveByAlias(
        tenantId: String,
        alias: String,
        providerId: String?,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> =
        queryMany("active key references by alias") {
            queries.findAllActiveByAlias(tenantId, alias, providerId).executeAsList()
        }

    override suspend fun findAllActiveByKid(
        tenantId: String,
        kid: String,
        providerId: String?,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> =
        queryMany("active key references by kid") {
            queries.findAllActiveByKid(tenantId, kid, providerId).executeAsList()
        }

    override suspend fun findAllByAliasIncludingDeleted(
        tenantId: String,
        alias: String,
        providerId: String?,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> =
        queryMany("key reference history by alias") {
            queries.findAllByAliasIncludingDeleted(tenantId, alias, providerId).executeAsList()
        }

    override suspend fun findAllByKidIncludingDeleted(
        tenantId: String,
        kid: String,
        providerId: String?,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> =
        queryMany("key reference history by kid") {
            queries.findAllByKidIncludingDeleted(tenantId, kid, providerId).executeAsList()
        }

    override suspend fun findLatestByAliasIncludingDeleted(
        tenantId: String,
        alias: String,
        providerId: String?,
    ): IdkResult<KeyReferenceRecord?, IdkError> =
        withContext(IO) {
            try {
                val row =
                    queries
                        .findLatestByAliasIncludingDeleted(
                            tenantId = tenantId,
                            alias = alias,
                            providerId = providerId,
                        ).executeAsOneOrNull()
                Ok(row?.toKeyReferenceRecord())
            } catch (expected: Exception) {
                Err(IdkError.UNKNOWN_ERROR(message = "Failed to find key reference history by alias: ${expected.message}", exception = expected))
            }
        }

    override suspend fun findLatestByKidIncludingDeleted(
        tenantId: String,
        kid: String,
        providerId: String?,
    ): IdkResult<KeyReferenceRecord?, IdkError> =
        withContext(IO) {
            try {
                val row =
                    queries
                        .findLatestByKidIncludingDeleted(
                            tenantId = tenantId,
                            kid = kid,
                            providerId = providerId,
                        ).executeAsOneOrNull()
                Ok(row?.toKeyReferenceRecord())
            } catch (expected: Exception) {
                Err(IdkError.UNKNOWN_ERROR(message = "Failed to find key reference history by kid: ${expected.message}", exception = expected))
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

    private fun insertRecord(record: KeyReferenceRecord) {
        queries.insertRecord(
            id = record.id,
            tenantId = record.tenantId,
            alias = record.alias,
            kid = record.kid,
            providerId = record.providerId,
            origin = record.origin.name.lowercase(),
            controlMode = record.controlMode.toStorageValue(),
            keyType = record.keyType?.let { it::class.simpleName },
            signatureAlgorithm = record.signatureAlgorithm?.let { it::class.simpleName },
            keyVisibility = record.keyVisibility?.name,
            keyEncoding = record.keyEncoding?.name,
            publicKeyJwk = record.publicKeyJwk,
            walletUnitId = record.walletUnitId,
            createdAt = record.createdAt.toString(),
            createdById = record.createdById,
            updatedAt = record.updatedAt.toString(),
            updatedById = record.updatedById,
        )
    }

    private suspend fun queryMany(
        description: String,
        query: () -> List<Key_reference>,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> =
        withContext(IO) {
            try {
                Ok(query().map { it.toKeyReferenceRecord() })
            } catch (expected: Exception) {
                Err(IdkError.UNKNOWN_ERROR(message = "Failed to find $description: ${expected.message}", exception = expected))
            }
        }

    private fun upsertError(expected: Exception): IdkError {
        val message = generateSequence<Throwable>(expected) { it.cause }.joinToString(" ") { it.message.orEmpty() }
        val isKeyIdentityConflict =
            message.contains("UNIQUE constraint failed", ignoreCase = true) &&
                message.contains("key_reference.", ignoreCase = true) &&
                (message.contains(".alias", ignoreCase = true) || message.contains(".kid", ignoreCase = true))
        return if (isKeyIdentityConflict) {
            IdkError.fromString(
                code = KeyReferenceStoreErrorCodes.EXTERNAL_KEY_REGISTRATION_CONFLICT,
                message = "The key alias or canonical provider key identifier is already registered",
            )
        } else {
            IdkError.UNKNOWN_ERROR(message = "Failed to upsert key reference: ${expected.message}", exception = expected)
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
            controlMode = control_mode.toResourceControlMode(),
            keyType = key_type?.let { KeyTypeMapping.fromValue(it) },
            signatureAlgorithm = signature_algorithm?.let { SignatureAlgorithm.fromValue(it) },
            keyVisibility = key_visibility?.let { KeyVisibility.fromValue(it) },
            keyEncoding = key_encoding?.let { KeyEncoding.fromValue(it) },
            publicKeyJwk = public_key_jwk,
            walletUnitId = wallet_unit_id,
            createdAt = Instant.parse(created_at),
            createdById = created_by_id,
            updatedAt = Instant.parse(updated_at),
            updatedById = updated_by_id,
            deletedAt = deleted_at?.let { Instant.parse(it) },
            deletedById = deleted_by_id,
        )

    private fun ResourceControlMode.toStorageValue(): String =
        when (this) {
            ResourceControlMode.PLATFORM_MANAGED -> "platform_managed"
            ResourceControlMode.EXTERNALLY_MANAGED -> "externally_managed"
        }

    private fun String.toResourceControlMode(): ResourceControlMode =
        when (this) {
            "platform_managed" -> ResourceControlMode.PLATFORM_MANAGED
            "externally_managed" -> ResourceControlMode.EXTERNALLY_MANAGED
            else -> throw IllegalArgumentException("Unknown key reference control_mode: $this")
        }
}
