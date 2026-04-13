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

import com.sphereon.data.store.party.model.CorrelationIdentifier
import com.sphereon.data.store.party.model.Identity
import com.sphereon.data.store.party.model.IdentityRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Result model for identity queries.
 *
 * Contains all core identity fields plus optional associations that can be
 * populated based on [IdentityFetchOptions].
 *
 * Usage:
 * ```kotlin
 * // Create from entity
 * val result = IdentityResult.from(identity)
 *
 * // Create with associations
 * val resultWithIds = IdentityResult.from(
 *     identity = identity,
 *     correlationIdentifiers = listOf(...)
 * )
 * ```
 */
@Serializable
data class IdentityResult(
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
    val isDefault: Boolean,
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
    /**
     * Correlation identifiers associated with this identity.
     * Populated when [IdentityFetchOptions.includeCorrelationIdentifiers] is true.
     * Null means not fetched (vs empty list which means no identifiers exist).
     */
    @SerialName("correlationIdentifiers")
    val correlationIdentifiers: List<CorrelationIdentifierResult>? = null,
) {
    companion object {
        /**
         * Create an IdentityResult from an Identity entity.
         *
         * @param identity The source identity entity
         * @param correlationIdentifiers Optional list of correlation identifier results
         */
        fun from(
            identity: Identity,
            correlationIdentifiers: List<CorrelationIdentifierResult>? = null,
        ) = IdentityResult(
            partyId = identity.partyId,
            tenantId = identity.tenantId,
            identityRole = identity.identityRole,
            isDefault = identity.isDefault,
            createdAt = identity.createdAt,
            createdById = identity.createdById,
            updatedAt = identity.updatedAt,
            updatedById = identity.updatedById,
            deletedAt = identity.deletedAt,
            deletedById = identity.deletedById,
            correlationIdentifiers = correlationIdentifiers,
        )

        /**
         * Create an IdentityResult from an Identity entity with raw correlation identifiers.
         *
         * @param identity The source identity entity
         * @param correlationIdentifiers List of correlation identifier entities to convert
         */
        fun fromWithIdentifiers(
            identity: Identity,
            correlationIdentifiers: List<CorrelationIdentifier>,
        ) = from(
            identity = identity,
            correlationIdentifiers = correlationIdentifiers.map { CorrelationIdentifierResult.from(it) },
        )
    }

    /** Convert back to the core Identity entity (without associations) */
    fun toIdentity() =
        Identity(
            partyId = partyId,
            tenantId = tenantId,
            identityRole = identityRole,
            isDefault = isDefault,
            createdAt = createdAt,
            createdById = createdById,
            updatedAt = updatedAt,
            updatedById = updatedById,
            deletedAt = deletedAt,
            deletedById = deletedById,
        )
}
