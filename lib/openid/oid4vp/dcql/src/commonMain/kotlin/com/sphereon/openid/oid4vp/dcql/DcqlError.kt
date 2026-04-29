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

package com.sphereon.openid.oid4vp.dcql

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.validation.ValidationErrorDetail

/**
 * DCQL Error Types
 *
 * Errors that can occur during DCQL query parsing, validation, and matching.
 *
 * These errors follow the IDK error handling pattern using sealed interfaces
 * and IdkResult<V, E> for expected failures.
 *
 * @see com.sphereon.core.api.IdkResult
 */
sealed interface DcqlError : IdkErrorType {
    /**
     * Invalid DCQL Query Structure
     *
     * Thrown when the DCQL query JSON is malformed or violates structural constraints.
     *
     * Examples:
     * - Missing both `credentials` and `credential_sets`
     * - Empty arrays where non-empty is required
     * - Invalid JSON syntax
     * - Type mismatches in JSON
     */
    data class InvalidQuery(
        val reason: String,
        override val code: String = "DCQL_INVALID_QUERY",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "dcql.error.invalid_query",
                defaultMessage = reason,
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : DcqlError

    /**
     * Invalid Credential Format
     *
     * Thrown when a credential format identifier is invalid or unsupported.
     *
     * Valid formats (OpenID4VP 1.0):
     * - "dc+sd-jwt" (SD-JWT VC)
     * - "mso_mdoc" (ISO mDoc)
     * - "jwt_vc_json" (W3C VC JWT)
     * - "ldp_vc" (W3C VC with Linked Data Proofs)
     * - "jwt_vp" (JWT VP)
     * - "ldp_vp" (LDP VP)
     */
    data class InvalidFormat(
        val format: String,
        override val code: String = "DCQL_INVALID_FORMAT",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "dcql.error.invalid_format",
                defaultMessage = "Invalid credential format: $format",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : DcqlError

    /**
     * Invalid Claim Path
     *
     * Thrown when a claim path is malformed or violates constraints.
     *
     * Examples:
     * - Empty path array
     * - Path contains empty strings
     * - Path contains invalid characters for the credential format
     */
    data class InvalidClaimPath(
        val path: List<String>,
        override val code: String = "DCQL_INVALID_CLAIM_PATH",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "dcql.error.invalid_claim_path",
                defaultMessage = "Invalid claim path: ${path.joinToString(".")}",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : DcqlError

    /**
     * Invalid Format-Specific Metadata
     *
     * Thrown when format-specific metadata in the `meta` field is invalid.
     *
     * Examples:
     * - Invalid VCT URI in SD-JWT VC meta
     * - Invalid algorithm identifier
     * - Invalid doctype for mDoc
     * - Meta doesn't match the specified format
     */
    data class InvalidMeta(
        val format: String,
        val metaField: String,
        val reason: String,
        override val code: String = "DCQL_INVALID_META",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "dcql.error.invalid_meta",
                defaultMessage = "Invalid $metaField for format $format: $reason",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : DcqlError

    /**
     * Validation Error
     *
     * Thrown when DCQL validation fails (konform validation errors).
     *
     * This is a wrapper around konform validation errors, providing structured
     * error information with paths and messages.
     */
    data class ValidationError(
        val errors: List<ValidationErrorDetail>,
        override val code: String = "DCQL_VALIDATION_ERROR",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "dcql.error.validation_error",
                defaultMessage = "DCQL validation failed: ${errors.joinToString { it.message }}",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : DcqlError

    /**
     * Credential Matching Error
     *
     * Thrown when credential matching logic encounters an error.
     *
     * Examples:
     * - No credentials match the query constraints
     * - Ambiguous matches
     * - Required credential set not satisfied
     */
    data class MatchingError(
        val reason: String,
        val credentialId: String? = null,
        override val code: String = "DCQL_MATCHING_ERROR",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "dcql.error.matching_error",
                defaultMessage =
                    if (credentialId != null) {
                        "Matching error for credential '$credentialId': $reason"
                    } else {
                        reason
                    },
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : DcqlError

    /**
     * Serialization Error
     *
     * Thrown when DCQL serialization/deserialization fails.
     *
     * Examples:
     * - Invalid JSON syntax
     * - Missing required fields
     * - Type mismatches
     * - Unsupported polymorphic types
     */
    data class SerializationError(
        val reason: String,
        override val code: String = "DCQL_SERIALIZATION_ERROR",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "dcql.error.serialization_error",
                defaultMessage = reason,
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : DcqlError
}
