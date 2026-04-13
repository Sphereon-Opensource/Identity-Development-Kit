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
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.issuer.command.HandleCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleCredentialRequestCommand
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive

/**
 * Endpoint command for handling credential requests.
 *
 * POST /credential
 *
 * Handles both JSON and JWE-encrypted request bodies per OID4VCI 1.1.
 */
interface HandleCredentialEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.protocol.credential"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/credential",
                consumes = setOf(MediaType.ApplicationJson, MediaType.Custom(ACCEPT_JWT)),
                produces = setOf(MediaType.ApplicationJson, MediaType.Custom(ACCEPT_JWT)),
                operationId = "handleCredentialRequest",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer"),
                summary = "Handle a credential request",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleCredentialEndpointCommand>())
class HandleCredentialEndpointCommandImpl(
    execution: SessionExecution,
    private val handleCredentialRequestCommand: HandleCredentialRequestCommand,
    private val decryptJweCommand: DecryptJweCommand,
    private val configProvider: Oid4vciIssuerConfigProvider,
) : HttpEndpointCommandAdapter(
        id = HandleCredentialEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = HandleCredentialEndpointCommand.ENDPOINT,
    ),
    HandleCredentialEndpointCommand {
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

        log.info("Credential request body: $requestBody")

        val credentialRequest =
            try {
                protocolJson.decodeFromString<CredentialRequest>(requestBody)
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed credential request: ${expected.message}"))
            }

        val response =
            handleCredentialRequestCommand
                .execute(
                    HandleCredentialRequestArgs(
                        accessToken = accessToken,
                        dpopProof = dpopProof,
                        credentialRequest = credentialRequest,
                        issuerIdentifier = configProvider.issuerIdentifier,
                        credentialConfigurations = configProvider.credentialConfigurations,
                    ),
                ).getOrElse { error ->
                    log.info("Credential error: ${error.code} - ${error.message.defaultMessage}")
                    return Err(error)
                }

        // OID4VCI 1.1 Section 8.3: When the response is encrypted, return the raw
        // JWE compact string with Content-Type: application/jwt
        val credentialElement = response.credential
        if (credentialRequest.credentialResponseEncryption != null &&
            credentialElement is JsonPrimitive
        ) {
            val jweCandidate = credentialElement.content
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
        val responseJson = protocolJson.encodeToString(response)
        log.info("Credential response: $responseJson")
        return Ok(jsonResponse(200, responseJson))
    }
}
