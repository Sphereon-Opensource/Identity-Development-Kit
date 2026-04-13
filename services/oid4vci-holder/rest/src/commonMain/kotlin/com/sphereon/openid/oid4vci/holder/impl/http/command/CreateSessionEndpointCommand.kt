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
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSession
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSessionStatus
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSessionStore
import com.sphereon.openid.oid4vci.holder.rest.CreateSessionRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Command interface for creating a new OID4VCI holder session.
 *
 * POST /sessions
 *
 * Parses and resolves a credential offer, creates a session with status OFFER_RESOLVED.
 */
interface CreateSessionEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.client.createSession"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/sessions",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "createSession",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-holder-session"),
                summary = "Create a new OID4VCI holder session from a credential offer",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateSessionEndpointCommand>())
class CreateSessionEndpointCommandImpl(
    execution: SessionExecution,
    private val clientService: Oid4vciHolderService,
    private val sessionStore: Oid4vciHolderSessionStore,
) : HttpEndpointCommandAdapter(
        id = CreateSessionEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = CreateSessionEndpointCommand.ENDPOINT,
    ),
    CreateSessionEndpointCommand {
    companion object {
        private const val SESSION_TTL_SECONDS = 3600L
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        val createRequest =
            try {
                holderJson.decodeFromString<CreateSessionRequest>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed create session request: ${expected.message}"))
            }

        val offer =
            clientService.parseCredentialOffer(createRequest.offerUri).getOrElse { error ->
                return Err(error)
            }
        val resolved =
            clientService.resolveCredentialOffer(offer).getOrElse { error ->
                return Err(error)
            }

        val sessionId = Uuid.random().toString()
        val now = Clock.System.now().epochSeconds
        val session =
            Oid4vciHolderSession(
                sessionId = sessionId,
                issuerUrl = resolved.issuerMetadata.credentialIssuer,
                credentialConfigurationIds = offer.credentialConfigurationIds,
                status = Oid4vciHolderSessionStatus.OFFER_RESOLVED,
                preAuthorizedCode = offer.grants?.preAuthorizedCode?.preAuthorizedCode,
                issuerState = offer.grants?.authorizationCode?.issuerState,
                createdAt = now,
                expiresAt = now + SESSION_TTL_SECONDS,
            )
        return sessionStore.create(session).map { stored ->
            jsonResponse(201, holderJson.encodeToString(stored))
        }
    }
}
