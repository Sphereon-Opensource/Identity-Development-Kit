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

package com.sphereon.openid.oid4vci.issuer.store

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@JsExportCompat
@Serializable
data class IssuanceSession(
    val sessionId: String,
    val issuerId: String,
    val credentialConfigurationIds: List<String>,
    val issuerState: String? = null,
    val authorizationContextRef: String? = null,
    val status: IssuanceSessionStatus,
    val subject: String? = null,
    val clientId: String? = null,
    val credentialIdentifiers: List<String> = emptyList(),
    @JsExportIgnoreCompat
    val preSeededAttributes: Map<String, JsonElement>? = null,
    @JsExportIgnoreCompat
    val accumulatedAttributes: Map<String, JsonElement>? = null,
    /**
     * Opaque usage-token bound to this issuance (set when the offer was minted
     * from an invitation / redemption flow). Carried end-to-end so
     * post-issuance hooks can correlate the credential to its source without
     * the offer/issuance layers needing to understand invitation semantics.
     */
    val boundUsageToken: String? = null,
    /** Pre-authorized code for this issuance when the pre-auth flow was used. */
    val preAuthCode: String? = null,
    /**
     * Optional per-session allow-list narrowing which post-issuance hook
     * command IDs fire for this issuance. Null = no narrowing (the deployment-
     * level resolved set wins). Non-null = the hook dispatcher intersects
     * the deployment-level set with this list so a single batch / single
     * offer can scope hooks (test batch limiting to the consume hook,
     * audit-only batch skipping webhooks, etc.).
     */
    val postIssuanceHookAllowList: List<String>? = null,
    /** Join key to the EDK pipeline session (its correlationId). Null when no pipeline is bound. */
    val pipelineCorrelationId: String? = null,
    val createdAt: Long,
    val expiresAt: Long,
)

@JsExportCompat
@Serializable
enum class IssuanceSessionStatus {
    OFFER_CREATED,
    OFFER_RECEIVED,
    TOKEN_REQUESTED,
    CREDENTIAL_REQUESTED,
    CREDENTIAL_ISSUED,
    DEFERRED,
    COMPLETED,
    EXPIRED,
    FAILED,
}

@JsExportCompat
@Serializable
data class DeferredCredentialEntry(
    val transactionId: String,
    val issuanceSessionId: String,
    val credentialConfigurationId: String,
    val status: DeferredCredentialStatus,
    val credentialResponse: JsonElement? = null,
    val credentialResponses: List<JsonElement>? = null,
    val notificationId: String? = null,
    val retryAfterSeconds: Int = 5,
    val createdAt: Long,
    val expiresAt: Long,
)

@JsExportCompat
@Serializable
enum class DeferredCredentialStatus {
    PENDING,
    READY,
    FAILED,
    EXPIRED,
    DELIVERED,
}
