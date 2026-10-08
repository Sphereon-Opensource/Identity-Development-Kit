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

package com.sphereon.oauth2.client.client

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.oauth2.client.command.AuthorizationResponseSource
import com.sphereon.oauth2.client.command.CompleteOidcLoginArgs
import com.sphereon.oauth2.client.command.OidcLoginResult
import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.OAuth2ResponseMode
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.common.model.TokenResponse
import kotlin.jvm.JvmOverloads

/**
 * High-level OAuth 2.0 client facade
 *
 * Provides simplified API for common OAuth 2.0 flows by integrating
 * all underlying services (metadata, authorization, token, PKCE, introspection).
 *
 * Supports:
 * - Authorization Code Flow (RFC 6749 Section 4.1)
 * - Authorization Code Flow with PKCE (RFC 7636)
 * - Refresh Token Flow (RFC 6749 Section 6)
 * - Pre-Authorized Code Flow (OpenID4VCI)
 * - Token Introspection (RFC 7662)
 */
@JsExportCompat
interface OAuth2Client {
    /**
     * Fetches authorization server metadata using well-known discovery
     *
     * @param issuer The issuer URL (will try .well-known endpoints)
     * @return IdkResult containing authorization server metadata
     */
    suspend fun fetchAuthorizationServerMetadata(issuer: String): IdkResult<AuthorizationServerMetadata, IdkError>

    /**
     * Checks if DPoP is supported by the authorization server
     *
     * @param authorizationServerMetadata The authorization server metadata
     * @return True if DPoP is supported
     */
    fun isDpopSupported(authorizationServerMetadata: AuthorizationServerMetadata): Boolean

    /**
     * Façade grouping the OIDC login API (`initiate` + `complete`) so the secure path is the
     * obvious default for OIDC RPs. Raw OAuth2 methods (`initiateAuthorization`,
     * `exchangeAuthorizationCode`) remain on the root for non-OIDC callers.
     */
    @JsExportIgnoreCompat
    val oidcLogin: OidcLoginApi

    /**
     * Initiates an OIDC authorization-code login.
     *
     * Unlike [initiateAuthorization], this method generates CSRF `state`, replay-protection
     * `nonce`, and an S256 PKCE verifier/challenge using the injected CSPRNG, persists the
     * resulting [com.sphereon.oauth2.client.transaction.OidcLoginTransaction] via the
     * [com.sphereon.oauth2.client.transaction.OidcLoginTransactionStore], and returns the
     * authorization URL plus the `state` value the caller may want to echo back. Callback
     * handling via `completeOidcLogin` consumes the transaction atomically.
     *
     * All parameters are OIDC Core-aligned — callers building non-OIDC OAuth2 flows should
     * continue to use [initiateAuthorization].
     *
     * @param issuer The OIDC issuer URL — metadata is discovered via [fetchAuthorizationServerMetadata].
     * @param clientId Registered OAuth2 client identifier.
     * @param redirectUri Registered redirect URI for the callback.
     * @param scopes OAuth2/OIDC scopes; defaults to `openid`.
     * @param responseMode Response mode to request; defaults to `query`.
     * @param prompt Optional `prompt` parameter (`login`, `consent`, `none`, ...).
     * @param loginHint Optional `login_hint` parameter.
     * @param tenantId Optional multi-tenant partition key for the transaction store.
     */
    @JsExportIgnoreCompat
    suspend fun initiateOidcLogin(
        issuer: String,
        clientId: String,
        redirectUri: String,
        scopes: Set<String> = setOf("openid"),
        responseMode: OAuth2ResponseMode = OAuth2ResponseMode.QUERY,
        prompt: String? = null,
        loginHint: String? = null,
        tenantId: String? = null,
        resource: String? = null,
        audience: String? = null,
        ownerHandleDigest: String? = null,
        grantBinding: String? = null,
        clientCorrelation: String? = null,
    ): IdkResult<OidcLoginInitiation, IdkError>

    /**
     * Metadata-typed overload of [initiateOidcLogin] for callers that have already resolved
     * the authorization server metadata. Primarily used by tests and advanced integrations that
     * cache metadata themselves.
     */
    @JsExportIgnoreCompat
    suspend fun initiateOidcLogin(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientId: String,
        redirectUri: String,
        scopes: Set<String> = setOf("openid"),
        responseMode: OAuth2ResponseMode = OAuth2ResponseMode.QUERY,
        prompt: String? = null,
        loginHint: String? = null,
        tenantId: String? = null,
        resource: String? = null,
        audience: String? = null,
        ownerHandleDigest: String? = null,
        grantBinding: String? = null,
        clientCorrelation: String? = null,
    ): IdkResult<OidcLoginInitiation, IdkError>

