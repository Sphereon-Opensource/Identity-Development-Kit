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
import com.sphereon.data.store.credential.design.command.CreateCredentialDesignArgs
import com.sphereon.data.store.credential.design.command.CreateCredentialDesignServiceCommand
import com.sphereon.data.store.credential.design.command.DeleteCredentialDesignServiceCommand
import com.sphereon.data.store.credential.design.command.DeleteDesignArgs
import com.sphereon.data.store.credential.design.command.FindByBindingArgs
import com.sphereon.data.store.credential.design.command.FindByBindingKeyArgs
import com.sphereon.data.store.credential.design.command.FindCredentialDesignByBindingKeyServiceCommand
import com.sphereon.data.store.credential.design.command.FindCredentialDesignByBindingServiceCommand
import com.sphereon.data.store.credential.design.command.GetCredentialDesignArgs
import com.sphereon.data.store.credential.design.command.GetCredentialDesignServiceCommand
import com.sphereon.data.store.credential.design.command.ListCredentialDesignsServiceCommand
import com.sphereon.data.store.credential.design.command.ListDesignsArgs
import com.sphereon.data.store.credential.design.command.UpdateCredentialDesignArgs
import com.sphereon.data.store.credential.design.command.UpdateCredentialDesignServiceCommand
import com.sphereon.data.store.credential.design.config.CredentialDesignConfigProvider
import com.sphereon.data.store.credential.design.model.CredentialDesignRecord
import com.sphereon.data.store.credential.design.validation.createCredentialDesignValidator
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.konform.validation.Invalid
import kotlin.uuid.ExperimentalUuidApi

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateCredentialDesignServiceCommand>())
class CreateCredentialDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
    private val configProvider: CredentialDesignConfigProvider,
) : TypedServiceCommandAdapter<CreateCredentialDesignArgs, CredentialDesignRecord, IdkError>(
        commandId = CreateCredentialDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateCredentialDesignArgs>(),
        outputTypeToken = typeToken<CredentialDesignRecord>(),
    ),
    CreateCredentialDesignServiceCommand {
    override val commandId: String get() = CreateCredentialDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateCredentialDesignArgs,
        applyDuring: (CreateCredentialDesignArgs) -> CreateCredentialDesignArgs,
    ): IdkResult<CredentialDesignRecord, IdkError> {
        val input = applyDuring(args)
        val validationConfig = configProvider.getValidationConfig()
        val validation = createCredentialDesignValidator(validationConfig)(input)
        if (validation is Invalid) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Validation failed: ${validation.errors.joinToString { "${it.dataPath}: ${it.message}" }}",
                ),
            )
        }
        return designService.createCredentialDesign(input.tenantId, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetCredentialDesignServiceCommand>())
class GetCredentialDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<GetCredentialDesignArgs, CredentialDesignRecord, IdkError>(
        commandId = GetCredentialDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetCredentialDesignArgs>(),
        outputTypeToken = typeToken<CredentialDesignRecord>(),
    ),
    GetCredentialDesignServiceCommand {
    override val commandId: String get() = GetCredentialDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetCredentialDesignArgs,
        applyDuring: (GetCredentialDesignArgs) -> GetCredentialDesignArgs,
    ): IdkResult<CredentialDesignRecord, IdkError> {
        val input = applyDuring(args)
        return designService.getCredentialDesign(input.tenantId, input.id)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FindCredentialDesignByBindingServiceCommand>())
class FindCredentialDesignByBindingServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<FindByBindingArgs, List<CredentialDesignRecord>, IdkError>(
        commandId = FindCredentialDesignByBindingServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<FindByBindingArgs>(),
        outputTypeToken = typeToken<List<CredentialDesignRecord>>(),
    ),
    FindCredentialDesignByBindingServiceCommand {
    override val commandId: String get() = FindCredentialDesignByBindingServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: FindByBindingArgs,
        applyDuring: (FindByBindingArgs) -> FindByBindingArgs,
    ): IdkResult<List<CredentialDesignRecord>, IdkError> {
        val input = applyDuring(args)
        return designService.findCredentialDesignByBinding(input.tenantId, input.binding)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FindCredentialDesignByBindingKeyServiceCommand>())
class FindCredentialDesignByBindingKeyServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<FindByBindingKeyArgs, List<CredentialDesignRecord>, IdkError>(
        commandId = FindCredentialDesignByBindingKeyServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<FindByBindingKeyArgs>(),
        outputTypeToken = typeToken<List<CredentialDesignRecord>>(),
    ),
    FindCredentialDesignByBindingKeyServiceCommand {
    override val commandId: String get() = FindCredentialDesignByBindingKeyServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: FindByBindingKeyArgs,
        applyDuring: (FindByBindingKeyArgs) -> FindByBindingKeyArgs,
    ): IdkResult<List<CredentialDesignRecord>, IdkError> {
        val input = applyDuring(args)
        return designService.findCredentialDesignByBindingKey(input.tenantId, input.bindingKey, input.bindingValue)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListCredentialDesignsServiceCommand>())
class ListCredentialDesignsServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<ListDesignsArgs, List<CredentialDesignRecord>, IdkError>(
        commandId = ListCredentialDesignsServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ListDesignsArgs>(),
        outputTypeToken = typeToken<List<CredentialDesignRecord>>(),
    ),
    ListCredentialDesignsServiceCommand {
    override val commandId: String get() = ListCredentialDesignsServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ListDesignsArgs,
        applyDuring: (ListDesignsArgs) -> ListDesignsArgs,
    ): IdkResult<List<CredentialDesignRecord>, IdkError> {
        val input = applyDuring(args)
        return designService.listCredentialDesigns(input.tenantId, input.filter)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UpdateCredentialDesignServiceCommand>())
class UpdateCredentialDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<UpdateCredentialDesignArgs, CredentialDesignRecord, IdkError>(
        commandId = UpdateCredentialDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UpdateCredentialDesignArgs>(),
        outputTypeToken = typeToken<CredentialDesignRecord>(),
    ),
    UpdateCredentialDesignServiceCommand {
    override val commandId: String get() = UpdateCredentialDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: UpdateCredentialDesignArgs,
        applyDuring: (UpdateCredentialDesignArgs) -> UpdateCredentialDesignArgs,
    ): IdkResult<CredentialDesignRecord, IdkError> {
        val input = applyDuring(args)
        return designService.updateCredentialDesign(input.tenantId, input.id, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeleteCredentialDesignServiceCommand>())
class DeleteCredentialDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<DeleteDesignArgs, Boolean, IdkError>(
        commandId = DeleteCredentialDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DeleteDesignArgs>(),
        outputTypeToken = typeToken<Boolean>(),
    ),
    DeleteCredentialDesignServiceCommand {
    override val commandId: String get() = DeleteCredentialDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DeleteDesignArgs,
        applyDuring: (DeleteDesignArgs) -> DeleteDesignArgs,
    ): IdkResult<Boolean, IdkError> {
        val input = applyDuring(args)
        return designService.deleteCredentialDesign(input.tenantId, input.id)
    }
}
