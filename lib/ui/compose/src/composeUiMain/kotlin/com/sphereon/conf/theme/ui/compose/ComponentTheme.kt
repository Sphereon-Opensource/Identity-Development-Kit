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

package com.sphereon.conf.theme.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.sphereon.conf.theme.compose.LocalThemeTokens
import com.sphereon.conf.theme.ui.compose.tokens.ComponentTokenMapper
import com.sphereon.conf.theme.ui.compose.tokens.LocalBadgeTokens
import com.sphereon.conf.theme.ui.compose.tokens.LocalBlobExplorerTokens
import com.sphereon.conf.theme.ui.compose.tokens.LocalButtonTokens
import com.sphereon.conf.theme.ui.compose.tokens.LocalCardTokens
import com.sphereon.conf.theme.ui.compose.tokens.LocalCheckboxTokens
import com.sphereon.conf.theme.ui.compose.tokens.LocalInputTokens
import com.sphereon.conf.theme.ui.compose.tokens.LocalModalTokens
import com.sphereon.conf.theme.ui.compose.tokens.LocalRadioTokens
import com.sphereon.conf.theme.ui.compose.tokens.LocalSelectTokens
import com.sphereon.conf.theme.ui.compose.tokens.LocalTabTokens
import com.sphereon.conf.theme.ui.compose.tokens.LocalToastTokens

/**
 * Provides graph tokens derived from the current theme's raw token map.
 * Wrap your content with this composable to make graph tokens available
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
fun ComponentTheme(content: @Composable () -> Unit) {
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
