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

package com.sphereon.oauth2.server.authorization.impl.command.federation

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.LogLevel
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.client.OAuth2Client
import com.sphereon.oauth2.client.command.FetchUserInfoResult
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientCredentials
import com.sphereon.oauth2.common.token.OidcTokenClaimExtractor
import com.sphereon.oauth2.server.authorization.command.federation.ExchangeCodeAndExtractClaimsArgs
import com.sphereon.oauth2.server.authorization.command.federation.ExchangeCodeAndExtractClaimsCommand
import com.sphereon.oauth2.server.authorization.command.federation.FederatedExchangeResult
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.contentOrNull

/**
 * Exchange the upstream authorization code for tokens, then merge claims from the id_token
 * with any userinfo endpoint response (userinfo wins per OIDC Core §5.3). Preserves structural
 * `acr`/`amr`/`sid` claims so downstream [com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser]
 * surfaces ground-truth upstream authentication metadata and inbound Back-Channel Logout can
 * terminate the precise local session.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ExchangeCodeAndExtractClaimsCommand>())
class ExchangeCodeAndExtractClaimsCommandImpl(
    execution: SessionExecution,
    private val oauth2Client: OAuth2Client,
    private val tokenClaimExtractor: OidcTokenClaimExtractor,
) : TypedServiceCommandAdapter<ExchangeCodeAndExtractClaimsArgs, FederatedExchangeResult, AuthenticationError>(
        commandId = ExchangeCodeAndExtractClaimsCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ExchangeCodeAndExtractClaimsArgs>(),
        outputTypeToken = typeToken<FederatedExchangeResult>(),
    ),
    ExchangeCodeAndExtractClaimsCommand {
    override val commandId: String get() = ExchangeCodeAndExtractClaimsCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ExchangeCodeAndExtractClaimsArgs

    override suspend fun doExecute(
        args: ExchangeCodeAndExtractClaimsArgs,
        applyDuring: (ExchangeCodeAndExtractClaimsArgs) -> ExchangeCodeAndExtractClaimsArgs,
    ): IdkResult<FederatedExchangeResult, AuthenticationError> {
        val applied = applyDuring(args)
        val providerConfig = applied.providerConfig
        val pending = applied.pending
        val log = execution.log

        val clientAuth =
            providerConfig.clientSecret?.let { secret ->
                ClientAuthenticationConfig.Post(
                    credentials =
                        ClientCredentials(
                            clientId = providerConfig.clientId,
                            clientSecret = secret,
                        ),
                )
            } ?: ClientAuthenticationConfig.None(clientId = providerConfig.clientId)

        val tokenResponse =
            oauth2Client
                .exchangeAuthorizationCode(
                    authorizationServerMetadata = pending.metadata,
                    clientAuthentication = clientAuth,
                    authorizationCode = applied.code,
                    redirectUri = pending.callbackRedirectUri,
                    pkceData = pending.pkceData,
                ).getOrElse {
                    return Err(
                        AuthenticationError.Generic(
                            description = "Token exchange failed for ${providerConfig.id}: ${it.message.defaultMessage}",
                        ),
                    )
                }

        val idToken = tokenResponse.idToken
        val accessToken = tokenResponse.accessToken

        var upstreamAcr: String? = null
        var upstreamAmr: List<String>? = null
        var upstreamSid: String? = null
        val userClaims: Map<String, Any> =
            if (idToken != null) {
                val rawIdTokenClaims =
                    tokenClaimExtractor
                        .extractAllClaims(idToken)
                        .getOrElse {
                            return Err(
                                AuthenticationError.Generic(
                                    description = "Failed to decode upstream ID token: ${it.message.defaultMessage}",
                                ),
                            )
                        }
                upstreamAcr =
                    (rawIdTokenClaims["acr"] as? kotlinx.serialization.json.JsonPrimitive)
                        ?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                upstreamAmr =
                    (rawIdTokenClaims["amr"] as? kotlinx.serialization.json.JsonArray)
                        ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
                        ?.takeIf { it.isNotEmpty() }
                upstreamSid =
                    (rawIdTokenClaims["sid"] as? kotlinx.serialization.json.JsonPrimitive)
                        ?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                rawIdTokenClaims.mapValues { (_, v) -> v.toString().removeSurrounding("\"") }
            } else {
                emptyMap()
            }

        val userinfoResult: FetchUserInfoResult? =
            if (accessToken != null && pending.metadata.userinfoEndpoint != null) {
                val result = oauth2Client.fetchUserInfo(accessToken, pending.metadata)
                if (result.isOk) {
                    log.debug("UserInfo fetched successfully from ${pending.metadata.userinfoEndpoint}: ${result.value.claims.keys}")
                    result.value
                } else {
                    log.debug("UserInfo fetch failed from ${pending.metadata.userinfoEndpoint}: ${result.error.message}")
                    null
                }
            } else {
                log.debug("Skipping UserInfo: accessToken=${accessToken != null}, userinfoEndpoint=${pending.metadata.userinfoEndpoint}")
                null
            }

        val mergedClaims =
            buildMap<String, Any> {
                putAll(userClaims)
                userinfoResult?.claims?.forEach { (key, value) ->
                    put(key, value.toString().removeSurrounding("\""))
                }
            }

        log.debug("ID token claims: ${userClaims.keys}")
        log.debug("Merged claims (${mergedClaims.size} total, keys=${mergedClaims.keys})")
        if (log.isEnabled(level = LogLevel.TRACE)) {
            mergedClaims.forEach { (k, v) -> log.trace("  $k = $v") }
        }
        log.debug("Upstream acr=$upstreamAcr, amr=$upstreamAmr")

        return Ok(
            FederatedExchangeResult(
                claims = mergedClaims,
                upstreamAcr = upstreamAcr,
                upstreamAmr = upstreamAmr,
                upstreamSid = upstreamSid,
            ),
        )
    }
}
