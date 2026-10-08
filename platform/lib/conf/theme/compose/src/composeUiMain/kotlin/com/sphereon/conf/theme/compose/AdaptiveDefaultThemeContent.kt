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
    content: @Composable () -> Unit,
) {
    BoxWithConstraints {
        val widthSizeClass =
            when {
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
            content = content,
        )
    }
}
