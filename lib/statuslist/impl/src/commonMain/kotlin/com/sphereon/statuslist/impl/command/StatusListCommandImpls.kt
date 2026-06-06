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

package com.sphereon.statuslist.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.pagination.Page
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.statuslist.CreateStatusListArgs
import com.sphereon.statuslist.EntryRef
import com.sphereon.statuslist.ListStatusListsArgs
import com.sphereon.statuslist.ResolveStatusArgs
import com.sphereon.statuslist.ResolvedStatus
import com.sphereon.statuslist.RevokeCredentialStatusArgs
import com.sphereon.statuslist.StatusListEntry
import com.sphereon.statuslist.StatusListErrors
import com.sphereon.statuslist.StatusListRef
import com.sphereon.statuslist.StatusListResult
import com.sphereon.statuslist.StatusListSummary
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.UpdateEntryStatusArgs
import com.sphereon.statuslist.command.CheckCredentialStatusCommand
import com.sphereon.statuslist.command.CreateStatusListCommand
import com.sphereon.statuslist.command.GetStatusListCommand
import com.sphereon.statuslist.command.GetStatusListEntryCommand
import com.sphereon.statuslist.command.GetStatusListTokenCommand
import com.sphereon.statuslist.command.ListStatusListsCommand
import com.sphereon.statuslist.command.RevokeCredentialStatusCommand
import com.sphereon.statuslist.command.UpdateCredentialStatusCommand
import com.sphereon.statuslist.spi.StatusListDriver
import com.sphereon.statuslist.spi.StatusListResolver
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(SessionScope::class)
class CreateStatusListCommandImpl(
    execution: SessionExecution,
    private val driver: StatusListDriver,
) : TypedServiceCommandAdapter<CreateStatusListArgs, StatusListResult, IdkError>(
        commandId = CreateStatusListCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateStatusListArgs>(),
        outputTypeToken = typeToken<StatusListResult>(),
    ),
    CreateStatusListCommand {
    override val commandId: String get() = CreateStatusListCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateStatusListArgs

    override suspend fun doExecute(
        args: CreateStatusListArgs,
        applyDuring: (CreateStatusListArgs) -> CreateStatusListArgs,
    ): IdkResult<StatusListResult, IdkError> {
        val effective = applyDuring(args)
        StatusListErrors.validateCreateArgs(effective)?.let { return Err(it) }
        return driver.createStatusList(effective)
    }
}

@Inject
@SingleIn(SessionScope::class)
class GetStatusListCommandImpl(
    execution: SessionExecution,
    private val driver: StatusListDriver,
) : TypedServiceCommandAdapter<StatusListRef, StatusListResult, IdkError>(
        commandId = GetStatusListCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<StatusListRef>(),
        outputTypeToken = typeToken<StatusListResult>(),
    ),
    GetStatusListCommand {
    override val commandId: String get() = GetStatusListCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is StatusListRef

    override suspend fun doExecute(
        args: StatusListRef,
        applyDuring: (StatusListRef) -> StatusListRef,
    ): IdkResult<StatusListResult, IdkError> {
        val ref = applyDuring(args)
        val result = driver.getStatusList(ref).getOrElse { return Err(it) }
        return result?.let { Ok(it) } ?: Err(StatusListErrors.listNotFound(ref.id ?: ref.correlationId ?: "<none>"))
    }
}

@Inject
@SingleIn(SessionScope::class)
class ListStatusListsCommandImpl(
    execution: SessionExecution,
    private val driver: StatusListDriver,
) : TypedServiceCommandAdapter<ListStatusListsArgs, Page<StatusListSummary>, IdkError>(
        commandId = ListStatusListsCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ListStatusListsArgs>(),
        outputTypeToken = typeToken<Page<StatusListSummary>>(),
    ),
    ListStatusListsCommand {
    override val commandId: String get() = ListStatusListsCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ListStatusListsArgs

    override suspend fun doExecute(
        args: ListStatusListsArgs,
        applyDuring: (ListStatusListsArgs) -> ListStatusListsArgs,
    ): IdkResult<Page<StatusListSummary>, IdkError> = driver.listStatusLists(applyDuring(args))
}

