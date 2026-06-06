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

package com.sphereon.openid.wallet

import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.model.CredentialClaim
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class CredentialInstanceState { ACTIVE, USED, EXPIRED, REVOKED }

/**
 * A single credential instance stored in the wallet — the raw credential bytes,
 * the holder key alias used to bind it, and its current lifecycle state.
 *
 * [boundTo] identifies the relying party this instance is bound to (e.g. via
 * Key Binding JWT). Null means the instance is not bound to any specific party.
 */
@Serializable
data class WalletCredentialInstance(
    val credentialId: String,
    val format: String,
    val raw: String,
    val holderKeyAlias: String,
    val domain: String = "default",
    val usageCount: Int = 0,
    val validFrom: Instant? = null,
    val validUntil: Instant? = null,
    val state: CredentialInstanceState = CredentialInstanceState.ACTIVE,
    val boundTo: IdentifierRef? = null,
)

/**
 * Configuration for when and how to replenish credential instances.
 */
@Serializable
data class WalletRefreshState(
    val refreshToken: String? = null,
    val reprovisionEndpoint: String? = null,
    val lowWatermark: Int = 1,
)

/**
 * A WalletDocument is the wallet's unit of storage for a single credential type
 * from a single issuer. It holds one or more [WalletCredentialInstance]s
 * (Multipaz-style pooling), the OID4VCI multi-language display metadata, and
 * replenishment state.
 */
@Serializable
data class WalletDocument(
    val id: String,
    val issuer: IdentifierRef,
    val credentialTypeId: String,
    val subjects: List<IdentifierRef> = emptyList(),
    val issuerDisplay: List<DisplayProperties> = emptyList(),
    val credentialDisplay: List<DisplayProperties> = emptyList(),
    val claims: List<CredentialClaim> = emptyList(),
    val refresh: WalletRefreshState = WalletRefreshState(),
    val credentials: List<WalletCredentialInstance> = emptyList(),
) {
    /**
     * Returns the display name for the given locale, falling back to the first entry.
     */
    fun displayName(locale: String? = null): String? = (credentialDisplay.firstOrNull { it.locale == locale } ?: credentialDisplay.firstOrNull())?.name

    /**
     * Returns a copy of this document with the given instance appended.
     */
    fun withAddedInstance(instance: WalletCredentialInstance): WalletDocument = copy(credentials = credentials + instance)

    /**
     * Returns the first unused (ACTIVE) instance for the given domain, if any.
     */
    fun unusedInstance(domain: String = "default"): WalletCredentialInstance? = credentials.firstOrNull { it.domain == domain && it.state == CredentialInstanceState.ACTIVE }

    /**
     * True when the active instance count falls below [WalletRefreshState.lowWatermark].
     */
    val needsRefresh: Boolean
        get() = credentials.count { it.state == CredentialInstanceState.ACTIVE } < refresh.lowWatermark

    /**
     * Derives a [WalletDocumentMetadata] snapshot of this document at [now].
     *
     * Format resolution: uses [CredentialFormat.fromValueLenient] on the first instance's
     * format string. If instances are empty or the format is unrecognized, defaults to
     * [CredentialFormat.SD_JWT_DC] as a safe fallback for the wallet's primary use case.
     *
     * Status priority: REVOKED > EXPIRED (no ACTIVE instances) > ACTIVE.
     */
    fun metadata(now: Instant): WalletDocumentMetadata {
        val credentialFormat =
            credentials
                .firstOrNull()
                ?.let { CredentialFormat.fromValueLenient(it.format) }
                ?: CredentialFormat.SD_JWT_DC

        val issuedAt = credentials.mapNotNull { it.validFrom }.minOrNull()
        val expiresAt = credentials.mapNotNull { it.validUntil }.maxOrNull()

        val status =
            when {
                credentials.any { it.state == CredentialInstanceState.REVOKED } -> CredentialInstanceState.REVOKED
                credentials.none { it.state == CredentialInstanceState.ACTIVE } -> CredentialInstanceState.EXPIRED
                else -> CredentialInstanceState.ACTIVE
            }

        val boundInstanceCount = credentials.count { it.boundTo != null }

        return WalletDocumentMetadata(
            documentId = id,
            issuer = issuer,
            subjects = subjects,
            credentialFormat = credentialFormat,
            credentialType = credentialTypeId,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            status = status,
            instanceCount = credentials.size,
            boundInstanceCount = boundInstanceCount,
            issuerDisplay = issuerDisplay,
            credentialDisplay = credentialDisplay,
            updatedAt = now,
        )
    }
}
