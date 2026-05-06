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
 * The central entity for any participant in the system.
 * Everything is a Party - natural persons, organizations, identities, addresses, and resources
 * all extend the party model.
 *
 * The party_id is used consistently as the primary key pattern across the system.
 */
@JsExportCompat
@Serializable
data class Party
    @JvmOverloads
    constructor(
        /** Unique identifier for the party */
        @SerialName("id")
        val partyId: Uuid,
        /** Tenant this party belongs to */
        @SerialName("tenantId")
        val tenantId: String,
        /** The type of party */
        @SerialName("partyType")
        val partyType: PartyType,
        /** Origin of the party (external/managed) */
        val origin: PartyOrigin,
        /**
         * User-friendly display name (editable by user). Non-null: abstract
         * identity-backed parties get filled with the identity's own UUID at
         * create-time so the schema invariant holds without a semantically-
         * empty sentinel; concrete Party roles (NaturalPerson / Organization /
         * Contact / OID4VCI-issuer / OID4VP-verifier / credential-template)
         * populate this with a meaningful human-readable name.
         */
        @SerialName("displayName")
        val displayName: String,
        /** Optional URI for the party (DID, URL, etc.) */
        val uri: String? = null,
        /** Primary jurisdiction for this party (ISO 3166-1 code or region, e.g. "EU", "US", "NL") */
        val jurisdiction: String? = null,
        /** Reference to the owning party (for identities, this is the person/org that owns the identity) */
        @SerialName("ownerId")
        val ownerId: Uuid? = null,
        /** When the party was created */
        @SerialName("createdAt")
        val createdAt: Instant,
        /** Who created the party (party ID) */
        @SerialName("createdById")
        val createdById: Uuid? = null,
        /** When the party was last updated */
        @SerialName("updatedAt")
        val updatedAt: Instant,
        /** Who last updated the party (party ID) */
        @SerialName("updatedById")
        val updatedById: Uuid? = null,
        /** When the party was soft-deleted (null if not deleted) */
        @SerialName("deletedAt")
        val deletedAt: Instant? = null,
        /** Who deleted the party (party ID) */
        @SerialName("deletedById")
        val deletedById: Uuid? = null,
    ) : HasId {
        override val id: String get() = partyId.toString()
    }
