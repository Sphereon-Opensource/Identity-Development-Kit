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
import com.sphereon.data.store.credential.design.command.CreateVerifierDesignArgs
import com.sphereon.data.store.credential.design.command.CreateVerifierDesignServiceCommand
import com.sphereon.data.store.credential.design.command.DeleteDesignArgs
import com.sphereon.data.store.credential.design.command.DeleteVerifierDesignServiceCommand
import com.sphereon.data.store.credential.design.command.FindByBindingArgs
import com.sphereon.data.store.credential.design.command.FindByBindingKeyArgs
import com.sphereon.data.store.credential.design.command.FindVerifierDesignByBindingKeyServiceCommand
import com.sphereon.data.store.credential.design.command.FindVerifierDesignByBindingServiceCommand
import com.sphereon.data.store.credential.design.command.GetDesignArgs
import com.sphereon.data.store.credential.design.command.GetVerifierDesignServiceCommand
import com.sphereon.data.store.credential.design.command.ListDesignsArgs
import com.sphereon.data.store.credential.design.command.ListVerifierDesignsServiceCommand
import com.sphereon.data.store.credential.design.command.UpdateVerifierDesignArgs
import com.sphereon.data.store.credential.design.command.UpdateVerifierDesignServiceCommand
import com.sphereon.data.store.credential.design.config.CredentialDesignConfigProvider
import com.sphereon.data.store.credential.design.model.VerifierDesignRecord
import com.sphereon.data.store.credential.design.validation.createVerifierDesignValidator
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.konform.validation.Invalid
import kotlin.uuid.ExperimentalUuidApi

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateVerifierDesignServiceCommand>())
class CreateVerifierDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
    private val configProvider: CredentialDesignConfigProvider,
) : TypedServiceCommandAdapter<CreateVerifierDesignArgs, VerifierDesignRecord>(
        commandId = CreateVerifierDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateVerifierDesignArgs>(),
        outputTypeToken = typeToken<VerifierDesignRecord>(),
    ),
    CreateVerifierDesignServiceCommand {
    override val commandId: String get() = CreateVerifierDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateVerifierDesignArgs,
        applyDuring: (CreateVerifierDesignArgs) -> CreateVerifierDesignArgs,
    ): IdkResult<VerifierDesignRecord, IdkError> {
        val input = applyDuring(args)
        val validationConfig = configProvider.getValidationConfig()
        val validation = createVerifierDesignValidator(validationConfig)(input)
        if (validation is Invalid) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Validation failed: ${validation.errors.joinToString { "${it.dataPath}: ${it.message}" }}",
                ),
            )
        }
        return designService.createVerifierDesign(input.tenantId, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetVerifierDesignServiceCommand>())
class GetVerifierDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<GetDesignArgs, VerifierDesignRecord>(
        commandId = GetVerifierDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetDesignArgs>(),
        outputTypeToken = typeToken<VerifierDesignRecord>(),
    ),
    GetVerifierDesignServiceCommand {
    override val commandId: String get() = GetVerifierDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetDesignArgs,
        applyDuring: (GetDesignArgs) -> GetDesignArgs,
    ): IdkResult<VerifierDesignRecord, IdkError> {
        val input = applyDuring(args)
        return designService.getVerifierDesign(input.tenantId, input.id)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FindVerifierDesignByBindingServiceCommand>())
class FindVerifierDesignByBindingServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<FindByBindingArgs, List<VerifierDesignRecord>>(
        commandId = FindVerifierDesignByBindingServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<FindByBindingArgs>(),
        outputTypeToken = typeToken<List<VerifierDesignRecord>>(),
    ),
    FindVerifierDesignByBindingServiceCommand {
    override val commandId: String get() = FindVerifierDesignByBindingServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: FindByBindingArgs,
        applyDuring: (FindByBindingArgs) -> FindByBindingArgs,
    ): IdkResult<List<VerifierDesignRecord>, IdkError> {
        val input = applyDuring(args)
        return designService.findVerifierDesignByBinding(input.tenantId, input.binding)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FindVerifierDesignByBindingKeyServiceCommand>())
class FindVerifierDesignByBindingKeyServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<FindByBindingKeyArgs, List<VerifierDesignRecord>>(
        commandId = FindVerifierDesignByBindingKeyServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<FindByBindingKeyArgs>(),
        outputTypeToken = typeToken<List<VerifierDesignRecord>>(),
    ),
    FindVerifierDesignByBindingKeyServiceCommand {
    override val commandId: String get() = FindVerifierDesignByBindingKeyServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: FindByBindingKeyArgs,
        applyDuring: (FindByBindingKeyArgs) -> FindByBindingKeyArgs,
    ): IdkResult<List<VerifierDesignRecord>, IdkError> {
        val input = applyDuring(args)
        return designService.findVerifierDesignByBindingKey(input.tenantId, input.bindingKey, input.bindingValue)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListVerifierDesignsServiceCommand>())
class ListVerifierDesignsServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<ListDesignsArgs, List<VerifierDesignRecord>>(
        commandId = ListVerifierDesignsServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ListDesignsArgs>(),
        outputTypeToken = typeToken<List<VerifierDesignRecord>>(),
    ),
    ListVerifierDesignsServiceCommand {
    override val commandId: String get() = ListVerifierDesignsServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ListDesignsArgs,
        applyDuring: (ListDesignsArgs) -> ListDesignsArgs,
    ): IdkResult<List<VerifierDesignRecord>, IdkError> {
        val input = applyDuring(args)
        return designService.listVerifierDesigns(input.tenantId, input.filter)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UpdateVerifierDesignServiceCommand>())
class UpdateVerifierDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<UpdateVerifierDesignArgs, VerifierDesignRecord>(
        commandId = UpdateVerifierDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UpdateVerifierDesignArgs>(),
        outputTypeToken = typeToken<VerifierDesignRecord>(),
    ),
    UpdateVerifierDesignServiceCommand {
    override val commandId: String get() = UpdateVerifierDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: UpdateVerifierDesignArgs,
        applyDuring: (UpdateVerifierDesignArgs) -> UpdateVerifierDesignArgs,
    ): IdkResult<VerifierDesignRecord, IdkError> {
        val input = applyDuring(args)
        return designService.updateVerifierDesign(input.tenantId, input.id, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeleteVerifierDesignServiceCommand>())
class DeleteVerifierDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<DeleteDesignArgs, Boolean>(
        commandId = DeleteVerifierDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DeleteDesignArgs>(),
        outputTypeToken = typeToken<Boolean>(),
    ),
    DeleteVerifierDesignServiceCommand {
    override val commandId: String get() = DeleteVerifierDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DeleteDesignArgs,
        applyDuring: (DeleteDesignArgs) -> DeleteDesignArgs,
    ): IdkResult<Boolean, IdkError> {
        val input = applyDuring(args)
        return designService.deleteVerifierDesign(input.tenantId, input.id)
    }
}
