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
import com.sphereon.did.manager.DidRole
import com.sphereon.did.persistence.DidAlsoKnownAsRecord
import com.sphereon.did.persistence.DidControllerRecord
import com.sphereon.did.persistence.DidDetail
import com.sphereon.did.persistence.DidDocumentContextRecord
import com.sphereon.did.persistence.DidEquivalentIdRecord
import com.sphereon.did.persistence.DidKeyMappingRecord
import com.sphereon.did.persistence.DidRecord
import com.sphereon.did.persistence.DidRecordFilter
import com.sphereon.did.persistence.DidRepository
import com.sphereon.did.persistence.DidServiceRecord
import com.sphereon.did.persistence.DidVerificationMethodRecord
import com.sphereon.did.persistence.DidVerificationRelationshipRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * SQLite-backed [DidRepository]. Aggregate operations run inside SQLDelight
 * `transaction { }` blocks; child rows use delete-then-insert semantics (D4).
 *
 * Constructed by [SqliteDidRepositoryFactory] — not DI-bound directly.
 */
class SqliteDidRepositoryImpl(
    private val database: DidDatabaseSqlite,
) : DidRepository {
    private val queries get() = database.didQueries

    // ============ Aggregate ops ============

    override suspend fun save(aggregate: DidDetail): IdkResult<Unit, IdkError> =
        io {
            try {
                database.transaction {
                    val record = aggregate.record
                    val exists = queries.existsDidRecordById(record.id).executeAsOne() > 0L
                    if (exists) {
                        queries.updateDidRecord(
                            alias = record.alias,
                            role = record.role.name,
                            canonicalId = record.canonicalId,
                            deactivated = record.deactivated,
                            extensionPropertiesJson = record.extensionPropertiesJson,
                            updatedAt = record.updatedAt,
                            updatedById = record.updatedById,
                            deletedAt = record.deletedAt,
                            deletedById = record.deletedById,
                            id = record.id,
                        )
                    } else {
                        queries.insertDidRecord(
                            id = record.id,
                            tenantId = record.tenantId,
                            did = record.did,
                            method = record.method,
                            alias = record.alias,
                            role = record.role.name,
                            canonicalId = record.canonicalId,
                            deactivated = record.deactivated,
                            extensionPropertiesJson = record.extensionPropertiesJson,
                            createdAt = record.createdAt,
                            createdById = record.createdById,
                            updatedAt = record.updatedAt,
                            updatedById = record.updatedById,
                            deletedAt = record.deletedAt,
                            deletedById = record.deletedById,
                        )
                    }

                    // Children: delete-then-insert.
                    queries.deleteControllersByDidRecord(record.id)
                    aggregate.controller.forEach { controller -> insertControllerRow(controller) }

                    queries.deleteAkasByDidRecord(record.id)
                    aggregate.alsoKnownAs.forEach { aka -> insertAkaRow(aka) }

                    queries.deleteEquivalentIdsByDidRecord(record.id)
                    aggregate.equivalentId.forEach { equivalentId -> insertEquivalentIdRow(equivalentId) }

                    queries.deleteContextsByDidRecord(record.id)
                    aggregate.context.forEach { context -> insertContextRow(context) }

                    // Relationships FK the VM table, so delete rels BEFORE vms.
                    queries.deleteRelsByDidRecord(record.id)
                    queries.deleteVmsByDidRecord(record.id)
                    aggregate.verificationMethod.forEach { vm -> insertVmRow(vm) }
                    aggregate.verificationRelationship.forEach { rel -> insertRelRow(rel) }

                    queries.deleteServicesByDidRecord(record.id)
                    aggregate.service.forEach { service -> insertServiceRow(service) }

                    queries.deleteKeyMappingsByDidRecord(record.id)
                    aggregate.keyMapping.forEach { mapping -> insertKeyMappingRow(mapping) }
                }
                Ok(Unit)
            } catch (exception: Exception) {
                Err(translate(exception, "save"))
            }
        }

    override suspend fun findByDid(
        tenantId: String?,
        did: String,
        includeDeleted: Boolean,
    ): IdkResult<DidDetail?, IdkError> =
        io {
            try {
                val row = queries.findDidRecordByDid(did, tenantId, boolToLong(includeDeleted)).executeAsList().firstOrNull()
                if (row == null) Ok(null) else Ok(loadAggregate(row.toDidRecord()))
            } catch (exception: Exception) {
                Err(translate(exception, "findByDid"))
            }
        }

    override suspend fun findByAlias(
        tenantId: String?,
        alias: String,
        includeDeleted: Boolean,
    ): IdkResult<DidDetail?, IdkError> =
        io {
            try {
                val row = queries.findDidRecordByAlias(alias, tenantId, boolToLong(includeDeleted)).executeAsList().firstOrNull()
                if (row == null) Ok(null) else Ok(loadAggregate(row.toDidRecord()))
            } catch (exception: Exception) {
                Err(translate(exception, "findByAlias"))
            }
        }

    override suspend fun findAll(filter: DidRecordFilter): IdkResult<List<DidDetail>, IdkError> =
        io {
            try {
                // size=null means "no pagination" — pass Long.MAX_VALUE so every backend (sqlite,
                // postgres, mysql) accepts the LIMIT clause without dialect-specific shimming.
                val limit = filter.size?.toLong() ?: Long.MAX_VALUE
                val offset = filter.size?.let { (filter.page.coerceAtLeast(0).toLong()) * it } ?: 0L
                val rows =
                    queries
                        .findDidRecordsFiltered(
                            tenantId = filter.tenantId,
                            method = filter.method,
                            alias = filter.alias,
                            role = filter.role?.name,
                            search = filter.search,
                            includeDeactivated = boolToLong(filter.includeDeactivated),
                            includeDeleted = boolToLong(filter.includeDeleted),
                            sort = filter.sort.toWireString(),
                            sortDirection = filter.sortDirection.name,
                            limit = limit,
                            offset = offset,
                        ).executeAsList()
                Ok(loadAggregates(rows.map { row -> row.toDidRecord() }))
            } catch (exception: Exception) {
                Err(translate(exception, "findAll"))
            }
        }

    override suspend fun count(filter: DidRecordFilter): IdkResult<Long, IdkError> =
        io {
            try {
                val total =
                    queries
                        .countDidRecordsFiltered(
                            tenantId = filter.tenantId,
                            method = filter.method,
                            alias = filter.alias,
                            role = filter.role?.name,
                            search = filter.search,
                            includeDeactivated = boolToLong(filter.includeDeactivated),
                            includeDeleted = boolToLong(filter.includeDeleted),
                        ).executeAsOne()
                Ok(total)
            } catch (exception: Exception) {
                Err(translate(exception, "count"))
            }
        }

    override suspend fun softDelete(
        tenantId: String?,
        did: String,
        deletedAt: Instant,
        deletedBy: String?,
    ): IdkResult<Unit, IdkError> =
        io {
            try {
                var found = false
                database.transaction {
                    val row = queries.findDidRecordByDid(did, tenantId, includeDeleted = 0).executeAsList().firstOrNull()
                    if (row != null) {
                        found = true
                        queries.softDeleteDidRecord(
                            deletedAt = deletedAt,
                            deletedById = deletedBy,
                            updatedAt = deletedAt,
                            did = did,
                            tenantId = tenantId,
                        )
                    }
                }
                if (found) Ok(Unit) else notFound("softDelete", tenantId, did)
            } catch (exception: Exception) {
                Err(translate(exception, "softDelete"))
            }
        }

    override suspend fun delete(
        tenantId: String?,
        did: String
    ): IdkResult<Unit, IdkError> =
        io {
            try {
                var found = false
                database.transaction {
                    val row = queries.findDidRecordByDid(did, tenantId, includeDeleted = 1).executeAsList().firstOrNull()
                    if (row != null) {
                        found = true
                        queries.deleteDidRecord(did, tenantId)
                    }
                }
                if (found) Ok(Unit) else notFound("delete", tenantId, did)
            } catch (exception: Exception) {
                Err(translate(exception, "delete"))
            }
        }

    private fun notFound(
        label: String,
        tenantId: String?,
        did: String
    ): IdkResult<Unit, IdkError> =
        Err(
            IdkError.NOT_FOUND_ERROR(
                message = "$label: no DID record found for tenantId=$tenantId did=$did",
            )
        )

    // ============ Child-row ops ============

    override suspend fun saveVerificationMethod(vm: DidVerificationMethodRecord): IdkResult<Unit, IdkError> =
        io {
            runCatchingQuery("saveVerificationMethod") {
                queries.upsertVm(
                    id = vm.id,
                    didRecordId = vm.didRecordId,
                    vmId = vm.vmId,
                    vmIdAuthored = vm.vmIdAuthored,
                    type = vm.type,
                    controller = vm.controller,
                    kmsProviderId = vm.kmsProviderId,
                    kmsKeyAlias = vm.kmsKeyAlias,
                    kmsKid = vm.kmsKid,
                    keyReferenceId = vm.keyReferenceId,
                    publicKeyJwkJson = vm.publicKeyJwkJson,
                    publicKeyMultibase = vm.publicKeyMultibase,
                    inlineInJson = vm.inlineInJson,
                    expiresAt = vm.expiresAt,
                    revokedAt = vm.revokedAt,
                    blockchainAccountId = vm.blockchainAccountId,
                    extensionPropertiesJson = vm.extensionPropertiesJson,
                    ordinal = vm.ordinal,
                    createdAt = vm.createdAt,
                    createdById = vm.createdById,
                    updatedAt = vm.updatedAt,
                    updatedById = vm.updatedById,
                )
            }
        }

    override suspend fun deleteVerificationMethod(
        tenantId: String?,
        verificationMethodId: String,
    ): IdkResult<Unit, IdkError> =
        io {
            try {
                var found = false
                database.transaction {
                    val row = queries.findVmWireIdAndDidRecordById(verificationMethodId, tenantId).executeAsOneOrNull()
                    if (row != null) {
                        found = true
                        queries.deleteRelsByEntryRefDidUrlForDid(row.vm_id, row.did_record_id)
                    }
                    queries.deleteVmById(verificationMethodId, tenantId)
                }
                if (found) {
                    Ok(Unit)
                } else {
                    Err(IdkError.NOT_FOUND_ERROR(message = "deleteVerificationMethod: no verification method found for id=$verificationMethodId"))
                }
            } catch (exception: Exception) {
                Err(translate(exception, "deleteVerificationMethod"))
            }
        }

    override suspend fun saveVerificationRelationship(rel: DidVerificationRelationshipRecord): IdkResult<Unit, IdkError> =
        io {
            runCatchingQuery("saveVerificationRelationship") {
                queries.upsertRel(
                    id = rel.id,
                    didRecordId = rel.didRecordId,
                    purpose = rel.purpose,
                    entryEmbeddedVmId = rel.entryEmbeddedVmId,
                    entryRefDidUrl = rel.entryRefDidUrl,
                    ordinal = rel.ordinal,
                    createdAt = rel.createdAt,
                    updatedAt = rel.updatedAt,
                )
            }
        }

    override suspend fun deleteVerificationRelationship(
        tenantId: String?,
        relationshipId: String,
    ): IdkResult<Unit, IdkError> =
        io {
            runCatchingQuery("deleteVerificationRelationship") { queries.deleteRelById(relationshipId, tenantId) }
        }

    override suspend fun saveService(service: DidServiceRecord): IdkResult<Unit, IdkError> =
        io {
            runCatchingQuery("saveService") {
                queries.upsertService(
                    id = service.id,
                    didRecordId = service.didRecordId,
                    serviceId = service.serviceId,
                    typeJson = service.typeJson,
                    serviceEndpointJson = service.serviceEndpointJson,
                    extensionPropertiesJson = service.extensionPropertiesJson,
                    ordinal = service.ordinal,
                    createdAt = service.createdAt,
                    createdById = service.createdById,
                    updatedAt = service.updatedAt,
                    updatedById = service.updatedById,
                )
            }
        }

    override suspend fun deleteService(
        tenantId: String?,
        serviceId: String,
    ): IdkResult<Unit, IdkError> =
        io {
            runCatchingQuery("deleteService") { queries.deleteServiceById(serviceId, tenantId) }
        }

    override suspend fun saveKeyMapping(mapping: DidKeyMappingRecord): IdkResult<Unit, IdkError> =
        io {
            runCatchingQuery("saveKeyMapping") {
                queries.upsertKeyMapping(
                    id = mapping.id,
                    didRecordId = mapping.didRecordId,
                    verificationMethodId = mapping.verificationMethodId,
                    verificationMethodDidUrl = mapping.verificationMethodDidUrl,
                    kmsProviderId = mapping.kmsProviderId,
                    kmsKeyAlias = mapping.kmsKeyAlias,
                    kmsKid = mapping.kmsKid,
                    keyReferenceId = mapping.keyReferenceId,
                    purposesJson = mapping.purposesJson,
                    createdAt = mapping.createdAt,
                    updatedAt = mapping.updatedAt,
                )
            }
        }

    override suspend fun deleteKeyMapping(
        tenantId: String?,
        mappingId: String,
    ): IdkResult<Unit, IdkError> =
        io {
            runCatchingQuery("deleteKeyMapping") { queries.deleteKeyMappingById(mappingId, tenantId) }
        }

    override suspend fun saveController(controller: DidControllerRecord): IdkResult<Unit, IdkError> =
        io {
            runCatchingQuery("saveController") { insertControllerRow(controller, upsert = true) }
        }

    override suspend fun deleteController(
        tenantId: String?,
        controllerId: String,
    ): IdkResult<Unit, IdkError> =
        io {
            runCatchingQuery("deleteController") { queries.deleteControllerById(controllerId, tenantId) }
        }

    override suspend fun saveAlsoKnownAs(aka: DidAlsoKnownAsRecord): IdkResult<Unit, IdkError> =
        io {
            runCatchingQuery("saveAlsoKnownAs") { insertAkaRow(aka, upsert = true) }
        }

    override suspend fun deleteAlsoKnownAs(
        tenantId: String?,
        akaId: String,
    ): IdkResult<Unit, IdkError> =
        io {
            runCatchingQuery("deleteAlsoKnownAs") { queries.deleteAkaById(akaId, tenantId) }
        }

    override suspend fun saveEquivalentId(eq: DidEquivalentIdRecord): IdkResult<Unit, IdkError> =
        io {
            runCatchingQuery("saveEquivalentId") { insertEquivalentIdRow(eq, upsert = true) }
        }

    override suspend fun deleteEquivalentId(
        tenantId: String?,
        equivalentIdRowId: String,
    ): IdkResult<Unit, IdkError> =
        io {
            runCatchingQuery("deleteEquivalentId") { queries.deleteEquivalentIdById(equivalentIdRowId, tenantId) }
        }

    // ============ Context ops ============

    override suspend fun getContexts(didRecordId: String): IdkResult<List<DidDocumentContextRecord>, IdkError> =
        io {
            try {
                Ok(queries.selectContextsByDidRecord(didRecordId).executeAsList().map { row -> row.toRecord() })
            } catch (exception: Exception) {
                Err(translate(exception, "getContexts"))
            }
        }

    override suspend fun replaceContexts(
        didRecordId: String,
        contexts: List<DidDocumentContextRecord>,
    ): IdkResult<Unit, IdkError> =
        io {
            try {
                database.transaction {
                    queries.deleteContextsByDidRecord(didRecordId)
                    contexts.forEach { context -> insertContextRow(context) }
                }
                Ok(Unit)
            } catch (exception: Exception) {
                Err(translate(exception, "replaceContexts"))
            }
        }

    // ============ Internal helpers ============

    private fun loadAggregate(record: DidRecord): DidDetail = loadAggregates(listOf(record)).single()

    private fun loadAggregates(records: List<DidRecord>): List<DidDetail> {
        if (records.isEmpty()) return emptyList()
        val ids = records.map { record -> record.id }
        val controllersByRecord =
            queries
                .selectControllersByDidRecords(ids)
                .executeAsList()
                .map { row -> row.toRecord() }
                .groupBy { row -> row.didRecordId }
        val akasByRecord =
            queries
                .selectAkasByDidRecords(ids)
                .executeAsList()
                .map { row -> row.toRecord() }
                .groupBy { row -> row.didRecordId }
        val equivalentsByRecord =
            queries
                .selectEquivalentIdsByDidRecords(ids)
                .executeAsList()
                .map { row -> row.toRecord() }
                .groupBy { row -> row.didRecordId }
        val contextsByRecord =
            queries
                .selectContextsByDidRecords(ids)
                .executeAsList()
                .map { row -> row.toRecord() }
                .groupBy { row -> row.didRecordId }
        val vmsByRecord =
            queries
                .selectVmsByDidRecords(ids)
                .executeAsList()
                .map { row -> row.toRecord() }
                .groupBy { row -> row.didRecordId }
        val relsByRecord =
            queries
                .selectRelsByDidRecords(ids)
                .executeAsList()
                .map { row -> row.toRecord() }
                .groupBy { row -> row.didRecordId }
        val servicesByRecord =
            queries
                .selectServicesByDidRecords(ids)
                .executeAsList()
                .map { row -> row.toRecord() }
                .groupBy { row -> row.didRecordId }
        val keyMappingsByRecord =
            queries
                .selectKeyMappingsByDidRecords(ids)
                .executeAsList()
                .map { row -> row.toRecord() }
                .groupBy { row -> row.didRecordId }

        return records.map { record ->
            DidDetail(
                record = record,
                controller = controllersByRecord[record.id].orEmpty(),
                alsoKnownAs = akasByRecord[record.id].orEmpty(),
                equivalentId = equivalentsByRecord[record.id].orEmpty(),
                context = contextsByRecord[record.id].orEmpty(),
                verificationMethod = vmsByRecord[record.id].orEmpty(),
                verificationRelationship = relsByRecord[record.id].orEmpty(),
                service = servicesByRecord[record.id].orEmpty(),
                keyMapping = keyMappingsByRecord[record.id].orEmpty(),
            )
        }
    }

    private fun insertControllerRow(
        controller: DidControllerRecord,
        upsert: Boolean = false
    ) {
        val call = if (upsert) queries::upsertController else queries::insertController
        call(
            controller.id,
            controller.didRecordId,
            controller.controllerDid,
            controller.ordinal,
            controller.createdAt,
            controller.createdById,
            controller.updatedAt,
            controller.updatedById,
        )
    }

    private fun insertAkaRow(
        aka: DidAlsoKnownAsRecord,
        upsert: Boolean = false
    ) {
        val call = if (upsert) queries::upsertAka else queries::insertAka
        call(
            aka.id,
            aka.didRecordId,
            aka.akaUri,
            aka.ordinal,
            aka.createdAt,
            aka.createdById,
            aka.updatedAt,
            aka.updatedById,
        )
    }

    private fun insertEquivalentIdRow(
        equivalentId: DidEquivalentIdRecord,
        upsert: Boolean = false
    ) {
        val call = if (upsert) queries::upsertEquivalentId else queries::insertEquivalentId
        call(
            equivalentId.id,
            equivalentId.didRecordId,
            equivalentId.equivalentDid,
            equivalentId.ordinal,
            equivalentId.createdAt,
            equivalentId.createdById,
            equivalentId.updatedAt,
            equivalentId.updatedById,
        )
    }

    private fun insertContextRow(context: DidDocumentContextRecord) {
        queries.insertContext(
            id = context.id,
            didRecordId = context.didRecordId,
            contextUri = context.contextUri,
            ordinal = context.ordinal,
            createdAt = context.createdAt,
            updatedAt = context.updatedAt,
        )
    }

    private fun insertVmRow(vm: DidVerificationMethodRecord) {
        queries.insertVm(
            id = vm.id,
            didRecordId = vm.didRecordId,
            vmId = vm.vmId,
            vmIdAuthored = vm.vmIdAuthored,
            type = vm.type,
            controller = vm.controller,
            kmsProviderId = vm.kmsProviderId,
            kmsKeyAlias = vm.kmsKeyAlias,
            kmsKid = vm.kmsKid,
            keyReferenceId = vm.keyReferenceId,
            publicKeyJwkJson = vm.publicKeyJwkJson,
            publicKeyMultibase = vm.publicKeyMultibase,
            inlineInJson = vm.inlineInJson,
            expiresAt = vm.expiresAt,
            revokedAt = vm.revokedAt,
            blockchainAccountId = vm.blockchainAccountId,
            extensionPropertiesJson = vm.extensionPropertiesJson,
            ordinal = vm.ordinal,
            createdAt = vm.createdAt,
            createdById = vm.createdById,
            updatedAt = vm.updatedAt,
            updatedById = vm.updatedById,
        )
    }

    private fun insertRelRow(rel: DidVerificationRelationshipRecord) {
        queries.insertRel(
            id = rel.id,
            didRecordId = rel.didRecordId,
            purpose = rel.purpose,
            entryEmbeddedVmId = rel.entryEmbeddedVmId,
            entryRefDidUrl = rel.entryRefDidUrl,
            ordinal = rel.ordinal,
            createdAt = rel.createdAt,
            updatedAt = rel.updatedAt,
        )
    }

    private fun insertServiceRow(service: DidServiceRecord) {
        queries.insertService(
            id = service.id,
            didRecordId = service.didRecordId,
            serviceId = service.serviceId,
            typeJson = service.typeJson,
            serviceEndpointJson = service.serviceEndpointJson,
            extensionPropertiesJson = service.extensionPropertiesJson,
            ordinal = service.ordinal,
            createdAt = service.createdAt,
            createdById = service.createdById,
            updatedAt = service.updatedAt,
            updatedById = service.updatedById,
        )
    }

    private fun insertKeyMappingRow(mapping: DidKeyMappingRecord) {
        queries.insertKeyMapping(
            id = mapping.id,
            didRecordId = mapping.didRecordId,
            verificationMethodId = mapping.verificationMethodId,
            verificationMethodDidUrl = mapping.verificationMethodDidUrl,
            kmsProviderId = mapping.kmsProviderId,
            kmsKeyAlias = mapping.kmsKeyAlias,
            kmsKid = mapping.kmsKid,
            keyReferenceId = mapping.keyReferenceId,
            purposesJson = mapping.purposesJson,
            createdAt = mapping.createdAt,
            updatedAt = mapping.updatedAt,
        )
    }

    private inline fun runCatchingQuery(
        label: String,
        block: () -> Unit
    ): IdkResult<Unit, IdkError> =
        try {
            block()
            Ok(Unit)
        } catch (exception: Exception) {
            Err(translate(exception, label))
        }

    private suspend inline fun <V, E : IdkError> io(crossinline block: suspend () -> IdkResult<V, E>,): IdkResult<V, E> = withContext(Dispatchers.IO) { block() }

    private fun boolToLong(flag: Boolean): Long = if (flag) 1L else 0L

    private fun translate(
        exception: Exception,
        label: String
    ): IdkError {
        val message = exception.message ?: exception::class.simpleName ?: "error"
        return when {
            exception is IllegalArgumentException -> {
                IdkError.ILLEGAL_ARGUMENT_ERROR(message = "$label: $message", throwable = exception)
            }

            // Uniqueness violations are the only "already exists" condition. CHECK and other
            // generic CONSTRAINT failures are validation problems, not duplicates.
            message.contains("UNIQUE", ignoreCase = true) -> {
                IdkError.ALREADY_EXISTS_ERROR(message = "$label: $message", throwable = exception)
            }

            message.contains("CHECK", ignoreCase = true) ||
                message.contains("NOT NULL", ignoreCase = true) ||
                message.contains("FOREIGN KEY", ignoreCase = true) ||
                message.contains("CONSTRAINT", ignoreCase = true) -> {
                IdkError.ILLEGAL_ARGUMENT_ERROR(message = "$label: $message", throwable = exception)
            }

            else -> {
                IdkError.UNKNOWN_ERROR(message = "$label: $message", exception = exception)
            }
        }
    }

    // ---- row → record mappers ----

    private fun Did_record.toDidRecord() =
        DidRecord(
            id = id,
            tenantId = tenant_id,
            did = did,
            method = method,
            alias = alias,
            role = DidRole.valueOf(role),
            canonicalId = canonical_id,
            deactivated = deactivated,
            extensionPropertiesJson = extension_properties_json,
            createdAt = created_at,
            createdById = created_by_id,
            updatedAt = updated_at,
            updatedById = updated_by_id,
            deletedAt = deleted_at,
            deletedById = deleted_by_id,
        )

    private fun Did_controller.toRecord() =
        DidControllerRecord(
            id = id,
            didRecordId = did_record_id,
            controllerDid = controller_did,
            ordinal = ordinal,
            createdAt = created_at,
            createdById = created_by_id,
            updatedAt = updated_at,
            updatedById = updated_by_id,
        )

    private fun Did_also_known_as.toRecord() =
        DidAlsoKnownAsRecord(
            id = id,
            didRecordId = did_record_id,
            akaUri = aka_uri,
            ordinal = ordinal,
            createdAt = created_at,
            createdById = created_by_id,
            updatedAt = updated_at,
            updatedById = updated_by_id,
        )

    private fun Did_equivalent_id.toRecord() =
        DidEquivalentIdRecord(
            id = id,
            didRecordId = did_record_id,
            equivalentDid = equivalent_did,
            ordinal = ordinal,
            createdAt = created_at,
            createdById = created_by_id,
            updatedAt = updated_at,
            updatedById = updated_by_id,
        )

    private fun Did_document_context.toRecord() =
        DidDocumentContextRecord(
            id = id,
            didRecordId = did_record_id,
            contextUri = context_uri,
            ordinal = ordinal,
            createdAt = created_at,
            updatedAt = updated_at,
        )

    private fun Did_verification_method.toRecord() =
        DidVerificationMethodRecord(
            id = id,
            didRecordId = did_record_id,
            vmId = vm_id,
            vmIdAuthored = vm_id_authored,
            type = type,
            controller = controller,
            kmsProviderId = kms_provider_id,
            kmsKeyAlias = kms_key_alias,
            kmsKid = kms_kid,
            keyReferenceId = key_reference_id,
            publicKeyJwkJson = public_key_jwk_json,
            publicKeyMultibase = public_key_multibase,
            inlineInJson = inline_in_json,
            expiresAt = expires_at,
            revokedAt = revoked_at,
            blockchainAccountId = blockchain_account_id,
            extensionPropertiesJson = extension_properties_json,
            ordinal = ordinal,
            createdAt = created_at,
            createdById = created_by_id,
            updatedAt = updated_at,
            updatedById = updated_by_id,
        )

    private fun Did_verification_relationship.toRecord() =
        DidVerificationRelationshipRecord(
            id = id,
            didRecordId = did_record_id,
            purpose = purpose,
            entryEmbeddedVmId = entry_embedded_vm_id,
            entryRefDidUrl = entry_ref_did_url,
            ordinal = ordinal,
            createdAt = created_at,
            updatedAt = updated_at,
        )

    private fun Did_service.toRecord() =
        DidServiceRecord(
            id = id,
            didRecordId = did_record_id,
            serviceId = service_id,
            typeJson = type_json,
            serviceEndpointJson = service_endpoint_json,
            extensionPropertiesJson = extension_properties_json,
            ordinal = ordinal,
            createdAt = created_at,
            createdById = created_by_id,
            updatedAt = updated_at,
            updatedById = updated_by_id,
        )

    private fun Did_key_mapping.toRecord() =
        DidKeyMappingRecord(
            id = id,
            didRecordId = did_record_id,
            verificationMethodId = verification_method_id,
            verificationMethodDidUrl = verification_method_did_url,
            kmsProviderId = kms_provider_id,
            kmsKeyAlias = kms_key_alias,
            kmsKid = kms_kid,
            keyReferenceId = key_reference_id,
            purposesJson = purposes_json,
            createdAt = created_at,
            updatedAt = updated_at,
        )
}

/**
 * Maps the typed [DidSortField] enum to its camelCase wire form expected by the
 * `findDidRecordsFiltered` / `countDidRecordsFiltered` SQLDelight queries.
 */
private fun com.sphereon.did.manager.DidSortField.toWireString(): String =
    when (this) {
        com.sphereon.did.manager.DidSortField.CREATED_AT -> "createdAt"
        com.sphereon.did.manager.DidSortField.UPDATED_AT -> "updatedAt"
        com.sphereon.did.manager.DidSortField.DID -> "did"
        com.sphereon.did.manager.DidSortField.METHOD -> "method"
        com.sphereon.did.manager.DidSortField.ALIAS -> "alias"
    }
