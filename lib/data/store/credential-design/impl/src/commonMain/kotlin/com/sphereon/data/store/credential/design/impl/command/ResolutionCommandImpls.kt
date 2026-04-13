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
import com.sphereon.data.store.credential.design.command.ResolveCredentialDesignArgs
import com.sphereon.data.store.credential.design.command.ResolveCredentialDesignServiceCommand
import com.sphereon.data.store.credential.design.command.ResolveIssuerDesignArgs
import com.sphereon.data.store.credential.design.command.ResolveIssuerDesignServiceCommand
import com.sphereon.data.store.credential.design.command.ResolveVerifierDesignArgs
import com.sphereon.data.store.credential.design.command.ResolveVerifierDesignServiceCommand
import com.sphereon.data.store.credential.design.model.ResolvedCredentialDesign
import com.sphereon.data.store.credential.design.model.ResolvedIssuerDesign
import com.sphereon.data.store.credential.design.model.ResolvedVerifierDesign
import com.sphereon.data.store.credential.design.validation.resolveCredentialDesignValidator
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.konform.validation.Invalid
import kotlin.uuid.ExperimentalUuidApi

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ResolveCredentialDesignServiceCommand>())
class ResolveCredentialDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<ResolveCredentialDesignArgs, ResolvedCredentialDesign>(
        commandId = ResolveCredentialDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResolveCredentialDesignArgs>(),
        outputTypeToken = typeToken<ResolvedCredentialDesign>(),
    ),
    ResolveCredentialDesignServiceCommand {
    override val commandId: String get() = ResolveCredentialDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ResolveCredentialDesignArgs,
        applyDuring: (ResolveCredentialDesignArgs) -> ResolveCredentialDesignArgs,
    ): IdkResult<ResolvedCredentialDesign, IdkError> {
        val input = applyDuring(args)
        val validation = resolveCredentialDesignValidator(input)
        if (validation is Invalid) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Validation failed: ${validation.errors.joinToString { "${it.dataPath}: ${it.message}" }}",
                ),
            )
        }
        return designService.resolveCredentialDesign(input.tenantId, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ResolveIssuerDesignServiceCommand>())
class ResolveIssuerDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<ResolveIssuerDesignArgs, ResolvedIssuerDesign>(
        commandId = ResolveIssuerDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResolveIssuerDesignArgs>(),
        outputTypeToken = typeToken<ResolvedIssuerDesign>(),
    ),
    ResolveIssuerDesignServiceCommand {
    override val commandId: String get() = ResolveIssuerDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ResolveIssuerDesignArgs,
        applyDuring: (ResolveIssuerDesignArgs) -> ResolveIssuerDesignArgs,
    ): IdkResult<ResolvedIssuerDesign, IdkError> {
        val input = applyDuring(args)
        return designService.resolveIssuerDesign(input.tenantId, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ResolveVerifierDesignServiceCommand>())
class ResolveVerifierDesignServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<ResolveVerifierDesignArgs, ResolvedVerifierDesign>(
        commandId = ResolveVerifierDesignServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResolveVerifierDesignArgs>(),
        outputTypeToken = typeToken<ResolvedVerifierDesign>(),
    ),
    ResolveVerifierDesignServiceCommand {
    override val commandId: String get() = ResolveVerifierDesignServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ResolveVerifierDesignArgs,
        applyDuring: (ResolveVerifierDesignArgs) -> ResolveVerifierDesignArgs,
    ): IdkResult<ResolvedVerifierDesign, IdkError> {
        val input = applyDuring(args)
        return designService.resolveVerifierDesign(input.tenantId, input.input)
    }
}
