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

package com.sphereon.oauth2.server.authorization.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual

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
     * Redirect URI from the authorization request
     */
    val redirectUri: String,

    /**
     * Requested scope
     */
    val scope: String? = null,

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
     * Additional session data
     */
    val additionalData: Map<String, @Contextual Any> = emptyMap()
)

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
    COMPLETED
}
