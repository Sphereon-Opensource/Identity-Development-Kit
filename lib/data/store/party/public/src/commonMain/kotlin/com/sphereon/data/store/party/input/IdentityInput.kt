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

import com.sphereon.data.store.party.model.IdentityRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Input for creating a new identity.
 *
 * The [id] is optional - if not provided, the system will generate one.
 */
@Serializable
data class IdentityCreateInput(
    /** Optional ID - if null, system generates one */
    @SerialName("partyId")
    val id: Uuid? = null,
    /** The role of this identity in the credential ecosystem */
    @SerialName("identityRole")
    val identityRole: IdentityRole,
    /** Whether this is the default identity for the owning party */
    @SerialName("isDefault")
    val isDefault: Boolean = false,
)

/**
 * Input for updating an existing identity.
 *
 * The [id] is required to identify which identity to update.
 * All other fields are optional - only non-null values will be updated.
 */
@Serializable
data class IdentityUpdateInput(
    /** Required - the identity to update */
    @SerialName("partyId")
    val id: Uuid,
    /** New identity role (null = keep current) */
    @SerialName("identityRole")
    val identityRole: IdentityRole? = null,
    /** New default flag (null = keep current) */
    @SerialName("isDefault")
    val isDefault: Boolean? = null,
)
