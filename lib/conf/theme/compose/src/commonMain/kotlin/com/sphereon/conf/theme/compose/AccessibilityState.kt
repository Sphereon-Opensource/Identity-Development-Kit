package com.sphereon.conf.theme.compose

import androidx.compose.runtime.Immutable

/**
 * Accessibility preferences detected from the platform.
 *
 * @property isHighContrast Whether the OS has high contrast mode enabled
 * @property prefersReducedMotion Whether the OS requests reduced motion/animations
 */
@Immutable
data class AccessibilityState(
    val isHighContrast: Boolean = false,
    val prefersReducedMotion: Boolean = false
)
