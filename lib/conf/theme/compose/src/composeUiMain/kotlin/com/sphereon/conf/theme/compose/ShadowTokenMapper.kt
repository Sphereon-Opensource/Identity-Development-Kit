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
 * Maps resolved theme tokens to [ShadowTokens].
 * Shadow values are CSS box-shadow strings; Compose consumers parse them as needed.
 */
object ShadowTokenMapper {
    fun toShadowTokens(theme: ResolvedTheme): ShadowTokens {
        val tokens = theme.tokens
        return ShadowTokens(
            none = tokens[TokenKeyConstants.SHADOW_ELEVATION_NONE] ?: "none",
            xs = tokens[TokenKeyConstants.SHADOW_ELEVATION_XS] ?: ShadowTokens.Default.xs,
            sm = tokens[TokenKeyConstants.SHADOW_ELEVATION_SM] ?: ShadowTokens.Default.sm,
            md = tokens[TokenKeyConstants.SHADOW_ELEVATION_MD] ?: ShadowTokens.Default.md,
            lg = tokens[TokenKeyConstants.SHADOW_ELEVATION_LG] ?: ShadowTokens.Default.lg,
            xl = tokens[TokenKeyConstants.SHADOW_ELEVATION_XL] ?: ShadowTokens.Default.xl,
            xxl = tokens[TokenKeyConstants.SHADOW_ELEVATION_XXL] ?: ShadowTokens.Default.xxl,
        )
    }
}
