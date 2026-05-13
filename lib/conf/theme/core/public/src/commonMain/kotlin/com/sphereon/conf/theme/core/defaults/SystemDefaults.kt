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

package com.sphereon.conf.theme.core.defaults

import com.sphereon.conf.theme.core.model.ThemeDefinition
import com.sphereon.conf.theme.core.model.ThemeScope
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.token.TokenBuilder
import com.sphereon.conf.theme.core.token.TokenKeyConstants
import com.sphereon.conf.theme.core.token.buildTokens

/**
 * Built-in baseline theme. Three-tier token structure:
 *
 * - Tier 1 — palette primitives (`palette.brand.*`, `palette.gray.*`, ...)
 * - Tier 2 — semantic roles (`color.primary`, `shape.radius.md`, `shadow.raised`, ...)
 * - Tier 3 — component defaults (`comp.button.primary.*`, `comp.card.*`, ...)
 *
 * Values are aligned to the Sphereon Wallet design system: Poppins type face,
 * #7C40E8 brand purple, #FBFBFB neutral surface, charcoal text. Component
 * tokens reference Tier 2 via `{token.ref}` strings so tenant overrides at
 * Tier 2 cascade through automatically.
 */
object SystemDefaults {
    // ════════════════════════════════════════════════════════════════════
    // VARIANT-INDEPENDENT EXTENSIONS
    // Palettes, spacing, radii, shadows, motion, typography, components.
    // Called by every baseline definition (light / dark / high-contrast).
    // ════════════════════════════════════════════════════════════════════

    private fun TokenBuilder.addVariantIndependentExtensions() {
        addPalettePrimitives()
        addSpacing()
        addBorderWidth()
        addShape()
        addElevation()
        addShadows()
        addMotion()
        addResponsive()
        addTypography()
        addAccessibility()
        addComponentTokens()
    }

    // ── Palette primitives (Tier 1) ──────────────────────────────────────
    // 11 ramps × 10 stops. Components MUST NOT reference these directly;
    // consume Tier 2 semantic roles instead.

    private fun TokenBuilder.addPalettePrimitives() {
        // Brand — single saturated accent, primary CTA + brand gradient
        color(TokenKeyConstants.PALETTE_BRAND_50, "#ECE4FC")
        color(TokenKeyConstants.PALETTE_BRAND_100, "#E0D2FA")
        color(TokenKeyConstants.PALETTE_BRAND_200, "#C7ADF5")
        color(TokenKeyConstants.PALETTE_BRAND_300, "#AE89F1")
        color(TokenKeyConstants.PALETTE_BRAND_400, "#9564EC")
        color(TokenKeyConstants.PALETTE_BRAND_500, "#7C40E8")
        color(TokenKeyConstants.PALETTE_BRAND_600, "#5D1AD6")
        color(TokenKeyConstants.PALETTE_BRAND_700, "#4714A4")
        color(TokenKeyConstants.PALETTE_BRAND_800, "#320E72")
        color(TokenKeyConstants.PALETTE_BRAND_900, "#1C0840")

        // Gray — neutral foundation
        color(TokenKeyConstants.PALETTE_GRAY_50, "#FBFBFB")
        color(TokenKeyConstants.PALETTE_GRAY_100, "#F2F2F2")
        color(TokenKeyConstants.PALETTE_GRAY_200, "#E3E3E3")
        color(TokenKeyConstants.PALETTE_GRAY_300, "#C4C4C4")
        color(TokenKeyConstants.PALETTE_GRAY_400, "#969696")
        color(TokenKeyConstants.PALETTE_GRAY_500, "#585858")
        color(TokenKeyConstants.PALETTE_GRAY_600, "#727272")
        color(TokenKeyConstants.PALETTE_GRAY_700, "#4E4E4E")
        color(TokenKeyConstants.PALETTE_GRAY_800, "#303030")
        color(TokenKeyConstants.PALETTE_GRAY_900, "#0A0D12")

        // Blue — secondary, dark inverted surface
        color(TokenKeyConstants.PALETTE_BLUE_50, "#D8DDEB")
        color(TokenKeyConstants.PALETTE_BLUE_100, "#CBD1E4")
        color(TokenKeyConstants.PALETTE_BLUE_200, "#B0B9D6")
        color(TokenKeyConstants.PALETTE_BLUE_300, "#96A1C8")
        color(TokenKeyConstants.PALETTE_BLUE_400, "#6071AC")
        color(TokenKeyConstants.PALETTE_BLUE_500, "#4E5E95")
        color(TokenKeyConstants.PALETTE_BLUE_600, "#404D7A")
        color(TokenKeyConstants.PALETTE_BLUE_700, "#2D3655")
        color(TokenKeyConstants.PALETTE_BLUE_800, "#2C334B")
        color(TokenKeyConstants.PALETTE_BLUE_900, "#202537")

        // Error — warm burnt orange (deliberately not red)
        color(TokenKeyConstants.PALETTE_ERROR_50, "#FFF6F3")
        color(TokenKeyConstants.PALETTE_ERROR_100, "#FFEAE3")
        color(TokenKeyConstants.PALETTE_ERROR_200, "#FFD0C0")
        color(TokenKeyConstants.PALETTE_ERROR_300, "#F2A091")
        color(TokenKeyConstants.PALETTE_ERROR_400, "#DB7759")
        color(TokenKeyConstants.PALETTE_ERROR_500, "#D14500")
        color(TokenKeyConstants.PALETTE_ERROR_600, "#B53A00")
        color(TokenKeyConstants.PALETTE_ERROR_700, "#8E2D06")
        color(TokenKeyConstants.PALETTE_ERROR_800, "#531B6C")
        color(TokenKeyConstants.PALETTE_ERROR_900, "#7A271A")

        // Warning — amber
        color(TokenKeyConstants.PALETTE_WARNING_50, "#FFFAEB")
        color(TokenKeyConstants.PALETTE_WARNING_100, "#FEEDC7")
        color(TokenKeyConstants.PALETTE_WARNING_200, "#FEDFB9")
        color(TokenKeyConstants.PALETTE_WARNING_300, "#FECB4B")
        color(TokenKeyConstants.PALETTE_WARNING_400, "#FDB922")
        color(TokenKeyConstants.PALETTE_WARNING_500, "#E0B910")
        color(TokenKeyConstants.PALETTE_WARNING_600, "#DC9A02")
        color(TokenKeyConstants.PALETTE_WARNING_700, "#985A0B")
        color(TokenKeyConstants.PALETTE_WARNING_800, "#5F4D3E")
        color(TokenKeyConstants.PALETTE_WARNING_900, "#44E108")

        // Success — green
        color(TokenKeyConstants.PALETTE_SUCCESS_50, "#CCFFDF")
        color(TokenKeyConstants.PALETTE_SUCCESS_100, "#B8FFD3")
        color(TokenKeyConstants.PALETTE_SUCCESS_200, "#8BFFB9")
        color(TokenKeyConstants.PALETTE_SUCCESS_300, "#66BFA0")
        color(TokenKeyConstants.PALETTE_SUCCESS_400, "#15FF5D")
        color(TokenKeyConstants.PALETTE_SUCCESS_500, "#00E963")
        color(TokenKeyConstants.PALETTE_SUCCESS_600, "#00C249")
        color(TokenKeyConstants.PALETTE_SUCCESS_700, "#006B34")
        color(TokenKeyConstants.PALETTE_SUCCESS_800, "#005C2F")
        color(TokenKeyConstants.PALETTE_SUCCESS_900, "#003516")

        // Pending — info-blue
        color(TokenKeyConstants.PALETTE_PENDING_50, "#D7EAFF")
        color(TokenKeyConstants.PALETTE_PENDING_100, "#AED5FF")
        color(TokenKeyConstants.PALETTE_PENDING_200, "#85C0FF")
        color(TokenKeyConstants.PALETTE_PENDING_300, "#5DABFF")
        color(TokenKeyConstants.PALETTE_PENDING_400, "#3496FF")
        color(TokenKeyConstants.PALETTE_PENDING_500, "#0B81FF")
        color(TokenKeyConstants.PALETTE_PENDING_600, "#0066D2")
        color(TokenKeyConstants.PALETTE_PENDING_700, "#004A9A")
        color(TokenKeyConstants.PALETTE_PENDING_800, "#002F62")
        color(TokenKeyConstants.PALETTE_PENDING_900, "#002246")

        // Aqua — category accent
        color(TokenKeyConstants.PALETTE_AQUA_50, "#E3F9F7")
        color(TokenKeyConstants.PALETTE_AQUA_100, "#C1F3EE")
        color(TokenKeyConstants.PALETTE_AQUA_200, "#9FECE5")
        color(TokenKeyConstants.PALETTE_AQUA_300, "#7DE5DB")
        color(TokenKeyConstants.PALETTE_AQUA_400, "#5BDED2")
        color(TokenKeyConstants.PALETTE_AQUA_500, "#2CD5C5")
        color(TokenKeyConstants.PALETTE_AQUA_600, "#22A79B")
        color(TokenKeyConstants.PALETTE_AQUA_700, "#187870")
        color(TokenKeyConstants.PALETTE_AQUA_800, "#0F4A44")
        color(TokenKeyConstants.PALETTE_AQUA_900, "#0A322F")

        // Selenas — category accent
        color(TokenKeyConstants.PALETTE_SELENAS_50, "#F4E3F9")
        color(TokenKeyConstants.PALETTE_SELENAS_100, "#E6C1F3")
        color(TokenKeyConstants.PALETTE_SELENAS_200, "#D89FEC")
        color(TokenKeyConstants.PALETTE_SELENAS_300, "#CA7DE5")
        color(TokenKeyConstants.PALETTE_SELENAS_400, "#BC5BDE")
        color(TokenKeyConstants.PALETTE_SELENAS_500, "#A92CD5")
        color(TokenKeyConstants.PALETTE_SELENAS_600, "#8522A7")
        color(TokenKeyConstants.PALETTE_SELENAS_700, "#5F1878")
        color(TokenKeyConstants.PALETTE_SELENAS_800, "#3A0F4A")
        color(TokenKeyConstants.PALETTE_SELENAS_900, "#280A32")

        // Magenta — category accent
        color(TokenKeyConstants.PALETTE_MAGENTA_50, "#FFD0DB")
        color(TokenKeyConstants.PALETTE_MAGENTA_100, "#FFA7BC")
        color(TokenKeyConstants.PALETTE_MAGENTA_200, "#FF7F9D")
        color(TokenKeyConstants.PALETTE_MAGENTA_300, "#FF567E")
        color(TokenKeyConstants.PALETTE_MAGENTA_400, "#FF2D5F")
        color(TokenKeyConstants.PALETTE_MAGENTA_500, "#F4003A")
        color(TokenKeyConstants.PALETTE_MAGENTA_600, "#BC002D")
        color(TokenKeyConstants.PALETTE_MAGENTA_700, "#84001F")
        color(TokenKeyConstants.PALETTE_MAGENTA_800, "#4C0012")
        color(TokenKeyConstants.PALETTE_MAGENTA_900, "#30000B")

        // Purple — illustration accent (distinct from brand)
        color(TokenKeyConstants.PALETTE_PURPLE_50, "#E7DEFB")
        color(TokenKeyConstants.PALETTE_PURPLE_100, "#CBBAF6")
        color(TokenKeyConstants.PALETTE_PURPLE_200, "#B096F2")
        color(TokenKeyConstants.PALETTE_PURPLE_300, "#9571ED")
        color(TokenKeyConstants.PALETTE_PURPLE_400, "#7A4DE9")
        color(TokenKeyConstants.PALETTE_PURPLE_500, "#551CE2")
        color(TokenKeyConstants.PALETTE_PURPLE_600, "#4216B0")
        color(TokenKeyConstants.PALETTE_PURPLE_700, "#2F107E")
        color(TokenKeyConstants.PALETTE_PURPLE_800, "#1D094C")
        color(TokenKeyConstants.PALETTE_PURPLE_900, "#130633")
    }

