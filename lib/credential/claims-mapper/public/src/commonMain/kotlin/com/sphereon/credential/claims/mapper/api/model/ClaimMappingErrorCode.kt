package com.sphereon.credential.claims.mapper.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Error codes for claim mapping operations.
 */
@Serializable
enum class ClaimMappingErrorCode {
    /**
     * Configuration not found.
     */
    @SerialName("CONFIGURATION_NOT_FOUND")
    CONFIGURATION_NOT_FOUND,

    /**
     * Required credential not provided.
     */
    @SerialName("REQUIRED_CREDENTIAL_MISSING")
    REQUIRED_CREDENTIAL_MISSING,

    /**
     * No resolver available for the credential format.
     */
    @SerialName("UNSUPPORTED_FORMAT")
    UNSUPPORTED_FORMAT,

    /**
     * Claim extraction failed.
     */
    @SerialName("EXTRACTION_FAILED")
    EXTRACTION_FAILED,

    /**
     * Claim path not found in credential.
     */
    @SerialName("CLAIM_NOT_FOUND")
    CLAIM_NOT_FOUND,

    /**
     * Transformation failed.
     */
    @SerialName("TRANSFORMATION_FAILED")
    TRANSFORMATION_FAILED,

    /**
     * Store operation failed.
     */
    @SerialName("STORE_ERROR")
    STORE_ERROR,

    /**
     * Invalid configuration.
     */
    @SerialName("INVALID_CONFIGURATION")
    INVALID_CONFIGURATION,

    /**
     * Invalid or malformed request body.
     */
    @SerialName("INVALID_REQUEST_BODY")
    INVALID_REQUEST_BODY;

    companion object {
        /**
         * Find an error code by its string value.
         *
         * @param value The string value to search for
         * @return The matching [ClaimMappingErrorCode], or null if not found
         */
        fun fromValue(value: String): ClaimMappingErrorCode =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown ClaimMappingErrorCode: $value")
    }
}
