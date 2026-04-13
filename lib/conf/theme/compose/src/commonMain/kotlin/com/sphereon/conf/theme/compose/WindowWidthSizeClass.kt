package com.sphereon.conf.theme.compose

/**
 * Material 3 window width size classes for responsive layouts.
 *
 * Breakpoints follow the official M3 spec:
 * - **Compact**: width < 600dp (phones)
 * - **Medium**: 600dp <= width < 840dp (tablets, foldables)
 * - **Expanded**: width >= 840dp (desktops, large tablets)
 */
enum class WindowWidthSizeClass {
    Compact,
    Medium,
    Expanded
}
