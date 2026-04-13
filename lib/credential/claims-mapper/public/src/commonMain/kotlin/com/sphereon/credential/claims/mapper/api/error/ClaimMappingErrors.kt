package com.sphereon.credential.claims.mapper.api.error

import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.claims.mapper.api.model.ClaimMappingErrorCode

/**
 * Error codes and factory methods for claim mapping operations.
 */
object ClaimMappingErrors {

    /**
     * Configuration not found error.
     */
    fun configurationNotFound(configId: String) = IdkError(
        code = ClaimMappingErrorCode.CONFIGURATION_NOT_FOUND.name,
        message = IdkError.Message(
            i18nKey = "com.sphereon.openid.claims.error.configuration-not-found",
            i18nParams = mapOf("configId" to configId),
            defaultMessage = "Claim mapping configuration not found: $configId"
        ),
        severity = IdkError.Severity.ERROR
    )

    /**
     * Required credential not provided error.
     */
    fun requiredCredentialMissing(credentialId: String) = IdkError(
        code = ClaimMappingErrorCode.REQUIRED_CREDENTIAL_MISSING.name,
        message = IdkError.Message(
            i18nKey = "com.sphereon.openid.claims.error.required-credential-missing",
            i18nParams = mapOf("credentialId" to credentialId),
            defaultMessage = "Required credential not provided: $credentialId"
        ),
        severity = IdkError.Severity.ERROR
    )

    /**
     * No resolver available for the credential format.
     */
    fun unsupportedCredentialFormat(format: String) = IdkError(
        code = ClaimMappingErrorCode.UNSUPPORTED_FORMAT.name,
        message = IdkError.Message(
            i18nKey = "com.sphereon.openid.claims.error.unsupported-format",
            i18nParams = mapOf("format" to format),
            defaultMessage = "No resolver available for credential format: $format"
        ),
        severity = IdkError.Severity.ERROR
    )

    /**
     * Claim extraction failed.
     */
    fun claimExtractionFailed(credentialId: String, reason: String, cause: Throwable? = null) = IdkError(
        code = ClaimMappingErrorCode.EXTRACTION_FAILED.name,
        message = IdkError.Message(
            i18nKey = "com.sphereon.openid.claims.error.extraction-failed",
            i18nParams = mapOf("credentialId" to credentialId, "reason" to reason),
            defaultMessage = "Failed to extract claims from credential '$credentialId': $reason"
        ),
        severity = IdkError.Severity.ERROR,
        exception = cause
    )

    /**
     * Claim path not found in credential.
     */
    fun claimNotFound(credentialId: String, claimPath: String) = IdkError(
        code = ClaimMappingErrorCode.CLAIM_NOT_FOUND.name,
        message = IdkError.Message(
            i18nKey = "com.sphereon.openid.claims.error.claim-not-found",
            i18nParams = mapOf("credentialId" to credentialId, "claimPath" to claimPath),
            defaultMessage = "Claim not found in credential '$credentialId' at path: $claimPath"
        ),
        severity = IdkError.Severity.WARNING
    )

    /**
     * Transformation failed.
     */
    fun transformationFailed(targetClaim: String, reason: String, cause: Throwable? = null) = IdkError(
        code = ClaimMappingErrorCode.TRANSFORMATION_FAILED.name,
        message = IdkError.Message(
            i18nKey = "com.sphereon.openid.claims.error.transformation-failed",
            i18nParams = mapOf("targetClaim" to targetClaim, "reason" to reason),
            defaultMessage = "Failed to transform claim '$targetClaim': $reason"
        ),
        severity = IdkError.Severity.ERROR,
        exception = cause
    )

    /**
     * Store operation failed.
     */
    fun storeOperationFailed(operation: String, reason: String, cause: Throwable? = null) = IdkError(
        code = ClaimMappingErrorCode.STORE_ERROR.name,
        message = IdkError.Message(
            i18nKey = "com.sphereon.openid.claims.error.store-operation-failed",
            i18nParams = mapOf("operation" to operation, "reason" to reason),
            defaultMessage = "Store operation '$operation' failed: $reason"
        ),
        severity = IdkError.Severity.ERROR,
        exception = cause
    )

    /**
     * Invalid configuration error.
     */
    fun invalidConfiguration(reason: String) = IdkError(
        code = ClaimMappingErrorCode.INVALID_CONFIGURATION.name,
        message = IdkError.Message(
            i18nKey = "com.sphereon.openid.claims.error.invalid-configuration",
            i18nParams = mapOf("reason" to reason),
            defaultMessage = "Invalid claim mapping configuration: $reason"
        ),
        severity = IdkError.Severity.ERROR
    )

    /**
     * Invalid or malformed request body.
     */
    fun invalidRequestBody(reason: String, cause: Throwable? = null) = IdkError(
        code = ClaimMappingErrorCode.INVALID_REQUEST_BODY.name,
        message = IdkError.Message(
            i18nKey = "com.sphereon.openid.claims.error.invalid-request-body",
            i18nParams = mapOf("reason" to reason),
            defaultMessage = "Invalid request body: $reason"
        ),
        severity = IdkError.Severity.ERROR,
        exception = cause
    )
}
