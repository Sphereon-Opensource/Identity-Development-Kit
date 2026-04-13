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
import com.sphereon.openid.oid4vci.holder.rest.AuthorizationRequestResponse
import com.sphereon.openid.oid4vci.holder.rest.BuildAuthorizationRequestRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString

/**
 * Command interface for building an authorization request URL with PKCE.
 *
 * POST /auth/request
 *
 * Builds an OID4VCI Authorization Code Flow authorization request URL.
 */
interface BuildAuthRequestEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.client.authRequest"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/auth/request",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "buildAuthorizationRequest",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-holder"),
                summary = "Build an authorization request URL with PKCE",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<BuildAuthRequestEndpointCommand>())
class BuildAuthRequestEndpointCommandImpl(
    execution: SessionExecution,
    private val clientService: Oid4vciHolderService,
) : HttpEndpointCommandAdapter(
        id = BuildAuthRequestEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = BuildAuthRequestEndpointCommand.ENDPOINT,
    ),
    BuildAuthRequestEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        val authRequest =
            try {
                holderJson.decodeFromString<BuildAuthorizationRequestRequest>(request.body ?: "{}")
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed authorization request: ${expected.message}"))
            }

        return clientService
            .buildAuthorizationRequest(
                authorizationEndpoint = authRequest.authorizationEndpoint,
                clientId = authRequest.clientId,
                redirectUri = authRequest.redirectUri,
                credentialConfigurationIds = authRequest.credentialConfigurationIds,
                scope = authRequest.scope,
                issuerState = authRequest.issuerState,
                usePar = authRequest.usePar,
                parEndpoint = authRequest.parEndpoint,
                credentialIdentifiers = authRequest.credentialIdentifiers,
                locations = authRequest.locations,
            ).map { result ->
                val response =
                    AuthorizationRequestResponse(
                        authorizationUrl = result.authorizationUrl,
                        codeVerifier = result.codeVerifier,
                        state = result.state,
                    )
                jsonResponse(200, holderJson.encodeToString(response))
            }
    }
}
