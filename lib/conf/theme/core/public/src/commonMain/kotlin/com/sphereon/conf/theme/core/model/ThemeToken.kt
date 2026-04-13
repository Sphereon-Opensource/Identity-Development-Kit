package com.sphereon.conf.theme.core.model

import kotlinx.serialization.Serializable

/**
 * A single design token within a theme.
 *
 * @property key Dot-separated token key (e.g. "color.primary", "typography.body.fontSize")
 * @property value The token value. Can be a literal or a reference like "{color.primary}"
 * @property type The semantic type of this token
 */
@Serializable
data class ThemeToken(
    val key: String,
    val value: String,
    val type: ThemeTokenType = ThemeTokenType.STRING
)
