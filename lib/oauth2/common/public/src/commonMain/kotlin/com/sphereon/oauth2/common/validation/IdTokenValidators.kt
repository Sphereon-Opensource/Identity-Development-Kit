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

package com.sphereon.oauth2.common.validation

import com.sphereon.oauth2.common.model.IdTokenPayload
import io.konform.validation.Validation
import io.konform.validation.constraints.minLength
import kotlin.time.Clock

/**
 * Validates ID Token payload structure (OpenID Connect Core 1.0)
 *
 * Validates required claims and basic structure, but does NOT validate:
 * - JWT signature (must be done separately with JWT service)
 * - Audience matching (must be checked against expected client_id)
 * - Issuer matching (must be checked against expected issuer)
 * - Nonce matching (must be checked if nonce was sent)
 * - Time-based claims with custom logic (exp, iat, auth_time)
 *
 * Use IdTokenValidator commands for full validation including these checks.
 */
val validateIdTokenPayload =
    Validation<IdTokenPayload> {
        // Required claims (OpenID Connect Core Section 2)
        IdTokenPayload::iss {
            minLength(1) hint "iss (issuer) is required and cannot be empty"
        }

        IdTokenPayload::sub {
            minLength(1) hint "sub (subject) is required and cannot be empty"
        }

        // aud is validated separately because it can be string or array
        // exp and iat are validated with custom time logic
    }

/**
 * Validates ID Token expiration
 *
 * @param exp Expiration time (Unix timestamp seconds)
 * @param clockSkewSeconds Clock skew tolerance in seconds (default: 60)
 * @return true if token is not expired (accounting for clock skew)
 */
fun isIdTokenExpired(
    exp: Long,
    clockSkewSeconds: Long = 60,
): Boolean {
    val now = Clock.System.now().epochSeconds
    return now > (exp + clockSkewSeconds)
}

/**
 * Validates ID Token issued-at time is not in the future
 *
 * @param iat Issued-at time (Unix timestamp seconds)
 * @param clockSkewSeconds Clock skew tolerance in seconds (default: 60)
 * @return true if iat is in the past (accounting for clock skew)
 */
fun isIdTokenIssuedAtValid(
    iat: Long,
    clockSkewSeconds: Long = 60,
): Boolean {
    val now = Clock.System.now().epochSeconds
    return iat <= (now + clockSkewSeconds)
}

/**
 * Validates authentication age against max_age requirement
 *
 * OpenID Connect Core Section 3.1.2.1:
 * "The max_age request parameter specifies the maximum elapsed time in seconds since the last time the End-User was actively authenticated"
 *
 * @param authTime Authentication time (Unix timestamp seconds)
 * @param maxAge Maximum allowed age in seconds
 * @param clockSkewSeconds Clock skew tolerance in seconds (default: 60)
 * @return true if authentication is not too old
 */
fun isAuthenticationFresh(
    authTime: Long,
    maxAge: Long,
    clockSkewSeconds: Long = 60,
): Boolean {
    val now = Clock.System.now().epochSeconds
    val authAge = now - authTime
    return authAge <= (maxAge + clockSkewSeconds)
}

/**
 * Validates audience claim contains expected value
 *
 * OpenID Connect Core Section 2:
 * "REQUIRED. Audience(s) that this ID Token is intended for. It MUST contain the OAuth 2.0 client_id of the Relying Party as an audience value"
 *
 * @param aud The aud claim value (list of audience strings)
 * @param expectedAudience The expected audience (usually client_id)
 * @return true if aud contains the expected audience
 */
fun validateAudience(
    aud: List<String>,
    expectedAudience: String,
): Boolean = aud.contains(expectedAudience)

/**
 * Validates at_hash (Access Token hash)
 *
 * OpenID Connect Core Section 3.1.3.3:
 * "Access Token hash value. Its value is the base64url encoding of the left-most half of the hash of the octets of the ASCII representation of the access_token value"
 *
 * Algorithm:
 * 1. Hash the access token using the hash algorithm specified in the ID Token's alg header
 * 2. Take the left-most half of the hash
 * 3. Base64url encode it
 * 4. Compare with at_hash claim
 *
 * Note: Hash algorithm must match the signing algorithm:
 * - RS256/ES256/PS256 → SHA-256
 * - RS384/ES384/PS384 → SHA-384
 * - RS512/ES512/PS512 → SHA-512
 *
 * NOTE: This function is provided as a validation rule reference only.
 * For complete ID Token validation including at_hash and c_hash, use ValidateIdTokenCommand
 * which has access to the crypto library hash functions and integrates with JWT verification.
 *
 * @param accessToken The access token to validate
 * @param atHash The at_hash claim from ID Token
 * @param algorithm The signing algorithm from ID Token (e.g., "RS256")
 * @return true if at_hash matches
 */
fun validateAtHash(
    accessToken: String,
    atHash: String,
    algorithm: String,
): Boolean {
    // Implementation is in ValidateIdTokenCommand which has crypto library access
    throw UnsupportedOperationException("Use ValidateIdTokenCommand for complete ID Token validation including at_hash")
}

/**
 * Validates c_hash (Authorization Code hash)
 *
 * OpenID Connect Core Section 3.3.2.11:
 * "Code hash value. Its value is the base64url encoding of the left-most half of the hash of the octets of the ASCII representation of the code value"
 *
 * Same algorithm as at_hash but for authorization code.
 *
 * NOTE: This function is provided as a validation rule reference only.
 * For complete ID Token validation including at_hash and c_hash, use ValidateIdTokenCommand
 * which has access to the crypto library hash functions and integrates with JWT verification.
 *
 * @param code The authorization code to validate
 * @param cHash The c_hash claim from ID Token
 * @param algorithm The signing algorithm from ID Token (e.g., "RS256")
 * @return true if c_hash matches
 */
fun validateCHash(
    code: String,
    cHash: String,
    algorithm: String,
): Boolean {
    // Implementation is in ValidateIdTokenCommand which has crypto library access
    throw UnsupportedOperationException("Use ValidateIdTokenCommand for complete ID Token validation including c_hash")
}
