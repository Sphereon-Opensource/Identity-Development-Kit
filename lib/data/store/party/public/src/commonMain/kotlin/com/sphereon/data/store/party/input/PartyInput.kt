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

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.store.party.model.PartyOrigin
import com.sphereon.data.store.party.model.PartyType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Input for creating a new party.
 *
 * The [id] is optional - if not provided, the system will generate one.
 */
@JsExportCompat
@Serializable
data class PartyCreateInput
    @JvmOverloads
    constructor(
        /** Optional ID - if null, system generates one */
        val id: Uuid? = null,
        /** The type of party */
        @SerialName("partyType")
        val partyType: PartyType,
        /** Origin of the party (external/managed) */
        val origin: PartyOrigin,
        /** User-friendly display name */
        @SerialName("displayName")
        val displayName: String,
        /** Optional URI for the party (DID, URL, etc.) */
        val uri: String? = null,
        /** Primary jurisdiction for this party (ISO 3166-1 code or region, e.g. "EU", "US", "NL") */
        val jurisdiction: String? = null,
        /** Reference to the owning party */
        @SerialName("ownerId")
        val ownerId: Uuid? = null,
        /** Home organization unit. Null is valid for tenant-root and organization-unit Parties. */
        @SerialName("organizationUnitId")
        val organizationUnitId: Uuid? = null,
    )

/**
 * Input for updating an existing party.
 *
 * The [id] is required to identify which party to update.
 * All other fields are optional - only non-null values will be updated.
 */
@JsExportCompat
@Serializable
data class PartyUpdateInput
    @JvmOverloads
    constructor(
        /** Required - the party to update */
        val id: Uuid,
        /** New display name (null = keep current) */
        @SerialName("displayName")
        val displayName: String? = null,
        /** New URI (null = keep current) */
        val uri: String? = null,
        /** New jurisdiction (null = keep current) */
        val jurisdiction: String? = null,
        /** New owner ID (null = keep current) */
        @SerialName("ownerId")
        val ownerId: Uuid? = null,
        /** New home organization unit (null = keep current). */
        @SerialName("organizationUnitId")
        val organizationUnitId: Uuid? = null,
    )
