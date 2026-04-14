/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.store.schema.registry.persistence

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.store.schema.registry.SchemaRecord
import com.sphereon.data.store.schema.registry.SchemaRecordFilter
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Dialect-agnostic repository for schema record metadata.
 *
 * IDK provides a blob-store-backed implementation. EDK provides SQL implementations
 * (PostgreSQL, MySQL) that replace it via `@ContributesBinding(replaces=[...])`.
 */
@JsExportCompat
interface SchemaRecordRepository {
    suspend fun findById(
        tenantId: String,
        id: Uuid,
    ): SchemaRecord?

    suspend fun findByNamespaceName(
        tenantId: String,
        namespace: String,
        name: String,
    ): SchemaRecord?

    suspend fun findAll(
        tenantId: String,
        filter: SchemaRecordFilter = SchemaRecordFilter.DEFAULT,
    ): List<SchemaRecord>

    suspend fun create(record: SchemaRecord): SchemaRecord

    suspend fun update(record: SchemaRecord): SchemaRecord

    suspend fun delete(
        tenantId: String,
        id: Uuid,
    ): Boolean
}
