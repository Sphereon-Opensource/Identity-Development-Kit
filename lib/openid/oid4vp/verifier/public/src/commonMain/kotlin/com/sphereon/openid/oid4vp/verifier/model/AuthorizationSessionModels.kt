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

package com.sphereon.openid.oid4vp.verifier.model

import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.verifier.ParsedAuthorizationResponse
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

/**
 * Authorization session status aligned with Universal OID4VP.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthorizationSessionStatus", exact = true)
@JsExportCompat
@Serializable
enum class AuthorizationSessionStatus {
    AUTHORIZATION_REQUEST_CREATED,
    AUTHORIZATION_REQUEST_RETRIEVED,
    AUTHORIZATION_RESPONSE_RECEIVED,
    AUTHORIZATION_RESPONSE_VERIFIED,
    ERROR
}

/**
 * Callback configuration for status updates (webhook).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthorizationSessionCallbackConfig", exact = true)
@JsExportCompat
data class AuthorizationSessionCallbackConfig(
    val url: String,
    /**
     * If empty, all status transitions may be emitted.
     */
    val statuses: List<AuthorizationSessionStatus> = emptyList()
)

/**
 * Additional error information for a failed session.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthorizationSessionError", exact = true)
@JsExportCompat
data class AuthorizationSessionError(
    val code: String,
    val message: String
)

/**
 * Inputs for creating an authorization session.
 *
 * This is intentionally minimal in Phase 1; it will be extended in later phases
 * (configuration stores, request_uri handling, verified data, etc).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthorizationSessionCreateArgs", exact = true)
@JsExportCompat
data class AuthorizationSessionCreateArgs(
    /**
     * Optional reference to a pre-configured query (configuration mode).
     */
    val queryId: String? = null,
    /**
     * Inline DCQL query (direct mode).
     */
    val dcqlQuery: DcqlQuery? = null,
    val clientId: String,
    val responseMode: ResponseMode = ResponseMode.DIRECT_POST,
    val responseUri: String? = null,
    val redirectUri: String? = null,
    val nonce: String,
    val state: String? = null,
    val callback: AuthorizationSessionCallbackConfig? = null
)

/**
 * Stored authorization session state.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthorizationSession", exact = true)
@JsExportCompat
data class AuthorizationSession(
    val sessionId: String,
    /**
     * Business key / correlation id.
     */
    val correlationId: String,
    val queryId: String? = null,
    val dcqlQuery: DcqlQuery,
    val authorizationRequest: AuthorizationRequest,
    val status: AuthorizationSessionStatus,
    val error: AuthorizationSessionError? = null,
    val parsedResponse: ParsedAuthorizationResponse? = null,
    val validationResult: ValidationResult? = null,
    val callback: AuthorizationSessionCallbackConfig? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long
)
