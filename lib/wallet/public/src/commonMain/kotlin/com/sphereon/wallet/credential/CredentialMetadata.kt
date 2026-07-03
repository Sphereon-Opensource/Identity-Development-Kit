/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.wallet.credential

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CredentialLifecycleSummary(
    val lifecycleState: CredentialLifecycleState,
    val validityState: CredentialValidityState,
    val tombstone: Boolean = false,
)

/**
 * Queryable wallet credential sidecar. It is stored separately from the credential body
 * and intentionally contains only selection/display/indexing data.
 */
@Serializable
data class CredentialMetadata(
    val credentialRecordId: String,
    val walletInstanceId: String,
    val issuerRef: IdentifierRef,
    val subjectRefs: List<IdentifierRef> = emptyList(),
    val format: CredentialFormat,
    val credentialTypeRefs: Set<CredentialTypeRef>,
    val credentialConfigurationId: String? = null,
    val display: CredentialDisplayMetadata = CredentialDisplayMetadata(),
    val lifecycleSummary: CredentialLifecycleSummary,
    val instanceCount: Int,
    val activeInstanceCount: Int,
    val boundInstanceCount: Int,
    val issuedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val updatedAt: Instant,
) {
    init {
        require(credentialRecordId.isNotBlank()) { "CredentialMetadata.credentialRecordId must not be blank" }
        require(walletInstanceId.isNotBlank()) { "CredentialMetadata.walletInstanceId must not be blank" }
        require(credentialTypeRefs.isNotEmpty()) { "CredentialMetadata.credentialTypeRefs must not be empty" }
    }

    fun matches(filter: CredentialMetadataFilter): Boolean {
        if (!filter.includeDeleted && lifecycleSummary.tombstone) return false
        if (filter.formats.isNotEmpty() && format !in filter.formats) return false
        if (filter.issuerRef != null && issuerRef != filter.issuerRef) return false
        if (filter.subjectRef != null && filter.subjectRef !in subjectRefs) return false
        if (filter.credentialConfigurationId != null && credentialConfigurationId != filter.credentialConfigurationId) return false
        if (filter.lifecycleStates.isNotEmpty() && lifecycleSummary.lifecycleState !in filter.lifecycleStates) return false
        if (filter.credentialTypeRefs.isNotEmpty() && !hasAnyTypeRef(filter.credentialTypeRefs)) return false
        return true
    }

    fun hasTypeRef(ref: CredentialTypeRef): Boolean =
        credentialTypeRefs.any { it.sameReference(ref) }

    private fun hasAnyTypeRef(refs: Set<CredentialTypeRef>): Boolean =
        refs.any { hasTypeRef(it) }
}

@Serializable
data class CredentialMetadataFilter(
    val credentialTypeRefs: Set<CredentialTypeRef> = emptySet(),
    val formats: Set<CredentialFormat> = emptySet(),
    val issuerRef: IdentifierRef? = null,
    val subjectRef: IdentifierRef? = null,
    val credentialConfigurationId: String? = null,
    val lifecycleStates: Set<CredentialLifecycleState> = emptySet(),
    val includeDeleted: Boolean = false,
)
