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
import kotlin.uuid.Uuid

@Serializable
data class ClaimCardinality(
    val min: Int? = null,
    val max: Int? = null,
)

@Serializable
data class FieldRenderHint(
    val path: DesignClaimPath,
    val valueKind: ClaimValueKind,
    val widgetHint: ClaimWidgetHint? = null,
    val formatHint: String? = null,
    val contentMediaType: String? = null,
    val contentEncoding: String? = null,
    val repeatable: Boolean = false,
    val contextTerms: List<String> = emptyList(),
    val characterEncoding: String? = null,
    val standard: String? = null,
    val cardinality: ClaimCardinality? = null,
    val sensitive: Boolean = false,
)

@Serializable
data class DerivedRenderHintsRecord(
    val id: Uuid,
    val tenantId: String,
    val sourceSnapshotIds: List<Uuid> = emptyList(),
    val fieldHints: List<FieldRenderHint>,
    val groupHints: Map<String, List<DesignClaimPath>> = emptyMap(),
    val defaultOrdering: List<DesignClaimPath> = emptyList(),
)
