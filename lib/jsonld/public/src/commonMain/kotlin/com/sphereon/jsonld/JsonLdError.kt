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

package com.sphereon.jsonld

import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType

/**
 * JSON-LD-specific typed errors. Implements [IdkErrorType] so callers can
 * surface them through `IdkResult<*, IdkError>` and recover the typed shape via
 * `IdkError.sourceAs<JsonLdError.X>()`.
 *
 * Variants map to W3C JSON-LD 1.1 Processing API error codes that Track A of
 * the IDK JSON-LD work actively produces (loader + context-shape validator +
 * JSON-Schema validator). Algorithm-specific codes (`invalid keyword`,
 * `processing mode conflict`, etc.) arrive in Track B alongside the operations
 * that raise them.
 */
sealed interface JsonLdError : IdkErrorType {
    /**
     * The loader could not retrieve a remote `@context` document.
     *
     * Maps to the W3C JSON-LD 1.1 `loading document failed` error code.
     */
    data class LoadingDocumentFailed(
        val iri: String,
        val reason: String,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
    ) : JsonLdError {
        override val code: String = "JSONLD_LOADING_DOCUMENT_FAILED"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.jsonld.error.loading-document-failed",
                i18nParams = mapOf("iri" to iri, "reason" to reason),
                defaultMessage = "Failed to load JSON-LD document <$iri>: $reason",
            )
        override val severity: IdkError.Severity = IdkError.Severity.ERROR
        override val category: ErrorCategory = ErrorCategory.UNAVAILABLE
        override val meta: Map<String, Any?> = mapOf("iri" to iri, "reason" to reason)
    }

    /**
     * A built-in context bundle was requested but is not registered.
     *
     * IDK-specific (no W3C equivalent); raised by
     * `BuiltInContextLinkedDataDocumentLoader` when the requested IRI is not
     * in its classpath manifest.
     */
    data class BuiltInContextNotFound(
        val iri: String,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
    ) : JsonLdError {
        override val code: String = "JSONLD_BUILT_IN_CONTEXT_NOT_FOUND"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.jsonld.error.built-in-context-not-found",
                i18nParams = mapOf("iri" to iri),
                defaultMessage = "Built-in JSON-LD context <$iri> is not registered",
            )
        override val severity: IdkError.Severity = IdkError.Severity.ERROR
        override val category: ErrorCategory = ErrorCategory.NOT_FOUND
        override val meta: Map<String, Any?> = mapOf("iri" to iri)
    }

    /**
     * The bytes returned for a built-in context did not match the pinned
     * SHA-256 digest, or a remote context did not match its configured pin.
     *
     * IDK-specific. Pin mismatch is a hard failure: an attacker who can serve
     * a different context body could change the meaning of every credential
     * verified against it.
     */
    data class IntegrityPinMismatch(
        val iri: String,
        val expectedSha256: String,
        val actualSha256: String,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
    ) : JsonLdError {
        override val code: String = "JSONLD_INTEGRITY_PIN_MISMATCH"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.jsonld.error.integrity-pin-mismatch",
                i18nParams =
                    mapOf(
                        "iri" to iri,
                        "expected" to expectedSha256,
                        "actual" to actualSha256,
                    ),
                defaultMessage =
                    "Integrity pin mismatch for <$iri>: expected sha256:$expectedSha256, got sha256:$actualSha256",
            )
        override val severity: IdkError.Severity = IdkError.Severity.FATAL
        override val category: ErrorCategory = ErrorCategory.VALIDATION
        override val meta: Map<String, Any?> =
            mapOf(
                "iri" to iri,
                "expected_sha256" to expectedSha256,
                "actual_sha256" to actualSha256,
            )
    }

    /**
     * An IRI string supplied to a JSON-LD operation could not be parsed per
     * RFC 3987.
     */
    data class InvalidIri(
        val rawValue: String,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
    ) : JsonLdError {
        override val code: String = "JSONLD_INVALID_IRI"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.jsonld.error.invalid-iri",
                i18nParams = mapOf("raw_value" to rawValue),
                defaultMessage = "Invalid IRI: '$rawValue'",
            )
        override val severity: IdkError.Severity = IdkError.Severity.ERROR
        override val category: ErrorCategory = ErrorCategory.VALIDATION
        override val meta: Map<String, Any?> = mapOf("raw_value" to rawValue)
    }

    /**
     * The `@context` value was syntactically invalid, e.g. neither an IRI, an
     * object, an array, nor `null`.
     *
     * Maps to the W3C JSON-LD 1.1 `invalid local context` error code.
     */
    data class InvalidLocalContext(
        val location: String,
        val reason: String,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
    ) : JsonLdError {
        override val code: String = "JSONLD_INVALID_LOCAL_CONTEXT"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.jsonld.error.invalid-local-context",
                i18nParams = mapOf("location" to location, "reason" to reason),
                defaultMessage = "Invalid local @context at $location: $reason",
            )
        override val severity: IdkError.Severity = IdkError.Severity.ERROR
        override val category: ErrorCategory = ErrorCategory.VALIDATION
        override val meta: Map<String, Any?> = mapOf("location" to location, "reason" to reason)
    }

    /**
     * A `@vocab` keyword appeared in a context where it is forbidden. UNTP
     * 0.7.0 VCP MUST-NOT permits `@vocab` in any UNTP credential's context
     * chain; this error is raised by the UNTP-aware context validator.
     *
     * Maps to the W3C JSON-LD 1.1 `invalid vocab mapping` error code with the
     * UNTP-specific reason captured in [reason].
     */
    data class InvalidVocabMapping(
        val location: String,
        val reason: String = "@vocab is forbidden by UNTP 0.7.0 Verifiable Credentials Profile",
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
    ) : JsonLdError {
        override val code: String = "JSONLD_INVALID_VOCAB_MAPPING"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.jsonld.error.invalid-vocab-mapping",
                i18nParams = mapOf("location" to location, "reason" to reason),
                defaultMessage = "Invalid @vocab mapping at $location: $reason",
            )
        override val severity: IdkError.Severity = IdkError.Severity.ERROR
        override val category: ErrorCategory = ErrorCategory.VALIDATION
        override val meta: Map<String, Any?> = mapOf("location" to location, "reason" to reason)
    }

    /**
     * A keyword (`@id`, `@type`, …) was redefined in a context. Reserved for
     * Track B context-processing; included here so Track A error consumers can
     * pattern-match on the full sealed hierarchy without recompilation when
     * Track B lands.
     */
    data class KeywordRedefinition(
        val keyword: String,
        val location: String,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
    ) : JsonLdError {
        override val code: String = "JSONLD_KEYWORD_REDEFINITION"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.jsonld.error.keyword-redefinition",
                i18nParams = mapOf("keyword" to keyword, "location" to location),
                defaultMessage = "Keyword '$keyword' redefined at $location",
            )
        override val severity: IdkError.Severity = IdkError.Severity.ERROR
        override val category: ErrorCategory = ErrorCategory.VALIDATION
        override val meta: Map<String, Any?> = mapOf("keyword" to keyword, "location" to location)
    }

    /**
     * No JSON Schema is registered for the requested credential type. Raised
     * by [JsonLdSchemaRegistry] consumers when the registry has no entry;
     * deployments may treat this as a soft pass for unknown types or a hard
     * failure depending on policy.
     */
    data class NoSchemaRegistered(
        val credentialType: String,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
    ) : JsonLdError {
        override val code: String = "JSONLD_NO_SCHEMA_REGISTERED"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.jsonld.error.no-schema-registered",
                i18nParams = mapOf("credential_type" to credentialType),
                defaultMessage = "No JSON Schema is registered for credential type '$credentialType'",
            )
        override val severity: IdkError.Severity = IdkError.Severity.WARNING
        override val category: ErrorCategory = ErrorCategory.NOT_FOUND
        override val meta: Map<String, Any?> = mapOf("credential_type" to credentialType)
    }

    /**
     * The credential payload failed JSON Schema validation against the schema
     * registered for its credential type.
     *
     * IDK-specific. Wraps a list of per-pointer schema violations.
     */
    data class JsonSchemaValidationFailed(
        val schemaUri: String,
        val violations: List<SchemaViolation>,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
    ) : JsonLdError {
        override val code: String = "JSONLD_JSON_SCHEMA_VALIDATION_FAILED"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.jsonld.error.json-schema-validation-failed",
                i18nParams = mapOf("schema" to schemaUri, "violation_count" to violations.size),
                defaultMessage =
                    "JSON Schema validation against <$schemaUri> failed with ${violations.size} violation(s)",
            )
        override val severity: IdkError.Severity = IdkError.Severity.ERROR
        override val category: ErrorCategory = ErrorCategory.VALIDATION
        override val meta: Map<String, Any?> =
            mapOf(
                "schema" to schemaUri,
                "violations" to violations.map { mapOf("pointer" to it.pointer, "message" to it.message) },
            )

        /** A single JSON Schema validation failure tied to a JSON Pointer location. */
        data class SchemaViolation(
            val pointer: String,
            val message: String,
        )
    }
}
