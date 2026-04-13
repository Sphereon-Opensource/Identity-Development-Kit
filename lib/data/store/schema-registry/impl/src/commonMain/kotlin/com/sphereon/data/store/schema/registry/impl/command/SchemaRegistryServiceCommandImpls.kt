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

package com.sphereon.data.store.schema.registry.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.data.store.schema.registry.ResolvedSchemaContent
import com.sphereon.data.store.schema.registry.SchemaRecord
import com.sphereon.data.store.schema.registry.SchemaRegistryService
import com.sphereon.data.store.schema.registry.command.CreateSchemaArgs
import com.sphereon.data.store.schema.registry.command.CreateSchemaServiceCommand
import com.sphereon.data.store.schema.registry.command.DeleteSchemaArgs
import com.sphereon.data.store.schema.registry.command.DeleteSchemaResult
import com.sphereon.data.store.schema.registry.command.DeleteSchemaServiceCommand
import com.sphereon.data.store.schema.registry.command.FindSchemaByNameArgs
import com.sphereon.data.store.schema.registry.command.FindSchemaByNameServiceCommand
import com.sphereon.data.store.schema.registry.command.GetContentArgs
import com.sphereon.data.store.schema.registry.command.GetContentServiceCommand
import com.sphereon.data.store.schema.registry.command.GetSchemaArgs
import com.sphereon.data.store.schema.registry.command.GetSchemaServiceCommand
import com.sphereon.data.store.schema.registry.command.ImportExternalArgs
import com.sphereon.data.store.schema.registry.command.ImportExternalServiceCommand
import com.sphereon.data.store.schema.registry.command.ListSchemasArgs
import com.sphereon.data.store.schema.registry.command.ListSchemasServiceCommand
import com.sphereon.data.store.schema.registry.command.RefreshCachedArgs
import com.sphereon.data.store.schema.registry.command.RefreshCachedServiceCommand
import com.sphereon.data.store.schema.registry.command.ResolveByPathArgs
import com.sphereon.data.store.schema.registry.command.ResolveByPathServiceCommand
import com.sphereon.data.store.schema.registry.command.UpdateSchemaArgs
import com.sphereon.data.store.schema.registry.command.UpdateSchemaServiceCommand
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.uuid.ExperimentalUuidApi

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateSchemaServiceCommand>())
class CreateSchemaServiceCommandImpl(
    execution: SessionExecution,
    private val schemaService: SchemaRegistryService,
) : TypedServiceCommandAdapter<CreateSchemaArgs, SchemaRecord>(
        commandId = CreateSchemaServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateSchemaArgs>(),
        outputTypeToken = typeToken<SchemaRecord>(),
    ),
    CreateSchemaServiceCommand {
    override val commandId: String get() = CreateSchemaServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateSchemaArgs,
        applyDuring: (CreateSchemaArgs) -> CreateSchemaArgs,
    ): IdkResult<SchemaRecord, IdkError> {
        val input = applyDuring(args)
        return schemaService.createSchema(input.tenantId, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetSchemaServiceCommand>())
class GetSchemaServiceCommandImpl(
    execution: SessionExecution,
    private val schemaService: SchemaRegistryService,
) : TypedServiceCommandAdapter<GetSchemaArgs, SchemaRecord>(
        commandId = GetSchemaServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetSchemaArgs>(),
        outputTypeToken = typeToken<SchemaRecord>(),
    ),
    GetSchemaServiceCommand {
    override val commandId: String get() = GetSchemaServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetSchemaArgs,
        applyDuring: (GetSchemaArgs) -> GetSchemaArgs,
    ): IdkResult<SchemaRecord, IdkError> {
        val input = applyDuring(args)
        return schemaService.getSchema(input.tenantId, input.schemaId)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FindSchemaByNameServiceCommand>())
class FindSchemaByNameServiceCommandImpl(
    execution: SessionExecution,
    private val schemaService: SchemaRegistryService,
) : TypedServiceCommandAdapter<FindSchemaByNameArgs, SchemaRecord>(
        commandId = FindSchemaByNameServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<FindSchemaByNameArgs>(),
        outputTypeToken = typeToken<SchemaRecord>(),
    ),
    FindSchemaByNameServiceCommand {
    override val commandId: String get() = FindSchemaByNameServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: FindSchemaByNameArgs,
        applyDuring: (FindSchemaByNameArgs) -> FindSchemaByNameArgs,
    ): IdkResult<SchemaRecord, IdkError> {
        val input = applyDuring(args)
        return schemaService.findSchemaByName(input.tenantId, input.namespace, input.name)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListSchemasServiceCommand>())
class ListSchemasServiceCommandImpl(
    execution: SessionExecution,
    private val schemaService: SchemaRegistryService,
) : TypedServiceCommandAdapter<ListSchemasArgs, List<SchemaRecord>>(
        commandId = ListSchemasServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ListSchemasArgs>(),
        outputTypeToken = typeToken<List<SchemaRecord>>(),
    ),
    ListSchemasServiceCommand {
    override val commandId: String get() = ListSchemasServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ListSchemasArgs,
        applyDuring: (ListSchemasArgs) -> ListSchemasArgs,
    ): IdkResult<List<SchemaRecord>, IdkError> {
        val input = applyDuring(args)
        return schemaService.listSchemas(input.tenantId, input.filter)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UpdateSchemaServiceCommand>())
class UpdateSchemaServiceCommandImpl(
    execution: SessionExecution,
    private val schemaService: SchemaRegistryService,
) : TypedServiceCommandAdapter<UpdateSchemaArgs, SchemaRecord>(
        commandId = UpdateSchemaServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UpdateSchemaArgs>(),
        outputTypeToken = typeToken<SchemaRecord>(),
    ),
    UpdateSchemaServiceCommand {
    override val commandId: String get() = UpdateSchemaServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: UpdateSchemaArgs,
        applyDuring: (UpdateSchemaArgs) -> UpdateSchemaArgs,
    ): IdkResult<SchemaRecord, IdkError> {
        val input = applyDuring(args)
        return schemaService.updateSchema(input.tenantId, input.schemaId, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeleteSchemaServiceCommand>())
class DeleteSchemaServiceCommandImpl(
    execution: SessionExecution,
    private val schemaService: SchemaRegistryService,
) : TypedServiceCommandAdapter<DeleteSchemaArgs, DeleteSchemaResult>(
        commandId = DeleteSchemaServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DeleteSchemaArgs>(),
        outputTypeToken = typeToken<DeleteSchemaResult>(),
    ),
    DeleteSchemaServiceCommand {
    override val commandId: String get() = DeleteSchemaServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DeleteSchemaArgs,
        applyDuring: (DeleteSchemaArgs) -> DeleteSchemaArgs,
    ): IdkResult<DeleteSchemaResult, IdkError> {
        val input = applyDuring(args)
        return schemaService.deleteSchema(input.tenantId, input.schemaId).fold(
            success = { Ok(DeleteSchemaResult(deleted = it)) },
            failure = { Err(it) },
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetContentServiceCommand>())
class GetContentServiceCommandImpl(
    execution: SessionExecution,
    private val schemaService: SchemaRegistryService,
) : TypedServiceCommandAdapter<GetContentArgs, ResolvedSchemaContent>(
        commandId = GetContentServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetContentArgs>(),
        outputTypeToken = typeToken<ResolvedSchemaContent>(),
    ),
    GetContentServiceCommand {
    override val commandId: String get() = GetContentServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetContentArgs,
        applyDuring: (GetContentArgs) -> GetContentArgs,
    ): IdkResult<ResolvedSchemaContent, IdkError> {
        val input = applyDuring(args)
        return schemaService.getContent(input.tenantId, input.schemaId)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ResolveByPathServiceCommand>())
class ResolveByPathServiceCommandImpl(
    execution: SessionExecution,
    private val schemaService: SchemaRegistryService,
) : TypedServiceCommandAdapter<ResolveByPathArgs, ResolvedSchemaContent>(
        commandId = ResolveByPathServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResolveByPathArgs>(),
        outputTypeToken = typeToken<ResolvedSchemaContent>(),
    ),
    ResolveByPathServiceCommand {
    override val commandId: String get() = ResolveByPathServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ResolveByPathArgs,
        applyDuring: (ResolveByPathArgs) -> ResolveByPathArgs,
    ): IdkResult<ResolvedSchemaContent, IdkError> {
        val input = applyDuring(args)
        return schemaService.resolveByPath(input.tenantId, input.namespace, input.name)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ImportExternalServiceCommand>())
class ImportExternalServiceCommandImpl(
    execution: SessionExecution,
    private val schemaService: SchemaRegistryService,
) : TypedServiceCommandAdapter<ImportExternalArgs, SchemaRecord>(
        commandId = ImportExternalServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ImportExternalArgs>(),
        outputTypeToken = typeToken<SchemaRecord>(),
    ),
    ImportExternalServiceCommand {
    override val commandId: String get() = ImportExternalServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ImportExternalArgs,
        applyDuring: (ImportExternalArgs) -> ImportExternalArgs,
    ): IdkResult<SchemaRecord, IdkError> {
        val input = applyDuring(args)
        return schemaService.importExternal(input.tenantId, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RefreshCachedServiceCommand>())
class RefreshCachedServiceCommandImpl(
    execution: SessionExecution,
    private val schemaService: SchemaRegistryService,
) : TypedServiceCommandAdapter<RefreshCachedArgs, SchemaRecord>(
        commandId = RefreshCachedServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RefreshCachedArgs>(),
        outputTypeToken = typeToken<SchemaRecord>(),
    ),
    RefreshCachedServiceCommand {
    override val commandId: String get() = RefreshCachedServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: RefreshCachedArgs,
        applyDuring: (RefreshCachedArgs) -> RefreshCachedArgs,
    ): IdkResult<SchemaRecord, IdkError> {
        val input = applyDuring(args)
        return schemaService.refreshCached(input.tenantId, input.schemaId)
    }
}
