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
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.key.persistence.impl.ManagedKeyReferenceRegistrar
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyInput
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyOutput
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GenerateKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GetKeyInput
import com.sphereon.crypto.kms.rest.api.command.GetKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ImportKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ListKeysInput
import com.sphereon.crypto.kms.rest.api.command.ListKeysServiceCommand
import com.sphereon.crypto.kms.rest.api.command.RegisterKeyReferenceInput
import com.sphereon.crypto.kms.rest.api.command.RegisterKeyReferenceResponse
import com.sphereon.crypto.kms.rest.api.command.RegisterKeyReferenceServiceCommand
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyGlobal
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.GetKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ImportKey
import com.sphereon.crypto.kms.rest.api.generated.models.ImportKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ListKeysResponse
import com.sphereon.crypto.kms.rest.api.mapper.toRest
import com.sphereon.crypto.kms.rest.api.mapper.toSdk
import com.sphereon.crypto.kms.rest.server.service.KmsRestService
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import com.sphereon.crypto.kms.rest.api.generated.models.KeyOperations as KeyOperationsRest

// ========== KMS Keys Service Command Implementations ==========
//
// These are the LOCAL implementations (business logic).
// Each command provides TWO DI bindings:
//   1. Command interface single binding - for direct injection by consumers
//   2. Map multibinding (@IntoMap @StringKey) - for registry discovery
//
// The -remote module's KSP-generated routed class contributes ONLY map multibinding
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
    private val kmsService: KmsRestService,
) : TypedServiceCommandAdapter<GetKeyInput, GetKeyResponse, IdkError>(
        commandId = GetKeyServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetKeyInput>(),
        outputTypeToken = typeToken<GetKeyResponse>(),
    ),
    GetKeyServiceCommand {
    override val commandId = GetKeyServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetKeyInput,
        applyDuring: (GetKeyInput) -> GetKeyInput,
    ): IdkResult<GetKeyResponse, IdkError> {
        val input = applyDuring(args)

        val keyInfo =
            try {
                kmsService.getKey(input.aliasOrKid, input.providerId)
            } catch (expected: Exception) {
                return Err(
                    IdkError.NOT_FOUND_ERROR(
                        message = "Key not found: ${input.aliasOrKid}",
                        throwable = expected,
                    ),
                )
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
    private val kmsService: KmsRestService,
) : TypedServiceCommandAdapter<ListKeysInput, ListKeysResponse, IdkError>(
        commandId = ListKeysServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ListKeysInput>(),
        outputTypeToken = typeToken<ListKeysResponse>(),
    ),
    ListKeysServiceCommand {
    override val commandId = ListKeysServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ListKeysInput,
        applyDuring: (ListKeysInput) -> ListKeysInput,
    ): IdkResult<ListKeysResponse, IdkError> {
        val input = applyDuring(args)

        val keyInfos =
            try {
                kmsService.listKeys(input.providerId).map { it.toRest() }.toTypedArray()
            } catch (expected: Exception) {
                return Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to list keys: ${expected.message}",
                        exception = expected,
                    ),
                )
            }

        return Ok(ListKeysResponse(keyInfos = keyInfos))
    }
}

// ========== ImportKey Service Command Implementation ==========

/**
 * LOCAL implementation of [ImportKeyServiceCommand].
 *
 * POST /keys/import
 */
@Inject
@SingleIn(SessionScope::class)
class ImportKeyServiceCommandImpl(
    execution: SessionExecution,
    private val kmsService: KmsRestService,
) : TypedServiceCommandAdapter<ImportKey, ImportKeyResponse, IdkError>(
        commandId = ImportKeyServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ImportKey>(),
        outputTypeToken = typeToken<ImportKeyResponse>(),
    ),
    ImportKeyServiceCommand {
    override val commandId = ImportKeyServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ImportKey,
        applyDuring: (ImportKey) -> ImportKey,
    ): IdkResult<ImportKeyResponse, IdkError> {
        val importKeyRequest = applyDuring(args)

        val key =
            try {
                kmsService.storeKey(
                    keyInfo = importKeyRequest.keyInfo.toSdk(),
                    certChain = importKeyRequest.certChain,
                )
            } catch (expected: Exception) {
                return Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to import key: ${expected.message}",
                        exception = expected,
                    ),
                )
            }

        return Ok(ImportKeyResponse(keyInfo = key.toRest()))
    }
}

// ========== GenerateKey Service Command Implementation ==========

/**
 * LOCAL implementation of [GenerateKeyServiceCommand].
 *
 * POST /keys
 */
