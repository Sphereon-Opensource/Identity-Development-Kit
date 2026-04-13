/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

package com.sphereon.data.store.credential.design.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class EntityLocaleDesign(
    val locale: String,
    val displayName: String? = null,
    val description: String? = null,
)

@Serializable
data class IssuerDesignRecord(
    val id: Uuid,
    val tenantId: String,
    val alias: String? = null,
    val hostingMode: DesignHostingMode,
    val bindings: List<DesignBinding>,
    val partyId: Uuid? = null,
    val displays: List<EntityLocaleDesign>,
    val renderVariantIds: List<Uuid> = emptyList(),
    val sourceSnapshotIds: List<Uuid> = emptyList(),
    val contentHash: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class VerifierDesignRecord(
    val id: Uuid,
    val tenantId: String,
    val alias: String? = null,
    val hostingMode: DesignHostingMode,
    val bindings: List<DesignBinding>,
    val partyId: Uuid? = null,
    val displays: List<EntityLocaleDesign>,
    val renderVariantIds: List<Uuid> = emptyList(),
    val sourceSnapshotIds: List<Uuid> = emptyList(),
    val contentHash: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
