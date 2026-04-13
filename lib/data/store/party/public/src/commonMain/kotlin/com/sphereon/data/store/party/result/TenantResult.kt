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

package com.sphereon.data.store.party.result

import com.sphereon.data.store.party.model.Tenant
import com.sphereon.data.store.party.model.TenantType
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Result model for tenant queries.
 *
 * Contains all core tenant fields plus optional aggregates and associations
 * that can be populated based on [TenantFetchOptions].
 *
 * Usage:
 * ```kotlin
 * // Create from entity
 * val result = TenantResult.from(tenant)
 *
 * // Create with aggregate counts
 * val resultWithCounts = TenantResult.from(
 *     tenant = tenant,
 *     identityCount = 42
 * )
 * ```
 */
@Serializable
data class TenantResult(
    // Core tenant fields
    /** Unique identifier for this tenant */
    @SerialName("id")
    val tenantId: String,

    /** The type of tenant */
    @SerialName("tenantType")
    val tenantType: TenantType,

    /** Human-readable name for this tenant */
    val name: String,

    /** Optional description of this tenant */
    val description: String? = null,

    /** The organization party that owns this tenant */
    @SerialName("ownerPartyId")
    val ownerPartyId: Uuid? = null,

    // Audit fields
    /** When the tenant was created */
    @SerialName("createdAt")
    val createdAt: Instant,

    /** Who created the tenant (party ID) */
    @SerialName("createdById")
    val createdById: Uuid? = null,

    /** When the tenant was last updated */
    @SerialName("updatedAt")
    val updatedAt: Instant,

    /** Who last updated the tenant (party ID) */
    @SerialName("updatedById")
    val updatedById: Uuid? = null,

    /** When the tenant was soft-deleted (null = active) */
    @SerialName("deletedAt")
    val deletedAt: Instant? = null,

    /** Who deleted the tenant (party ID) */
    @SerialName("deletedById")
    val deletedById: Uuid? = null,

    // Aggregate data (populated based on FetchOptions)
    /**
     * Count of identities in this tenant.
     * Populated when [TenantFetchOptions.includeIdentityCount] is true.
     * Null means not fetched.
     */
    @SerialName("identityCount")
    val identityCount: Long? = null,

    // Associated entities (populated based on FetchOptions)
    /**
     * The owner party details.
     * Populated when [TenantFetchOptions.includeOwnerParty] is true.
     * Null means not fetched (vs ownerPartyId being null which means no owner).
     */
    @SerialName("ownerParty")
    val ownerParty: PartyResult? = null
) {
    companion object {
        /**
         * Create a TenantResult from a Tenant entity.
         *
         * @param tenant The source tenant entity
         * @param identityCount Optional count of identities
         * @param ownerParty Optional owner party result
         */
        fun from(
            tenant: Tenant,
            identityCount: Long? = null,
            ownerParty: PartyResult? = null
        ) = TenantResult(
            tenantId = tenant.tenantId,
            tenantType = tenant.tenantType,
            name = tenant.name,
            description = tenant.description,
            ownerPartyId = tenant.ownerPartyId,
            createdAt = tenant.createdAt,
            createdById = tenant.createdById,
            updatedAt = tenant.updatedAt,
            updatedById = tenant.updatedById,
            deletedAt = tenant.deletedAt,
            deletedById = tenant.deletedById,
            identityCount = identityCount,
            ownerParty = ownerParty
        )
    }

    /** Convert back to the core Tenant entity (without associations) */
    fun toTenant() = Tenant(
        tenantId = tenantId,
        tenantType = tenantType,
        name = name,
        description = description,
        ownerPartyId = ownerPartyId,
        createdAt = createdAt,
        createdById = createdById,
        updatedAt = updatedAt,
        updatedById = updatedById,
        deletedAt = deletedAt,
        deletedById = deletedById
    )
}
