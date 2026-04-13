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

package com.sphereon.data.store.party.result

import com.sphereon.data.store.party.model.Party
import com.sphereon.data.store.party.model.PartyOrigin
import com.sphereon.data.store.party.model.PartyType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Result model for party queries.
 *
 * Contains all core party fields plus optional associations that can be
 * populated based on [PartyFetchOptions].
 *
 * Usage:
 * ```kotlin
 * // Create from entity
 * val result = PartyResult.from(party)
 *
 * // Create with associations
 * val resultWithIdentities = PartyResult.from(
 *     party = party,
 *     identities = listOf(...)
 * )
 * ```
 */
@Serializable
data class PartyResult(
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
    /** User-friendly display name (editable by user) */
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
    /**
     * The owner party details.
     * Populated when [PartyFetchOptions.includeOwner] is true.
     * Null means not fetched (vs ownerId being null which means no owner).
     */
    @SerialName("owner")
    val owner: PartyResult? = null,
    /**
     * Identities associated with this party.
     * Populated when [PartyFetchOptions.includeIdentities] is true.
     * Null means not fetched (vs empty list which means no identities exist).
     */
    @SerialName("identities")
    val identities: List<IdentityResult>? = null,
) {
    companion object {
        /**
         * Create a PartyResult from a Party entity.
         *
         * @param party The source party entity
         * @param owner Optional owner party result
         * @param identities Optional list of identity results
         */
        fun from(
            party: Party,
            owner: PartyResult? = null,
            identities: List<IdentityResult>? = null,
        ) = PartyResult(
            partyId = party.partyId,
            tenantId = party.tenantId,
            partyType = party.partyType,
            origin = party.origin,
            displayName = party.displayName,
            uri = party.uri,
            jurisdiction = party.jurisdiction,
            ownerId = party.ownerId,
            createdAt = party.createdAt,
            createdById = party.createdById,
            updatedAt = party.updatedAt,
            updatedById = party.updatedById,
            deletedAt = party.deletedAt,
            deletedById = party.deletedById,
            owner = owner,
            identities = identities,
        )
    }

    /** Convert back to the core Party entity (without associations) */
    fun toParty() =
        Party(
            partyId = partyId,
            tenantId = tenantId,
            partyType = partyType,
            origin = origin,
            displayName = displayName,
            uri = uri,
            jurisdiction = jurisdiction,
            ownerId = ownerId,
            createdAt = createdAt,
            createdById = createdById,
            updatedAt = updatedAt,
            updatedById = updatedById,
            deletedAt = deletedAt,
            deletedById = deletedById,
        )
}
