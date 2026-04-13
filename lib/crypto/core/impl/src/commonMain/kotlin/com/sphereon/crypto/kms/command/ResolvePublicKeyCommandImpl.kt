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
import com.sphereon.crypto.core.kms.KeyResolverRegistry
import com.sphereon.crypto.core.kms.command.ResolvePublicKeyArgs
import com.sphereon.crypto.core.kms.command.ResolvePublicKeyCommand
import com.sphereon.crypto.core.kms.command.ResolvePublicKeyResult
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of ResolvePublicKeyCommand.
 * Resolves a public key from key information using the appropriate resolver.
 */
@Inject
@SingleIn(SessionScope::class)
class ResolvePublicKeyCommandImpl(
    execution: SessionExecution,
    private val resolverRegistry: KeyResolverRegistry,
) : TypedServiceCommandAdapter<ResolvePublicKeyArgs, ResolvePublicKeyResult>(
        commandId = ResolvePublicKeyCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResolvePublicKeyArgs>(),
        outputTypeToken = typeToken<ResolvePublicKeyResult>(),
    ),
    ResolvePublicKeyCommand {
    override val commandId: String get() = ResolvePublicKeyCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ResolvePublicKeyArgs,
        applyDuring: (ResolvePublicKeyArgs) -> ResolvePublicKeyArgs,
    ): IdkResult<ResolvePublicKeyResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val keyInfo =
            appliedArgs.keyInfo
                ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "keyInfo is required").asErrorResult()

        log.debug("Resolving public key: ${keyInfo.kid ?: keyInfo.alias ?: "unknown"}, identifierMethod: ${appliedArgs.identifierMethod}")

        return try {
            val resolver =
                resolverRegistry.getResolverByKeyTypeOrIdentifier(
                    identifierMethod = appliedArgs.identifierMethod,
                    keyType = keyInfo.keyType,
                    resolverId = keyInfo.providerId,
                )
            val resolvedKey =
                resolver.resolvePublicKey(
                    keyInfo,
                    appliedArgs.identifierMethod,
                    appliedArgs.trustedCerts,
                    appliedArgs.verifyX509CertificateChain,
                )
            log.debug("Public key resolved successfully")
            ResolvePublicKeyResult(resolvedKey).asOkResult()
        } catch (expected: Exception) {
            log.warn("Public key resolution failed: ${expected.message}")
            IdkError.fromString(message = "Failed to resolve public key: ${expected.message}", code = "CRYPTO_ERROR", exception = expected).asErrorResult()
        }
    }

    override suspend fun supports(args: Any): Boolean = args is ResolvePublicKeyArgs && args.keyInfo != null
}
