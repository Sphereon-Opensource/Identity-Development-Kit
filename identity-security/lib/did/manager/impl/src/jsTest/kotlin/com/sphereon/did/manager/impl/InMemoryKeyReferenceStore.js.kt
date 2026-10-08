/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.manager.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.ManagedKeyReferenceFilter
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.crypto.key.persistence.NoOpKeyReferenceStore
import com.sphereon.crypto.key.persistence.SelectingKeyReferenceStore
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<KeyReferenceStore>(),
    // Replace both the config-driven selector and the NoOp fallback it would
    // otherwise re-expose once the selector itself is replaced.
    replaces = [SelectingKeyReferenceStore::class, NoOpKeyReferenceStore::class],
)
class InMemoryKeyReferenceStore : KeyReferenceStore {
    private val records = mutableMapOf<String, KeyReferenceRecord>()

    override suspend fun save(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> {
        records[record.id] = record
        return Ok(record)
    }

    override suspend fun upsert(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> {
        val existing = findActiveByAliasAndProvider(record.tenantId, record.alias, record.providerId)
        return if (existing != null) {
            val updated = record.copy(id = existing.id)
            records[existing.id] = updated
            Ok(updated)
        } else {
            save(record)
        }
    }

    override suspend fun findById(
        tenantId: String,
        id: String
    ): IdkResult<KeyReferenceRecord?, IdkError> = Ok(records[id]?.takeIf { it.tenantId == tenantId && it.deletedAt == null })

    override suspend fun findByKid(
        tenantId: String,
        kid: String,
        providerId: String?
    ): IdkResult<KeyReferenceRecord?, IdkError> =
        Ok(
            records.values.firstOrNull {
                it.tenantId == tenantId &&
                    it.deletedAt == null &&
                    it.kid == kid &&
                    (providerId == null || it.providerId == providerId)
            },
        )

    override suspend fun findByAlias(
        tenantId: String,
        alias: String,
        providerId: String?
    ): IdkResult<KeyReferenceRecord?, IdkError> = Ok(findActiveByAliasAndProvider(tenantId, alias, providerId))

    override suspend fun findAll(
        tenantId: String,
        filter: ManagedKeyReferenceFilter?,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> {
        val matches =
            records.values.filter { record ->
                if (record.tenantId != tenantId || record.deletedAt != null) return@filter false
                if (filter == null) return@filter true
                filter.providerId?.let { if (record.providerId != it) return@filter false }
                filter.alias?.let { if (record.alias != it) return@filter false }
                filter.kid?.let { if (record.kid != it) return@filter false }
                filter.origin?.let { if (record.origin != it) return@filter false }
                filter.keyType?.let { if (record.keyType != it) return@filter false }
                true
            }
        return Ok(matches)
    }

    override suspend fun delete(
        tenantId: String,
        alias: String,
        providerId: String
    ): IdkResult<Boolean, IdkError> {
        val existing = findActiveByAliasAndProvider(tenantId, alias, providerId) ?: return Ok(false)
        records[existing.id] = existing.copy(deletedAt = Clock.System.now())
        return Ok(true)
    }

    override suspend fun deleteByKid(
        tenantId: String,
        kid: String,
        providerId: String?
    ): IdkResult<Boolean, IdkError> {
        val existing =
            records.values.firstOrNull {
                it.tenantId == tenantId &&
                    it.deletedAt == null &&
                    it.kid == kid &&
                    (providerId == null || it.providerId == providerId)
            } ?: return Ok(false)
        records[existing.id] = existing.copy(deletedAt = Clock.System.now())
        return Ok(true)
    }

    override suspend fun exists(
        tenantId: String,
        alias: String,
        providerId: String
    ): IdkResult<Boolean, IdkError> = Ok(findActiveByAliasAndProvider(tenantId, alias, providerId) != null)

    private fun findActiveByAliasAndProvider(
        tenantId: String,
        alias: String,
        providerId: String?
    ): KeyReferenceRecord? =
        records.values.firstOrNull {
            it.tenantId == tenantId &&
                it.deletedAt == null &&
                it.alias == alias &&
                (providerId == null || it.providerId == providerId)
        }
}
