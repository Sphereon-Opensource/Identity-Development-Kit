package com.sphereon.conf.theme.web

import com.sphereon.conf.theme.core.token.TokenKeyConstants
import com.sphereon.core.compat.JsExportCompat

/**
 * Legacy CSS variable aliases for backwards compatibility with existing portal CSS modules.
 * Maps old portal variable names to IDK token values.
 *
 * Also includes web-wallet semantic aliases (derived from IDK tokens).
 */
@JsExportCompat
object CssLegacyAliases {

    /**
     * Generate legacy alias CSS variables from a flat IDK token map.
     * Returns a map of CSS variable name → value.
     */
    fun generateAliases(tokens: Map<String, String>): Map<String, String> {
        val vars = mutableMapOf<String, String>()

        // Direct legacy mappings
        tokens[TokenKeyConstants.COLOR_BACKGROUND]?.let { vars["--color-background"] = it }
        tokens[TokenKeyConstants.COLOR_ON_SURFACE]?.let { vars["--color-foreground"] = it }
        tokens[TokenKeyConstants.COLOR_SURFACE]?.let { vars["--color-surface"] = it }
        tokens[TokenKeyConstants.COLOR_SURFACE_VARIANT]?.let { vars["--color-surface-variant"] = it }
        tokens[TokenKeyConstants.COLOR_PRIMARY]?.let { vars["--color-primary"] = it }
        tokens[TokenKeyConstants.COLOR_SECONDARY]?.let { vars["--color-secondary"] = it }
        tokens[TokenKeyConstants.COLOR_ERROR]?.let { vars["--color-error"] = it }
        tokens[TokenKeyConstants.COLOR_OUTLINE]?.let { vars["--color-outline"] = it }
        tokens[TokenKeyConstants.COLOR_SHADOW]?.let { vars["--color-shadow"] = it }
        tokens[TokenKeyConstants.COLOR_ON_PRIMARY]?.let { vars["--color-on-primary"] = it }
        tokens[TokenKeyConstants.COLOR_PRIMARY_CONTAINER]?.let { vars["--color-primary-light"] = it }
        tokens[TokenKeyConstants.COLOR_ON_PRIMARY_CONTAINER]?.let { vars["--color-primary-dark"] = it }

        // Shape aliases
        tokens[TokenKeyConstants.SHAPE_CORNER_EXTRA_SMALL]?.let { vars["--radius-sm"] = CssTokenMapper.convertUnit(it) }
        tokens[TokenKeyConstants.SHAPE_CORNER_SMALL]?.let { vars["--radius-md"] = CssTokenMapper.convertUnit(it) }
        tokens[TokenKeyConstants.SHAPE_CORNER_MEDIUM]?.let { vars["--radius-lg"] = CssTokenMapper.convertUnit(it) }
        tokens[TokenKeyConstants.SHAPE_CORNER_LARGE]?.let { vars["--radius-xl"] = CssTokenMapper.convertUnit(it) }
        tokens[TokenKeyConstants.SHAPE_CORNER_EXTRA_LARGE]?.let { vars["--radius-2xl"] = CssTokenMapper.convertUnit(it) }

        // Web-wallet semantic aliases
        tokens[TokenKeyConstants.COLOR_PRIMARY]?.let { pc ->
            vars["--color-primary-bg"] = CssTokenMapper.hexToRgba(pc, 0.1)
            vars["--color-text-link"] = pc
            vars["--color-border-focus"] = pc
        }
        tokens[TokenKeyConstants.COLOR_ON_SURFACE]?.let { vars["--color-text-primary"] = it }
        tokens[TokenKeyConstants.COLOR_ON_SURFACE_VARIANT]?.let { vars["--color-text-secondary"] = it }
        tokens[TokenKeyConstants.COLOR_BACKGROUND]?.let { vars["--color-bg-primary"] = it }
        tokens[TokenKeyConstants.COLOR_SURFACE_CONTAINER]?.let { vars["--color-bg-secondary"] = it }
        tokens[TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGH]?.let {
            vars["--color-bg-tertiary"] = it
            vars["--color-bg-hover"] = it
        }
        tokens[TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOW]?.let { vars["--color-bg-card"] = it }
        tokens[TokenKeyConstants.COLOR_OUTLINE]?.let { vars["--color-border-primary"] = it }
        tokens[TokenKeyConstants.COLOR_OUTLINE_VARIANT]?.let { vars["--color-border-secondary"] = it }
        tokens[TokenKeyConstants.COLOR_ERROR]?.let { vars["--color-border-error"] = it }

        // Legacy elevation → shadow.elevation aliases
        tokens[TokenKeyConstants.SHADOW_ELEVATION_NONE]?.let { vars["--elevation-none"] = it }
        tokens[TokenKeyConstants.SHADOW_ELEVATION_XS]?.let { vars["--elevation-xs"] = it }
        tokens[TokenKeyConstants.SHADOW_ELEVATION_SM]?.let { vars["--elevation-sm"] = it }
        tokens[TokenKeyConstants.SHADOW_ELEVATION_MD]?.let { vars["--elevation-md"] = it }
        tokens[TokenKeyConstants.SHADOW_ELEVATION_LG]?.let { vars["--elevation-lg"] = it }
        tokens[TokenKeyConstants.SHADOW_ELEVATION_XL]?.let { vars["--elevation-xl"] = it }

        // Legacy shape.corner* → shape.radius.* aliases
        tokens[TokenKeyConstants.SHAPE_RADIUS_SM]?.let { vars["--shape-cornerExtraSmall"] = CssTokenMapper.convertUnit(it) }
        tokens[TokenKeyConstants.SHAPE_RADIUS_MD]?.let { vars["--shape-cornerSmall"] = CssTokenMapper.convertUnit(it) }
        tokens[TokenKeyConstants.SHAPE_RADIUS_LG]?.let { vars["--shape-cornerMedium"] = CssTokenMapper.convertUnit(it) }
        tokens[TokenKeyConstants.SHAPE_RADIUS_XL]?.let { vars["--shape-cornerLarge"] = CssTokenMapper.convertUnit(it) }
        tokens[TokenKeyConstants.SHAPE_RADIUS_XXXL]?.let { vars["--shape-cornerExtraLarge"] = CssTokenMapper.convertUnit(it) }

        return vars
    }
}
