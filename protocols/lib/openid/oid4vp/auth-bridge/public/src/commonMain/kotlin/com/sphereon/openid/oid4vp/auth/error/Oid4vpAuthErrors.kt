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

package com.sphereon.openid.oid4vp.auth.error

import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthErrorCode

/**
 * Error codes and factory methods for OID4VP Authentication Bridge operations.
 */
object Oid4vpAuthErrors {
    fun queryIdRequired() =
        IdkError(
            code = Oid4vpAuthErrorCode.QUERY_ID_REQUIRED.name,
            message =
                IdkError.Message(
                    i18nKey = "com.sphereon.openid.oid4vp.auth.error.query-id-required",
                    defaultMessage = "Query ID is required: provide it in the request or set a default in configuration",
                ),
            severity = IdkError.Severity.ERROR,
        )

    fun sessionNotFound(sessionId: String) =
        IdkError(
            code = Oid4vpAuthErrorCode.SESSION_NOT_FOUND.name,
            message =
                IdkError.Message(
                    i18nKey = "com.sphereon.openid.oid4vp.auth.error.session-not-found",
                    i18nParams = mapOf("sessionId" to sessionId),
                    defaultMessage = "Session not found: $sessionId",
                ),
            severity = IdkError.Severity.ERROR,
        )

    fun sessionAlreadyCompleted(sessionId: String) =
        IdkError(
            code = Oid4vpAuthErrorCode.SESSION_ALREADY_COMPLETED.name,
            message =
                IdkError.Message(
                    i18nKey = "com.sphereon.openid.oid4vp.auth.error.session-already-completed",
                    i18nParams = mapOf("sessionId" to sessionId),
                    defaultMessage = "Session already completed: $sessionId",
                ),
            severity = IdkError.Severity.ERROR,
        )

    fun sessionNotVerified(sessionId: String) =
        IdkError(
            code = Oid4vpAuthErrorCode.SESSION_NOT_VERIFIED.name,
            message =
                IdkError.Message(
                    i18nKey = "com.sphereon.openid.oid4vp.auth.error.session-not-verified",
                    i18nParams = mapOf("sessionId" to sessionId),
                    defaultMessage = "Session is not verified yet: $sessionId",
                ),
            severity = IdkError.Severity.ERROR,
        )

    fun verifiedDataMissing(sessionId: String) =
        IdkError(
            code = Oid4vpAuthErrorCode.VERIFIED_DATA_MISSING.name,
            message =
                IdkError.Message(
                    i18nKey = "com.sphereon.openid.oid4vp.auth.error.verified-data-missing",
                    i18nParams = mapOf("sessionId" to sessionId),
                    defaultMessage = "Verified data not available for session: $sessionId",
                ),
            severity = IdkError.Severity.ERROR,
        )

    fun userIdentifierNotFound(claimPath: String) =
        IdkError(
            code = Oid4vpAuthErrorCode.USER_IDENTIFIER_NOT_FOUND.name,
            message =
                IdkError.Message(
                    i18nKey = "com.sphereon.openid.oid4vp.auth.error.user-identifier-not-found",
                    i18nParams = mapOf("claimPath" to claimPath),
                    defaultMessage = "Could not extract user identifier from credentials using path: $claimPath",
                ),
            severity = IdkError.Severity.ERROR,
        )

    fun userNotFound(reason: String) =
        IdkError(
            code = Oid4vpAuthErrorCode.USER_NOT_FOUND.name,
            message =
                IdkError.Message(
                    i18nKey = "com.sphereon.openid.oid4vp.auth.error.user-not-found",
                    i18nParams = mapOf("reason" to reason),
                    defaultMessage = "User not found: $reason",
                ),
            severity = IdkError.Severity.ERROR,
        )

    fun claimsMappingFailed(
        reason: String,
        cause: Throwable? = null,
    ) = IdkError(
        code = Oid4vpAuthErrorCode.CLAIMS_MAPPING_FAILED.name,
        message =
            IdkError.Message(
                i18nKey = "com.sphereon.openid.oid4vp.auth.error.claims-mapping-failed",
                i18nParams = mapOf("reason" to reason),
                defaultMessage = "Claims mapping failed: $reason",
            ),
        severity = IdkError.Severity.ERROR,
        exception = cause,
    )

