/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.command.par

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.CreatePushedAuthorizationResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreatePushedAuthorizationResponseCommand
import com.sphereon.oauth2.server.authorization.command.PushedAuthorizationResponse
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of CreatePushedAuthorizationResponseCommand
 *
 * Creates PAR endpoint responses according to RFC 9126 Section 2.2.
 *
 * Response format (JSON):
 * ```json
 * HTTP/1.1 201 Created
 * Content-Type: application/json
 * Cache-Control: no-store
 *
 * {
 *   "request_uri": "urn:ietf:params:oauth:request_uri:6esc_11ACC5bwc014ltc14eY22c",
 *   "expires_in": 90
 * }
 * ```
 *
 * Response parameters:
 * - request_uri (REQUIRED) - The request URI representing the pushed request
 * - expires_in (REQUIRED) - Lifetime in seconds (typically 90 seconds)
 *
 * The client then uses the request_uri in the authorization request:
 * ```
 * GET /authorize?client_id=CLIENT_ID&request_uri=urn:ietf:params:oauth:request_uri:...
 * ```
 *
 * This is a simple command that builds the response structure.
 * The actual request_uri generation and storage should be done before calling this command.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreatePushedAuthorizationResponseCommandImpl", exact = true)
class CreatePushedAuthorizationResponseCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<CreatePushedAuthorizationResponseArgs, PushedAuthorizationResponse>(
    commandId = CreatePushedAuthorizationResponseCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<CreatePushedAuthorizationResponseArgs>(),
    outputTypeToken = typeToken<PushedAuthorizationResponse>(),
), CreatePushedAuthorizationResponseCommand {

    override val commandId: String get() = CreatePushedAuthorizationResponseCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreatePushedAuthorizationResponseArgs

    override suspend fun doExecute(
        args: CreatePushedAuthorizationResponseArgs,
        applyDuring: (CreatePushedAuthorizationResponseArgs) -> CreatePushedAuthorizationResponseArgs
    ): IdkResult<PushedAuthorizationResponse, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.requestUri, applied.expiresIn).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        requestUri: String,
        expiresIn: Int
    ): IdkResult<PushedAuthorizationResponse, AuthorizationServerError> {
        // Build PAR response
        val response = PushedAuthorizationResponse(
            requestUri = requestUri,
            expiresIn = expiresIn
        )

        return Ok(response)
    }
}
