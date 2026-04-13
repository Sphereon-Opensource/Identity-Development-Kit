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

package com.sphereon.data.store.credential.design.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.data.store.credential.design.CredentialDesignService
import com.sphereon.data.store.credential.design.command.CreateIssuerDesignArgs
import com.sphereon.data.store.credential.design.command.CreateIssuerDesignServiceCommand
import com.sphereon.data.store.credential.design.command.DeleteDesignArgs
import com.sphereon.data.store.credential.design.command.DeleteIssuerDesignServiceCommand
import com.sphereon.data.store.credential.design.command.FindByBindingArgs
import com.sphereon.data.store.credential.design.command.FindByBindingKeyArgs
import com.sphereon.data.store.credential.design.command.FindIssuerDesignByBindingKeyServiceCommand
import com.sphereon.data.store.credential.design.command.FindIssuerDesignByBindingServiceCommand
import com.sphereon.data.store.credential.design.command.GetDesignArgs
import com.sphereon.data.store.credential.design.command.GetIssuerDesignServiceCommand
import com.sphereon.data.store.credential.design.command.ListDesignsArgs
import com.sphereon.data.store.credential.design.command.ListIssuerDesignsServiceCommand
import com.sphereon.data.store.credential.design.command.UpdateIssuerDesignArgs
import com.sphereon.data.store.credential.design.command.UpdateIssuerDesignServiceCommand
import com.sphereon.data.store.credential.design.config.CredentialDesignConfigProvider
import com.sphereon.data.store.credential.design.model.IssuerDesignRecord
import com.sphereon.data.store.credential.design.validation.createIssuerDesignValidator
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.konform.validation.Invalid
import kotlin.uuid.ExperimentalUuidApi

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateIssuerDesignServiceCommand>())
class CreateIssuerDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
    private val configProvider: CredentialDesignConfigProvider,
) : TypedServiceCommandAdapter<CreateIssuerDesignArgs, IssuerDesignRecord>(
        commandId = CreateIssuerDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateIssuerDesignArgs>(),
        outputTypeToken = typeToken<IssuerDesignRecord>(),
    ),
    CreateIssuerDesignServiceCommand {
    override val commandId: String get() = CreateIssuerDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateIssuerDesignArgs,
        applyDuring: (CreateIssuerDesignArgs) -> CreateIssuerDesignArgs,
    ): IdkResult<IssuerDesignRecord, IdkError> {
        val input = applyDuring(args)
        val validationConfig = configProvider.getValidationConfig()
        val validation = createIssuerDesignValidator(validationConfig)(input)
        if (validation is Invalid) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Validation failed: ${validation.errors.joinToString { "${it.dataPath}: ${it.message}" }}",
                ),
            )
        }
        return designService.createIssuerDesign(input.tenantId, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetIssuerDesignServiceCommand>())
class GetIssuerDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<GetDesignArgs, IssuerDesignRecord>(
        commandId = GetIssuerDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetDesignArgs>(),
        outputTypeToken = typeToken<IssuerDesignRecord>(),
    ),
    GetIssuerDesignServiceCommand {
    override val commandId: String get() = GetIssuerDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetDesignArgs,
        applyDuring: (GetDesignArgs) -> GetDesignArgs,
    ): IdkResult<IssuerDesignRecord, IdkError> {
        val input = applyDuring(args)
        return designService.getIssuerDesign(input.tenantId, input.id)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FindIssuerDesignByBindingServiceCommand>())
class FindIssuerDesignByBindingServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<FindByBindingArgs, List<IssuerDesignRecord>>(
        commandId = FindIssuerDesignByBindingServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<FindByBindingArgs>(),
        outputTypeToken = typeToken<List<IssuerDesignRecord>>(),
    ),
    FindIssuerDesignByBindingServiceCommand {
    override val commandId: String get() = FindIssuerDesignByBindingServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: FindByBindingArgs,
        applyDuring: (FindByBindingArgs) -> FindByBindingArgs,
    ): IdkResult<List<IssuerDesignRecord>, IdkError> {
        val input = applyDuring(args)
        return designService.findIssuerDesignByBinding(input.tenantId, input.binding)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FindIssuerDesignByBindingKeyServiceCommand>())
class FindIssuerDesignByBindingKeyServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<FindByBindingKeyArgs, List<IssuerDesignRecord>>(
        commandId = FindIssuerDesignByBindingKeyServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<FindByBindingKeyArgs>(),
        outputTypeToken = typeToken<List<IssuerDesignRecord>>(),
    ),
    FindIssuerDesignByBindingKeyServiceCommand {
    override val commandId: String get() = FindIssuerDesignByBindingKeyServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: FindByBindingKeyArgs,
        applyDuring: (FindByBindingKeyArgs) -> FindByBindingKeyArgs,
    ): IdkResult<List<IssuerDesignRecord>, IdkError> {
        val input = applyDuring(args)
        return designService.findIssuerDesignByBindingKey(input.tenantId, input.bindingKey, input.bindingValue)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListIssuerDesignsServiceCommand>())
class ListIssuerDesignsServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<ListDesignsArgs, List<IssuerDesignRecord>>(
        commandId = ListIssuerDesignsServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ListDesignsArgs>(),
        outputTypeToken = typeToken<List<IssuerDesignRecord>>(),
    ),
    ListIssuerDesignsServiceCommand {
    override val commandId: String get() = ListIssuerDesignsServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ListDesignsArgs,
        applyDuring: (ListDesignsArgs) -> ListDesignsArgs,
    ): IdkResult<List<IssuerDesignRecord>, IdkError> {
        val input = applyDuring(args)
        return designService.listIssuerDesigns(input.tenantId, input.filter)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UpdateIssuerDesignServiceCommand>())
class UpdateIssuerDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<UpdateIssuerDesignArgs, IssuerDesignRecord>(
        commandId = UpdateIssuerDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UpdateIssuerDesignArgs>(),
        outputTypeToken = typeToken<IssuerDesignRecord>(),
    ),
    UpdateIssuerDesignServiceCommand {
    override val commandId: String get() = UpdateIssuerDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: UpdateIssuerDesignArgs,
        applyDuring: (UpdateIssuerDesignArgs) -> UpdateIssuerDesignArgs,
    ): IdkResult<IssuerDesignRecord, IdkError> {
        val input = applyDuring(args)
        return designService.updateIssuerDesign(input.tenantId, input.id, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeleteIssuerDesignServiceCommand>())
class DeleteIssuerDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<DeleteDesignArgs, Boolean>(
        commandId = DeleteIssuerDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DeleteDesignArgs>(),
        outputTypeToken = typeToken<Boolean>(),
    ),
    DeleteIssuerDesignServiceCommand {
    override val commandId: String get() = DeleteIssuerDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DeleteDesignArgs,
        applyDuring: (DeleteDesignArgs) -> DeleteDesignArgs,
    ): IdkResult<Boolean, IdkError> {
        val input = applyDuring(args)
        return designService.deleteIssuerDesign(input.tenantId, input.id)
    }
}