    fun sessionStoreFailed(
        reason: String,
        cause: Throwable? = null,
    ) = IdkError(
        code = Oid4vpAuthErrorCode.SESSION_STORE_FAILED.name,
        message =
            IdkError.Message(
                i18nKey = "com.sphereon.openid.oid4vp.auth.error.session-store-failed",
                i18nParams = mapOf("reason" to reason),
                defaultMessage = "Session store operation failed: $reason",
            ),
        severity = IdkError.Severity.ERROR,
        exception = cause,
    )

    fun invalidRequestBody(
        reason: String,
        cause: Throwable? = null,
    ) = IdkError(
        code = Oid4vpAuthErrorCode.INVALID_REQUEST_BODY.name,
        message =
            IdkError.Message(
                i18nKey = "com.sphereon.openid.oid4vp.auth.error.invalid-request-body",
                i18nParams = mapOf("reason" to reason),
                defaultMessage = "Invalid request body: $reason",
            ),
        severity = IdkError.Severity.ERROR,
        exception = cause,
    )

    fun idvRequired(sessionId: String) =
        IdkError(
            code = Oid4vpAuthErrorCode.IDV_REQUIRED.name,
            message =
                IdkError.Message(
                    i18nKey = "com.sphereon.openid.oid4vp.auth.error.idv-required",
                    i18nParams = mapOf("sessionId" to sessionId),
                    defaultMessage = "Identity verification required for session: $sessionId",
                ),
            severity = IdkError.Severity.INFO,
        )

    fun sessionNotIdvRequired(sessionId: String) =
        IdkError(
            code = Oid4vpAuthErrorCode.SESSION_NOT_IDV_REQUIRED.name,
            message =
                IdkError.Message(
                    i18nKey = "com.sphereon.openid.oid4vp.auth.error.session-not-idv-required",
                    i18nParams = mapOf("sessionId" to sessionId),
                    defaultMessage = "Session is not in IDV_REQUIRED state: $sessionId",
                ),
            severity = IdkError.Severity.ERROR,
        )

    fun noReconciliationMapping(credentialType: String) =
        IdkError(
            code = Oid4vpAuthErrorCode.NO_RECONCILIATION_MAPPING.name,
            message =
                IdkError.Message(
                    i18nKey = "com.sphereon.openid.oid4vp.auth.error.no-reconciliation-mapping",
                    i18nParams = mapOf("credentialType" to credentialType),
                    defaultMessage = "No reconciliation provider configured for credential type: $credentialType",
                ),
            severity = IdkError.Severity.ERROR,
        )

    fun reconciliationFailed(
        reason: String,
        cause: Throwable? = null,
    ) = IdkError(
        code = Oid4vpAuthErrorCode.RECONCILIATION_FAILED.name,
        message =
            IdkError.Message(
                i18nKey = "com.sphereon.openid.oid4vp.auth.error.reconciliation-failed",
                i18nParams = mapOf("reason" to reason),
                defaultMessage = "Reconciliation failed: $reason",
            ),
        severity = IdkError.Severity.ERROR,
        exception = cause,
    )

    fun holderKeyExtractionFailed(reason: String) =
        IdkError(
            code = Oid4vpAuthErrorCode.HOLDER_KEY_EXTRACTION_FAILED.name,
            message =
                IdkError.Message(
                    i18nKey = "com.sphereon.openid.oid4vp.auth.error.holder-key-extraction-failed",
                    i18nParams = mapOf("reason" to reason),
                    defaultMessage =
                        "Failed to extract cryptographic holder key from VP token: $reason. " +
                            "When reconciliation is required, the VP must contain a holder binding via jwk, did, or x5c.",
                ),
            severity = IdkError.Severity.ERROR,
        )

    fun noReconciliationSession(sessionId: String) =
        IdkError(
            code = Oid4vpAuthErrorCode.NO_RECONCILIATION_SESSION.name,
            message =
                IdkError.Message(
                    i18nKey = "com.sphereon.openid.oid4vp.auth.error.no-reconciliation-session",
                    i18nParams = mapOf("sessionId" to sessionId),
                    defaultMessage = "No reconciliation session linked to OID4VP session: $sessionId",
                ),
            severity = IdkError.Severity.ERROR,
        )
}
