/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.identity.matching.model

import com.sphereon.identity.matching.crypto.EncryptedPayload
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Encrypted profile record linking a holder identity to an institution identity.
 *
 * This is the durable, encrypted binding that stores the reversible identity link
 * between a wallet holder and their institutional identity (e.g., eduID).
 *
 * All sensitive identifiers are either HMAC-hashed (for lookup) or AES-encrypted
 * (for reversible access). Key version metadata is carried in each crypto field
 * for rotation support.
 */
@Serializable
data class IdentityLinkBinding(
    val id: String,
    val tenantId: String,
    val matchId: String,

    // Hash metadata (key version for dual-read rotation)
    val holderIdentifierHash: String,
    val holderHashKeyVersion: String,
    val institutionIdentifierHash: String?,
    val institutionHashKeyVersion: String?,

    // Encrypted reversible payload
    val encryptedInstitutionId: EncryptedPayload?,
    val persistedAttributesEnvelope: PersistedAttributesEnvelope,

    // Provenance
    val providerId: String,
    val institutionId: String?,

    // Version metadata for staleness detection
    val canonicalSchemaVersion: String? = null,
    val materialProfileVersion: String? = null,
    val selectorRuleVersion: String? = null,
    val persistedAttributeNames: Set<String>? = null,
    val materialFingerprints: Set<String>? = null,

    // Assurance metadata (IDV-compatible field names)
    val assuranceSummary: AssuranceSummary?,

    // Lifecycle
    val createdAt: Instant,
    val updatedAt: Instant?,
    val lastUsedAt: Instant?,
)
