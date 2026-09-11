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
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.client.OAuth2Client
import com.sphereon.oauth2.client.command.FetchUserInfoResult
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientCredentials
import com.sphereon.oauth2.common.token.OidcTokenClaimExtractor
import com.sphereon.oauth2.common.model.IdTokenValidationOptions
import com.sphereon.oauth2.client.metadata.IssuerJwksResolver
import com.sphereon.oauth2.server.authorization.command.federation.ExchangeCodeAndExtractClaimsArgs
import com.sphereon.oauth2.server.authorization.command.federation.ExchangeCodeAndExtractClaimsCommand
import com.sphereon.oauth2.server.authorization.command.federation.FederatedExchangeResult
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.FederationProviderRuntimeResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

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
    private val issuerJwksResolver: IssuerJwksResolver,
    private val providerResolver: FederationProviderRuntimeResolver,
    private val clock: kotlin.time.Clock,
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
        val upstreamPkce = pending.pkceData
        if (upstreamPkce.codeVerifier.isBlank() || upstreamPkce.codeChallenge.isBlank()) {
            return Err(AuthenticationError.Generic(description = "Pinned upstream PKCE transaction is invalid"))
        }

        val clientAuth = providerResolver.clientAuthentication(providerConfig.id, pending.upstreamIssuer)
            .getOrElse { return Err(it) }

        val tokenResponse =
            oauth2Client
                .exchangeAuthorizationCode(
                    authorizationServerMetadata = pending.metadata,
                    clientAuthentication = clientAuth,
                    authorizationCode = applied.code,
                    redirectUri = pending.callbackRedirectUri,
                    pkceData = upstreamPkce,
                ).getOrElse {
                    return Err(
                        AuthenticationError.Generic(
                            description = "Token exchange failed for ${providerConfig.id}: ${it.message.defaultMessage}",
                        ),
                    )
                }

        val idToken = tokenResponse.idToken
        val accessToken = tokenResponse.accessToken
        if (idToken == null) {
            return Err(AuthenticationError.Generic(description = "Upstream token response did not contain an ID token"))
        }
        val jwks = issuerJwksResolver.resolve(pending.metadata).getOrElse {
            return Err(AuthenticationError.Generic(description = "Failed to resolve pinned upstream signing keys: ${it.message.defaultMessage}"))
        }
        val validatedIdToken = oauth2Client.validateIdToken(
            idToken,
            IdTokenValidationOptions(
                expectedIssuer = pending.upstreamIssuer,
                expectedAudience = providerConfig.clientId,
                expectedNonce = pending.nonce,
                accessToken = accessToken,
                authorizationCode = applied.code,
                allowedAlgorithms = pending.metadata.idTokenSigningAlgValuesSupported.orEmpty().ifEmpty { IdTokenValidationOptions.DEFAULT_ID_TOKEN_ALG_ALLOWLIST },
                trustedJwks = jwks,
            ),
        ).getOrElse {
            return Err(AuthenticationError.Generic(description = "Upstream ID token validation failed: ${it.message.defaultMessage}"))
        }

        var upstreamAcr: String? = null
        var upstreamAmr: List<String>? = null
        var upstreamSid: String? = null
        var upstreamAuthTime: kotlin.time.Instant? = null
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
                upstreamAuthTime = (rawIdTokenClaims["auth_time"] as? JsonPrimitive)?.longOrNull?.let(kotlin.time.Instant::fromEpochSeconds)
                rawIdTokenClaims.mapValues { (_, value) -> value.toFederatedClaimValue() }
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

        val validatedSubject = validatedIdToken.payload.sub
        val userInfoSubject = userinfoResult?.claims?.get("sub")?.let {
            when (it) {
                is JsonPrimitive -> it.contentOrNull
                else -> it.toString().trim('"')
            }
        }
        if (userInfoSubject != null && userInfoSubject != validatedSubject) {
            return Err(AuthenticationError.Generic(description = "Upstream UserInfo subject does not match the validated ID token"))
        }

        val mergedClaims =
            buildMap<String, Any> {
                putAll(userClaims)
                userinfoResult?.claims?.forEach { (key, value) ->
                    put(key, value.toFederatedClaimValue())
                }
            }

        log.debug("ID token claims: ${userClaims.keys}")
        log.debug("Merged claims (${mergedClaims.size} total, keys=${mergedClaims.keys})")
        // Claim values are authentication evidence and may contain personal data.  Only
        // structural diagnostics are safe to emit; values belong in the normalized,
        // access-controlled evidence record produced by the outcome handler.
        log.debug("Upstream authentication evidence fields: acr=${upstreamAcr != null}, amrCount=${upstreamAmr?.size ?: 0}")

        return Ok(
            FederatedExchangeResult(
                claims = mergedClaims,
                upstreamAcr = upstreamAcr,
                upstreamAmr = upstreamAmr,
                upstreamSid = upstreamSid,
                upstreamSubject = validatedIdToken.payload.sub,
                upstreamAuthTime = upstreamAuthTime,
                validatedAt = clock.now(),
            ),
        )
    }
}

internal fun JsonElement.toFederatedClaimValue(): Any =
    when (this) {
        JsonNull -> JsonNull
        is JsonPrimitive ->
            when {
                isString -> content
                booleanOrNull != null -> booleanOrNull!!
                longOrNull != null -> longOrNull!!
                doubleOrNull != null -> doubleOrNull!!
                else -> content
            }
        is JsonArray -> map { it.toFederatedClaimValue() }
        is JsonObject -> mapValues { (_, value) -> value.toFederatedClaimValue() }
    }
