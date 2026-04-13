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

package com.sphereon.openid.oid4vci.holder

import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ResolvedCredentialOffer(
    val offer: CredentialOffer,
    val issuerMetadata: CredentialIssuerMetadata,
    val authorizationServerMetadata: JsonElement? = null,
    val preferredAuthorizationServer: String? = null,
)

@Serializable
data class Oid4vciHolderSession(
    val sessionId: String,
    val issuerUrl: String,
    val credentialConfigurationIds: List<String>,
    val status: Oid4vciHolderSessionStatus,
    val accessToken: String? = null,
    val tokenType: String? = null,
    @SerialName("credential_identifiers") val credentialIdentifiers: List<String>? = null,
    val cNonce: String? = null,
    val preAuthorizedCode: String? = null,
    val issuerState: String? = null,
    val createdAt: Long,
    val expiresAt: Long? = null,
)

@Serializable
enum class Oid4vciHolderSessionStatus {
    CREATED,
    OFFER_RESOLVED,
    TOKEN_OBTAINED,
    CREDENTIAL_REQUESTED,
    CREDENTIAL_RECEIVED,
    DEFERRED_PENDING,
    COMPLETED,
    FAILED,
}

@Serializable
data class CachedOffer(
    val offer: CredentialOffer,
    val metadata: CredentialIssuerMetadata,
    val cachedAt: Long,
)

@Serializable
data class DeferredPollingEntry(
    val transactionId: String,
    val sessionId: String,
    val interval: Int,
    val lastPolledAt: Long? = null,
    val status: DeferredPollingStatus = DeferredPollingStatus.PENDING,
)

@Serializable
enum class DeferredPollingStatus { PENDING, COMPLETED, FAILED }

@Serializable
data class IssuedCredentialReference(
    val credentialId: String,
    val issuerUrl: String,
    val credentialConfigurationId: String,
    val format: String,
    val issuedAt: Long,
    val notificationId: String? = null,
)
