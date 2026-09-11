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

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Refresh token data stored by the authorization server
 *
 * RFC 6749 Section 1.5: Refresh tokens are credentials used to obtain access tokens.
 * Refresh tokens are issued to the client by the authorization server and are used
 * to obtain a new access token when the current access token becomes invalid or expires.
 */
@Serializable
data class RefreshTokenData(
    /**
     * The refresh token string
     */
    val refreshToken: String,
    /**
     * Client ID this token was issued to
     */
    val clientId: String,
    /**
     * Resource owner identifier (user ID)
     */
    val subject: String,
    /**
     * Granted scope
     * RFC 6749 Section 6: The requested scope MUST NOT include any scope not originally granted
     */
    val scope: String? = null,
    /** RFC 8707 resource indicators preserved through this refresh-token chain. */
    val resource: List<String> = emptyList(),
    /** Exact AS-client default audience preserved through this refresh-token chain. */
    val defaultAccessTokenAudience: String? = null,
    /** OID4VCI credential configurations authorized for the entire refresh-token chain. */
    val credentialConfigurationIds: List<String> = emptyList(),
    /** Opaque server-side OID4VCI offer/session correlation. */
    val oid4vciIssuerState: String? = null,
    /**
     * When the token was issued
     */
    val issuedAt: Instant,
    /**
     * When the token expires
     * Refresh tokens typically have longer lifetimes than access tokens (null = never expires)
     */
    val expiresAt: Instant? = null,
    /**
     * Whether this token has been revoked
     */
    val revoked: Boolean = false,
    /**
     * Whether this token has been used
     * Some deployments use one-time refresh tokens (rotate on use)
     */
    val used: Boolean = false,
    /** RFC 7638 thumbprint of the attested client-instance key bound to this token chain. */
    val clientInstanceKeyJkt: String? = null,
    /** Time at which rotation consumed this token; null until the first successful rotation. */
    val rotatedAt: Instant? = null,
    /** Successor returned for lost-response retries during the configured grace period. */
    val replacementRefreshToken: String? = null,
    /**
     * DPoP JWK thumbprint (if DPoP-bound)
     * RFC 9449: Refresh tokens can also be DPoP-bound
     */
    val dpopJkt: String? = null,
    /**
     * Epoch seconds of the original end-user authentication that produced this token chain.
     * Preserved across refresh-token rotation so OIDC Core 1.0 §12 reissue can populate the
     * refreshed id_token's `auth_time` claim with the original authentication time, never
     * the rotation time. Null for non-OIDC flows (client_credentials, token-exchange).
     */
    val authTime: Long? = null,
    /**
     * Authentication Context Class Reference (OpenID Connect Core 1.0 §2). Preserved from the
     * original AuthCode grant so refresh-time id_token reissue keeps the same `acr` claim
     * the wallet/RP saw at first issuance. Null when the original auth had no acr.
     */
    val acr: String? = null,
    /**
     * Authentication Methods References (OpenID Connect Core 1.0 §2). Preserved from the
     * original AuthCode grant so refresh-time id_token reissue keeps the same `amr` array
     * the RP saw at first issuance. Null when the original auth had no amr.
     */
    val amr: List<String>? = null,
    /**
     * OpenID Connect nonce captured at the original authorization request. Preserved so the
     * refresh-time id_token's `nonce` matches the original (OIDC Core 1.0 §3.1.3.7 step 11
     * binds nonce to the user agent, not to a single id_token issuance). Null when the
     * original /authorize had no nonce parameter.
     */
    val nonce: String? = null,
    /**
     * Cookie-keyed `oidc_login_sid` for the OIDC login session that produced this chain.
     * Used as the refreshed id_token's `sid` claim (OIDC Front-Channel and Back-Channel
     * Logout 1.0). Null for flows not bound to a login session (pre-authorized code,
     * machine-to-machine).
     */
    val loginSessionId: String? = null,
    /**
     * Additional metadata
     */
    val additionalData: Map<String, @Contextual Any> = emptyMap(),
    /** Typed storage-only JSON; default preserves compatibility with pre-federation rows. */
    val federationClaims: String? = null,
)
