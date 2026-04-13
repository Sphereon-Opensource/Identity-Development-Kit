package com.sphereon.conf.theme.compose

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal providing elevation tokens.
 *
 * Declared in the composeUiMain source set because [ElevationTokens] uses
 * `androidx.compose.ui.unit.Dp` which is not available on the JS target.
 */
val LocalElevationTokens = staticCompositionLocalOf { ElevationTokens.Default }
