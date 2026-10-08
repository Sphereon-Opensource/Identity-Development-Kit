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

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * An identity represents a credential-ecosystem principal and lookup boundary.
 *
 * Plaintext profile data belongs in Party records and Party extensions when policy permits it.
 * This type intentionally carries no natural-person, organization, address, or endpoint profile
 * fields. The [partyId] property is the storage identifier for the identity row; new code should
 * treat it as the identity id and use explicit identity-party bindings for Party profile links.
 */
@JsExportCompat
@Serializable
data class Identity
    @JvmOverloads
    constructor(
        /** Party ID - serves as the primary key */
        @SerialName("partyId")
        val partyId: Uuid,
        /** Tenant this identity belongs to */
        @SerialName("tenantId")
        val tenantId: String,
        /** The role of this identity in the credential ecosystem */
        @SerialName("identityRole")
        val identityRole: IdentityRole,
        /** Whether this is the default identity for the owning party */
        @SerialName("isDefault")
        val isDefault: Boolean = false,
        /**
         * The specialization this identity serves, when it is intrinsically role-scoped
         * (e.g. an `employee` login identity vs a `customer` login identity on the same person).
         * Null means the identity is party-global and login resolution fans out then filters by binding.
         */
        @SerialName("specializationSubtype")
        val specializationSubtype: String? = null,
        /** Controls whether readable profile data may exist on bound Party records. */
        @SerialName("privacyMode")
        val privacyMode: IdentityPrivacyMode = IdentityPrivacyMode.PARTY_PROFILED,
        /** Opaque per-identity salt handle (reference only; no crypto in this layer) */
        @SerialName("saltRef")
        val saltRef: String? = null,
        /** When the identity was created */
        @SerialName("createdAt")
        val createdAt: Instant,
        /** Who created the identity (party ID) */
        @SerialName("createdById")
        val createdById: Uuid? = null,
        /** When the identity was last updated */
        @SerialName("updatedAt")
        val updatedAt: Instant,
        /** Who last updated the identity (party ID) */
        @SerialName("updatedById")
        val updatedById: Uuid? = null,
        /** When the identity was soft-deleted (null if not deleted) */
        @SerialName("deletedAt")
        val deletedAt: Instant? = null,
        /** Who deleted the identity (party ID) */
        @SerialName("deletedById")
        val deletedById: Uuid? = null,
    ) {
        val identityId: Uuid get() = partyId
    }