    /**
     * Initiates an authorization request
     *
     * This is a high-level method that:
     * 1. Generates PKCE challenge/verifier if supported by the server
     * 2. Includes dpop_jkt parameter if DPoP context is provided (RFC 9449)
     * 3. Pushes authorization request (PAR) if required/supported
     * 4. Builds the authorization request URL
     *
     * @param authorizationServerMetadata The authorization server metadata
     * @param clientId The client identifier
     * @param redirectUri The redirect URI for the authorization response
     * @param scope Optional OAuth 2.0 scope values
     * @param state Optional state parameter for CSRF protection
     * @param resource Optional resource indicators (RFC 8707)
     * @param clientAuthentication Optional client authentication for PAR requests
     * @param dpopContext Optional DPoP context for DPoP-bound tokens (RFC 9449)
     * @param additionalParameters Additional request parameters
     * @return IdkResult containing authorization URL and optional PKCE/DPoP data
     */
    @JsExportIgnoreCompat
    suspend fun initiateAuthorization(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientId: String,
        redirectUri: String,
        scope: String? = null,
        state: String? = null,
        resource: List<String>? = null,
        clientAuthentication: ClientAuthenticationConfig? = null,
        dpopContext: DpopContext? = null,
        additionalParameters: Map<String, String> = emptyMap(),
    ): IdkResult<AuthorizationResult, IdkError>

    /**
     * Parses an authorization response from a redirect URL
     *
     * @param redirectUrl The full redirect URL containing the authorization response
     * @return IdkResult containing the parsed authorization response
     */
    suspend fun parseAuthorizationResponse(redirectUrl: String): IdkResult<AuthorizationResponse, IdkError>

    /**
     * Exchanges an authorization code for an access token
     *
     * Supports Authorization Code Flow with optional PKCE verification and DPoP.
     *
     * @param authorizationServerMetadata The authorization server metadata
     * @param clientAuthentication Client authentication configuration
     * @param authorizationCode The authorization code from the authorization response
     * @param redirectUri The redirect URI used in the authorization request
     * @param pkceData Optional PKCE data from initiateAuthorization
     * @param resource Optional resource indicators
     * @param dpopContext Optional DPoP context for DPoP-bound tokens (RFC 9449)
     * @return IdkResult containing the token response (includes dpopNonce if server sent one)
     */
    suspend fun exchangeAuthorizationCode(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientAuthentication: ClientAuthenticationConfig,
        authorizationCode: String,
        redirectUri: String,
        pkceData: PkceData? = null,
        resource: List<String>? = null,
        dpopContext: DpopContext? = null,
        audience: List<String>? = null,
    ): IdkResult<TokenResponse, IdkError>

    /**
     * Exchanges a pre-authorized code for an access token (OpenID4VCI)
     *
     * @param authorizationServerMetadata The authorization server metadata
     * @param clientAuthentication Client authentication configuration
     * @param preAuthorizedCode The pre-authorized code
     * @param txCode Optional transaction code (user PIN)
     * @param resource Optional resource indicators
     * @param dpopContext Optional DPoP context for DPoP-bound tokens (RFC 9449)
     * @return IdkResult containing the token response
     */
    suspend fun exchangePreAuthorizedCode(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientAuthentication: ClientAuthenticationConfig,
        preAuthorizedCode: String,
        txCode: String? = null,
        resource: List<String>? = null,
        dpopContext: DpopContext? = null,
    ): IdkResult<TokenResponse, IdkError>

    /**
     * Refreshes an access token using a refresh token
     *
     * @param authorizationServerMetadata The authorization server metadata
     * @param clientAuthentication Client authentication configuration
     * @param refreshToken The refresh token
     * @param scope Optional scope (must be subset of original scope)
     * @param resource Optional resource indicators
     * @param dpopContext Optional DPoP context for DPoP-bound tokens (RFC 9449)
     * @return IdkResult containing the new token response
     */
    suspend fun refreshAccessToken(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientAuthentication: ClientAuthenticationConfig,
        refreshToken: String,
        scope: String? = null,
        resource: List<String>? = null,
        dpopContext: DpopContext? = null,
        audience: List<String>? = null,
    ): IdkResult<TokenResponse, IdkError>

    /**
     * Introspects a token to get its metadata and validity status
     *
     * @param authorizationServerMetadata The authorization server metadata
     * @param clientAuthentication Client authentication configuration
     * @param token The token to introspect (access token or refresh token)
     * @param tokenTypeHint Optional hint about token type ("access_token" or "refresh_token")
     * @return IdkResult containing the introspection response
     */
    suspend fun introspectToken(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientAuthentication: ClientAuthenticationConfig,
        token: String,
        tokenTypeHint: String? = null,
    ): IdkResult<TokenIntrospectionResponse, IdkError>

