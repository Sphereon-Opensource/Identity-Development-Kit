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

package com.sphereon.oauth2.server.authorization.model

import com.sphereon.oauth2.common.model.OAuth2ResponseMode
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRouteDecision
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Authorization session tracking data
 *
 * Tracks the state of an authorization flow across multiple HTTP requests
 * (initial request → user authentication → user consent → authorization response)
 */
@Serializable
data class AuthorizationSession(
    /**
     * Unique session identifier
     */
    val sessionId: String,
    /**
     * Client ID making the authorization request
     */
    val clientId: String,
    /**
     * State parameter from the authorization request
     * RFC 6749 Section 4.1.1: RECOMMENDED for CSRF protection
     */
    val state: String? = null,
    /**
     * Requested response type(s)
     */
    val responseType: String,
    /**
     * Resolved response mode for this session. Used by the approval handler to pick between
     * query-param redirect, fragment redirect, and `form_post` HTML response. Populated by the
     * verifier (OIDC Core §3.1.2.1 default per response_type when not specified in the request).
     */
    val responseMode: OAuth2ResponseMode = OAuth2ResponseMode.QUERY,
    /**
     * Redirect URI from the authorization request
     */
    val redirectUri: String,
    /**
     * Requested scope
     */
    val scope: String? = null,
    /** RFC 8707 resource indicators bound to this authorization session. */
    val resource: List<String> = emptyList(),
    /** Exact AS-client default audience bound for this authorization flow. */
    val defaultAccessTokenAudience: String? = null,
    /**
     * PKCE code challenge (if present)
     */
    val codeChallenge: String? = null,
    /**
     * PKCE code challenge method (if present)
     */
    val codeChallengeMethod: String? = null,
    /**
     * DPoP JWK thumbprint (if present)
     */
    val dpopJkt: String? = null,
    /**
     * OpenID Connect nonce from authorization request
     * Propagated to ID token for replay protection
     */
    val nonce: String? = null,
    /**
     * Epoch seconds when user authenticated
     * Used for auth_time claim in ID token (OpenID Connect Core Section 2)
     */
    val authTime: Long? = null,
    /**
     * Current session status
     */
    val status: SessionStatus = SessionStatus.PENDING_AUTHENTICATION,
    /**
     * Authenticated user ID (populated after authentication)
     */
    val authenticatedUserId: String? = null,
    /**
     * User consent decision (populated after consent)
     */
    val consentDecision: ConsentDecision? = null,
    /**
     * When the session was created
     */
    val createdAt: Instant,
    /**
     * When the session expires
     * Sessions should be short-lived (typically 10-15 minutes)
     */
    val expiresAt: Instant,
    /**
     * Request URI from PAR (if using PAR)
     * RFC 9126
     */
    val requestUri: String? = null,
    /**
     * Requested `acr_values` from the authorization request (OIDC Core §3.1.2.1). The AS uses
     * this list at code-issuance time to populate the granted `acr` claim on the id_token
     * when the authenticator didn't surface a specific level — picking the first requested
     * value satisfies the OIDC SHOULD on `acr_values` echo.
     */
    val acrValues: List<String>? = null,
    /**
     * Requested OIDC `max_age` (seconds) from the original authorization request. Carried
     * through so the AS can enforce auth freshness at code-issuance time as a
     * belt-and-suspenders check on top of the existing prompt/session-eval handling
     * in [com.sphereon.oauth2.server.authorization.command.StandardAuthorizeRequestCommand].
     * Null means the request did not pin a max_age (no freshness gate).
     */
    val maxAge: Long? = null,
    /**
     * Additional session data
     */
    val additionalData: Map<String, @Contextual Any> = emptyMap(),
    /**
     * The application / login-surface id resolved from [clientId]; opaque to IDK.
     */
    val applicationId: String? = null,
    /** Authentication route selected from durable hosted-resource and binding state. */
    val authenticationRoute: AuthenticationRouteDecision? = null,
)

/**
 * Well-known keys used inside [AuthorizationSession.additionalData] /
 * `AuthorizationCodeData.additionalData` / `AccessTokenData.additionalData` for the OIDC
 * Core §5.5 `claims` request parameter. The values stored under these keys are
 * `List<String>` of claim names — `userinfo` is consumed by `GetUserInfoCommandImpl`,
 * `id_token` by `CreateIdTokenCommandImpl`. Carried across hops as map entries (not
 * promoted to typed fields) so the existing additionalData propagation path works
 * without touching every model in the chain.
 */
const val SESSION_KEY_OIDC_CLAIMS_USERINFO: String = "oidc.claims.userinfo"
const val SESSION_KEY_OIDC_CLAIMS_ID_TOKEN: String = "oidc.claims.id_token"

/**
 * Authorization session status
 */
enum class SessionStatus {
    /**
     * Awaiting user authentication
     */
    PENDING_AUTHENTICATION,

    /**
     * User authenticated, awaiting consent
     */
    PENDING_CONSENT,

    /**
     * User consent granted, ready to issue code/tokens
     */
    AUTHORIZED,

    /**
     * User denied authorization
     */
    DENIED,

    /**
     * Session expired
     */
    EXPIRED,

    /**
     * Session completed (code/tokens issued)
     */
    COMPLETED,
}
