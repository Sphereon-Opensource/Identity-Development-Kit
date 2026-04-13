/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.crypto.kms.rest.server.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyInput
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyOutput
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GenerateKeyInput
import com.sphereon.crypto.kms.rest.api.command.GenerateKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GetKeyInput
import com.sphereon.crypto.kms.rest.api.command.GetKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ListKeysInput
import com.sphereon.crypto.kms.rest.api.command.ListKeysServiceCommand
import com.sphereon.crypto.kms.rest.api.command.StoreKeyInput
import com.sphereon.crypto.kms.rest.api.command.StoreKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.GetKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ListKeysResponse
import com.sphereon.crypto.kms.rest.api.generated.models.StoreKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.KeyOperations as KeyOperationsRest
import com.sphereon.crypto.kms.rest.api.mapper.toRest
import com.sphereon.crypto.kms.rest.api.mapper.toSdk
import com.sphereon.crypto.kms.rest.server.service.KmsRestService
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

// ========== KMS Keys Service Command Implementations ==========
//
// These are the LOCAL implementations (business logic).
// Each command provides TWO DI bindings:
//   1. Command interface single binding - for direct injection by consumers
//   2. RegistrableServiceCommand multibinding - for registry discovery
//
// The -remote module's KSP-generated routed class contributes ONLY multibinding
// (no single binding), so direct injection always resolves to the -impl.
// The config-aware registry selects between them for transport dispatching.

// ========== GetKey Service Command Implementation ==========

/**
 * LOCAL implementation of [GetKeyServiceCommand].
 *
 * GET /keys/{aliasOrKid}
 */
@Inject
@SingleIn(SessionScope::class)
class GetKeyServiceCommandImpl(
    execution: SessionExecution,
    private val kmsService: KmsRestService
) : TypedServiceCommandAdapter<GetKeyInput, GetKeyResponse>(
    commandId = GetKeyServiceCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<GetKeyInput>(),
    outputTypeToken = typeToken<GetKeyResponse>()
), GetKeyServiceCommand {

    override val commandId = GetKeyServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetKeyInput,
        applyDuring: (GetKeyInput) -> GetKeyInput
    ): IdkResult<GetKeyResponse, IdkError> {
        val input = applyDuring(args)

        val keyInfo = try {
            kmsService.getKey(input.aliasOrKid, input.providerId)
        } catch (e: Exception) {
            return Err(IdkError.NOT_FOUND_ERROR(
                message = "Key not found: ${input.aliasOrKid}",
                throwable = e
            ))
        }

        return Ok(GetKeyResponse(keyInfo = keyInfo.toRest()))
    }
}

// ========== ListKeys Service Command Implementation ==========

/**
 * LOCAL implementation of [ListKeysServiceCommand].
 *
 * GET /keys
 */
@Inject
@SingleIn(SessionScope::class)
class ListKeysServiceCommandImpl(
    execution: SessionExecution,
    private val kmsService: KmsRestService
) : TypedServiceCommandAdapter<ListKeysInput, ListKeysResponse>(
    commandId = ListKeysServiceCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ListKeysInput>(),
    outputTypeToken = typeToken<ListKeysResponse>()
), ListKeysServiceCommand {

    override val commandId = ListKeysServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ListKeysInput,
        applyDuring: (ListKeysInput) -> ListKeysInput
    ): IdkResult<ListKeysResponse, IdkError> {
        val input = applyDuring(args)

        val keyInfos = try {
            kmsService.listKeys(input.providerId).map { it.toRest() }.toTypedArray()
        } catch (e: Exception) {
            return Err(IdkError.UNKNOWN_ERROR(
                message = "Failed to list keys: ${e.message}",
                exception = e
            ))
        }

        return Ok(ListKeysResponse(keyInfos = keyInfos))
    }
}

// ========== StoreKey Service Command Implementation ==========

/**
 * LOCAL implementation of [StoreKeyServiceCommand].
 *
 * POST /keys
 */
