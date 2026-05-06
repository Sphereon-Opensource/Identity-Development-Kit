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

package com.sphereon.oauth2.client.impl.oidc

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.CompleteOidcLoginArgs
import com.sphereon.oauth2.client.command.CompleteOidcLoginCommand
import com.sphereon.oauth2.client.command.DiscoveryMode
import com.sphereon.oauth2.client.command.ExchangeTokenArgs
import com.sphereon.oauth2.client.command.ExchangeTokenCommand
import com.sphereon.oauth2.client.command.FetchAuthorizationServerMetadataCommand
import com.sphereon.oauth2.client.command.FetchServerMetadataArgs
import com.sphereon.oauth2.client.command.OidcLoginResult
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseArgs
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseCommand
import com.sphereon.oauth2.client.command.ParsedAuthorizationResponse
import com.sphereon.oauth2.client.metadata.IssuerJwksResolver
import com.sphereon.oauth2.client.transaction.OidcLoginTransaction
import com.sphereon.oauth2.client.transaction.OidcLoginTransactionStore
import com.sphereon.oauth2.common.command.ValidateIdTokenArgs
import com.sphereon.oauth2.common.command.ValidateIdTokenCommand
import com.sphereon.oauth2.common.error.Oauth2Error
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.IdTokenValidationOptions
import com.sphereon.oauth2.common.model.TokenRequest
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Top-level OIDC login-callback command.
 *
 * Composes the OIDC RP callback pipeline: parse response → consume state atomically →
 * fetch metadata → exchange code → resolve issuer JWKS → validate ID token → return typed result.
 * Every step that can fail maps into an [Oauth2Error] and stops the flow atomically.
 */
