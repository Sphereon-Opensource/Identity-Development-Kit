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
 */

package com.sphereon.openid.oid4vp.auth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Error codes for OID4VP Authentication Bridge operations.
 */
@Serializable
enum class Oid4vpAuthErrorCode {
    /**
     * Query ID is required but was not provided and no default is configured.
     */
    @SerialName("QUERY_ID_REQUIRED")
    QUERY_ID_REQUIRED,

    /**
     * Session ID does not exist.
     */
    @SerialName("SESSION_NOT_FOUND")
    SESSION_NOT_FOUND,

    /**
     * Session has already been completed.
     */
    @SerialName("SESSION_ALREADY_COMPLETED")
    SESSION_ALREADY_COMPLETED,

    /**
     * Attempting to complete authentication before verification is done.
     */
    @SerialName("SESSION_NOT_VERIFIED")
    SESSION_NOT_VERIFIED,

    /**
     * Verified data is not available on a verified session.
     */
    @SerialName("VERIFIED_DATA_MISSING")
    VERIFIED_DATA_MISSING,

    /**
     * Could not extract a user identifier from the presented credentials.
     */
    @SerialName("USER_IDENTIFIER_NOT_FOUND")
    USER_IDENTIFIER_NOT_FOUND,

    /**
     * User not found and auto-creation is disabled.
     */
    @SerialName("USER_NOT_FOUND")
    USER_NOT_FOUND,

    /**
     * ClaimsMappingService returned an error.
     */
    @SerialName("CLAIMS_MAPPING_FAILED")
    CLAIMS_MAPPING_FAILED,

    /**
     * Session store operation failed.
     */
    @SerialName("SESSION_STORE_FAILED")
    SESSION_STORE_FAILED,

    /**
     * Invalid or malformed request body.
     */
    @SerialName("INVALID_REQUEST_BODY")
    INVALID_REQUEST_BODY,

    /**
     * Identity verification (reconciliation) is required before authentication can complete.
     */
    @SerialName("IDV_REQUIRED")
    IDV_REQUIRED,

    /**
     * Session is not in IDV_REQUIRED state when attempting IDV operations.
     */
    @SerialName("SESSION_NOT_IDV_REQUIRED")
    SESSION_NOT_IDV_REQUIRED,

    /**
     * No reconciliation provider mapping found for the credential type.
     */
    @SerialName("NO_RECONCILIATION_MAPPING")
    NO_RECONCILIATION_MAPPING,

    /**
     * Reconciliation operation failed.
     */
    @SerialName("RECONCILIATION_FAILED")
    RECONCILIATION_FAILED,

    /**
     * No reconciliation session linked to the OID4VP session.
     */
    @SerialName("NO_RECONCILIATION_SESSION")
    NO_RECONCILIATION_SESSION,

    /**
     * Failed to extract a cryptographic holder key from the VP token.
     * When reconciliation is required, a cryptographic holder binding (jwk, did, or x5c) is mandatory.
     */
    @SerialName("HOLDER_KEY_EXTRACTION_FAILED")
    HOLDER_KEY_EXTRACTION_FAILED;

    companion object {
        /**
         * Find an error code by its string value.
         *
         * @param value The string value to search for
         * @return The matching [Oid4vpAuthErrorCode]
         * @throws IllegalArgumentException if value is not found
         */
        fun fromValue(value: String): Oid4vpAuthErrorCode =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown Oid4vpAuthErrorCode: $value")
    }
}
