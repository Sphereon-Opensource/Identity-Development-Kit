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

package com.sphereon.crypto.key.persistence

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.ManagedKeyReferenceFilter

enum class KeyReferenceHistoryCapability {
    UNSUPPORTED,
    DURABLE,
}

object KeyReferenceStoreErrorCodes {
    const val DURABLE_HISTORY_UNSUPPORTED = "KMS_KEY_REFERENCE_DURABLE_HISTORY_UNSUPPORTED"
    const val AMBIGUOUS_REFERENCE = "KMS_KEY_REFERENCE_AMBIGUOUS"
    const val EXTERNAL_KEY_REGISTRATION_CONFLICT = "KMS_EXTERNAL_KEY_REGISTRATION_CONFLICT"
}

class KeyReferenceResolutionException(
    val code: String,
    message: String,
) : Exception(message)

private fun <T> unsupportedHistoryResult(): IdkResult<T, IdkError> =
    Err(
        IdkError.fromString(
            code = KeyReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED,
            message = "This key reference store does not provide durable ownership history",
        ),
    )

/**
 * Repository interface for tenant-aware key reference persistence.
 *
 * Stores key references: metadata, plus the public verification material a verifier would be
 * handed anyway. Never private or symmetric key material. The database is an index/reference
 * layer; the KMS provider remains the system of record for key material.
 *
 * Implementations may use SQLite (IDK), PostgreSQL/MySQL (EDK), or in-memory storage.
 */
interface KeyReferenceStore {
    /**
     * Whether this store is backed by a real persistence implementation.
     * Returns `false` for the [NoOpKeyReferenceStore] default binding.
     */
    val isAvailable: Boolean get() = true

    /**
     * Whether destructive ownership decisions can rely on durable active and soft-deleted history.
     *
     * The source-compatible default is deliberately unsupported. Implementations may advertise
     * [KeyReferenceHistoryCapability.DURABLE] only when they override every all-match lookup below
     * and retain soft-deleted rows across process restarts.
     */
    val ownershipHistoryCapability: KeyReferenceHistoryCapability
        get() = KeyReferenceHistoryCapability.UNSUPPORTED

    /** Insert a new key reference. Fails if a record with the same alias+provider already exists for this tenant. */
    suspend fun save(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError>

    /** Insert or update a key reference, matched by tenant + alias + provider. */
    suspend fun upsert(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError>

    /** Find a key reference by its primary-key id within a tenant. */
    suspend fun findById(
        tenantId: String,
        id: String,
    ): IdkResult<KeyReferenceRecord?, IdkError>

    /** Find a key reference by its key identifier within a tenant. Optionally scoped to a specific provider. */
    suspend fun findByKid(
        tenantId: String,
        kid: String,
        providerId: String? = null,
    ): IdkResult<KeyReferenceRecord?, IdkError>

    /** Find a key reference by alias within a tenant. Optionally scoped to a specific provider. */
    suspend fun findByAlias(
        tenantId: String,
        alias: String,
        providerId: String? = null,
    ): IdkResult<KeyReferenceRecord?, IdkError>

    /** Find every active alias match. Durable stores must override this method. */
    suspend fun findAllActiveByAlias(
        tenantId: String,
        alias: String,
        providerId: String? = null,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> =
        findByAlias(tenantId, alias, providerId).map { record -> listOfNotNull(record) }

    /** Find every active kid match. Durable stores must override this method. */
    suspend fun findAllActiveByKid(
        tenantId: String,
        kid: String,
        providerId: String? = null,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> =
        findByKid(tenantId, kid, providerId).map { record -> listOfNotNull(record) }

    /** Find every alias match, including soft-deleted rows. */
    suspend fun findAllByAliasIncludingDeleted(
        tenantId: String,
        alias: String,
        providerId: String? = null,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> = unsupportedHistoryResult()

    /** Find every kid match, including soft-deleted rows. */
    suspend fun findAllByKidIncludingDeleted(
        tenantId: String,
        kid: String,
        providerId: String? = null,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> = unsupportedHistoryResult()

    /**
     * Find the most recent key reference by alias, including soft-deleted history.
     *
     * This is an ownership-authority lookup for destructive operations. It is tenant scoped and
     * optionally provider scoped; implementations must return a deterministic latest row when an
     * identifier has been reused after a previous soft delete.
     */
    suspend fun findLatestByAliasIncludingDeleted(
        tenantId: String,
        alias: String,
        providerId: String? = null,
    ): IdkResult<KeyReferenceRecord?, IdkError> = unsupportedHistoryResult()

    /**
     * Find the most recent key reference by kid, including soft-deleted history.
     *
     * This is an ownership-authority lookup for destructive operations. It is tenant scoped and
     * optionally provider scoped; implementations must return a deterministic latest row when an
     * identifier has been reused after a previous soft delete.
     */
    suspend fun findLatestByKidIncludingDeleted(
        tenantId: String,
        kid: String,
        providerId: String? = null,
    ): IdkResult<KeyReferenceRecord?, IdkError> = unsupportedHistoryResult()

    /** List all key references for a tenant, optionally filtered. */
    suspend fun findAll(
        tenantId: String,
        filter: ManagedKeyReferenceFilter? = null,
    ): IdkResult<List<KeyReferenceRecord>, IdkError>

    /**
     * Soft-delete a key reference by alias + provider within a tenant.
     * Returns `true` if at least one record was deleted, `false` if no matching active record existed.
     */
    suspend fun delete(
        tenantId: String,
        alias: String,
        providerId: String,
    ): IdkResult<Boolean, IdkError>

    /**
     * Soft-delete a key reference by kid within a tenant. Optionally scoped to a specific provider.
     * Returns `true` if at least one record was deleted, `false` if no matching active record existed.
     */
    suspend fun deleteByKid(
        tenantId: String,
        kid: String,
        providerId: String? = null,
    ): IdkResult<Boolean, IdkError>

    /** Check whether a key reference exists for the given alias + provider within a tenant. */
    suspend fun exists(
        tenantId: String,
        alias: String,
        providerId: String,
    ): IdkResult<Boolean, IdkError>
}
