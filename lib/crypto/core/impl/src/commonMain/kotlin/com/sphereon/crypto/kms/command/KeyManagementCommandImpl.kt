/*
 * Copyright (c) 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.crypto.kms.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.kms.KmsProviderRegistry
import com.sphereon.crypto.core.kms.ManagedKeyStoreService
import com.sphereon.crypto.core.kms.command.DeleteKeyArgs
import com.sphereon.crypto.core.kms.command.DeleteKeyCommand
import com.sphereon.crypto.core.kms.command.DeleteKeyResult
import com.sphereon.crypto.core.kms.command.GenerateKeyArgs
import com.sphereon.crypto.core.kms.command.GenerateKeyCommand
import com.sphereon.crypto.core.kms.command.GenerateKeyResult
import com.sphereon.crypto.core.kms.command.GetKeyArgs
import com.sphereon.crypto.core.kms.command.GetKeyCommand
import com.sphereon.crypto.core.kms.command.GetKeyResult
import com.sphereon.crypto.core.kms.command.ListKeysArgs
import com.sphereon.crypto.core.kms.command.ListKeysCommand
import com.sphereon.crypto.core.kms.command.ListKeysResult
import com.sphereon.crypto.core.kms.command.StoreKeyArgs
import com.sphereon.crypto.core.kms.command.StoreKeyCommand
import com.sphereon.crypto.core.kms.command.StoreKeyResult
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of GenerateKeyCommand.
 * Generates a new cryptographic key pair.
 */
