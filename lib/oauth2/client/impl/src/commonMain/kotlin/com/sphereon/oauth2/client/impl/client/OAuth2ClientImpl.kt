/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.client.impl.client

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.client.AuthorizationResult
import com.sphereon.oauth2.client.client.DpopContext
import com.sphereon.oauth2.client.client.OAuth2Client
import com.sphereon.oauth2.client.command.CreateAuthorizationRequestUrlCommand
import com.sphereon.oauth2.client.command.CreateAuthorizationRequestUrlOptions
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.CreatePkceCommand
import com.sphereon.oauth2.client.command.ExchangeTokenArgs
import com.sphereon.oauth2.client.command.ExchangeTokenCommand
import com.sphereon.oauth2.client.command.FetchAuthorizationServerMetadataCommand
import com.sphereon.oauth2.client.command.FetchServerMetadataArgs
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseArgs
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseCommand
import com.sphereon.oauth2.client.command.ParsedAuthorizationResponse
import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.client.service.DpopService
import com.sphereon.oauth2.common.command.IntrospectTokenArgs
import com.sphereon.oauth2.common.command.IntrospectTokenCommand
import com.sphereon.oauth2.common.command.ValidateIdTokenArgs
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.CreateDpopProofOptions
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.HttpMethod
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.common.model.TokenRequest
import com.sphereon.oauth2.common.model.TokenResponse
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of OAuth2Client facade
 *
 * Integrates all OAuth 2.0 services to provide a simplified high-level API
 * for common OAuth 2.0 client operations, including DPoP support (RFC 9449).
 */
