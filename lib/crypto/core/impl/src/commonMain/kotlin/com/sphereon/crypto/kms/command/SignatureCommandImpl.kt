/*
 * Copyright (c) 2026 Sphereon International B.V.
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

@file:Suppress("TooGenericExceptionCaught")

package com.sphereon.crypto.kms.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.kms.KmsProviderRegistry
import com.sphereon.crypto.core.kms.command.CreateRawSignatureArgs
import com.sphereon.crypto.core.kms.command.CreateRawSignatureCommand
import com.sphereon.crypto.core.kms.command.CreateRawSignatureResult
import com.sphereon.crypto.core.kms.command.VerifyRawSignatureArgs
import com.sphereon.crypto.core.kms.command.VerifyRawSignatureCommand
import com.sphereon.crypto.core.kms.command.VerifyRawSignatureResult
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of CreateRawSignatureCommand.
 * Creates a raw digital signature using the specified key and input data.
 */
@Inject
@SingleIn(SessionScope::class)
class CreateRawSignatureCommandImpl(
    execution: SessionExecution,
    private val providerRegistry: KmsProviderRegistry,
) : TypedServiceCommandAdapter<CreateRawSignatureArgs, CreateRawSignatureResult, IdkError>(
        commandId = CreateRawSignatureCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateRawSignatureArgs>(),
        outputTypeToken = typeToken<CreateRawSignatureResult>(),
    ),
    CreateRawSignatureCommand {
    override val commandId: String get() = CreateRawSignatureCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateRawSignatureArgs,
        applyDuring: (CreateRawSignatureArgs) -> CreateRawSignatureArgs,
    ): IdkResult<CreateRawSignatureResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val keyInfo =
            appliedArgs.keyInfo
                ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "keyInfo is required").asErrorResult()

        log.debug("Creating raw signature with key: ${keyInfo.kid ?: keyInfo.alias ?: "unknown"}")

        return try {
            val provider = providerRegistry.getProvider(keyInfo.providerId, keyInfo.signatureAlgorithm)
            val signature = provider.createRawSignature(keyInfo, appliedArgs.input, appliedArgs.requireX5Chain)
            log.debug("Signature created successfully, length: ${signature.size} bytes")
            CreateRawSignatureResult(signature).asOkResult()
        } catch (expected: Exception) {
            log.warn("Failed to create signature: ${expected.message}")
            IdkError.fromString(message = "Failed to create signature: ${expected.message}", code = "CRYPTO_ERROR", exception = expected).asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean = args is CreateRawSignatureArgs && args.keyInfo != null
}

/**
 * Implementation of VerifyRawSignatureCommand.
 * Verifies a raw digital signature against the provided input.
 */
@Inject
@SingleIn(SessionScope::class)
class VerifyRawSignatureCommandImpl(
    execution: SessionExecution,
    private val providerRegistry: KmsProviderRegistry,
) : TypedServiceCommandAdapter<VerifyRawSignatureArgs, VerifyRawSignatureResult, IdkError>(
        commandId = VerifyRawSignatureCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifyRawSignatureArgs>(),
        outputTypeToken = typeToken<VerifyRawSignatureResult>(),
    ),
    VerifyRawSignatureCommand {
    override val commandId: String get() = VerifyRawSignatureCommand.COMMAND_ID

    override suspend fun doExecute(
        args: VerifyRawSignatureArgs,
        applyDuring: (VerifyRawSignatureArgs) -> VerifyRawSignatureArgs,
    ): IdkResult<VerifyRawSignatureResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val keyInfo =
            appliedArgs.keyInfo
                ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "keyInfo is required").asErrorResult()

        log.debug("Verifying raw signature with key: ${keyInfo.kid ?: keyInfo.alias ?: "unknown"}")

        return try {
            val provider = providerRegistry.getProvider(keyInfo.providerId, keyInfo.signatureAlgorithm)
            val isValid = provider.isValidRawSignature(keyInfo, appliedArgs.input, appliedArgs.signature)
            log.debug("Signature verification result: $isValid")
            VerifyRawSignatureResult(isValid).asOkResult()
        } catch (expected: Exception) {
            log.warn("Failed to verify signature: ${expected.message}")
            IdkError.fromString(message = "Failed to verify signature: ${expected.message}", code = "CRYPTO_ERROR", exception = expected).asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean = args is VerifyRawSignatureArgs && args.keyInfo != null
}
