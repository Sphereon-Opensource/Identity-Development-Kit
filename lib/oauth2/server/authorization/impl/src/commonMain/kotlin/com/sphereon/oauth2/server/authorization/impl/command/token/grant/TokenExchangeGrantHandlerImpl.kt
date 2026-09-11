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

package com.sphereon.oauth2.server.authorization.impl.command.token.grant

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseCommand
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthorization
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantCommand
import com.sphereon.oauth2.server.authorization.command.token.GrantContext
import com.sphereon.oauth2.server.authorization.command.token.GrantHandler
import com.sphereon.oauth2.server.authorization.command.token.GrantHandlerKeys
import com.sphereon.oauth2.server.authorization.impl.command.token.executeWithTrustedClientAuthorization
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.binding

/**
 * RFC 8693 token-exchange grant handler.
 *
 * Verifies the subject token (and optional actor token), enforces RFC 9449 §10.1 proof-jkt
 * continuity against the subject token's `cnf.jkt`, and emits the exchanged access token with the
 * delegation `act` claim when present. No refresh token is minted (RFC 8693 §2.1).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<GrantHandler>())
@StringKey(GrantHandlerKeys.TOKEN_EXCHANGE)
class TokenExchangeGrantHandlerImpl(
    private val verifyTokenExchangeGrant: VerifyTokenExchangeGrantCommand,
    private val createAccessToken: CreateAccessTokenCommand,
    private val createTokenResponse: CreateTokenResponseCommand,
) : GrantHandler {
    override val grantType: String = GrantHandlerKeys.TOKEN_EXCHANGE

    override fun supports(params: GrantParameters): Boolean = params is GrantParameters.TokenExchange

    override suspend fun handle(
        params: GrantParameters,
        context: GrantContext,
    ): IdkResult<TokenResponse, IdkError> = handleInternal(params, context, null)

    internal suspend fun handleTrusted(
        params: GrantParameters,
        context: GrantContext,
        clientAuthorization: VerifiedClientAuthorization?,
    ): IdkResult<TokenResponse, IdkError> = handleInternal(params, context, clientAuthorization)

    private suspend fun handleInternal(
        params: GrantParameters,
        context: GrantContext,
        clientAuthorization: VerifiedClientAuthorization?,
    ): IdkResult<TokenResponse, IdkError> {
        val txParams = params as GrantParameters.TokenExchange
        val tokenRequest = context.tokenRequest
        val applied = context.applied
        val proofJkt = context.proofJkt
        val certThumbprint = context.certThumbprintS256

        val verified =
            verifyTokenExchangeGrant
                .executeWithTrustedClientAuthorization(
                    VerifyTokenExchangeGrantArgs(
                        subjectToken = txParams.subjectToken,
                        subjectTokenType = txParams.subjectTokenType,
                        actorToken = txParams.actorToken,
                        actorTokenType = txParams.actorTokenType,
                        resources = txParams.resources,
                        audiences = txParams.audiences,
                        scope = txParams.scope,
                        requestedTokenType = txParams.requestedTokenType,
                        clientId = tokenRequest.clientId,
                    ),
                    clientAuthorization,
                ).getOrElse { error -> return Err(error) }

        // RFC 9449 §10.1: when the subject token carries `cnf.jkt`, the DPoP proof
        // on this exchange MUST be from the same key. A bound subject token with no
        // proof, or a proof from a different key, fails as `invalid_dpop_proof`.
        val subjectJkt = verified.subjectCnfJkt
        if (subjectJkt != null && proofJkt == null) {
            return invalidDpopProof("Subject token is DPoP-bound but request did not present a DPoP proof")
        }
        if (subjectJkt != null && proofJkt != null && subjectJkt != proofJkt) {
            return invalidDpopProof("DPoP proof thumbprint does not match subject token cnf.jkt")
        }
        // The exchanged token is bound to the proof's thumbprint. When the subject
        // token was bound, this is enforced equal to subjectJkt by the check above.
        val exchangeBoundJkt = subjectJkt ?: proofJkt

        // Build additional claims (include act for delegation)
        val additionalClaims =
            buildMap<String, Any> {
                putAll(verified.additionalClaims)
                verified.actorClaim?.let { put("act", it) }
            }

        // Create access token
        val accessToken =
            createAccessToken
                .execute(
                    CreateAccessTokenArgs(
                        subject = verified.subject,
                        clientId = verified.clientId,
                        scope = verified.scope,
                        audience = verified.audience,
                        dpopJkt = exchangeBoundJkt,
                        certificateThumbprintS256 = certThumbprint,
                        authTime = verified.authTime,
                        acr = verified.acr,
                        amr = verified.amr,
                        additionalClaims = additionalClaims,
                        baseUrlOverride = applied.baseUrlOverride,
                    ),
                ).getOrElse { error -> return Err(error) }

        // No refresh token for token exchange (RFC 8693 Section 2.1)
        return createTokenResponse.execute(
            CreateTokenResponseArgs(
                accessToken = accessToken.value,
                tokenType = tokenTypeFor(exchangeBoundJkt),
                scope = verified.scope,
                issuedTokenType = verified.issuedTokenType,
            ),
        )
    }
}
