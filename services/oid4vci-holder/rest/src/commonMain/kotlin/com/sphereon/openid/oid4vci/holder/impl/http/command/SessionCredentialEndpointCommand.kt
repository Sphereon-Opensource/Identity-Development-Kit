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
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.jsonResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import com.sphereon.openid.oid4vci.holder.CredentialFlowResult
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSessionStatus
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSessionStore
import com.sphereon.openid.oid4vci.holder.Oid4vciIssuanceFlowService
import com.sphereon.openid.oid4vci.holder.RequestCredentialWithFlowArgs
import com.sphereon.openid.oid4vci.holder.rest.SessionCredentialFlowResponse
import com.sphereon.openid.oid4vci.holder.rest.SessionCredentialRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString

/**
 * Command interface for requesting a credential within a session context.
 *
 * POST /sessions/{id}/credentials
 *
 * Uses the session's access token to drive the full credential issuance flow including
 * auto-proof creation, optional nonce fetch, deferred polling, and notification.
 */
interface SessionCredentialEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.client.sessionCredential"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/sessions/{id}/credentials",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "sessionRequestCredential",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-holder-session"),
                summary = "Request a credential using the session's access token",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SessionCredentialEndpointCommand>())
class SessionCredentialEndpointCommandImpl(
    execution: SessionExecution,
    private val sessionStore: Oid4vciHolderSessionStore,
    private val flowService: Oid4vciIssuanceFlowService,
) : HttpEndpointCommandAdapter(
        id = SessionCredentialEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = SessionCredentialEndpointCommand.ENDPOINT,
    ),
    SessionCredentialEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val req = request.withExtractedParams("/sessions/{id}/credentials")

        val sessionId = req.requirePathParam("id").getOrElse { return Err(it) }

        val credReq =
            try {
                holderJson.decodeFromString<SessionCredentialRequest>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed credential request: ${expected.message}"))
            }

        val session =
            sessionStore.get(sessionId).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Session $sessionId not found"))

        val accessToken =
            session.accessToken
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Session $sessionId has no access token; exchange token first"))

        val flowArgs =
            RequestCredentialWithFlowArgs(
                sessionId = sessionId,
                credentialEndpoint = credReq.credentialEndpoint,
                accessToken = accessToken,
                issuerUrl = session.issuerUrl,
                signingKeyId = credReq.signingKeyId,
                signingAlgorithm = credReq.signingAlgorithm,
                credentialConfigurationId = credReq.credentialConfigurationId,
                credentialIdentifier = credReq.credentialIdentifier,
                nonceEndpoint = credReq.nonceEndpoint,
                deferredCredentialEndpoint = credReq.deferredCredentialEndpoint,
                notificationEndpoint = credReq.notificationEndpoint,
                credentialResponseEncryption = credReq.credentialResponseEncryption,
            )

        val result =
            flowService.requestCredentialWithFlow(flowArgs).getOrElse { error ->
                return Err(error)
            }

        val (newStatus, responseBody) =
            when (result) {
                is CredentialFlowResult.Immediate -> {
                    Oid4vciHolderSessionStatus.CREDENTIAL_RECEIVED to
                        SessionCredentialFlowResponse(
                            outcome = "immediate",
                            credential = result.credential,
                            notificationSent = result.notificationSent,
                        )
                }

                is CredentialFlowResult.DeferredCompleted -> {
                    Oid4vciHolderSessionStatus.CREDENTIAL_RECEIVED to
                        SessionCredentialFlowResponse(
                            outcome = "deferred_completed",
                            credential = result.credential,
                            pollAttempts = result.pollAttempts,
                            notificationSent = result.notificationSent,
                        )
                }

                is CredentialFlowResult.DeferredExhausted -> {
                    Oid4vciHolderSessionStatus.DEFERRED_PENDING to
                        SessionCredentialFlowResponse(
                            outcome = "deferred_exhausted",
                            transactionId = result.transactionId,
                            attemptsMade = result.attemptsMade,
                            lastInterval = result.lastInterval,
                        )
                }
            }
        sessionStore.update(session.copy(status = newStatus))
        return Ok(jsonResponse(200, holderJson.encodeToString(responseBody)))
    }
}
