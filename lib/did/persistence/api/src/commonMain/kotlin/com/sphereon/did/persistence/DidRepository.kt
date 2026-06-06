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
import kotlin.time.Instant

/**
 * Persistence contract for DIDs.
 *
 * Every [DidDetail] is loaded and saved as a unit — the aggregate-root [DidRecord] plus every
 * child-row collection. Sub-resource methods (`saveVerificationMethod`, `deleteService`, etc.)
 * exist for the incremental mutations the IDK-19 REST surface exposes as addressable endpoints;
 * full-graph save is always `save(aggregate)`.
 *
 * Rules that apply across every implementation (Memory, SQLite, PostgreSQL, MySQL):
 * - Returns [IdkResult] with [IdkError]. "Not found" returns `Ok(null)`, not an `Err` —
 *   see `vdx/CLAUDE.md` §Result Type.
 * - Timestamps are [kotlin.time.Instant].
 * - [tenantId] is nullable on the Memory + SQLite IDK dev dialects; PostgreSQL + MySQL
 *   require a non-null tenant and return [IdkError.ILLEGAL_ARGUMENT_ERROR] when passed `null`.
 * - Aggregate operations are transactional: SQL dialects use `transaction { }`; Memory wraps
 *   the same work in a single `Mutex.withLock`.
 * - Aggregate save is **delete-then-insert** for child rows — simple, idempotent, fine at the
 *   sizes we expect (typical aggregates hold fewer than 30 rows).
 */
interface DidRepository {
    // ============ Aggregate ops (primary read/write path) ============

    /**
     * Persists [aggregate] atomically. Upserts the aggregate root by [DidRecord.id] and
     * replaces every child collection (delete-then-insert inside one transaction).
     */
    suspend fun save(aggregate: DidDetail): IdkResult<Unit, IdkError>

    /**
     * Loads the aggregate for [did] within [tenantId]. Returns `Ok(null)` when no row matches.
     *
     * @param includeDeleted When `true`, returns soft-deleted aggregates as well.
     */
    suspend fun findByDid(
        tenantId: String?,
        did: String,
        includeDeleted: Boolean = false,
    ): IdkResult<DidDetail?, IdkError>

    /**
     * Loads the aggregate whose [DidRecord.alias] matches. Returns `Ok(null)` when no row matches.
     */
    suspend fun findByAlias(
        tenantId: String?,
        alias: String,
        includeDeleted: Boolean = false,
    ): IdkResult<DidDetail?, IdkError>

    /**
     * Loads every aggregate matching [filter]. Implementation must batch child-row fetches
     * (1 query for did_record + 1 batched-IN query per child table = 9 queries total,
     * regardless of result size — never N+1). Ordering follows [DidRecord.createdAt] ascending.
     *
     * If [DidRecordFilter.size] is non-null the storage layer applies `LIMIT/OFFSET` (SQL) or an
     * equivalent slice (memory) so the caller never has to materialise the full set just to
     * retrieve one page. [DidRecordFilter.page] is zero-based.
     */
    suspend fun findAll(filter: DidRecordFilter): IdkResult<List<DidDetail>, IdkError>

    /**
     * Counts every aggregate matching [filter] (page/size on [filter] are ignored — the count
     * always reflects the full result set). Used by paginated list flows to populate
     * `totalElements` without materialising every aggregate.
     */
    suspend fun count(filter: DidRecordFilter): IdkResult<Long, IdkError>

    /**
     * Soft-deletes the DID by setting `deleted_at = [deletedAt]`. Child rows are retained so
     * audit queries (`includeDeleted = true`) still see the historic shape.
     */
    suspend fun softDelete(
        tenantId: String?,
        did: String,
        deletedAt: Instant,
        deletedBy: String?,
    ): IdkResult<Unit, IdkError>

    /**
     * Deletes the DID and cascades to every child row. Reserved for tenant offboarding
     * and similar administrative flows — day-to-day use is [softDelete].
     */
    suspend fun delete(
        tenantId: String?,
        did: String,
    ): IdkResult<Unit, IdkError>

