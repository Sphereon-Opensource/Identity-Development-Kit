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

package com.sphereon.data.store.party.input

import com.sphereon.data.store.party.model.TenantType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Input for creating a new tenant.
 *
 * The [id] is optional - if not provided, the system will generate one.
 */
@Serializable
data class TenantCreateInput(
    /** Optional ID - if null, system generates one */
    val id: String? = null,
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
)

/**
 * Input for updating an existing tenant.
 *
 * The [id] is required to identify which tenant to update.
 * All other fields are optional - only non-null values will be updated.
 */
@Serializable
data class TenantUpdateInput(
    /** Required - the tenant to update */
    val id: String,
    /** New name (null = keep current) */
    val name: String? = null,
    /** New description (null = keep current) */
    val description: String? = null,
    /** New owner party ID (null = keep current) */
    @SerialName("ownerPartyId")
    val ownerPartyId: Uuid? = null,
)