    // ── Spacing — 4px grid, full Figma scale + half-stops + axis aliases ─

    private fun TokenBuilder.addSpacing() {
        spacing(TokenKeyConstants.SPACING_0, "0px")
        spacing(TokenKeyConstants.SPACING_0_5, "2px")
        spacing(TokenKeyConstants.SPACING_1, "4px")
        spacing(TokenKeyConstants.SPACING_1_5, "6px")
        spacing(TokenKeyConstants.SPACING_2, "8px")
        spacing(TokenKeyConstants.SPACING_2_5, "10px")
        spacing(TokenKeyConstants.SPACING_3, "12px")
        spacing(TokenKeyConstants.SPACING_3_5, "14px")
        spacing(TokenKeyConstants.SPACING_4, "16px")
        spacing(TokenKeyConstants.SPACING_5, "20px")
        spacing(TokenKeyConstants.SPACING_6, "24px")
        spacing(TokenKeyConstants.SPACING_7, "28px")
        spacing(TokenKeyConstants.SPACING_8, "32px")
        spacing(TokenKeyConstants.SPACING_9, "36px")
        spacing(TokenKeyConstants.SPACING_10, "40px")
        spacing(TokenKeyConstants.SPACING_11, "44px")
        spacing(TokenKeyConstants.SPACING_12, "48px")
        spacing(TokenKeyConstants.SPACING_14, "56px")
        spacing(TokenKeyConstants.SPACING_16, "64px")
        spacing(TokenKeyConstants.SPACING_20, "80px")
        spacing(TokenKeyConstants.SPACING_24, "96px")
        spacing(TokenKeyConstants.SPACING_28, "112px")
        spacing(TokenKeyConstants.SPACING_32, "128px")
        spacing(TokenKeyConstants.SPACING_36, "144px")
        spacing(TokenKeyConstants.SPACING_40, "160px")
        spacing(TokenKeyConstants.SPACING_44, "176px")
        spacing(TokenKeyConstants.SPACING_48, "192px")
        spacing(TokenKeyConstants.SPACING_52, "208px")
        spacing(TokenKeyConstants.SPACING_56, "224px")
        spacing(TokenKeyConstants.SPACING_60, "240px")
        spacing(TokenKeyConstants.SPACING_64, "256px")
        spacing(TokenKeyConstants.SPACING_72, "288px")
        spacing(TokenKeyConstants.SPACING_80, "320px")
        spacing(TokenKeyConstants.SPACING_96, "384px")

        // Axis aliases — Tier 2, reference Tier 1 numeric stops
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
    }

    private fun TokenBuilder.addBorderWidth() {
        borderWidth(TokenKeyConstants.BORDER_WIDTH_NONE, "0px")
        borderWidth(TokenKeyConstants.BORDER_WIDTH_THIN, "1px")
        borderWidth(TokenKeyConstants.BORDER_WIDTH_MEDIUM, "2px")
        borderWidth(TokenKeyConstants.BORDER_WIDTH_THICK, "4px")
    }

    // ── Shape / Border Radius ────────────────────────────────────────────
    // Numeric Figma stops (0/1/2/3/4/6/8/12) + semantic aliases (xs..xxxl)
    // + legacy M3 corner aliases (extraSmall..extraLarge).

    private fun TokenBuilder.addShape() {
        // Numeric Figma stops
        dimension(TokenKeyConstants.SHAPE_RADIUS_0, "0dp")
        dimension(TokenKeyConstants.SHAPE_RADIUS_1, "2dp")
        dimension(TokenKeyConstants.SHAPE_RADIUS_2, "4dp")
        dimension(TokenKeyConstants.SHAPE_RADIUS_3, "6dp")
        dimension(TokenKeyConstants.SHAPE_RADIUS_4, "8dp")
        dimension(TokenKeyConstants.SHAPE_RADIUS_6, "12dp")
        dimension(TokenKeyConstants.SHAPE_RADIUS_8, "16dp")
        dimension(TokenKeyConstants.SHAPE_RADIUS_12, "24dp")

        // Semantic aliases — Tier 2
        dimension(TokenKeyConstants.SHAPE_RADIUS_NONE, "{shape.radius.0}")
        dimension(TokenKeyConstants.SHAPE_RADIUS_XS, "{shape.radius.1}")
        dimension(TokenKeyConstants.SHAPE_RADIUS_SM, "{shape.radius.2}")
        dimension(TokenKeyConstants.SHAPE_RADIUS_MD, "{shape.radius.4}")
        dimension(TokenKeyConstants.SHAPE_RADIUS_LG, "{shape.radius.6}")
        dimension(TokenKeyConstants.SHAPE_RADIUS_XL, "{shape.radius.8}")
        dimension(TokenKeyConstants.SHAPE_RADIUS_XXL, "{shape.radius.12}")
        dimension(TokenKeyConstants.SHAPE_RADIUS_XXXL, "{shape.radius.12}")
        dimension(TokenKeyConstants.SHAPE_RADIUS_FULL, "9999dp")

        // Legacy M3 corner aliases
        dimension(TokenKeyConstants.SHAPE_CORNER_EXTRA_SMALL, "{shape.radius.sm}")
        dimension(TokenKeyConstants.SHAPE_CORNER_SMALL, "{shape.radius.md}")
        dimension(TokenKeyConstants.SHAPE_CORNER_MEDIUM, "{shape.radius.lg}")
        dimension(TokenKeyConstants.SHAPE_CORNER_LARGE, "{shape.radius.xl}")
        dimension(TokenKeyConstants.SHAPE_CORNER_EXTRA_LARGE, "{shape.radius.xxxl}")
    }

    private fun TokenBuilder.addElevation() {
        // Legacy elevation namespace (kept for Compose Material 3 interop).
        dimension(TokenKeyConstants.ELEVATION_NONE, "0dp")
        dimension(TokenKeyConstants.ELEVATION_XS, "1dp")
        dimension(TokenKeyConstants.ELEVATION_SM, "3dp")
        dimension(TokenKeyConstants.ELEVATION_MD, "6dp")
        dimension(TokenKeyConstants.ELEVATION_LG, "8dp")
        dimension(TokenKeyConstants.ELEVATION_XL, "12dp")
    }

    // ── Shadows — variant-independent in the wallet system ───────────────
    // Numeric Figma tier (100/200/300/400) + M3 elevation scale + semantic
    // aliases (subtle/raised/floating/overlay/dramatic) + state shadows.

    private fun TokenBuilder.addShadows() {
        // Numeric tier — Figma exact values
        shadow(TokenKeyConstants.SHADOW_100, "0 1px 2px rgba(10,13,18,0.05)")
        shadow(TokenKeyConstants.SHADOW_200, "0 2px 4px rgba(0,0,0,0.10)")
        shadow(TokenKeyConstants.SHADOW_300, "0 4px 8px rgba(0,0,0,0.12)")
        shadow(TokenKeyConstants.SHADOW_400, "0 12px 24px rgba(10,13,18,0.16)")

        // M3 elevation scale (consumed by Compose / web alike)
        shadow(TokenKeyConstants.SHADOW_ELEVATION_NONE, "none")
        shadow(TokenKeyConstants.SHADOW_ELEVATION_XS, "{shadow.100}")
        shadow(TokenKeyConstants.SHADOW_ELEVATION_SM, "{shadow.200}")
        shadow(TokenKeyConstants.SHADOW_ELEVATION_MD, "{shadow.300}")
        shadow(TokenKeyConstants.SHADOW_ELEVATION_LG, "{shadow.400}")
        shadow(TokenKeyConstants.SHADOW_ELEVATION_XL, "0 24px 48px rgba(10,13,18,0.20)")
        shadow(TokenKeyConstants.SHADOW_ELEVATION_XXL, "0 25px 50px -12px rgba(0,0,0,0.25)")

        // Semantic aliases — Tier 2
        shadow(TokenKeyConstants.SHADOW_SUBTLE, "{shadow.elevation.xs}")
        shadow(TokenKeyConstants.SHADOW_RAISED, "{shadow.elevation.sm}")
        shadow(TokenKeyConstants.SHADOW_FLOATING, "{shadow.elevation.md}")
        shadow(TokenKeyConstants.SHADOW_OVERLAY, "{shadow.elevation.lg}")
        shadow(TokenKeyConstants.SHADOW_DRAMATIC, "{shadow.elevation.xxl}")

        // State shadows — focus/active are wallet-constant across themes.
        shadow(TokenKeyConstants.SHADOW_STATE_FOCUS, "0 0 0 3px rgba(11,129,255,0.5)")
        shadow(TokenKeyConstants.SHADOW_STATE_ERROR, "0 0 0 3px rgba(181,58,0,0.5)")
        shadow(TokenKeyConstants.SHADOW_STATE_ACTIVE, "inset 0 2px 4px rgba(0,0,0,0.16)")
        shadow(TokenKeyConstants.SHADOW_STATE_SELECTED, "0 0 0 2px rgba(124,64,232,0.3)")
    }

