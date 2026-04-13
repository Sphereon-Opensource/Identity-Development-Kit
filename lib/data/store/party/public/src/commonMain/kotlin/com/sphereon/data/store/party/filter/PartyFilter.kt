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
 *
 */

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.store.party.filter

import com.sphereon.data.store.party.model.PartyOrigin
import com.sphereon.data.store.party.model.PartyType
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Filter for querying parties.
 *
 * All filter fields are optional - null means no filter on that field.
 * Multiple filters are combined with AND logic.
 *
 * Usage:
 * ```kotlin
 * // Find organization parties
 * PartyFilter(partyType = PartyType.ORGANIZATION)
 *
 * // Search by display name
 * PartyFilter(displayNamePattern = "%Corp%")
 *
 * // Find managed parties owned by a specific party
 * PartyFilter(origin = PartyOrigin.MANAGED, ownerId = someUuid)
 * ```
 */
@Serializable
data class PartyFilter(
    /** Filter by party type (exact match) */
    @SerialName("partyType")
    val partyType: PartyType? = null,

    /** Filter by multiple types (IN clause) */
    @SerialName("partyTypes")
    val partyTypes: List<PartyType>? = null,

    /** Filter by origin (external/managed) */
    val origin: PartyOrigin? = null,

    /** Filter by display name pattern (LIKE clause, use % for wildcards) */
    @SerialName("displayNamePattern")
    val displayNamePattern: String? = null,

    /** Filter by exact display name */
    @SerialName("displayName")
    val displayName: String? = null,

    /** Filter by URI pattern (LIKE clause, use % for wildcards) */
    @SerialName("uriPattern")
    val uriPattern: String? = null,

    /** Filter by exact URI */
    val uri: String? = null,

    /** Filter by owner party ID */
    @SerialName("ownerId")
    val ownerId: Uuid? = null,

    /** Filter parties that have no owner (root parties) */
    @SerialName("hasNoOwner")
    val hasNoOwner: Boolean? = null,

    /** Filter parties created after this time */
    @SerialName("createdAfter")
    val createdAfter: Instant? = null,

    /** Filter parties created before this time */
    @SerialName("createdBefore")
    val createdBefore: Instant? = null,

    /** Filter parties updated after this time */
    @SerialName("updatedAfter")
    val updatedAfter: Instant? = null,

    /** Filter parties updated before this time */
    @SerialName("updatedBefore")
    val updatedBefore: Instant? = null,

    /** Include soft-deleted parties (default: false) */
    @SerialName("includeDeleted")
    val includeDeleted: Boolean = false,

    /** Pagination and sorting */
    val page: PageRequest = PageRequest.DEFAULT
) {
    companion object {
        /** Default filter (no filtering, default pagination) */
        val DEFAULT = PartyFilter()

        /** Filter by party type */
        fun byType(type: PartyType) = PartyFilter(partyType = type)

        /** Filter by multiple types */
        fun byTypes(types: List<PartyType>) = PartyFilter(partyTypes = types)

        /** Filter by origin */
        fun byOrigin(origin: PartyOrigin) = PartyFilter(origin = origin)

        /** Search by display name pattern */
        fun byDisplayName(pattern: String) = PartyFilter(displayNamePattern = "%$pattern%")

        /** Filter by owner */
        fun byOwner(ownerId: Uuid) = PartyFilter(ownerId = ownerId)

        /** Filter for root parties (no owner) */
        fun rootParties() = PartyFilter(hasNoOwner = true)

        /** Filter parties created in a time range */
        fun createdBetween(after: Instant, before: Instant) =
            PartyFilter(createdAfter = after, createdBefore = before)
    }

    /** Set pagination */
    fun withPage(pageRequest: PageRequest) = copy(page = pageRequest)

    /** Set limit */
    fun withLimit(limit: Int) = copy(page = page.copy(limit = limit))

    /** Set offset */
    fun withOffset(offset: Int) = copy(page = page.copy(offset = offset))

    /** Add sorting */
    fun withSort(field: String, direction: SortDirection = SortDirection.ASC) =
        copy(page = page.withSort(field, direction))
}
