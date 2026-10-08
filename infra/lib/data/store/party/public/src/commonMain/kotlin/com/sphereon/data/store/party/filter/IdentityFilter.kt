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

import com.sphereon.core.api.pagination.PageRequest
import com.sphereon.core.api.pagination.SortDirection
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.store.party.model.IdentityRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Filter for querying identities.
 *
 * All filter fields are optional - null means no filter on that field.
 * Multiple filters are combined with AND logic.
 *
 * Usage:
 * ```kotlin
 * // Find all issuer identities
 * IdentityFilter(identityRole = IdentityRole.ISSUER)
 *
 * // Find default identities created recently
 * IdentityFilter(
 *     isDefault = true,
 *     createdAfter = Clock.System.now().minus(7.days)
 * )
 *
 * // Using companion helpers
 * IdentityFilter.byRole(IdentityRole.VERIFIER)
 * IdentityFilter.defaultOnly()
 * ```
 */
@JsExportCompat
@Serializable
data class IdentityFilter
    @JvmOverloads
    constructor(
        /** Filter by identity role (exact match) */
        @SerialName("identityRole")
        val identityRole: IdentityRole? = null,
        /** Filter by multiple roles (IN clause) */
        @SerialName("identityRoles")
        val identityRoles: List<IdentityRole>? = null,
        /** Filter by default flag */
        @SerialName("isDefault")
        val isDefault: Boolean? = null,
        /** Filter identities created after this time */
        @SerialName("createdAfter")
        val createdAfter: Instant? = null,
        /** Filter identities created before this time */
        @SerialName("createdBefore")
        val createdBefore: Instant? = null,
        /** Filter identities updated after this time */
        @SerialName("updatedAfter")
        val updatedAfter: Instant? = null,
        /** Filter identities updated before this time */
        @SerialName("updatedBefore")
        val updatedBefore: Instant? = null,
        /** Include soft-deleted identities (default: false) */
        @SerialName("includeDeleted")
        val includeDeleted: Boolean = false,
        /** Pagination and sorting */
        val page: PageRequest = PageRequest.DEFAULT,
    ) {
        companion object {
            /** Default filter (no filtering, default pagination) */
            val DEFAULT = IdentityFilter()

            /** Filter by a single role */
            fun byRole(role: IdentityRole) = IdentityFilter(identityRole = role)

            /** Filter by multiple roles */
            fun byRoles(roles: List<IdentityRole>) = IdentityFilter(identityRoles = roles)

            /** Filter for default identities only */
            fun defaultOnly() = IdentityFilter(isDefault = true)

            /** Filter identities created in a time range */
            fun createdBetween(
                after: Instant,
                before: Instant,
            ) = IdentityFilter(createdAfter = after, createdBefore = before)
        }

        /** Set pagination */
        fun withPage(pageRequest: PageRequest) = copy(page = pageRequest)

        /** Set limit */
        fun withLimit(limit: Int) = copy(page = page.copy(limit = limit))

        /** Set offset */
        fun withOffset(offset: Int) = copy(page = page.copy(offset = offset))

        /** Add sorting */
        fun withSort(
            field: String,
            direction: SortDirection = SortDirection.ASC,
        ) = copy(page = page.withSort(field, direction))
    }
