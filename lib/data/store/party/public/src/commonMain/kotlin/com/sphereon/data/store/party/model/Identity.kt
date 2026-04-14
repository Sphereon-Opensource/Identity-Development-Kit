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
 * An identity represents how a party presents itself in the credential ecosystem.
 * A party can have multiple identities for different purposes (e.g., separate issuer
 * and holder identities).
 *
 * In the "everything is a party" pattern, Identity extends Party. The [partyId] serves
 * as both the primary key and the foreign key to the party table when persistence is used.
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
    )
