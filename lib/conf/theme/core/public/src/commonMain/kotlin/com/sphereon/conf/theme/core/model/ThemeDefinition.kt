package com.sphereon.conf.theme.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

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
    val updatedAt: Instant? = null
)
