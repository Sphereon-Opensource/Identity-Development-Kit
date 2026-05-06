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

package com.sphereon.data.store.blob.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.ListResult
import com.sphereon.data.store.blob.cas.ContentAddress
import com.sphereon.data.store.blob.cas.ContentAddressDescriptor
import com.sphereon.data.store.blob.command.BlobCopyInput
import com.sphereon.data.store.blob.command.BlobDeleteInput
import com.sphereon.data.store.blob.command.BlobDeleteOutput
import com.sphereon.data.store.blob.command.BlobGetInput
import com.sphereon.data.store.blob.command.BlobGetOutput
import com.sphereon.data.store.blob.command.BlobListInput
import com.sphereon.data.store.blob.command.BlobMoveInput
import com.sphereon.data.store.blob.command.BlobPutInput
import com.sphereon.data.store.blob.command.BlobStatInput
import com.sphereon.data.store.blob.command.CasGetInput
import com.sphereon.data.store.blob.command.CasStoreInput
import com.sphereon.data.store.blob.command.CasVerifyInput
import com.sphereon.data.store.blob.command.CasVerifyOutput
import com.sphereon.data.store.blob.command.MetadataSearchInput
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.native.ObjCName

// ================================================================================================
// blob.store.put
// ================================================================================================

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobStorePutCommand", exact = true)
class BlobStorePutCommand(
    execution: SessionExecution,
    private val blobService: BlobService,
) : TypedServiceCommandAdapter<BlobPutInput, BlobDescriptor, IdkError>(
        commandId = COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<BlobPutInput>(),
        outputTypeToken = typeToken<BlobDescriptor>(),
    ) {
    override val actionType: ActionType get() = ActionType.CREATE

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun doExecute(
        args: BlobPutInput,
        applyDuring: (BlobPutInput) -> BlobPutInput,
    ): IdkResult<BlobDescriptor, IdkError> {
        val input = applyDuring(args)
        val data = Base64.decode(input.dataBase64)
        return blobService.storeBlob(
            target = input.target,
            data = data,
            options = input.options,
        )
    }

    companion object {
        const val COMMAND_ID = "blob.store.put"
    }
}

// ================================================================================================
// blob.store.get
// ================================================================================================

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobStoreGetCommand", exact = true)
class BlobStoreGetCommand(
    execution: SessionExecution,
    private val blobService: BlobService,
) : TypedServiceCommandAdapter<BlobGetInput, BlobGetOutput, IdkError>(
        commandId = COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<BlobGetInput>(),
        outputTypeToken = typeToken<BlobGetOutput>(),
    ) {
    override val actionType: ActionType get() = ActionType.READ

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun doExecute(
        args: BlobGetInput,
        applyDuring: (BlobGetInput) -> BlobGetInput,
    ): IdkResult<BlobGetOutput, IdkError> {
        val input = applyDuring(args)
        val result = blobService.getBlob(input.info)
        if (result.isErr) {
            return Err(result.error)
        }
        val resolved = result.value
        return Ok(
            BlobGetOutput(
                dataBase64 = resolved.dataBase64,
                path = resolved.path ?: input.info.path ?: "",
                storeId = resolved.storeId ?: input.info.storeId ?: blobService.defaultStoreId(),
                sizeBytes = resolved.sizeBytes,
                contentType = resolved.contentType,
            ),
        )
    }

    companion object {
        const val COMMAND_ID = "blob.store.get"
    }
}

// ================================================================================================
// blob.store.delete
// ================================================================================================

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobStoreDeleteCommand", exact = true)
class BlobStoreDeleteCommand(
    execution: SessionExecution,
    private val blobService: BlobService,
) : TypedServiceCommandAdapter<BlobDeleteInput, BlobDeleteOutput, IdkError>(
        commandId = COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<BlobDeleteInput>(),
        outputTypeToken = typeToken<BlobDeleteOutput>(),
    ) {
    override val actionType: ActionType get() = ActionType.DELETE

    override suspend fun doExecute(
        args: BlobDeleteInput,
        applyDuring: (BlobDeleteInput) -> BlobDeleteInput,
    ): IdkResult<BlobDeleteOutput, IdkError> {
        val input = applyDuring(args)
        val result = blobService.deleteBlob(input.info)
        if (result.isErr) {
            return Err(result.error)
        }
        return Ok(BlobDeleteOutput(deleted = result.value))
    }

    companion object {
        const val COMMAND_ID = "blob.store.delete"
    }
}