@Inject
@SingleIn(SessionScope::class)
class StoreKeyServiceCommandImpl(
    execution: SessionExecution,
    private val kmsService: KmsRestService
) : TypedServiceCommandAdapter<StoreKeyInput, StoreKeyResponse>(
    commandId = StoreKeyServiceCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<StoreKeyInput>(),
    outputTypeToken = typeToken<StoreKeyResponse>()
), StoreKeyServiceCommand {

    override val commandId = StoreKeyServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: StoreKeyInput,
        applyDuring: (StoreKeyInput) -> StoreKeyInput
    ): IdkResult<StoreKeyResponse, IdkError> {
        val input = applyDuring(args)
        val storeKeyRequest = input.storeKey

        val key = try {
            kmsService.storeKey(
                keyInfo = storeKeyRequest.keyInfo.toSdk(),
                certChain = storeKeyRequest.certChain
            )
        } catch (e: Exception) {
            return Err(IdkError.UNKNOWN_ERROR(
                message = "Failed to store key: ${e.message}",
                exception = e
            ))
        }

        return Ok(StoreKeyResponse(keyInfo = key.toRest()))
    }
}

// ========== GenerateKey Service Command Implementation ==========

/**
 * LOCAL implementation of [GenerateKeyServiceCommand].
 *
 * POST /keys/generate
 */
@Inject
@SingleIn(SessionScope::class)
class GenerateKeyServiceCommandImpl(
    execution: SessionExecution,
    private val kmsService: KmsRestService
) : TypedServiceCommandAdapter<GenerateKeyInput, GenerateKeyResponse>(
    commandId = GenerateKeyServiceCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<GenerateKeyInput>(),
    outputTypeToken = typeToken<GenerateKeyResponse>()
), GenerateKeyServiceCommand {

    override val commandId = GenerateKeyServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GenerateKeyInput,
        applyDuring: (GenerateKeyInput) -> GenerateKeyInput
    ): IdkResult<GenerateKeyResponse, IdkError> {
        val input = applyDuring(args)
        val generateRequest = input.generateKey

        val keyPair = try {
            kmsService.generateKey(
                alias = generateRequest.alias,
                use = generateRequest.use?.let { JwkUse.valueOf(it.value) },
                keyOperations = generateRequest.keyOperations
                    ?.map<KeyOperationsRest, KeyOperations> {
                        KeyOperations.fromJose(JoseKeyOperations.valueOf(it.value.uppercase()))
                    }
                    ?.toTypedArray(),
                alg = generateRequest.alg?.let { SignatureAlgorithm.fromValue(it.value) },
                providerId = generateRequest.providerId
            )
        } catch (e: Exception) {
            return Err(IdkError.UNKNOWN_ERROR(
                message = "Failed to generate key: ${e.message}",
                exception = e
            ))
        }

        return Ok(GenerateKeyResponse(keyPair = keyPair.toRest()))
    }
}

// ========== DeleteKey Service Command Implementation ==========

/**
 * LOCAL implementation of [DeleteKeyServiceCommand].
 *
 * DELETE /keys/{aliasOrKid}
 */
@Inject
@SingleIn(SessionScope::class)
class DeleteKeyServiceCommandImpl(
    execution: SessionExecution,
    private val kmsService: KmsRestService
) : TypedServiceCommandAdapter<DeleteKeyInput, DeleteKeyOutput>(
    commandId = DeleteKeyServiceCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<DeleteKeyInput>(),
    outputTypeToken = typeToken<DeleteKeyOutput>()
), DeleteKeyServiceCommand {

    override val commandId = DeleteKeyServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DeleteKeyInput,
        applyDuring: (DeleteKeyInput) -> DeleteKeyInput
    ): IdkResult<DeleteKeyOutput, IdkError> {
        val input = applyDuring(args)

        try {
            kmsService.deleteKey(input.aliasOrKid, input.providerId)
        } catch (e: Exception) {
            return Err(IdkError.NOT_FOUND_ERROR(
                message = "Key not found or could not be deleted: ${input.aliasOrKid}",
                throwable = e
            ))
        }

        return Ok(DeleteKeyOutput(aliasOrKid = input.aliasOrKid, deleted = true))
    }
}
