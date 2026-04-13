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

package com.sphereon.data.store.schema.registry

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Lightweight schema registry service. Provides basic CRUD for schema records with
 * blob-store-backed content. No versioning — each schema has one content blob.
 *
 * EDK extends this with [versioning support][com.sphereon.data.store.schema.registry.persistence.VersionedSchemaRegistryService].
 */
interface SchemaRegistryService {
    suspend fun createSchema(
        tenantId: String,
        input: CreateSchemaInput,
    ): IdkResult<SchemaRecord, IdkError>

    suspend fun getSchema(
        tenantId: String,
        schemaId: Uuid,
    ): IdkResult<SchemaRecord, IdkError>

    suspend fun findSchemaByName(
        tenantId: String,
        namespace: String,
        name: String,
    ): IdkResult<SchemaRecord, IdkError>

    suspend fun listSchemas(
        tenantId: String,
        filter: SchemaRecordFilter = SchemaRecordFilter.DEFAULT,
    ): IdkResult<List<SchemaRecord>, IdkError>

    suspend fun updateSchema(
        tenantId: String,
        schemaId: Uuid,
        input: UpdateSchemaInput,
    ): IdkResult<SchemaRecord, IdkError>

    suspend fun deleteSchema(
        tenantId: String,
        schemaId: Uuid,
    ): IdkResult<Boolean, IdkError>

    suspend fun getContent(
        tenantId: String,
        schemaId: Uuid,
    ): IdkResult<ResolvedSchemaContent, IdkError>

    suspend fun resolveByPath(
        tenantId: String,
        namespace: String,
        name: String,
    ): IdkResult<ResolvedSchemaContent, IdkError>

    suspend fun importExternal(
        tenantId: String,
        input: ImportExternalInput,
    ): IdkResult<SchemaRecord, IdkError>

    suspend fun refreshCached(
        tenantId: String,
        schemaId: Uuid,
    ): IdkResult<SchemaRecord, IdkError>
}
