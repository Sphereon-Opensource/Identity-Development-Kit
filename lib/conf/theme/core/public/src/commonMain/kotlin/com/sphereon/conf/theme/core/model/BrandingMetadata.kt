package com.sphereon.conf.theme.core.model

import kotlinx.serialization.Serializable

/**
 * Structured branding metadata extracted from resolved theme tokens.
 * Provides typed access to branding-specific values.
 */
@Serializable
data class BrandingMetadata(
    val appName: String? = null,
    val primaryColor: String? = null,
    val logoUrl: String? = null,
    val logoDarkUrl: String? = null,
    val faviconUrl: String? = null,
    val fontResourceId: String? = null,
    val logoResourceId: String? = null,
    val logoDarkResourceId: String? = null
)