    private fun TokenBuilder.addMotion() {
        // M3 numeric durations
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

        // Semantic durations — wallet uses fast/base/slow trio
        duration(TokenKeyConstants.MOTION_DURATION_INSTANT, "0ms")
        duration(TokenKeyConstants.MOTION_DURATION_FAST, "150ms")
        duration(TokenKeyConstants.MOTION_DURATION_NORMAL, "200ms")
        duration(TokenKeyConstants.MOTION_DURATION_SLOW, "300ms")
        duration(TokenKeyConstants.MOTION_DURATION_SLOWER, "600ms")

        duration(TokenKeyConstants.MOTION_DELAY_NONE, "0ms")
        duration(TokenKeyConstants.MOTION_DELAY_SHORT, "100ms")

        // Easings
        easing(TokenKeyConstants.MOTION_EASING_STANDARD, "cubic-bezier(0.2, 0, 0, 1)")
        easing(TokenKeyConstants.MOTION_EASING_STANDARD_DECELERATE, "cubic-bezier(0, 0, 0, 1)")
        easing(TokenKeyConstants.MOTION_EASING_STANDARD_ACCELERATE, "cubic-bezier(0.3, 0, 1, 1)")
        easing(TokenKeyConstants.MOTION_EASING_EMPHASIZED, "cubic-bezier(0.2, 0, 0, 1)")
        easing(TokenKeyConstants.MOTION_EASING_EMPHASIZED_DECELERATE, "cubic-bezier(0.05, 0.7, 0.1, 1)")
        easing(TokenKeyConstants.MOTION_EASING_EMPHASIZED_ACCELERATE, "cubic-bezier(0.3, 0, 0.8, 0.15)")
        easing(TokenKeyConstants.MOTION_EASING_LINEAR, "cubic-bezier(0, 0, 1, 1)")
        easing(TokenKeyConstants.MOTION_EASING_BOUNCE, "cubic-bezier(0.34, 1.56, 0.64, 1)")

        // Theme transition
        duration(TokenKeyConstants.MOTION_THEME_TRANSITION_DURATION, "300ms")
        easing(TokenKeyConstants.MOTION_THEME_TRANSITION_EASING, "cubic-bezier(0.2, 0, 0, 1)")
    }

    private fun TokenBuilder.addResponsive() {
        string(TokenKeyConstants.RESPONSIVE_SCALE_MEDIUM_DISPLAY, "1.10")
        string(TokenKeyConstants.RESPONSIVE_SCALE_MEDIUM_HEADLINE, "1.05")
        string(TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_DISPLAY, "1.20")
        string(TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_HEADLINE, "1.10")
        string(TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_TITLE, "1.05")
    }

    // ── Typography — Poppins primary, mobile-first scale ─────────────────
    // Both M3 keys (display/headline/title/body/label) and the wallet's
    // mobile role names (text.style.xl/h1/h2/h3/subtitle1/2/body1/micro1/2)
    // are populated. M3 keys are mapped onto the wallet's compact scale.

