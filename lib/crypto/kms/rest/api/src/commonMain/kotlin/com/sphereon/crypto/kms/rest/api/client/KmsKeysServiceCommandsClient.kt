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

package com.sphereon.crypto.kms.rest.api.client

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
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

/**
 * Shared transport invoker for KMS REST command clients.
 *
 * Runtime session authority is bound in the transport wrapper and reused for every command execution.
 */
abstract class KmsKeysTransportCommandClient(
    private val transport: SessionBoundKmsCommandTransport,
) {
    protected suspend fun <I : Any, O : Any> invokeCommand(
        commandId: String,
        input: I,
        outputTypeToken: TypeToken<O>,
    ): IdkResult<O, IdkError> =
        transport.invoke(
            commandId = commandId,
            input = input,
            outputTypeToken = outputTypeToken,
        )
}

// ========== KMS Keys Client ServiceCommand Implementations ==========
//
// IDK-ONLY REST CLIENT
//
// These are standalone (non-DI) command implementations for IDK-only consumers
// who need to talk to a KMS REST API over HTTP. They bridge pre-existing KMS
// software without requiring EDK.
//
// EDK consumers should NOT use these classes. Instead, use the DI-injected
// command interfaces (e.g., GetKeyServiceCommand) which resolve to:
//   - GetKeyServiceCommandImpl (local, via -impl module)
//   - GetKeyServiceCommandRouted (config-driven, via -remote module)
//
// These client classes use IDK's HttpServiceCommandTransport (JVM-only, Ktor CIO)
// with direct HTTP calls. EDK's routed transport uses HttpCommandClient with
// config-driven endpoint resolution, unified codecs, and consistent auth handling.
//
// Transport metadata is derived from the PublicApiCommand interface (httpEndpoint)
// declared on each command interface.

/**
 * HTTP client implementation of [GetKeyServiceCommand].
 *
 * GET /keys/{aliasOrKid}
 */
class GetKeyServiceCommandClient(
    transport: SessionBoundKmsCommandTransport,
) : KmsKeysTransportCommandClient(transport),
    GetKeyServiceCommand {
    override val id: String = GetKeyServiceCommand.COMMAND_ID
    override val isEnabled: Boolean = true
    override val commandId: String = GetKeyServiceCommand.COMMAND_ID
    override val inputTypeToken: TypeToken<GetKeyInput> = typeToken<GetKeyInput>()
    override val outputTypeToken: TypeToken<GetKeyResponse> = typeToken<GetKeyResponse>()

    override suspend fun execute(args: GetKeyInput): IdkResult<GetKeyResponse, IdkError> = invokeCommand(commandId = commandId, input = args, outputTypeToken = outputTypeToken)
}

/**
 * HTTP client implementation of [ListKeysServiceCommand].
 *
 * GET /keys
 */
class ListKeysServiceCommandClient(
    transport: SessionBoundKmsCommandTransport,
) : KmsKeysTransportCommandClient(transport),
    ListKeysServiceCommand {
    override val id: String = ListKeysServiceCommand.COMMAND_ID
    override val isEnabled: Boolean = true
    override val commandId: String = ListKeysServiceCommand.COMMAND_ID
    override val inputTypeToken: TypeToken<ListKeysInput> = typeToken<ListKeysInput>()
    override val outputTypeToken: TypeToken<ListKeysResponse> = typeToken<ListKeysResponse>()

    override suspend fun execute(args: ListKeysInput): IdkResult<ListKeysResponse, IdkError> = invokeCommand(commandId = commandId, input = args, outputTypeToken = outputTypeToken)
}

/**
 * HTTP client implementation of [StoreKeyServiceCommand].
 *
 * POST /keys
 */
class StoreKeyServiceCommandClient(
    transport: SessionBoundKmsCommandTransport,
) : KmsKeysTransportCommandClient(transport),
    StoreKeyServiceCommand {
    override val id: String = StoreKeyServiceCommand.COMMAND_ID
    override val isEnabled: Boolean = true
    override val commandId: String = StoreKeyServiceCommand.COMMAND_ID
    override val inputTypeToken: TypeToken<StoreKeyInput> = typeToken<StoreKeyInput>()
    override val outputTypeToken: TypeToken<StoreKeyResponse> = typeToken<StoreKeyResponse>()

    override suspend fun execute(args: StoreKeyInput): IdkResult<StoreKeyResponse, IdkError> = invokeCommand(commandId = commandId, input = args, outputTypeToken = outputTypeToken)
}

/**
 * HTTP client implementation of [GenerateKeyServiceCommand].
 *
 * POST /keys/generate
 */
class GenerateKeyServiceCommandClient(
    transport: SessionBoundKmsCommandTransport,
) : KmsKeysTransportCommandClient(transport),
    GenerateKeyServiceCommand {
    override val id: String = GenerateKeyServiceCommand.COMMAND_ID
    override val isEnabled: Boolean = true
    override val commandId: String = GenerateKeyServiceCommand.COMMAND_ID
    override val inputTypeToken: TypeToken<GenerateKeyInput> = typeToken<GenerateKeyInput>()
    override val outputTypeToken: TypeToken<GenerateKeyResponse> = typeToken<GenerateKeyResponse>()

    override suspend fun execute(args: GenerateKeyInput): IdkResult<GenerateKeyResponse, IdkError> = invokeCommand(commandId = commandId, input = args, outputTypeToken = outputTypeToken)
}

/**
 * HTTP client implementation of [DeleteKeyServiceCommand].
 *
 * DELETE /keys/{aliasOrKid}
 */
class DeleteKeyServiceCommandClient(
    transport: SessionBoundKmsCommandTransport,
) : KmsKeysTransportCommandClient(transport),
    DeleteKeyServiceCommand {
    override val id: String = DeleteKeyServiceCommand.COMMAND_ID
    override val isEnabled: Boolean = true
    override val commandId: String = DeleteKeyServiceCommand.COMMAND_ID
    override val inputTypeToken: TypeToken<DeleteKeyInput> = typeToken<DeleteKeyInput>()
    override val outputTypeToken: TypeToken<DeleteKeyOutput> = typeToken<DeleteKeyOutput>()

    override suspend fun execute(args: DeleteKeyInput): IdkResult<DeleteKeyOutput, IdkError> = invokeCommand(commandId = commandId, input = args, outputTypeToken = outputTypeToken)
}
