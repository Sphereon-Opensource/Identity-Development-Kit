package com.sphereon.conf.theme.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

/**
 * JS (Browser) implementation of platform accessibility detection.
 *
 * Uses CSS media queries via `window.matchMedia`:
 * - `prefers-reduced-motion: reduce` → [AccessibilityState.prefersReducedMotion]
 * - `prefers-contrast: more` → [AccessibilityState.isHighContrast]
 */
@Composable
actual fun rememberPlatformAccessibilityState(): AccessibilityState {
    return remember {
        try {
            val reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches
            val highContrast = window.matchMedia("(prefers-contrast: more)").matches
            AccessibilityState(
                isHighContrast = highContrast,
                prefersReducedMotion = reducedMotion
            )
        } catch (_: Throwable) {
            AccessibilityState()
        }
    }
}
