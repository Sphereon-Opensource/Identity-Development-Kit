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

package com.sphereon.identity.reconciliation.oidc

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.reconciliation.api.ReconciliationOidcClient
import com.sphereon.identity.reconciliation.api.ReconciliationOidcEndpoints
import com.sphereon.identity.reconciliation.api.ReconciliationPkceMaterial
import com.sphereon.identity.reconciliation.api.ReconciliationTokenExchangeRequest
import com.sphereon.identity.reconciliation.api.ReconciliationTokenExchangeResult
import com.sphereon.identity.reconciliation.api.ReconciliationUserInfoResult
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.CreatePkceCommand
import com.sphereon.oauth2.client.command.ExchangeTokenArgs
import com.sphereon.oauth2.client.command.ExchangeTokenCommand
import com.sphereon.oauth2.client.command.FetchUserInfoArgs
import com.sphereon.oauth2.client.command.FetchUserInfoCommand
import com.sphereon.oauth2.common.model.TokenRequest
import com.sphereon.oauth2.common.token.OidcTokenClaimExtractor
import com.sphereon.oauth2.jwt.validation.OidcDiscoveryService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonElement

/**
 * Protocols-pack adapter that backs [ReconciliationOidcClient] with OAuth2 client commands.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ReconciliationOidcClient>())
class OAuth2ReconciliationOidcClient(
    private val createPkceCommand: CreatePkceCommand,
    private val oidcDiscoveryService: OidcDiscoveryService,
    private val exchangeTokenCommand: ExchangeTokenCommand,
    private val fetchUserInfoCommand: FetchUserInfoCommand,
    private val tokenClaimExtractor: OidcTokenClaimExtractor,
) : ReconciliationOidcClient {
    override suspend fun createPkce(): IdkResult<ReconciliationPkceMaterial, IdkError> {
        val pkce =
            createPkceCommand.execute(CreatePkceArgs()).getOrElse { error ->
                return Err(error)
            }
        return Ok(
            ReconciliationPkceMaterial(
                codeVerifier = pkce.codeVerifier,
                codeChallenge = pkce.codeChallenge,
                codeChallengeMethod = pkce.codeChallengeMethod.name,
            ),
        )
    }

    override suspend fun discoverEndpoints(issuer: String): ReconciliationOidcEndpoints? {
        val metadata = oidcDiscoveryService.getMetadata(issuer).getOrNull() ?: return null
        return ReconciliationOidcEndpoints(
            authorizationEndpoint = metadata.authorizationEndpoint,
            tokenEndpoint = metadata.tokenEndpoint,
            userinfoEndpoint = metadata.userinfoEndpoint,
        )
    }

    override suspend fun exchangeAuthorizationCode(
        request: ReconciliationTokenExchangeRequest,
    ): IdkResult<ReconciliationTokenExchangeResult, IdkError> {
        val tokenRequest =
            TokenRequest(
                grantType = "authorization_code",
                code = request.authorizationCode,
                redirectUri = request.redirectUri,
                codeVerifier = request.codeVerifier,
                clientId = request.clientId,
                clientSecret = request.clientSecret,
            )
        val response =
            exchangeTokenCommand
                .execute(
                    ExchangeTokenArgs(tokenEndpoint = request.tokenEndpoint, request = tokenRequest),
                ).getOrElse { error ->
                    return Err(error)
                }
        return Ok(
            ReconciliationTokenExchangeResult(
                accessToken = response.accessToken,
                idToken = response.idToken,
            ),
        )
    }

    override suspend fun fetchUserInfo(
        accessToken: String,
        userinfoEndpoint: String,
    ): IdkResult<ReconciliationUserInfoResult, IdkError> {
        val result =
            fetchUserInfoCommand
                .execute(
                    FetchUserInfoArgs(
                        accessToken = accessToken,
                        userinfoEndpoint = userinfoEndpoint,
                    ),
                ).getOrElse { error ->
                    return Err(error)
                }
        return Ok(
            ReconciliationUserInfoResult(
                sub = result.sub,
                claims = result.claims,
            ),
        )
    }

    override fun extractIdTokenClaims(idToken: String): Map<String, JsonElement> =
        tokenClaimExtractor.extractAllClaims(idToken).getOrNull() ?: emptyMap()
}