@Inject
@SingleIn(SessionScope::class)
class UpdateCredentialStatusCommandImpl(
    execution: SessionExecution,
    private val driver: StatusListDriver,
) : TypedServiceCommandAdapter<UpdateEntryStatusArgs, StatusListEntry, IdkError>(
        commandId = UpdateCredentialStatusCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UpdateEntryStatusArgs>(),
        outputTypeToken = typeToken<StatusListEntry>(),
    ),
    UpdateCredentialStatusCommand {
    override val commandId: String get() = UpdateCredentialStatusCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is UpdateEntryStatusArgs

    override suspend fun doExecute(
        args: UpdateEntryStatusArgs,
        applyDuring: (UpdateEntryStatusArgs) -> UpdateEntryStatusArgs,
    ): IdkResult<StatusListEntry, IdkError> = driver.updateEntryStatus(applyDuring(args))
}

@Inject
@SingleIn(SessionScope::class)
class GetStatusListEntryCommandImpl(
    execution: SessionExecution,
    private val driver: StatusListDriver,
) : TypedServiceCommandAdapter<EntryRef, StatusListEntry, IdkError>(
        commandId = GetStatusListEntryCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<EntryRef>(),
        outputTypeToken = typeToken<StatusListEntry>(),
    ),
    GetStatusListEntryCommand {
    override val commandId: String get() = GetStatusListEntryCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is EntryRef

    override suspend fun doExecute(
        args: EntryRef,
        applyDuring: (EntryRef) -> EntryRef,
    ): IdkResult<StatusListEntry, IdkError> {
        val ref = applyDuring(args)
        val entry = driver.getEntry(ref).getOrElse { return Err(it) }
        return entry?.let { Ok(it) }
            ?: Err(
                StatusListErrors.entryNotFound(ref.entryCorrelationId ?: ref.credentialId ?: ref.statusListIndex?.toString() ?: "<none>"),
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
class GetStatusListTokenCommandImpl(
    execution: SessionExecution,
    private val driver: StatusListDriver,
) : TypedServiceCommandAdapter<StatusListRef, StatusListToken, IdkError>(
        commandId = GetStatusListTokenCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<StatusListRef>(),
        outputTypeToken = typeToken<StatusListToken>(),
    ),
    GetStatusListTokenCommand {
    override val commandId: String get() = GetStatusListTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is StatusListRef

    override suspend fun doExecute(
        args: StatusListRef,
        applyDuring: (StatusListRef) -> StatusListRef,
    ): IdkResult<StatusListToken, IdkError> {
        val ref = applyDuring(args)
        val token = driver.getStatusListToken(ref).getOrElse { return Err(it) }
        return token?.let { Ok(it) } ?: Err(StatusListErrors.listNotFound(ref.id ?: ref.correlationId ?: "<none>"))
    }
}

@Inject
@SingleIn(SessionScope::class)
class CheckCredentialStatusCommandImpl(
    execution: SessionExecution,
    private val resolver: StatusListResolver,
) : TypedServiceCommandAdapter<ResolveStatusArgs, ResolvedStatus, IdkError>(
        commandId = CheckCredentialStatusCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResolveStatusArgs>(),
        outputTypeToken = typeToken<ResolvedStatus>(),
    ),
    CheckCredentialStatusCommand {
    override val commandId: String get() = CheckCredentialStatusCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ResolveStatusArgs

    override suspend fun doExecute(
        args: ResolveStatusArgs,
        applyDuring: (ResolveStatusArgs) -> ResolveStatusArgs,
    ): IdkResult<ResolvedStatus, IdkError> = resolver.resolveStatus(applyDuring(args))
}

@Inject
@SingleIn(SessionScope::class)
class RevokeCredentialStatusCommandImpl(
    execution: SessionExecution,
    private val driver: StatusListDriver,
) : TypedServiceCommandAdapter<RevokeCredentialStatusArgs, StatusListEntry, IdkError>(
        commandId = RevokeCredentialStatusCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RevokeCredentialStatusArgs>(),
        outputTypeToken = typeToken<StatusListEntry>(),
    ),
    RevokeCredentialStatusCommand {
    override val commandId: String get() = RevokeCredentialStatusCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is RevokeCredentialStatusArgs

    override suspend fun doExecute(
        args: RevokeCredentialStatusArgs,
        applyDuring: (RevokeCredentialStatusArgs) -> RevokeCredentialStatusArgs,
    ): IdkResult<StatusListEntry, IdkError> {
        val applied = applyDuring(args)
        return driver.updateEntryStatus(UpdateEntryStatusArgs(entry = applied.entry, value = applied.action.value))
    }
}
