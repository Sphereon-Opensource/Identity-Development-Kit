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
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantArgs
import com.sphereon.oauth2.server.authorization.command.token.GrantContext
import com.sphereon.oauth2.server.authorization.command.token.GrantHandler
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * RFC 6749 §4.4 client-credentials grant handler.
 *
 * The DPoP proof presented at this first issuance establishes the binding for the access token
 * (RFC 9449 §10.1). No refresh token is minted; subsequent calls go through this same grant.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<GrantHandler>())
class ClientCredentialsGrantHandlerImpl : GrantHandler {
    override val grantType: String = GrantType.CLIENT_CREDENTIALS.value

    override fun supports(params: GrantParameters): Boolean = params is GrantParameters.ClientCredentials

    override suspend fun handle(
        params: GrantParameters,
        context: GrantContext,
    ): IdkResult<TokenResponse, IdkError> {
        val ccParams = params as GrantParameters.ClientCredentials
        val tokenRequest = context.tokenRequest
        val applied = context.applied
        val commands = context.commands
        val proofJkt = context.proofJkt
        val certThumbprint = context.certThumbprintS256

        val verified =
            commands.verifyClientCredentialsGrant
                .execute(
                    VerifyClientCredentialsGrantArgs(
                        clientId = tokenRequest.clientId,
                        requestedScope = ccParams.scope,
                    ),
                ).getOrElse { error -> return Err(error) }

        // RFC 9449 §10.1: client_credentials carries no prior commitment, so the
        // proof presented at this first issuance establishes the binding. The
        // proof's thumbprint is pinned as `cnf.jkt` on the access token; subsequent
        // resource-server requests must present a proof from the same key.
        val accessToken =
            commands.createAccessToken
                .execute(
                    CreateAccessTokenArgs(
                        subject = verified.subject,
                        clientId = tokenRequest.clientId,
                        scope = verified.scope,
                        dpopJkt = proofJkt,
                        certificateThumbprintS256 = certThumbprint,
                        baseUrlOverride = applied.baseUrlOverride,
                    ),
                ).getOrElse { error -> return Err(error) }

        return commands.createTokenResponse.execute(
            CreateTokenResponseArgs(
                accessToken = accessToken.value,
                tokenType = tokenTypeFor(proofJkt),
                scope = verified.scope,
            ),
        )
    }
}
