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
 * A tenant represents an isolated organizational context.
 *
 * In the "everything is a party" pattern, tenants can extend Party.
 * Tenants provide data isolation - all queries are filtered by tenant_id.
 */
@JsExportCompat
@Serializable
data class Tenant
    @JvmOverloads
    constructor(
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
    ) : HasId {
        override val id: String get() = tenantId
    }
