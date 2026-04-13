package com.sphereon.conf.theme.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit

/**
 * JVM (Desktop) implementation of platform accessibility detection.
 *
 * Reads AWT desktop properties:
 * - `win.highContrast.on` → [AccessibilityState.isHighContrast]
 * - `win.animation.enabled` (false) → [AccessibilityState.prefersReducedMotion]
 *
 * Falls back to defaults (false) on non-Windows or headless environments.
 */
@Composable
actual fun rememberPlatformAccessibilityState(): AccessibilityState {
    return remember {
        try {
            val toolkit = Toolkit.getDefaultToolkit()
            val highContrast = toolkit.getDesktopProperty("win.highContrast.on") as? Boolean ?: false
            val animationEnabled = toolkit.getDesktopProperty("win.animation.enabled") as? Boolean
            val reducedMotion = animationEnabled == false
            AccessibilityState(
                isHighContrast = highContrast,
                prefersReducedMotion = reducedMotion
            )
        } catch (_: Exception) {
            AccessibilityState()
        }
    }
}
