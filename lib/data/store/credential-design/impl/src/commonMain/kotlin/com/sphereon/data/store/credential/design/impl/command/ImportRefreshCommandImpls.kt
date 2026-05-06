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
import com.sphereon.data.store.credential.design.command.GetSourceSnapshotArgs
import com.sphereon.data.store.credential.design.command.GetSourceSnapshotServiceCommand
import com.sphereon.data.store.credential.design.command.ImportCredentialDesignServiceCommand
import com.sphereon.data.store.credential.design.command.ImportExternalDesignArgs
import com.sphereon.data.store.credential.design.command.ImportIssuerDesignServiceCommand
import com.sphereon.data.store.credential.design.command.ImportVerifierDesignServiceCommand
import com.sphereon.data.store.credential.design.command.RefreshCredentialDesignServiceCommand
import com.sphereon.data.store.credential.design.command.RefreshDesignArgs
import com.sphereon.data.store.credential.design.command.RefreshIssuerDesignServiceCommand
import com.sphereon.data.store.credential.design.command.RefreshSourceSnapshotArgs
import com.sphereon.data.store.credential.design.command.RefreshSourceSnapshotServiceCommand
import com.sphereon.data.store.credential.design.command.RefreshVerifierDesignServiceCommand
import com.sphereon.data.store.credential.design.model.CredentialDesignRecord
import com.sphereon.data.store.credential.design.model.IssuerDesignRecord
import com.sphereon.data.store.credential.design.model.SourceSnapshotRecord
import com.sphereon.data.store.credential.design.model.VerifierDesignRecord
import com.sphereon.data.store.credential.design.validation.importExternalDesignValidator
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.konform.validation.Invalid
import kotlin.uuid.ExperimentalUuidApi

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ImportCredentialDesignServiceCommand>())
class ImportCredentialDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<ImportExternalDesignArgs, CredentialDesignRecord, IdkError>(
        commandId = ImportCredentialDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ImportExternalDesignArgs>(),
        outputTypeToken = typeToken<CredentialDesignRecord>(),
    ),
    ImportCredentialDesignServiceCommand {
    override val commandId: String get() = ImportCredentialDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ImportExternalDesignArgs,
        applyDuring: (ImportExternalDesignArgs) -> ImportExternalDesignArgs,
    ): IdkResult<CredentialDesignRecord, IdkError> {
        val input = applyDuring(args)
        val validation = importExternalDesignValidator(input)
        if (validation is Invalid) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Validation failed: ${validation.errors.joinToString { "${it.dataPath}: ${it.message}" }}",
                ),
            )
        }
        return designService.importExternalDesign(input.tenantId, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ImportIssuerDesignServiceCommand>())
class ImportIssuerDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<ImportExternalDesignArgs, IssuerDesignRecord, IdkError>(
        commandId = ImportIssuerDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ImportExternalDesignArgs>(),
        outputTypeToken = typeToken<IssuerDesignRecord>(),
    ),
    ImportIssuerDesignServiceCommand {
    override val commandId: String get() = ImportIssuerDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ImportExternalDesignArgs,
        applyDuring: (ImportExternalDesignArgs) -> ImportExternalDesignArgs,
    ): IdkResult<IssuerDesignRecord, IdkError> {
        val input = applyDuring(args)
        val validation = importExternalDesignValidator(input)
        if (validation is Invalid) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Validation failed: ${validation.errors.joinToString { "${it.dataPath}: ${it.message}" }}",
                ),
            )
        }
        return designService.importIssuerDesign(input.tenantId, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ImportVerifierDesignServiceCommand>())
class ImportVerifierDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<ImportExternalDesignArgs, VerifierDesignRecord, IdkError>(
        commandId = ImportVerifierDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ImportExternalDesignArgs>(),
        outputTypeToken = typeToken<VerifierDesignRecord>(),
    ),
    ImportVerifierDesignServiceCommand {
    override val commandId: String get() = ImportVerifierDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ImportExternalDesignArgs,
        applyDuring: (ImportExternalDesignArgs) -> ImportExternalDesignArgs,
    ): IdkResult<VerifierDesignRecord, IdkError> {
        val input = applyDuring(args)
        val validation = importExternalDesignValidator(input)
        if (validation is Invalid) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Validation failed: ${validation.errors.joinToString { "${it.dataPath}: ${it.message}" }}",
                ),
            )
        }
        return designService.importVerifierDesign(input.tenantId, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RefreshCredentialDesignServiceCommand>())
class RefreshCredentialDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<RefreshDesignArgs, CredentialDesignRecord, IdkError>(
        commandId = RefreshCredentialDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RefreshDesignArgs>(),
        outputTypeToken = typeToken<CredentialDesignRecord>(),
    ),
    RefreshCredentialDesignServiceCommand {
    override val commandId: String get() = RefreshCredentialDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: RefreshDesignArgs,
        applyDuring: (RefreshDesignArgs) -> RefreshDesignArgs,
    ): IdkResult<CredentialDesignRecord, IdkError> {
        val input = applyDuring(args)
        return designService.refreshCredentialDesign(input.tenantId, input.designId)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RefreshIssuerDesignServiceCommand>())
class RefreshIssuerDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<RefreshDesignArgs, IssuerDesignRecord, IdkError>(
        commandId = RefreshIssuerDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RefreshDesignArgs>(),
        outputTypeToken = typeToken<IssuerDesignRecord>(),
    ),
    RefreshIssuerDesignServiceCommand {
    override val commandId: String get() = RefreshIssuerDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: RefreshDesignArgs,
        applyDuring: (RefreshDesignArgs) -> RefreshDesignArgs,
    ): IdkResult<IssuerDesignRecord, IdkError> {
        val input = applyDuring(args)
        return designService.refreshIssuerDesign(input.tenantId, input.designId)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RefreshVerifierDesignServiceCommand>())
class RefreshVerifierDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<RefreshDesignArgs, VerifierDesignRecord, IdkError>(
        commandId = RefreshVerifierDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RefreshDesignArgs>(),
        outputTypeToken = typeToken<VerifierDesignRecord>(),
    ),
    RefreshVerifierDesignServiceCommand {
    override val commandId: String get() = RefreshVerifierDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: RefreshDesignArgs,
        applyDuring: (RefreshDesignArgs) -> RefreshDesignArgs,
    ): IdkResult<VerifierDesignRecord, IdkError> {
        val input = applyDuring(args)
        return designService.refreshVerifierDesign(input.tenantId, input.designId)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetSourceSnapshotServiceCommand>())
class GetSourceSnapshotServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<GetSourceSnapshotArgs, SourceSnapshotRecord, IdkError>(
        commandId = GetSourceSnapshotServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetSourceSnapshotArgs>(),
        outputTypeToken = typeToken<SourceSnapshotRecord>(),
    ),
    GetSourceSnapshotServiceCommand {
    override val commandId: String get() = GetSourceSnapshotServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetSourceSnapshotArgs,
        applyDuring: (GetSourceSnapshotArgs) -> GetSourceSnapshotArgs,
    ): IdkResult<SourceSnapshotRecord, IdkError> {
        val input = applyDuring(args)
        return designService.getSourceSnapshot(input.tenantId, input.snapshotId)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RefreshSourceSnapshotServiceCommand>())
class RefreshSourceSnapshotServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<RefreshSourceSnapshotArgs, SourceSnapshotRecord, IdkError>(
        commandId = RefreshSourceSnapshotServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RefreshSourceSnapshotArgs>(),
        outputTypeToken = typeToken<SourceSnapshotRecord>(),
    ),
    RefreshSourceSnapshotServiceCommand {
    override val commandId: String get() = RefreshSourceSnapshotServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: RefreshSourceSnapshotArgs,
        applyDuring: (RefreshSourceSnapshotArgs) -> RefreshSourceSnapshotArgs,
    ): IdkResult<SourceSnapshotRecord, IdkError> {
        val input = applyDuring(args)
        return designService.refreshSourceSnapshot(input.tenantId, input.snapshotId)
    }
}
