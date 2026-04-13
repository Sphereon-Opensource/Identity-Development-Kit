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
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.ManagedKeyReferenceFilter

/**
 * Repository interface for tenant-aware key reference persistence.
 *
 * Stores metadata-only key references — never stores actual key material,
 * JWKs, public keys, or certificates. The database is an index/reference
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

    /** Insert a new key reference. Fails if a record with the same alias+provider already exists for this tenant. */
    suspend fun save(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError>

    /** Insert or update a key reference, matched by tenant + alias + provider. */
    suspend fun upsert(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError>

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

    /** List all key references for a tenant, optionally filtered. */
    suspend fun findAll(
        tenantId: String,
        filter: ManagedKeyReferenceFilter? = null,
    ): IdkResult<List<KeyReferenceRecord>, IdkError>

    /** Soft-delete a key reference by alias + provider within a tenant. */
    suspend fun delete(
        tenantId: String,
        alias: String,
        providerId: String,
    ): IdkResult<Boolean, IdkError>

    /** Soft-delete a key reference by kid within a tenant. Optionally scoped to a specific provider. */
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
