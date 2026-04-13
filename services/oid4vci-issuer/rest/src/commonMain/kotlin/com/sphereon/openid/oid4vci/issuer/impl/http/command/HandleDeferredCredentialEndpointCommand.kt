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

package com.sphereon.openid.oid4vci.issuer.impl.http.command

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
import com.sphereon.crypto.jose.jwe.DecryptJweCommand
import com.sphereon.crypto.jose.jwe.JweCompact
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.DeferredCredentialRequest
import com.sphereon.openid.oid4vci.issuer.command.HandleDeferredCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleDeferredCredentialRequestCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive

/**
 * Endpoint command for handling deferred credential requests.
 *
 * POST /deferredCredential
 *
 * OID4VCI 1.1 Section 10.2: pending responses carry transaction_id + interval
 * but no credential — return 202, otherwise 200.
 */
interface HandleDeferredCredentialEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.protocol.deferred"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/deferredCredential",
                consumes = setOf(MediaType.ApplicationJson, MediaType.Custom(ACCEPT_JWT)),
                produces = setOf(MediaType.ApplicationJson, MediaType.Custom(ACCEPT_JWT)),
                operationId = "handleDeferredCredentialRequest",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer"),
                summary = "Handle a deferred credential request",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleDeferredCredentialEndpointCommand>())
class HandleDeferredCredentialEndpointCommandImpl(
    execution: SessionExecution,
    private val handleDeferredCommand: HandleDeferredCredentialRequestCommand,
    private val decryptJweCommand: DecryptJweCommand,
) : HttpEndpointCommandAdapter(
        id = HandleDeferredCredentialEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = HandleDeferredCredentialEndpointCommand.ENDPOINT,
    ),
    HandleDeferredCredentialEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        val accessToken =
            extractBearerToken(request)
                ?: return Err(IdkError.UNAUTHORIZED_ERROR(message = "Missing or invalid Authorization header"))
        val dpopProof = request.headers["DPoP"] ?: request.headers["dpop"]

        val requestBody =
            decryptRequestIfNeeded(request, decryptJweCommand)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to decrypt JWE request body"))

        val deferredRequest =
            try {
                protocolJson.decodeFromString<DeferredCredentialRequest>(requestBody)
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed deferred credential request: ${expected.message}"))
            }

        val response =
            handleDeferredCommand
                .execute(
                    HandleDeferredCredentialRequestArgs(
                        accessToken = accessToken,
                        dpopProof = dpopProof,
                        deferredRequest = deferredRequest,
                    ),
                ).getOrElse { error -> return Err(error) }

        // OID4VCI 1.1 Section 8.3: When the response is encrypted, return the raw
        // JWE compact string with Content-Type: application/jwt
        val deferredCredentialElement = response.credential
        if (deferredRequest.credentialResponseEncryption != null &&
            deferredCredentialElement is JsonPrimitive
        ) {
            val jweCandidate = deferredCredentialElement.content
            if (JweCompact.isValidCompactFormat(jweCandidate)) {
                return Ok(
                    GenericHttpResponse(
                        statusCode = 200,
                        headers = JWT_HEADERS,
                        body = jweCandidate,
                    ),
                )
            }
        }

        // OID4VCI 1.1 Section 10.2: a pending response carries transaction_id + interval
        // but no credential. Return 202 in that case, 200 when the credential is ready.
        return if (response.transactionId != null && response.credential == null && response.credentials == null) {
            Ok(
                GenericHttpResponse(
                    statusCode = 202,
                    headers = JSON_HEADERS,
                    body = protocolJson.encodeToString(response),
                ),
            )
        } else {
            Ok(jsonResponse(200, protocolJson.encodeToString(response)))
        }
    }
}
