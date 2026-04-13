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

package com.sphereon.conf.theme.core.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A theme definition containing a set of design tokens at a given scope.
 *
 * Definitions are layered during resolution: SYSTEM < APP < TENANT < PRINCIPAL,
 * with variant-specific definitions overriding common (null-variant) ones at the same scope.
 *
 * @property id Unique identifier for this definition
 * @property name Human-readable name
 * @property variant Optional variant (LIGHT/DARK/HIGH_CONTRAST). Null = common baseline for this scope.
 * @property parentId Optional parent definition to inherit from
 * @property scope The hierarchical scope at which this definition applies
 * @property tokens The design tokens defined at this level
 * @property createdAt When this definition was created
 * @property updatedAt When this definition was last updated
 */
@Serializable
data class ThemeDefinition(
    val id: String,
    val name: String,
    val variant: ThemeVariant? = null,
    val parentId: String? = null,
    val scope: ThemeScope = ThemeScope.APP,
    val appId: String? = null,
    val tokens: List<ThemeToken> = emptyList(),
    val version: Long = 1,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
