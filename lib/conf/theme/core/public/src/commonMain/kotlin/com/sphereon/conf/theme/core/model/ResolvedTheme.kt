package com.sphereon.conf.theme.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * The fully resolved, flattened theme ready for client consumption.
 * All token references have been expanded and all scope layers merged.
 *
 * @property tokens Flat map of token key -> resolved value
 * @property resolvedAt Timestamp when this resolution was computed
 * @property variant The variant that was resolved (null if default)
 * @property layerCount Number of definition layers that were merged
 * @property etag Content hash for conditional HTTP caching
 * @property fallback Whether system fallback defaults were used
 * @property tenantId The tenant this theme was resolved for
 * @property appId The app this theme was resolved for (null = tenant default)
 * @property branding Structured branding metadata extracted from resolved tokens
 * @property appliedLayers Names of the layers that contributed to this resolution
 * @property web Web-specific branding metadata (custom CSS URLs, font stylesheets)
 */
@Serializable
data class ResolvedTheme(
    val tokens: Map<String, String>,
    val resolvedAt: Instant,
    val variant: ThemeVariant? = null,
    val layerCount: Int = 0,
    val etag: String? = null,
    val fallback: Boolean = false,
    val tenantId: String? = null,
    val appId: String? = null,
    val branding: BrandingMetadata? = null,
    val appliedLayers: List<String>? = null,
    val web: WebBrandingMetadata? = null
)
