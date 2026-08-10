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

package com.sphereon.oauth2.server.authorization.impl.command.par

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.RetrieveAuthorizationRequestByUriCommand
import com.sphereon.oauth2.server.authorization.command.RetrieveByRequestUriArgs
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationRequest
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.storage.PushedAuthorizationRequestStorage
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of RetrieveAuthorizationRequestByUriCommand
 *
 * Retrieves stored authorization requests by request_uri for PAR (RFC 9126).
 *
 * When a client uses a request_uri in an authorization request:
 * ```
 * GET /authorize?client_id=CLIENT_ID&request_uri=urn:ietf:params:oauth:request_uri:ABC123
 * ```
 *
 * This command:
 * 1. Retrieves the stored authorization request by request_uri
 * 2. Verifies the request has not expired
 * 3. Verifies the request has not been used (single-use or limited-use)
 * 4. Verifies the client_id matches the stored request
 * 5. Marks the request as used (or consumes it)
 * 6. Returns the verified authorization request
 *
 * Security considerations:
 * - request_uri MUST be validated before retrieval
 * - Stored requests MUST be short-lived (typically 90 seconds)
 * - Stored requests SHOULD be single-use
 * - Client binding MUST be verified
 * - Expired requests MUST be rejected
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("RetrieveAuthorizationRequestByUriCommandImpl", exact = true)
class RetrieveAuthorizationRequestByUriCommandImpl(
    execution: SessionExecution,
    private val pushedAuthorizationRequestStorage: PushedAuthorizationRequestStorage,
) : TypedServiceCommandAdapter<RetrieveByRequestUriArgs, VerifiedAuthorizationRequest, IdkError>(
        commandId = RetrieveAuthorizationRequestByUriCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RetrieveByRequestUriArgs>(),
        outputTypeToken = typeToken<VerifiedAuthorizationRequest>(),
    ),
    RetrieveAuthorizationRequestByUriCommand {
    override val commandId: String get() = RetrieveAuthorizationRequestByUriCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is RetrieveByRequestUriArgs

    override suspend fun doExecute(
        args: RetrieveByRequestUriArgs,
        applyDuring: (RetrieveByRequestUriArgs) -> RetrieveByRequestUriArgs,
    ): IdkResult<VerifiedAuthorizationRequest, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.requestUri).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(requestUri: String): IdkResult<VerifiedAuthorizationRequest, AuthorizationServerError> {
        // Verify request_uri format (RFC 9126 Section 2.2)
        if (!requestUri.startsWith("urn:ietf:params:oauth:request_uri:")) {
            return Err(
                AuthorizationServerError.InvalidRequestUri(
                    details = "Invalid request_uri format",
                ),
            )
        }

        // FAPI 2.0 SP §5.3.2.2 Note 3: single-use is enforced at the *authorization* step
        // (auth-code issuance), NOT at every `/authorize` visit. We only peek here so the
        // user can hit the endpoint twice in a row, navigate back, refresh, etc. The actual
        // consume happens in `CreateAuthorizationCodeCommandImpl` once a code is being minted.
        val lookupResult = pushedAuthorizationRequestStorage.lookupRequest(requestUri)
        if (lookupResult.isErr) {
            return Err(
                AuthorizationServerError.ServerError(
                    details = "Failed to look up pushed authorization request: ${lookupResult.error.details}",
                    exception = lookupResult.error.exception,
                ),
            )
        }
        val stored =
            lookupResult.value
                ?: return Err(
                    AuthorizationServerError.InvalidRequestUri(
                        details = "Pushed authorization request_uri not found, expired, or already consumed: $requestUri",
                    ),
                )
        return Ok(stored)
    }
}
