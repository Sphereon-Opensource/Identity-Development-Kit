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

import com.sphereon.conf.theme.core.defaults.SystemDefaults
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.token.TokenFlattener
import com.sphereon.conf.theme.core.token.TokenReferenceResolver
import kotlin.time.Clock

/**
 * Pre-built [ResolvedTheme] instances from [SystemDefaults].
 *
 * Provides offline/bundled default branding without needing a network call.
 * Used as fallback when [ThemeClient] fails or while loading.
 */
object DefaultThemeDefaults {
    private const val HASH_MULTIPLIER = 31

    val light: ResolvedTheme by lazy { buildResolvedTheme(SystemDefaults.baseline, ThemeVariant.LIGHT) }
    val dark: ResolvedTheme by lazy { buildResolvedTheme(SystemDefaults.baselineDark, ThemeVariant.DARK) }
    val highContrastLight: ResolvedTheme by lazy { buildResolvedTheme(SystemDefaults.baselineHighContrastLight, ThemeVariant.HIGH_CONTRAST_LIGHT) }
    val highContrastDark: ResolvedTheme by lazy { buildResolvedTheme(SystemDefaults.baselineHighContrastDark, ThemeVariant.HIGH_CONTRAST_DARK) }

    private fun buildResolvedTheme(
        definition: com.sphereon.conf.theme.core.model.ThemeDefinition,
        variant: ThemeVariant,
    ): ResolvedTheme {
        val merged = TokenFlattener.merge(listOf(definition))
        val resolved = TokenReferenceResolver.resolve(merged)
        val sortedHash =
            resolved.entries
                .sortedBy { it.key }
                .fold(0) { acc, (k, v) -> acc * HASH_MULTIPLIER + k.hashCode() + v.hashCode() }
                .toUInt()
        return ResolvedTheme(
            tokens = resolved,
            resolvedAt = Clock.System.now(),
            variant = variant,
            layerCount = 1,
            etag = "W/\"$sortedHash\"",
            fallback = true,
        )
    }
}