@Inject
@ContributesBinding(SessionScope::class, binding = binding<OAuth2Client>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("OAuth2ClientImpl", exact = true)
class OAuth2ClientImpl(
    private val fetchMetadataCommand: FetchAuthorizationServerMetadataCommand,
    private val createPkceCommand: CreatePkceCommand,
    private val createAuthorizationRequestUrlCommand: CreateAuthorizationRequestUrlCommand,
    private val parseAuthorizationResponseCommand: ParseAuthorizationResponseCommand,
    private val exchangeTokenCommand: ExchangeTokenCommand,
    private val introspectTokenCommand: IntrospectTokenCommand,
    private val dpopService: DpopService,
    private val validateIdTokenCommand: com.sphereon.oauth2.common.command.ValidateIdTokenCommand,
    private val fetchUserInfoCommand: com.sphereon.oauth2.client.command.FetchUserInfoCommand,
) : OAuth2Client {
    override suspend fun fetchAuthorizationServerMetadata(issuer: String): IdkResult<AuthorizationServerMetadata, IdkError> = fetchMetadataCommand.execute(FetchServerMetadataArgs(issuer))

    override fun isDpopSupported(authorizationServerMetadata: AuthorizationServerMetadata): Boolean = !authorizationServerMetadata.dpopSigningAlgValuesSupported.isNullOrEmpty()

    override suspend fun initiateAuthorization(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientId: String,
        redirectUri: String,
        scope: String?,
        state: String?,
        resource: List<String>?,
        clientAuthentication: ClientAuthenticationConfig?,
        dpopContext: DpopContext?,
        additionalParameters: Map<String, String>,
    ): IdkResult<AuthorizationResult, IdkError> {
        // Generate PKCE if supported by the server
        val supportedMethods = authorizationServerMetadata.codeChallengeMethodsSupported
        val pkceData =
            if (supportedMethods != null && supportedMethods.isNotEmpty()) {
                val allowedMethods =
                    supportedMethods.mapNotNull { method ->
                        when (method.uppercase()) {
                            "S256" -> PkceMethod.S256
                            "PLAIN" -> PkceMethod.PLAIN
                            else -> null
                        }
                    }

                if (allowedMethods.isNotEmpty()) {
                    val pkceResult =
                        createPkceCommand.execute(
                            CreatePkceArgs(
                                codeVerifier = null,
                                allowedMethods = allowedMethods,
                            ),
                        )
                    if (pkceResult.isErr) {
                        // PKCE generation failed, but continue without it
                        null
                    } else {
                        pkceResult.value
                    }
                } else {
                    null
                }
            } else {
                null
            }

        // Build authorization request
        val authRequest =
            AuthorizationRequest(
                clientId = clientId,
                redirectUri = redirectUri,
                responseType = "code",
                scope = scope,
                state = state,
                codeChallenge = pkceData?.codeChallenge,
                codeChallengeMethod = pkceData?.codeChallengeMethod?.value,
                resource = resource?.joinToString(" "),
                dpopJkt = dpopContext?.jwkThumbprint, // Add DPoP JWK thumbprint if using DPoP
            )

        // Create authorization URL
        val urlResult =
            createAuthorizationRequestUrlCommand.execute(
                CreateAuthorizationRequestUrlOptions(
                    authorizationServerMetadata = authorizationServerMetadata,
                    authorizationRequest = authRequest,
                    pkceCodeVerifier = pkceData?.codeVerifier,
                    clientAuthentication = clientAuthentication,
                ),
            )

        return if (urlResult.isErr) {
            Err(urlResult.error)
        } else {
            Ok(
                AuthorizationResult(
                    authorizationUrl = urlResult.value.authorizationRequestUrl,
                    pkceData = pkceData,
                    state = state,
                    dpopContext = dpopContext,
                ),
            )
        }
    }

    override suspend fun parseAuthorizationResponse(redirectUrl: String): IdkResult<AuthorizationResponse, IdkError> {
        val result = parseAuthorizationResponseCommand.execute(ParseAuthorizationResponseArgs(redirectUrl))
        return when {
            result.isErr -> {
                Err(result.error)
            }

            result.value is ParsedAuthorizationResponse.Success -> {
                Ok((result.value as ParsedAuthorizationResponse.Success).response)
            }

            result.value is ParsedAuthorizationResponse.Error -> {
                val errorResponse = (result.value as ParsedAuthorizationResponse.Error).response
                Err(
                    IdkError(
                        code = errorResponse.error,
                        message =
                            IdkError.Message(
                                i18nKey = "oauth2.error.error_response",
                                defaultMessage = errorResponse.errorDescription ?: errorResponse.error,
                            ),
                        meta =
                            mapOf(
                                "error" to errorResponse.error,
                                "error_description" to errorResponse.errorDescription,
                                "error_uri" to errorResponse.errorUri,
                            ),
                    ),
                )
            }

            else -> {
                Err(
                    IdkError(
                        code = "validation_failed",
                        message =
                            IdkError.Message(
                                i18nKey = "oauth2.error.validation_failed",
                                defaultMessage = "Unknown authorization response type",
                            ),
                    ),
                )
            }
        }
    }

    override suspend fun exchangeAuthorizationCode(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientAuthentication: ClientAuthenticationConfig,
        authorizationCode: String,
        redirectUri: String,
        pkceData: PkceData?,
        resource: List<String>?,
        dpopContext: DpopContext?,
    ): IdkResult<TokenResponse, IdkError> {
        val tokenEndpoint =
            authorizationServerMetadata.tokenEndpoint
                ?: return Err(
                    IdkError(
                        code = "validation_failed",
                        message =
                            IdkError.Message(
                                i18nKey = "oauth2.error.validation_failed",
                                defaultMessage = "token_endpoint is required",
                            ),
                        meta = mapOf("validationErrors" to listOf("Authorization server metadata missing token_endpoint")),
                    ),
                )

        // Extract client credentials from authentication config
        val (clientId, clientSecret) = extractClientCredentials(clientAuthentication)

        // Use helper method that handles DPoP nonce retry logic
        return exchangeTokenWithDpopRetry(
            tokenEndpoint = tokenEndpoint,
            dpopContext = dpopContext,
            createTokenRequest = { dpopProof ->
                TokenRequest(
                    grantType = GrantType.AUTHORIZATION_CODE.value,
                    code = authorizationCode,
                    redirectUri = redirectUri,
                    codeVerifier = pkceData?.codeVerifier,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    resource = resource ?: emptyList(),
                    dpop = dpopProof,
                )
            },
        )
    }

    private fun extractClientCredentials(config: ClientAuthenticationConfig): Pair<String?, String?> =
        when (config) {
            is ClientAuthenticationConfig.Post -> Pair(config.credentials.clientId, config.credentials.clientSecret)
            is ClientAuthenticationConfig.Basic -> Pair(config.credentials.clientId, config.credentials.clientSecret)
            is ClientAuthenticationConfig.None -> Pair(config.clientId, null)
            else -> Pair(null, null)
        }

    override suspend fun exchangePreAuthorizedCode(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientAuthentication: ClientAuthenticationConfig,
        preAuthorizedCode: String,
        txCode: String?,
        resource: List<String>?,
        dpopContext: DpopContext?,
    ): IdkResult<TokenResponse, IdkError> {
        val tokenEndpoint =
            authorizationServerMetadata.tokenEndpoint
                ?: return Err(
                    IdkError(
                        code = "validation_failed",
                        message =
                            IdkError.Message(
                                i18nKey = "oauth2.error.validation_failed",
                                defaultMessage = "token_endpoint is required",
                            ),
                        meta = mapOf("validationErrors" to listOf("Authorization server metadata missing token_endpoint")),
                    ),
                )

        // Use helper method that handles DPoP nonce retry logic
        return exchangeTokenWithDpopRetry(
            tokenEndpoint = tokenEndpoint,
            dpopContext = dpopContext,
            createTokenRequest = { dpopProof ->
                TokenRequest(
                    grantType = "urn:ietf:params:oauth:grant-type:pre-authorized_code",
                    preAuthorizedCode = preAuthorizedCode,
                    txCode = txCode,
                    resource = resource ?: emptyList(),
                    dpop = dpopProof,
                )
            },
        )
    }

    override suspend fun refreshAccessToken(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientAuthentication: ClientAuthenticationConfig,
        refreshToken: String,
        scope: String?,
        resource: List<String>?,
        dpopContext: DpopContext?,
    ): IdkResult<TokenResponse, IdkError> {
        val tokenEndpoint =
            authorizationServerMetadata.tokenEndpoint
                ?: return Err(
                    IdkError(
                        code = "validation_failed",
                        message =
                            IdkError.Message(
                                i18nKey = "oauth2.error.validation_failed",
                                defaultMessage = "token_endpoint is required",
                            ),
                        meta = mapOf("validationErrors" to listOf("Authorization server metadata missing token_endpoint")),
                    ),
                )

        // Use helper method that handles DPoP nonce retry logic
        return exchangeTokenWithDpopRetry(
            tokenEndpoint = tokenEndpoint,
            dpopContext = dpopContext,
            createTokenRequest = { dpopProof ->
                TokenRequest(
                    grantType = GrantType.REFRESH_TOKEN.value,
                    refreshToken = refreshToken,
                    scope = scope,
                    resource = resource ?: emptyList(),
                    dpop = dpopProof,
                )
            },
        )
    }

    override suspend fun introspectToken(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientAuthentication: ClientAuthenticationConfig,
        token: String,
        tokenTypeHint: String?,
    ): IdkResult<TokenIntrospectionResponse, IdkError> =
        introspectTokenCommand.execute(
            IntrospectTokenArgs(
                authorizationServerMetadata = authorizationServerMetadata,
                token = token,
                clientAuthentication = clientAuthentication,
                tokenTypeHint = tokenTypeHint,
                additionalParameters = emptyMap(),
            ),
        )

    /**
     * Helper method that executes token exchange with DPoP nonce retry logic
     *
     * If a token request fails with DpopNonceRequired error, this method will:
     * 1. Extract the new nonce from the error
     * 2. Generate a new DPoP proof with the updated nonce
     * 3. Retry the token request once
     *
     * This implements the DPoP nonce retry mechanism described in RFC 9449 Section 8.
     *
     * @param tokenEndpoint The token endpoint URL
     * @param dpopContext Optional DPoP context for generating proofs
     * @param createTokenRequest Lambda that creates a TokenRequest given an optional DPoP proof
     * @return IdkResult containing the token response or error
     */
    private suspend fun exchangeTokenWithDpopRetry(
        tokenEndpoint: String,
        dpopContext: DpopContext?,
        createTokenRequest: (dpopProof: String?) -> TokenRequest,
    ): IdkResult<TokenResponse, IdkError> {
        // Generate initial DPoP proof if DPoP context is provided
        val initialDpopProof =
            dpopContext?.let { context ->
                val proofOptions =
                    CreateDpopProofOptions(
                        issuer = context.issuer,
                        httpMethod = HttpMethod.POST,
                        httpUrl = tokenEndpoint,
                        nonce = context.dpopNonce,
                    )
                val proofResult = dpopService.createDpopProof(proofOptions, context.publicJwk)
                proofResult.getOrElse { error ->
                    return Err(
                        IdkError(
                            code = "server_error",
                            message =
                                IdkError.Message(
                                    i18nKey = "oauth2.error.server_error",
                                    defaultMessage = "DPoP proof generation failed: ${error.message.defaultMessage ?: error.code}",
                                ),
                        ),
                    )
                }
            }

        // First attempt
        val tokenRequest = createTokenRequest(initialDpopProof?.dpopProof)
        val result = exchangeTokenCommand.execute(ExchangeTokenArgs(tokenEndpoint, tokenRequest))

        // Check if we need to retry with a new nonce
        return if (result.isOk) {
            result
        } else {
            val error = result.error
            val dpopNonce = error.meta["dpop_nonce"] as? String
            // If DPoP nonce is required and we have a DPoP context, retry once
            if (error.code == "use_dpop_nonce" && dpopNonce != null && dpopContext != null) {
                // Generate new DPoP proof with the server-provided nonce
                val retryProofOptions =
                    CreateDpopProofOptions(
                        issuer = dpopContext.issuer,
                        httpMethod = HttpMethod.POST,
                        httpUrl = tokenEndpoint,
                        nonce = dpopNonce,
                    )
                val retryProofResult = dpopService.createDpopProof(retryProofOptions, dpopContext.publicJwk)
                val retryProof =
                    retryProofResult.getOrElse { retryError ->
                        return Err(
                            IdkError(
                                code = "server_error",
                                message =
                                    IdkError.Message(
                                        i18nKey = "oauth2.error.server_error",
                                        defaultMessage = "DPoP proof generation failed on retry: ${retryError.message.defaultMessage ?: retryError.code}",
                                    ),
                            ),
                        )
                    }

                // Retry the token request with the new DPoP proof
                val retryRequest = createTokenRequest(retryProof.dpopProof)
                exchangeTokenCommand.execute(ExchangeTokenArgs(tokenEndpoint, retryRequest))
            } else {
                // Not a nonce error or no DPoP context, return the error as-is
                result
            }
        }
    }

    override suspend fun validateIdToken(
        idToken: String,
        options: com.sphereon.oauth2.common.model.IdTokenValidationOptions,
    ): IdkResult<com.sphereon.oauth2.common.model.ValidatedIdToken, IdkError> = validateIdTokenCommand.execute(ValidateIdTokenArgs(idToken, options))

    override suspend fun fetchUserInfo(
        accessToken: String,
        metadata: AuthorizationServerMetadata,
    ): IdkResult<com.sphereon.oauth2.client.command.FetchUserInfoResult, IdkError> {
        val userinfoEndpoint =
            metadata.userinfoEndpoint
                ?: return Err(
                    IdkError(
                        code = "invalid_request",
                        message =
                            IdkError.Message(
                                i18nKey = "oauth2.client.error.no_userinfo_endpoint",
                                defaultMessage = "Authorization server metadata does not include a userinfo_endpoint",
                            ),
                    ),
                )

        return fetchUserInfoCommand.execute(
            com.sphereon.oauth2.client.command.FetchUserInfoArgs(
                accessToken = accessToken,
                userinfoEndpoint = userinfoEndpoint,
            ),
        )
    }
}
