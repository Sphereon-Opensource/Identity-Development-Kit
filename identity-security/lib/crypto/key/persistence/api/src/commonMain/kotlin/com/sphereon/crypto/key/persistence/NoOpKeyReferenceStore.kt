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
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.ManagedKeyReferenceFilter
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default no-op implementation of [KeyReferenceStore].
 *
 * Bound as the default when no persistence module is on the classpath.
 * Persistence modules (SQLite, PostgreSQL, MySQL) replace this binding
 * via `@ContributesBinding(replaces = [NoOpKeyReferenceStore::class])`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<KeyReferenceStore>())
class NoOpKeyReferenceStore : KeyReferenceStore {
    override val isAvailable: Boolean = false
    override val ownershipHistoryCapability: KeyReferenceHistoryCapability = KeyReferenceHistoryCapability.UNSUPPORTED

    override suspend fun save(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> = Ok(record)

    override suspend fun upsert(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> = Ok(record)

    override suspend fun findById(
        tenantId: String,
        id: String,
    ): IdkResult<KeyReferenceRecord?, IdkError> = Ok(null)

    override suspend fun findByKid(
        tenantId: String,
        kid: String,
        providerId: String?,
    ): IdkResult<KeyReferenceRecord?, IdkError> = Ok(null)

    override suspend fun findByAlias(
        tenantId: String,
        alias: String,
        providerId: String?,
    ): IdkResult<KeyReferenceRecord?, IdkError> = Ok(null)

    override suspend fun findAll(
        tenantId: String,
        filter: ManagedKeyReferenceFilter?,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> = Ok(emptyList())

    override suspend fun delete(
        tenantId: String,
        alias: String,
        providerId: String,
    ): IdkResult<Boolean, IdkError> = Ok(false)

    override suspend fun deleteByKid(
        tenantId: String,
        kid: String,
        providerId: String?,
    ): IdkResult<Boolean, IdkError> = Ok(false)

    override suspend fun exists(
        tenantId: String,
        alias: String,
        providerId: String,
    ): IdkResult<Boolean, IdkError> = Ok(false)
}
