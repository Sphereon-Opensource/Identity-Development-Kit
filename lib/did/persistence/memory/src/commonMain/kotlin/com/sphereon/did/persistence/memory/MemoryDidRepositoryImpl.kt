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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * In-memory [DidRepository] with the same semantics as the SQLite dialect.
 *
 * All maps are guarded by a single [Mutex]; aggregate operations (`save`, `replaceContexts`,
 * `delete`) wrap every map mutation in a single `withLock` to emulate the SQL
 * transaction guarantee — in-flight readers see either the pre- or the post-state, never a
 * partial mix.
 *
 * Constructed by [MemoryDidRepositoryFactory] — not DI-bound directly.
 */
class MemoryDidRepositoryImpl : DidRepository {
    private val mutex = Mutex()

    // Aggregate root, keyed by DidRecord.id.
    private val records = mutableMapOf<String, DidRecord>()

    // Child-row stores keyed by didRecordId → ordered list.
    private val controllers = mutableMapOf<String, MutableList<DidControllerRecord>>()
    private val akas = mutableMapOf<String, MutableList<DidAlsoKnownAsRecord>>()
    private val equivalentIds = mutableMapOf<String, MutableList<DidEquivalentIdRecord>>()
    private val contexts = mutableMapOf<String, MutableList<DidDocumentContextRecord>>()
    private val vms = mutableMapOf<String, MutableList<DidVerificationMethodRecord>>()
    private val rels = mutableMapOf<String, MutableList<DidVerificationRelationshipRecord>>()
    private val services = mutableMapOf<String, MutableList<DidServiceRecord>>()
    private val keyMappings = mutableMapOf<String, MutableList<DidKeyMappingRecord>>()

    // ============ Aggregate ops ============

