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

package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationSessionCommand
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationRequest
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.SessionStatus
import com.sphereon.oauth2.server.authorization.storage.SessionStorage
import kotlinx.datetime.Clock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of CreateAuthorizationSessionCommand
 *
 * Creates authorization sessions for tracking multi-step authorization flows.
 *
 * Authorization sessions are used to track the state of an authorization request
 * across multiple HTTP requests:
 * 1. Initial authorization request
 * 2. User authentication (redirect to login)
 * 3. User consent (redirect to consent page)
 * 4. Final authorization code generation
 *
 * Session lifecycle:
 * - PENDING_AUTHENTICATION: Waiting for user to authenticate
 * - PENDING_CONSENT: Waiting for user consent
 * - AUTHORIZED: User has granted consent
 * - DENIED: User has denied consent
 * - EXPIRED: Session has expired
 * - COMPLETED: Authorization code has been issued
 *
 * Sessions are short-lived (typically 10-15 minutes) and stored in SessionStorage.
 *
 * Security considerations:
 * - Session IDs MUST be cryptographically secure random strings
 * - Sessions MUST be short-lived
 * - Sessions MUST be bound to the original request parameters
 * - Session state MUST be validated on each step
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateAuthorizationSessionCommandImpl", exact = true)
class CreateAuthorizationSessionCommandImpl(
    execution: SessionExecution,
    private val sessionStorage: SessionStorage,
    private val sessionLifetimeSeconds: Int = 900  // 15 minutes default
) : TypedServiceCommandAdapter<VerifiedAuthorizationRequest, AuthorizationSession>(
    commandId = CreateAuthorizationSessionCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<VerifiedAuthorizationRequest>(),
    outputTypeToken = typeToken<AuthorizationSession>(),
), CreateAuthorizationSessionCommand {

    override val commandId: String get() = CreateAuthorizationSessionCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifiedAuthorizationRequest

    override suspend fun doExecute(
        args: VerifiedAuthorizationRequest,
        applyDuring: (VerifiedAuthorizationRequest) -> VerifiedAuthorizationRequest
    ): IdkResult<AuthorizationSession, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        request: VerifiedAuthorizationRequest
    ): IdkResult<AuthorizationSession, AuthorizationServerError> {
        val now = Clock.System.now()
        val expiresAt = now + sessionLifetimeSeconds.seconds

        // Generate cryptographically secure random session ID
        val sessionId = generateSecureSessionId()

        // Create authorization session
        val session = AuthorizationSession(
            sessionId = sessionId,
            clientId = request.clientId,
            redirectUri = request.redirectUri,
            scope = request.request.scope,
            state = request.request.state,
            responseType = request.request.responseType.joinToString(" ") { it.value },
            codeChallenge = request.request.codeChallenge,
            codeChallengeMethod = request.request.codeChallengeMethod?.value,
            dpopJkt = request.request.dpopJkt,
            nonce = request.request.nonce,
            status = SessionStatus.PENDING_AUTHENTICATION,
            authenticatedUserId = null,
            consentDecision = null,
            createdAt = now,
            expiresAt = expiresAt
        )

        // Store session
        return sessionStorage.createSession(session)
            .mapError { error ->
                AuthorizationServerError.ServerError(
                    details = "Failed to create authorization session: ${error}",
                    exception = null
                )
            }
            .map { session }
    }

    /**
     * Generate a cryptographically secure random session ID
     * 32 bytes (256 bits) of entropy, base64url encoded
     */
    private fun generateSecureSessionId(): String {
        val randomBytes = Random.Default.nextBytes(32)
        return "authz_" + randomBytes.encodeToBase64Url()
    }
}
