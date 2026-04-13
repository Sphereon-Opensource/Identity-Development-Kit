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
    size: Dp = 48.dp
) {
    val branding = LocalBrandingTokens.current
    val variant = LocalThemeVariant.current
    val isDark = variant == ThemeVariant.DARK

    val painter = ThemeResourceMapper.resolveLogoPainter(branding, isDark)

    if (painter != null) {
        Image(
            painter = painter(),
            contentDescription = branding.appName,
            modifier = modifier.size(size)
        )
    } else {
        Text(
            text = branding.appName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier
        )
    }
}
