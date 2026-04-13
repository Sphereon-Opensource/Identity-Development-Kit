package com.sphereon.conf.theme.core.model

import kotlinx.serialization.Serializable

/**
 * Configuration for media asset policy.
 * Controls size limits and caching for theme media assets (logos, favicons, fonts).
 */
@Serializable
data class MediaPolicyConfig(
    val maxAssetSizeBytes: Int = DEFAULT_MAX_ASSET_SIZE_BYTES,
    val cacheMaxAgeSeconds: Int = DEFAULT_CACHE_MAX_AGE_SECONDS
) {
    companion object {
        const val DEFAULT_MAX_ASSET_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
        const val DEFAULT_CACHE_MAX_AGE_SECONDS = 3600 // 1 hour
    }
}
