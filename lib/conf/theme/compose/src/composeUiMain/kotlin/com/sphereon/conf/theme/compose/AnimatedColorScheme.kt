/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.conf.theme.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * Animates all M3 color roles in [target] using [animateColorAsState],
 * creating a smooth transition when themes change at runtime.
 *
 * Duration and easing are read from [MotionTokens] (via [LocalMotionTokens]),
 * making them tenant-customizable through the standard theme resolution chain.
 *
 * Usage:
 * ```kotlin
 * DefaultTheme(resolvedTheme, animateTransition = true) { ... }
 * ```
 */
@Composable
fun animateThemeColorScheme(
    target: ColorScheme,
    motionTokens: MotionTokens = LocalMotionTokens.current,
): ColorScheme {
    val easingSpec = motionTokens.themeTransitionEasing
    val easing = CubicBezierEasing(easingSpec.x1, easingSpec.y1, easingSpec.x2, easingSpec.y2)
    val spec = tween<androidx.compose.ui.graphics.Color>(motionTokens.themeTransitionDuration, easing = easing)

    return target.copy(
        primary = animateColorAsState(target.primary, spec).value,
        onPrimary = animateColorAsState(target.onPrimary, spec).value,
        primaryContainer = animateColorAsState(target.primaryContainer, spec).value,
        onPrimaryContainer = animateColorAsState(target.onPrimaryContainer, spec).value,
        secondary = animateColorAsState(target.secondary, spec).value,
        onSecondary = animateColorAsState(target.onSecondary, spec).value,
        secondaryContainer = animateColorAsState(target.secondaryContainer, spec).value,
        onSecondaryContainer = animateColorAsState(target.onSecondaryContainer, spec).value,
        tertiary = animateColorAsState(target.tertiary, spec).value,
        onTertiary = animateColorAsState(target.onTertiary, spec).value,
        tertiaryContainer = animateColorAsState(target.tertiaryContainer, spec).value,
        onTertiaryContainer = animateColorAsState(target.onTertiaryContainer, spec).value,
        error = animateColorAsState(target.error, spec).value,
        onError = animateColorAsState(target.onError, spec).value,
        errorContainer = animateColorAsState(target.errorContainer, spec).value,
        onErrorContainer = animateColorAsState(target.onErrorContainer, spec).value,
        surface = animateColorAsState(target.surface, spec).value,
        onSurface = animateColorAsState(target.onSurface, spec).value,
        surfaceVariant = animateColorAsState(target.surfaceVariant, spec).value,
        onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, spec).value,
        background = animateColorAsState(target.background, spec).value,
        onBackground = animateColorAsState(target.onBackground, spec).value,
        outline = animateColorAsState(target.outline, spec).value,
        outlineVariant = animateColorAsState(target.outlineVariant, spec).value,
        inverseSurface = animateColorAsState(target.inverseSurface, spec).value,
        inverseOnSurface = animateColorAsState(target.inverseOnSurface, spec).value,
        inversePrimary = animateColorAsState(target.inversePrimary, spec).value,
        scrim = animateColorAsState(target.scrim, spec).value,
        surfaceContainer = animateColorAsState(target.surfaceContainer, spec).value,
        surfaceContainerHigh = animateColorAsState(target.surfaceContainerHigh, spec).value,
        surfaceContainerHighest = animateColorAsState(target.surfaceContainerHighest, spec).value,
        surfaceContainerLow = animateColorAsState(target.surfaceContainerLow, spec).value,
        surfaceContainerLowest = animateColorAsState(target.surfaceContainerLowest, spec).value,
    )
}
