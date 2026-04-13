package com.sphereon.conf.theme.compose

import androidx.compose.runtime.Composable

/**
 * Platform-specific detection of accessibility preferences.
 * Returns an [AccessibilityState] reflecting the OS-level settings.
 */
@Composable
expect fun rememberPlatformAccessibilityState(): AccessibilityState
