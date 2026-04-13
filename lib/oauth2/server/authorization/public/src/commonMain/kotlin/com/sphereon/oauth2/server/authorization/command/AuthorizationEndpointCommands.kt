/*
 * © 2025 Sphereon International B.V.
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
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.common.model.ResponseType
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.ConsentDecision

// ============================================================================
// 1. ParseAuthorizationRequestCommand
// ============================================================================

/**
 * Arguments for parsing an authorization request
 */
data class ParseAuthorizationRequestArgs(
    val queryParameters: Map<String, String>
)

/**
 * Parse authorization request command
 *
 * RFC 6749 Section 4.1.1: Authorization Request
 *
 * Parses and validates the incoming authorization request.
 * Extracts all parameters from query string.
 */
interface ParseAuthorizationRequestCommand : ServiceCommand<ParseAuthorizationRequestArgs, AuthorizationRequestData> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.authorization.parse"
    }
}

// ============================================================================
// 2. VerifyAuthorizationRequestCommand
// ============================================================================

/**
 * Verify authorization request command
 *
 * RFC 6749 Section 4.1.2.1: Error Response
 *
 * Verifies the authorization request:
 * - Validates client_id
 * - Validates redirect_uri
 * - Validates response_type
 * - Validates scope
 * - Validates PKCE (if present)
 */
interface VerifyAuthorizationRequestCommand : ServiceCommand<AuthorizationRequestData, VerifiedAuthorizationRequest> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.authorization.verify"
    }
}

// ============================================================================
// 3. CreateAuthorizationSessionCommand
// ============================================================================

/**
 * Create authorization session command
 *
 * Creates a session to track the authorization flow across multiple requests.
 * The session stores all request parameters and will be updated as the flow progresses.
 */
interface CreateAuthorizationSessionCommand : ServiceCommand<VerifiedAuthorizationRequest, AuthorizationSession> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.session.create"
    }
}

// ============================================================================
// 4. CreateAuthorizationCodeCommand
// ============================================================================

/**
 * Arguments for creating an authorization code
 */
data class CreateAuthorizationCodeArgs(
    val session: AuthorizationSession,
    val userId: String,
    val consent: ConsentDecision,
    val userClaims: Map<String, Any> = emptyMap()
)

/**
 * Create authorization code command
 *
 * Generates an authorization code after successful user authentication and consent.
 *
 * RFC 6749 Section 4.1.2: Authorization Response
 */
interface CreateAuthorizationCodeCommand : ServiceCommand<CreateAuthorizationCodeArgs, StringResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.authcode.create"
    }
}

// ============================================================================
// 5. CreateAuthorizationResponseCommand
// ============================================================================

/**
 * Arguments for creating an authorization response
 */
data class CreateAuthorizationResponseArgs(
    val code: String,
    val state: String? = null,
    val redirectUri: String,
    val responseMode: String = "query"
)

/**
 * Create authorization response command
 *
 * Creates the authorization response to redirect the user back to the client.
 *
 * RFC 6749 Section 4.1.2: Authorization Response
 */
interface CreateAuthorizationResponseCommand : ServiceCommand<CreateAuthorizationResponseArgs, AuthorizationResponseData> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.authorization.respond"
    }
}

// ============================================================================
// 6. CreateAuthorizationErrorResponseCommand
// ============================================================================

/**
 * Arguments for creating an authorization error response
 */
data class CreateAuthorizationErrorResponseArgs(
    val error: String,
    val errorDescription: String? = null,
    val errorUri: String? = null,
    val state: String? = null,
    val redirectUri: String
)

/**
 * Create authorization error response command
 *
 * Creates an error response to redirect the user back to the client.
 *
 * RFC 6749 Section 4.1.2.1: Error Response
 */
interface CreateAuthorizationErrorResponseCommand : ServiceCommand<CreateAuthorizationErrorResponseArgs, AuthorizationErrorResponseData> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.authorization.error"
    }
}

// ============================================================================
// Data Models
// ============================================================================

/**
 * Parsed authorization request data
 */
data class AuthorizationRequestData(
    /**
     * Client identifier
     */
    val clientId: String,

    /**
     * Redirect URI
     */
    val redirectUri: String,

    /**
     * Response type (code, token, id_token, etc.)
     */
    val responseType: List<ResponseType>,

    /**
     * Requested scope
     */
    val scope: String? = null,

    /**
     * State parameter (RECOMMENDED for CSRF protection)
     */
    val state: String? = null,

    /**
     * PKCE code challenge
     */
    val codeChallenge: String? = null,

    /**
     * PKCE code challenge method
     */
    val codeChallengeMethod: PkceMethod? = null,

    /**
     * DPoP JWK thumbprint (RFC 9449)
     */
    val dpopJkt: String? = null,

    /**
     * Response mode (query, fragment, form_post)
     */
    val responseMode: String? = null,

    /**
     * Nonce (for OpenID Connect)
     */
    val nonce: String? = null,

    /**
     * Display mode (page, popup, touch, wap)
     */
    val display: String? = null,

    /**
     * Prompt (none, login, consent, select_account)
     */
    val prompt: String? = null,

    /**
     * Max age (maximum authentication age)
     */
    val maxAge: Int? = null,

    /**
     * UI locales
     */
    val uiLocales: List<String>? = null,

    /**
     * ID token hint
     */
    val idTokenHint: String? = null,

    /**
     * Login hint
     */
    val loginHint: String? = null,

    /**
     * ACR values (authentication context class reference)
     */
    val acrValues: List<String>? = null,

    /**
     * Resource indicators (RFC 8707)
     */
    val resource: List<String>? = null,

    /**
     * Request URI (for JAR)
     */
    val requestUri: String? = null,

    /**
     * Request object (JAR)
     */
    val request: String? = null,

    /**
     * Additional parameters
     */
    val additionalParameters: Map<String, String> = emptyMap()
)

/**
 * Verified authorization request
 */
data class VerifiedAuthorizationRequest(
    /**
     * Original request data
     */
    val request: AuthorizationRequestData,

    /**
     * Client ID (validated)
     */
    val clientId: String,

    /**
     * Redirect URI (validated against client registration)
     */
    val redirectUri: String,

    /**
     * Granted scopes (may be subset of requested)
     */
    val grantedScopes: List<String>,

    /**
     * Whether PKCE is required for this client
     */
    val pkceRequired: Boolean,

    /**
     * Whether PAR is required for this client
     */
    val parRequired: Boolean
)

/**
 * Authorization response data
 */
data class AuthorizationResponseData(
    /**
     * Authorization code
     */
    val code: String,

    /**
     * State parameter (echoed from request)
     */
    val state: String?,

    /**
     * Redirect URI with parameters
     */
    val redirectUri: String,

    /**
     * Response mode used (query or fragment)
     */
    val responseMode: String = "query"
)

/**
 * Authorization error response data
 */
data class AuthorizationErrorResponseData(
    /**
     * Error code
     */
    val error: String,

    /**
     * Error description
     */
    val errorDescription: String? = null,

    /**
     * Error URI
     */
    val errorUri: String? = null,

    /**
     * State parameter (echoed from request)
     */
    val state: String? = null,

    /**
     * Redirect URI with error parameters
     */
    val redirectUri: String
)
