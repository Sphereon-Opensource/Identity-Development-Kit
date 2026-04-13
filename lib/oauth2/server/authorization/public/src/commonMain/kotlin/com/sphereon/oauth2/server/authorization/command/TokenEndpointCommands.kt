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

package com.sphereon.oauth2.server.authorization.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.StringResult
import com.sphereon.oauth2.common.model.ActorClaim
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.model.AuthorizationCodeData
import kotlinx.serialization.json.JsonElement

// ============================================================================
// ParseTokenRequestCommand
// ============================================================================

/**
 * Arguments for parsing a token request
 */
data class ParseTokenRequestArgs(
    val requestBody: Map<String, List<String>>,
    val requestHeaders: Map<String, String> = emptyMap(),
)

/**
 * Parse token request command
 *
 * RFC 6749 Section 4.1.3: Token Request
 *
 * Parses and validates the incoming token request from the client.
 * Extracts grant type and grant-specific parameters.
 */
interface ParseTokenRequestCommand : ServiceCommand<ParseTokenRequestArgs, TokenRequestData> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.token.parse"
    }
}

// ============================================================================
// VerifyTokenExchangeGrantCommand
// ============================================================================

/**
 * Arguments for verifying a token exchange grant
 */
data class VerifyTokenExchangeGrantArgs(
    val subjectToken: String,
    val subjectTokenType: String,
    val actorToken: String? = null,
    val actorTokenType: String? = null,
    val resources: List<String> = emptyList(),
    val audiences: List<String> = emptyList(),
    val scope: String? = null,
    val requestedTokenType: String? = null,
    val clientId: String,
)

/**
 * Verify token exchange grant command
 *
 * RFC 8693: OAuth 2.0 Token Exchange
 *
 * Verifies a token exchange grant:
 * - Validates subject token
 * - Validates actor token (if present)
 * - Evaluates token exchange policy
 * - Determines delegation vs impersonation
 * - Builds actor claim chain for delegation
 */
interface VerifyTokenExchangeGrantCommand : ServiceCommand<VerifyTokenExchangeGrantArgs, VerifiedTokenExchangeGrant> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.tokenexchange.verify"
    }
}

// ============================================================================
// VerifyAuthorizationCodeGrantCommand
// ============================================================================

/**
 * Arguments for verifying an authorization code grant
 */
data class VerifyAuthorizationCodeGrantArgs(
    val code: String,
    val redirectUri: String,
    val clientId: String,
    val codeVerifier: String? = null,
)

/**
 * Verify authorization code grant command
 *
 * RFC 6749 Section 4.1.3: Authorization Code Grant
 *
 * Verifies an authorization code grant token request:
 * - Validates authorization code
 * - Verifies redirect_uri matches
 * - Validates PKCE (if used)
 * - Ensures code hasn't been used
 * - Verifies client authentication
 */
interface VerifyAuthorizationCodeGrantCommand : ServiceCommand<VerifyAuthorizationCodeGrantArgs, VerifiedAuthorizationCodeGrant> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.authcode.verify"
    }
}

// ============================================================================
// VerifyRefreshTokenGrantCommand
// ============================================================================

/**
 * Arguments for verifying a refresh token grant
 */
data class VerifyRefreshTokenGrantArgs(
    val refreshToken: String,
    val clientId: String,
    val requestedScope: String? = null,
)

/**
 * Verify refresh token grant command
 *
 * RFC 6749 Section 6: Refreshing an Access Token
 *
 * Verifies a refresh token grant:
 * - Validates refresh token
 * - Ensures token not expired/revoked
 * - Verifies client authentication
 * - Validates requested scope (must be subset of original)
 */
interface VerifyRefreshTokenGrantCommand : ServiceCommand<VerifyRefreshTokenGrantArgs, VerifiedRefreshTokenGrant> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.refresh.verify"
    }
}

// ============================================================================
// VerifyClientCredentialsGrantCommand
// ============================================================================

/**
 * Arguments for verifying a client credentials grant
 */
data class VerifyClientCredentialsGrantArgs(
    val clientId: String,
    val requestedScope: String? = null,
)

/**
 * Verify client credentials grant command
 *
 * RFC 6749 Section 4.4: Client Credentials Grant
 *
 * Verifies a client credentials grant:
 * - Validates client authentication
 * - Verifies client is authorized for this grant type
 * - Validates requested scope
 */
interface VerifyClientCredentialsGrantCommand : ServiceCommand<VerifyClientCredentialsGrantArgs, VerifiedClientCredentialsGrant> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.clientcreds.verify"
    }
}

// ============================================================================
// CreateAccessTokenCommand
// ============================================================================

/**
 * Arguments for creating an access token
 */
data class CreateAccessTokenArgs(
    val subject: String,
    val clientId: String,
    val scope: String? = null,
    val audience: List<String> = emptyList(),
    val expiresInSeconds: Int = 3600,
    val dpopJkt: String? = null,
    val clientInstanceKeyJkt: String? = null,
    val additionalClaims: Map<String, Any> = emptyMap(),
)

/**
 * Create access token command
 *
 * Generates a new access token (JWT format recommended).
 * Includes all necessary claims and bindings.
 */
interface CreateAccessTokenCommand : ServiceCommand<CreateAccessTokenArgs, StringResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.accesstoken.create"
    }
}

// ============================================================================
// CreateRefreshTokenCommand
// ============================================================================

/**
 * Arguments for creating a refresh token
 */