@Inject
@SingleIn(SessionScope::class)
class GenerateKeyCommandImpl(
    execution: SessionExecution,
    private val providerRegistry: KmsProviderRegistry
) : TypedServiceCommandAdapter<GenerateKeyArgs, GenerateKeyResult>(
    commandId = GenerateKeyCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<GenerateKeyArgs>(),
    outputTypeToken = typeToken<GenerateKeyResult>(),
), GenerateKeyCommand {

    override val commandId: String get() = GenerateKeyCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GenerateKeyArgs,
        applyDuring: (GenerateKeyArgs) -> GenerateKeyArgs
    ): IdkResult<GenerateKeyResult, IdkError> {
        val appliedArgs = applyDuring(args)

        log.debug("Generating key with provider: ${appliedArgs.providerId ?: "default"}, alias: ${appliedArgs.alias ?: "none"}")

        return try {
            val provider = providerRegistry.getProvider(appliedArgs.providerId, appliedArgs.alg)
            val keyPair = provider.generateKeyAsync(
                appliedArgs.alias,
                appliedArgs.use,
                appliedArgs.keyOperations,
                appliedArgs.alg
            )
            log.debug("Key generated successfully: ${keyPair.kid ?: keyPair.alias ?: "unknown"}")
            GenerateKeyResult(keyPair).asOkResult()
        } catch (e: Exception) {
            log.warn("Key generation failed: ${e.message}")
            IdkError.fromString(message = "Key generation failed: ${e.message}", code = "CRYPTO_ERROR", exception = e).asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean {
        return args is GenerateKeyArgs
    }
}

/**
 * Implementation of ListKeysCommand.
 * Lists all keys in the key store.
 */
@Inject
@SingleIn(SessionScope::class)
class ListKeysCommandImpl(
    execution: SessionExecution,
    private val keyStore: ManagedKeyStoreService
) : TypedServiceCommandAdapter<ListKeysArgs, ListKeysResult>(
    commandId = ListKeysCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ListKeysArgs>(),
    outputTypeToken = typeToken<ListKeysResult>(),
), ListKeysCommand {

    override val commandId: String get() = ListKeysCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ListKeysArgs,
        applyDuring: (ListKeysArgs) -> ListKeysArgs
    ): IdkResult<ListKeysResult, IdkError> {
        val appliedArgs = applyDuring(args)

        log.debug("Listing keys${appliedArgs.providerId?.let { " for provider: $it" } ?: ""}")

        return try {
            val keys = keyStore.listKeys()
            val filteredKeys = if (appliedArgs.providerId != null) {
                keys.filter { it.providerId == appliedArgs.providerId }.toTypedArray()
            } else {
                keys
            }
            log.debug("Listed ${filteredKeys.size} keys")
            ListKeysResult(filteredKeys).asOkResult()
        } catch (e: Exception) {
            log.warn("List keys failed: ${e.message}")
            IdkError.UNKNOWN_ERROR(message = "Failed to list keys: ${e.message}").asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean {
        return args is ListKeysArgs
    }
}

/**
 * Implementation of GetKeyCommand.
 * Retrieves a specific key from the key store.
 */
@Inject
@SingleIn(SessionScope::class)
class GetKeyCommandImpl(
    execution: SessionExecution,
    private val keyStore: ManagedKeyStoreService
) : TypedServiceCommandAdapter<GetKeyArgs, GetKeyResult>(
    commandId = GetKeyCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<GetKeyArgs>(),
    outputTypeToken = typeToken<GetKeyResult>(),
), GetKeyCommand {

    override val commandId: String get() = GetKeyCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetKeyArgs,
        applyDuring: (GetKeyArgs) -> GetKeyArgs
    ): IdkResult<GetKeyResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val keyInfo = appliedArgs.keyInfo
            ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "keyInfo is required").asErrorResult()

        log.debug("Getting key: ${keyInfo.kid ?: keyInfo.alias ?: "unknown"}")

        return try {
            val key = keyStore.getKey(keyInfo)
            log.debug("Key retrieved successfully")
            GetKeyResult(key).asOkResult()
        } catch (e: Exception) {
            log.warn("Get key failed: ${e.message}")
            IdkError.NOT_FOUND_ERROR(
                resource = "Key",
                message = "Key not found: ${e.message}"
            ).asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean {
        return args is GetKeyArgs && args.keyInfo != null
    }
}

/**
 * Implementation of StoreKeyCommand.
 * Stores a key in the key store.
 */
@Inject
@SingleIn(SessionScope::class)
class StoreKeyCommandImpl(
    execution: SessionExecution,
    private val keyStore: ManagedKeyStoreService
) : TypedServiceCommandAdapter<StoreKeyArgs, StoreKeyResult>(
    commandId = StoreKeyCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<StoreKeyArgs>(),
    outputTypeToken = typeToken<StoreKeyResult>(),
), StoreKeyCommand {

    override val commandId: String get() = StoreKeyCommand.COMMAND_ID

    override suspend fun doExecute(
        args: StoreKeyArgs,
        applyDuring: (StoreKeyArgs) -> StoreKeyArgs
    ): IdkResult<StoreKeyResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val keyInfo = appliedArgs.keyInfo
            ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "keyInfo is required").asErrorResult()

        if (appliedArgs.providerId.isBlank()) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "providerId is required").asErrorResult()
        }
        if (appliedArgs.alias.isBlank()) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "alias is required").asErrorResult()
        }

        log.debug("Storing key with alias: ${appliedArgs.alias} in provider: ${appliedArgs.providerId}")

        return try {
            val storedKey = keyStore.storeKey(
                keyInfo,
                appliedArgs.providerId,
                appliedArgs.alias,
                appliedArgs.certChain
            )
            log.debug("Key stored successfully")
            StoreKeyResult(storedKey).asOkResult()
        } catch (e: Exception) {
            log.warn("Store key failed: ${e.message}")
            IdkError.UNKNOWN_ERROR(message = "Failed to store key: ${e.message}").asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean {
        return args is StoreKeyArgs && args.keyInfo != null
    }
}

/**
 * Implementation of DeleteKeyCommand.
 * Deletes a key from the key store.
 */
@Inject
@SingleIn(SessionScope::class)
class DeleteKeyCommandImpl(
    execution: SessionExecution,
    private val keyStore: ManagedKeyStoreService
) : TypedServiceCommandAdapter<DeleteKeyArgs, DeleteKeyResult>(
    commandId = DeleteKeyCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<DeleteKeyArgs>(),
    outputTypeToken = typeToken<DeleteKeyResult>(),
), DeleteKeyCommand {

    override val commandId: String get() = DeleteKeyCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DeleteKeyArgs,
        applyDuring: (DeleteKeyArgs) -> DeleteKeyArgs
    ): IdkResult<DeleteKeyResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val keyInfo = appliedArgs.keyInfo
            ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "keyInfo is required").asErrorResult()

        log.debug("Deleting key: ${keyInfo.kid ?: keyInfo.alias ?: "unknown"}")

        return try {
            val deleted = keyStore.deleteKey(keyInfo)
            log.debug("Key deletion result: $deleted")
            DeleteKeyResult(deleted).asOkResult()
        } catch (e: Exception) {
            log.warn("Delete key failed: ${e.message}")
            IdkError.UNKNOWN_ERROR(message = "Failed to delete key: ${e.message}").asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean {
        return args is DeleteKeyArgs && args.keyInfo != null
    }
}

