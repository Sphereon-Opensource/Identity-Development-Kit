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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.core.model.ThemeVariant

/**
 * Brand logo composable that automatically resolves the correct logo
 * from [LocalBrandingTokens] and [ThemeResourceMapper].
 *
 * Priority:
 * 1. Registered drawable painter (via [ThemeResourceMapper])
 * 2. App name text fallback
 *
 * Automatically selects light/dark variant based on [LocalThemeVariant].
 */
@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val branding = LocalBrandingTokens.current
    val variant = LocalThemeVariant.current
    val isDark = variant == ThemeVariant.DARK

    val painter = ThemeResourceMapper.resolveLogoPainter(branding, isDark)

    if (painter != null) {
        Image(
            painter = painter(),
            contentDescription = branding.appName,
            modifier = modifier.size(size),
        )
    } else {
        Text(
            text = branding.appName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )
    }
}
