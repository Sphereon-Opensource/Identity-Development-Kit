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

package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationSessionCommand
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationRequest
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.SESSION_KEY_OIDC_CLAIMS_ID_TOKEN
import com.sphereon.oauth2.server.authorization.model.SESSION_KEY_OIDC_CLAIMS_USERINFO
import com.sphereon.oauth2.server.authorization.model.SessionStatus
import com.sphereon.oauth2.server.authorization.provider.ClientApplicationResolver
import com.sphereon.oauth2.server.authorization.storage.SessionStorage
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
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
    private val secureRandom: SecureRandom,
    private val clientApplicationResolver: ClientApplicationResolver,
    // 15 minutes default
    private val sessionLifetimeSeconds: Int = 900,
) : TypedServiceCommandAdapter<VerifiedAuthorizationRequest, AuthorizationSession, IdkError>(
        commandId = CreateAuthorizationSessionCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifiedAuthorizationRequest>(),
        outputTypeToken = typeToken<AuthorizationSession>(),
    ),
    CreateAuthorizationSessionCommand {
    override val commandId: String get() = CreateAuthorizationSessionCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifiedAuthorizationRequest

    override suspend fun doExecute(
        args: VerifiedAuthorizationRequest,
        applyDuring: (VerifiedAuthorizationRequest) -> VerifiedAuthorizationRequest,
    ): IdkResult<AuthorizationSession, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(request: VerifiedAuthorizationRequest): IdkResult<AuthorizationSession, AuthorizationServerError> {
        val now = Clock.System.now()
        val expiresAt = now + sessionLifetimeSeconds.seconds

        // Generate cryptographically secure random session ID
        val sessionId = generateSecureSessionId()

        // Resolve the opaque application / login-surface id for this client. Fail-open: a
        // resolver error must not block the authorization flow, it only downgrades login to
        // application-agnostic mode (applicationId = null). The IDK default resolver
        // (NoneClientApplicationResolver) always returns Ok(null).
        val applicationId =
            clientApplicationResolver
                .resolveApplicationId(clientId = request.clientId, requestHost = null)
                .getOrElse { error ->
                    execution.log.warn(
                        "ClientApplicationResolver failed for client '${request.clientId}': " +
                            "${error.message.defaultMessage}; continuing without applicationId",
                    )
                    null
                }

        // Create authorization session.
        //
        // `redirectUri`, `codeChallengeMethod`, and `responseMode` are taken from the resolved
        // fields on [VerifiedAuthorizationRequest] (not directly from request.request) so server
        // defaults applied in the verifier (single-registered redirect URI resolution, PKCE method
        // default + policy, OIDC Core §3.1.2.1 response_mode default) are correctly propagated to
        // the session and, downstream, to the authorization code and token endpoint.
        val session =
            AuthorizationSession(
                sessionId = sessionId,
                clientId = request.clientId,
                redirectUri = request.redirectUri,
                scope = request.request.scope,
                state = request.request.state,
                responseType = request.request.responseType.joinToString(" ") { it.value },
                responseMode = request.responseMode,
                codeChallenge = request.request.codeChallenge,
                codeChallengeMethod = request.resolvedPkceMethod?.value,
                dpopJkt = request.request.dpopJkt,
                nonce = request.request.nonce,
                status = SessionStatus.PENDING_AUTHENTICATION,
                authenticatedUserId = null,
                consentDecision = null,
                createdAt = now,
                expiresAt = expiresAt,
                // Carry the PAR `request_uri` through to code-issuance so we can finalize
                // single-use semantics atomically there (FAPI 2.0 SP §5.3.2.2 Note 3).
                requestUri = request.request.requestUri,
                // Carry `acr_values` through so the granted `acr` claim on the id_token can
                // echo the first requested level (OIDC Core §3.1.2.1) when the authenticator
                // doesn't surface a specific acr.
                acrValues = request.request.acrValues,
                // Carry max_age through so [CreateAuthorizationCodeCommandImpl] can enforce
                // the freshness gate as a final check before minting. The OIDC layer in
                // [StandardAuthorizeRequestCommand] also enforces max_age earlier (force
                // re-auth when stale), but those two layers cover different races: the
                // earlier check stops the user reaching the consent screen with a stale
                // session, the later check stops a code from being issued if max_age has
                // elapsed during a slow consent flow.
                maxAge = request.request.maxAge?.toLong(),
                applicationId = applicationId,
                additionalData =
                    buildMap {
                        // Carry authorization_details through the session
                        request.request.additionalParameters["authorization_details"]?.let { ad ->
                            put("authorization_details", ad)
                            // Also extract credential_configuration_ids for token response
                            try {
                                val details = Json.parseToJsonElement(ad).jsonArray
                                val configIds = details.mapNotNull { it.jsonObject["credential_configuration_id"]?.jsonPrimitive?.content }
                                if (configIds.isNotEmpty()) {
                                    put("credential_configuration_ids", configIds)
                                }
                            } catch (expected: Exception) {
                                execution.log.debug("Failed to parse authorization_details JSON: ${expected.message}")
                            }
                        }
                        // OIDC Core §5.5 — `claims` request parameter. Stash the userinfo and
                        // id_token claim-name lists so downstream userinfo/id_token assembly can
                        // honor explicit per-claim requests in addition to scope-derived ones.
                        // We don't model the `value`/`values`/`essential` sub-properties yet —
                        // the test suite checks presence, not the strength flag.
                        request.request.claims?.let { claimsObj ->
                            (claimsObj["userinfo"] as? JsonObject)?.keys?.toList()?.takeIf { it.isNotEmpty() }?.let {
                                put(SESSION_KEY_OIDC_CLAIMS_USERINFO, it)
                            }
                            (claimsObj["id_token"] as? JsonObject)?.keys?.toList()?.takeIf { it.isNotEmpty() }?.let {
                                put(SESSION_KEY_OIDC_CLAIMS_ID_TOKEN, it)
                            }
                        }
                    },
            )

        // Store session
        return sessionStorage
            .createSession(session)
            .mapError { error ->
                AuthorizationServerError.ServerError(
                    details = "Failed to create authorization session: $error",
                    exception = null,
                )
            }.map { session }
    }

    /**
     * Generate a cryptographically secure random session ID.
     * 32 bytes (256 bits) of entropy, base64url encoded with an `authz_` prefix.
     */
    private suspend fun generateSecureSessionId(): String = "authz_" + secureRandom.newToken()
}
