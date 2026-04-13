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
import com.sphereon.core.api.Ok
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
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrorResponse
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vci.holder.rest.DeferredCredentialRequestBody
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString

/**
 * Command interface for polling the issuer's deferred credential endpoint.
 *
 * POST /deferred
 *
 * Polls for a pending credential. Returns the credential when ready, or 202 when still pending.
 */
interface RequestDeferredEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.client.deferred"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/deferred",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "requestDeferredCredential",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-holder"),
                summary = "Poll the deferred credential endpoint",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RequestDeferredEndpointCommand>())
class RequestDeferredEndpointCommandImpl(
    execution: SessionExecution,
    private val clientService: Oid4vciHolderService,
) : HttpEndpointCommandAdapter(
        id = RequestDeferredEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = RequestDeferredEndpointCommand.ENDPOINT,
    ),
    RequestDeferredEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        val deferredRequest =
            try {
                holderJson.decodeFromString<DeferredCredentialRequestBody>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed deferred credential request: ${expected.message}"))
            }

        val response =
            clientService
                .requestDeferredCredential(
                    deferredCredentialEndpoint = deferredRequest.deferredCredentialEndpoint,
                    accessToken = deferredRequest.accessToken,
                    transactionId = deferredRequest.transactionId,
                    credentialResponseEncryption = deferredRequest.credentialResponseEncryption,
                    requestEncryptionJwk = deferredRequest.requestEncryptionJwk,
                    requestEncryptionAlg = deferredRequest.requestEncryptionAlg,
                    requestEncryptionEnc = deferredRequest.requestEncryptionEnc,
                    decryptionKeyId = deferredRequest.decryptionKeyId,
                ).getOrElse { error ->
                    val errorMsg = error.message.defaultMessage
                    return when {
                        errorMsg == Oid4vciErrors.ISSUANCE_PENDING || error.code == Oid4vciErrors.ISSUANCE_PENDING -> {
                            Ok(
                                GenericHttpResponse(
                                    statusCode = 202,
                                    headers = JSON_HEADERS,
                                    body =
                                        holderJson.encodeToString(
                                            Oid4vciErrorResponse(
                                                error = Oid4vciErrors.ISSUANCE_PENDING,
                                                errorDescription = "Credential issuance is still pending",
                                                interval = 5,
                                            ),
                                        ),
                                ),
                            )
                        }

                        else -> {
                            Err(error)
                        }
                    }
                }

        return Ok(jsonResponse(200, holderJson.encodeToString(response)))
    }
}
