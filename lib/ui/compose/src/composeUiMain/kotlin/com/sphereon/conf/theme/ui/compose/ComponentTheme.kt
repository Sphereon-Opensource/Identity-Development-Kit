package com.sphereon.conf.theme.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.sphereon.conf.theme.compose.LocalThemeTokens
import com.sphereon.conf.theme.ui.compose.tokens.*

/**
 * Provides component tokens derived from the current theme's raw token map.
 * Wrap your content with this composable to make component tokens available
 * via CompositionLocals.
 *
 * Usage:
 * ```kotlin
 * DefaultTheme(primaryColor = "#1a73e8") {
 *     ComponentTheme {
 *         Button(onClick = {}) { Text("Click me") }
 *     }
 * }
 * ```
 */
@Composable
fun ComponentTheme(
    content: @Composable () -> Unit
) {
    val tokens = LocalThemeTokens.current

    CompositionLocalProvider(
        LocalButtonTokens provides ComponentTokenMapper.toButtonTokens(tokens),
        LocalInputTokens provides ComponentTokenMapper.toInputTokens(tokens),
        LocalCheckboxTokens provides ComponentTokenMapper.toCheckboxTokens(tokens),
        LocalRadioTokens provides ComponentTokenMapper.toRadioTokens(tokens),
        LocalCardTokens provides ComponentTokenMapper.toCardTokens(tokens),
        LocalBadgeTokens provides ComponentTokenMapper.toBadgeTokens(tokens),
        LocalModalTokens provides ComponentTokenMapper.toModalTokens(tokens),
        LocalTabTokens provides ComponentTokenMapper.toTabTokens(tokens),
        LocalSelectTokens provides ComponentTokenMapper.toSelectTokens(tokens),
        LocalToastTokens provides ComponentTokenMapper.toToastTokens(tokens),
        LocalBlobExplorerTokens provides ComponentTokenMapper.toBlobExplorerTokens(tokens),
    ) {
        content()
    }
}
