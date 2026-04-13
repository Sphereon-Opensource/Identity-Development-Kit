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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.palette.PaletteMapping
import com.sphereon.conf.theme.core.palette.ThemeColorConfig

/**
 * Default theme wrapper that provides M3 MaterialTheme from a [ResolvedTheme].
 *
 * Maps resolved tokens to M3 ColorScheme, Typography, and provides raw token
 * access via [LocalThemeTokens], plus elevation via [LocalElevationTokens].
 *
 * @param resolvedTheme The resolved theme to apply
 * @param animateTransition Whether to animate color transitions (overridden to false if reduced motion is preferred)
 * @param adaptToWindowSize Whether to adapt typography to window size class (requires BoxWithConstraints)
 * @param accessibilityState Accessibility preferences; null means auto-detect from the platform
 *
 * Usage:
 * ```kotlin
 * DefaultTheme(colorConfig = ThemeColorConfig.SeedColor("#1a73e8"), appName = "MyApp") { MyApp() }
 * ```
 */
@Composable
fun DefaultTheme(
    resolvedTheme: ResolvedTheme,
    animateTransition: Boolean = false,
    adaptToWindowSize: Boolean = false,
    accessibilityState: AccessibilityState? = null,
    content: @Composable () -> Unit,
) {
    val a11y = accessibilityState ?: rememberPlatformAccessibilityState()
    val effectiveAnimate = animateTransition && !a11y.prefersReducedMotion

    // Apply high-contrast boost if OS requests it
    val themedTokens =
        if (a11y.isHighContrast) {
            HighContrastTokenResolver.resolve(resolvedTheme)
        } else {
            resolvedTheme
        }

    if (adaptToWindowSize) {
        AdaptiveDefaultThemeContent(
            resolvedTheme = themedTokens,
            animateTransition = effectiveAnimate,
            accessibilityState = a11y,
            content = content,
        )
    } else {
        DefaultThemeContent(
            resolvedTheme = themedTokens,
            animateTransition = effectiveAnimate,
            accessibilityState = a11y,
            windowWidthSizeClass = null,
            content = content,
        )
    }
}

/**
 * IDK-level DefaultTheme with two usage patterns:
 *
 * **1. Zero-config** (system defaults):
 * ```kotlin
 * DefaultTheme { MyApp() }
 * ```
 *
 * **2. Color config** (design system palette or seed):
 * ```kotlin
 * DefaultTheme(
 *     colorConfig = ThemeColorConfig.Hybrid(
 *         palettes = DesignSystemPalette(brand = PaletteScale(...)),
 *     ),
 *     appName = "MyApp",
 * ) { MyApp() }
 * ```
 *
 * This is the standalone IDK variant -- no VDX API connection needed.
 * For VDX-connected theming with tenant-aware resolution, use the
 * EDK `edk-theme-compose` module instead.
 */
@Composable
fun DefaultTheme(
    colorConfig: ThemeColorConfig? = null,
    paletteMapping: PaletteMapping? = null,
    appName: String? = null,
    logoUrl: String? = null,
    mode: ThemeMode = ThemeMode.SYSTEM,
    animateTransition: Boolean = false,
    adaptToWindowSize: Boolean = false,
    accessibilityState: AccessibilityState? = null,
    content: @Composable () -> Unit,
) {
    val isDark =
        when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val variant =
        if (isDark) {
            ThemeVariant.DARK
        } else {
            ThemeVariant.LIGHT
        }

    val resolvedTheme =
        when {
            // Color config provided: resolve via DesignSystemPaletteResolver
            colorConfig != null -> {
                ClientPaletteResolver.resolve(
                    colorConfig = colorConfig,
                    appName = appName,
                    logoUrl = logoUrl,
                    variant = variant,
                    paletteMapping = paletteMapping,
                )
            }

            // Zero-config
            else -> {
                if (isDark) {
                    DefaultThemeDefaults.dark
                } else {
                    DefaultThemeDefaults.light
                }
            }
        }

    DefaultTheme(
        resolvedTheme = resolvedTheme,
        animateTransition = animateTransition,
        adaptToWindowSize = adaptToWindowSize,
        accessibilityState = accessibilityState,
        content = content,
    )
}

/**
 * Internal composable that provides theme content with all CompositionLocals.
 */
@Composable
internal fun DefaultThemeContent(
    resolvedTheme: ResolvedTheme,
    animateTransition: Boolean,
    accessibilityState: AccessibilityState,
    windowWidthSizeClass: WindowWidthSizeClass?,
    content: @Composable () -> Unit,
) {
    val branding = BrandingTokens.fromTokens(resolvedTheme.tokens)
    val customFont = ThemeResourceMapper.resolveFontFamily(branding)
    val rawColorScheme = ThemeTokenMapper.toColorScheme(resolvedTheme)
    val typography = TypographyTokenMapper.toTypography(resolvedTheme, baseFontFamily = customFont)
    val elevation = ElevationTokenMapper.toElevationTokens(resolvedTheme)
    val spacing = SpacingTokenMapper.toSpacingTokens(resolvedTheme)
    val shadows = ShadowTokenMapper.toShadowTokens(resolvedTheme)
    val borders = BorderTokenMapper.toBorderTokens(resolvedTheme)
    val motion =
        if (accessibilityState.prefersReducedMotion) {
            MotionTokens.Zero
        } else {
            MotionTokenMapper.toMotionTokens(resolvedTheme)
        }
    val colorScheme =
        if (animateTransition) {
            animateThemeColorScheme(rawColorScheme, motion)
        } else {
            rawColorScheme
        }
    val paletteTokens = PaletteTokens.fromTokens(resolvedTheme.tokens)

    CompositionLocalProvider(
        LocalThemeTokens provides resolvedTheme.tokens,
        LocalResolvedTheme provides resolvedTheme,
        LocalThemeVariant provides resolvedTheme.variant,
        LocalElevationTokens provides elevation,
        LocalBrandingTokens provides branding,
        LocalAccessibilityState provides accessibilityState,
        LocalWindowWidthSizeClass provides (windowWidthSizeClass ?: WindowWidthSizeClass.Compact),
        LocalMotionTokens provides motion,
        LocalSpacingTokens provides spacing,
        LocalShadowTokens provides shadows,
        LocalBorderTokens provides borders,
        LocalPaletteTokens provides paletteTokens,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}