    private fun TokenBuilder.addTypography() {
        val sans = "Poppins, 'Plus Jakarta Sans', system-ui, sans-serif"
        val secondary = "'Plus Jakarta Sans', Poppins, system-ui, sans-serif"
        val meta = "Inter, system-ui, sans-serif"
        val mono = "ui-monospace, 'JetBrains Mono', 'SF Mono', Menlo, monospace"

        // Wallet-native family tokens
        fontFamily(TokenKeyConstants.TEXT_FAMILY_SANS, sans)
        fontFamily(TokenKeyConstants.TEXT_FAMILY_SECONDARY, secondary)
        fontFamily(TokenKeyConstants.TEXT_FAMILY_META, meta)
        fontFamily(TokenKeyConstants.TEXT_FAMILY_MONO, mono)

        // Generic IDK family aliases
        fontFamily(TokenKeyConstants.TYPOGRAPHY_FONT_FAMILY, sans)
        fontFamily(TokenKeyConstants.TYPOGRAPHY_FONT_FAMILY_MONO, mono)

        // Wallet mobile role tokens — text.style.*
        textStyle(
            TokenKeyConstants.TEXT_STYLE_XL_FONT_FAMILY,
            TokenKeyConstants.TEXT_STYLE_XL_FONT_SIZE,
            TokenKeyConstants.TEXT_STYLE_XL_FONT_WEIGHT,
            TokenKeyConstants.TEXT_STYLE_XL_LINE_HEIGHT,
            sans,
            "46sp",
            "600",
            "54sp"
        )
        textStyle(
            TokenKeyConstants.TEXT_STYLE_H1_FONT_FAMILY,
            TokenKeyConstants.TEXT_STYLE_H1_FONT_SIZE,
            TokenKeyConstants.TEXT_STYLE_H1_FONT_WEIGHT,
            TokenKeyConstants.TEXT_STYLE_H1_LINE_HEIGHT,
            sans,
            "24sp",
            "600",
            "36sp"
        )
        textStyle(
            TokenKeyConstants.TEXT_STYLE_H2_FONT_FAMILY,
            TokenKeyConstants.TEXT_STYLE_H2_FONT_SIZE,
            TokenKeyConstants.TEXT_STYLE_H2_FONT_WEIGHT,
            TokenKeyConstants.TEXT_STYLE_H2_LINE_HEIGHT,
            sans,
            "16sp",
            "600",
            "24sp"
        )
        textStyle(
            TokenKeyConstants.TEXT_STYLE_H3_FONT_FAMILY,
            TokenKeyConstants.TEXT_STYLE_H3_FONT_SIZE,
            TokenKeyConstants.TEXT_STYLE_H3_FONT_WEIGHT,
            TokenKeyConstants.TEXT_STYLE_H3_LINE_HEIGHT,
            sans,
            "16sp",
            "400",
            "24sp"
        )
        textStyle(
            TokenKeyConstants.TEXT_STYLE_SUBTITLE1_FONT_FAMILY,
            TokenKeyConstants.TEXT_STYLE_SUBTITLE1_FONT_SIZE,
            TokenKeyConstants.TEXT_STYLE_SUBTITLE1_FONT_WEIGHT,
            TokenKeyConstants.TEXT_STYLE_SUBTITLE1_LINE_HEIGHT,
            sans,
            "14sp",
            "600",
            "21sp"
        )
        textStyle(
            TokenKeyConstants.TEXT_STYLE_SUBTITLE2_FONT_FAMILY,
            TokenKeyConstants.TEXT_STYLE_SUBTITLE2_FONT_SIZE,
            TokenKeyConstants.TEXT_STYLE_SUBTITLE2_FONT_WEIGHT,
            TokenKeyConstants.TEXT_STYLE_SUBTITLE2_LINE_HEIGHT,
            sans,
            "11sp",
            "600",
            "17sp"
        )
        textStyle(
            TokenKeyConstants.TEXT_STYLE_BODY1_FONT_FAMILY,
            TokenKeyConstants.TEXT_STYLE_BODY1_FONT_SIZE,
            TokenKeyConstants.TEXT_STYLE_BODY1_FONT_WEIGHT,
            TokenKeyConstants.TEXT_STYLE_BODY1_LINE_HEIGHT,
            sans,
            "12sp",
            "400",
            "21sp"
        )
        textStyle(
            TokenKeyConstants.TEXT_STYLE_MICRO1_FONT_FAMILY,
            TokenKeyConstants.TEXT_STYLE_MICRO1_FONT_SIZE,
            TokenKeyConstants.TEXT_STYLE_MICRO1_FONT_WEIGHT,
            TokenKeyConstants.TEXT_STYLE_MICRO1_LINE_HEIGHT,
            sans,
            "10sp",
            "400",
            "15sp"
        )
        textStyle(
            TokenKeyConstants.TEXT_STYLE_MICRO2_FONT_FAMILY,
            TokenKeyConstants.TEXT_STYLE_MICRO2_FONT_SIZE,
            TokenKeyConstants.TEXT_STYLE_MICRO2_FONT_WEIGHT,
            TokenKeyConstants.TEXT_STYLE_MICRO2_LINE_HEIGHT,
            sans,
            "9sp",
            "400",
            "15sp"
        )

        // Desktop overrides — picked up by web at @media (min-width: 768px),
        // by Compose at WindowWidthSizeClass.Medium / Expanded.
        dimension(TokenKeyConstants.TEXT_STYLE_XL_DESKTOP_FONT_SIZE, "56sp")
        dimension(TokenKeyConstants.TEXT_STYLE_XL_DESKTOP_LINE_HEIGHT, "64sp")
        dimension(TokenKeyConstants.TEXT_STYLE_H1_DESKTOP_FONT_SIZE, "32sp")
        dimension(TokenKeyConstants.TEXT_STYLE_H1_DESKTOP_LINE_HEIGHT, "40sp")
        dimension(TokenKeyConstants.TEXT_STYLE_H2_DESKTOP_FONT_SIZE, "20sp")
        dimension(TokenKeyConstants.TEXT_STYLE_H2_DESKTOP_LINE_HEIGHT, "28sp")
        dimension(TokenKeyConstants.TEXT_STYLE_H3_DESKTOP_FONT_SIZE, "18sp")
        dimension(TokenKeyConstants.TEXT_STYLE_H3_DESKTOP_LINE_HEIGHT, "26sp")
        dimension(TokenKeyConstants.TEXT_STYLE_SUBTITLE1_DESKTOP_FONT_SIZE, "16sp")
        dimension(TokenKeyConstants.TEXT_STYLE_SUBTITLE1_DESKTOP_LINE_HEIGHT, "24sp")
        dimension(TokenKeyConstants.TEXT_STYLE_SUBTITLE2_DESKTOP_FONT_SIZE, "14sp")
        dimension(TokenKeyConstants.TEXT_STYLE_SUBTITLE2_DESKTOP_LINE_HEIGHT, "20sp")
        dimension(TokenKeyConstants.TEXT_STYLE_BODY1_DESKTOP_FONT_SIZE, "14sp")
        dimension(TokenKeyConstants.TEXT_STYLE_BODY1_DESKTOP_LINE_HEIGHT, "22sp")
        dimension(TokenKeyConstants.TEXT_STYLE_MICRO1_DESKTOP_FONT_SIZE, "12sp")
        dimension(TokenKeyConstants.TEXT_STYLE_MICRO1_DESKTOP_LINE_HEIGHT, "18sp")
        dimension(TokenKeyConstants.TEXT_STYLE_MICRO2_DESKTOP_FONT_SIZE, "11sp")
        dimension(TokenKeyConstants.TEXT_STYLE_MICRO2_DESKTOP_LINE_HEIGHT, "16sp")

        // M3 typography keys — mapped to the wallet's compact scale.
        m3TypeScale(
            family = sans,
            family_ = TokenKeyConstants.TYPOGRAPHY_DISPLAY_EXTRA_LARGE_FONT_FAMILY,
            size = TokenKeyConstants.TYPOGRAPHY_DISPLAY_EXTRA_LARGE_FONT_SIZE,
            weight = TokenKeyConstants.TYPOGRAPHY_DISPLAY_EXTRA_LARGE_FONT_WEIGHT,
            line = TokenKeyConstants.TYPOGRAPHY_DISPLAY_EXTRA_LARGE_LINE_HEIGHT,
            letter = TokenKeyConstants.TYPOGRAPHY_DISPLAY_EXTRA_LARGE_LETTER_SPACING,
            sizeV = "46sp",
            weightV = "600",
            lineV = "54sp",
            letterV = "0sp",
        )
        m3TypeScale(
            sans,
            TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_FAMILY,
            TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_SIZE,
            TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_WEIGHT,
            TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_LINE_HEIGHT,
            TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_LETTER_SPACING,
            "46sp",
            "600",
            "54sp",
            "0sp"
        )
        m3TypeScale(
            sans,
            TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_FONT_FAMILY,
            TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_FONT_SIZE,
            TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_FONT_WEIGHT,
            TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_LINE_HEIGHT,
            TokenKeyConstants.TYPOGRAPHY_DISPLAY_MEDIUM_LETTER_SPACING,
            "32sp",
            "600",
            "40sp",
            "0sp"
        )
        m3TypeScale(
            sans,
            TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_FONT_FAMILY,
            TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_FONT_SIZE,
            TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_FONT_WEIGHT,
            TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_LINE_HEIGHT,
            TokenKeyConstants.TYPOGRAPHY_DISPLAY_SMALL_LETTER_SPACING,
            "24sp",
            "600",
            "36sp",
            "0sp"
        )
        m3TypeScale(
            sans,
            TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_FONT_FAMILY,
            TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_FONT_SIZE,
            TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_FONT_WEIGHT,
            TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_LINE_HEIGHT,
            TokenKeyConstants.TYPOGRAPHY_HEADLINE_LARGE_LETTER_SPACING,
            "24sp",
            "600",
            "36sp",
            "0sp"
        )
        m3TypeScale(
            sans,
            TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_FONT_FAMILY,
            TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_FONT_SIZE,
            TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_FONT_WEIGHT,
            TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_LINE_HEIGHT,
            TokenKeyConstants.TYPOGRAPHY_HEADLINE_MEDIUM_LETTER_SPACING,
            "20sp",
            "600",
            "30sp",
            "0sp"
        )
        m3TypeScale(
            sans,
            TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_FONT_FAMILY,
            TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_FONT_SIZE,
            TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_FONT_WEIGHT,
            TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_LINE_HEIGHT,
            TokenKeyConstants.TYPOGRAPHY_HEADLINE_SMALL_LETTER_SPACING,
            "18sp",
            "600",
            "27sp",
            "0sp"
        )
        m3TypeScale(
            sans,
            TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_FONT_FAMILY,
            TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_FONT_SIZE,
            TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_FONT_WEIGHT,
            TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_LINE_HEIGHT,
            TokenKeyConstants.TYPOGRAPHY_TITLE_LARGE_LETTER_SPACING,
            "16sp",
            "600",
            "24sp",
            "0sp"
        )
        m3TypeScale(
            sans,
            TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_FONT_FAMILY,
            TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_FONT_SIZE,
            TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_FONT_WEIGHT,
            TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_LINE_HEIGHT,
            TokenKeyConstants.TYPOGRAPHY_TITLE_MEDIUM_LETTER_SPACING,
            "16sp",
            "400",
            "24sp",
            "0sp"
        )
        m3TypeScale(
            sans,
            TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_FONT_FAMILY,
            TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_FONT_SIZE,
            TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_FONT_WEIGHT,
            TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_LINE_HEIGHT,
            TokenKeyConstants.TYPOGRAPHY_TITLE_SMALL_LETTER_SPACING,
            "14sp",
            "600",
            "21sp",
            "0sp"
        )
        m3TypeScale(
            sans,
            TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_FONT_FAMILY,
            TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_FONT_SIZE,
            TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_FONT_WEIGHT,
            TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_LINE_HEIGHT,
            TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_LETTER_SPACING,
            "14sp",
            "400",
            "21sp",
            "0sp"
        )
        m3TypeScale(
            sans,
            TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_FONT_FAMILY,
            TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_FONT_SIZE,
            TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_FONT_WEIGHT,
            TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_LINE_HEIGHT,
            TokenKeyConstants.TYPOGRAPHY_BODY_MEDIUM_LETTER_SPACING,
            "12sp",
            "400",
            "21sp",
            "0sp"
        )
        m3TypeScale(
            sans,
            TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_FONT_FAMILY,
            TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_FONT_SIZE,
            TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_FONT_WEIGHT,
            TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_LINE_HEIGHT,
            TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_LETTER_SPACING,
            "10sp",
            "400",
            "15sp",
            "0sp"
        )
        m3TypeScale(
            sans,
            TokenKeyConstants.TYPOGRAPHY_BODY_EXTRA_SMALL_FONT_FAMILY,
            TokenKeyConstants.TYPOGRAPHY_BODY_EXTRA_SMALL_FONT_SIZE,
            TokenKeyConstants.TYPOGRAPHY_BODY_EXTRA_SMALL_FONT_WEIGHT,
            TokenKeyConstants.TYPOGRAPHY_BODY_EXTRA_SMALL_LINE_HEIGHT,
            TokenKeyConstants.TYPOGRAPHY_BODY_EXTRA_SMALL_LETTER_SPACING,
            "9sp",
            "400",
            "15sp",
            "0sp"
        )
        m3TypeScale(
            sans,
            TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_FONT_FAMILY,
            TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_FONT_SIZE,
            TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_FONT_WEIGHT,
            TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_LINE_HEIGHT,
            TokenKeyConstants.TYPOGRAPHY_LABEL_LARGE_LETTER_SPACING,
            "14sp",
            "600",
            "21sp",
            "0sp"
        )
        m3TypeScale(
            sans,
            TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_FONT_FAMILY,
            TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_FONT_SIZE,
            TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_FONT_WEIGHT,
            TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_LINE_HEIGHT,
            TokenKeyConstants.TYPOGRAPHY_LABEL_MEDIUM_LETTER_SPACING,
            "12sp",
            "500",
            "21sp",
            "0sp"
        )
        m3TypeScale(
            sans,
            TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_FONT_FAMILY,
            TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_FONT_SIZE,
            TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_FONT_WEIGHT,
            TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_LINE_HEIGHT,
            TokenKeyConstants.TYPOGRAPHY_LABEL_SMALL_LETTER_SPACING,
            "11sp",
            "600",
            "17sp",
            "0sp"
        )
    }

    private fun TokenBuilder.textStyle(
        familyKey: String,
        sizeKey: String,
        weightKey: String,
        lineKey: String,
        family: String,
        size: String,
        weight: String,
        line: String,
    ) {
        fontFamily(familyKey, family)
        dimension(sizeKey, size)
        fontWeight(weightKey, weight)
        dimension(lineKey, line)
    }

    @Suppress("LongParameterList")
    private fun TokenBuilder.m3TypeScale(
        family: String,
        family_: String,
        size: String,
        weight: String,
        line: String,
        letter: String,
        sizeV: String,
        weightV: String,
        lineV: String,
        letterV: String,
    ) {
        fontFamily(family_, family)
        dimension(size, sizeV)
        fontWeight(weight, weightV)
        dimension(line, lineV)
        dimension(letter, letterV)
    }

    private fun TokenBuilder.addAccessibility() {
        dimension(TokenKeyConstants.A11Y_TARGET_SIZE_MIN, "44px")
    }

    // ── Component tokens (Tier 3) ────────────────────────────────────────
    // Reference Tier 2 via `{token.ref}` so tenant overrides cascade.

