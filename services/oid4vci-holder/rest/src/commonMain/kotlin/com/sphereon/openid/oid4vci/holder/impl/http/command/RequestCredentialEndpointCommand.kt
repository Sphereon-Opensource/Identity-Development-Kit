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

package com.sphereon.openid.oid4vci.holder.impl.http.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.jsonResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vci.holder.rest.CredentialRequestBody
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString

/**
 * Command interface for requesting a credential from the issuer.
 *
 * POST /credential
 *
 * Sends a credential request to the issuer's credential endpoint using the provided access token.
 */
interface RequestCredentialEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.client.credential"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/credential",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "requestCredential",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-holder"),
                summary = "Request a credential from the issuer",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RequestCredentialEndpointCommand>())
class RequestCredentialEndpointCommandImpl(
    execution: SessionExecution,
    private val clientService: Oid4vciHolderService,
) : HttpEndpointCommandAdapter(
        id = RequestCredentialEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = RequestCredentialEndpointCommand.ENDPOINT,
    ),
    RequestCredentialEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        val credentialRequest =
            try {
                holderJson.decodeFromString<CredentialRequestBody>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed credential request: ${expected.message}"))
            }

        return clientService
            .requestCredential(
                credentialEndpoint = credentialRequest.credentialEndpoint,
                accessToken = credentialRequest.accessToken,
                credentialConfigurationId = credentialRequest.credentialConfigurationId,
                credentialIdentifier = credentialRequest.credentialIdentifier,
                proofs = credentialRequest.proofs,
                credentialResponseEncryption = credentialRequest.credentialResponseEncryption,
                requestEncryptionJwk = credentialRequest.requestEncryptionJwk,
                requestEncryptionAlg = credentialRequest.requestEncryptionAlg,
                requestEncryptionEnc = credentialRequest.requestEncryptionEnc,
                decryptionKeyId = credentialRequest.decryptionKeyId,
            ).map { response -> jsonResponse(200, holderJson.encodeToString(response)) }
    }
}
