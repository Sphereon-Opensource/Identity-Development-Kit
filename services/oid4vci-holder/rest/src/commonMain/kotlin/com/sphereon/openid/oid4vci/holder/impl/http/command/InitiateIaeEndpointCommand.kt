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
import com.sphereon.openid.oid4vci.holder.InitiateIaeArgs
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vci.holder.rest.InitiateIaeRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString

/**
 * Command interface for initiating an OID4VCI 1.1 Interactive Authorization Endpoint (IAE) flow.
 *
 * POST /iae/initiate
 *
 * Converts credential configuration IDs to authorization_details and submits to the IAE endpoint.
 */
interface InitiateIaeEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.client.initiateIae"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/iae/initiate",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "initiateIae",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-holder"),
                summary = "Initiate an IAE flow",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<InitiateIaeEndpointCommand>())
class InitiateIaeEndpointCommandImpl(
    execution: SessionExecution,
    private val clientService: Oid4vciHolderService,
) : HttpEndpointCommandAdapter(
        id = InitiateIaeEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = InitiateIaeEndpointCommand.ENDPOINT,
    ),
    InitiateIaeEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        val iaeRequest =
            try {
                holderJson.decodeFromString<InitiateIaeRequest>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed IAE initiate request: ${expected.message}"))
            }

        val authorizationDetails = buildAuthorizationDetailsFromConfigIds(iaeRequest.credentialConfigurationIds)

        return clientService
            .initiateIae(
                InitiateIaeArgs(
                    iaeEndpoint = iaeRequest.iaeEndpoint,
                    clientId = iaeRequest.clientId,
                    redirectUri = iaeRequest.redirectUri,
                    interactionTypesSupported = iaeRequest.interactionTypesSupported,
                    authorizationDetails = authorizationDetails,
                    scope = iaeRequest.scope,
                ),
            ).map { result -> jsonResponse(200, holderJson.encodeToString(result)) }
    }
}
