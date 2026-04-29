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

package com.sphereon.oauth2.common.error

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.validation.ValidationErrorDetail

/**
 * OAuth 2.0 error types
 */
sealed interface Oauth2Error : IdkErrorType {
    /**
     * Invalid request error (RFC 6749 Section 5.2)
     */
    data class InvalidRequest(
        val details: List<ValidationErrorDetail>,
        override val code: String = "invalid_request",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.invalid_request",
                defaultMessage = "Invalid request: ${details.joinToString { it.message }}",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : Oauth2Error

    /**
     * Invalid client error (RFC 6749 Section 5.2)
     */
    data class InvalidClient(
        val reason: String,
        override val code: String = "invalid_client",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.invalid_client",
                defaultMessage = reason,
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : Oauth2Error

    /**
     * Invalid grant error (RFC 6749 Section 5.2)
     */
    data class InvalidGrant(
        val reason: String,
        override val code: String = "invalid_grant",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.invalid_grant",
                defaultMessage = reason,
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : Oauth2Error

    /**
     * Unauthorized client error (RFC 6749 Section 5.2)
     */
    data class UnauthorizedClient(
        val reason: String,
        override val code: String = "unauthorized_client",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.unauthorized_client",
                defaultMessage = reason,
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : Oauth2Error

    /**
     * Unsupported grant type error (RFC 6749 Section 5.2)
     */
    data class UnsupportedGrantType(
        val grantType: String,
        override val code: String = "unsupported_grant_type",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.unsupported_grant_type",
                defaultMessage = "Unsupported grant type: $grantType",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : Oauth2Error

    /**
     * Invalid scope error (RFC 6749 Section 5.2)
     */
    data class InvalidScope(
        val scope: String,
        override val code: String = "invalid_scope",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.invalid_scope",
                defaultMessage = "Invalid scope: $scope",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : Oauth2Error

    /**
     * Server error (RFC 6749 Section 5.2)
     */
    data class ServerError(
        val reason: String,
        override val exception: Throwable? = null,
        override val code: String = "server_error",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.server_error",
                defaultMessage = reason,
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : Oauth2Error

    /**
     * Network error
     */
    data class NetworkError(
        override val exception: Throwable,
        override val code: String = "network_error",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.network_error",
                defaultMessage = "Network error: ${exception.message}",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : Oauth2Error

    /**
     * Invalid DPoP proof (RFC 9449)
     */
    data class InvalidDpopProof(
        val reason: String,
        override val code: String = "invalid_dpop_proof",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.invalid_dpop_proof",
                defaultMessage = reason,
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : Oauth2Error

    /**
     * DPoP nonce required (RFC 9449)
     */
    data class DpopNonceRequired(
        val nonce: String,
        override val code: String = "use_dpop_nonce",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.dpop_nonce_required",
                defaultMessage = "DPoP nonce required: $nonce",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("dpop_nonce" to nonce),
    ) : Oauth2Error

    /**
     * Generic validation failed error
     */
    data class ValidationFailed(
        val failureMessage: String,
        val validationErrors: List<String>,
        override val code: String = "validation_failed",
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : Oauth2Error {
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.validation_failed",
                defaultMessage = failureMessage,
            )
    }

    /**
     * Generic fetch failed error
     */
    data class FetchFailed(
        val failureMessage: String,
        val cause: Throwable? = null,
        override val code: String = "fetch_failed",
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = cause,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : Oauth2Error {
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.fetch_failed",
                defaultMessage = failureMessage,
            )
    }

    /**
     * OAuth 2.0 error response from authorization server (RFC 6749 Section 5.2)
     */
    data class ErrorResponse(
        val error: String,
        val errorDescription: String? = null,
        val errorUri: String? = null,
        override val code: String = error,
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> =
            mapOf(
                "error" to error,
                "error_description" to errorDescription,
                "error_uri" to errorUri,
            ),
    ) : Oauth2Error {
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.error_response",
                defaultMessage = errorDescription ?: error,
            )
    }

    /**
     * Invalid ID Token error (OpenID Connect Core 1.0)
     */
    data class InvalidIdToken(
        val reason: String? = null,
        override val exception: Throwable? = null,
        override val code: String = "invalid_id_token",
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : Oauth2Error {
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.invalid_id_token",
                defaultMessage = reason ?: "Invalid ID Token",
            )
    }

    /**
     * JAR creation failed error (RFC 9101)
     */
    data class JarCreationFailed(
        val failureMessage: String,
        val cause: Throwable? = null,
        override val code: String = "jar_creation_failed",
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = cause,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : Oauth2Error {
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.jar_creation_failed",
                defaultMessage = failureMessage,
            )
    }

    /**
     * Invalid target error (RFC 8693 Section 2.2.1)
     *
     * The requested resource or audience is invalid, unknown, or malformed.
     */
    data class InvalidTarget(
        val target: String,
        val reason: String,
        override val code: String = "invalid_target",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.invalid_target",
                defaultMessage = "Invalid target '$target': $reason",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("target" to target),
    ) : Oauth2Error

    /**
     * JAR parsing failed error (RFC 9101)
     */
    data class JarParsingFailed(
        val failureMessage: String,
        val cause: Throwable? = null,
        override val code: String = "jar_parsing_failed",
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = cause,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : Oauth2Error {
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.error.jar_parsing_failed",
                defaultMessage = failureMessage,
            )
    }
}

/**
 * DPoP-specific errors (RFC 9449)
 */
sealed interface DpopError : Oauth2Error {
    /**
     * DPoP proof generation failed
     */
    data class GenerationFailed(
        val reason: String,
        override val exception: Throwable? = null,
        override val code: String = "dpop_generation_failed",
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : DpopError {
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.dpop.generation_failed",
                defaultMessage = "DPoP proof generation failed: $reason",
            )
    }

    /**
     * DPoP proof verification failed
     */
    data class VerificationFailed(
        val reason: String,
        override val exception: Throwable? = null,
        override val code: String = "dpop_verification_failed",
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : DpopError {
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.dpop.verification_failed",
                defaultMessage = "DPoP proof verification failed: $reason",
            )
    }

    /**
     * Invalid DPoP proof format
     */
    data class InvalidFormat(
        val reason: String,
        override val code: String = "dpop_invalid_format",
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : DpopError {
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.dpop.invalid_format",
                defaultMessage = "Invalid DPoP proof format: $reason",
            )
    }

    /**
     * Missing required DPoP claim
     */
    data class MissingClaim(
        val claimName: String,
        override val code: String = "dpop_missing_claim",
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("claimName" to claimName),
    ) : DpopError {
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.dpop.missing_claim",
                defaultMessage = "Missing required DPoP claim: $claimName",
            )
    }

    /**
     * DPoP claim mismatch
     */
    data class ClaimMismatch(
        val claimName: String,
        val expected: String,
        val actual: String,
        override val code: String = "dpop_claim_mismatch",
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> =
            mapOf(
                "claimName" to claimName,
                "expected" to expected,
                "actual" to actual,
            ),
    ) : DpopError {
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.dpop.claim_mismatch",
                defaultMessage = "DPoP claim '$claimName' mismatch: expected '$expected' but got '$actual'",
            )
    }
}

/**
 * PKCE-specific errors
 */
sealed interface PkceError : IdkErrorType {
    data class GenerationFailed(
        val reason: String,
        override val code: String = "pkce_generation_failed",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.pkce.generation_failed",
                defaultMessage = reason,
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : PkceError

    data class VerificationFailed(
        val reason: String,
        override val code: String = "pkce_verification_failed",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.pkce.verification_failed",
                defaultMessage = reason,
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : PkceError

    data object NoMethodsAllowed : PkceError {
        override val code: String = "pkce_no_methods_allowed"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.pkce.no_methods_allowed",
                defaultMessage = "No PKCE methods allowed",
            )
        override val severity: IdkError.Severity = IdkError.Severity.ERROR
        override val exception: Throwable? = null
        override val causes: List<IdkErrorType> = emptyList()
        override val meta: Map<String, Any?> = emptyMap()
    }
}

/**
 * Token introspection-specific errors (RFC 7662)
 */
sealed interface IntrospectionError : Oauth2Error {
    /**
     * Introspection endpoint not configured in metadata
     */
    data class EndpointNotConfigured(
        override val code: String = "introspection_endpoint_not_configured",
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : IntrospectionError {
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.introspection.endpoint_not_configured",
                defaultMessage = "Missing required 'introspection_endpoint' in authorization server metadata",
            )
    }

    /**
     * Introspection request failed
     */
    data class RequestFailed(
        val reason: String,
        override val exception: Throwable? = null,
        override val code: String = "introspection_request_failed",
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : IntrospectionError {
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.introspection.request_failed",
                defaultMessage = "Token introspection request failed: $reason",
            )
    }

    /**
     * Introspection response validation failed
     */
    data class ResponseValidationFailed(
        val details: List<ValidationErrorDetail>,
        override val code: String = "introspection_response_validation_failed",
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : IntrospectionError {
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.introspection.response_validation_failed",
                defaultMessage = "Token introspection response validation failed: ${details.joinToString { it.message }}",
            )
    }
}

/**
 * Metadata-specific errors (RFC 8414)
 */
sealed interface MetadataError : Oauth2Error {
    /**
     * Metadata fetch failed (404 or network error)
     */
    data class FetchFailed(
        val url: String,
        val reason: String,
        override val exception: Throwable? = null,
        override val code: String = "metadata_fetch_failed",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.metadata.fetch_failed",
                defaultMessage = "Failed to fetch metadata from $url: $reason",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("url" to url),
    ) : MetadataError

    /**
     * Metadata validation failed
     */
    data class ValidationFailed(
        val url: String,
        val details: List<ValidationErrorDetail>,
        override val code: String = "metadata_validation_failed",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.metadata.validation_failed",
                defaultMessage = "Metadata validation failed for $url: ${details.joinToString { it.message }}",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("url" to url),
    ) : MetadataError

    /**
     * Issuer mismatch between requested and received
     */
    data class IssuerMismatch(
        val requestedIssuer: String,
        val receivedIssuer: String,
        val metadataUrl: String,
        override val code: String = "metadata_issuer_mismatch",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.metadata.issuer_mismatch",
                defaultMessage = "Issuer mismatch: requested '$requestedIssuer' but metadata contains '$receivedIssuer'",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> =
            mapOf(
                "requestedIssuer" to requestedIssuer,
                "receivedIssuer" to receivedIssuer,
                "metadataUrl" to metadataUrl,
            ),
    ) : MetadataError

    /**
     * Metadata not found (all well-known URLs returned 404)
     */
    data class NotFound(
        val issuer: String,
        val attemptedUrls: List<String>,
        override val code: String = "metadata_not_found",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.metadata.not_found",
                defaultMessage = "Metadata not found for issuer '$issuer'. Tried: ${attemptedUrls.joinToString()}",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> =
            mapOf(
                "issuer" to issuer,
                "attemptedUrls" to attemptedUrls,
            ),
    ) : MetadataError

    /**
     * Invalid metadata URL
     */
    data class InvalidUrl(
        val url: String,
        val reason: String,
        override val code: String = "metadata_invalid_url",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.metadata.invalid_url",
                defaultMessage = "Invalid metadata URL '$url': $reason",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("url" to url),
    ) : MetadataError
}
