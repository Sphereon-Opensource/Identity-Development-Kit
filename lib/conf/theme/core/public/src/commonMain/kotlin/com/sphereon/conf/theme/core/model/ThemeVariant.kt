package com.sphereon.conf.theme.core.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * Visual variant of a theme, controlling light/dark/high-contrast appearance.
 */
@JsExportCompat
@Serializable
enum class ThemeVariant {
    LIGHT,
    DARK,
    HIGH_CONTRAST
}
