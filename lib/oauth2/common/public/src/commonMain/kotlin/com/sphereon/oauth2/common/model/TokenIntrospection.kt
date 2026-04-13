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

package com.sphereon.oauth2.common.model

import com.sphereon.crypto.core.jose.Jwk
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * JWT Confirmation (cnf) claim for proof-of-possession.
 * RFC 7800 Section 3.1 - Proof-of-Possession Key Semantics for JWTs
 *
 * Used in token introspection responses to indicate key binding.
 */
@Serializable
data class JwtConfirmation(
    /**
     * JWK public key to which the access token is bound.
     * RFC 7800 Section 3.2
     */
    val jwk: Jwk? = null,
    /**
     * JWK thumbprint (SHA-256 hash) of the DPoP public key.
     * RFC 9449 Section 6 - DPoP Access Token Binding
     */
    val jkt: String? = null,
)

/**
 * Token introspection request as defined in RFC 7662 Section 2.1.
 *
 * The introspection endpoint is used to query the authorization server
 * about the active state and metadata of a token.
 *
 * @property token The token to introspect (REQUIRED).
 * @property tokenTypeHint Optional hint about the type of token being introspected
 *                         (e.g., "access_token", "refresh_token").
 * @property additionalParameters Additional parameters for extensions.
 */
@Serializable
data class TokenIntrospectionRequest(
    val token: String,
    @SerialName("token_type_hint") val tokenTypeHint: String? = null,
    val additionalParameters: Map<String, String> = emptyMap(),
)

/**
 * Token introspection response as defined in RFC 7662 Section 2.2.
 *
 * The introspection endpoint returns information about the token,
 * including whether it is active and its associated metadata.
 *
 * @property active REQUIRED. Boolean indicator of whether the token is currently active.
 * @property scope OAuth 2.0 scope values for the token.
 * @property clientId Client identifier for the OAuth 2.0 client that requested the token.
 * @property username Human-readable identifier for the resource owner who authorized the token.
 * @property tokenType Type of the token (e.g., "Bearer", "DPoP").
 * @property exp Expiration timestamp (seconds since Unix epoch).
 * @property iat Issued-at timestamp (seconds since Unix epoch).
 * @property nbf Not-before timestamp (seconds since Unix epoch).
 * @property sub Subject of the token (usually a machine-readable identifier).
 * @property aud Audience(s) for the token (single string or array).
 * @property iss Issuer of the token.
 * @property jti JWT ID - unique identifier for the token.
 * @property cnf Confirmation claim for proof-of-possession (RFC 7800).
 * @property additionalClaims Additional claims for extensions.
 */
@Serializable
data class TokenIntrospectionResponse(
    val active: Boolean,
    val scope: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    val username: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    // Timestamps (seconds since Unix epoch)
    val exp: Long? = null,
    val iat: Long? = null,
    val nbf: Long? = null,
    // Standard JWT claims
    val sub: String? = null,
    @Serializable(with = AudienceSerializer::class)
    val aud: List<String>? = null, // Can be single string or array in JSON
    val iss: String? = null,
    val jti: String? = null,
    // Proof-of-possession confirmation
    val cnf: JwtConfirmation? = null,
    // Extension support - manually populated, not auto-captured from unknown JSON fields
    // NOTE: To auto-capture unknown fields, would need custom serializer like IdTokenPayload
    // See: https://github.com/Kotlin/kotlinx.serialization/issues/1978
    val additionalClaims: Map<String, JsonElement> = emptyMap(),
)
