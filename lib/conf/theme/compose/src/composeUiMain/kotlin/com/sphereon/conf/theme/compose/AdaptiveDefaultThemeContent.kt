package com.sphereon.conf.theme.compose

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.core.model.ResolvedTheme

/**
 * Wraps theme content in [BoxWithConstraints] to detect the window width class
 * and apply [AdaptiveTokenResolver] for responsive typography scaling.
 */
@Composable
internal fun AdaptiveDefaultThemeContent(
    resolvedTheme: ResolvedTheme,
    animateTransition: Boolean,
    accessibilityState: AccessibilityState,
    content: @Composable () -> Unit
) {
    BoxWithConstraints {
        val widthSizeClass = when {
            maxWidth < 600.dp -> WindowWidthSizeClass.Compact
            maxWidth < 840.dp -> WindowWidthSizeClass.Medium
            else -> WindowWidthSizeClass.Expanded
        }

        val adaptedTheme = AdaptiveTokenResolver.resolve(resolvedTheme, widthSizeClass)

        DefaultThemeContent(
            resolvedTheme = adaptedTheme,
            animateTransition = animateTransition,
            accessibilityState = accessibilityState,
            windowWidthSizeClass = widthSizeClass,
            content = content
        )
    }
}
