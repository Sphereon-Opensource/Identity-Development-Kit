package com.sphereon.conf.theme.compose

/**
 * Controls how the theme variant (light/dark) is determined.
 */
enum class ThemeMode {
    /** Follow system dark mode setting */
    SYSTEM,
    /** Always use light theme */
    LIGHT,
    /** Always use dark theme */
    DARK
}