data class CreateRefreshTokenArgs(
    val subject: String,
    val clientId: String,
    val scope: String? = null,
    val expiresInSeconds: Int? = null,
    val dpopJkt: String? = null,
    val clientInstanceKeyJkt: String? = null,
)

/**
 * Create refresh token command
 *
 * Generates a new refresh token (opaque or JWT).
 */
interface CreateRefreshTokenCommand : ServiceCommand<CreateRefreshTokenArgs, StringResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.refreshtoken.create"
    }
}

// ============================================================================
// CreateTokenResponseCommand
// ============================================================================

/**
 * Arguments for creating a token response
 */
data class CreateTokenResponseArgs(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Int = 3600,
    val refreshToken: String? = null,
    val scope: String? = null,
    val idToken: String? = null,
    val cNonce: String? = null,
    val cNonceExpiresIn: Int? = null,
    val issuedTokenType: String? = null,
    /** OID4VCI 1.1 Section 7.2: authorization_details array to include in the token response. */
    val authorizationDetails: JsonElement? = null,
    val additionalParameters: Map<String, Any> = emptyMap(),
)

/**
 * Create token response command
 *
 * Assembles the complete token response including:
 * - Access token
 * - Token type (Bearer or DPoP)
 * - Expires in
 * - Refresh token (optional)
 * - Scope (optional)
 * - Additional parameters (c_nonce, etc.)
 */
interface CreateTokenResponseCommand : ServiceCommand<CreateTokenResponseArgs, TokenResponse> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.tokenresponse.create"
    }
}

// ============================================================================
// Data Models
// ============================================================================

/**
 * Parsed token request data
 */
data class TokenRequestData(
    /**
     * Grant type from the request
     */
    val grantType: GrantType,
    /**
     * Client ID (from body or authentication)
     */
    val clientId: String,
    /**
     * Client authentication config (extracted from headers/body)
     */
    val clientAuthentication: ClientAuthenticationConfig,
    /**
     * Grant-specific parameters
     */
    val grantParameters: GrantParameters,
    /**
     * DPoP proof JWT (if present)
     */
    val dpopProof: String? = null,
    /**
     * HTTP method and URL (for DPoP verification)
     */
    val httpMethod: String = "POST",
    val httpUrl: String,
)

/**
 * Grant-specific parameters
 */
sealed interface GrantParameters {
    /**
     * Authorization code grant parameters
     */
    data class AuthorizationCode(
        val code: String,
        val redirectUri: String,
        val codeVerifier: String? = null,
    ) : GrantParameters

    /**
     * Refresh token grant parameters
     */
    data class RefreshToken(
        val refreshToken: String,
        val scope: String? = null,
    ) : GrantParameters

    /**
     * Client credentials grant parameters
     */
    data class ClientCredentials(
        val scope: String? = null,
    ) : GrantParameters

    /**
     * Pre-authorized code grant parameters (OpenID4VCI)
     */
    data class PreAuthorizedCode(
        val preAuthorizedCode: String,
        val txCode: String? = null,
    ) : GrantParameters

    /**
     * Token exchange grant parameters (RFC 8693)
     */
    data class TokenExchange(
        val subjectToken: String,
        val subjectTokenType: String,
        val actorToken: String? = null,
        val actorTokenType: String? = null,
        val resources: List<String> = emptyList(),
        val audiences: List<String> = emptyList(),
        val scope: String? = null,
        val requestedTokenType: String? = null,
    ) : GrantParameters
}

/**
 * Verified authorization code grant
 */
data class VerifiedAuthorizationCodeGrant(
    /**
     * Authorization code data
     */
    val codeData: AuthorizationCodeData,
    /**
     * Subject (user ID)
     */
    val subject: String,
    /**
     * Client ID
     */
    val clientId: String,
    /**
     * Granted scope
     */
    val scope: String? = null,
    /**
     * DPoP JWK thumbprint (if DPoP-bound)
     */
    val dpopJkt: String? = null,
    /**
     * User claims carried through the authorization code exchange.
     * Used for id_token population (scope-filtered) at token issuance.
     */
    val userClaims: Map<String, Any> = emptyMap(),
    /**
     * Additional authorization details
     */
    val additionalData: Map<String, Any> = emptyMap(),
)

/**
 * Verified refresh token grant
 */
data class VerifiedRefreshTokenGrant(
    /**
     * Subject (user ID)
     */
    val subject: String,
    /**
     * Client ID
     */
    val clientId: String,
    /**
     * Granted scope (may be subset of original)
     */
    val scope: String? = null,
    /**
     * DPoP JWK thumbprint (if DPoP-bound)
     */
    val dpopJkt: String? = null,
    /**
     * Original refresh token ID (for rotation)
     */
    val refreshTokenId: String,
)

/**
 * Verified client credentials grant
 */
data class VerifiedClientCredentialsGrant(
    /**
     * Subject (typically client ID)
     */
    val subject: String,
    /**
     * Client ID
     */
    val clientId: String,
    /**
     * Granted scope
     */
    val scope: String? = null,
)

/**
 * Verified token exchange grant (RFC 8693)
 */
data class VerifiedTokenExchangeGrant(
    val subject: String,
    val clientId: String,
    val scope: String? = null,
    val audience: List<String> = emptyList(),
    val resource: List<String> = emptyList(),
    val issuedTokenType: String,
    val isDelegation: Boolean,
    val actorSubject: String? = null,
    val actorClaim: ActorClaim? = null,
    val additionalClaims: Map<String, Any> = emptyMap(),
)
