/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wscd.testfixtures

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.ManagedKeyReferenceFilter
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceStore

/**
 * Session-scoped durable-in-process key-reference authority for wallet test composition roots.
 *
 * The production graph supplies a real durable dialect. This fixture deliberately replaces both
 * optional persistence defaults so restart tests can reconstruct WSCD instances while retaining
 * the owner metadata that the KMS command indexed at key creation.
 */
class InMemoryKeyReferenceStore : KeyReferenceStore {
    override val isAvailable: Boolean = true

    private val records = mutableMapOf<String, KeyReferenceRecord>()

    override suspend fun save(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> {
        val key = key(record.tenantId, record.alias, record.providerId)
        if (records[key] != null) {
            return Err(IdkError.fromString(code = "KMS_EXTERNAL_KEY_REGISTRATION_CONFLICT", message = "Key reference already exists"))
        }
        records[key] = record
        return Ok(record)
    }

    override suspend fun upsert(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> {
        val key = key(record.tenantId, record.alias, record.providerId)
        val existing = records[key]
        // Match durable SQLDelight ownership: walletUnitId is immutable once written. A later
        // upsert without owner must not erase the authoritative binding SoftwareWscd requires.
        val persisted =
            if (existing == null) {
                record
            } else {
                record.copy(id = existing.id, walletUnitId = existing.walletUnitId ?: record.walletUnitId)
            }
        records[key] = persisted
        return Ok(persisted)
    }

    override suspend fun findById(tenantId: String, id: String): IdkResult<KeyReferenceRecord?, IdkError> =
        Ok(records.values.firstOrNull { it.tenantId == tenantId && it.id == id && it.deletedAt == null })

    override suspend fun findByKid(tenantId: String, kid: String, providerId: String?): IdkResult<KeyReferenceRecord?, IdkError> =
        Ok(records.values.firstOrNull { it.tenantId == tenantId && it.kid == kid && it.deletedAt == null && (providerId == null || it.providerId == providerId) })

    override suspend fun findByAlias(tenantId: String, alias: String, providerId: String?): IdkResult<KeyReferenceRecord?, IdkError> =
        Ok(records.values.firstOrNull { it.tenantId == tenantId && it.alias == alias && it.deletedAt == null && (providerId == null || it.providerId == providerId) })

    override suspend fun findAll(tenantId: String, filter: ManagedKeyReferenceFilter?): IdkResult<List<KeyReferenceRecord>, IdkError> =
        Ok(
            records.values.filter { record ->
                record.tenantId == tenantId && record.deletedAt == null &&
                    (filter?.providerId == null || record.providerId == filter.providerId) &&
                    (filter?.alias == null || record.alias == filter.alias) &&
                    (filter?.kid == null || record.kid == filter.kid) &&
                    (filter?.origin == null || record.origin == filter.origin) &&
                    (filter?.keyType == null || record.keyType == filter.keyType)
            },
        )

    override suspend fun delete(tenantId: String, alias: String, providerId: String): IdkResult<Boolean, IdkError> {
        val key = key(tenantId, alias, providerId)
        val record = records[key] ?: return Ok(false)
        records[key] = record.copy(deletedAt = record.updatedAt, deletedById = "test")
        return Ok(true)
    }

    override suspend fun deleteByKid(tenantId: String, kid: String, providerId: String?): IdkResult<Boolean, IdkError> {
        val matches = records.filter { (_, record) -> record.tenantId == tenantId && record.kid == kid && record.deletedAt == null && (providerId == null || record.providerId == providerId) }
        matches.forEach { (key, record) -> records[key] = record.copy(deletedAt = record.updatedAt, deletedById = "test") }
        return Ok(matches.isNotEmpty())
    }

    override suspend fun exists(tenantId: String, alias: String, providerId: String): IdkResult<Boolean, IdkError> =
        Ok(records.values.any { it.tenantId == tenantId && it.alias == alias && it.providerId == providerId && it.deletedAt == null })

    private fun key(tenantId: String, alias: String, providerId: String): String = "$tenantId\u0000$alias\u0000$providerId"
}
