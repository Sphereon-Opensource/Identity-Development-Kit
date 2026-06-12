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
 *
 */

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.store.party.model

import com.sphereon.core.api.HasId
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A correlation identifier that can be used to look up an identity.
 * Examples include DIDs, X.509 certificates, VAT numbers, email addresses, etc.
 *
 * Each identifier can have a type-specific extension (e.g., [IdentifierX509] for X.509 certs).
 * The lookup flow is: identifier value → correlation_identifier → identity → party
 */
@JsExportCompat
@Serializable
data class CorrelationIdentifier
    @JvmOverloads
    constructor(
        /** Unique identifier for this correlation identifier */
        @SerialName("id")
        val correlationId: Uuid,
        /** The identity this identifier belongs to (identity.party_id) */
        @SerialName("identityId")
        val identityId: Uuid,
        /** Tenant this identifier belongs to */
        @SerialName("tenantId")
        val tenantId: String,
        /** The type of identifier */
        @SerialName("identifierType")
        val identifierType: IdentifierType,
        /** The actual identifier value (DID string, email, phone number, VAT number, etc.) */
        val value: String,
        /** Optional protected representation of the identifier value (inert envelope; no crypto in this layer) */
        @SerialName("protectedValue")
        val protectedValue: ProtectedIdentifierValue? = null,
        /** Whether this is the primary identifier for its type */
        @SerialName("isPrimary")
        val isPrimary: Boolean = false,
        /** Whether this identifier has been verified */
        @SerialName("isVerified")
        val isVerified: Boolean = false,
        /** When the identifier was verified */
        @SerialName("verifiedAt")
        val verifiedAt: Instant? = null,
        /** When this identifier became active */
        @SerialName("validFrom")
        val validFrom: Instant,
        /** When this identifier expired (null = current/active) */
        @SerialName("validUntil")
        val validUntil: Instant? = null,
        /** When the identifier was created */
        @SerialName("createdAt")
        val createdAt: Instant,
        /** Who created the identifier (party ID) */
        @SerialName("createdById")
        val createdById: Uuid? = null,
        /** When the identifier was last updated */
        @SerialName("updatedAt")
        val updatedAt: Instant,
        /** Who last updated the identifier (party ID) */
        @SerialName("updatedById")
        val updatedById: Uuid? = null,
        /** When the identifier was soft-deleted (null if not deleted) */
        @SerialName("deletedAt")
        val deletedAt: Instant? = null,
        /** Who deleted the identifier (party ID) */
        @SerialName("deletedById")
        val deletedById: Uuid? = null,
    ) : HasId {
        override val id: String get() = correlationId.toString()
    }