    /**
     * Validates an OpenID Connect ID Token
     *
     * Performs complete ID Token validation including:
     * - JWT signature verification
     * - Issuer, audience, expiration validation
     * - Nonce validation (if nonce was sent)
     * - Access token hash validation (if access token provided)
     * - Authorization code hash validation (if code provided)
     *
     * Used in:
     * - OpenID Connect authentication flows
     * - OpenID4VP presentation exchange
     * - OpenID4VCI credential issuance
     *
     * @param idToken The ID Token JWT string (from token response)
     * @param options Validation options (expected issuer, audience, nonce, etc.)
     * @return IdkResult containing the validated ID Token payload
     */
    suspend fun validateIdToken(
        idToken: String,
        options: com.sphereon.oauth2.common.model.IdTokenValidationOptions,
    ): IdkResult<com.sphereon.oauth2.common.model.ValidatedIdToken, IdkError>

    /**
     * Fetches user claims from the OIDC UserInfo endpoint
     *
     * OpenID Connect Core 1.0 Section 5.3: UserInfo Endpoint
     *
     * @param accessToken Bearer token with openid scope
     * @param metadata Authorization server metadata (used to discover userinfo_endpoint)
     * @return IdkResult containing the UserInfo response with claims
     */
    suspend fun fetchUserInfo(
        accessToken: String,
        metadata: AuthorizationServerMetadata,
    ): IdkResult<com.sphereon.oauth2.client.command.FetchUserInfoResult, IdkError>
}

/**
 * Result of [OAuth2Client.initiateOidcLogin]: the authorization URL to redirect the user agent
 * to, plus the generated `state` so the RP can echo it or log it. The `nonce` and PKCE verifier
 * are held server-side in the transaction store and are NOT returned to the caller — that keeps
 * the secure defaults in the happy path.
 */
@JsExportCompat
public data class OidcLoginInitiation(
    public val authorizationUrl: String,
    public val state: String,
)

/**
 * Façade grouping the OIDC login workflow into `initiate` + `complete`, so that callers have a
 * single obvious place to reach for the secure authorization-code flow. Raw OAuth2 methods live
 * on the parent [OAuth2Client] for non-OIDC callers.
 */
@JsExportIgnoreCompat
interface OidcLoginApi {
    /** Begin an OIDC login — generates state/nonce/PKCE and returns the authorization URL. */
    suspend fun initiate(
        issuer: String,
        clientId: String,
        redirectUri: String,
        scopes: Set<String> = setOf("openid"),
        responseMode: OAuth2ResponseMode = OAuth2ResponseMode.QUERY,
        prompt: String? = null,
        loginHint: String? = null,
        tenantId: String? = null,
        resource: String? = null,
        audience: String? = null,
        ownerHandleDigest: String? = null,
        grantBinding: String? = null,
        clientCorrelation: String? = null,
    ): IdkResult<OidcLoginInitiation, IdkError>

    /** Metadata-typed variant of [initiate] — useful when metadata is cached elsewhere. */
    suspend fun initiate(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientId: String,
        redirectUri: String,
        scopes: Set<String> = setOf("openid"),
        responseMode: OAuth2ResponseMode = OAuth2ResponseMode.QUERY,
        prompt: String? = null,
        loginHint: String? = null,
        tenantId: String? = null,
        resource: String? = null,
        audience: String? = null,
        ownerHandleDigest: String? = null,
        grantBinding: String? = null,
        clientCorrelation: String? = null,
    ): IdkResult<OidcLoginInitiation, IdkError>

    /** Complete an OIDC login by handling the authorization response callback. */
    suspend fun complete(
        clientId: String,
        clientAuthentication: ClientAuthenticationConfig,
        callbackUrl: String,
        callbackFormBody: String? = null,
        responseSource: AuthorizationResponseSource = AuthorizationResponseSource.QUERY,
        tenantId: String? = null,
    ): IdkResult<OidcLoginResult, IdkError>
}

/**
 * Result of initiateAuthorization operation
 *
 * @property authorizationUrl The URL to redirect the user to for authorization
 * @property pkceData Optional PKCE data (needed for token exchange)
 * @property state The state parameter for CSRF protection
 * @property dpopContext Optional DPoP context (if DPoP was used)
 */
@JsExportCompat
data class AuthorizationResult
    @JvmOverloads
    constructor(
        val authorizationUrl: String,
        val pkceData: PkceData? = null,
        val state: String? = null,
        val dpopContext: DpopContext? = null,
    )

/**
 * DPoP context for tracking DPoP state across OAuth flows
 *
 * @property publicJwk The public key embedded in DPoP proofs
 * @property issuer The signing key/identifier for creating DPoP proofs
 * @property jwkThumbprint The JWK thumbprint (for token binding verification)
 * @property dpopNonce Optional server-provided nonce (updated after each token response)
 */
@JsExportCompat
data class DpopContext
    @JvmOverloads
    constructor(
        val publicJwk: Jwk,
        val issuer: ManagedIdentifierOptsOrResult,
        val jwkThumbprint: String,
        val dpopNonce: String? = null,
    )
