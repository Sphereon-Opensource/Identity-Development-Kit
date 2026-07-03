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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.AuthAssuranceLevel
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.AuthorizationCodeData
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.ConsentDecision
import com.sphereon.oauth2.server.authorization.requiredaction.RequiredActionEvaluator
import com.sphereon.oauth2.server.authorization.stepup.OAuth2AcrEnforcementResult
import com.sphereon.oauth2.server.authorization.stepup.OAuth2AcrEnforcer
import com.sphereon.oauth2.server.authorization.storage.AuthorizationCodeStorage
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionIdProvider
import com.sphereon.oauth2.server.authorization.storage.PushedAuthorizationRequestStorage
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of CreateAuthorizationCodeCommand
 *
 * Creates authorization codes according to RFC 6749 Section 4.1.2.
 *
 * Authorization codes are short-lived credentials that represent the resource owner's
 * authorization to access their protected resources.
 *
 * Code structure:
 * - Opaque random string (not JWT)
 * - Cryptographically secure random generation
 * - Sufficient entropy (256 bits recommended)
 * - Short-lived (typically 10 minutes max)
 * - Single-use (MUST be consumed atomically)
 *
 * The code is stored in AuthorizationCodeStorage with associated metadata including:
 * - Client ID binding
 * - Redirect URI binding
 * - PKCE challenge (if present)
 * - DPoP JKT (if present)
 * - Scope
 * - Subject (user ID)
 *
 * Security considerations:
 * - Codes MUST be single-use (RFC 6749 Section 10.5)
 * - Codes MUST be short-lived (typically 10 minutes)
 * - Codes MUST be bound to client_id
 * - Codes MUST be bound to redirect_uri
 * - PKCE binding MUST be preserved
 * - DPoP binding MUST be preserved
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateAuthorizationCodeCommandImpl", exact = true)
class CreateAuthorizationCodeCommandImpl(
    execution: SessionExecution,
    private val authorizationCodeStorage: AuthorizationCodeStorage,
    private val configProvider: OAuth2ServersConfigProvider,
    private val secureRandom: SecureRandom,
    private val loginSessionIdProvider: OidcLoginSessionIdProvider,
    private val pushedAuthorizationRequestStorage: PushedAuthorizationRequestStorage,
    private val acrEnforcer: OAuth2AcrEnforcer,
    private val clientRegistry: ClientRegistry,
    private val requiredActionEvaluators: Set<RequiredActionEvaluator>,
) : TypedServiceCommandAdapter<CreateAuthorizationCodeArgs, StringResult, IdkError>(
        commandId = CreateAuthorizationCodeCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateAuthorizationCodeArgs>(),
        outputTypeToken = typeToken<StringResult>(),
    ),
    CreateAuthorizationCodeCommand {
    override val commandId: String get() = CreateAuthorizationCodeCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateAuthorizationCodeArgs

    override suspend fun doExecute(
        args: CreateAuthorizationCodeArgs,
        applyDuring: (CreateAuthorizationCodeArgs) -> CreateAuthorizationCodeArgs,
    ): IdkResult<StringResult, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.session, applied.userId, applied.consent, applied.userClaims, applied.acr, applied.amr).map { StringResult(it) }.mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        session: AuthorizationSession,
        userId: String,
        consent: ConsentDecision,
        userClaims: Map<String, Any> = emptyMap(),
        acr: String? = null,
        amr: List<String>? = null,
    ): IdkResult<String, AuthorizationServerError> {
        val now = Clock.System.now()
        val effectiveLifetime = configProvider.serverConfig.authorizationCodeLifetimeSeconds
        val expiresAt = now + effectiveLifetime.seconds

        // FAPI 2.0 SP §5.3.2.2 Note 3: PAR request_uri is single-use AT THE POINT OF
        // AUTHORIZATION, not at every `/authorize` visit. Consume here, atomically, so the
        // first concurrent code-issuance wins and any racing flow against the same
        // request_uri sees `null` and is rejected. Skip when the session wasn't PAR-backed.
        val parRequestUri = session.requestUri
        if (parRequestUri != null) {
            val consumeResult = pushedAuthorizationRequestStorage.consumeRequest(parRequestUri)
            if (consumeResult.isErr) {
                return Err(
                    AuthorizationServerError.ServerError(
                        details = "Failed to consume pushed authorization request: ${consumeResult.error.details}",
                        exception = consumeResult.error.exception,
                    ),
                )
            }
            if (consumeResult.value == null) {
                // Another concurrent flow against the same request_uri already minted a code,
                // or the PAR entry expired between /authorize and code issuance. Either way
                // the request_uri is gone and we cannot issue a second code under it.
                return Err(
                    AuthorizationServerError.InvalidRequest(
                        details = "Pushed authorization request_uri already consumed or expired: $parRequestUri",
                    ),
                )
            }
        }

        // RFC 9470 / OIDC Core §3.1.2.1 step-up enforcement. Compare the achieved
        // authentication state (acr / aal / amr / authTime) against the request's
        // `acr_values` and `max_age` parameters. The enforcer returns either Sufficient
        // (with the granted ACR string to put on the id_token) or Insufficient with the
        // challenge parameters the AS surfaces back to the caller.
        //
        // Achieved AAL is best-effort here: the IDK doesn't yet pipe an authenticated
        // AAL through to the code-issuance call site (the legacy [acr] arg is the only
        // signal). When acr_values is requested and the achieved acr maps to a known AAL
        // we use that level; otherwise default to AAL1 so a request for AAL2+ correctly
        // demands step-up. EDK deployments with a real authenticator can rebind
        // [OAuth2AcrEnforcer] to use [AuthenticatedUser.aal] directly.
        val achievedAal = inferAalFromAcr(acr) ?: AuthAssuranceLevel.AAL1
        val achievedAmr = amr?.toSet().orEmpty()
        val enforcement =
            acrEnforcer.enforce(
                requestedAcrValues = session.acrValues,
                currentAcr = acr,
                currentAal = achievedAal,
                currentAmr = achievedAmr,
                authTimeEpochSeconds = session.authTime,
                maxAge = session.maxAge,
                nowEpochSeconds = now.epochSeconds,
            )
        val effectiveAcr: String? =
            when (enforcement) {
                is OAuth2AcrEnforcementResult.Sufficient -> {
                    enforcement.grantedAcr
                }

                is OAuth2AcrEnforcementResult.Insufficient -> {
                    return Err(
                        AuthorizationServerError.UnmetAuthRequirements(
                            requiredAal = enforcement.requiredAal.name,
                            currentAal = enforcement.currentAal.name,
                            acrValues = enforcement.acrValues,
                            maxAge = enforcement.maxAge,
                            reason = enforcement.reason.name,
                        )
                    )
                }
            }

        // ── RequiredActions enforcement (P2-K1) ────────────────────────────
        // Run every RequiredActionEvaluator and refuse to mint if any returns
        // unmet obligations. The IDK ships zero evaluators (no-op gate by default);
        // EDK overlays add concrete evaluators (force-password-rotation,
        // accept-terms-version, mandatory-mfa-enrollment) via @ContributesIntoSet.
        // The orchestrator that ROUTES to the matching IDV graph based on the
        // returned actions is a follow-up — for now the gate refuses the mint and
        // surfaces the action ids on the error meta so the caller can react.
        if (requiredActionEvaluators.isNotEmpty()) {
            val clientLookup = clientRegistry.getClient(session.clientId)
            if (!clientLookup.isOk) {
                return Err(
                    AuthorizationServerError.ServerError(
                        details = "RequiredAction evaluation: client lookup failed: ${clientLookup.error}",
                        exception = null,
                    ),
                )
            }
            val client =
                clientLookup.value
                    ?: return Err(AuthorizationServerError.ClientNotFound(clientId = session.clientId))

            // Evaluators are SessionScope and resolve tenant from their own session-scoped
            // dependencies (TenantConfigService cascades naturally to AppConfigService;
            // SessionExecution exposes the tenant binding when needed). The gate just
            // hands them the client/user/session and unions the unmet actions.
            val unmet = mutableListOf<com.sphereon.oauth2.server.authorization.requiredaction.RequiredAction>()
            for (evaluator in requiredActionEvaluators) {
                unmet += evaluator.evaluate(client = client, userId = userId, session = session)
            }
            if (unmet.isNotEmpty()) {
                return Err(
                    AuthorizationServerError.RequiredActionsPending(
                        actionIds = unmet.map { it.actionId },
                        actionLabels = unmet.map { it.displayName },
                        actionMetadata = unmet.map { it.metadata },
                    ),
                )
            }
        }

        // Generate cryptographically secure random authorization code
        // 256 bits = 32 bytes, encoded as base64url
        val code = generateSecureCode()

        // Convert code challenge method from String to PkceMethod
        val pkceMethod =
            session.codeChallengeMethod?.let { method ->
                when (method.uppercase()) {
                    "PLAIN" -> PkceMethod.PLAIN
                    "S256" -> PkceMethod.S256
                    else -> null
                }
            }

        // Create authorization code data
        val codeData =
            AuthorizationCodeData(
                code = code,
                clientId = session.clientId,
                subject = userId,
                redirectUri = session.redirectUri,
                scope = consent.grantedScopes?.joinToString(" "),
                codeChallenge = session.codeChallenge,
                codeChallengeMethod = pkceMethod,
                dpopJkt = session.dpopJkt,
                nonce = session.nonce,
                authTime = session.authTime ?: now.epochSeconds,
                issuedAt = now,
                expiresAt = expiresAt,
                used = false,
                acr = effectiveAcr,
                amr = amr,
                userClaims = userClaims,
                additionalData = session.additionalData,
                // Prefer the cookie-keyed OIDC login session id over the pending-authorization
                // session id so the redeemed code propagates the same `sid` value the AS will
                // emit on the id_token (and use as the [OidcLoginSession] lookup key when the
                // OIDC RP-Initiated Logout end-session request later arrives carrying that
                // id_token as `id_token_hint`). When no cookie was on the request (federated
                // grants that don't write the AS-side cookie) we fall back to the pending id
                // so older flows keep functioning.
                sessionId = loginSessionIdProvider.currentLoginSessionId() ?: session.sessionId,
            )

        // Store authorization code
        return authorizationCodeStorage
            .storeAuthorizationCode(code, codeData)
            .mapError { error ->
                AuthorizationServerError.ServerError(
                    details = "Failed to store authorization code: $error",
                    exception = null,
                )
            }.map { code }
    }

    /**
     * Generate a cryptographically secure random authorization code.
     * 32 bytes (256 bits) of entropy, base64url encoded per RFC 6749 §10.10.
     */
    private suspend fun generateSecureCode(): String = secureRandom.newToken()

    /**
     * Best-effort AAL inference from a known SAML ACR string. Used by the step-up
     * enforcement path when the authenticator surfaces an ACR but no explicit AAL.
     * Returns null for unknown ACR vocabularies — the enforcer treats null as AAL1.
     */
    private fun inferAalFromAcr(acr: String?): AuthAssuranceLevel? =
        acr?.let { value ->
            AuthAssuranceLevel.entries.firstOrNull { it.acr == value }
                ?: when (value) {
                    "urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport" -> AuthAssuranceLevel.AAL1
                    "urn:oasis:names:tc:SAML:2.0:ac:classes:MobileTwoFactorContract" -> AuthAssuranceLevel.AAL2
                    "urn:oasis:names:tc:SAML:2.0:ac:classes:SmartcardPKI" -> AuthAssuranceLevel.AAL3
                    else -> null
                }
        }
}
