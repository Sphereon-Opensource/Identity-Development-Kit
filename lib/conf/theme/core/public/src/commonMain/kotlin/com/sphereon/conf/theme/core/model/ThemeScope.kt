package com.sphereon.conf.theme.core.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * The hierarchical scope at which a theme definition applies.
 * Lower scopes override higher ones during resolution:
 * SYSTEM < APP < TENANT < PRINCIPAL
 */
@JsExportCompat
@Serializable
enum class ThemeScope {
    /** Built-in system defaults (lowest priority) */
    SYSTEM,

    /** Application-wide theme */
    APP,

    /** Tenant-specific overrides */
    TENANT,

    /** Per-principal (user) overrides (highest priority) */
    PRINCIPAL
}
