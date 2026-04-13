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

package com.sphereon.oauth2.server.authorization.impl.command.iae

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.server.authorization.command.HandleIaeFollowUpArgs
import com.sphereon.oauth2.server.authorization.command.HandleIaeFollowUpCommand
import com.sphereon.oauth2.server.authorization.command.IaeResult
import com.sphereon.oauth2.server.authorization.model.IaeAuthorizationCodeResponse
import com.sphereon.oauth2.server.authorization.model.IaeErrorResponse
import com.sphereon.oauth2.server.authorization.model.IaeErrors
import com.sphereon.oauth2.server.authorization.model.IaeInteractionTypes
import com.sphereon.oauth2.server.authorization.model.IaeSession
import com.sphereon.oauth2.server.authorization.model.IaeSessionStatus
import com.sphereon.oauth2.server.authorization.storage.IaeSessionStore
import com.sphereon.openid.oid4vp.verifier.Oid4vpVerifierService
import com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

private const val RANDOM_TOKEN_BYTES = 32
private const val MIN_PKCE_LENGTH = 43
private const val MAX_PKCE_LENGTH = 128

/**
 * Implementation of [HandleIaeFollowUpCommand].
 *
 * Processes IAE follow-up requests (OID4VCI 1.1 Section 6.3):
 * 1. Looks up [IaeSession] by authSession token
 * 2. Validates session exists, is not expired, and is in INTERACTION_REQUIRED state
 * 3. Branches on currentInteractionType:
 *    - OPENID4VP_PRESENTATION: validates VP response, issues authorization code
 *    - REDIRECT_TO_WEB: validates optional PKCE, issues authorization code
 * 4. Returns [IaeResult.AuthorizationCode] on success
 *
 * Registered via [com.sphereon.oauth2.server.authorization.impl.command.OAuth2AuthServerCommandDescriptors].
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("HandleIaeFollowUpCommandImpl", exact = true)
class HandleIaeFollowUpCommandImpl(
    execution: SessionExecution,
    private val iaeSessionStore: IaeSessionStore,
    private val verifierService: Oid4vpVerifierService? = null,
) : TypedServiceCommandAdapter<HandleIaeFollowUpArgs, IaeResult>(
        commandId = HandleIaeFollowUpCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleIaeFollowUpArgs>(),
        outputTypeToken = typeToken<IaeResult>(),
    ),
    HandleIaeFollowUpCommand {
    override val commandId: String get() = HandleIaeFollowUpCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is HandleIaeFollowUpArgs

    override suspend fun doExecute(
        args: HandleIaeFollowUpArgs,
        applyDuring: (HandleIaeFollowUpArgs) -> HandleIaeFollowUpArgs,
    ): IdkResult<IaeResult, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied)
    }

    private suspend fun executeInternal(args: HandleIaeFollowUpArgs): IdkResult<IaeResult, IdkError> {
        // Step 1: Look up session by authSession token
        val lookupResult = iaeSessionStore.getByAuthSession(args.authSession)
        if (lookupResult.isErr) {
            return Err(lookupResult.error)
        }

        val session =
            lookupResult.value
                ?: return Ok(
                    IaeResult.Error(
                        IaeErrorResponse(
                            error = IaeErrors.INVALID_REQUEST,
                            errorDescription = "Unknown or expired auth_session",
                        ),
                    ),
                )

        // Step 2: Validate session is not expired
        val now = Clock.System.now().epochSeconds
        if (now > session.expiresAt) {
            return Ok(
                IaeResult.Error(
                    IaeErrorResponse(
                        error = IaeErrors.ACCESS_DENIED,
                        errorDescription = "IAE session has expired",
                    ),
                ),
            )
        }

        // Step 2 (cont): Validate session status is INTERACTION_REQUIRED
        if (session.status != IaeSessionStatus.INTERACTION_REQUIRED) {
            return Ok(
                IaeResult.Error(
                    IaeErrorResponse(
                        error = IaeErrors.INVALID_REQUEST,
                        errorDescription = "Session is not awaiting interaction; current status: ${session.status}",
                    ),
                ),
            )
        }

        // Step 3: Branch on currentInteractionType
        return when (session.currentInteractionType) {
            IaeInteractionTypes.OPENID4VP_PRESENTATION -> {
                handleVpPresentation(args, session)
            }

            IaeInteractionTypes.REDIRECT_TO_WEB -> {
                handleRedirectToWeb(args, session)
            }

            else -> {
                Ok(
                    IaeResult.Error(
                        IaeErrorResponse(
                            error = IaeErrors.INVALID_REQUEST,
                            errorDescription = "Unsupported interaction type: ${session.currentInteractionType}",
                        ),
                    ),
                )
            }
        }
    }

    private suspend fun handleVpPresentation(
        args: HandleIaeFollowUpArgs,
        session: IaeSession,
    ): IdkResult<IaeResult, IdkError> {
        // VP response must be present
        val vpResponse =
            args.openid4vpResponse
                ?: return Ok(
                    IaeResult.Error(
                        IaeErrorResponse(
                            error = IaeErrors.INVALID_REQUEST,
                            errorDescription = "openid4vp_response is required for VP presentation interaction",
                        ),
                    ),
                )

        // Check for VP error
        if (vpResponse.containsKey("error")) {
            val vpError = vpResponse["error"]?.jsonPrimitive?.contentOrNull ?: "unknown_error"
            val updatedSession = session.copy(status = IaeSessionStatus.FAILED)
            iaeSessionStore.update(updatedSession)
            return Ok(
                IaeResult.Error(
                    IaeErrorResponse(
                        error = IaeErrors.ACCESS_DENIED,
                        errorDescription = "VP presentation failed: $vpError",
                    ),
                ),
            )
        }

        // VP verification is mandatory — the verifier service must be present.
        if (verifierService == null) {
            return Ok(
                IaeResult.Error(
                    IaeErrorResponse(
                        error = IaeErrors.ACCESS_DENIED,
                        errorDescription = "VP verification service not available — cannot process VP presentation",
                    ),
                ),
            )
        }

        // The stored verifier session must exist to validate the DCQL query and nonce.
        val vpSessionId = session.vpSessionId
        val verifierSession =
            vpSessionId?.let {
                verifierService.authorizationSessionStore
                    .getByCorrelationId(it)
                    .let { result ->
                        if (result.isOk) {
                            result.value
                        } else {
                            null
                        }
                    }
            }
        if (vpSessionId == null || verifierSession == null) {
            return Ok(
                IaeResult.Error(
                    IaeErrorResponse(
                        error = IaeErrors.ACCESS_DENIED,
                        errorDescription = "VP verification session not found — cannot verify presentation",
                    ),
                ),
            )
        }

        // Verify the VP response.
        val vpVerificationResult: JsonElement =
            verifyVpResponse(session, vpResponse, verifierSession)
                ?: return Ok(
                    IaeResult.Error(
                        IaeErrorResponse(
                            error = IaeErrors.ACCESS_DENIED,
                            errorDescription = "VP verification failed",
                        ),
                    ),
                )

        // VP verified — generate authorization code.
        val authCode = generateAuthCode()
        val updatedSession =
            session.copy(
                status = IaeSessionStatus.AUTHORIZED,
                authorizationCode = authCode,
                vpVerificationResult = vpVerificationResult,
            )

        val updateResult = iaeSessionStore.update(updatedSession)
        if (updateResult.isErr) {
            return Err(updateResult.error)
        }

        return Ok(IaeResult.AuthorizationCode(IaeAuthorizationCodeResponse(code = authCode)))
    }

    /**
     * Verifies the VP authorization response received from the wallet.
     *
     * Callers MUST pre-validate that [verifierService] and [verifierSession] are present before invoking
     * this method. The response is parsed and validated against the stored DCQL query and
     * authorization request from [verifierSession]. The nonce in the VP response MUST match
     * [IaeSession.vpNonce]; this is enforced by [ValidateAuthorizationResponseArgs.expectedNonce].
     *
     * @param session The current [IaeSession] (INTERACTION_REQUIRED status).
     * @param vpResponse The VP response object from the wallet (from [HandleIaeFollowUpArgs.openid4vpResponse]).
     * @param verifierSession The stored OID4VP verifier session (non-null; caller has verified).
     * @return A [JsonElement] summarizing the verification result to store on the session, or
     *   `null` if cryptographic verification failed and the request should be rejected.
     */
    private suspend fun verifyVpResponse(
        session: IaeSession,
        vpResponse: JsonObject,
        verifierSession: AuthorizationSession,
    ): JsonElement? {
        // vpNonce is required for VP flows — a missing nonce means the session was not set up
        // correctly. Reject rather than skip the nonce check.
        val vpNonce = session.vpNonce ?: return null

        // Convert the JsonObject response params to Map<String, String> for the verifier.
        val responseParams =
            vpResponse.entries
                .mapNotNull { (key, value) ->
                    val str =
                        when (value) {
                            is JsonPrimitive -> value.contentOrNull ?: return@mapNotNull null
                            else -> value.toString()
                        }
                    key to str
                }.toMap()

        // Step 1: Parse the authorization response.
        // verifierService is non-null here — the caller (handleVpPresentation) guards against null.
        val parseResult =
            verifierService!!.parseAuthorizationResponse(
                ParseAuthorizationResponseArgs(responseParams = responseParams),
            )
        if (parseResult.isErr) {
            return null
        }
        val parsedResponse = parseResult.value

        // Step 2: Validate the parsed response against the original request and DCQL query.
        val validateResult =
            verifierService.validateAuthorizationResponse(
                ValidateAuthorizationResponseArgs(
                    parsedResponse = parsedResponse,
                    originalRequest = verifierSession.authorizationRequest,
                    dcqlQuery = verifierSession.dcqlQuery,
                    expectedNonce = vpNonce,
                ),
            )
        if (validateResult.isErr) {
            return null
        }

        val validationResult = validateResult.value
        if (!validationResult.valid) {
            return null
        }

        // Return a summary of matched credentials as the verification result.
        return JsonPrimitive(
            "ok:${validationResult.matchedCredentials.size}_credentials_matched",
        )
    }

    private suspend fun handleRedirectToWeb(
        args: HandleIaeFollowUpArgs,
        session: IaeSession,
    ): IdkResult<IaeResult, IdkError> {
        // Perform PKCE S256 validation when a code_challenge was recorded in the session (RFC 7636).
        if (session.codeChallenge != null) {
            val codeVerifier =
                args.codeVerifier
                    ?: return Ok(
                        IaeResult.Error(
                            IaeErrorResponse(
                                error = IaeErrors.INVALID_REQUEST,
                                errorDescription = "code_verifier is required (PKCE)",
                            ),
                        ),
                    )

            // Validate verifier length per RFC 7636 Section 4.1 (43–128 characters).
            if (codeVerifier.length !in MIN_PKCE_LENGTH..MAX_PKCE_LENGTH) {
                return Ok(
                    IaeResult.Error(
                        IaeErrorResponse(
                            error = IaeErrors.INVALID_REQUEST,
                            errorDescription = "code_verifier must be 43–128 characters",
                        ),
                    ),
                )
            }

            val method =
                session.codeChallengeMethod?.let { raw ->
                    PkceMethod.entries.firstOrNull { it.value == raw }
                } ?: PkceMethod.S256

            val computedChallenge =
                when (method) {
                    PkceMethod.S256 -> hash(codeVerifier.encodeToByteArray(), DigestAlg.SHA256).encodeToBase64Url()
                    PkceMethod.PLAIN -> codeVerifier
                }

            if (computedChallenge != session.codeChallenge) {
                return Ok(
                    IaeResult.Error(
                        IaeErrorResponse(
                            error = "invalid_grant",
                            errorDescription = "PKCE verification failed",
                        ),
                    ),
                )
            }
        }

        val authCode = generateAuthCode()
        val updatedSession =
            session.copy(
                status = IaeSessionStatus.AUTHORIZED,
                authorizationCode = authCode,
            )

        val updateResult = iaeSessionStore.update(updatedSession)
        if (updateResult.isErr) {
            return Err(updateResult.error)
        }

        return Ok(IaeResult.AuthorizationCode(IaeAuthorizationCodeResponse(code = authCode)))
    }

    /**
     * Generates a cryptographically secure random authorization code (32 bytes, base64url-encoded).
     */
    private fun generateAuthCode(): String {
        val randomBytes = CryptographyRandom.nextBytes(RANDOM_TOKEN_BYTES)
        return randomBytes.encodeToBase64Url()
    }
}
