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

import androidx.compose.runtime.Immutable
import com.sphereon.conf.theme.core.token.TokenKeyConstants

/**
 * Structured branding tokens extracted from the flat token map.
 *
 * Provides typed access to branding-specific values (app name, logos, fonts)
 * that don't map directly to M3 color/typography roles.
 */
@Immutable
data class BrandingTokens(
    val appName: String = "Sphereon",
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val logoUrl: String? = null,
    val logoDarkUrl: String? = null,
    val faviconUrl: String? = null,
    val fontResourceId: String? = null,
    val logoResourceId: String? = null,
    val logoDarkResourceId: String? = null,
) {
    companion object {
        val Default = BrandingTokens()

        /**
         * Extract branding tokens from the flat resolved token map.
         */
        fun fromTokens(tokens: Map<String, String>): BrandingTokens =
            BrandingTokens(
                appName = tokens[TokenKeyConstants.BRANDING_APP_NAME] ?: Default.appName,
                primaryColor = tokens[TokenKeyConstants.BRANDING_PRIMARY_COLOR],
                secondaryColor = tokens[TokenKeyConstants.BRANDING_SECONDARY_COLOR],
                logoUrl = tokens[TokenKeyConstants.BRANDING_LOGO_URL],
                logoDarkUrl = tokens[TokenKeyConstants.BRANDING_LOGO_DARK_URL],
                faviconUrl = tokens[TokenKeyConstants.BRANDING_FAVICON_URL],
                fontResourceId = tokens[TokenKeyConstants.BRANDING_FONT_RESOURCE_ID],
                logoResourceId = tokens[TokenKeyConstants.BRANDING_LOGO_RESOURCE_ID],
                logoDarkResourceId = tokens[TokenKeyConstants.BRANDING_LOGO_DARK_RESOURCE_ID],
            )
    }
}