    @Suppress("LongMethod")
    private fun TokenBuilder.addComponentTokens() {
        // Button — primary (gradient handled by app code; token holds the solid)
        color(TokenKeyConstants.COMP_BUTTON_PRIMARY_BACKGROUND, "{color.primary}")
        color(TokenKeyConstants.COMP_BUTTON_PRIMARY_BACKGROUND_HOVER, "{palette.brand.600}")
        color(TokenKeyConstants.COMP_BUTTON_PRIMARY_BACKGROUND_ACTIVE, "{palette.brand.700}")
        color(TokenKeyConstants.COMP_BUTTON_PRIMARY_FOREGROUND, "{color.onPrimary}")
        color(TokenKeyConstants.COMP_BUTTON_PRIMARY_BORDER, "{color.primary}")
        dimension(TokenKeyConstants.COMP_BUTTON_PRIMARY_BORDER_WIDTH, "{borderWidth.none}")
        dimension(TokenKeyConstants.COMP_BUTTON_PRIMARY_RADIUS, "{shape.radius.3}")
        shadow(TokenKeyConstants.COMP_BUTTON_PRIMARY_SHADOW, "{shadow.subtle}")
        spacing(TokenKeyConstants.COMP_BUTTON_PRIMARY_PADDING_X, "{spacing.inline.md}")
        spacing(TokenKeyConstants.COMP_BUTTON_PRIMARY_PADDING_Y, "{spacing.stack.sm}")
        dimension(TokenKeyConstants.COMP_BUTTON_PRIMARY_HEIGHT, "42dp")

        // Button — secondary (outlined)
        color(TokenKeyConstants.COMP_BUTTON_SECONDARY_BACKGROUND, "transparent")
        color(TokenKeyConstants.COMP_BUTTON_SECONDARY_BACKGROUND_HOVER, "{palette.brand.50}")
        color(TokenKeyConstants.COMP_BUTTON_SECONDARY_BACKGROUND_ACTIVE, "{palette.brand.100}")
        color(TokenKeyConstants.COMP_BUTTON_SECONDARY_FOREGROUND, "{color.text.primary}")
        color(TokenKeyConstants.COMP_BUTTON_SECONDARY_BORDER, "{color.secondary}")
        dimension(TokenKeyConstants.COMP_BUTTON_SECONDARY_BORDER_WIDTH, "{borderWidth.thin}")
        dimension(TokenKeyConstants.COMP_BUTTON_SECONDARY_RADIUS, "{shape.radius.3}")
        dimension(TokenKeyConstants.COMP_BUTTON_SECONDARY_HEIGHT, "42dp")

        // Button — ghost
        color(TokenKeyConstants.COMP_BUTTON_GHOST_BACKGROUND, "transparent")
        color(TokenKeyConstants.COMP_BUTTON_GHOST_BACKGROUND_HOVER, "{palette.brand.50}")
        color(TokenKeyConstants.COMP_BUTTON_GHOST_FOREGROUND, "{color.primary}")
        dimension(TokenKeyConstants.COMP_BUTTON_GHOST_RADIUS, "{shape.radius.3}")
        dimension(TokenKeyConstants.COMP_BUTTON_GHOST_HEIGHT, "42dp")

        // Input
        color(TokenKeyConstants.COMP_INPUT_BACKGROUND, "{color.surface}")
        color(TokenKeyConstants.COMP_INPUT_FOREGROUND, "{color.onSurface}")
        color(TokenKeyConstants.COMP_INPUT_BORDER, "{color.border.default}")
        color(TokenKeyConstants.COMP_INPUT_BORDER_FOCUS, "{color.primary}")
        color(TokenKeyConstants.COMP_INPUT_BORDER_ERROR, "{color.error}")
        color(TokenKeyConstants.COMP_INPUT_PLACEHOLDER, "{color.text.disabled}")
        dimension(TokenKeyConstants.COMP_INPUT_RADIUS, "{shape.radius.2}")
        dimension(TokenKeyConstants.COMP_INPUT_MIN_HEIGHT, "56dp")
        spacing(TokenKeyConstants.COMP_INPUT_PADDING_X, "{spacing.inline.sm}")
        spacing(TokenKeyConstants.COMP_INPUT_PADDING_Y, "{spacing.stack.xs}")

        // Card
        color(TokenKeyConstants.COMP_CARD_BACKGROUND, "{color.surfaceContainerLow}")
        color(TokenKeyConstants.COMP_CARD_FOREGROUND, "{color.onSurface}")
        color(TokenKeyConstants.COMP_CARD_BORDER, "{color.border.subtle}")
        dimension(TokenKeyConstants.COMP_CARD_BORDER_WIDTH, "{borderWidth.thin}")
        dimension(TokenKeyConstants.COMP_CARD_RADIUS, "{shape.radius.xl}")
        shadow(TokenKeyConstants.COMP_CARD_SHADOW, "{shadow.raised}")
        spacing(TokenKeyConstants.COMP_CARD_PADDING, "{spacing.inset.md}")

        // Badge
        color(TokenKeyConstants.COMP_BADGE_BACKGROUND, "{color.secondaryContainer}")
        color(TokenKeyConstants.COMP_BADGE_FOREGROUND, "{color.onSecondaryContainer}")
        color(TokenKeyConstants.COMP_BADGE_BORDER, "{color.border.subtle}")
        dimension(TokenKeyConstants.COMP_BADGE_BORDER_WIDTH, "{borderWidth.none}")
        dimension(TokenKeyConstants.COMP_BADGE_RADIUS, "{shape.radius.full}")
        spacing(TokenKeyConstants.COMP_BADGE_PADDING_X, "{spacing.inline.sm}")
        spacing(TokenKeyConstants.COMP_BADGE_PADDING_Y, "{spacing.stack.xs}")
        color(TokenKeyConstants.COMP_BADGE_ERROR_BACKGROUND, "{color.error}")
        color(TokenKeyConstants.COMP_BADGE_ERROR_FOREGROUND, "{color.onError}")

        // Chip
        color(TokenKeyConstants.COMP_CHIP_BACKGROUND, "{color.surface}")
        color(TokenKeyConstants.COMP_CHIP_BACKGROUND_ACTIVE, "{color.text.primary}")
        color(TokenKeyConstants.COMP_CHIP_FOREGROUND, "{color.text.primary}")
        color(TokenKeyConstants.COMP_CHIP_FOREGROUND_ACTIVE, "{color.onPrimary}")
        color(TokenKeyConstants.COMP_CHIP_BORDER, "{color.border.default}")
        dimension(TokenKeyConstants.COMP_CHIP_RADIUS, "{shape.radius.full}")

        // Modal
        color(TokenKeyConstants.COMP_MODAL_BACKGROUND, "{color.surface}")
        color(TokenKeyConstants.COMP_MODAL_FOREGROUND, "{color.onSurface}")
        color(TokenKeyConstants.COMP_MODAL_BORDER, "{color.border.subtle}")
        shadow(TokenKeyConstants.COMP_MODAL_SHADOW, "{shadow.overlay}")
        color(TokenKeyConstants.COMP_MODAL_OVERLAY, "{color.scrim}")
        dimension(TokenKeyConstants.COMP_MODAL_RADIUS, "{shape.radius.xl}")

        // Toast
        color(TokenKeyConstants.COMP_TOAST_BACKGROUND, "{color.inverseSurface}")
        color(TokenKeyConstants.COMP_TOAST_FOREGROUND, "{color.inverseOnSurface}")
        dimension(TokenKeyConstants.COMP_TOAST_RADIUS, "{shape.radius.md}")
        shadow(TokenKeyConstants.COMP_TOAST_SHADOW, "{shadow.floating}")
        spacing(TokenKeyConstants.COMP_TOAST_PADDING, "{spacing.inset.md}")
        color(TokenKeyConstants.COMP_TOAST_SUCCESS_BACKGROUND, "{color.feedback.success}")
        color(TokenKeyConstants.COMP_TOAST_SUCCESS_FOREGROUND, "{color.feedback.onSuccess}")
        color(TokenKeyConstants.COMP_TOAST_ERROR_BACKGROUND, "{color.error}")
        color(TokenKeyConstants.COMP_TOAST_ERROR_FOREGROUND, "{color.onError}")
        color(TokenKeyConstants.COMP_TOAST_WARNING_BACKGROUND, "{color.feedback.warning}")
        color(TokenKeyConstants.COMP_TOAST_WARNING_FOREGROUND, "{color.feedback.onWarning}")
        color(TokenKeyConstants.COMP_TOAST_INFO_BACKGROUND, "{color.feedback.info}")
        color(TokenKeyConstants.COMP_TOAST_INFO_FOREGROUND, "{color.feedback.onInfo}")
        string(TokenKeyConstants.COMP_TOAST_DISMISSIBLE, "true")

        // Snackbar (sits at the bottom edge of the surface — distinct from toast)
        color(TokenKeyConstants.COMP_SNACKBAR_BACKGROUND, "{color.inverseSurface}")
        color(TokenKeyConstants.COMP_SNACKBAR_FOREGROUND, "{color.inverseOnSurface}")
        dimension(TokenKeyConstants.COMP_SNACKBAR_RADIUS, "{shape.radius.md}")
        shadow(TokenKeyConstants.COMP_SNACKBAR_SHADOW, "{shadow.floating}")
        spacing(TokenKeyConstants.COMP_SNACKBAR_PADDING, "{spacing.inset.md}")

        // Tab
        color(TokenKeyConstants.COMP_TAB_BACKGROUND, "{color.surface}")
        color(TokenKeyConstants.COMP_TAB_FOREGROUND, "{color.text.secondary}")
        color(TokenKeyConstants.COMP_TAB_ACTIVE_FOREGROUND, "{color.primary}")
        color(TokenKeyConstants.COMP_TAB_ACTIVE_INDICATOR, "{color.primary}")
        color(TokenKeyConstants.COMP_TAB_BORDER, "{color.border.subtle}")

        // Navigation — side
        color(TokenKeyConstants.COMP_NAV_SIDE_BACKGROUND, "{palette.gray.100}")
        color(TokenKeyConstants.COMP_NAV_SIDE_FOREGROUND, "{color.text.primary}")
        color(TokenKeyConstants.COMP_NAV_SIDE_ACTIVE_BACKGROUND, "{palette.brand.50}")
        color(TokenKeyConstants.COMP_NAV_SIDE_ACTIVE_FOREGROUND, "{color.primary}")
        color(TokenKeyConstants.COMP_NAV_SIDE_SECTION_LABEL, "{color.text.secondary}")
        color(TokenKeyConstants.COMP_NAV_SIDE_DIVIDER, "{color.border.default}")
        dimension(TokenKeyConstants.COMP_NAV_SIDE_WIDTH, "200dp")
        dimension(TokenKeyConstants.COMP_NAV_SIDE_ITEM_HEIGHT, "40dp")

        // Navigation — top
        color(TokenKeyConstants.COMP_NAV_TOP_BACKGROUND, "{palette.blue.900}")
        color(TokenKeyConstants.COMP_NAV_TOP_FOREGROUND, "{color.onPrimary}")
        dimension(TokenKeyConstants.COMP_NAV_TOP_HEIGHT, "70dp")
        color(TokenKeyConstants.COMP_NAV_TOP_DIVIDER, "{palette.blue.600}")

        // Navigation — bottom
        color(TokenKeyConstants.COMP_NAV_BOTTOM_BACKGROUND, "{color.surface}")
        color(TokenKeyConstants.COMP_NAV_BOTTOM_FOREGROUND, "{color.text.secondary}")
        color(TokenKeyConstants.COMP_NAV_BOTTOM_ACTIVE, "{color.primary}")
        color(TokenKeyConstants.COMP_NAV_BOTTOM_DIVIDER, "{palette.blue.600}")
        dimension(TokenKeyConstants.COMP_NAV_BOTTOM_HEIGHT, "52dp")
        dimension(TokenKeyConstants.COMP_NAV_BOTTOM_TARGET, "42dp")

        // Avatar
        color(TokenKeyConstants.COMP_AVATAR_BACKGROUND, "{color.secondaryContainer}")
        color(TokenKeyConstants.COMP_AVATAR_FOREGROUND, "{color.onSecondaryContainer}")
        dimension(TokenKeyConstants.COMP_AVATAR_RADIUS, "{shape.radius.full}")
        color(TokenKeyConstants.COMP_AVATAR_BORDER, "{color.surface}")
        dimension(TokenKeyConstants.COMP_AVATAR_BORDER_WIDTH, "2dp")
        dimension(TokenKeyConstants.COMP_AVATAR_SIZE_XS, "24dp")
        dimension(TokenKeyConstants.COMP_AVATAR_SIZE_SM, "32dp")
        dimension(TokenKeyConstants.COMP_AVATAR_SIZE_MD, "40dp")
        dimension(TokenKeyConstants.COMP_AVATAR_SIZE_LG, "56dp")
        dimension(TokenKeyConstants.COMP_AVATAR_SIZE_XL, "72dp")

        // Progress
        color(TokenKeyConstants.COMP_PROGRESS_TRACK, "{palette.gray.200}")
        color(TokenKeyConstants.COMP_PROGRESS_INDICATOR, "{color.primary}")
        color(TokenKeyConstants.COMP_PROGRESS_SUCCESS, "{color.feedback.success}")
        color(TokenKeyConstants.COMP_PROGRESS_ERROR, "{color.error}")
        dimension(TokenKeyConstants.COMP_PROGRESS_TRACK_HEIGHT, "4dp")
        dimension(TokenKeyConstants.COMP_PROGRESS_RADIUS, "{shape.radius.full}")

        // List item
        color(TokenKeyConstants.COMP_LIST_ITEM_BACKGROUND, "transparent")
        color(TokenKeyConstants.COMP_LIST_ITEM_BACKGROUND_HOVER, "{color.interactive.hover}")
        color(TokenKeyConstants.COMP_LIST_ITEM_FOREGROUND, "{color.onSurface}")
        color(TokenKeyConstants.COMP_LIST_ITEM_FOREGROUND_SECONDARY, "{color.text.secondary}")
        color(TokenKeyConstants.COMP_LIST_ITEM_DIVIDER, "{color.border.subtle}")
        spacing(TokenKeyConstants.COMP_LIST_ITEM_PADDING_X, "{spacing.inline.md}")
        spacing(TokenKeyConstants.COMP_LIST_ITEM_PADDING_Y, "{spacing.stack.sm}")
        dimension(TokenKeyConstants.COMP_LIST_ITEM_RADIUS, "{shape.radius.md}")

        // Live preview (issuer-side credential preview frame)
        color(TokenKeyConstants.COMP_LIVE_PREVIEW_BACKGROUND, "{color.surface}")
        color(TokenKeyConstants.COMP_LIVE_PREVIEW_BORDER, "#8A38F5")
        dimension(TokenKeyConstants.COMP_LIVE_PREVIEW_BORDER_WIDTH, "{borderWidth.thin}")
        dimension(TokenKeyConstants.COMP_LIVE_PREVIEW_RADIUS, "{shape.radius.xl}")
        spacing(TokenKeyConstants.COMP_LIVE_PREVIEW_PADDING, "{spacing.inset.md}")

        // Checkbox
        color(TokenKeyConstants.COMP_CHECKBOX_BORDER, "{color.border.default}")
        dimension(TokenKeyConstants.COMP_CHECKBOX_BORDER_WIDTH, "{borderWidth.medium}")
        dimension(TokenKeyConstants.COMP_CHECKBOX_RADIUS, "{shape.radius.xs}")
        color(TokenKeyConstants.COMP_CHECKBOX_CHECKED_BACKGROUND, "{color.primary}")
        color(TokenKeyConstants.COMP_CHECKBOX_CHECKED_FOREGROUND, "{color.onPrimary}")
        dimension(TokenKeyConstants.COMP_CHECKBOX_SIZE, "20dp")
        color(TokenKeyConstants.COMP_CHECKBOX_LABEL_FOREGROUND, "{color.text.primary}")
        color(TokenKeyConstants.COMP_CHECKBOX_DISABLED_BACKGROUND, "{color.interactive.disabled}")

        // Radio
        color(TokenKeyConstants.COMP_RADIO_BORDER, "{color.border.default}")
        dimension(TokenKeyConstants.COMP_RADIO_BORDER_WIDTH, "{borderWidth.medium}")
        color(TokenKeyConstants.COMP_RADIO_SELECTED_BORDER, "{color.primary}")
        color(TokenKeyConstants.COMP_RADIO_SELECTED_INDICATOR, "{color.primary}")
        dimension(TokenKeyConstants.COMP_RADIO_SIZE, "20dp")
        color(TokenKeyConstants.COMP_RADIO_LABEL_FOREGROUND, "{color.text.primary}")
        color(TokenKeyConstants.COMP_RADIO_DISABLED_BORDER, "{color.border.disabled}")

        // Select / Dropdown
        color(TokenKeyConstants.COMP_SELECT_BACKGROUND, "{color.surface}")
        color(TokenKeyConstants.COMP_SELECT_FOREGROUND, "{color.onSurface}")
        color(TokenKeyConstants.COMP_SELECT_BORDER, "{color.border.default}")
        color(TokenKeyConstants.COMP_SELECT_BORDER_FOCUS, "{color.primary}")
        dimension(TokenKeyConstants.COMP_SELECT_RADIUS, "{shape.radius.sm}")
        spacing(TokenKeyConstants.COMP_SELECT_PADDING_X, "{spacing.inline.sm}")
        spacing(TokenKeyConstants.COMP_SELECT_PADDING_Y, "{spacing.stack.xs}")
        color(TokenKeyConstants.COMP_SELECT_MENU_BACKGROUND, "{color.surfaceContainerHigh}")
        shadow(TokenKeyConstants.COMP_SELECT_MENU_SHADOW, "{shadow.floating}")
        dimension(TokenKeyConstants.COMP_SELECT_MENU_RADIUS, "{shape.radius.md}")
        color(TokenKeyConstants.COMP_SELECT_OPTION_HOVER, "{color.interactive.hover}")
        color(TokenKeyConstants.COMP_SELECT_PLACEHOLDER, "{color.text.disabled}")

        // Blob explorer
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_BACKGROUND, "{color.surface}")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_FOREGROUND, "{color.onSurface}")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_BORDER, "{color.border.subtle}")
        dimension(TokenKeyConstants.COMP_BLOB_EXPLORER_RADIUS, "{shape.radius.md}")
        spacing(TokenKeyConstants.COMP_BLOB_EXPLORER_PADDING, "{spacing.inset.md}")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_TOOLBAR_BACKGROUND, "{color.surfaceContainerLow}")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_TOOLBAR_BORDER, "{color.border.subtle}")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_SIDEBAR_BACKGROUND, "{color.surfaceContainerLow}")
        dimension(TokenKeyConstants.COMP_BLOB_EXPLORER_SIDEBAR_WIDTH, "240dp")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_SIDEBAR_BORDER, "{color.border.subtle}")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_ITEM_BACKGROUND, "transparent")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_ITEM_BACKGROUND_HOVER, "{color.interactive.hover}")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_ITEM_BACKGROUND_SELECTED, "{color.secondaryContainer}")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_ITEM_FOREGROUND, "{color.onSurface}")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_ITEM_FOREGROUND_SECONDARY, "{color.text.secondary}")
        spacing(TokenKeyConstants.COMP_BLOB_EXPLORER_ITEM_PADDING, "{spacing.inset.sm}")
        dimension(TokenKeyConstants.COMP_BLOB_EXPLORER_ITEM_RADIUS, "{shape.radius.sm}")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_BREADCRUMB_FOREGROUND, "{color.text.secondary}")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_BREADCRUMB_FOREGROUND_ACTIVE, "{color.onSurface}")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_BREADCRUMB_SEPARATOR, "{color.border.default}")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_DETAIL_BACKGROUND, "{color.surfaceContainerLow}")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_DETAIL_BORDER, "{color.border.subtle}")
        dimension(TokenKeyConstants.COMP_BLOB_EXPLORER_DETAIL_WIDTH, "320dp")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_EMPTY_FOREGROUND, "{color.text.secondary}")
        color(TokenKeyConstants.COMP_BLOB_EXPLORER_EMPTY_ICON_COLOR, "{color.border.subtle}")
    }

    // ════════════════════════════════════════════════════════════════════
    // VARIANTS — color-only deltas; everything else lives in extensions.
    // ════════════════════════════════════════════════════════════════════

    /** Wallet light theme (#7C40E8 brand purple, #FBFBFB neutral surface). */
    val baseline: ThemeDefinition =
        ThemeDefinition(
            id = "system-default",
            name = "System Default (Wallet Light)",
            variant = ThemeVariant.LIGHT,
            scope = ThemeScope.SYSTEM,
            tokens =
                buildTokens {
                    // Primary
                    color(TokenKeyConstants.COLOR_PRIMARY, "{palette.brand.500}")
                    color(TokenKeyConstants.COLOR_ON_PRIMARY, "#FBFBFB")
                    color(TokenKeyConstants.COLOR_PRIMARY_CONTAINER, "{palette.brand.50}")
                    color(TokenKeyConstants.COLOR_ON_PRIMARY_CONTAINER, "{palette.brand.800}")

                    // Secondary
                    color(TokenKeyConstants.COLOR_SECONDARY, "{palette.blue.500}")
                    color(TokenKeyConstants.COLOR_ON_SECONDARY, "#FFFFFF")
                    color(TokenKeyConstants.COLOR_SECONDARY_CONTAINER, "{palette.blue.50}")
                    color(TokenKeyConstants.COLOR_ON_SECONDARY_CONTAINER, "{palette.blue.800}")

                    // Tertiary — selenas (magenta-purple), brand-adjacent category accent
                    color(TokenKeyConstants.COLOR_TERTIARY, "{palette.selenas.500}")
                    color(TokenKeyConstants.COLOR_ON_TERTIARY, "#FBFBFB")
                    color(TokenKeyConstants.COLOR_TERTIARY_CONTAINER, "{palette.selenas.50}")
                    color(TokenKeyConstants.COLOR_ON_TERTIARY_CONTAINER, "{palette.selenas.800}")

                    // Error — warm burnt orange
                    color(TokenKeyConstants.COLOR_ERROR, "{palette.error.600}")
                    color(TokenKeyConstants.COLOR_ON_ERROR, "#FFFFFF")
                    color(TokenKeyConstants.COLOR_ERROR_CONTAINER, "{palette.error.50}")
                    color(TokenKeyConstants.COLOR_ON_ERROR_CONTAINER, "{palette.error.700}")

                    // Surface
                    color(TokenKeyConstants.COLOR_SURFACE, "#FFFFFF")
                    color(TokenKeyConstants.COLOR_ON_SURFACE, "{palette.gray.900}")
                    color(TokenKeyConstants.COLOR_SURFACE_VARIANT, "{palette.gray.100}")
                    color(TokenKeyConstants.COLOR_ON_SURFACE_VARIANT, "{palette.gray.700}")
                    color(TokenKeyConstants.COLOR_SURFACE_CONTAINER, "{palette.gray.100}")
                    color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGH, "{palette.gray.200}")
                    color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGHEST, "{palette.gray.300}")
                    color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOW, "{palette.gray.50}")
                    color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOWEST, "#FFFFFF")

                    // Background
                    color(TokenKeyConstants.COLOR_BACKGROUND, "{palette.gray.50}")
                    color(TokenKeyConstants.COLOR_ON_BACKGROUND, "{palette.gray.900}")

                    // Inverse + outline + scrim
                    color(TokenKeyConstants.COLOR_OUTLINE, "{palette.gray.300}")
                    color(TokenKeyConstants.COLOR_OUTLINE_VARIANT, "{palette.gray.200}")
                    color(TokenKeyConstants.COLOR_INVERSE_SURFACE, "{palette.blue.900}")
                    color(TokenKeyConstants.COLOR_INVERSE_ON_SURFACE, "#FBFBFB")
                    color(TokenKeyConstants.COLOR_INVERSE_PRIMARY, "{palette.brand.300}")
                    color(TokenKeyConstants.COLOR_SCRIM, "rgba(10,13,18,0.5)")
                    color(TokenKeyConstants.COLOR_SHADOW, "rgba(10,13,18,0.16)")

                    // Interactive
                    color(TokenKeyConstants.COLOR_INTERACTIVE_HOVER, "{palette.gray.100}")
                    color(TokenKeyConstants.COLOR_INTERACTIVE_PRESSED, "{palette.gray.200}")
                    color(TokenKeyConstants.COLOR_INTERACTIVE_DISABLED, "{palette.gray.200}")
                    color(TokenKeyConstants.COLOR_INTERACTIVE_FOCUS, "{color.primary}")

                    // Feedback
                    color(TokenKeyConstants.COLOR_FEEDBACK_SUCCESS, "{palette.success.600}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_SUCCESS_CONTAINER, "{palette.success.50}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_SUCCESS, "#FFFFFF")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_SUCCESS_CONTAINER, "{palette.success.900}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_WARNING, "{palette.warning.600}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_WARNING_CONTAINER, "{palette.warning.50}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_WARNING, "{palette.gray.900}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_WARNING_CONTAINER, "{palette.warning.700}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_INFO, "{palette.pending.500}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_INFO_CONTAINER, "{palette.pending.50}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_INFO, "#FFFFFF")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_INFO_CONTAINER, "{palette.pending.900}")

                    // Text
                    color(TokenKeyConstants.COLOR_TEXT_PRIMARY, "{palette.gray.900}")
                    color(TokenKeyConstants.COLOR_TEXT_SECONDARY, "{palette.gray.700}")
                    color(TokenKeyConstants.COLOR_TEXT_DISABLED, "{palette.gray.400}")
                    color(TokenKeyConstants.COLOR_TEXT_INVERSE, "{color.inverseOnSurface}")

                    // Border
                    color(TokenKeyConstants.COLOR_BORDER_DEFAULT, "{palette.gray.300}")
                    color(TokenKeyConstants.COLOR_BORDER_STRONG, "{palette.gray.700}")
                    color(TokenKeyConstants.COLOR_BORDER_SUBTLE, "{palette.gray.200}")
                    color(TokenKeyConstants.COLOR_BORDER_DISABLED, "{palette.gray.200}")

                    addVariantIndependentExtensions()
                },
        )

    /** Wallet dark theme — flips Tier 2 only; palettes and components unchanged. */
    val baselineDark: ThemeDefinition =
        ThemeDefinition(
            id = "system-default-dark",
            name = "System Default (Wallet Dark)",
            variant = ThemeVariant.DARK,
            scope = ThemeScope.SYSTEM,
            tokens =
                buildTokens {
                    // Primary — lighter for AA on dark
                    color(TokenKeyConstants.COLOR_PRIMARY, "{palette.brand.300}")
                    color(TokenKeyConstants.COLOR_ON_PRIMARY, "{palette.brand.900}")
                    color(TokenKeyConstants.COLOR_PRIMARY_CONTAINER, "{palette.brand.800}")
                    color(TokenKeyConstants.COLOR_ON_PRIMARY_CONTAINER, "{palette.brand.100}")

                    // Secondary
                    color(TokenKeyConstants.COLOR_SECONDARY, "{palette.blue.200}")
                    color(TokenKeyConstants.COLOR_ON_SECONDARY, "{palette.blue.900}")
                    color(TokenKeyConstants.COLOR_SECONDARY_CONTAINER, "{palette.blue.700}")
                    color(TokenKeyConstants.COLOR_ON_SECONDARY_CONTAINER, "{palette.blue.100}")

                    // Tertiary — selenas, lifted for AA on dark
                    color(TokenKeyConstants.COLOR_TERTIARY, "{palette.selenas.300}")
                    color(TokenKeyConstants.COLOR_ON_TERTIARY, "{palette.selenas.900}")
                    color(TokenKeyConstants.COLOR_TERTIARY_CONTAINER, "{palette.selenas.800}")
                    color(TokenKeyConstants.COLOR_ON_TERTIARY_CONTAINER, "{palette.selenas.100}")

                    // Error — lift 300 for contrast on dark
                    color(TokenKeyConstants.COLOR_ERROR, "{palette.error.300}")
                    color(TokenKeyConstants.COLOR_ON_ERROR, "{palette.error.900}")
                    color(TokenKeyConstants.COLOR_ERROR_CONTAINER, "{palette.error.800}")
                    color(TokenKeyConstants.COLOR_ON_ERROR_CONTAINER, "{palette.error.100}")

                    // Surface — wallet's blue dark-panel ramp (Figma blue.900/800/700)
                    color(TokenKeyConstants.COLOR_SURFACE, "{palette.blue.800}")
                    color(TokenKeyConstants.COLOR_ON_SURFACE, "#FBFBFB")
                    color(TokenKeyConstants.COLOR_SURFACE_VARIANT, "{palette.blue.700}")
                    color(TokenKeyConstants.COLOR_ON_SURFACE_VARIANT, "{palette.gray.200}")
                    color(TokenKeyConstants.COLOR_SURFACE_CONTAINER, "{palette.blue.900}")
                    color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGH, "{palette.blue.700}")
                    color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGHEST, "{palette.blue.600}")
                    color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOW, "{palette.blue.800}")
                    color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOWEST, "{palette.blue.900}")

                    // Background
                    color(TokenKeyConstants.COLOR_BACKGROUND, "{palette.blue.900}")
                    color(TokenKeyConstants.COLOR_ON_BACKGROUND, "#FBFBFB")

                    // Inverse — toast/snackbar gets a light surface on dark page
                    color(TokenKeyConstants.COLOR_INVERSE_SURFACE, "{palette.gray.50}")
                    color(TokenKeyConstants.COLOR_INVERSE_ON_SURFACE, "{palette.gray.900}")
                    color(TokenKeyConstants.COLOR_INVERSE_PRIMARY, "{palette.brand.500}")

                    // Outlines & scrim
                    color(TokenKeyConstants.COLOR_OUTLINE, "{palette.blue.600}")
                    color(TokenKeyConstants.COLOR_OUTLINE_VARIANT, "{palette.blue.700}")
                    color(TokenKeyConstants.COLOR_SCRIM, "rgba(0,0,0,0.7)")
                    color(TokenKeyConstants.COLOR_SHADOW, "rgba(0,0,0,0.5)")

                    // Interactive
                    color(TokenKeyConstants.COLOR_INTERACTIVE_HOVER, "rgba(255,255,255,0.06)")
                    color(TokenKeyConstants.COLOR_INTERACTIVE_PRESSED, "rgba(255,255,255,0.10)")
                    color(TokenKeyConstants.COLOR_INTERACTIVE_DISABLED, "rgba(255,255,255,0.12)")
                    color(TokenKeyConstants.COLOR_INTERACTIVE_FOCUS, "{color.primary}")

                    // Feedback — wallet dark stops
                    color(TokenKeyConstants.COLOR_FEEDBACK_SUCCESS, "{palette.success.400}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_SUCCESS_CONTAINER, "{palette.success.800}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_SUCCESS, "{palette.success.900}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_SUCCESS_CONTAINER, "{palette.success.100}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_WARNING, "{palette.warning.300}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_WARNING_CONTAINER, "{palette.warning.700}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_WARNING, "{palette.warning.900}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_WARNING_CONTAINER, "{palette.warning.100}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_INFO, "{palette.pending.300}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_INFO_CONTAINER, "{palette.pending.800}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_INFO, "{palette.pending.900}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_INFO_CONTAINER, "{palette.pending.100}")

                    // Text
                    color(TokenKeyConstants.COLOR_TEXT_PRIMARY, "#FBFBFB")
                    color(TokenKeyConstants.COLOR_TEXT_SECONDARY, "{palette.gray.200}")
                    color(TokenKeyConstants.COLOR_TEXT_DISABLED, "{palette.gray.500}")
                    color(TokenKeyConstants.COLOR_TEXT_INVERSE, "{color.inverseOnSurface}")

                    // Border
                    color(TokenKeyConstants.COLOR_BORDER_DEFAULT, "{palette.blue.600}")
                    color(TokenKeyConstants.COLOR_BORDER_STRONG, "{palette.gray.200}")
                    color(TokenKeyConstants.COLOR_BORDER_SUBTLE, "{palette.blue.700}")
                    color(TokenKeyConstants.COLOR_BORDER_DISABLED, "{palette.blue.700}")

                    addVariantIndependentExtensions()

                    // Dark-mode shadow overrides — higher opacity to read on dark
                    // surfaces. These intentionally re-emit the alias names AFTER
                    // the variant-independent block so they win.
                    shadow(TokenKeyConstants.SHADOW_SUBTLE, "0 1px 2px rgba(0,0,0,0.4)")
                    shadow(TokenKeyConstants.SHADOW_RAISED, "0 2px 4px rgba(0,0,0,0.5)")
                    shadow(TokenKeyConstants.SHADOW_FLOATING, "0 4px 8px rgba(0,0,0,0.55)")
                    shadow(TokenKeyConstants.SHADOW_OVERLAY, "0 12px 24px rgba(0,0,0,0.6)")
                    shadow(TokenKeyConstants.SHADOW_ELEVATION_XS, "0 1px 2px rgba(0,0,0,0.4)")
                    shadow(TokenKeyConstants.SHADOW_ELEVATION_SM, "0 2px 4px rgba(0,0,0,0.5)")
                    shadow(TokenKeyConstants.SHADOW_ELEVATION_MD, "0 4px 8px rgba(0,0,0,0.55)")
                    shadow(TokenKeyConstants.SHADOW_ELEVATION_LG, "0 12px 24px rgba(0,0,0,0.6)")
                },
        )

    /** Wallet high-contrast theme — pushes contrast to AAA, retains brand. */
    val baselineHighContrast: ThemeDefinition =
        ThemeDefinition(
            id = "system-default-high-contrast",
            name = "System Default (Wallet High Contrast)",
            variant = ThemeVariant.HIGH_CONTRAST,
            scope = ThemeScope.SYSTEM,
            tokens =
                buildTokens {
                    // Primary — darker stops for max contrast on white
                    color(TokenKeyConstants.COLOR_PRIMARY, "{palette.brand.700}")
                    color(TokenKeyConstants.COLOR_ON_PRIMARY, "#FFFFFF")
                    color(TokenKeyConstants.COLOR_PRIMARY_CONTAINER, "{palette.brand.50}")
                    color(TokenKeyConstants.COLOR_ON_PRIMARY_CONTAINER, "{palette.brand.900}")

                    // Secondary
                    color(TokenKeyConstants.COLOR_SECONDARY, "{palette.blue.700}")
                    color(TokenKeyConstants.COLOR_ON_SECONDARY, "#FFFFFF")
                    color(TokenKeyConstants.COLOR_SECONDARY_CONTAINER, "{palette.blue.50}")
                    color(TokenKeyConstants.COLOR_ON_SECONDARY_CONTAINER, "{palette.blue.900}")

                    // Tertiary — selenas, darker stops for high-contrast AA
                    color(TokenKeyConstants.COLOR_TERTIARY, "{palette.selenas.700}")
                    color(TokenKeyConstants.COLOR_ON_TERTIARY, "#FFFFFF")
                    color(TokenKeyConstants.COLOR_TERTIARY_CONTAINER, "{palette.selenas.50}")
                    color(TokenKeyConstants.COLOR_ON_TERTIARY_CONTAINER, "{palette.selenas.900}")

                    // Error
                    color(TokenKeyConstants.COLOR_ERROR, "{palette.error.700}")
                    color(TokenKeyConstants.COLOR_ON_ERROR, "#FFFFFF")
                    color(TokenKeyConstants.COLOR_ERROR_CONTAINER, "{palette.error.50}")
                    color(TokenKeyConstants.COLOR_ON_ERROR_CONTAINER, "{palette.error.900}")

                    // Surface — pure white/black extremes
                    color(TokenKeyConstants.COLOR_SURFACE, "#FFFFFF")
                    color(TokenKeyConstants.COLOR_ON_SURFACE, "#000000")
                    color(TokenKeyConstants.COLOR_SURFACE_VARIANT, "{palette.gray.100}")
                    color(TokenKeyConstants.COLOR_ON_SURFACE_VARIANT, "#000000")
                    color(TokenKeyConstants.COLOR_SURFACE_CONTAINER, "{palette.gray.100}")
                    color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGH, "{palette.gray.200}")
                    color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGHEST, "{palette.gray.300}")
                    color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOW, "{palette.gray.50}")
                    color(TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOWEST, "#FFFFFF")

                    color(TokenKeyConstants.COLOR_BACKGROUND, "#FFFFFF")
                    color(TokenKeyConstants.COLOR_ON_BACKGROUND, "#000000")

                    // Outlines, near-black for visibility
                    color(TokenKeyConstants.COLOR_OUTLINE, "#000000")
                    color(TokenKeyConstants.COLOR_OUTLINE_VARIANT, "{palette.gray.700}")
                    color(TokenKeyConstants.COLOR_INVERSE_SURFACE, "#000000")
                    color(TokenKeyConstants.COLOR_INVERSE_ON_SURFACE, "#FFFFFF")
                    color(TokenKeyConstants.COLOR_INVERSE_PRIMARY, "{palette.brand.300}")
                    color(TokenKeyConstants.COLOR_SCRIM, "rgba(0,0,0,0.7)")
                    color(TokenKeyConstants.COLOR_SHADOW, "#000000")

                    // Interactive
                    color(TokenKeyConstants.COLOR_INTERACTIVE_HOVER, "{palette.gray.200}")
                    color(TokenKeyConstants.COLOR_INTERACTIVE_PRESSED, "{palette.gray.300}")
                    color(TokenKeyConstants.COLOR_INTERACTIVE_DISABLED, "{palette.gray.300}")
                    color(TokenKeyConstants.COLOR_INTERACTIVE_FOCUS, "{color.primary}")

                    // Feedback — darker stops
                    color(TokenKeyConstants.COLOR_FEEDBACK_SUCCESS, "{palette.success.700}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_SUCCESS_CONTAINER, "{palette.success.50}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_SUCCESS, "#FFFFFF")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_SUCCESS_CONTAINER, "{palette.success.900}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_WARNING, "{palette.warning.700}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_WARNING_CONTAINER, "{palette.warning.50}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_WARNING, "#FFFFFF")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_WARNING_CONTAINER, "{palette.warning.700}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_INFO, "{palette.pending.700}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_INFO_CONTAINER, "{palette.pending.50}")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_INFO, "#FFFFFF")
                    color(TokenKeyConstants.COLOR_FEEDBACK_ON_INFO_CONTAINER, "{palette.pending.900}")

                    // Text
                    color(TokenKeyConstants.COLOR_TEXT_PRIMARY, "#000000")
                    color(TokenKeyConstants.COLOR_TEXT_SECONDARY, "{palette.gray.800}")
                    color(TokenKeyConstants.COLOR_TEXT_DISABLED, "{palette.gray.500}")
                    color(TokenKeyConstants.COLOR_TEXT_INVERSE, "{color.inverseOnSurface}")

                    // Border
                    color(TokenKeyConstants.COLOR_BORDER_DEFAULT, "#000000")
                    color(TokenKeyConstants.COLOR_BORDER_STRONG, "#000000")
                    color(TokenKeyConstants.COLOR_BORDER_SUBTLE, "{palette.gray.500}")
                    color(TokenKeyConstants.COLOR_BORDER_DISABLED, "{palette.gray.400}")

                    addVariantIndependentExtensions()
                },
        )
}
