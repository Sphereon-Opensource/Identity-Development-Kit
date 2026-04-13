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
import com.sphereon.data.store.credential.design.command.CreateRenderVariantArgs
import com.sphereon.data.store.credential.design.command.CreateRenderVariantServiceCommand
import com.sphereon.data.store.credential.design.command.DeleteRenderVariantArgs
import com.sphereon.data.store.credential.design.command.DeleteRenderVariantServiceCommand
import com.sphereon.data.store.credential.design.command.GetRenderVariantArgs
import com.sphereon.data.store.credential.design.command.GetRenderVariantServiceCommand
import com.sphereon.data.store.credential.design.command.ListRenderVariantsArgs
import com.sphereon.data.store.credential.design.command.ListRenderVariantsServiceCommand
import com.sphereon.data.store.credential.design.command.UpdateRenderVariantArgs
import com.sphereon.data.store.credential.design.command.UpdateRenderVariantServiceCommand
import com.sphereon.data.store.credential.design.model.DesignFilter
import com.sphereon.data.store.credential.design.model.RenderVariantRecord
import com.sphereon.data.store.credential.design.validation.createRenderVariantValidator
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.konform.validation.Invalid
import kotlin.uuid.ExperimentalUuidApi

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateRenderVariantServiceCommand>())
class CreateRenderVariantServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<CreateRenderVariantArgs, RenderVariantRecord>(
        commandId = CreateRenderVariantServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateRenderVariantArgs>(),
        outputTypeToken = typeToken<RenderVariantRecord>(),
    ),
    CreateRenderVariantServiceCommand {
    override val commandId: String get() = CreateRenderVariantServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateRenderVariantArgs,
        applyDuring: (CreateRenderVariantArgs) -> CreateRenderVariantArgs,
    ): IdkResult<RenderVariantRecord, IdkError> {
        val input = applyDuring(args)
        val validation = createRenderVariantValidator(input)
        if (validation is Invalid) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Validation failed: ${validation.errors.joinToString { "${it.dataPath}: ${it.message}" }}",
                ),
            )
        }
        return designService.createRenderVariant(input.tenantId, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetRenderVariantServiceCommand>())
class GetRenderVariantServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<GetRenderVariantArgs, RenderVariantRecord>(
        commandId = GetRenderVariantServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetRenderVariantArgs>(),
        outputTypeToken = typeToken<RenderVariantRecord>(),
    ),
    GetRenderVariantServiceCommand {
    override val commandId: String get() = GetRenderVariantServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetRenderVariantArgs,
        applyDuring: (GetRenderVariantArgs) -> GetRenderVariantArgs,
    ): IdkResult<RenderVariantRecord, IdkError> {
        val input = applyDuring(args)
        return designService.getRenderVariant(input.tenantId, input.id)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UpdateRenderVariantServiceCommand>())
class UpdateRenderVariantServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<UpdateRenderVariantArgs, RenderVariantRecord>(
        commandId = UpdateRenderVariantServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UpdateRenderVariantArgs>(),
        outputTypeToken = typeToken<RenderVariantRecord>(),
    ),
    UpdateRenderVariantServiceCommand {
    override val commandId: String get() = UpdateRenderVariantServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: UpdateRenderVariantArgs,
        applyDuring: (UpdateRenderVariantArgs) -> UpdateRenderVariantArgs,
    ): IdkResult<RenderVariantRecord, IdkError> {
        val input = applyDuring(args)
        return designService.updateRenderVariant(input.tenantId, input.id, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeleteRenderVariantServiceCommand>())
class DeleteRenderVariantServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<DeleteRenderVariantArgs, Boolean>(
        commandId = DeleteRenderVariantServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DeleteRenderVariantArgs>(),
        outputTypeToken = typeToken<Boolean>(),
    ),
    DeleteRenderVariantServiceCommand {
    override val commandId: String get() = DeleteRenderVariantServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DeleteRenderVariantArgs,
        applyDuring: (DeleteRenderVariantArgs) -> DeleteRenderVariantArgs,
    ): IdkResult<Boolean, IdkError> {
        val input = applyDuring(args)
        return designService.deleteRenderVariant(input.tenantId, input.id)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListRenderVariantsServiceCommand>())
class ListRenderVariantsServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<ListRenderVariantsArgs, List<RenderVariantRecord>>(
        commandId = ListRenderVariantsServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ListRenderVariantsArgs>(),
        outputTypeToken = typeToken<List<RenderVariantRecord>>(),
    ),
    ListRenderVariantsServiceCommand {
    override val commandId: String get() = ListRenderVariantsServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ListRenderVariantsArgs,
        applyDuring: (ListRenderVariantsArgs) -> ListRenderVariantsArgs,
    ): IdkResult<List<RenderVariantRecord>, IdkError> {
        val input = applyDuring(args)
        return designService.listRenderVariants(input.tenantId, input.filter)
    }
}
