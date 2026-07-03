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
import com.sphereon.crypto.core.kms.KmsProviderOperation
import com.sphereon.crypto.core.kms.command.CreateRawSignatureArgs
import com.sphereon.crypto.core.kms.command.CreateRawSignatureCommand
import com.sphereon.crypto.core.kms.command.CreateRawSignatureResult
import com.sphereon.crypto.core.kms.command.SignDigestArgs
import com.sphereon.crypto.core.kms.command.SignDigestCommand
import com.sphereon.crypto.core.kms.command.SignDigestResult
import com.sphereon.crypto.core.kms.command.VerifyDigestArgs
import com.sphereon.crypto.core.kms.command.VerifyDigestCommand
import com.sphereon.crypto.core.kms.command.VerifyDigestResult
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

/**
 * Implementation of SignDigestCommand.
 * Creates a signature over a caller-supplied digest or scalar.
 */
@Inject
@SingleIn(SessionScope::class)
class SignDigestCommandImpl(
    execution: SessionExecution,
    private val providerRegistry: KmsProviderRegistry,
) : TypedServiceCommandAdapter<SignDigestArgs, SignDigestResult, IdkError>(
        commandId = SignDigestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<SignDigestArgs>(),
        outputTypeToken = typeToken<SignDigestResult>(),
    ),
    SignDigestCommand {
    override val commandId: String get() = SignDigestCommand.COMMAND_ID

    override suspend fun doExecute(
        args: SignDigestArgs,
        applyDuring: (SignDigestArgs) -> SignDigestArgs,
    ): IdkResult<SignDigestResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val keyInfo =
            appliedArgs.keyInfo
                ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "keyInfo is required").asErrorResult()
        val signatureAlgorithm = appliedArgs.signatureAlgorithm ?: keyInfo.signatureAlgorithm
            ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "signatureAlgorithm is required").asErrorResult()
        if (appliedArgs.digest.isEmpty()) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "digest is required").asErrorResult()
        }

        return try {
            val provider = providerRegistry.getProvider(keyInfo.providerId, signatureAlgorithm)
            if (!provider.getCapabilities().supportsOperation(KmsProviderOperation.SIGN_DIGEST)) {
                return IdkError
                    .UNSUPPORTED_OPERATION_ERROR(
                        operation = KmsProviderOperation.SIGN_DIGEST.name,
                        reason = "Provider ${provider.id} does not advertise digest signing",
                    ).asErrorResult()
            }
            if (!provider.supportsSignatureAlgorithm(KmsProviderOperation.SIGN_DIGEST, signatureAlgorithm)) {
                return IdkError
                    .UNSUPPORTED_OPERATION_ERROR(
                        operation = KmsProviderOperation.SIGN_DIGEST.name,
                        reason = "Provider ${provider.id} does not advertise digest signing for $signatureAlgorithm",
                    ).asErrorResult()
            }
            val signature =
                provider.signDigest(
                    keyInfo = keyInfo,
                    digest = appliedArgs.digest,
                    signatureAlgorithm = signatureAlgorithm,
                    signatureEncoding = appliedArgs.signatureEncoding,
                    requireX5Chain = appliedArgs.requireX5Chain,
                )
            SignDigestResult(signature).asOkResult()
        } catch (expected: Exception) {
            log.warn("Failed to create digest signature: ${expected.message}")
            IdkError
                .fromString(
                    message = "Failed to create digest signature: ${expected.message}",
                    code = "CRYPTO_ERROR",
                    exception = expected,
                ).asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean = args is SignDigestArgs && args.keyInfo != null && args.digest.isNotEmpty()
}

/**
 * Implementation of VerifyDigestCommand.
 * Verifies a signature over a caller-supplied digest or scalar.
 */
@Inject
@SingleIn(SessionScope::class)
class VerifyDigestCommandImpl(
    execution: SessionExecution,
    private val providerRegistry: KmsProviderRegistry,
) : TypedServiceCommandAdapter<VerifyDigestArgs, VerifyDigestResult, IdkError>(
        commandId = VerifyDigestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifyDigestArgs>(),
        outputTypeToken = typeToken<VerifyDigestResult>(),
    ),
    VerifyDigestCommand {
    override val commandId: String get() = VerifyDigestCommand.COMMAND_ID

    override suspend fun doExecute(
        args: VerifyDigestArgs,
        applyDuring: (VerifyDigestArgs) -> VerifyDigestArgs,
    ): IdkResult<VerifyDigestResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val keyInfo =
            appliedArgs.keyInfo
                ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "keyInfo is required").asErrorResult()
        val signatureAlgorithm = appliedArgs.signatureAlgorithm ?: keyInfo.signatureAlgorithm
            ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "signatureAlgorithm is required").asErrorResult()
        if (appliedArgs.digest.isEmpty()) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "digest is required").asErrorResult()
        }
        if (appliedArgs.signature.isEmpty()) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "signature is required").asErrorResult()
        }

        return try {
            val provider = providerRegistry.getProvider(keyInfo.providerId, signatureAlgorithm)
            if (!provider.getCapabilities().supportsOperation(KmsProviderOperation.VERIFY_DIGEST)) {
                return IdkError
                    .UNSUPPORTED_OPERATION_ERROR(
                        operation = KmsProviderOperation.VERIFY_DIGEST.name,
                        reason = "Provider ${provider.id} does not advertise digest verification",
                    ).asErrorResult()
            }
            if (!provider.supportsSignatureAlgorithm(KmsProviderOperation.VERIFY_DIGEST, signatureAlgorithm)) {
                return IdkError
                    .UNSUPPORTED_OPERATION_ERROR(
                        operation = KmsProviderOperation.VERIFY_DIGEST.name,
                        reason = "Provider ${provider.id} does not advertise digest verification for $signatureAlgorithm",
                    ).asErrorResult()
            }
            val isValid =
                provider.verifyDigest(
                    keyInfo = keyInfo,
                    digest = appliedArgs.digest,
                    signature = appliedArgs.signature,
                    signatureAlgorithm = signatureAlgorithm,
                    signatureEncoding = appliedArgs.signatureEncoding,
                )
            VerifyDigestResult(isValid).asOkResult()
        } catch (expected: Exception) {
            log.warn("Failed to verify digest signature: ${expected.message}")
            IdkError
                .fromString(
                    message = "Failed to verify digest signature: ${expected.message}",
                    code = "CRYPTO_ERROR",
                    exception = expected,
                ).asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean =
        args is VerifyDigestArgs &&
            args.keyInfo != null &&
            args.digest.isNotEmpty() &&
            args.signature.isNotEmpty()
}

private fun com.sphereon.crypto.core.kms.KmsProvider.supportsSignatureAlgorithm(
    operation: KmsProviderOperation,
    signatureAlgorithm: com.sphereon.crypto.core.generic.SignatureAlgorithm,
): Boolean {
    val capability = getCapabilities().getOperationCapability(operation) ?: return false
    return capability.supported &&
        (
            capability.signatureAlgorithms.isEmpty() ||
                capability.signatureAlgorithms.contains(signatureAlgorithm)
        )
}
