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
    val logoDarkResourceId: String? = null
) {
    companion object {
        val Default = BrandingTokens()

        /**
         * Extract branding tokens from the flat resolved token map.
         */
        fun fromTokens(tokens: Map<String, String>): BrandingTokens = BrandingTokens(
            appName = tokens[TokenKeyConstants.BRANDING_APP_NAME] ?: Default.appName,
            primaryColor = tokens[TokenKeyConstants.BRANDING_PRIMARY_COLOR],
            secondaryColor = tokens[TokenKeyConstants.BRANDING_SECONDARY_COLOR],
            logoUrl = tokens[TokenKeyConstants.BRANDING_LOGO_URL],
            logoDarkUrl = tokens[TokenKeyConstants.BRANDING_LOGO_DARK_URL],
            faviconUrl = tokens[TokenKeyConstants.BRANDING_FAVICON_URL],
            fontResourceId = tokens[TokenKeyConstants.BRANDING_FONT_RESOURCE_ID],
            logoResourceId = tokens[TokenKeyConstants.BRANDING_LOGO_RESOURCE_ID],
            logoDarkResourceId = tokens[TokenKeyConstants.BRANDING_LOGO_DARK_RESOURCE_ID]
        )
    }
}
