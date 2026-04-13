package com.sphereon.conf.theme.core.model

import kotlinx.serialization.Serializable

/**
 * Web-specific branding metadata for browser-based consumers.
 * Includes URLs for custom CSS, font stylesheets, and asset base paths.
 */
@Serializable
data class WebBrandingMetadata(
    val customCssUrl: String? = null,
    val fontStylesheetUrls: List<String>? = null,
    val assetBaseUrl: String? = null
)
