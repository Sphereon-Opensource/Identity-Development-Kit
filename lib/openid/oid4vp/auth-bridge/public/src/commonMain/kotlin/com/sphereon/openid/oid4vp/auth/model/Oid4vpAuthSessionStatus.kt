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

package com.sphereon.openid.oid4vp.auth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Status of an OID4VP authentication session.
 *
 * The session progresses through these states:
 * 1. PENDING - Authorization request created, waiting for wallet to scan QR
 * 2. INTERACTION_STARTED - Wallet has retrieved the authorization request (scanned QR)
 * 3. VERIFIED - Credentials verified by Universal OID4VP
 * 4. IDV_REQUIRED - Credentials verified but holder identity not found, reconciliation needed
 * 5. COMPLETED - User resolved, claims mapped, authentication complete
 * 6. EXPIRED - Session TTL exceeded
 * 7. ERROR - An error occurred during the flow
 */
@Serializable
enum class Oid4vpAuthSessionStatus {
    /**
     * Authorization request created, waiting for wallet to scan QR code.
     */
    @SerialName("PENDING")
    PENDING,

    /**
     * Wallet has retrieved the authorization request via request_uri.
     * Waiting for wallet to submit verifiable presentation.
     */
    @SerialName("INTERACTION_STARTED")
    INTERACTION_STARTED,

    /**
     * Credentials have been verified by Universal OID4VP.
     * Ready for user resolution and claims mapping.
     */
    @SerialName("VERIFIED")
    VERIFIED,

    /**
     * Credentials verified but holder identity not found in the system.
     * Identity verification (reconciliation) via an external OIDC provider is required
     * to link the wallet holder to an institutional identity.
     */
    @SerialName("IDV_REQUIRED")
    IDV_REQUIRED,

    /**
     * Authentication completed successfully.
     * User has been resolved/created and claims have been mapped.
     */
    @SerialName("COMPLETED")
    COMPLETED,

    /**
     * Session has expired (TTL exceeded).
     */
    @SerialName("EXPIRED")
    EXPIRED,

    /**
     * An error occurred during the authentication flow.
     */
    @SerialName("ERROR")
    ERROR,

    ;

    companion object {
        /**
         * Find a status by its string value.
         *
         * @param value The string value to search for
         * @return The matching [Oid4vpAuthSessionStatus]
         * @throws IllegalArgumentException if value is not found
         */
        fun fromValue(value: String): Oid4vpAuthSessionStatus =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown Oid4vpAuthSessionStatus: $value")
    }
}