@Inject
@SingleIn(SessionScope::class)
class GenerateKeyServiceCommandImpl(
    execution: SessionExecution,
    private val kmsService: KmsRestService,
) : TypedServiceCommandAdapter<GenerateKeyGlobal, GenerateKeyResponse, IdkError>(
        commandId = GenerateKeyServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GenerateKeyGlobal>(),
        outputTypeToken = typeToken<GenerateKeyResponse>(),
    ),
    GenerateKeyServiceCommand {
    override val commandId = GenerateKeyServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GenerateKeyGlobal,
        applyDuring: (GenerateKeyGlobal) -> GenerateKeyGlobal,
    ): IdkResult<GenerateKeyResponse, IdkError> {
        val generateRequest = applyDuring(args)

        val keyPair =
            try {
                kmsService.generateKey(
                    alias = generateRequest.alias,
                    use = generateRequest.use?.let { JwkUse.valueOf(it.value) },
                    keyOperations =
                        generateRequest.keyOperations
                            ?.map<KeyOperationsRest, KeyOperations> {
                                KeyOperations.fromValue(it.value)
                            }?.toTypedArray(),
                    alg = generateRequest.alg?.let { SignatureAlgorithm.fromValue(it.value) },
                    providerId = generateRequest.providerId,
                )
            } catch (expected: Exception) {
                return Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to generate key: ${expected.message}",
                        exception = expected,
                    ),
                )
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
    private val kmsService: KmsRestService,
) : TypedServiceCommandAdapter<DeleteKeyInput, DeleteKeyOutput, IdkError>(
        commandId = DeleteKeyServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DeleteKeyInput>(),
        outputTypeToken = typeToken<DeleteKeyOutput>(),
    ),
    DeleteKeyServiceCommand {
    override val commandId = DeleteKeyServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DeleteKeyInput,
        applyDuring: (DeleteKeyInput) -> DeleteKeyInput,
    ): IdkResult<DeleteKeyOutput, IdkError> {
        val input = applyDuring(args)

        try {
            kmsService.deleteKey(input.aliasOrKid, input.providerId)
        } catch (expected: Exception) {
            return Err(
                IdkError.NOT_FOUND_ERROR(
                    message = "Key not found or could not be deleted: ${input.aliasOrKid}",
                    throwable = expected,
                ),
            )
        }

        return Ok(DeleteKeyOutput(aliasOrKid = input.aliasOrKid, deleted = true))
    }
}

// ========== RegisterKeyReference Service Command Implementation ==========

/**
 * LOCAL implementation of [RegisterKeyReferenceServiceCommand].
 *
 * POST /keys/register
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RegisterKeyReferenceServiceCommand>())
class RegisterKeyReferenceServiceCommandImpl(
    execution: SessionExecution,
    private val registrar: ManagedKeyReferenceRegistrar,
    private val kmsService: KmsRestService,
) : TypedServiceCommandAdapter<RegisterKeyReferenceInput, RegisterKeyReferenceResponse, IdkError>(
        commandId = RegisterKeyReferenceServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RegisterKeyReferenceInput>(),
        outputTypeToken = typeToken<RegisterKeyReferenceResponse>(),
    ),
    RegisterKeyReferenceServiceCommand {
    override val commandId = RegisterKeyReferenceServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: RegisterKeyReferenceInput,
        applyDuring: (RegisterKeyReferenceInput) -> RegisterKeyReferenceInput,
    ): IdkResult<RegisterKeyReferenceResponse, IdkError> {
        val input = applyDuring(args)

        // Validate the key exists in the provider before registering a reference
        val resolvedKey =
            try {
                kmsService.getKey(input.alias, input.providerId)
            } catch (expected: Exception) {
                return Err(
                    IdkError.NOT_FOUND_ERROR(
                        message = "Key '${input.alias}' not found in provider '${input.providerId}': ${expected.message}",
                        throwable = expected,
                    ),
                )
            }

        val result =
            registrar.registerKeyReference(
                providerId = input.providerId,
                alias = input.alias,
                kid = input.kid ?: resolvedKey.kid,
                keyType = resolvedKey.keyType,
                signatureAlgorithm = resolvedKey.signatureAlgorithm,
                keyVisibility = resolvedKey.keyVisibility,
                keyEncoding = resolvedKey.keyEncoding,
            )

        val record = result.getOrElse { error -> return Err(error) }

        return Ok(
            RegisterKeyReferenceResponse(
                registered = true,
                alias = record.alias,
                providerId = record.providerId,
                kid = record.kid,
            ),
        )
    }
}
