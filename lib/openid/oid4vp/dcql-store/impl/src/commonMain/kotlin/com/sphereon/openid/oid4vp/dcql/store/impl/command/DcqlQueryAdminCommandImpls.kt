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
 */

package com.sphereon.openid.oid4vp.dcql.store.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.dcql.store.DcqlQueryConfigurationStore
import com.sphereon.openid.oid4vp.dcql.store.command.CreateDcqlQueryArgs
import com.sphereon.openid.oid4vp.dcql.store.command.CreateDcqlQueryServiceCommand
import com.sphereon.openid.oid4vp.dcql.store.command.DeleteDcqlQueryArgs
import com.sphereon.openid.oid4vp.dcql.store.command.DeleteDcqlQueryServiceCommand
import com.sphereon.openid.oid4vp.dcql.store.command.GetDcqlQueryArgs
import com.sphereon.openid.oid4vp.dcql.store.command.GetDcqlQueryServiceCommand
import com.sphereon.openid.oid4vp.dcql.store.command.ListDcqlQueriesArgs
import com.sphereon.openid.oid4vp.dcql.store.command.ListDcqlQueriesServiceCommand
import com.sphereon.openid.oid4vp.dcql.store.command.UpdateDcqlQueryArgs
import com.sphereon.openid.oid4vp.dcql.store.command.UpdateDcqlQueryServiceCommand
import com.sphereon.openid.oid4vp.dcql.store.model.DcqlQueryConfiguration
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Loads a stored configuration by `query_id`, failing with a NOT_FOUND error when absent.
 */
private suspend fun DcqlQueryConfigurationStore.requireByQueryId(queryId: String,): IdkResult<DcqlQueryConfiguration, IdkError> {
    val existing =
        getByQueryId(queryId).getOrElse { return Err(it) }
            ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DCQL query configuration not found: $queryId"))
    return Ok(existing)
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateDcqlQueryServiceCommand>())
class CreateDcqlQueryServiceCommandImpl(
    execution: SessionExecution,
    private val store: DcqlQueryConfigurationStore,
) : TypedServiceCommandAdapter<CreateDcqlQueryArgs, DcqlQueryConfiguration, IdkError>(
        commandId = CreateDcqlQueryServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateDcqlQueryArgs>(),
        outputTypeToken = typeToken<DcqlQueryConfiguration>(),
    ),
    CreateDcqlQueryServiceCommand {
    override val commandId: String get() = CreateDcqlQueryServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateDcqlQueryArgs,
        applyDuring: (CreateDcqlQueryArgs) -> CreateDcqlQueryArgs,
    ): IdkResult<DcqlQueryConfiguration, IdkError> {
        val input = applyDuring(args)
        val alreadyExists = store.exists(input.queryId).getOrElse { return Err(it) }
        if (alreadyExists) {
            return Err(IdkError.ALREADY_EXISTS_ERROR(message = "DCQL query configuration already exists: ${input.queryId}"))
        }
        val configuration =
            DcqlQueryConfiguration(
                queryId = input.queryId,
                name = input.name,
                description = input.description,
                dcqlQuery = input.dcqlQuery,
                enabled = input.enabled,
                createdAt = 0,
                updatedAt = 0,
            )
        store.putPersistent(input.queryId, configuration).getOrElse { return Err(it) }
        return store.requireByQueryId(input.queryId)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetDcqlQueryServiceCommand>())
class GetDcqlQueryServiceCommandImpl(
    execution: SessionExecution,
    private val store: DcqlQueryConfigurationStore,
) : TypedServiceCommandAdapter<GetDcqlQueryArgs, DcqlQueryConfiguration, IdkError>(
        commandId = GetDcqlQueryServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetDcqlQueryArgs>(),
        outputTypeToken = typeToken<DcqlQueryConfiguration>(),
    ),
    GetDcqlQueryServiceCommand {
    override val commandId: String get() = GetDcqlQueryServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetDcqlQueryArgs,
        applyDuring: (GetDcqlQueryArgs) -> GetDcqlQueryArgs,
    ): IdkResult<DcqlQueryConfiguration, IdkError> {
        val input = applyDuring(args)
        return store.requireByQueryId(input.queryId)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListDcqlQueriesServiceCommand>())
class ListDcqlQueriesServiceCommandImpl(
    execution: SessionExecution,
    private val store: DcqlQueryConfigurationStore,
) : TypedServiceCommandAdapter<ListDcqlQueriesArgs, List<DcqlQueryConfiguration>, IdkError>(
        commandId = ListDcqlQueriesServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ListDcqlQueriesArgs>(),
        outputTypeToken = typeToken<List<DcqlQueryConfiguration>>(),
    ),
    ListDcqlQueriesServiceCommand {
    override val commandId: String get() = ListDcqlQueriesServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ListDcqlQueriesArgs,
        applyDuring: (ListDcqlQueriesArgs) -> ListDcqlQueriesArgs,
    ): IdkResult<List<DcqlQueryConfiguration>, IdkError> {
        applyDuring(args)
        val all = store.getAll().getOrElse { return Err(it) }
        return Ok(all.values.sortedBy { it.queryId })
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UpdateDcqlQueryServiceCommand>())
class UpdateDcqlQueryServiceCommandImpl(
    execution: SessionExecution,
    private val store: DcqlQueryConfigurationStore,
) : TypedServiceCommandAdapter<UpdateDcqlQueryArgs, DcqlQueryConfiguration, IdkError>(
        commandId = UpdateDcqlQueryServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UpdateDcqlQueryArgs>(),
        outputTypeToken = typeToken<DcqlQueryConfiguration>(),
    ),
    UpdateDcqlQueryServiceCommand {
    override val commandId: String get() = UpdateDcqlQueryServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: UpdateDcqlQueryArgs,
        applyDuring: (UpdateDcqlQueryArgs) -> UpdateDcqlQueryArgs,
    ): IdkResult<DcqlQueryConfiguration, IdkError> {
        val input = applyDuring(args)
        val existing = store.requireByQueryId(input.queryId).getOrElse { return Err(it) }
        val merged =
            existing.copy(
                name = input.name ?: existing.name,
                description = input.description ?: existing.description,
                dcqlQuery = input.dcqlQuery ?: existing.dcqlQuery,
                enabled = input.enabled ?: existing.enabled,
            )
        store.putPersistent(input.queryId, merged).getOrElse { return Err(it) }
        return store.requireByQueryId(input.queryId)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeleteDcqlQueryServiceCommand>())
class DeleteDcqlQueryServiceCommandImpl(
    execution: SessionExecution,
    private val store: DcqlQueryConfigurationStore,
) : TypedServiceCommandAdapter<DeleteDcqlQueryArgs, Boolean, IdkError>(
        commandId = DeleteDcqlQueryServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DeleteDcqlQueryArgs>(),
        outputTypeToken = typeToken<Boolean>(),
    ),
    DeleteDcqlQueryServiceCommand {
    override val commandId: String get() = DeleteDcqlQueryServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DeleteDcqlQueryArgs,
        applyDuring: (DeleteDcqlQueryArgs) -> DeleteDcqlQueryArgs,
    ): IdkResult<Boolean, IdkError> {
        val input = applyDuring(args)
        return store.delete(input.queryId)
    }
}
