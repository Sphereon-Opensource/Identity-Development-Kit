package com.sphereon.conf.theme.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityDarkerSystemColorsEnabled
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/**
 * iOS implementation of platform accessibility detection.
 *
 * Reads UIKit accessibility properties:
 * - [UIAccessibilityIsReduceMotionEnabled] → [AccessibilityState.prefersReducedMotion]
 * - [UIAccessibilityDarkerSystemColorsEnabled] → [AccessibilityState.isHighContrast]
 */
@Composable
actual fun rememberPlatformAccessibilityState(): AccessibilityState {
    return remember {
        AccessibilityState(
            isHighContrast = UIAccessibilityDarkerSystemColorsEnabled(),
            prefersReducedMotion = UIAccessibilityIsReduceMotionEnabled()
        )
    }
}
