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

package com.sphereon.conf.theme.web

import com.sphereon.conf.theme.core.model.BrandingMetadata
import com.sphereon.conf.theme.core.token.TokenKeyConstants
import com.sphereon.core.compat.JsExportCompat

/**
 * Extracts structured branding from a flat token map.
 * Mirrors the Compose SDK's BrandingTokens but for web consumers.
 */
@JsExportCompat
object WebBrandingTokens {
    /**
     * Extract BrandingMetadata from a flat IDK token map.
     */
    fun extract(tokens: Map<String, String>): BrandingMetadata =
        BrandingMetadata(
            appName = tokens[TokenKeyConstants.BRANDING_APP_NAME],
            primaryColor = tokens[TokenKeyConstants.BRANDING_PRIMARY_COLOR],
            logoUrl = tokens[TokenKeyConstants.BRANDING_LOGO_URL],
            logoDarkUrl = tokens[TokenKeyConstants.BRANDING_LOGO_DARK_URL],
            faviconUrl = tokens[TokenKeyConstants.BRANDING_FAVICON_URL],
            fontResourceId = tokens[TokenKeyConstants.BRANDING_FONT_RESOURCE_ID],
            logoResourceId = tokens[TokenKeyConstants.BRANDING_LOGO_RESOURCE_ID],
            logoDarkResourceId = tokens[TokenKeyConstants.BRANDING_LOGO_DARK_RESOURCE_ID],
        )
}
