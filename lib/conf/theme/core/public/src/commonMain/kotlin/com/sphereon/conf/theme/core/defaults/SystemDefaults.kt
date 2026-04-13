package com.sphereon.conf.theme.core.defaults

import com.sphereon.conf.theme.core.model.ThemeDefinition
import com.sphereon.conf.theme.core.model.ThemeScope
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.token.TokenBuilder
import com.sphereon.conf.theme.core.token.TokenKeyConstants
import com.sphereon.conf.theme.core.token.buildTokens

/**
 * Built-in Material Design 3 baseline theme.
 * This is the root of the resolution chain — every resolved theme
 * starts from these system defaults.
 */
object SystemDefaults {

    /**
     * Adds all variant-independent tokens (spacing, border-width, shape.radius,
     * typography extensions, motion extensions) to a TokenBuilder.
     * Called by all three baseline definitions to avoid duplication.
     */
    private fun TokenBuilder.addVariantIndependentExtensions() {
        // ── Spacing (4px grid, 17 stops) ──────────────────────────────
        spacing(TokenKeyConstants.SPACING_0, "0px")
        spacing(TokenKeyConstants.SPACING_1, "4px")
        spacing(TokenKeyConstants.SPACING_2, "8px")
        spacing(TokenKeyConstants.SPACING_3, "12px")
        spacing(TokenKeyConstants.SPACING_4, "16px")
        spacing(TokenKeyConstants.SPACING_5, "20px")
        spacing(TokenKeyConstants.SPACING_6, "24px")
        spacing(TokenKeyConstants.SPACING_8, "32px")
        spacing(TokenKeyConstants.SPACING_10, "40px")
        spacing(TokenKeyConstants.SPACING_12, "48px")
        spacing(TokenKeyConstants.SPACING_14, "56px")
        spacing(TokenKeyConstants.SPACING_16, "64px")
        spacing(TokenKeyConstants.SPACING_20, "80px")
        spacing(TokenKeyConstants.SPACING_24, "96px")
        spacing(TokenKeyConstants.SPACING_32, "128px")
        spacing(TokenKeyConstants.SPACING_40, "160px")
        spacing(TokenKeyConstants.SPACING_48, "192px")

        // Spacing — semantic (Tier 2, references to Tier 1)
        spacing(TokenKeyConstants.SPACING_INLINE_XS, "{spacing.1}")
        spacing(TokenKeyConstants.SPACING_INLINE_SM, "{spacing.2}")
        spacing(TokenKeyConstants.SPACING_INLINE_MD, "{spacing.4}")
        spacing(TokenKeyConstants.SPACING_INLINE_LG, "{spacing.6}")
        spacing(TokenKeyConstants.SPACING_INLINE_XL, "{spacing.8}")

        spacing(TokenKeyConstants.SPACING_STACK_XS, "{spacing.1}")
        spacing(TokenKeyConstants.SPACING_STACK_SM, "{spacing.2}")
        spacing(TokenKeyConstants.SPACING_STACK_MD, "{spacing.4}")
        spacing(TokenKeyConstants.SPACING_STACK_LG, "{spacing.6}")
        spacing(TokenKeyConstants.SPACING_STACK_XL, "{spacing.8}")

        spacing(TokenKeyConstants.SPACING_INSET_XS, "{spacing.1}")
        spacing(TokenKeyConstants.SPACING_INSET_SM, "{spacing.2}")
        spacing(TokenKeyConstants.SPACING_INSET_MD, "{spacing.4}")
        spacing(TokenKeyConstants.SPACING_INSET_LG, "{spacing.6}")
        spacing(TokenKeyConstants.SPACING_INSET_XL, "{spacing.8}")

        // ── Border Width ──────────────────────────────────────────────
        borderWidth(TokenKeyConstants.BORDER_WIDTH_NONE, "0px")
        borderWidth(TokenKeyConstants.BORDER_WIDTH_THIN, "1px")
        borderWidth(TokenKeyConstants.BORDER_WIDTH_MEDIUM, "2px")
        borderWidth(TokenKeyConstants.BORDER_WIDTH_THICK, "4px")

        // ── Shape / Border Radius (new 9-stop scale) ─────────────────
        dimension(TokenKeyConstants.SHAPE_RADIUS_NONE, "0dp")
        dimension(TokenKeyConstants.SHAPE_RADIUS_XS, "2dp")
        dimension(TokenKeyConstants.SHAPE_RADIUS_SM, "4dp")
        dimension(TokenKeyConstants.SHAPE_RADIUS_MD, "8dp")
        dimension(TokenKeyConstants.SHAPE_RADIUS_LG, "12dp")
        dimension(TokenKeyConstants.SHAPE_RADIUS_XL, "16dp")
        dimension(TokenKeyConstants.SHAPE_RADIUS_XXL, "24dp")
        dimension(TokenKeyConstants.SHAPE_RADIUS_XXXL, "28dp")
        dimension(TokenKeyConstants.SHAPE_RADIUS_FULL, "9999dp")

        // ── Typography — extended styles ──────────────────────────────
        // Display Extra Large (larger than Display Large for hero sections)
        fontFamily(TokenKeyConstants.TYPOGRAPHY_DISPLAY_EXTRA_LARGE_FONT_FAMILY, "Roboto")
        dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_EXTRA_LARGE_FONT_SIZE, "72sp")
        fontWeight(TokenKeyConstants.TYPOGRAPHY_DISPLAY_EXTRA_LARGE_FONT_WEIGHT, "400")
        dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_EXTRA_LARGE_LINE_HEIGHT, "80sp")
        dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_EXTRA_LARGE_LETTER_SPACING, "-0.5sp")

        // Body Extra Small (smaller than Body Small for captions/fine print)
        fontFamily(TokenKeyConstants.TYPOGRAPHY_BODY_EXTRA_SMALL_FONT_FAMILY, "Roboto")
        dimension(TokenKeyConstants.TYPOGRAPHY_BODY_EXTRA_SMALL_FONT_SIZE, "10sp")
        fontWeight(TokenKeyConstants.TYPOGRAPHY_BODY_EXTRA_SMALL_FONT_WEIGHT, "400")
        dimension(TokenKeyConstants.TYPOGRAPHY_BODY_EXTRA_SMALL_LINE_HEIGHT, "14sp")
        dimension(TokenKeyConstants.TYPOGRAPHY_BODY_EXTRA_SMALL_LETTER_SPACING, "0.4sp")

        // ── Motion — semantic durations ───────────────────────────────
        duration(TokenKeyConstants.MOTION_DURATION_INSTANT, "0ms")
        duration(TokenKeyConstants.MOTION_DURATION_FAST, "100ms")
        duration(TokenKeyConstants.MOTION_DURATION_NORMAL, "250ms")
        duration(TokenKeyConstants.MOTION_DURATION_SLOW, "400ms")
        duration(TokenKeyConstants.MOTION_DURATION_SLOWER, "600ms")

        // Motion — delays
        duration(TokenKeyConstants.MOTION_DELAY_NONE, "0ms")
        duration(TokenKeyConstants.MOTION_DELAY_SHORT, "100ms")

        // Motion — additional easings
        easing(TokenKeyConstants.MOTION_EASING_BOUNCE, "cubic-bezier(0.34, 1.56, 0.64, 1)")
    }

    /** M3 baseline light theme (default purple seed #6750A4) */
    val baseline: ThemeDefinition = ThemeDefinition(
        id = "system-default",
        name = "System Default (M3 Baseline)",
        variant = ThemeVariant.LIGHT,
        scope = ThemeScope.SYSTEM,
        tokens = buildTokens {
            // Primary
            color(TokenKeyConstants.COLOR_PRIMARY, "#6750A4")
            color(TokenKeyConstants.COLOR_ON_PRIMARY, "#FFFFFF")
            color(TokenKeyConstants.COLOR_PRIMARY_CONTAINER, "#EADDFF")
            color(TokenKeyConstants.COLOR_ON_PRIMARY_CONTAINER, "#21005D")

            // Secondary
            color(TokenKeyConstants.COLOR_SECONDARY, "#625B71")
            color(TokenKeyConstants.COLOR_ON_SECONDARY, "#FFFFFF")
            color(TokenKeyConstants.COLOR_SECONDARY_CONTAINER, "#E8DEF8")
            color(TokenKeyConstants.COLOR_ON_SECONDARY_CONTAINER, "#1D192B")

            // Tertiary
            color(TokenKeyConstants.COLOR_TERTIARY, "#7D5260")
            color(TokenKeyConstants.COLOR_ON_TERTIARY, "#FFFFFF")
            color(TokenKeyConstants.COLOR_TERTIARY_CONTAINER, "#FFD8E4")
            color(TokenKeyConstants.COLOR_ON_TERTIARY_CONTAINER, "#31111D")

            // Error
            color(TokenKeyConstants.COLOR_ERROR, "#B3261E")
            color(TokenKeyConstants.COLOR_ON_ERROR, "#FFFFFF")
            color(TokenKeyConstants.COLOR_ERROR_CONTAINER, "#F9DEDC")
            color(TokenKeyConstants.COLOR_ON_ERROR_CONTAINER, "#410E0B")

            // Surface
            color(TokenKeyConstants.COLOR_SURFACE, "#FEF7FF")
            color(TokenKeyConstants.COLOR_ON_SURFACE, "#1D1B20")
            color(TokenKeyConstants.COLOR_SURFACE_VARIANT, "#E7E0EC")
            color(TokenKeyConstants.COLOR_ON_SURFACE_VARIANT, "#49454F")
            color(TokenKeyConstants.COLOR_SURFACE_CONTAINER, "#F3EDF7")
            color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGH, "#ECE6F0")
            color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGHEST, "#E6E0E9")
            color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOW, "#F7F2FA")
            color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOWEST, "#FFFFFF")

            // Background
            color(TokenKeyConstants.COLOR_BACKGROUND, "#FEF7FF")
            color(TokenKeyConstants.COLOR_ON_BACKGROUND, "#1D1B20")

            // Other
            color(TokenKeyConstants.COLOR_OUTLINE, "#79747E")
            color(TokenKeyConstants.COLOR_OUTLINE_VARIANT, "#CAC4D0")
            color(TokenKeyConstants.COLOR_INVERSE_SURFACE, "#322F35")
            color(TokenKeyConstants.COLOR_INVERSE_ON_SURFACE, "#F5EFF7")
            color(TokenKeyConstants.COLOR_INVERSE_PRIMARY, "#D0BCFF")
            color(TokenKeyConstants.COLOR_SCRIM, "#000000")
            color(TokenKeyConstants.COLOR_SHADOW, "#000000")

            // Elevation
            dimension(TokenKeyConstants.ELEVATION_NONE, "0dp")
            dimension(TokenKeyConstants.ELEVATION_XS, "1dp")
            dimension(TokenKeyConstants.ELEVATION_SM, "3dp")
            dimension(TokenKeyConstants.ELEVATION_MD, "6dp")
            dimension(TokenKeyConstants.ELEVATION_LG, "8dp")
            dimension(TokenKeyConstants.ELEVATION_XL, "12dp")

            // Typography — generic
            fontFamily(TokenKeyConstants.TYPOGRAPHY_FONT_FAMILY, "Roboto")
            fontFamily(TokenKeyConstants.TYPOGRAPHY_FONT_FAMILY_MONO, "Roboto Mono")

            // Typography — Display
            fontFamily(TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_SIZE, "57sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_LINE_HEIGHT, "64sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_LETTER_SPACING, "-0.25sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_FONT_SIZE, "45sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_LINE_HEIGHT, "52sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_LETTER_SPACING, "0sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_FONT_SIZE, "36sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_LINE_HEIGHT, "44sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_LETTER_SPACING, "0sp")

            // Typography — Headline
            fontFamily(TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_FONT_SIZE, "32sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_LINE_HEIGHT, "40sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_LETTER_SPACING, "0sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_FONT_SIZE, "28sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_LINE_HEIGHT, "36sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_LETTER_SPACING, "0sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_FONT_SIZE, "24sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_LINE_HEIGHT, "32sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_LETTER_SPACING, "0sp")

            // Typography — Title
            fontFamily(TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_FONT_SIZE, "22sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_LINE_HEIGHT, "28sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_LETTER_SPACING, "0sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_FONT_SIZE, "16sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_FONT_WEIGHT, "500")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_LINE_HEIGHT, "24sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_LETTER_SPACING, "0.15sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_FONT_SIZE, "14sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_FONT_WEIGHT, "500")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_LINE_HEIGHT, "20sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_LETTER_SPACING, "0.1sp")

            // Typography — Body
            fontFamily(TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_FONT_SIZE, "16sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_LINE_HEIGHT, "24sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_LETTER_SPACING, "0.5sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_FONT_SIZE, "14sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_LINE_HEIGHT, "20sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_LETTER_SPACING, "0.25sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_FONT_SIZE, "12sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_LINE_HEIGHT, "16sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_LETTER_SPACING, "0.4sp")

            // Typography — Label
            fontFamily(TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_FONT_SIZE, "14sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_FONT_WEIGHT, "500")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_LINE_HEIGHT, "20sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_LETTER_SPACING, "0.1sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_FONT_SIZE, "12sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_FONT_WEIGHT, "500")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_LINE_HEIGHT, "16sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_LETTER_SPACING, "0.5sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_FONT_SIZE, "11sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_FONT_WEIGHT, "500")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_LINE_HEIGHT, "16sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_LETTER_SPACING, "0.5sp")

            // Shape
            dimension(TokenKeyConstants.SHAPE_CORNER_EXTRA_SMALL, "4dp")
            dimension(TokenKeyConstants.SHAPE_CORNER_SMALL, "8dp")
            dimension(TokenKeyConstants.SHAPE_CORNER_MEDIUM, "12dp")
            dimension(TokenKeyConstants.SHAPE_CORNER_LARGE, "16dp")
            dimension(TokenKeyConstants.SHAPE_CORNER_EXTRA_LARGE, "28dp")

            // Motion — durations (M3 motion spec)
            duration(TokenKeyConstants.MOTION_DURATION_SHORT1, "50ms")
            duration(TokenKeyConstants.MOTION_DURATION_SHORT2, "100ms")
            duration(TokenKeyConstants.MOTION_DURATION_SHORT3, "150ms")
            duration(TokenKeyConstants.MOTION_DURATION_SHORT4, "200ms")
            duration(TokenKeyConstants.MOTION_DURATION_MEDIUM1, "250ms")
            duration(TokenKeyConstants.MOTION_DURATION_MEDIUM2, "300ms")
            duration(TokenKeyConstants.MOTION_DURATION_MEDIUM3, "350ms")
            duration(TokenKeyConstants.MOTION_DURATION_MEDIUM4, "400ms")
            duration(TokenKeyConstants.MOTION_DURATION_LONG1, "450ms")
            duration(TokenKeyConstants.MOTION_DURATION_LONG2, "500ms")
            duration(TokenKeyConstants.MOTION_DURATION_LONG3, "550ms")
            duration(TokenKeyConstants.MOTION_DURATION_LONG4, "600ms")

            // Motion — easing
            easing(TokenKeyConstants.MOTION_EASING_STANDARD, "cubic-bezier(0.2, 0.0, 0, 1.0)")
            easing(TokenKeyConstants.MOTION_EASING_STANDARD_DECELERATE, "cubic-bezier(0, 0, 0, 1.0)")
            easing(TokenKeyConstants.MOTION_EASING_STANDARD_ACCELERATE, "cubic-bezier(0.3, 0, 1, 1)")
            easing(TokenKeyConstants.MOTION_EASING_EMPHASIZED, "cubic-bezier(0.2, 0.0, 0, 1.0)")
            easing(TokenKeyConstants.MOTION_EASING_EMPHASIZED_DECELERATE, "cubic-bezier(0.05, 0.7, 0.1, 1.0)")
            easing(TokenKeyConstants.MOTION_EASING_EMPHASIZED_ACCELERATE, "cubic-bezier(0.3, 0.0, 0.8, 0.15)")
            easing(TokenKeyConstants.MOTION_EASING_LINEAR, "cubic-bezier(0, 0, 1, 1)")

            // Motion — theme transition
            duration(TokenKeyConstants.MOTION_THEME_TRANSITION_DURATION, "500ms")
            easing(TokenKeyConstants.MOTION_THEME_TRANSITION_EASING, "cubic-bezier(0.2, 0.0, 0, 1.0)")

            // Responsive — typography scale factors
            string(TokenKeyConstants.RESPONSIVE_SCALE_MEDIUM_DISPLAY, "1.10")
            string(TokenKeyConstants.RESPONSIVE_SCALE_MEDIUM_HEADLINE, "1.05")
            string(TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_DISPLAY, "1.20")
            string(TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_HEADLINE, "1.10")
            string(TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_TITLE, "1.05")

            // ── Variant-independent extensions ────────────────────────
            addVariantIndependentExtensions()

            // ── Shadow — light theme (real shadows, not zero) ─────────
            shadow(TokenKeyConstants.SHADOW_ELEVATION_NONE, "none")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_XS, "0 1px 2px 0 rgba(0,0,0,0.05)")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_SM, "0 1px 3px 0 rgba(0,0,0,0.1), 0 1px 2px -1px rgba(0,0,0,0.1)")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_MD, "0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -2px rgba(0,0,0,0.1)")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_LG, "0 10px 15px -3px rgba(0,0,0,0.1), 0 4px 6px -4px rgba(0,0,0,0.1)")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_XL, "0 20px 25px -5px rgba(0,0,0,0.1), 0 8px 10px -6px rgba(0,0,0,0.1)")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_XXL, "0 25px 50px -12px rgba(0,0,0,0.25)")

            // Shadow — semantic (Tier 2 references)
            shadow(TokenKeyConstants.SHADOW_SUBTLE, "{shadow.elevation.xs}")
            shadow(TokenKeyConstants.SHADOW_RAISED, "{shadow.elevation.sm}")
            shadow(TokenKeyConstants.SHADOW_FLOATING, "{shadow.elevation.lg}")
            shadow(TokenKeyConstants.SHADOW_OVERLAY, "{shadow.elevation.xl}")
            shadow(TokenKeyConstants.SHADOW_DRAMATIC, "{shadow.elevation.xxl}")

            // Shadow — state (brand-adaptive)
            shadow(TokenKeyConstants.SHADOW_STATE_FOCUS, "0 0 0 3px rgba(103,80,164,0.4)")
            shadow(TokenKeyConstants.SHADOW_STATE_ERROR, "0 0 0 3px rgba(179,38,30,0.4)")
            shadow(TokenKeyConstants.SHADOW_STATE_ACTIVE, "0 0 0 2px rgba(103,80,164,0.3)")
            shadow(TokenKeyConstants.SHADOW_STATE_SELECTED, "0 0 0 2px rgba(103,80,164,0.2)")

            // ── Color — Tier 2 semantic (light theme) ─────────────────
            // Interactive states
            color(TokenKeyConstants.COLOR_INTERACTIVE_HOVER, "#5B4399")
            color(TokenKeyConstants.COLOR_INTERACTIVE_PRESSED, "#4F378B")
            color(TokenKeyConstants.COLOR_INTERACTIVE_DISABLED, "#1D1B2014")
            color(TokenKeyConstants.COLOR_INTERACTIVE_FOCUS, "{color.primary}")

            // Feedback — success (green)
            color(TokenKeyConstants.COLOR_FEEDBACK_SUCCESS, "#1B6D30")
            color(TokenKeyConstants.COLOR_FEEDBACK_SUCCESS_CONTAINER, "#A8F5AC")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_SUCCESS, "#FFFFFF")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_SUCCESS_CONTAINER, "#002107")

            // Feedback — warning (amber)
            color(TokenKeyConstants.COLOR_FEEDBACK_WARNING, "#7C5800")
            color(TokenKeyConstants.COLOR_FEEDBACK_WARNING_CONTAINER, "#FFDEA1")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_WARNING, "#FFFFFF")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_WARNING_CONTAINER, "#271900")

            // Feedback — info (blue)
            color(TokenKeyConstants.COLOR_FEEDBACK_INFO, "#0061A4")
            color(TokenKeyConstants.COLOR_FEEDBACK_INFO_CONTAINER, "#D1E4FF")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_INFO, "#FFFFFF")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_INFO_CONTAINER, "#001D36")

            // Text semantic
            color(TokenKeyConstants.COLOR_TEXT_PRIMARY, "{color.onSurface}")
            color(TokenKeyConstants.COLOR_TEXT_SECONDARY, "{color.onSurfaceVariant}")
            color(TokenKeyConstants.COLOR_TEXT_DISABLED, "#1D1B2061")
            color(TokenKeyConstants.COLOR_TEXT_INVERSE, "{color.inverseOnSurface}")

            // Border semantic
            color(TokenKeyConstants.COLOR_BORDER_DEFAULT, "{color.outline}")
            color(TokenKeyConstants.COLOR_BORDER_STRONG, "{color.onSurface}")
            color(TokenKeyConstants.COLOR_BORDER_SUBTLE, "{color.outlineVariant}")
            color(TokenKeyConstants.COLOR_BORDER_DISABLED, "#1D1B2029")
        }
    )

    /** M3 baseline high-contrast theme — maximized contrast for accessibility */
    val baselineHighContrast: ThemeDefinition = ThemeDefinition(
        id = "system-default-high-contrast",
        name = "System Default High Contrast (M3 Baseline)",
        variant = ThemeVariant.HIGH_CONTRAST,
        scope = ThemeScope.SYSTEM,
        tokens = buildTokens {
            // Primary — darker for maximum contrast against white backgrounds
            color(TokenKeyConstants.COLOR_PRIMARY, "#3D0090")
            color(TokenKeyConstants.COLOR_ON_PRIMARY, "#FFFFFF")
            color(TokenKeyConstants.COLOR_PRIMARY_CONTAINER, "#F0E6FF")
            color(TokenKeyConstants.COLOR_ON_PRIMARY_CONTAINER, "#1A0040")

            // Secondary
            color(TokenKeyConstants.COLOR_SECONDARY, "#3B3350")
            color(TokenKeyConstants.COLOR_ON_SECONDARY, "#FFFFFF")
            color(TokenKeyConstants.COLOR_SECONDARY_CONTAINER, "#F0EAFF")
            color(TokenKeyConstants.COLOR_ON_SECONDARY_CONTAINER, "#0D0820")

            // Tertiary
            color(TokenKeyConstants.COLOR_TERTIARY, "#5C2040")
            color(TokenKeyConstants.COLOR_ON_TERTIARY, "#FFFFFF")
            color(TokenKeyConstants.COLOR_TERTIARY_CONTAINER, "#FFE8F0")
            color(TokenKeyConstants.COLOR_ON_TERTIARY_CONTAINER, "#2A0010")

            // Error
            color(TokenKeyConstants.COLOR_ERROR, "#8B0000")
            color(TokenKeyConstants.COLOR_ON_ERROR, "#FFFFFF")
            color(TokenKeyConstants.COLOR_ERROR_CONTAINER, "#FFE0E0")
            color(TokenKeyConstants.COLOR_ON_ERROR_CONTAINER, "#400000")

            // Surface — pure white/black extremes
            color(TokenKeyConstants.COLOR_SURFACE, "#FFFFFF")
            color(TokenKeyConstants.COLOR_ON_SURFACE, "#000000")
            color(TokenKeyConstants.COLOR_SURFACE_VARIANT, "#F0F0F0")
            color(TokenKeyConstants.COLOR_ON_SURFACE_VARIANT, "#1A1A1A")
            color(TokenKeyConstants.COLOR_SURFACE_CONTAINER, "#F5F5F5")
            color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGH, "#EBEBEB")
            color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGHEST, "#E0E0E0")
            color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOW, "#FAFAFA")
            color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOWEST, "#FFFFFF")

            // Background — pure white with black text
            color(TokenKeyConstants.COLOR_BACKGROUND, "#FFFFFF")
            color(TokenKeyConstants.COLOR_ON_BACKGROUND, "#000000")

            // Other — near-black outlines for visibility
            color(TokenKeyConstants.COLOR_OUTLINE, "#1A1A1A")
            color(TokenKeyConstants.COLOR_OUTLINE_VARIANT, "#3D3D3D")
            color(TokenKeyConstants.COLOR_INVERSE_SURFACE, "#000000")
            color(TokenKeyConstants.COLOR_INVERSE_ON_SURFACE, "#FFFFFF")
            color(TokenKeyConstants.COLOR_INVERSE_PRIMARY, "#D0BCFF")
            color(TokenKeyConstants.COLOR_SCRIM, "#000000")
            color(TokenKeyConstants.COLOR_SHADOW, "#000000")

            // Elevation (same as light — variant-independent)
            dimension(TokenKeyConstants.ELEVATION_NONE, "0dp")
            dimension(TokenKeyConstants.ELEVATION_XS, "1dp")
            dimension(TokenKeyConstants.ELEVATION_SM, "3dp")
            dimension(TokenKeyConstants.ELEVATION_MD, "6dp")
            dimension(TokenKeyConstants.ELEVATION_LG, "8dp")
            dimension(TokenKeyConstants.ELEVATION_XL, "12dp")

            // Typography (same as light — variant-independent)
            fontFamily(TokenKeyConstants.TYPOGRAPHY_FONT_FAMILY, "Roboto")
            fontFamily(TokenKeyConstants.TYPOGRAPHY_FONT_FAMILY_MONO, "Roboto Mono")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_SIZE, "57sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_LINE_HEIGHT, "64sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_LETTER_SPACING, "-0.25sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_FONT_SIZE, "45sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_LINE_HEIGHT, "52sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_LETTER_SPACING, "0sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_FONT_SIZE, "36sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_LINE_HEIGHT, "44sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_LETTER_SPACING, "0sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_FONT_SIZE, "32sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_LINE_HEIGHT, "40sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_LETTER_SPACING, "0sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_FONT_SIZE, "28sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_LINE_HEIGHT, "36sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_LETTER_SPACING, "0sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_FONT_SIZE, "24sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_LINE_HEIGHT, "32sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_LETTER_SPACING, "0sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_FONT_SIZE, "22sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_LINE_HEIGHT, "28sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_LETTER_SPACING, "0sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_FONT_SIZE, "16sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_FONT_WEIGHT, "500")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_LINE_HEIGHT, "24sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_LETTER_SPACING, "0.15sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_FONT_SIZE, "14sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_FONT_WEIGHT, "500")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_LINE_HEIGHT, "20sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_LETTER_SPACING, "0.1sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_FONT_SIZE, "16sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_LINE_HEIGHT, "24sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_LETTER_SPACING, "0.5sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_FONT_SIZE, "14sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_LINE_HEIGHT, "20sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_LETTER_SPACING, "0.25sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_FONT_SIZE, "12sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_LINE_HEIGHT, "16sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_LETTER_SPACING, "0.4sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_FONT_SIZE, "14sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_FONT_WEIGHT, "500")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_LINE_HEIGHT, "20sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_LETTER_SPACING, "0.1sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_FONT_SIZE, "12sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_FONT_WEIGHT, "500")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_LINE_HEIGHT, "16sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_LETTER_SPACING, "0.5sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_FONT_SIZE, "11sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_FONT_WEIGHT, "500")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_LINE_HEIGHT, "16sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_LETTER_SPACING, "0.5sp")

            // Shape (same as light — variant-independent)
            dimension(TokenKeyConstants.SHAPE_CORNER_EXTRA_SMALL, "4dp")
            dimension(TokenKeyConstants.SHAPE_CORNER_SMALL, "8dp")
            dimension(TokenKeyConstants.SHAPE_CORNER_MEDIUM, "12dp")
            dimension(TokenKeyConstants.SHAPE_CORNER_LARGE, "16dp")
            dimension(TokenKeyConstants.SHAPE_CORNER_EXTRA_LARGE, "28dp")

            // Motion — durations (same as light — variant-independent)
            duration(TokenKeyConstants.MOTION_DURATION_SHORT1, "50ms")
            duration(TokenKeyConstants.MOTION_DURATION_SHORT2, "100ms")
            duration(TokenKeyConstants.MOTION_DURATION_SHORT3, "150ms")
            duration(TokenKeyConstants.MOTION_DURATION_SHORT4, "200ms")
            duration(TokenKeyConstants.MOTION_DURATION_MEDIUM1, "250ms")
            duration(TokenKeyConstants.MOTION_DURATION_MEDIUM2, "300ms")
            duration(TokenKeyConstants.MOTION_DURATION_MEDIUM3, "350ms")
            duration(TokenKeyConstants.MOTION_DURATION_MEDIUM4, "400ms")
            duration(TokenKeyConstants.MOTION_DURATION_LONG1, "450ms")
            duration(TokenKeyConstants.MOTION_DURATION_LONG2, "500ms")
            duration(TokenKeyConstants.MOTION_DURATION_LONG3, "550ms")
            duration(TokenKeyConstants.MOTION_DURATION_LONG4, "600ms")

            // Motion — easing (same as light — variant-independent)
            easing(TokenKeyConstants.MOTION_EASING_STANDARD, "cubic-bezier(0.2, 0.0, 0, 1.0)")
            easing(TokenKeyConstants.MOTION_EASING_STANDARD_DECELERATE, "cubic-bezier(0, 0, 0, 1.0)")
            easing(TokenKeyConstants.MOTION_EASING_STANDARD_ACCELERATE, "cubic-bezier(0.3, 0, 1, 1)")
            easing(TokenKeyConstants.MOTION_EASING_EMPHASIZED, "cubic-bezier(0.2, 0.0, 0, 1.0)")
            easing(TokenKeyConstants.MOTION_EASING_EMPHASIZED_DECELERATE, "cubic-bezier(0.05, 0.7, 0.1, 1.0)")
            easing(TokenKeyConstants.MOTION_EASING_EMPHASIZED_ACCELERATE, "cubic-bezier(0.3, 0.0, 0.8, 0.15)")
            easing(TokenKeyConstants.MOTION_EASING_LINEAR, "cubic-bezier(0, 0, 1, 1)")

            // Motion — theme transition (same as light — variant-independent)
            duration(TokenKeyConstants.MOTION_THEME_TRANSITION_DURATION, "500ms")
            easing(TokenKeyConstants.MOTION_THEME_TRANSITION_EASING, "cubic-bezier(0.2, 0.0, 0, 1.0)")

            // Responsive — typography scale factors (same as light — variant-independent)
            string(TokenKeyConstants.RESPONSIVE_SCALE_MEDIUM_DISPLAY, "1.10")
            string(TokenKeyConstants.RESPONSIVE_SCALE_MEDIUM_HEADLINE, "1.05")
            string(TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_DISPLAY, "1.20")
            string(TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_HEADLINE, "1.10")
            string(TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_TITLE, "1.05")

            // ── Variant-independent extensions ────────────────────────
            addVariantIndependentExtensions()

            // ── Shadow — high contrast (sharper, more defined) ────────
            shadow(TokenKeyConstants.SHADOW_ELEVATION_NONE, "none")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_XS, "0 1px 2px 0 rgba(0,0,0,0.15)")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_SM, "0 1px 3px 0 rgba(0,0,0,0.2), 0 1px 2px -1px rgba(0,0,0,0.15)")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_MD, "0 4px 6px -1px rgba(0,0,0,0.2), 0 2px 4px -2px rgba(0,0,0,0.15)")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_LG, "0 10px 15px -3px rgba(0,0,0,0.2), 0 4px 6px -4px rgba(0,0,0,0.15)")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_XL, "0 20px 25px -5px rgba(0,0,0,0.2), 0 8px 10px -6px rgba(0,0,0,0.15)")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_XXL, "0 25px 50px -12px rgba(0,0,0,0.4)")

            // Shadow — semantic
            shadow(TokenKeyConstants.SHADOW_SUBTLE, "{shadow.elevation.xs}")
            shadow(TokenKeyConstants.SHADOW_RAISED, "{shadow.elevation.sm}")
            shadow(TokenKeyConstants.SHADOW_FLOATING, "{shadow.elevation.lg}")
            shadow(TokenKeyConstants.SHADOW_OVERLAY, "{shadow.elevation.xl}")
            shadow(TokenKeyConstants.SHADOW_DRAMATIC, "{shadow.elevation.xxl}")

            // Shadow — state (high contrast: thicker rings)
            shadow(TokenKeyConstants.SHADOW_STATE_FOCUS, "0 0 0 4px rgba(61,0,144,0.6)")
            shadow(TokenKeyConstants.SHADOW_STATE_ERROR, "0 0 0 4px rgba(139,0,0,0.6)")
            shadow(TokenKeyConstants.SHADOW_STATE_ACTIVE, "0 0 0 3px rgba(61,0,144,0.5)")
            shadow(TokenKeyConstants.SHADOW_STATE_SELECTED, "0 0 0 3px rgba(61,0,144,0.3)")

            // ── Color — Tier 2 semantic (high contrast) ───────────────
            color(TokenKeyConstants.COLOR_INTERACTIVE_HOVER, "#2D006B")
            color(TokenKeyConstants.COLOR_INTERACTIVE_PRESSED, "#1A0040")
            color(TokenKeyConstants.COLOR_INTERACTIVE_DISABLED, "#0000001F")
            color(TokenKeyConstants.COLOR_INTERACTIVE_FOCUS, "{color.primary}")

            color(TokenKeyConstants.COLOR_FEEDBACK_SUCCESS, "#0A5020")
            color(TokenKeyConstants.COLOR_FEEDBACK_SUCCESS_CONTAINER, "#C8FFC8")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_SUCCESS, "#FFFFFF")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_SUCCESS_CONTAINER, "#001A00")

            color(TokenKeyConstants.COLOR_FEEDBACK_WARNING, "#5C4000")
            color(TokenKeyConstants.COLOR_FEEDBACK_WARNING_CONTAINER, "#FFEDCC")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_WARNING, "#FFFFFF")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_WARNING_CONTAINER, "#1A0E00")

            color(TokenKeyConstants.COLOR_FEEDBACK_INFO, "#003D6B")
            color(TokenKeyConstants.COLOR_FEEDBACK_INFO_CONTAINER, "#E0F0FF")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_INFO, "#FFFFFF")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_INFO_CONTAINER, "#001020")

            color(TokenKeyConstants.COLOR_TEXT_PRIMARY, "{color.onSurface}")
            color(TokenKeyConstants.COLOR_TEXT_SECONDARY, "{color.onSurfaceVariant}")
            color(TokenKeyConstants.COLOR_TEXT_DISABLED, "#00000061")
            color(TokenKeyConstants.COLOR_TEXT_INVERSE, "{color.inverseOnSurface}")

            color(TokenKeyConstants.COLOR_BORDER_DEFAULT, "{color.outline}")
            color(TokenKeyConstants.COLOR_BORDER_STRONG, "{color.onSurface}")
            color(TokenKeyConstants.COLOR_BORDER_SUBTLE, "{color.outlineVariant}")
            color(TokenKeyConstants.COLOR_BORDER_DISABLED, "#00000029")
        }
    )

    /** M3 baseline dark theme */
    val baselineDark: ThemeDefinition = ThemeDefinition(
        id = "system-default-dark",
        name = "System Default Dark (M3 Baseline)",
        variant = ThemeVariant.DARK,
        scope = ThemeScope.SYSTEM,
        tokens = buildTokens {
            // Primary
            color(TokenKeyConstants.COLOR_PRIMARY, "#D0BCFF")
            color(TokenKeyConstants.COLOR_ON_PRIMARY, "#381E72")
            color(TokenKeyConstants.COLOR_PRIMARY_CONTAINER, "#4F378B")
            color(TokenKeyConstants.COLOR_ON_PRIMARY_CONTAINER, "#EADDFF")

            // Secondary
            color(TokenKeyConstants.COLOR_SECONDARY, "#CCC2DC")
            color(TokenKeyConstants.COLOR_ON_SECONDARY, "#332D41")
            color(TokenKeyConstants.COLOR_SECONDARY_CONTAINER, "#4A4458")
            color(TokenKeyConstants.COLOR_ON_SECONDARY_CONTAINER, "#E8DEF8")

            // Tertiary
            color(TokenKeyConstants.COLOR_TERTIARY, "#EFB8C8")
            color(TokenKeyConstants.COLOR_ON_TERTIARY, "#492532")
            color(TokenKeyConstants.COLOR_TERTIARY_CONTAINER, "#633B48")
            color(TokenKeyConstants.COLOR_ON_TERTIARY_CONTAINER, "#FFD8E4")

            // Error
            color(TokenKeyConstants.COLOR_ERROR, "#F2B8B5")
            color(TokenKeyConstants.COLOR_ON_ERROR, "#601410")
            color(TokenKeyConstants.COLOR_ERROR_CONTAINER, "#8C1D18")
            color(TokenKeyConstants.COLOR_ON_ERROR_CONTAINER, "#F9DEDC")

            // Surface
            color(TokenKeyConstants.COLOR_SURFACE, "#141218")
            color(TokenKeyConstants.COLOR_ON_SURFACE, "#E6E0E9")
            color(TokenKeyConstants.COLOR_SURFACE_VARIANT, "#49454F")
            color(TokenKeyConstants.COLOR_ON_SURFACE_VARIANT, "#CAC4D0")
            color(TokenKeyConstants.COLOR_SURFACE_CONTAINER, "#211F26")
            color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGH, "#2B2930")
            color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGHEST, "#36343B")
            color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOW, "#1D1B20")
            color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOWEST, "#0F0D13")

            // Background
            color(TokenKeyConstants.COLOR_BACKGROUND, "#141218")
            color(TokenKeyConstants.COLOR_ON_BACKGROUND, "#E6E0E9")

            // Other
            color(TokenKeyConstants.COLOR_OUTLINE, "#938F99")
            color(TokenKeyConstants.COLOR_OUTLINE_VARIANT, "#49454F")
            color(TokenKeyConstants.COLOR_INVERSE_SURFACE, "#E6E0E9")
            color(TokenKeyConstants.COLOR_INVERSE_ON_SURFACE, "#322F35")
            color(TokenKeyConstants.COLOR_INVERSE_PRIMARY, "#6750A4")
            color(TokenKeyConstants.COLOR_SCRIM, "#000000")
            color(TokenKeyConstants.COLOR_SHADOW, "#000000")

            // Elevation (same as light — elevation values are variant-independent)
            dimension(TokenKeyConstants.ELEVATION_NONE, "0dp")
            dimension(TokenKeyConstants.ELEVATION_XS, "1dp")
            dimension(TokenKeyConstants.ELEVATION_SM, "3dp")
            dimension(TokenKeyConstants.ELEVATION_MD, "6dp")
            dimension(TokenKeyConstants.ELEVATION_LG, "8dp")
            dimension(TokenKeyConstants.ELEVATION_XL, "12dp")

            // Typography (same as light — typography values are variant-independent)
            fontFamily(TokenKeyConstants.TYPOGRAPHY_FONT_FAMILY, "Roboto")
            fontFamily(TokenKeyConstants.TYPOGRAPHY_FONT_FAMILY_MONO, "Roboto Mono")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_SIZE, "57sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_LINE_HEIGHT, "64sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_LETTER_SPACING, "-0.25sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_FONT_SIZE, "45sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_LINE_HEIGHT, "52sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_LETTER_SPACING, "0sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_FONT_SIZE, "36sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_LINE_HEIGHT, "44sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_LETTER_SPACING, "0sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_FONT_SIZE, "32sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_LINE_HEIGHT, "40sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_LETTER_SPACING, "0sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_FONT_SIZE, "28sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_LINE_HEIGHT, "36sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_LETTER_SPACING, "0sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_FONT_SIZE, "24sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_LINE_HEIGHT, "32sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_LETTER_SPACING, "0sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_FONT_SIZE, "22sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_LINE_HEIGHT, "28sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_LETTER_SPACING, "0sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_FONT_SIZE, "16sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_FONT_WEIGHT, "500")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_LINE_HEIGHT, "24sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_LETTER_SPACING, "0.15sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_FONT_SIZE, "14sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_FONT_WEIGHT, "500")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_LINE_HEIGHT, "20sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_LETTER_SPACING, "0.1sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_FONT_SIZE, "16sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_LINE_HEIGHT, "24sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_LETTER_SPACING, "0.5sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_FONT_SIZE, "14sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_LINE_HEIGHT, "20sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_LETTER_SPACING, "0.25sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_FONT_SIZE, "12sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_FONT_WEIGHT, "400")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_LINE_HEIGHT, "16sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_LETTER_SPACING, "0.4sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_FONT_SIZE, "14sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_FONT_WEIGHT, "500")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_LINE_HEIGHT, "20sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_LETTER_SPACING, "0.1sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_FONT_SIZE, "12sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_FONT_WEIGHT, "500")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_LINE_HEIGHT, "16sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_LETTER_SPACING, "0.5sp")

            fontFamily(TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_FONT_FAMILY, "Roboto")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_FONT_SIZE, "11sp")
            fontWeight(TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_FONT_WEIGHT, "500")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_LINE_HEIGHT, "16sp")
            dimension(TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_LETTER_SPACING, "0.5sp")

            // Shape (same as light — shape values are variant-independent)
            dimension(TokenKeyConstants.SHAPE_CORNER_EXTRA_SMALL, "4dp")
            dimension(TokenKeyConstants.SHAPE_CORNER_SMALL, "8dp")
            dimension(TokenKeyConstants.SHAPE_CORNER_MEDIUM, "12dp")
            dimension(TokenKeyConstants.SHAPE_CORNER_LARGE, "16dp")
            dimension(TokenKeyConstants.SHAPE_CORNER_EXTRA_LARGE, "28dp")

            // Motion — durations (same as light — variant-independent)
            duration(TokenKeyConstants.MOTION_DURATION_SHORT1, "50ms")
            duration(TokenKeyConstants.MOTION_DURATION_SHORT2, "100ms")
            duration(TokenKeyConstants.MOTION_DURATION_SHORT3, "150ms")
            duration(TokenKeyConstants.MOTION_DURATION_SHORT4, "200ms")
            duration(TokenKeyConstants.MOTION_DURATION_MEDIUM1, "250ms")
            duration(TokenKeyConstants.MOTION_DURATION_MEDIUM2, "300ms")
            duration(TokenKeyConstants.MOTION_DURATION_MEDIUM3, "350ms")
            duration(TokenKeyConstants.MOTION_DURATION_MEDIUM4, "400ms")
            duration(TokenKeyConstants.MOTION_DURATION_LONG1, "450ms")
            duration(TokenKeyConstants.MOTION_DURATION_LONG2, "500ms")
            duration(TokenKeyConstants.MOTION_DURATION_LONG3, "550ms")
            duration(TokenKeyConstants.MOTION_DURATION_LONG4, "600ms")

            // Motion — easing (same as light — variant-independent)
            easing(TokenKeyConstants.MOTION_EASING_STANDARD, "cubic-bezier(0.2, 0.0, 0, 1.0)")
            easing(TokenKeyConstants.MOTION_EASING_STANDARD_DECELERATE, "cubic-bezier(0, 0, 0, 1.0)")
            easing(TokenKeyConstants.MOTION_EASING_STANDARD_ACCELERATE, "cubic-bezier(0.3, 0, 1, 1)")
            easing(TokenKeyConstants.MOTION_EASING_EMPHASIZED, "cubic-bezier(0.2, 0.0, 0, 1.0)")
            easing(TokenKeyConstants.MOTION_EASING_EMPHASIZED_DECELERATE, "cubic-bezier(0.05, 0.7, 0.1, 1.0)")
            easing(TokenKeyConstants.MOTION_EASING_EMPHASIZED_ACCELERATE, "cubic-bezier(0.3, 0.0, 0.8, 0.15)")
            easing(TokenKeyConstants.MOTION_EASING_LINEAR, "cubic-bezier(0, 0, 1, 1)")

            // Motion — theme transition (same as light — variant-independent)
            duration(TokenKeyConstants.MOTION_THEME_TRANSITION_DURATION, "500ms")
            easing(TokenKeyConstants.MOTION_THEME_TRANSITION_EASING, "cubic-bezier(0.2, 0.0, 0, 1.0)")

            // Responsive — typography scale factors (same as light — variant-independent)
            string(TokenKeyConstants.RESPONSIVE_SCALE_MEDIUM_DISPLAY, "1.10")
            string(TokenKeyConstants.RESPONSIVE_SCALE_MEDIUM_HEADLINE, "1.05")
            string(TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_DISPLAY, "1.20")
            string(TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_HEADLINE, "1.10")
            string(TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_TITLE, "1.05")

            // ── Variant-independent extensions ────────────────────────
            addVariantIndependentExtensions()

            // ── Shadow — dark theme (higher opacity for visibility) ───
            shadow(TokenKeyConstants.SHADOW_ELEVATION_NONE, "none")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_XS, "0 1px 2px 0 rgba(0,0,0,0.3)")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_SM, "0 1px 3px 0 rgba(0,0,0,0.4), 0 1px 2px -1px rgba(0,0,0,0.3)")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_MD, "0 4px 6px -1px rgba(0,0,0,0.4), 0 2px 4px -2px rgba(0,0,0,0.3)")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_LG, "0 10px 15px -3px rgba(0,0,0,0.4), 0 4px 6px -4px rgba(0,0,0,0.3)")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_XL, "0 20px 25px -5px rgba(0,0,0,0.4), 0 8px 10px -6px rgba(0,0,0,0.3)")
            shadow(TokenKeyConstants.SHADOW_ELEVATION_XXL, "0 25px 50px -12px rgba(0,0,0,0.6)")

            // Shadow — semantic
            shadow(TokenKeyConstants.SHADOW_SUBTLE, "{shadow.elevation.xs}")
            shadow(TokenKeyConstants.SHADOW_RAISED, "{shadow.elevation.sm}")
            shadow(TokenKeyConstants.SHADOW_FLOATING, "{shadow.elevation.lg}")
            shadow(TokenKeyConstants.SHADOW_OVERLAY, "{shadow.elevation.xl}")
            shadow(TokenKeyConstants.SHADOW_DRAMATIC, "{shadow.elevation.xxl}")

            // Shadow — state (dark theme: lighter brand color rings)
            shadow(TokenKeyConstants.SHADOW_STATE_FOCUS, "0 0 0 3px rgba(208,188,255,0.4)")
            shadow(TokenKeyConstants.SHADOW_STATE_ERROR, "0 0 0 3px rgba(242,184,181,0.4)")
            shadow(TokenKeyConstants.SHADOW_STATE_ACTIVE, "0 0 0 2px rgba(208,188,255,0.3)")
            shadow(TokenKeyConstants.SHADOW_STATE_SELECTED, "0 0 0 2px rgba(208,188,255,0.2)")

            // ── Color — Tier 2 semantic (dark theme) ──────────────────
            // Interactive states
            color(TokenKeyConstants.COLOR_INTERACTIVE_HOVER, "#E0D0FF")
            color(TokenKeyConstants.COLOR_INTERACTIVE_PRESSED, "#EADDFF")
            color(TokenKeyConstants.COLOR_INTERACTIVE_DISABLED, "#E6E0E914")
            color(TokenKeyConstants.COLOR_INTERACTIVE_FOCUS, "{color.primary}")

            // Feedback — success (dark theme inverted)
            color(TokenKeyConstants.COLOR_FEEDBACK_SUCCESS, "#6EDB75")
            color(TokenKeyConstants.COLOR_FEEDBACK_SUCCESS_CONTAINER, "#005313")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_SUCCESS, "#003909")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_SUCCESS_CONTAINER, "#A8F5AC")

            // Feedback — warning (dark theme inverted)
            color(TokenKeyConstants.COLOR_FEEDBACK_WARNING, "#F5C63D")
            color(TokenKeyConstants.COLOR_FEEDBACK_WARNING_CONTAINER, "#5C4000")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_WARNING, "#412D00")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_WARNING_CONTAINER, "#FFDEA1")

            // Feedback — info (dark theme inverted)
            color(TokenKeyConstants.COLOR_FEEDBACK_INFO, "#9ECAFF")
            color(TokenKeyConstants.COLOR_FEEDBACK_INFO_CONTAINER, "#003258")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_INFO, "#003258")
            color(TokenKeyConstants.COLOR_FEEDBACK_ON_INFO_CONTAINER, "#D1E4FF")

            // Text semantic
            color(TokenKeyConstants.COLOR_TEXT_PRIMARY, "{color.onSurface}")
            color(TokenKeyConstants.COLOR_TEXT_SECONDARY, "{color.onSurfaceVariant}")
            color(TokenKeyConstants.COLOR_TEXT_DISABLED, "#E6E0E961")
            color(TokenKeyConstants.COLOR_TEXT_INVERSE, "{color.inverseOnSurface}")

            // Border semantic
            color(TokenKeyConstants.COLOR_BORDER_DEFAULT, "{color.outline}")
            color(TokenKeyConstants.COLOR_BORDER_STRONG, "{color.onSurface}")
            color(TokenKeyConstants.COLOR_BORDER_SUBTLE, "{color.outlineVariant}")
            color(TokenKeyConstants.COLOR_BORDER_DISABLED, "#E6E0E929")
        }
    )
}
