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

package com.sphereon.data.store.schema.registry.command

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.store.schema.registry.CreateSchemaInput
import com.sphereon.data.store.schema.registry.ImportExternalInput
import com.sphereon.data.store.schema.registry.SchemaRecordFilter
import com.sphereon.data.store.schema.registry.UpdateSchemaInput
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JsExportCompat
@Serializable
data class CreateSchemaArgs(
    val tenantId: String,
    val input: CreateSchemaInput,
)

@JsExportCompat
@Serializable
data class GetSchemaArgs(
    val tenantId: String,
    val schemaId: Uuid,
)

@JsExportCompat
@Serializable
data class FindSchemaByNameArgs(
    val tenantId: String,
    val namespace: String,
    val name: String,
)

@JsExportCompat
@Serializable
data class ListSchemasArgs
    @JvmOverloads
    constructor(
        val tenantId: String,
        val filter: SchemaRecordFilter = SchemaRecordFilter.DEFAULT,
    )

@JsExportCompat
@Serializable
data class UpdateSchemaArgs(
    val tenantId: String,
    val schemaId: Uuid,
    val input: UpdateSchemaInput,
)

@JsExportCompat
@Serializable
data class DeleteSchemaArgs(
    val tenantId: String,
    val schemaId: Uuid,
)

@JsExportCompat
@Serializable
data class DeleteSchemaResult(
    val deleted: Boolean,
)

@JsExportCompat
@Serializable
data class GetContentArgs(
    val tenantId: String,
    val schemaId: Uuid,
)

@JsExportCompat
@Serializable
data class ResolveByPathArgs(
    val tenantId: String,
    val namespace: String,
    val name: String,
)

@JsExportCompat
@Serializable
data class ImportExternalArgs(
    val tenantId: String,
    val input: ImportExternalInput,
)

@JsExportCompat
@Serializable
data class RefreshCachedArgs(
    val tenantId: String,
    val schemaId: Uuid,
)
