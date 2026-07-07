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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.data.store.credential.design.CredentialDesignService
import com.sphereon.data.store.credential.design.command.GetDesignAssetArgs
import com.sphereon.data.store.credential.design.command.GetDesignAssetByHashArgs
import com.sphereon.data.store.credential.design.command.GetDesignAssetByHashServiceCommand
import com.sphereon.data.store.credential.design.command.GetDesignAssetServiceCommand
import com.sphereon.data.store.credential.design.command.ListDesignAssetsArgs
import com.sphereon.data.store.credential.design.command.ListDesignAssetsServiceCommand
import com.sphereon.data.store.credential.design.command.UploadDesignAssetArgs
import com.sphereon.data.store.credential.design.command.UploadDesignAssetServiceCommand
import com.sphereon.data.store.credential.design.command.UploadTenantAssetArgs
import com.sphereon.data.store.credential.design.command.UploadTenantAssetServiceCommand
import com.sphereon.data.store.asset.model.AssetInfo
import com.sphereon.data.store.asset.model.AssetReference
import com.sphereon.data.store.credential.design.model.ResolvedDesignAsset
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.uuid.ExperimentalUuidApi

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UploadDesignAssetServiceCommand>())
class UploadDesignAssetServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<UploadDesignAssetArgs, AssetReference, IdkError>(
        commandId = UploadDesignAssetServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UploadDesignAssetArgs>(),
        outputTypeToken = typeToken<AssetReference>(),
    ),
    UploadDesignAssetServiceCommand {
    override val commandId: String get() = UploadDesignAssetServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: UploadDesignAssetArgs,
        applyDuring: (UploadDesignAssetArgs) -> UploadDesignAssetArgs,
    ): IdkResult<AssetReference, IdkError> {
        val input = applyDuring(args)
        return designService.uploadDesignAsset(input.tenantId, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetDesignAssetServiceCommand>())
class GetDesignAssetServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<GetDesignAssetArgs, ResolvedDesignAsset, IdkError>(
        commandId = GetDesignAssetServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetDesignAssetArgs>(),
        outputTypeToken = typeToken<ResolvedDesignAsset>(),
    ),
    GetDesignAssetServiceCommand {
    override val commandId: String get() = GetDesignAssetServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetDesignAssetArgs,
        applyDuring: (GetDesignAssetArgs) -> GetDesignAssetArgs,
    ): IdkResult<ResolvedDesignAsset, IdkError> {
        val input = applyDuring(args)
        return designService.getDesignAsset(input.tenantId, input.input)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetDesignAssetByHashServiceCommand>())
class GetDesignAssetByHashServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<GetDesignAssetByHashArgs, ResolvedDesignAsset, IdkError>(
        commandId = GetDesignAssetByHashServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetDesignAssetByHashArgs>(),
        outputTypeToken = typeToken<ResolvedDesignAsset>(),
    ),
    GetDesignAssetByHashServiceCommand {
    override val commandId: String get() = GetDesignAssetByHashServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetDesignAssetByHashArgs,
        applyDuring: (GetDesignAssetByHashArgs) -> GetDesignAssetByHashArgs,
    ): IdkResult<ResolvedDesignAsset, IdkError> {
        val input = applyDuring(args)
        return designService.getDesignAssetByHash(input.tenantId, input.hash)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListDesignAssetsServiceCommand>())
class ListDesignAssetsServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<ListDesignAssetsArgs, List<AssetInfo>, IdkError>(
        commandId = ListDesignAssetsServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ListDesignAssetsArgs>(),
        outputTypeToken = typeToken<List<AssetInfo>>(),
    ),
    ListDesignAssetsServiceCommand {
    override val commandId: String get() = ListDesignAssetsServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ListDesignAssetsArgs,
        applyDuring: (ListDesignAssetsArgs) -> ListDesignAssetsArgs,
    ): IdkResult<List<AssetInfo>, IdkError> {
        val input = applyDuring(args)
        return designService.listDesignAssets(input.tenantId, input.filter)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UploadTenantAssetServiceCommand>())
class UploadTenantAssetServiceCommandImpl(
    execution: SessionExecution,
    private val designService: CredentialDesignService,
) : TypedServiceCommandAdapter<UploadTenantAssetArgs, AssetReference, IdkError>(
        commandId = UploadTenantAssetServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UploadTenantAssetArgs>(),
        outputTypeToken = typeToken<AssetReference>(),
    ),
    UploadTenantAssetServiceCommand {
    override val commandId: String get() = UploadTenantAssetServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: UploadTenantAssetArgs,
        applyDuring: (UploadTenantAssetArgs) -> UploadTenantAssetArgs,
    ): IdkResult<AssetReference, IdkError> {
        val input = applyDuring(args)
        return designService.uploadTenantAsset(input.tenantId, input.input)
    }
}
