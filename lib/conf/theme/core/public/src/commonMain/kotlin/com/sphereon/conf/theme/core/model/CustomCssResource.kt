package com.sphereon.conf.theme.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A managed custom CSS resource for per-tenant/per-app custom styling.
 */
@Serializable
data class CustomCssResource(
    val id: String,
    val tenantId: String,
    val appId: String? = null,
    val css: String,
    val version: Long = 1,
    val contentHash: String,
    val publicationMode: CssPublicationMode = CssPublicationMode.HOSTED,
    val publishedUrl: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null
)

@Serializable
enum class CssPublicationMode {
    HOSTED,
    EXTERNAL
}
