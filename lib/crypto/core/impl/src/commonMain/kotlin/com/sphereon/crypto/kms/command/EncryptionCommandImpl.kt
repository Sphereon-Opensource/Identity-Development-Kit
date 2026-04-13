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
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.kms.EcdhUtils
import com.sphereon.crypto.core.kms.KmsProviderRegistry
import com.sphereon.crypto.core.kms.command.DecryptArgs
import com.sphereon.crypto.core.kms.command.DecryptCommand
import com.sphereon.crypto.core.kms.command.DecryptResult
import com.sphereon.crypto.core.kms.command.EncryptArgs
import com.sphereon.crypto.core.kms.command.EncryptCommand
import com.sphereon.crypto.core.kms.command.EncryptResult
import com.sphereon.crypto.core.kms.command.PerformKeyAgreementArgs
import com.sphereon.crypto.core.kms.command.PerformKeyAgreementCommand
import com.sphereon.crypto.core.kms.command.PerformKeyAgreementResult
import com.sphereon.crypto.core.kms.command.UnwrapKeyArgs
import com.sphereon.crypto.core.kms.command.UnwrapKeyCommand
import com.sphereon.crypto.core.kms.command.UnwrapKeyResult
import com.sphereon.crypto.core.kms.command.WrapKeyArgs
import com.sphereon.crypto.core.kms.command.WrapKeyCommand
import com.sphereon.crypto.core.kms.command.WrapKeyResult
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of EncryptCommand.
 * Encrypts plaintext using authenticated encryption (AEAD).
 */
@Inject
@SingleIn(SessionScope::class)
class EncryptCommandImpl(
    execution: SessionExecution,
    private val providerRegistry: KmsProviderRegistry
) : TypedServiceCommandAdapter<EncryptArgs, EncryptResult>(
    commandId = EncryptCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<EncryptArgs>(),
    outputTypeToken = typeToken<EncryptResult>(),
), EncryptCommand {

    override val commandId: String get() = EncryptCommand.COMMAND_ID

    override suspend fun doExecute(
        args: EncryptArgs,
        applyDuring: (EncryptArgs) -> EncryptArgs
    ): IdkResult<EncryptResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val keyInfo = appliedArgs.keyInfo
            ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "keyInfo is required").asErrorResult()

        log.debug("Encrypting data with algorithm: ${appliedArgs.algorithm}")

        return try {
            val provider = providerRegistry.getProvider(keyInfo.providerId, keyInfo.signatureAlgorithm)
            val result = provider.encrypt(
                keyInfo,
                appliedArgs.plaintext,
                appliedArgs.algorithm,
                appliedArgs.additionalAuthenticatedData
            )
            log.debug("Encryption successful, ciphertext length: ${result.ciphertext.size} bytes")
            EncryptResult(
                ciphertext = result.ciphertext,
                iv = result.iv,
                authTag = result.authTag
            ).asOkResult()
        } catch (e: Exception) {
            log.warn("Encryption failed: ${e.message}")
            IdkError.fromString(message = "Encryption failed: ${e.message}").asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean {
        return args is EncryptArgs && args.keyInfo != null
    }
}

/**
 * Implementation of DecryptCommand.
 * Decrypts ciphertext encrypted with authenticated encryption.
 */