// ================================================================================================
// blob.store.stat
// ================================================================================================

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobStoreStatCommand", exact = true)
class BlobStoreStatCommand(
    execution: SessionExecution,
    private val blobService: BlobService,
) : TypedServiceCommandAdapter<BlobStatInput, BlobDescriptor, IdkError>(
        commandId = COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<BlobStatInput>(),
        outputTypeToken = typeToken<BlobDescriptor>(),
    ) {
    override val actionType: ActionType get() = ActionType.READ

    override suspend fun doExecute(
        args: BlobStatInput,
        applyDuring: (BlobStatInput) -> BlobStatInput,
    ): IdkResult<BlobDescriptor, IdkError> {
        val input = applyDuring(args)
        return blobService.getBlobInfo(input.info)
    }

    companion object {
        const val COMMAND_ID = "blob.store.stat"
    }
}

// ================================================================================================
// blob.store.list
// ================================================================================================

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobStoreListCommand", exact = true)
class BlobStoreListCommand(
    execution: SessionExecution,
    private val blobService: BlobService,
) : TypedServiceCommandAdapter<BlobListInput, ListResult, IdkError>(
        commandId = COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<BlobListInput>(),
        outputTypeToken = typeToken<ListResult>(),
    ) {
    override val actionType: ActionType get() = ActionType.LIST

    override suspend fun doExecute(
        args: BlobListInput,
        applyDuring: (BlobListInput) -> BlobListInput,
    ): IdkResult<ListResult, IdkError> {
        val input = applyDuring(args)
        return blobService.listBlobs(
            info = input.info,
            options = input.options,
        )
    }

    companion object {
        const val COMMAND_ID = "blob.store.list"
    }
}

// ================================================================================================
// blob.store.copy
// ================================================================================================

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobStoreCopyCommand", exact = true)
class BlobStoreCopyCommand(
    execution: SessionExecution,
    private val blobService: BlobService,
) : TypedServiceCommandAdapter<BlobCopyInput, BlobDescriptor, IdkError>(
        commandId = COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<BlobCopyInput>(),
        outputTypeToken = typeToken<BlobDescriptor>(),
    ) {
    override val actionType: ActionType get() = ActionType.CREATE

    override suspend fun doExecute(
        args: BlobCopyInput,
        applyDuring: (BlobCopyInput) -> BlobCopyInput,
    ): IdkResult<BlobDescriptor, IdkError> {
        val input = applyDuring(args)
        return blobService.copyBlob(
            source = input.source,
            destination = input.destination,
        )
    }

    companion object {
        const val COMMAND_ID = "blob.store.copy"
    }
}

// ================================================================================================
// blob.store.move
// ================================================================================================

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobStoreMoveCommand", exact = true)
class BlobStoreMoveCommand(
    execution: SessionExecution,
    private val blobService: BlobService,
) : TypedServiceCommandAdapter<BlobMoveInput, BlobDescriptor, IdkError>(
        commandId = COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<BlobMoveInput>(),
        outputTypeToken = typeToken<BlobDescriptor>(),
    ) {
    override val actionType: ActionType get() = ActionType.UPDATE

    override suspend fun doExecute(
        args: BlobMoveInput,
        applyDuring: (BlobMoveInput) -> BlobMoveInput,
    ): IdkResult<BlobDescriptor, IdkError> {
        val input = applyDuring(args)
        return blobService.moveBlob(
            source = input.source,
            destination = input.destination,
        )
    }

    companion object {
        const val COMMAND_ID = "blob.store.move"
    }
}

