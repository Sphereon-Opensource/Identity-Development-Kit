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

package com.sphereon.openid.oid4vc.common.vcdm

import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.compat.JsExportCompat

/** Typed validation failures raised while classifying or validating a VCDM document. */
@JsExportCompat
sealed interface VcdmError : IdkErrorType {
    override val exception: Throwable?
        get() = null

    override val causes: List<IdkErrorType>
        get() = emptyList()

    override val severity: IdkError.Severity
        get() = IdkError.Severity.ERROR

    override val category: ErrorCategory
        get() = ErrorCategory.VALIDATION

    data class UnsupportedVersion(
        val contexts: List<String>,
    ) : VcdmError {
        override val category: ErrorCategory = ErrorCategory.VALIDATION
        override val code: String = "VCDM_UNSUPPORTED_VERSION"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.openid.oid4vc.common.vcdm.error.unsupported-version",
                i18nParams = mapOf("contexts" to contexts),
                defaultMessage = "Unsupported VCDM version in ${contexts.joinToString()}",
            )
        override val meta: Map<String, Any?> = mapOf("contexts" to contexts)
    }

    data class AmbiguousVersion(
        val contexts: List<String>,
    ) : VcdmError {
        override val category: ErrorCategory = ErrorCategory.VALIDATION
        override val code: String = "VCDM_AMBIGUOUS_VERSION"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.openid.oid4vc.common.vcdm.error.ambiguous-version",
                i18nParams = mapOf("contexts" to contexts),
                defaultMessage = "Ambiguous VCDM version in ${contexts.joinToString()}",
            )
        override val meta: Map<String, Any?> = mapOf("contexts" to contexts)
    }

    data class MissingBaseContext(
        val contexts: List<String> = emptyList(),
    ) : VcdmError {
        override val category: ErrorCategory = ErrorCategory.VALIDATION
        override val code: String = "VCDM_MISSING_BASE_CONTEXT"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.openid.oid4vc.common.vcdm.error.missing-base-context",
                i18nParams = mapOf("contexts" to contexts),
                defaultMessage = "VCDM base context is missing",
            )
        override val meta: Map<String, Any?> = mapOf("contexts" to contexts)
    }

    data class ContradictoryDocumentShape(
        val reason: String,
    ) : VcdmError {
        override val category: ErrorCategory = ErrorCategory.VALIDATION
        override val code: String = "VCDM_CONTRADICTORY_DOCUMENT_SHAPE"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.openid.oid4vc.common.vcdm.error.contradictory-document-shape",
                i18nParams = mapOf("reason" to reason),
                defaultMessage = "Contradictory VCDM document shape: $reason",
            )
        override val meta: Map<String, Any?> = mapOf("reason" to reason)
    }

    data class UnsupportedDocumentKind(
        val types: List<String>,
    ) : VcdmError {
        override val category: ErrorCategory = ErrorCategory.VALIDATION
        override val code: String = "VCDM_UNSUPPORTED_DOCUMENT_KIND"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.openid.oid4vc.common.vcdm.error.unsupported-document-kind",
                i18nParams = mapOf("types" to types),
                defaultMessage = "Unsupported VCDM document type in ${types.joinToString()}",
            )
        override val meta: Map<String, Any?> = mapOf("types" to types)
    }

    data class InvalidProperty(
        val property: String,
        val reason: String,
    ) : VcdmError {
        override val category: ErrorCategory = ErrorCategory.VALIDATION
        override val code: String = "VCDM_INVALID_PROPERTY"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.openid.oid4vc.common.vcdm.error.invalid-property",
                i18nParams = mapOf("property" to property, "reason" to reason),
                defaultMessage = "Invalid VCDM property '$property': $reason",
            )
        override val meta: Map<String, Any?> = mapOf("property" to property, "reason" to reason)
    }

    data class InvalidJwtClaim(
        val claim: String,
        val reason: String,
    ) : VcdmError {
        override val category: ErrorCategory = ErrorCategory.VALIDATION
        override val code: String = "VCDM_INVALID_JWT_CLAIM"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.openid.oid4vc.common.vcdm.error.invalid-jwt-claim",
                i18nParams = mapOf("claim" to claim, "reason" to reason),
                defaultMessage = "Invalid VCDM JWT claim '$claim': $reason",
            )
        override val meta: Map<String, Any?> = mapOf("claim" to claim, "reason" to reason)
    }

    data class InconsistentJwtClaim(
        val claim: String,
        val property: String,
    ) : VcdmError {
        override val category: ErrorCategory = ErrorCategory.VALIDATION
        override val code: String = "VCDM_INCONSISTENT_JWT_CLAIM"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.openid.oid4vc.common.vcdm.error.inconsistent-jwt-claim",
                i18nParams = mapOf("claim" to claim, "property" to property),
                defaultMessage = "VCDM JWT claim '$claim' does not represent '$property'",
            )
        override val meta: Map<String, Any?> = mapOf("claim" to claim, "property" to property)
    }

    data class InvalidJoseHeader(
        val parameter: String,
        val reason: String,
    ) : VcdmError {
        override val category: ErrorCategory = ErrorCategory.VALIDATION
        override val code: String = "VCDM_INVALID_JOSE_HEADER"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.openid.oid4vc.common.vcdm.error.invalid-jose-header",
                i18nParams = mapOf("parameter" to parameter, "reason" to reason),
                defaultMessage = "Invalid VCDM JOSE header parameter '$parameter': $reason",
            )
        override val meta: Map<String, Any?> = mapOf("parameter" to parameter, "reason" to reason)
    }

    data class UnsupportedSecuringAlgorithm(
        val algorithm: String?,
    ) : VcdmError {
        override val category: ErrorCategory = ErrorCategory.VALIDATION
        override val code: String = "VCDM_UNSUPPORTED_SECURING_ALGORITHM"
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "com.sphereon.openid.oid4vc.common.vcdm.error.unsupported-securing-algorithm",
                i18nParams = mapOf("algorithm" to algorithm),
                defaultMessage = "Unsupported VCDM securing algorithm: ${algorithm ?: "missing"} (alg:none is never accepted)",
            )
        override val meta: Map<String, Any?> = mapOf("algorithm" to algorithm)
    }
}
