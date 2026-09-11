/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.certificate.persistence.sqlite

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.certificate.persistence.CertificateReferenceKind
import com.sphereon.crypto.certificate.persistence.CertificateReferenceRecord
import com.sphereon.crypto.certificate.persistence.CertificateReferenceSource
import com.sphereon.crypto.certificate.persistence.CertificateReferenceHistoryCapability
import com.sphereon.crypto.certificate.persistence.CertificateReferenceStore
import com.sphereon.crypto.certificate.persistence.CertificateReferenceStoreErrorCodes
import com.sphereon.crypto.core.ResourceControlMode
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/** SQLite implementation of the tenant-scoped certificate reference store. */
@Inject
class SqliteCertificateReferenceStoreImpl(
    private val database: CertificateReferenceDatabaseSqlite,
) : CertificateReferenceStore {
    private val queries get() = database.certificateReferenceQueries

    override val ownershipHistoryCapability: CertificateReferenceHistoryCapability =
        CertificateReferenceHistoryCapability.DURABLE

    override suspend fun save(record: CertificateReferenceRecord): IdkResult<CertificateReferenceRecord, IdkError> =
        withContext(IO) {
            try {
                val persisted = database.transactionWithResult {
                    insert(record)
                    queries.findPersistedById(record.tenantId, record.id).executeAsOneOrNull()
                        ?: error("Certificate reference was not persisted")
                }
                Ok(persisted.toRecord())
            } catch (e: Exception) {
                Err(IdkError.UNKNOWN_ERROR(message = "Failed to save certificate reference: ${e.message}", exception = e))
            }
        }

    override suspend fun upsert(record: CertificateReferenceRecord): IdkResult<CertificateReferenceRecord, IdkError> =
        withContext(IO) {
            try {
                val persisted = database.transactionWithResult {
                    val existing = queries.findExistingForUpsert(
                        tenantId = record.tenantId,
                        alias = record.alias,
                        providerId = record.providerId,
                        kind = record.kind.toStorageValue(),
                    ).executeAsOneOrNull()
                    val persistedId = if (existing == null) {
                        insert(record)
                        record.id
                    } else {
                        update(record, existing.id)
                        check(queries.lastChangeCount().executeAsOne() == 1L) {
                            "Certificate reference update affected no active tenant-owned row"
                        }
                        existing.id
                    }
                    queries.findPersistedById(
                        tenantId = record.tenantId,
                        id = persistedId,
                    ).executeAsOneOrNull()
                        ?: error("Certificate reference was not persisted")
                }
                Ok(persisted.toRecord())
            } catch (e: Exception) {
                Err(
                    IdkError.fromString(
                        code = CertificateReferenceStoreErrorCodes.PERSISTENCE_CONFLICT,
                        message = "Certificate reference persistence conflict",
                        exception = e,
                    ),
                )
            }
        }

    override suspend fun findById(tenantId: String, id: String): IdkResult<CertificateReferenceRecord?, IdkError> =
        withContext(IO) {
            try {
                Ok(queries.findById(tenantId, id).executeAsOneOrNull()?.toRecord())
            } catch (e: Exception) {
                Err(IdkError.UNKNOWN_ERROR(message = "Failed to find certificate reference by id: ${e.message}", exception = e))
            }
        }

    override suspend fun findByAlias(
        tenantId: String,
        alias: String,
        providerId: String,
        kind: CertificateReferenceKind,
    ): IdkResult<CertificateReferenceRecord?, IdkError> = withContext(IO) {
        try {
            Ok(queries.findByAlias(tenantId, alias, providerId, kind.toStorageValue()).executeAsOneOrNull()?.toRecord())
        } catch (e: Exception) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to find certificate reference by alias: ${e.message}", exception = e))
        }
    }

    override suspend fun findAllByAliasIncludingDeleted(
        tenantId: String,
        alias: String,
        providerId: String?,
        kind: CertificateReferenceKind,
    ): IdkResult<List<CertificateReferenceRecord>, IdkError> = withContext(IO) {
        try {
            Ok(
                queries.findAllByAliasIncludingDeleted(
                    tenantId = tenantId,
                    alias = alias,
                    providerId = providerId,
                    kind = kind.toStorageValue(),
                ).executeAsList().map { it.toRecord() },
            )
        } catch (e: Exception) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to find certificate reference history by alias: ${e.message}", exception = e))
        }
    }

    override suspend fun findLatestByAliasIncludingDeleted(
        tenantId: String,
        alias: String,
        providerId: String?,
        kind: CertificateReferenceKind,
    ): IdkResult<CertificateReferenceRecord?, IdkError> = withContext(IO) {
        try {
            Ok(
                queries.findLatestByAliasIncludingDeleted(
                    tenantId = tenantId,
                    alias = alias,
                    providerId = providerId,
                    kind = kind.toStorageValue(),
                ).executeAsOneOrNull()?.toRecord(),
            )
        } catch (e: Exception) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to find latest certificate reference history by alias: ${e.message}", exception = e))
        }
    }

    override suspend fun findByProviderCertificateId(
        tenantId: String,
        providerId: String,
        providerCertificateId: String,
    ): IdkResult<List<CertificateReferenceRecord>, IdkError> = withContext(IO) {
        try {
            Ok(queries.findByProviderCertificateId(tenantId, providerId, providerCertificateId).executeAsList().map { it.toRecord() })
        } catch (e: Exception) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to find certificate reference by provider id: ${e.message}", exception = e))
        }
    }

    override suspend fun findByLinkedKeyReferenceId(
        tenantId: String,
        linkedKeyReferenceId: String,
    ): IdkResult<List<CertificateReferenceRecord>, IdkError> = withContext(IO) {
        try {
            Ok(queries.findByLinkedKeyReferenceId(tenantId, linkedKeyReferenceId).executeAsList().map { it.toRecord() })
        } catch (e: Exception) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to find certificate references by linked key: ${e.message}", exception = e))
        }
    }

    override suspend fun findAll(
        tenantId: String,
        providerId: String?,
        kind: CertificateReferenceKind?,
        source: CertificateReferenceSource?,
    ): IdkResult<List<CertificateReferenceRecord>, IdkError> = withContext(IO) {
        try {
            Ok(
                queries.findAll(
                    tenantId = tenantId,
                    providerId = providerId,
                    kind = kind?.toStorageValue(),
                    source = source?.toStorageValue(),
                ).executeAsList().map { it.toRecord() },
            )
        } catch (e: Exception) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to list certificate references: ${e.message}", exception = e))
        }
    }

    override suspend fun delete(
        tenantId: String,
        alias: String,
        providerId: String,
        kind: CertificateReferenceKind,
    ): IdkResult<Boolean, IdkError> = withContext(IO) {
        try {
            val changed = database.transactionWithResult {
                queries.softDelete(
                    tenantId = tenantId,
                    alias = alias,
                    providerId = providerId,
                    kind = kind.toStorageValue(),
                    deletedAt = Clock.System.now().toString(),
                    deletedById = null,
                )
                queries.lastChangeCount().executeAsOne() == 1L
            }
            Ok(changed)
        } catch (e: Exception) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to delete certificate reference: ${e.message}", exception = e))
        }
    }

    override suspend fun deleteById(tenantId: String, id: String): IdkResult<Boolean, IdkError> = withContext(IO) {
        try {
            val changed = database.transactionWithResult {
                queries.softDeleteById(
                    tenantId = tenantId,
                    id = id,
                    deletedAt = Clock.System.now().toString(),
                    deletedById = null,
                )
                queries.lastChangeCount().executeAsOne() == 1L
            }
            Ok(changed)
        } catch (e: Exception) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to delete certificate reference by id: ${e.message}", exception = e))
        }
    }

    private fun insert(record: CertificateReferenceRecord) {
        queries.insertRecord(
            id = record.id,
            tenantId = record.tenantId,
            alias = record.alias,
            providerId = record.providerId,
            providerCertificateId = record.providerCertificateId,
            kind = record.kind.toStorageValue(),
            source = record.source.toStorageValue(),
            controlMode = record.controlMode.toStorageValue(),
            linkedKeyReferenceId = record.linkedKeyReferenceId,
            certificateChainDer = record.certificateChainDer,
            certificateFingerprint = record.certificateFingerprint,
            publicKeyFingerprint = record.publicKeyFingerprint,
            createdAt = record.createdAt.toString(),
            createdById = record.createdById,
            updatedAt = record.updatedAt.toString(),
            updatedById = record.updatedById,
            deletedAt = record.deletedAt?.toString(),
            deletedById = record.deletedById,
        )
    }

    private fun update(record: CertificateReferenceRecord, id: String) {
        queries.updateRecord(
            id = id,
            tenantId = record.tenantId,
            providerCertificateId = record.providerCertificateId,
            source = record.source.toStorageValue(),
            controlMode = record.controlMode.toStorageValue(),
            linkedKeyReferenceId = record.linkedKeyReferenceId,
            certificateChainDer = record.certificateChainDer,
            certificateFingerprint = record.certificateFingerprint,
            publicKeyFingerprint = record.publicKeyFingerprint,
            updatedAt = record.updatedAt.toString(),
            updatedById = record.updatedById,
            deletedAt = record.deletedAt?.toString(),
            deletedById = record.deletedById,
        )
    }

    private fun Certificate_reference.toRecord(): CertificateReferenceRecord = CertificateReferenceRecord(
        id = id,
        tenantId = tenant_id,
        alias = alias,
        providerId = provider_id,
        providerCertificateId = provider_certificate_id,
        kind = CertificateReferenceKind.fromStorageValue(kind),
        source = CertificateReferenceSource.fromStorageValue(source),
        controlMode = control_mode.toResourceControlMode(),
        linkedKeyReferenceId = linked_key_reference_id,
        certificateChainDer = certificate_chain_der,
        certificateFingerprint = certificate_fingerprint,
        publicKeyFingerprint = public_key_fingerprint,
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
            else -> error("Unknown certificate reference control_mode: $this")
        }
}
