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

import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.token.TokenKeyConstants

/**
 * Maps resolved theme tokens to [BorderTokens].
 */
object BorderTokenMapper {
    fun toBorderTokens(theme: ResolvedTheme): BorderTokens {
        val tokens = theme.tokens
        return BorderTokens(
            widthNone = tokens[TokenKeyConstants.BORDER_WIDTH_NONE] ?: "0px",
            widthThin = tokens[TokenKeyConstants.BORDER_WIDTH_THIN] ?: "1px",
            widthMedium = tokens[TokenKeyConstants.BORDER_WIDTH_MEDIUM] ?: "2px",
            widthThick = tokens[TokenKeyConstants.BORDER_WIDTH_THICK] ?: "4px",
        )
    }
}
