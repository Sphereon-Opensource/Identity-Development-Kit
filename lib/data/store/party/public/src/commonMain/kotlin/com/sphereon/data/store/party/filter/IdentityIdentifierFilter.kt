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
import com.sphereon.data.store.party.model.IdentifierType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Filter for querying identity identifiers.
 *
 * All filter fields are optional - null means no filter on that field.
 * Multiple filters are combined with AND logic.
 *
 * Usage:
 * ```kotlin
 * // Find all DID identifiers for an identity
 * IdentityIdentifierFilter(
 *     identityId = someUuid,
 *     identifierType = IdentifierType.DID
 * )
 *
 * // Find verified primary identifiers
 * IdentityIdentifierFilter(isPrimary = true, isVerified = true)
 *
 * // Search by lookup value pattern
 * IdentityIdentifierFilter(lookupValuePattern = "%@example.com")
 * ```
 */
@JsExportCompat
@Serializable
data class IdentityIdentifierFilter
    @JvmOverloads
    constructor(
        /** Filter by identity ID */
        @SerialName("identityId")
        val identityId: Uuid? = null,
        /** Filter by identifier type (exact match) */
        @SerialName("identifierType")
        val identifierType: IdentifierType? = null,
        /** Filter by multiple types (IN clause) */
        @SerialName("identifierTypes")
        val identifierTypes: List<IdentifierType>? = null,
        /** Filter by stored lookup value pattern (LIKE clause, use % for wildcards) */
        @SerialName("lookupValuePattern")
        val lookupValuePattern: String? = null,
        /** Filter by exact stored lookup value */
        @SerialName("lookupValue")
        val lookupValue: String? = null,
        /** Filter by primary flag */
        @SerialName("isPrimary")
        val isPrimary: Boolean? = null,
        /** Filter by verified flag */
        @SerialName("isVerified")
        val isVerified: Boolean? = null,
        /** Filter for currently valid identifiers only */
        @SerialName("validOnly")
        val validOnly: Boolean = false,
        /** Filter by valid_from after this time */
        @SerialName("validFromAfter")
        val validFromAfter: Instant? = null,
        /** Filter by valid_until before this time */
        @SerialName("validUntilBefore")
        val validUntilBefore: Instant? = null,
        /** Filter identifiers created after this time */
        @SerialName("createdAfter")
        val createdAfter: Instant? = null,
        /** Filter identifiers created before this time */
        @SerialName("createdBefore")
        val createdBefore: Instant? = null,
        /** Include soft-deleted identifiers (default: false) */
        @SerialName("includeDeleted")
        val includeDeleted: Boolean = false,
        /** Pagination and sorting */
        val page: PageRequest = PageRequest.DEFAULT,
    ) {
        companion object {
            /** Default filter (no filtering, default pagination) */
            val DEFAULT = IdentityIdentifierFilter()

            /** Filter by identifier type */
            fun byType(type: IdentifierType) = IdentityIdentifierFilter(identifierType = type)

            /** Filter by identity */
            fun byIdentity(identityId: Uuid) = IdentityIdentifierFilter(identityId = identityId)

            /** Filter for primary identifiers only */
            fun primaryOnly() = IdentityIdentifierFilter(isPrimary = true)

            /** Filter for verified identifiers only */
            fun verifiedOnly() = IdentityIdentifierFilter(isVerified = true)

            /** Filter for currently valid identifiers */
            fun validNow() = IdentityIdentifierFilter(validOnly = true)

            /** Search by exact stored lookup value */
            fun byLookupValue(lookupValue: String) = IdentityIdentifierFilter(lookupValue = lookupValue)

            /** Search by stored lookup value pattern */
            fun byLookupValuePattern(pattern: String) = IdentityIdentifierFilter(lookupValuePattern = pattern)
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