@Inject
@SingleIn(SessionScope::class)
class DecryptCommandImpl(
    execution: SessionExecution,
    private val providerRegistry: KmsProviderRegistry
) : TypedServiceCommandAdapter<DecryptArgs, DecryptResult>(
    commandId = DecryptCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<DecryptArgs>(),
    outputTypeToken = typeToken<DecryptResult>(),
), DecryptCommand {

    override val commandId: String get() = DecryptCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DecryptArgs,
        applyDuring: (DecryptArgs) -> DecryptArgs
    ): IdkResult<DecryptResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val keyInfo = appliedArgs.keyInfo
            ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "keyInfo is required").asErrorResult()

        log.debug("Decrypting data with algorithm: ${appliedArgs.algorithm}")

        return try {
            val provider = providerRegistry.getProvider(keyInfo.providerId, keyInfo.signatureAlgorithm)
            val plaintext = provider.decrypt(
                keyInfo,
                appliedArgs.ciphertext,
                appliedArgs.algorithm,
                appliedArgs.iv,
                appliedArgs.authTag,
                appliedArgs.additionalAuthenticatedData
            )
            log.debug("Decryption successful, plaintext length: ${plaintext.size} bytes")
            DecryptResult(plaintext).asOkResult()
        } catch (e: Exception) {
            log.warn("Decryption failed: ${e.message}")
            IdkError.fromString(message = "Decryption failed: ${e.message}").asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean {
        return args is DecryptArgs && args.keyInfo != null
    }
}

/**
 * Implementation of WrapKeyCommand.
 * Wraps (encrypts) a symmetric key using the specified algorithm.
 */
@Inject
@SingleIn(SessionScope::class)
class WrapKeyCommandImpl(
    execution: SessionExecution,
    private val providerRegistry: KmsProviderRegistry
) : TypedServiceCommandAdapter<WrapKeyArgs, WrapKeyResult>(
    commandId = WrapKeyCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<WrapKeyArgs>(),
    outputTypeToken = typeToken<WrapKeyResult>(),
), WrapKeyCommand {

    override val commandId: String get() = WrapKeyCommand.COMMAND_ID

    override suspend fun doExecute(
        args: WrapKeyArgs,
        applyDuring: (WrapKeyArgs) -> WrapKeyArgs
    ): IdkResult<WrapKeyResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val wrappingKeyInfo = appliedArgs.wrappingKeyInfo
            ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "wrappingKeyInfo is required").asErrorResult()

        log.debug("Wrapping key with algorithm: ${appliedArgs.algorithm}")

        return try {
            val provider = providerRegistry.getProvider(wrappingKeyInfo.providerId, wrappingKeyInfo.signatureAlgorithm)
            val wrappedKey = provider.wrapKey(
                wrappingKeyInfo,
                appliedArgs.keyToWrap,
                appliedArgs.algorithm
            )
            log.debug("Key wrap successful, wrapped key length: ${wrappedKey.size} bytes")
            WrapKeyResult(wrappedKey).asOkResult()
        } catch (e: Exception) {
            log.warn("Key wrap failed: ${e.message}")
            IdkError.fromString(message = "Key wrap failed: ${e.message}").asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean {
        return args is WrapKeyArgs && args.wrappingKeyInfo != null
    }
}

/**
 * Implementation of UnwrapKeyCommand.
 * Unwraps (decrypts) a wrapped symmetric key.
 */
@Inject
@SingleIn(SessionScope::class)
class UnwrapKeyCommandImpl(
    execution: SessionExecution,
    private val providerRegistry: KmsProviderRegistry
) : TypedServiceCommandAdapter<UnwrapKeyArgs, UnwrapKeyResult>(
    commandId = UnwrapKeyCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<UnwrapKeyArgs>(),
    outputTypeToken = typeToken<UnwrapKeyResult>(),
), UnwrapKeyCommand {

    override val commandId: String get() = UnwrapKeyCommand.COMMAND_ID

    override suspend fun doExecute(
        args: UnwrapKeyArgs,
        applyDuring: (UnwrapKeyArgs) -> UnwrapKeyArgs
    ): IdkResult<UnwrapKeyResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val unwrappingKeyInfo = appliedArgs.unwrappingKeyInfo
            ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "unwrappingKeyInfo is required").asErrorResult()

        log.debug("Unwrapping key with algorithm: ${appliedArgs.algorithm}")

        return try {
            val provider = providerRegistry.getProvider(unwrappingKeyInfo.providerId, unwrappingKeyInfo.signatureAlgorithm)
            val unwrappedKey = provider.unwrapKey(
                unwrappingKeyInfo,
                appliedArgs.wrappedKey,
                appliedArgs.algorithm
            )
            log.debug("Key unwrap successful, unwrapped key length: ${unwrappedKey.size} bytes")
            UnwrapKeyResult(unwrappedKey).asOkResult()
        } catch (e: Exception) {
            log.warn("Key unwrap failed: ${e.message}")
            IdkError.fromString(message = "Key unwrap failed: ${e.message}").asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean {
        return args is UnwrapKeyArgs && args.unwrappingKeyInfo != null
    }
}

