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

@file:Suppress("TooGenericExceptionCaught")

package com.sphereon.crypto.jose.jws.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.kms.command.CreateRawSignatureArgs
import com.sphereon.crypto.core.kms.command.CreateRawSignatureCommand
import com.sphereon.crypto.jose.jws.JwsJsonGeneral
import com.sphereon.crypto.jose.jws.assembleGeneral
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonGeneralCommand
import com.sphereon.crypto.jose.jws.command.PrepareJwsCommand
import com.sphereon.crypto.resolution.managed.ManagedIdentifierKeyResult
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Command implementation for creating general JSON JWS
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJwsJsonGeneralCommandImpl", exact = true)
class CreateJwsJsonGeneralCommandImpl(
    execution: SessionExecution,
    private val prepareJwsCommand: PrepareJwsCommand,
    private val createRawSignatureCommand: CreateRawSignatureCommand,
) : TypedServiceCommandAdapter<CreateJwsJsonArgs, JwsJsonGeneral, IdkError>(
        commandId = CreateJwsJsonGeneralCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateJwsJsonArgs>(),
        outputTypeToken = typeToken<JwsJsonGeneral>(),
    ),
    CreateJwsJsonGeneralCommand {
    override val commandId: String get() = CreateJwsJsonGeneralCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateJwsJsonArgs,
        applyDuring: (CreateJwsJsonArgs) -> CreateJwsJsonArgs,
    ): IdkResult<JwsJsonGeneral, IdkError> {
        val appliedArgs = applyDuring(args)

        // Prepare the JWS object
        val prepareResult = prepareJwsCommand.execute(appliedArgs)
        if (prepareResult.isErr) {
            return IdkResult.err(prepareResult.error)
        }

        val prepared = prepareResult.value

        // Sign the input
        // Execute through the public KMS command binding. That binding resolves via the
        // session-scoped command registry, so route-only workloads (for example tenant-AS)
        // honor transport.routing.modules.kms.services.signature instead of accidentally
        // selecting an in-process software provider from SignatureService/KeyManagerService.
        val signatureResult =
            try {
                createRawSignatureCommand.execute(
                    CreateRawSignatureArgs(
                        keyInfo = (prepared.identifier as ManagedIdentifierKeyResult).keyInfo,
                        input = prepared.signingInput,
                        requireX5Chain = false,
                    ),
                )
            } catch (expected: Exception) {
                return IdkResult.err(IdkError.fromString("Failed to create signature: ${expected.message}", exception = expected))
            }
        val signatureBytes =
            signatureResult.getOrElse { error ->
                return IdkResult.err(
                    IdkError.fromString(
                        "Failed to create signature: ${error.message.defaultMessage}",
                        exception = error.exception as? Exception,
                    ),
                )
            }.signature

        return prepared.assembleGeneral(signatureBytes).asOkResult()
    }
}
