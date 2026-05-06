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
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.CreateRequestUriCommand
import com.sphereon.oauth2.server.authorization.command.RequestUriData
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationRequest
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.storage.PushedAuthorizationRequestStorage
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of CreateRequestUriCommand
 *
 * Creates request URIs for Pushed Authorization Requests (PAR) according to RFC 9126.
 *
 * After a client pushes authorization request parameters to the PAR endpoint,
 * the server generates a request_uri that represents those parameters.
 *
 * Request URI format (RFC 9126 Section 2.2):
 * ```
 * urn:ietf:params:oauth:request_uri:<unique-identifier>
 * ```
 *
 * The request_uri is:
 * - Short-lived (typically 90 seconds, MUST be between 5 seconds and 10 minutes)
 * - Single-use (consumed when used in authorization request)
 * - Bound to the authenticated client
 * - Stored with the full authorization request parameters
 *
 * The request_uri is then used in the authorization request:
 * ```
 * GET /authorize?client_id=CLIENT_ID&request_uri=urn:ietf:params:oauth:request_uri:ABC123
 * ```
 *
 * Security considerations:
 * - Request URIs MUST be short-lived
 * - Request URIs MUST be single-use (or very limited use)
 * - Request URIs MUST be bound to the client that pushed them
 * - Request URIs MUST be unguessable (cryptographically random)
 * - Stored requests MUST include all original parameters for validation
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateRequestUriCommandImpl", exact = true)
class CreateRequestUriCommandImpl(
    execution: SessionExecution,
    private val secureRandom: SecureRandom,
    private val pushedAuthorizationRequestStorage: PushedAuthorizationRequestStorage,
    private val clock: Clock,
    // 60 seconds default (RFC 9126 §2.2 recommends a short lifetime; harness-friendly).
    private val requestUriLifetimeSeconds: Int = 60,
) : TypedServiceCommandAdapter<VerifiedAuthorizationRequest, RequestUriData, IdkError>(
        commandId = CreateRequestUriCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifiedAuthorizationRequest>(),
        outputTypeToken = typeToken<RequestUriData>(),
    ),
    CreateRequestUriCommand {
    override val commandId: String get() = CreateRequestUriCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifiedAuthorizationRequest

    override suspend fun doExecute(
        args: VerifiedAuthorizationRequest,
        applyDuring: (VerifiedAuthorizationRequest) -> VerifiedAuthorizationRequest,
    ): IdkResult<RequestUriData, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    // Note: Storage is handled by the caller via backing storage
    // This implementation assumes the stored request is passed in
    // In a full implementation, this would inject a RequestUriStorage interface

    private suspend fun executeInternal(request: VerifiedAuthorizationRequest): IdkResult<RequestUriData, AuthorizationServerError> {
        // Generate cryptographically secure random identifier
        val identifier = generateSecureIdentifier()

        // Build request_uri according to RFC 9126 format
        val requestUri = "urn:ietf:params:oauth:request_uri:$identifier"

        // RFC 9126 §2.2: persist the verified request under the issued URN with a short TTL so
        // /authorize can retrieve and consume it. Storage failure is bubbled up so the client
        // gets a server_error response rather than a request_uri the AS cannot honour later.
        val expiresAt = clock.now() + requestUriLifetimeSeconds.seconds
        val storeResult = pushedAuthorizationRequestStorage.storeRequest(requestUri, request, expiresAt)
        if (storeResult.isErr) {
            return Err(
                AuthorizationServerError.ServerError(
                    details = "Failed to persist pushed authorization request: ${storeResult.error.details}",
                    exception = storeResult.error.exception,
                ),
            )
        }

        return Ok(
            RequestUriData(
                requestUri = requestUri,
                expiresIn = requestUriLifetimeSeconds,
                authorizationRequest = request,
            ),
        )
    }

    /**
     * Generate a cryptographically secure random identifier.
     * 32 bytes (256 bits) of entropy, base64url encoded.
     */
    private suspend fun generateSecureIdentifier(): String = secureRandom.newToken()
}