/**
 * Implementation of PerformKeyAgreementCommand.
 * Performs ECDH key agreement to derive a shared secret.
 */
@Inject
@SingleIn(SessionScope::class)
class PerformKeyAgreementCommandImpl(
    execution: SessionExecution
) : TypedServiceCommandAdapter<PerformKeyAgreementArgs, PerformKeyAgreementResult>(
    commandId = PerformKeyAgreementCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<PerformKeyAgreementArgs>(),
    outputTypeToken = typeToken<PerformKeyAgreementResult>(),
), PerformKeyAgreementCommand {

    override val commandId: String get() = PerformKeyAgreementCommand.COMMAND_ID

    override suspend fun doExecute(
        args: PerformKeyAgreementArgs,
        applyDuring: (PerformKeyAgreementArgs) -> PerformKeyAgreementArgs
    ): IdkResult<PerformKeyAgreementResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val privateKeyInfo = appliedArgs.privateKeyInfo
            ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "privateKeyInfo is required").asErrorResult()
        val publicKeyInfo = appliedArgs.publicKeyInfo
            ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "publicKeyInfo is required").asErrorResult()

        log.debug("Performing key agreement with algorithm: ${appliedArgs.algorithm}")

        return try {
            // Call EcdhUtils directly to avoid circular command delegation
            // Convert key info to JWK format
            val privateKeyJwk = CoseJoseKeyMappingService.toJwkKeyInfo(privateKeyInfo).key
                ?: throw IllegalArgumentException("Private key info must contain a key")
            val publicKeyJwk = CoseJoseKeyMappingService.toJwkKeyInfo(publicKeyInfo).key
                ?: throw IllegalArgumentException("Public key info must contain a key")

            // Validate that both are EC keys (ECDH only works with EC keys)
            require(privateKeyJwk.kty == JwaKeyType.EC) {
                "Private key must be an EC key for ECDH key agreement, got: ${privateKeyJwk.kty}"
            }
            require(publicKeyJwk.kty == JwaKeyType.EC) {
                "Public key must be an EC key for ECDH key agreement, got: ${publicKeyJwk.kty}"
            }
            require(privateKeyJwk.d != null) {
                "Private key must have 'd' parameter for key agreement"
            }
            require(publicKeyJwk.x != null && publicKeyJwk.y != null) {
                "Public key must have 'x' and 'y' coordinates for key agreement"
            }

            // Determine the curve from the private key
            val curve = privateKeyJwk.crv?.let { Curve.fromJose(it) }
                ?: publicKeyJwk.crv?.let { Curve.fromJose(it) }
                ?: Curve.P_256 // Default to P-256 if not specified

            // Perform the key agreement using EcdhUtils
            val sharedSecret = EcdhUtils.performKeyAgreementForDecryption(
                ourPrivateKeyJwk = privateKeyJwk,
                senderEphemeralPublicKeyJwk = publicKeyJwk,
                curve = curve
            )
            log.debug("Key agreement successful, shared secret length: ${sharedSecret.size} bytes")
            PerformKeyAgreementResult(sharedSecret).asOkResult()
        } catch (e: Exception) {
            log.warn("Key agreement failed: ${e.message}")
            IdkError.fromString(message = "Key agreement failed: ${e.message}").asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean {
        return args is PerformKeyAgreementArgs &&
                args.privateKeyInfo != null &&
                args.publicKeyInfo != null
    }
}

