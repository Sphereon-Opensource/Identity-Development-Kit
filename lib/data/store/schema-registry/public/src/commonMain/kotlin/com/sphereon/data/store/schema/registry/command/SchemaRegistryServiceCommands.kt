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

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.store.schema.registry.ResolvedSchemaContent
import com.sphereon.data.store.schema.registry.SchemaRecord
import kotlin.uuid.ExperimentalUuidApi

@JsExportCompat
interface CreateSchemaServiceCommand : ServiceCommand<CreateSchemaArgs, SchemaRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "schema-registry.schemas.create"
    }
}

@JsExportCompat
interface GetSchemaServiceCommand : ServiceCommand<GetSchemaArgs, SchemaRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "schema-registry.schemas.get"
    }
}

@JsExportCompat
interface FindSchemaByNameServiceCommand : ServiceCommand<FindSchemaByNameArgs, SchemaRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "schema-registry.schemas.find"
    }
}

@JsExportCompat
interface ListSchemasServiceCommand : ServiceCommand<ListSchemasArgs, List<SchemaRecord>> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "schema-registry.schemas.list"
    }
}

@JsExportCompat
interface UpdateSchemaServiceCommand : ServiceCommand<UpdateSchemaArgs, SchemaRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "schema-registry.schemas.update"
    }
}

@JsExportCompat
interface DeleteSchemaServiceCommand : ServiceCommand<DeleteSchemaArgs, DeleteSchemaResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "schema-registry.schemas.delete"
    }
}

@JsExportCompat
interface GetContentServiceCommand : ServiceCommand<GetContentArgs, ResolvedSchemaContent> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "schema-registry.content.get"
    }
}

@JsExportCompat
interface ResolveByPathServiceCommand : ServiceCommand<ResolveByPathArgs, ResolvedSchemaContent> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "schema-registry.content.resolve"
    }
}

@JsExportCompat
interface ImportExternalServiceCommand : ServiceCommand<ImportExternalArgs, SchemaRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "schema-registry.external.import"
    }
}

@JsExportCompat
interface RefreshCachedServiceCommand : ServiceCommand<RefreshCachedArgs, SchemaRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "schema-registry.external.refresh"
    }
}
