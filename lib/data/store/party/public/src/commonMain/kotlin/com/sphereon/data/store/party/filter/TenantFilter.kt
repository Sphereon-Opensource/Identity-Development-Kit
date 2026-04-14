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

package com.sphereon.data.store.party.filter

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.store.party.model.TenantType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Filter for querying tenants.
 *
 * All filter fields are optional - null means no filter on that field.
 * Multiple filters are combined with AND logic.
 *
 * Usage:
 * ```kotlin
 * // Find platform tenants
 * TenantFilter(tenantType = TenantType.PLATFORM)
 *
 * // Search by name
 * TenantFilter(namePattern = "%Corp%")
 *
 * // Find tenants owned by a specific party
 * TenantFilter(ownerPartyId = someUuid)
 * ```
 */
@JsExportCompat
@Serializable
data class TenantFilter
    @JvmOverloads
    constructor(
        /** Filter by tenant type (exact match) */
        @SerialName("tenantType")
        val tenantType: TenantType? = null,
        /** Filter by multiple types (IN clause) */
        @SerialName("tenantTypes")
        val tenantTypes: List<TenantType>? = null,
        /** Filter by name pattern (LIKE clause, use % for wildcards) */
        @SerialName("namePattern")
        val namePattern: String? = null,
        /** Filter by exact name */
        val name: String? = null,
        /** Filter by owner party ID */
        @SerialName("ownerPartyId")
        val ownerPartyId: Uuid? = null,
        /** Filter tenants created after this time */
        @SerialName("createdAfter")
        val createdAfter: Instant? = null,
        /** Filter tenants created before this time */
        @SerialName("createdBefore")
        val createdBefore: Instant? = null,
        /** Filter tenants updated after this time */
        @SerialName("updatedAfter")
        val updatedAfter: Instant? = null,
        /** Filter tenants updated before this time */
        @SerialName("updatedBefore")
        val updatedBefore: Instant? = null,
        /** Include soft-deleted tenants (default: false) */
        @SerialName("includeDeleted")
        val includeDeleted: Boolean = false,
        /** Pagination and sorting */
        val page: PageRequest = PageRequest.DEFAULT,
    ) {
        companion object {
            /** Default filter (no filtering, default pagination) */
            val DEFAULT = TenantFilter()

            /** Filter by tenant type */
            fun byType(type: TenantType) = TenantFilter(tenantType = type)

            /** Search by name pattern */
            fun byName(pattern: String) = TenantFilter(namePattern = "%$pattern%")

            /** Filter by owner */
            fun byOwner(ownerPartyId: Uuid) = TenantFilter(ownerPartyId = ownerPartyId)

            /** Filter tenants created in a time range */
            fun createdBetween(
                after: Instant,
                before: Instant,
            ) = TenantFilter(createdAfter = after, createdBefore = before)
        }

        /** Set pagination */
        fun withPage(pageRequest: PageRequest) = copy(page = pageRequest)

        /** Set limit */
        fun withLimit(limit: Int) = copy(page = page.copy(limit = limit))

        /** Add sorting */
        fun withSort(
            field: String,
            direction: SortDirection = SortDirection.ASC,
        ) = copy(page = page.withSort(field, direction))
    }