// ================================================================================================
// blob.cas.store
// ================================================================================================

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CasStoreCommand", exact = true)
class CasStoreCommand(
    execution: SessionExecution,
    private val blobService: BlobService,
) : TypedServiceCommandAdapter<CasStoreInput, ContentAddressDescriptor, IdkError>(
        commandId = COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CasStoreInput>(),
        outputTypeToken = typeToken<ContentAddressDescriptor>(),
    ) {
    override val actionType: ActionType get() = ActionType.CREATE

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun doExecute(
        args: CasStoreInput,
        applyDuring: (CasStoreInput) -> CasStoreInput,
    ): IdkResult<ContentAddressDescriptor, IdkError> {
        val input = applyDuring(args)
        val data = Base64.decode(input.dataBase64)
        return blobService.casStore(
            info = input.info,
            data = data,
            algorithm = input.algorithm,
        )
    }

    companion object {
        const val COMMAND_ID = "blob.cas.store"
    }
}

// ================================================================================================
// blob.cas.get
// ================================================================================================

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CasGetCommand", exact = true)
class CasGetCommand(
    execution: SessionExecution,
    private val blobService: BlobService,
) : TypedServiceCommandAdapter<CasGetInput, BlobGetOutput, IdkError>(
        commandId = COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CasGetInput>(),
        outputTypeToken = typeToken<BlobGetOutput>(),
    ) {
    override val actionType: ActionType get() = ActionType.READ

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun doExecute(
        args: CasGetInput,
        applyDuring: (CasGetInput) -> CasGetInput,
    ): IdkResult<BlobGetOutput, IdkError> {
        val input = applyDuring(args)
        val address = ContentAddress.fromMultibaseString(input.addressMultibase)
        val result =
            blobService.casGet(
                info = input.info,
                address = address,
            )
        if (result.isErr) {
            return Err(result.error)
        }
        val resolved = result.value
        return Ok(
            BlobGetOutput(
                dataBase64 = resolved.dataBase64,
                path = resolved.path ?: "",
                storeId = resolved.storeId ?: input.info.storeId ?: blobService.defaultStoreId(),
                sizeBytes = resolved.sizeBytes,
                contentType = resolved.contentType,
            ),
        )
    }

    companion object {
        const val COMMAND_ID = "blob.cas.get"
    }
}

// ================================================================================================
// blob.cas.verify
// ================================================================================================

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CasVerifyCommand", exact = true)
class CasVerifyCommand(
    execution: SessionExecution,
    private val blobService: BlobService,
) : TypedServiceCommandAdapter<CasVerifyInput, CasVerifyOutput, IdkError>(
        commandId = COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CasVerifyInput>(),
        outputTypeToken = typeToken<CasVerifyOutput>(),
    ) {
    override val actionType: ActionType get() = ActionType.READ

    override suspend fun doExecute(
        args: CasVerifyInput,
        applyDuring: (CasVerifyInput) -> CasVerifyInput,
    ): IdkResult<CasVerifyOutput, IdkError> {
        val input = applyDuring(args)
        val address = ContentAddress.fromMultibaseString(input.addressMultibase)
        val result =
            blobService.casVerify(
                info = input.info,
                address = address,
            )
        if (result.isErr) {
            return Err(result.error)
        }
        return Ok(CasVerifyOutput(valid = result.value))
    }

    companion object {
        const val COMMAND_ID = "blob.cas.verify"
    }
}

// ================================================================================================
// blob.metadata.search
// ================================================================================================

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("MetadataSearchCommand", exact = true)
class MetadataSearchCommand(
    execution: SessionExecution,
    private val blobService: BlobService,
) : TypedServiceCommandAdapter<MetadataSearchInput, List<BlobDescriptor>, IdkError>(
        commandId = COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<MetadataSearchInput>(),
        outputTypeToken = typeToken<List<BlobDescriptor>>(),
    ) {
    override val actionType: ActionType get() = ActionType.LIST

    override suspend fun doExecute(
        args: MetadataSearchInput,
        applyDuring: (MetadataSearchInput) -> MetadataSearchInput,
    ): IdkResult<List<BlobDescriptor>, IdkError> {
        val input = applyDuring(args)
        return blobService.findByMetadata(
            info = input.info,
            query = input.query,
        )
    }

    companion object {
        const val COMMAND_ID = "blob.metadata.search"
    }
}