    // ============ Child-row ops (IDK-19 sub-resource REST endpoints) ============

    /** Upserts a verification method (insert on new `id`, replace otherwise). */
    suspend fun saveVerificationMethod(vm: DidVerificationMethodRecord): IdkResult<Unit, IdkError>

    /**
     * Removes a verification method and cascades to any relationships / key mappings.
     *
     * [tenantId] scopes the delete to a specific tenant. EDK dialects MUST reject
     * `tenantId == null` with `ILLEGAL_ARGUMENT_ERROR` and join through the parent
     * `did_record` so that a child-row UUID belonging to tenant A cannot be deleted from
     * a request scoped to tenant B. The IDK Memory/SQLite dialects accept null tenant
     * (development only). Returns `NOT_FOUND_ERROR` if no row matched.
     */
    suspend fun deleteVerificationMethod(
        tenantId: String?,
        verificationMethodId: String,
    ): IdkResult<Unit, IdkError>

    /** Upserts a relationship row. */
    suspend fun saveVerificationRelationship(rel: DidVerificationRelationshipRecord): IdkResult<Unit, IdkError>

    /**
     * Removes a relationship row by id. See [deleteVerificationMethod] for tenant
     * isolation semantics.
     */
    suspend fun deleteVerificationRelationship(
        tenantId: String?,
        relationshipId: String,
    ): IdkResult<Unit, IdkError>

    /** Upserts a service. */
    suspend fun saveService(service: DidServiceRecord): IdkResult<Unit, IdkError>

    /**
     * Removes a service by id. See [deleteVerificationMethod] for tenant isolation
     * semantics.
     */
    suspend fun deleteService(
        tenantId: String?,
        serviceId: String,
    ): IdkResult<Unit, IdkError>

    /** Upserts a key mapping. */
    suspend fun saveKeyMapping(mapping: DidKeyMappingRecord): IdkResult<Unit, IdkError>

    /**
     * Removes a key mapping by id. See [deleteVerificationMethod] for tenant isolation
     * semantics.
     */
    suspend fun deleteKeyMapping(
        tenantId: String?,
        mappingId: String,
    ): IdkResult<Unit, IdkError>

    /** Upserts a controller entry. */
    suspend fun saveController(controller: DidControllerRecord): IdkResult<Unit, IdkError>

    /**
     * Removes a controller entry by id. See [deleteVerificationMethod] for tenant
     * isolation semantics.
     */
    suspend fun deleteController(
        tenantId: String?,
        controllerId: String,
    ): IdkResult<Unit, IdkError>

    /** Upserts an `alsoKnownAs` entry. */
    suspend fun saveAlsoKnownAs(aka: DidAlsoKnownAsRecord): IdkResult<Unit, IdkError>

    /**
     * Removes an `alsoKnownAs` entry by id. See [deleteVerificationMethod] for tenant
     * isolation semantics.
     */
    suspend fun deleteAlsoKnownAs(
        tenantId: String?,
        akaId: String,
    ): IdkResult<Unit, IdkError>

    /** Upserts an `equivalentId` entry. */
    suspend fun saveEquivalentId(eq: DidEquivalentIdRecord): IdkResult<Unit, IdkError>

    /**
     * Removes an `equivalentId` entry by its row id (not the DID string). See
     * [deleteVerificationMethod] for tenant isolation semantics.
     */
    suspend fun deleteEquivalentId(
        tenantId: String?,
        equivalentIdRowId: String,
    ): IdkResult<Unit, IdkError>

    // ============ Context ops ============

    /** Returns the ordered `@context` list for a DID. */
    suspend fun getContexts(didRecordId: String): IdkResult<List<DidDocumentContextRecord>, IdkError>

    /**
     * Atomically replaces every context row for [didRecordId] with [contexts]
     * (delete-then-insert). Separate from per-row operations because the `@context` array is
     * a single ordered value whose mutation is always "replace the whole list".
     */
    suspend fun replaceContexts(
        didRecordId: String,
        contexts: List<DidDocumentContextRecord>,
    ): IdkResult<Unit, IdkError>
}
