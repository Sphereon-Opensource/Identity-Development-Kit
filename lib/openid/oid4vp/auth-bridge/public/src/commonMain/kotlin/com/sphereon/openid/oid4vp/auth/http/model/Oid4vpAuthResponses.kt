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

package com.sphereon.openid.oid4vp.auth.http.model

import com.sphereon.openid.oid4vp.auth.bridge.CreateSessionResult
import com.sphereon.openid.oid4vp.auth.model.IdvRequirementReason
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthErrorCode
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthResult
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSession
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSessionStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Response from creating a new OID4VP authentication session.
 *
 * POST /auth/oid4vp/sessions
 *
 * @property sessionId Internal session ID for this auth bridge session.
 * @property qrCodeDataUri QR code as data URI (data:image/png;base64,...).
 * @property requestUri Deep-link URI for wallet initiation (e.g., openid4vp://...).
 * @property statusUri Status polling endpoint relative path.
 * @property qrPageUri QR code page endpoint (HTML page with polling) relative path.
 * @property expiresAt Session expiration timestamp (Unix milliseconds).
 */
@Serializable
data class CreateOid4vpAuthSessionResponse(
    val sessionId: String,
    val correlationId: String? = null,
    val status: Oid4vpAuthSessionStatus,
    val qrCodeDataUri: String? = null,
    val requestUri: String? = null,
    val statusUri: String,
    val qrPageUri: String,
    val expiresAt: Long,
) {
    companion object {
        /**
         * Creates a response from a [CreateSessionResult] domain model.
         */
        fun from(result: CreateSessionResult): CreateOid4vpAuthSessionResponse =
            CreateOid4vpAuthSessionResponse(
                sessionId = result.session.sessionId,
                correlationId = result.session.correlationId,
                status = result.session.status,
                qrCodeDataUri = result.qrCodeDataUri,
                requestUri = result.requestUri,
                statusUri = result.statusUri,
                qrPageUri = result.qrPageUri,
                expiresAt = result.session.expiresAt.toEpochMilliseconds(),
            )
    }
}

/**
 * Response from polling session status.
 *
 * GET /auth/oid4vp/sessions/{sessionId}/status
 *
 * @property sessionId Session ID.
 * @property status Current session status.
 * @property mappedClaims Merged credential claims when status is VERIFIED.
 *   Contains all disclosed claims from verified credentials, flattened into a single map.
 *   Compatible with standard OIDC claim names (sub, email, name, etc.).
 * @property errorMessage Error message if status is ERROR.
 * @property idvRequirementReason Typed reason for identity verification (when status is IDV_REQUIRED).
 * @property idvMessage Message explaining why identity verification is required (when status is IDV_REQUIRED).
 */
@Serializable
data class Oid4vpAuthStatusResponse(
    val sessionId: String,
    val correlationId: String? = null,
    val status: Oid4vpAuthSessionStatus,
    val mappedClaims: Map<String, JsonElement>? = null,
    val errorMessage: String? = null,
    val errorCode: String? = null,
    val idvRequirementReason: IdvRequirementReason? = null,
    val idvMessage: String? = null,
    val expiresAt: Long? = null,
) {
    companion object {
        /**
         * Creates a response from an [Oid4vpAuthSession] domain model.
         *
         * @param session The auth session
         * @param mappedClaims Optional pre-mapped claims (e.g., from [DcqlClaimsMappingAdapter]).
         *   When provided, these are used instead of raw credential claims.
         *   When null and session has verified data, falls back to raw credential claims.
         */
        fun from(
            session: Oid4vpAuthSession,
            mappedClaims: Map<String, JsonElement>? = null,
        ): Oid4vpAuthStatusResponse {
            val claims =
                mappedClaims ?: session.verifiedData
                    ?.credentials
                    ?.flatMap { it.claims.entries }
                    ?.associate { it.key to it.value }

            return Oid4vpAuthStatusResponse(
                sessionId = session.sessionId,
                correlationId = session.correlationId,
                status = session.status,
                mappedClaims = claims,
                errorMessage = session.errorMessage,
                idvRequirementReason = session.idvRequirementReason,
                idvMessage = session.idvMessage,
                expiresAt = session.expiresAt.toEpochMilliseconds(),
            )
        }
    }
}

/**
 * Response from completing authentication.
 *
 * POST /auth/oid4vp/sessions/{sessionId}/complete
 *
 * @property userId Resolved user ID.
 * @property jwtClaims JWT claims string (if JWT generation is enabled).
 * @property claims Raw OIDC claims from credential mapping.
 * @property isNewUser Whether the user was newly created.
 * @property authenticatedAt Authentication timestamp (Unix milliseconds).
 * @property acr Authentication Context Class Reference.
 * @property amr Authentication Methods References.
 */
@Serializable
data class CompleteOid4vpAuthResponse(
    val userId: String,
    val jwtClaims: String? = null,
    val claims: Map<String, JsonElement>,
    val isNewUser: Boolean,
    val authenticatedAt: Long,
    val acr: String = "urn:sphereon:oid4vp:vp",
    val amr: List<String> = listOf("vp"),
) {
    companion object {
        /**
         * Creates a response from an [Oid4vpAuthResult] domain model.
         */
        fun from(result: Oid4vpAuthResult): CompleteOid4vpAuthResponse =
            CompleteOid4vpAuthResponse(
                userId = result.userId,
                jwtClaims = result.jwtClaims,
                claims = result.claims,
                isNewUser = result.isNewUser,
                authenticatedAt = result.authenticatedAt.toEpochMilliseconds(),
                acr = result.acr,
                amr = result.amr,
            )
    }
}

/**
 * HTTP error response for OID4VP Auth Bridge operations.
 *
 * @property error Error code.
 * @property errorDescription Human-readable error description.
 * @property errorDetails Additional error details.
 */
@Serializable
data class Oid4vpAuthErrorResponse(
    val error: Oid4vpAuthErrorCode,
    val errorDescription: String,
    val errorDetails: String? = null,
)