@Inject
@SingleIn(SessionScope::class)
class CompleteOidcLoginCommandImpl(
    execution: SessionExecution,
    private val parseAuthorizationResponseCommand: ParseAuthorizationResponseCommand,
    private val transactionStore: OidcLoginTransactionStore,
    private val fetchMetadataCommand: FetchAuthorizationServerMetadataCommand,
    private val exchangeTokenCommand: ExchangeTokenCommand,
    private val issuerJwksResolver: IssuerJwksResolver,
    private val validateIdTokenCommand: ValidateIdTokenCommand,
) : TypedServiceCommandAdapter<CompleteOidcLoginArgs, OidcLoginResult, IdkError>(
        commandId = CompleteOidcLoginCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CompleteOidcLoginArgs>(),
        outputTypeToken = typeToken<OidcLoginResult>(),
    ),
    CompleteOidcLoginCommand {
    override val commandId: String get() = CompleteOidcLoginCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CompleteOidcLoginArgs

    override suspend fun doExecute(
        args: CompleteOidcLoginArgs,
        applyDuring: (CompleteOidcLoginArgs) -> CompleteOidcLoginArgs,
    ): IdkResult<OidcLoginResult, IdkError> {
        val applied = applyDuring(args)

        val parsed =
            parseAuthorizationResponseCommand.execute(
                ParseAuthorizationResponseArgs(
                    redirectUrl = applied.callbackUrl,
                    source = applied.responseSource,
                    formBody = applied.callbackFormBody,
                ),
            )
        if (parsed.isErr) return Err(parsed.error)

        val successResponse =
            when (val response = parsed.value) {
                is ParsedAuthorizationResponse.Success -> {
                    response.response
                }

                is ParsedAuthorizationResponse.Error -> {
                    return Err(
                        IdkError.fromDTO(
                            Oauth2Error.ErrorResponse(
                                error = response.response.error,
                                errorDescription = response.response.errorDescription,
                                errorUri = response.response.errorUri,
                            ),
                        ),
                    )
                }
            }

        val state =
            successResponse.state
                ?: return Err(
                    IdkError.fromDTO(
                        Oauth2Error.InvalidGrant(reason = "Callback response missing 'state'"),
                    ),
                )

        // Atomic consume is the CSRF defence — the second arrival with the same state must fail.
        val transactionResult = transactionStore.consumeByState(state, applied.tenantId)
        if (transactionResult.isErr) return Err(IdkError.fromDTO(transactionResult.error))
        val transaction: OidcLoginTransaction = transactionResult.value

        // OIDC_FIRST so we prefer the richer openid-configuration document over RFC 8414.
        val metadataResult =
            fetchMetadataCommand.execute(
                FetchServerMetadataArgs(
                    issuer = transaction.issuer,
                    discoveryMode = DiscoveryMode.OIDC_FIRST,
                ),
            )
        if (metadataResult.isErr) return Err(metadataResult.error)
        val metadata = metadataResult.value
        val tokenEndpoint =
            metadata.tokenEndpoint
                ?: return Err(
                    IdkError.fromDTO(
                        Oauth2Error.InvalidGrant(reason = "Authorization server metadata missing token_endpoint"),
                    ),
                )

        val (clientId, clientSecret) = extractClientCredentials(applied.clientAuthentication)
        val tokenAuthMethod = resolveTokenAuthMethod(applied.clientAuthentication)
        val tokenResult =
            exchangeTokenCommand.execute(
                ExchangeTokenArgs(
                    tokenEndpoint = tokenEndpoint,
                    request =
                        TokenRequest(
                            grantType = GrantType.AUTHORIZATION_CODE.value,
                            code = successResponse.code,
                            redirectUri = transaction.redirectUri,
                            codeVerifier = transaction.pkceVerifier,
                            clientId = clientId,
                            clientSecret = clientSecret,
                            tokenEndpointAuthMethod = tokenAuthMethod,
                        ),
                ),
            )
        if (tokenResult.isErr) return Err(tokenResult.error)
        val tokenResponse = tokenResult.value

        val idToken =
            tokenResponse.idToken
                ?: return Err(
                    IdkError.fromDTO(
                        Oauth2Error.InvalidIdToken(reason = "Token response did not contain id_token"),
                    ),
                )

        // Resolve JWKS against the metadata we already have — the resolver short-circuits the
        // second discovery round-trip when called this way.
        val jwksResult = issuerJwksResolver.resolve(metadata)
        if (jwksResult.isErr) return Err(IdkError.fromDTO(jwksResult.error))
        val trustedJwks = jwksResult.value

        val validationResult =
            validateIdTokenCommand.execute(
                ValidateIdTokenArgs(
                    idToken = idToken,
                    options =
                        IdTokenValidationOptions(
                            expectedIssuer = transaction.issuer,
                            expectedAudience = applied.clientId,
                            expectedNonce = transaction.nonce,
                            accessToken = tokenResponse.accessToken,
                            trustedJwks = trustedJwks,
                        ),
                ),
            )
        if (validationResult.isErr) return Err(validationResult.error)
        val validated = validationResult.value

        return Ok(
            OidcLoginResult(
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                idToken = idToken,
                idTokenClaims = validated.payload,
                tokenResponse = tokenResponse,
            ),
        )
    }

    private fun extractClientCredentials(config: ClientAuthenticationConfig): Pair<String?, String?> =
        when (config) {
            is ClientAuthenticationConfig.Post -> config.credentials.clientId to config.credentials.clientSecret
            is ClientAuthenticationConfig.Basic -> config.credentials.clientId to config.credentials.clientSecret
            is ClientAuthenticationConfig.None -> config.clientId to null
            else -> null to null
        }

    /**
     * Maps the registered [ClientAuthenticationConfig] to the token endpoint authentication
     * method the exchange command should apply.
     *
     * The auth method comes from client config; we never infer it from which credential fields
     * the caller happened to populate. Configs that don't map to a credentials-based token
     * endpoint method (assertion-based, attestation-based, anonymous) fall back to legacy
     * client_secret_post: those flows already encode their authentication elsewhere.
     */
    private fun resolveTokenAuthMethod(config: ClientAuthenticationConfig): ClientAuthenticationMethod =
        when (config) {
            is ClientAuthenticationConfig.Basic -> ClientAuthenticationMethod.CLIENT_SECRET_BASIC
            is ClientAuthenticationConfig.Post -> ClientAuthenticationMethod.CLIENT_SECRET_POST
            is ClientAuthenticationConfig.None -> ClientAuthenticationMethod.NONE
            else -> ClientAuthenticationMethod.CLIENT_SECRET_POST
        }
}