    override suspend fun save(aggregate: DidDetail): IdkResult<Unit, IdkError> =
        mutex.withLock {
            try {
                val record = aggregate.record
                val duplicate =
                    records.values.firstOrNull { existing ->
                        existing.id != record.id &&
                            existing.tenantId == record.tenantId &&
                            existing.did == record.did &&
                            existing.deletedAt == null &&
                            record.deletedAt == null
                    }
                if (duplicate != null) {
                    return@withLock Err(
                        IdkError.ALREADY_EXISTS_ERROR(
                            message = "save: Live DID already exists for tenant=${record.tenantId} did=${record.did}",
                        ),
                    )
                }
                records[record.id] = record
                controllers[record.id] = aggregate.controller.toMutableList()
                akas[record.id] = aggregate.alsoKnownAs.toMutableList()
                equivalentIds[record.id] = aggregate.equivalentId.toMutableList()
                contexts[record.id] = aggregate.context.toMutableList()
                vms[record.id] = aggregate.verificationMethod.toMutableList()
                rels[record.id] = aggregate.verificationRelationship.toMutableList()
                services[record.id] = aggregate.service.toMutableList()
                keyMappings[record.id] = aggregate.keyMapping.toMutableList()
                Ok(Unit)
            } catch (exception: Exception) {
                Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "save: ${exception.message ?: exception::class.simpleName}",
                        exception = exception,
                    )
                )
            }
        }

    override suspend fun findByDid(
        tenantId: String?,
        did: String,
        includeDeleted: Boolean,
    ): IdkResult<DidDetail?, IdkError> =
        mutex.withLock {
            val match =
                records.values.firstOrNull { record ->
                    record.did == did &&
                        (tenantId == null || record.tenantId == tenantId) &&
                        (includeDeleted || record.deletedAt == null)
                }
            if (match == null) Ok(null) else Ok(loadAggregate(match).defensiveCopy())
        }

    override suspend fun findByAlias(
        tenantId: String?,
        alias: String,
        includeDeleted: Boolean,
    ): IdkResult<DidDetail?, IdkError> =
        mutex.withLock {
            val match =
                records.values.firstOrNull { record ->
                    record.alias == alias &&
                        (tenantId == null || record.tenantId == tenantId) &&
                        (includeDeleted || record.deletedAt == null)
                }
            if (match == null) Ok(null) else Ok(loadAggregate(match).defensiveCopy())
        }

    override suspend fun findAll(filter: DidRecordFilter): IdkResult<List<DidDetail>, IdkError> =
        mutex.withLock {
            val matches =
                records.values
                    .asSequence()
                    .filter { record -> matchesFilter(record, filter) }
                    .sortedWith(comparatorFor(filter))
                    .toList()
            val pageSize = filter.size
            val sliced =
                if (pageSize == null) {
                    matches
                } else {
                    val from = (filter.page.coerceAtLeast(0) * pageSize).coerceAtMost(matches.size)
                    val to = (from + pageSize).coerceAtMost(matches.size)
                    if (from >= to) emptyList() else matches.subList(from, to)
                }
            Ok(sliced.map { record -> loadAggregate(record).defensiveCopy() })
        }

    override suspend fun count(filter: DidRecordFilter): IdkResult<Long, IdkError> =
        mutex.withLock {
            Ok(records.values.count { record -> matchesFilter(record, filter) }.toLong())
        }

    private fun matchesFilter(
        record: DidRecord,
        filter: DidRecordFilter
    ): Boolean =
        (filter.tenantId == null || record.tenantId == filter.tenantId) &&
            (filter.method == null || record.method == filter.method) &&
            (filter.alias == null || record.alias == filter.alias) &&
            (filter.role == null || record.role == filter.role) &&
            (filter.includeDeactivated || !record.deactivated) &&
            (filter.includeDeleted || record.deletedAt == null) &&
            matchesSearch(record, filter.search)

    private fun matchesSearch(
        record: DidRecord,
        search: String?,
    ): Boolean {
        if (search.isNullOrBlank()) return true
        val needle = search.lowercase()
        return record.did.lowercase().contains(needle) ||
            (record.alias?.lowercase()?.contains(needle) == true)
    }

    private fun comparatorFor(filter: DidRecordFilter): Comparator<DidRecord> {
        val base: Comparator<DidRecord> =
            when (filter.sort) {
                com.sphereon.did.manager.DidSortField.CREATED_AT -> compareBy { it.createdAt }
                com.sphereon.did.manager.DidSortField.UPDATED_AT -> compareBy { it.updatedAt }
                com.sphereon.did.manager.DidSortField.DID -> compareBy { it.did }
                com.sphereon.did.manager.DidSortField.METHOD -> compareBy { it.method }
                com.sphereon.did.manager.DidSortField.ALIAS -> compareBy(nullsLast()) { it.alias }
            }
        return if (filter.sortDirection == com.sphereon.did.manager.SortDirection.DESC) base.reversed() else base
    }

    override suspend fun softDelete(
        tenantId: String?,
        did: String,
        deletedAt: Instant,
        deletedBy: String?,
    ): IdkResult<Unit, IdkError> =
        mutex.withLock {
            val target =
                records.values.firstOrNull { record ->
                    record.did == did &&
                        (tenantId == null || record.tenantId == tenantId) &&
                        record.deletedAt == null
                } ?: return@withLock Err(
                    IdkError.NOT_FOUND_ERROR(
                        message = "softDelete: no live DID record for tenantId=$tenantId did=$did",
                    )
                )
            records[target.id] =
                target.copy(
                    deletedAt = deletedAt,
                    deletedById = deletedBy,
                    updatedAt = deletedAt,
                )
            Ok(Unit)
        }

    override suspend fun delete(
        tenantId: String?,
        did: String
    ): IdkResult<Unit, IdkError> =
        mutex.withLock {
            val target =
                records.values.firstOrNull { record ->
                    record.did == did && (tenantId == null || record.tenantId == tenantId)
                } ?: return@withLock Err(
                    IdkError.NOT_FOUND_ERROR(
                        message = "delete: no DID record for tenantId=$tenantId did=$did",
                    )
                )
            val didRecordId = target.id
            records.remove(didRecordId)
            controllers.remove(didRecordId)
            akas.remove(didRecordId)
            equivalentIds.remove(didRecordId)
            contexts.remove(didRecordId)
            vms.remove(didRecordId)
            rels.remove(didRecordId)
            services.remove(didRecordId)
            keyMappings.remove(didRecordId)
            Ok(Unit)
        }

    // ============ Child-row ops ============

    override suspend fun saveVerificationMethod(vm: DidVerificationMethodRecord): IdkResult<Unit, IdkError> =
        upsertChild("saveVerificationMethod", vms, vm.didRecordId, vm) { existing -> existing.id == vm.id }

    override suspend fun deleteVerificationMethod(
        tenantId: String?,
        verificationMethodId: String,
    ): IdkResult<Unit, IdkError> =
        mutex.withLock {
            val target =
                vms.values
                    .asSequence()
                    .flatten()
                    .firstOrNull { existing -> existing.id == verificationMethodId }
                    ?: return@withLock Ok(Unit)
            if (!targetTenantMatches(tenantId, target.didRecordId)) return@withLock Ok(Unit)
            val wireId = target.vmId
            // Cascade: drop string-ref relationships (which still reference the wire-form
            // vm_id), embedded-VM relationship rows (by VM UUID), and key mappings (by VM
            // UUID after IDK-18 o77).
            rels.keys.toList().forEach { didRecordId ->
                val list = rels[didRecordId] ?: return@forEach
                list.removeAll { rel ->
                    rel.entryRefDidUrl == wireId || rel.entryEmbeddedVmId == verificationMethodId
                }
            }
            keyMappings.keys.toList().forEach { didRecordId ->
                val list = keyMappings[didRecordId] ?: return@forEach
                list.removeAll { mapping -> mapping.verificationMethodId == verificationMethodId }
            }
            vms[target.didRecordId]?.removeAll { existing -> existing.id == verificationMethodId }
            Ok(Unit)
        }

    override suspend fun saveVerificationRelationship(rel: DidVerificationRelationshipRecord): IdkResult<Unit, IdkError> =
        upsertChild("saveVerificationRelationship", rels, rel.didRecordId, rel) { existing -> existing.id == rel.id }

    override suspend fun deleteVerificationRelationship(
        tenantId: String?,
        relationshipId: String,
    ): IdkResult<Unit, IdkError> = deleteChildScoped("deleteVerificationRelationship", rels, tenantId) { existing -> existing.id == relationshipId }

    override suspend fun saveService(service: DidServiceRecord): IdkResult<Unit, IdkError> =
        upsertChild("saveService", services, service.didRecordId, service) { existing -> existing.id == service.id }

    override suspend fun deleteService(
        tenantId: String?,
        serviceId: String,
    ): IdkResult<Unit, IdkError> = deleteChildScoped("deleteService", services, tenantId) { existing -> existing.id == serviceId }

    override suspend fun saveKeyMapping(mapping: DidKeyMappingRecord): IdkResult<Unit, IdkError> =
        upsertChild("saveKeyMapping", keyMappings, mapping.didRecordId, mapping) { existing -> existing.id == mapping.id }

    override suspend fun deleteKeyMapping(
        tenantId: String?,
        mappingId: String,
    ): IdkResult<Unit, IdkError> = deleteChildScoped("deleteKeyMapping", keyMappings, tenantId) { existing -> existing.id == mappingId }

    override suspend fun saveController(controller: DidControllerRecord): IdkResult<Unit, IdkError> =
        upsertChild("saveController", controllers, controller.didRecordId, controller) { existing -> existing.id == controller.id }

    override suspend fun deleteController(
        tenantId: String?,
        controllerId: String,
    ): IdkResult<Unit, IdkError> = deleteChildScoped("deleteController", controllers, tenantId) { existing -> existing.id == controllerId }

    override suspend fun saveAlsoKnownAs(aka: DidAlsoKnownAsRecord): IdkResult<Unit, IdkError> = upsertChild("saveAlsoKnownAs", akas, aka.didRecordId, aka) { existing -> existing.id == aka.id }

    override suspend fun deleteAlsoKnownAs(
        tenantId: String?,
        akaId: String,
    ): IdkResult<Unit, IdkError> = deleteChildScoped("deleteAlsoKnownAs", akas, tenantId) { existing -> existing.id == akaId }

    override suspend fun saveEquivalentId(eq: DidEquivalentIdRecord): IdkResult<Unit, IdkError> =
        upsertChild("saveEquivalentId", equivalentIds, eq.didRecordId, eq) { existing -> existing.id == eq.id }

    override suspend fun deleteEquivalentId(
        tenantId: String?,
        equivalentIdRowId: String,
    ): IdkResult<Unit, IdkError> = deleteChildScoped("deleteEquivalentId", equivalentIds, tenantId) { existing -> existing.id == equivalentIdRowId }

    /**
     * Tenant-scope guard for child-row deletes: if [tenantId] is non-null, the parent
     * `did_record` must exist with that tenant. Null tenant is tolerated for dev-mode
     * IDK semantics. Cross-tenant deletes are silently ignored — the row appears not to
     * exist from the caller's perspective.
     */
    private fun targetTenantMatches(
        tenantId: String?,
        didRecordId: String
    ): Boolean {
        if (tenantId == null) return true
        val parent = records[didRecordId] ?: return false
        return parent.tenantId == tenantId
    }

    // ============ Context ops ============

    override suspend fun getContexts(didRecordId: String): IdkResult<List<DidDocumentContextRecord>, IdkError> = mutex.withLock { Ok(contexts[didRecordId]?.map { row -> row.copy() }.orEmpty()) }

    override suspend fun replaceContexts(
        didRecordId: String,
        contexts: List<DidDocumentContextRecord>,
    ): IdkResult<Unit, IdkError> =
        mutex.withLock {
            this.contexts[didRecordId] = contexts.toMutableList()
            Ok(Unit)
        }

    // ============ Internal helpers ============

    private fun loadAggregate(record: DidRecord): DidDetail {
        val didRecordId = record.id
        return DidDetail(
            record = record,
            controller = controllers[didRecordId].orEmptyListSortedBy { controller -> controller.ordinal },
            alsoKnownAs = akas[didRecordId].orEmptyListSortedBy { aka -> aka.ordinal },
            equivalentId = equivalentIds[didRecordId].orEmptyListSortedBy { equivalentId -> equivalentId.ordinal },
            context = contexts[didRecordId].orEmptyListSortedBy { context -> context.ordinal },
            verificationMethod = vms[didRecordId].orEmptyListSortedBy { vm -> vm.ordinal },
            verificationRelationship =
                rels[didRecordId].orEmptyListSorted(
                    compareBy({ rel -> rel.purpose }, { rel -> rel.ordinal })
                ),
            service = services[didRecordId].orEmptyListSortedBy { service -> service.ordinal },
            keyMapping = keyMappings[didRecordId].orEmptyListSortedBy { mapping -> mapping.createdAt },
        )
    }

    private fun DidDetail.defensiveCopy(): DidDetail =
        copy(
            record = record.copy(),
            controller = controller.map { row -> row.copy() },
            alsoKnownAs = alsoKnownAs.map { row -> row.copy() },
            equivalentId = equivalentId.map { row -> row.copy() },
            context = context.map { row -> row.copy() },
            verificationMethod = verificationMethod.map { row -> row.copy() },
            verificationRelationship = verificationRelationship.map { row -> row.copy() },
            service = service.map { row -> row.copy() },
            keyMapping = keyMapping.map { row -> row.copy() },
        )

    private suspend inline fun <T> upsertChild(
        label: String,
        map: MutableMap<String, MutableList<T>>,
        didRecordId: String,
        row: T,
        crossinline match: (T) -> Boolean,
    ): IdkResult<Unit, IdkError> =
        mutex.withLock {
            try {
                // A child row identified by `match` may currently live under a different parent
                // bucket (e.g. a row was reparented). Strip it from every other bucket first so it
                // never appears under two parents simultaneously.
                map.entries.forEach { (parentId, list) ->
                    if (parentId != didRecordId) list.removeAll { existing -> match(existing) }
                }
                val list = map.getOrPut(didRecordId) { mutableListOf() }
                val index = list.indexOfFirst { existing -> match(existing) }
                if (index >= 0) list[index] = row else list.add(row)
                Ok(Unit)
            } catch (exception: Exception) {
                Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "$label: ${exception.message ?: exception::class.simpleName}",
                        exception = exception,
                    )
                )
            }
        }

    private suspend inline fun <T> deleteChild(
        label: String,
        map: MutableMap<String, MutableList<T>>,
        crossinline match: (T) -> Boolean,
    ): IdkResult<Unit, IdkError> =
        mutex.withLock {
            try {
                map.values.forEach { list -> list.removeAll { existing -> match(existing) } }
                Ok(Unit)
            } catch (exception: Exception) {
                Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "$label: ${exception.message ?: exception::class.simpleName}",
                        exception = exception,
                    )
                )
            }
        }

    /**
     * Tenant-scoped variant of [deleteChild]. Only deletes from buckets whose parent
     * `did_record.tenantId` matches [tenantId]. A null [tenantId] tolerates any parent
     * (dev-mode IDK semantics). Cross-tenant attempts are silently ignored.
     */
    private suspend inline fun <T> deleteChildScoped(
        label: String,
        map: MutableMap<String, MutableList<T>>,
        tenantId: String?,
        crossinline match: (T) -> Boolean,
    ): IdkResult<Unit, IdkError> =
        mutex.withLock {
            try {
                map.entries.forEach { (parentId, list) ->
                    if (tenantId == null || records[parentId]?.tenantId == tenantId) {
                        list.removeAll { existing -> match(existing) }
                    }
                }
                Ok(Unit)
            } catch (exception: Exception) {
                Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "$label: ${exception.message ?: exception::class.simpleName}",
                        exception = exception,
                    )
                )
            }
        }

    private fun <Row, Key : Comparable<Key>> MutableList<Row>?.orEmptyListSortedBy(selector: (Row) -> Key,): List<Row> = this?.sortedBy(selector) ?: emptyList()

    private fun <Row> MutableList<Row>?.orEmptyListSorted(comparator: Comparator<Row>,): List<Row> = this?.sortedWith(comparator) ?: emptyList()
}
