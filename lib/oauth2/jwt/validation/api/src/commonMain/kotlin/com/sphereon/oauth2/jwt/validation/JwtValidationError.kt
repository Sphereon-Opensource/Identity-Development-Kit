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
 *
 */

package com.sphereon.oauth2.jwt.validation

import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.Serializable

/**
 * JWT validation error types for the zero-trust authentication pipeline.
 */
@Serializable
enum class JwtValidationErrorType {
    /** Token is missing from the request */
    MISSING_TOKEN,

    /** Token format is invalid (not a valid JWT structure) */
    INVALID_TOKEN_FORMAT,

    /** Token signature verification failed */
    SIGNATURE_INVALID,

    /** Token has expired */
    TOKEN_EXPIRED,

    /** Token is not yet valid (nbf claim in future) */
    TOKEN_NOT_YET_VALID,

    /** Token issuer is not trusted */
    UNTRUSTED_ISSUER,

    /** Token audience does not match expected audience */
    INVALID_AUDIENCE,

    /** Required claim is missing from the token */
    MISSING_REQUIRED_CLAIM,

    /** JWKS endpoint could not be reached */
    JWKS_UNAVAILABLE,

    /** Key with specified kid not found in JWKS */
    KEY_NOT_FOUND,

    /** Token algorithm is not allowed */
    ALGORITHM_NOT_ALLOWED,

    /** IdP configuration is invalid or missing */
    IDP_CONFIGURATION_ERROR,

    /** OIDC discovery failed */
    DISCOVERY_FAILED,

    /** Generic validation error */
    VALIDATION_ERROR
}

/**
 * Detailed JWT validation error with structured information.
 */
@Serializable
data class JwtValidationError(
    val type: JwtValidationErrorType,
    val message: String,
    val issuer: String? = null,
    val audience: String? = null,
    val claim: String? = null,
    val cause: String? = null
) {
    /**
     * Convert to standard IdkError for consistent error handling.
     */
    fun toIdkError(): IdkError = IdkError(
        code = "JWT_${type.name}",
        message = IdkError.Message(
            i18nKey = "com.sphereon.oauth2.jwt.validation.error.${type.name.lowercase().replace("_", "-")}",
            defaultMessage = message
        ),
        severity = when (type) {
            JwtValidationErrorType.TOKEN_EXPIRED,
            JwtValidationErrorType.TOKEN_NOT_YET_VALID -> IdkError.Severity.WARNING
            else -> IdkError.Severity.ERROR
        }
    )

    companion object {
        fun missingToken() = JwtValidationError(
            type = JwtValidationErrorType.MISSING_TOKEN,
            message = "Authorization token is required but was not provided"
        )

        fun invalidFormat(details: String) = JwtValidationError(
            type = JwtValidationErrorType.INVALID_TOKEN_FORMAT,
            message = "Token format is invalid: $details"
        )

        fun signatureInvalid(issuer: String?) = JwtValidationError(
            type = JwtValidationErrorType.SIGNATURE_INVALID,
            message = "Token signature verification failed",
            issuer = issuer
        )

        fun expired(expiresAt: Long) = JwtValidationError(
            type = JwtValidationErrorType.TOKEN_EXPIRED,
            message = "Token expired at $expiresAt"
        )

        fun notYetValid(notBefore: Long) = JwtValidationError(
            type = JwtValidationErrorType.TOKEN_NOT_YET_VALID,
            message = "Token is not valid until $notBefore"
        )

        fun untrustedIssuer(issuer: String, trustedIssuers: List<String>) = JwtValidationError(
            type = JwtValidationErrorType.UNTRUSTED_ISSUER,
            message = "Issuer '$issuer' is not in the list of trusted issuers",
            issuer = issuer
        )

        fun invalidAudience(tokenAudience: List<String>, expectedAudience: String) = JwtValidationError(
            type = JwtValidationErrorType.INVALID_AUDIENCE,
            message = "Token audience $tokenAudience does not contain expected audience '$expectedAudience'",
            audience = expectedAudience
        )

        fun missingClaim(claim: String) = JwtValidationError(
            type = JwtValidationErrorType.MISSING_REQUIRED_CLAIM,
            message = "Required claim '$claim' is missing from the token",
            claim = claim
        )

        fun jwksUnavailable(issuer: String, cause: String?) = JwtValidationError(
            type = JwtValidationErrorType.JWKS_UNAVAILABLE,
            message = "Unable to fetch JWKS for issuer '$issuer'",
            issuer = issuer,
            cause = cause
        )

        fun keyNotFound(kid: String, issuer: String?) = JwtValidationError(
            type = JwtValidationErrorType.KEY_NOT_FOUND,
            message = "Key with id '$kid' not found in JWKS",
            issuer = issuer
        )

        fun algorithmNotAllowed(algorithm: String, allowedAlgorithms: List<String>) = JwtValidationError(
            type = JwtValidationErrorType.ALGORITHM_NOT_ALLOWED,
            message = "Algorithm '$algorithm' is not in allowed list: $allowedAlgorithms"
        )

        fun idpConfigurationError(message: String) = JwtValidationError(
            type = JwtValidationErrorType.IDP_CONFIGURATION_ERROR,
            message = message
        )

        fun discoveryFailed(issuer: String, cause: String?) = JwtValidationError(
            type = JwtValidationErrorType.DISCOVERY_FAILED,
            message = "OIDC discovery failed for issuer '$issuer'",
            issuer = issuer,
            cause = cause
        )

        fun validationError(message: String, cause: String? = null) = JwtValidationError(
            type = JwtValidationErrorType.VALIDATION_ERROR,
            message = message,
            cause = cause
        )
    }
}
