package com.sphereon.conf.theme.core.model

import kotlinx.serialization.Serializable

/**
 * Configuration for CSS policy validation.
 * Controls what is allowed in custom CSS overrides.
 */
@Serializable
data class CssPolicyConfig(
    val maxSizeBytes: Int = 50_000,
    val allowedProperties: Set<String>? = null,
    val blockedSelectors: Set<String> = setOf("script", "iframe", "object", "embed", "form")
)
