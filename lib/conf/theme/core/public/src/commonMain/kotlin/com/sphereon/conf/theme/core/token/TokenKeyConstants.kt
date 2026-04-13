package com.sphereon.conf.theme.core.token

import com.sphereon.core.compat.JsExportCompat

/**
 * Well-known token keys following Material Design 3 naming conventions.
 */
@JsExportCompat
object TokenKeyConstants {
    // Primary colors
    const val COLOR_PRIMARY = "color.primary"
    const val COLOR_ON_PRIMARY = "color.onPrimary"
    const val COLOR_PRIMARY_CONTAINER = "color.primaryContainer"
    const val COLOR_ON_PRIMARY_CONTAINER = "color.onPrimaryContainer"

    // Secondary colors
    const val COLOR_SECONDARY = "color.secondary"
    const val COLOR_ON_SECONDARY = "color.onSecondary"
    const val COLOR_SECONDARY_CONTAINER = "color.secondaryContainer"
    const val COLOR_ON_SECONDARY_CONTAINER = "color.onSecondaryContainer"

    // Tertiary colors
    const val COLOR_TERTIARY = "color.tertiary"
    const val COLOR_ON_TERTIARY = "color.onTertiary"
    const val COLOR_TERTIARY_CONTAINER = "color.tertiaryContainer"
    const val COLOR_ON_TERTIARY_CONTAINER = "color.onTertiaryContainer"

    // Error colors
    const val COLOR_ERROR = "color.error"
    const val COLOR_ON_ERROR = "color.onError"
    const val COLOR_ERROR_CONTAINER = "color.errorContainer"
    const val COLOR_ON_ERROR_CONTAINER = "color.onErrorContainer"

    // Surface colors
    const val COLOR_SURFACE = "color.surface"
    const val COLOR_ON_SURFACE = "color.onSurface"
    const val COLOR_SURFACE_VARIANT = "color.surfaceVariant"
    const val COLOR_ON_SURFACE_VARIANT = "color.onSurfaceVariant"
    const val COLOR_SURFACE_CONTAINER = "color.surfaceContainer"
    const val COLOR_SURFACE_CONTAINER_HIGH = "color.surfaceContainerHigh"
    const val COLOR_SURFACE_CONTAINER_HIGHEST = "color.surfaceContainerHighest"
    const val COLOR_SURFACE_CONTAINER_LOW = "color.surfaceContainerLow"
    const val COLOR_SURFACE_CONTAINER_LOWEST = "color.surfaceContainerLowest"

    // Other colors
    const val COLOR_BACKGROUND = "color.background"
    const val COLOR_ON_BACKGROUND = "color.onBackground"
    const val COLOR_OUTLINE = "color.outline"
    const val COLOR_OUTLINE_VARIANT = "color.outlineVariant"
    const val COLOR_INVERSE_SURFACE = "color.inverseSurface"
    const val COLOR_INVERSE_ON_SURFACE = "color.inverseOnSurface"
    const val COLOR_INVERSE_PRIMARY = "color.inversePrimary"
    const val COLOR_SCRIM = "color.scrim"
    const val COLOR_SHADOW = "color.shadow"

    // Elevation
    const val ELEVATION_NONE = "elevation.none"
    const val ELEVATION_XS = "elevation.xs"
    const val ELEVATION_SM = "elevation.sm"
    const val ELEVATION_MD = "elevation.md"
    const val ELEVATION_LG = "elevation.lg"
    const val ELEVATION_XL = "elevation.xl"

    // Typography — generic
    const val TYPOGRAPHY_FONT_FAMILY = "typography.fontFamily"
    const val TYPOGRAPHY_FONT_FAMILY_MONO = "typography.fontFamilyMono"

    // Typography — Display
    const val TYPOGRAPHY_DISPLAY_LARGE_FONT_FAMILY = "typography.displayLarge.fontFamily"
    const val TYPOGRAPHY_DISPLAY_LARGE_FONT_SIZE = "typography.displayLarge.fontSize"
    const val TYPOGRAPHY_DISPLAY_LARGE_FONT_WEIGHT = "typography.displayLarge.fontWeight"
    const val TYPOGRAPHY_DISPLAY_LARGE_LINE_HEIGHT = "typography.displayLarge.lineHeight"
    const val TYPOGRAPHY_DISPLAY_LARGE_LETTER_SPACING = "typography.displayLarge.letterSpacing"

    const val TYPOGRAPHY_DISPLAY_MEDIUM_FONT_FAMILY = "typography.displayMedium.fontFamily"
    const val TYPOGRAPHY_DISPLAY_MEDIUM_FONT_SIZE = "typography.displayMedium.fontSize"
    const val TYPOGRAPHY_DISPLAY_MEDIUM_FONT_WEIGHT = "typography.displayMedium.fontWeight"
    const val TYPOGRAPHY_DISPLAY_MEDIUM_LINE_HEIGHT = "typography.displayMedium.lineHeight"
    const val TYPOGRAPHY_DISPLAY_MEDIUM_LETTER_SPACING = "typography.displayMedium.letterSpacing"

    const val TYPOGRAPHY_DISPLAY_SMALL_FONT_FAMILY = "typography.displaySmall.fontFamily"
    const val TYPOGRAPHY_DISPLAY_SMALL_FONT_SIZE = "typography.displaySmall.fontSize"
    const val TYPOGRAPHY_DISPLAY_SMALL_FONT_WEIGHT = "typography.displaySmall.fontWeight"
    const val TYPOGRAPHY_DISPLAY_SMALL_LINE_HEIGHT = "typography.displaySmall.lineHeight"
    const val TYPOGRAPHY_DISPLAY_SMALL_LETTER_SPACING = "typography.displaySmall.letterSpacing"

    // Typography — Headline
    const val TYPOGRAPHY_HEADLINE_LARGE_FONT_FAMILY = "typography.headlineLarge.fontFamily"
    const val TYPOGRAPHY_HEADLINE_LARGE_FONT_SIZE = "typography.headlineLarge.fontSize"
    const val TYPOGRAPHY_HEADLINE_LARGE_FONT_WEIGHT = "typography.headlineLarge.fontWeight"
    const val TYPOGRAPHY_HEADLINE_LARGE_LINE_HEIGHT = "typography.headlineLarge.lineHeight"
    const val TYPOGRAPHY_HEADLINE_LARGE_LETTER_SPACING = "typography.headlineLarge.letterSpacing"

    const val TYPOGRAPHY_HEADLINE_MEDIUM_FONT_FAMILY = "typography.headlineMedium.fontFamily"
    const val TYPOGRAPHY_HEADLINE_MEDIUM_FONT_SIZE = "typography.headlineMedium.fontSize"
    const val TYPOGRAPHY_HEADLINE_MEDIUM_FONT_WEIGHT = "typography.headlineMedium.fontWeight"
    const val TYPOGRAPHY_HEADLINE_MEDIUM_LINE_HEIGHT = "typography.headlineMedium.lineHeight"
    const val TYPOGRAPHY_HEADLINE_MEDIUM_LETTER_SPACING = "typography.headlineMedium.letterSpacing"

    const val TYPOGRAPHY_HEADLINE_SMALL_FONT_FAMILY = "typography.headlineSmall.fontFamily"
    const val TYPOGRAPHY_HEADLINE_SMALL_FONT_SIZE = "typography.headlineSmall.fontSize"
    const val TYPOGRAPHY_HEADLINE_SMALL_FONT_WEIGHT = "typography.headlineSmall.fontWeight"
    const val TYPOGRAPHY_HEADLINE_SMALL_LINE_HEIGHT = "typography.headlineSmall.lineHeight"
    const val TYPOGRAPHY_HEADLINE_SMALL_LETTER_SPACING = "typography.headlineSmall.letterSpacing"

    // Typography — Title
    const val TYPOGRAPHY_TITLE_LARGE_FONT_FAMILY = "typography.titleLarge.fontFamily"
    const val TYPOGRAPHY_TITLE_LARGE_FONT_SIZE = "typography.titleLarge.fontSize"
    const val TYPOGRAPHY_TITLE_LARGE_FONT_WEIGHT = "typography.titleLarge.fontWeight"
    const val TYPOGRAPHY_TITLE_LARGE_LINE_HEIGHT = "typography.titleLarge.lineHeight"
    const val TYPOGRAPHY_TITLE_LARGE_LETTER_SPACING = "typography.titleLarge.letterSpacing"

    const val TYPOGRAPHY_TITLE_MEDIUM_FONT_FAMILY = "typography.titleMedium.fontFamily"
    const val TYPOGRAPHY_TITLE_MEDIUM_FONT_SIZE = "typography.titleMedium.fontSize"
    const val TYPOGRAPHY_TITLE_MEDIUM_FONT_WEIGHT = "typography.titleMedium.fontWeight"
    const val TYPOGRAPHY_TITLE_MEDIUM_LINE_HEIGHT = "typography.titleMedium.lineHeight"
    const val TYPOGRAPHY_TITLE_MEDIUM_LETTER_SPACING = "typography.titleMedium.letterSpacing"

    const val TYPOGRAPHY_TITLE_SMALL_FONT_FAMILY = "typography.titleSmall.fontFamily"
    const val TYPOGRAPHY_TITLE_SMALL_FONT_SIZE = "typography.titleSmall.fontSize"
    const val TYPOGRAPHY_TITLE_SMALL_FONT_WEIGHT = "typography.titleSmall.fontWeight"
    const val TYPOGRAPHY_TITLE_SMALL_LINE_HEIGHT = "typography.titleSmall.lineHeight"
    const val TYPOGRAPHY_TITLE_SMALL_LETTER_SPACING = "typography.titleSmall.letterSpacing"

    // Typography — Body
    const val TYPOGRAPHY_BODY_LARGE_FONT_FAMILY = "typography.bodyLarge.fontFamily"
    const val TYPOGRAPHY_BODY_LARGE_FONT_SIZE = "typography.bodyLarge.fontSize"
    const val TYPOGRAPHY_BODY_LARGE_FONT_WEIGHT = "typography.bodyLarge.fontWeight"
    const val TYPOGRAPHY_BODY_LARGE_LINE_HEIGHT = "typography.bodyLarge.lineHeight"
    const val TYPOGRAPHY_BODY_LARGE_LETTER_SPACING = "typography.bodyLarge.letterSpacing"

    const val TYPOGRAPHY_BODY_MEDIUM_FONT_FAMILY = "typography.bodyMedium.fontFamily"
    const val TYPOGRAPHY_BODY_MEDIUM_FONT_SIZE = "typography.bodyMedium.fontSize"
    const val TYPOGRAPHY_BODY_MEDIUM_FONT_WEIGHT = "typography.bodyMedium.fontWeight"
    const val TYPOGRAPHY_BODY_MEDIUM_LINE_HEIGHT = "typography.bodyMedium.lineHeight"
    const val TYPOGRAPHY_BODY_MEDIUM_LETTER_SPACING = "typography.bodyMedium.letterSpacing"

    const val TYPOGRAPHY_BODY_SMALL_FONT_FAMILY = "typography.bodySmall.fontFamily"
    const val TYPOGRAPHY_BODY_SMALL_FONT_SIZE = "typography.bodySmall.fontSize"
    const val TYPOGRAPHY_BODY_SMALL_FONT_WEIGHT = "typography.bodySmall.fontWeight"
    const val TYPOGRAPHY_BODY_SMALL_LINE_HEIGHT = "typography.bodySmall.lineHeight"
    const val TYPOGRAPHY_BODY_SMALL_LETTER_SPACING = "typography.bodySmall.letterSpacing"

    // Typography — Label
    const val TYPOGRAPHY_LABEL_LARGE_FONT_FAMILY = "typography.labelLarge.fontFamily"
    const val TYPOGRAPHY_LABEL_LARGE_FONT_SIZE = "typography.labelLarge.fontSize"
    const val TYPOGRAPHY_LABEL_LARGE_FONT_WEIGHT = "typography.labelLarge.fontWeight"
    const val TYPOGRAPHY_LABEL_LARGE_LINE_HEIGHT = "typography.labelLarge.lineHeight"
    const val TYPOGRAPHY_LABEL_LARGE_LETTER_SPACING = "typography.labelLarge.letterSpacing"

    const val TYPOGRAPHY_LABEL_MEDIUM_FONT_FAMILY = "typography.labelMedium.fontFamily"
    const val TYPOGRAPHY_LABEL_MEDIUM_FONT_SIZE = "typography.labelMedium.fontSize"
    const val TYPOGRAPHY_LABEL_MEDIUM_FONT_WEIGHT = "typography.labelMedium.fontWeight"
    const val TYPOGRAPHY_LABEL_MEDIUM_LINE_HEIGHT = "typography.labelMedium.lineHeight"
    const val TYPOGRAPHY_LABEL_MEDIUM_LETTER_SPACING = "typography.labelMedium.letterSpacing"

    const val TYPOGRAPHY_LABEL_SMALL_FONT_FAMILY = "typography.labelSmall.fontFamily"
    const val TYPOGRAPHY_LABEL_SMALL_FONT_SIZE = "typography.labelSmall.fontSize"
    const val TYPOGRAPHY_LABEL_SMALL_FONT_WEIGHT = "typography.labelSmall.fontWeight"
    const val TYPOGRAPHY_LABEL_SMALL_LINE_HEIGHT = "typography.labelSmall.lineHeight"
    const val TYPOGRAPHY_LABEL_SMALL_LETTER_SPACING = "typography.labelSmall.letterSpacing"

    // Branding
    const val BRANDING_APP_NAME = "branding.appName"
    const val BRANDING_PRIMARY_COLOR = "branding.primaryColor"
    const val BRANDING_SECONDARY_COLOR = "branding.secondaryColor"
    const val BRANDING_LOGO_URL = "branding.logoUrl"
    const val BRANDING_LOGO_DARK_URL = "branding.logoDarkUrl"
    const val BRANDING_FAVICON_URL = "branding.faviconUrl"
    const val BRANDING_FONT_RESOURCE_ID = "branding.fontResourceId"
    const val BRANDING_LOGO_RESOURCE_ID = "branding.logoResourceId"
    const val BRANDING_LOGO_DARK_RESOURCE_ID = "branding.logoDarkResourceId"

    // Shape
    const val SHAPE_CORNER_EXTRA_SMALL = "shape.cornerExtraSmall"
    const val SHAPE_CORNER_SMALL = "shape.cornerSmall"
    const val SHAPE_CORNER_MEDIUM = "shape.cornerMedium"
    const val SHAPE_CORNER_LARGE = "shape.cornerLarge"
    const val SHAPE_CORNER_EXTRA_LARGE = "shape.cornerExtraLarge"

    // Motion — durations (M3 motion spec)
    const val MOTION_DURATION_SHORT1 = "motion.duration.short1"
    const val MOTION_DURATION_SHORT2 = "motion.duration.short2"
    const val MOTION_DURATION_SHORT3 = "motion.duration.short3"
    const val MOTION_DURATION_SHORT4 = "motion.duration.short4"
    const val MOTION_DURATION_MEDIUM1 = "motion.duration.medium1"
    const val MOTION_DURATION_MEDIUM2 = "motion.duration.medium2"
    const val MOTION_DURATION_MEDIUM3 = "motion.duration.medium3"
    const val MOTION_DURATION_MEDIUM4 = "motion.duration.medium4"
    const val MOTION_DURATION_LONG1 = "motion.duration.long1"
    const val MOTION_DURATION_LONG2 = "motion.duration.long2"
    const val MOTION_DURATION_LONG3 = "motion.duration.long3"
    const val MOTION_DURATION_LONG4 = "motion.duration.long4"

    // Motion — easing (M3 motion spec, cubic-bezier values)
    const val MOTION_EASING_STANDARD = "motion.easing.standard"
    const val MOTION_EASING_STANDARD_DECELERATE = "motion.easing.standardDecelerate"
    const val MOTION_EASING_STANDARD_ACCELERATE = "motion.easing.standardAccelerate"
    const val MOTION_EASING_EMPHASIZED = "motion.easing.emphasized"
    const val MOTION_EASING_EMPHASIZED_DECELERATE = "motion.easing.emphasizedDecelerate"
    const val MOTION_EASING_EMPHASIZED_ACCELERATE = "motion.easing.emphasizedAccelerate"
    const val MOTION_EASING_LINEAR = "motion.easing.linear"

    // Motion — theme transition (used by AnimatedColorScheme)
    const val MOTION_THEME_TRANSITION_DURATION = "motion.themeTransition.duration"
    const val MOTION_THEME_TRANSITION_EASING = "motion.themeTransition.easing"

    // Responsive — typography scale factors per WindowWidthSizeClass
    const val RESPONSIVE_SCALE_MEDIUM_DISPLAY = "responsive.scale.medium.display"
    const val RESPONSIVE_SCALE_MEDIUM_HEADLINE = "responsive.scale.medium.headline"
    const val RESPONSIVE_SCALE_EXPANDED_DISPLAY = "responsive.scale.expanded.display"
    const val RESPONSIVE_SCALE_EXPANDED_HEADLINE = "responsive.scale.expanded.headline"
    const val RESPONSIVE_SCALE_EXPANDED_TITLE = "responsive.scale.expanded.title"

    // ── Spacing (4px grid, 17-stop scale) ─────────────────────────────
    const val SPACING_0 = "spacing.0"
    const val SPACING_1 = "spacing.1"
    const val SPACING_2 = "spacing.2"
    const val SPACING_3 = "spacing.3"
    const val SPACING_4 = "spacing.4"
    const val SPACING_5 = "spacing.5"
    const val SPACING_6 = "spacing.6"
    const val SPACING_8 = "spacing.8"
    const val SPACING_10 = "spacing.10"
    const val SPACING_12 = "spacing.12"
    const val SPACING_14 = "spacing.14"
    const val SPACING_16 = "spacing.16"
    const val SPACING_20 = "spacing.20"
    const val SPACING_24 = "spacing.24"
    const val SPACING_32 = "spacing.32"
    const val SPACING_40 = "spacing.40"
    const val SPACING_48 = "spacing.48"

    // Spacing — semantic (Tier 2, reference Tier 1 primitives)
    const val SPACING_INLINE_XS = "spacing.inline.xs"
    const val SPACING_INLINE_SM = "spacing.inline.sm"
    const val SPACING_INLINE_MD = "spacing.inline.md"
    const val SPACING_INLINE_LG = "spacing.inline.lg"
    const val SPACING_INLINE_XL = "spacing.inline.xl"

    const val SPACING_STACK_XS = "spacing.stack.xs"
    const val SPACING_STACK_SM = "spacing.stack.sm"
    const val SPACING_STACK_MD = "spacing.stack.md"
    const val SPACING_STACK_LG = "spacing.stack.lg"
    const val SPACING_STACK_XL = "spacing.stack.xl"

    const val SPACING_INSET_XS = "spacing.inset.xs"
    const val SPACING_INSET_SM = "spacing.inset.sm"
    const val SPACING_INSET_MD = "spacing.inset.md"
    const val SPACING_INSET_LG = "spacing.inset.lg"
    const val SPACING_INSET_XL = "spacing.inset.xl"

    // ── Border Width (4-stop scale) ───────────────────────────────────
    const val BORDER_WIDTH_NONE = "borderWidth.none"
    const val BORDER_WIDTH_THIN = "borderWidth.thin"
    const val BORDER_WIDTH_MEDIUM = "borderWidth.medium"
    const val BORDER_WIDTH_THICK = "borderWidth.thick"

    // ── Shadow — elevation levels ─────────────────────────────────────
    const val SHADOW_ELEVATION_NONE = "shadow.elevation.none"
    const val SHADOW_ELEVATION_XS = "shadow.elevation.xs"
    const val SHADOW_ELEVATION_SM = "shadow.elevation.sm"
    const val SHADOW_ELEVATION_MD = "shadow.elevation.md"
    const val SHADOW_ELEVATION_LG = "shadow.elevation.lg"
    const val SHADOW_ELEVATION_XL = "shadow.elevation.xl"
    const val SHADOW_ELEVATION_XXL = "shadow.elevation.xxl"

    // Shadow — semantic aliases (Tier 2)
    const val SHADOW_SUBTLE = "shadow.subtle"
    const val SHADOW_RAISED = "shadow.raised"
    const val SHADOW_FLOATING = "shadow.floating"
    const val SHADOW_OVERLAY = "shadow.overlay"
    const val SHADOW_DRAMATIC = "shadow.dramatic"

    // Shadow — state (brand-adaptive, reference {color.primary})
    const val SHADOW_STATE_FOCUS = "shadow.state.focus"
    const val SHADOW_STATE_ERROR = "shadow.state.error"
    const val SHADOW_STATE_ACTIVE = "shadow.state.active"
    const val SHADOW_STATE_SELECTED = "shadow.state.selected"

    // ── Shape / Border Radius (9-stop, platform-agnostic naming) ──────
    const val SHAPE_RADIUS_NONE = "shape.radius.none"
    const val SHAPE_RADIUS_XS = "shape.radius.xs"
    const val SHAPE_RADIUS_SM = "shape.radius.sm"
    const val SHAPE_RADIUS_MD = "shape.radius.md"
    const val SHAPE_RADIUS_LG = "shape.radius.lg"
    const val SHAPE_RADIUS_XL = "shape.radius.xl"
    const val SHAPE_RADIUS_XXL = "shape.radius.xxl"
    const val SHAPE_RADIUS_XXXL = "shape.radius.xxxl"
    const val SHAPE_RADIUS_FULL = "shape.radius.full"

    // ── Typography — extended styles ──────────────────────────────────
    const val TYPOGRAPHY_DISPLAY_EXTRA_LARGE_FONT_FAMILY = "typography.displayExtraLarge.fontFamily"
    const val TYPOGRAPHY_DISPLAY_EXTRA_LARGE_FONT_SIZE = "typography.displayExtraLarge.fontSize"
    const val TYPOGRAPHY_DISPLAY_EXTRA_LARGE_FONT_WEIGHT = "typography.displayExtraLarge.fontWeight"
    const val TYPOGRAPHY_DISPLAY_EXTRA_LARGE_LINE_HEIGHT = "typography.displayExtraLarge.lineHeight"
    const val TYPOGRAPHY_DISPLAY_EXTRA_LARGE_LETTER_SPACING = "typography.displayExtraLarge.letterSpacing"

    const val TYPOGRAPHY_BODY_EXTRA_SMALL_FONT_FAMILY = "typography.bodyExtraSmall.fontFamily"
    const val TYPOGRAPHY_BODY_EXTRA_SMALL_FONT_SIZE = "typography.bodyExtraSmall.fontSize"
    const val TYPOGRAPHY_BODY_EXTRA_SMALL_FONT_WEIGHT = "typography.bodyExtraSmall.fontWeight"
    const val TYPOGRAPHY_BODY_EXTRA_SMALL_LINE_HEIGHT = "typography.bodyExtraSmall.lineHeight"
    const val TYPOGRAPHY_BODY_EXTRA_SMALL_LETTER_SPACING = "typography.bodyExtraSmall.letterSpacing"

    // ── Motion — semantic durations ───────────────────────────────────
    const val MOTION_DURATION_INSTANT = "motion.duration.instant"
    const val MOTION_DURATION_FAST = "motion.duration.fast"
    const val MOTION_DURATION_NORMAL = "motion.duration.normal"
    const val MOTION_DURATION_SLOW = "motion.duration.slow"
    const val MOTION_DURATION_SLOWER = "motion.duration.slower"

    // Motion — delays
    const val MOTION_DELAY_NONE = "motion.delay.none"
    const val MOTION_DELAY_SHORT = "motion.delay.short"

    // Motion — additional easings
    const val MOTION_EASING_BOUNCE = "motion.easing.bounce"

    // ── Color — extended semantic (Tier 2) ────────────────────────────
    // Interactive state colors
    const val COLOR_INTERACTIVE_HOVER = "color.interactive.hover"
    const val COLOR_INTERACTIVE_PRESSED = "color.interactive.pressed"
    const val COLOR_INTERACTIVE_DISABLED = "color.interactive.disabled"
    const val COLOR_INTERACTIVE_FOCUS = "color.interactive.focus"

    // Feedback colors (success/warning/info)
    const val COLOR_FEEDBACK_SUCCESS = "color.feedback.success"
    const val COLOR_FEEDBACK_SUCCESS_CONTAINER = "color.feedback.successContainer"
    const val COLOR_FEEDBACK_ON_SUCCESS = "color.feedback.onSuccess"
    const val COLOR_FEEDBACK_ON_SUCCESS_CONTAINER = "color.feedback.onSuccessContainer"

    const val COLOR_FEEDBACK_WARNING = "color.feedback.warning"
    const val COLOR_FEEDBACK_WARNING_CONTAINER = "color.feedback.warningContainer"
    const val COLOR_FEEDBACK_ON_WARNING = "color.feedback.onWarning"
    const val COLOR_FEEDBACK_ON_WARNING_CONTAINER = "color.feedback.onWarningContainer"

    const val COLOR_FEEDBACK_INFO = "color.feedback.info"
    const val COLOR_FEEDBACK_INFO_CONTAINER = "color.feedback.infoContainer"
    const val COLOR_FEEDBACK_ON_INFO = "color.feedback.onInfo"
    const val COLOR_FEEDBACK_ON_INFO_CONTAINER = "color.feedback.onInfoContainer"

    // Text semantic colors
    const val COLOR_TEXT_PRIMARY = "color.text.primary"
    const val COLOR_TEXT_SECONDARY = "color.text.secondary"
    const val COLOR_TEXT_DISABLED = "color.text.disabled"
    const val COLOR_TEXT_INVERSE = "color.text.inverse"

    // Border semantic colors
    const val COLOR_BORDER_DEFAULT = "color.border.default"
    const val COLOR_BORDER_STRONG = "color.border.strong"
    const val COLOR_BORDER_SUBTLE = "color.border.subtle"
    const val COLOR_BORDER_DISABLED = "color.border.disabled"

    // ── Legacy alias keys (old → new mappings) ────────────────────────
    // These constants document the legacy key names for backward compatibility.
    // TokenReferenceResolver handles these via {reference} values in SystemDefaults.

    // ── Palette primitives (Tier 0) ─────────────────────────────────
    const val PALETTE_PREFIX = "palette."

    // Brand palette
    const val PALETTE_BRAND_50 = "palette.brand.50"
    const val PALETTE_BRAND_100 = "palette.brand.100"
    const val PALETTE_BRAND_200 = "palette.brand.200"
    const val PALETTE_BRAND_300 = "palette.brand.300"
    const val PALETTE_BRAND_400 = "palette.brand.400"
    const val PALETTE_BRAND_500 = "palette.brand.500"
    const val PALETTE_BRAND_600 = "palette.brand.600"
    const val PALETTE_BRAND_700 = "palette.brand.700"
    const val PALETTE_BRAND_800 = "palette.brand.800"
    const val PALETTE_BRAND_900 = "palette.brand.900"

    // Secondary palette
    const val PALETTE_SECONDARY_50 = "palette.secondary.50"
    const val PALETTE_SECONDARY_100 = "palette.secondary.100"
    const val PALETTE_SECONDARY_200 = "palette.secondary.200"
    const val PALETTE_SECONDARY_300 = "palette.secondary.300"
    const val PALETTE_SECONDARY_400 = "palette.secondary.400"
    const val PALETTE_SECONDARY_500 = "palette.secondary.500"
    const val PALETTE_SECONDARY_600 = "palette.secondary.600"
    const val PALETTE_SECONDARY_700 = "palette.secondary.700"
    const val PALETTE_SECONDARY_800 = "palette.secondary.800"
    const val PALETTE_SECONDARY_900 = "palette.secondary.900"

    // Neutral palette
    const val PALETTE_NEUTRAL_50 = "palette.neutral.50"
    const val PALETTE_NEUTRAL_100 = "palette.neutral.100"
    const val PALETTE_NEUTRAL_200 = "palette.neutral.200"
    const val PALETTE_NEUTRAL_300 = "palette.neutral.300"
    const val PALETTE_NEUTRAL_400 = "palette.neutral.400"
    const val PALETTE_NEUTRAL_500 = "palette.neutral.500"
    const val PALETTE_NEUTRAL_600 = "palette.neutral.600"
    const val PALETTE_NEUTRAL_700 = "palette.neutral.700"
    const val PALETTE_NEUTRAL_800 = "palette.neutral.800"
    const val PALETTE_NEUTRAL_900 = "palette.neutral.900"

    // Error palette
    const val PALETTE_ERROR_50 = "palette.error.50"
    const val PALETTE_ERROR_100 = "palette.error.100"
    const val PALETTE_ERROR_200 = "palette.error.200"
    const val PALETTE_ERROR_300 = "palette.error.300"
    const val PALETTE_ERROR_400 = "palette.error.400"
    const val PALETTE_ERROR_500 = "palette.error.500"
    const val PALETTE_ERROR_600 = "palette.error.600"
    const val PALETTE_ERROR_700 = "palette.error.700"
    const val PALETTE_ERROR_800 = "palette.error.800"
    const val PALETTE_ERROR_900 = "palette.error.900"

    // Success palette
    const val PALETTE_SUCCESS_50 = "palette.success.50"
    const val PALETTE_SUCCESS_100 = "palette.success.100"
    const val PALETTE_SUCCESS_200 = "palette.success.200"
    const val PALETTE_SUCCESS_300 = "palette.success.300"
    const val PALETTE_SUCCESS_400 = "palette.success.400"
    const val PALETTE_SUCCESS_500 = "palette.success.500"
    const val PALETTE_SUCCESS_600 = "palette.success.600"
    const val PALETTE_SUCCESS_700 = "palette.success.700"
    const val PALETTE_SUCCESS_800 = "palette.success.800"
    const val PALETTE_SUCCESS_900 = "palette.success.900"

    // Warning palette
    const val PALETTE_WARNING_50 = "palette.warning.50"
    const val PALETTE_WARNING_100 = "palette.warning.100"
    const val PALETTE_WARNING_200 = "palette.warning.200"
    const val PALETTE_WARNING_300 = "palette.warning.300"
    const val PALETTE_WARNING_400 = "palette.warning.400"
    const val PALETTE_WARNING_500 = "palette.warning.500"
    const val PALETTE_WARNING_600 = "palette.warning.600"
    const val PALETTE_WARNING_700 = "palette.warning.700"
    const val PALETTE_WARNING_800 = "palette.warning.800"
    const val PALETTE_WARNING_900 = "palette.warning.900"

    // Info palette
    const val PALETTE_INFO_50 = "palette.info.50"
    const val PALETTE_INFO_100 = "palette.info.100"
    const val PALETTE_INFO_200 = "palette.info.200"
    const val PALETTE_INFO_300 = "palette.info.300"
    const val PALETTE_INFO_400 = "palette.info.400"
    const val PALETTE_INFO_500 = "palette.info.500"
    const val PALETTE_INFO_600 = "palette.info.600"
    const val PALETTE_INFO_700 = "palette.info.700"
    const val PALETTE_INFO_800 = "palette.info.800"
    const val PALETTE_INFO_900 = "palette.info.900"

    // Pending palette
    const val PALETTE_PENDING_50 = "palette.pending.50"
    const val PALETTE_PENDING_100 = "palette.pending.100"
    const val PALETTE_PENDING_200 = "palette.pending.200"
    const val PALETTE_PENDING_300 = "palette.pending.300"
    const val PALETTE_PENDING_400 = "palette.pending.400"
    const val PALETTE_PENDING_500 = "palette.pending.500"
    const val PALETTE_PENDING_600 = "palette.pending.600"
    const val PALETTE_PENDING_700 = "palette.pending.700"
    const val PALETTE_PENDING_800 = "palette.pending.800"
    const val PALETTE_PENDING_900 = "palette.pending.900"

    // ── Component token namespace (Tier 3) ──────────────────────────
    // Defined in IDK model for validation; populated by EDK presets.
    // These keys are recognized by the validator but NOT populated in SystemDefaults.

    // Button — primary
    const val COMP_BUTTON_PRIMARY_BACKGROUND = "comp.button.primary.background"
    const val COMP_BUTTON_PRIMARY_FOREGROUND = "comp.button.primary.foreground"
    const val COMP_BUTTON_PRIMARY_BORDER = "comp.button.primary.border"
    const val COMP_BUTTON_PRIMARY_BORDER_WIDTH = "comp.button.primary.borderWidth"
    const val COMP_BUTTON_PRIMARY_RADIUS = "comp.button.primary.radius"
    const val COMP_BUTTON_PRIMARY_SHADOW = "comp.button.primary.shadow"
    const val COMP_BUTTON_PRIMARY_PADDING_X = "comp.button.primary.paddingX"
    const val COMP_BUTTON_PRIMARY_PADDING_Y = "comp.button.primary.paddingY"

    // Button — secondary
    const val COMP_BUTTON_SECONDARY_BACKGROUND = "comp.button.secondary.background"
    const val COMP_BUTTON_SECONDARY_FOREGROUND = "comp.button.secondary.foreground"
    const val COMP_BUTTON_SECONDARY_BORDER = "comp.button.secondary.border"
    const val COMP_BUTTON_SECONDARY_BORDER_WIDTH = "comp.button.secondary.borderWidth"
    const val COMP_BUTTON_SECONDARY_RADIUS = "comp.button.secondary.radius"

    // Button — ghost
    const val COMP_BUTTON_GHOST_BACKGROUND = "comp.button.ghost.background"
    const val COMP_BUTTON_GHOST_FOREGROUND = "comp.button.ghost.foreground"

    // Tab
    const val COMP_TAB_BACKGROUND = "comp.tab.background"
    const val COMP_TAB_FOREGROUND = "comp.tab.foreground"
    const val COMP_TAB_ACTIVE_FOREGROUND = "comp.tab.active.foreground"
    const val COMP_TAB_ACTIVE_INDICATOR = "comp.tab.active.indicator"
    const val COMP_TAB_BORDER = "comp.tab.border"

    // Modal
    const val COMP_MODAL_BACKGROUND = "comp.modal.background"
    const val COMP_MODAL_FOREGROUND = "comp.modal.foreground"
    const val COMP_MODAL_BORDER = "comp.modal.border"
    const val COMP_MODAL_SHADOW = "comp.modal.shadow"
    const val COMP_MODAL_OVERLAY = "comp.modal.overlay"
    const val COMP_MODAL_RADIUS = "comp.modal.radius"

    // Input
    const val COMP_INPUT_BACKGROUND = "comp.input.background"
    const val COMP_INPUT_FOREGROUND = "comp.input.foreground"
    const val COMP_INPUT_BORDER = "comp.input.border"
    const val COMP_INPUT_BORDER_FOCUS = "comp.input.border.focus"
    const val COMP_INPUT_PLACEHOLDER = "comp.input.placeholder"
    const val COMP_INPUT_RADIUS = "comp.input.radius"
    const val COMP_INPUT_PADDING_X = "comp.input.paddingX"
    const val COMP_INPUT_PADDING_Y = "comp.input.paddingY"

    // Card
    const val COMP_CARD_BACKGROUND = "comp.card.background"
    const val COMP_CARD_FOREGROUND = "comp.card.foreground"
    const val COMP_CARD_BORDER = "comp.card.border"
    const val COMP_CARD_BORDER_WIDTH = "comp.card.borderWidth"
    const val COMP_CARD_RADIUS = "comp.card.radius"
    const val COMP_CARD_SHADOW = "comp.card.shadow"
    const val COMP_CARD_PADDING = "comp.card.padding"

    // Badge / Chip
    const val COMP_BADGE_BACKGROUND = "comp.badge.background"
    const val COMP_BADGE_FOREGROUND = "comp.badge.foreground"
    const val COMP_BADGE_BORDER = "comp.badge.border"
    const val COMP_BADGE_BORDER_WIDTH = "comp.badge.borderWidth"
    const val COMP_BADGE_RADIUS = "comp.badge.radius"
    const val COMP_BADGE_PADDING_X = "comp.badge.paddingX"
    const val COMP_BADGE_PADDING_Y = "comp.badge.paddingY"
    const val COMP_BADGE_ERROR_BACKGROUND = "comp.badge.error.background"
    const val COMP_BADGE_ERROR_FOREGROUND = "comp.badge.error.foreground"

    // Checkbox
    const val COMP_CHECKBOX_BORDER = "comp.checkbox.border"
    const val COMP_CHECKBOX_BORDER_WIDTH = "comp.checkbox.borderWidth"
    const val COMP_CHECKBOX_RADIUS = "comp.checkbox.radius"
    const val COMP_CHECKBOX_CHECKED_BACKGROUND = "comp.checkbox.checked.background"
    const val COMP_CHECKBOX_CHECKED_FOREGROUND = "comp.checkbox.checked.foreground"
    const val COMP_CHECKBOX_SIZE = "comp.checkbox.size"
    const val COMP_CHECKBOX_LABEL_FOREGROUND = "comp.checkbox.label.foreground"
    const val COMP_CHECKBOX_DISABLED_BACKGROUND = "comp.checkbox.disabled.background"

    // Radio
    const val COMP_RADIO_BORDER = "comp.radio.border"
    const val COMP_RADIO_BORDER_WIDTH = "comp.radio.borderWidth"
    const val COMP_RADIO_SELECTED_BORDER = "comp.radio.selected.border"
    const val COMP_RADIO_SELECTED_INDICATOR = "comp.radio.selected.indicator"
    const val COMP_RADIO_SIZE = "comp.radio.size"
    const val COMP_RADIO_LABEL_FOREGROUND = "comp.radio.label.foreground"
    const val COMP_RADIO_DISABLED_BORDER = "comp.radio.disabled.border"

    // Select / Dropdown
    const val COMP_SELECT_BACKGROUND = "comp.select.background"
    const val COMP_SELECT_FOREGROUND = "comp.select.foreground"
    const val COMP_SELECT_BORDER = "comp.select.border"
    const val COMP_SELECT_BORDER_FOCUS = "comp.select.border.focus"
    const val COMP_SELECT_RADIUS = "comp.select.radius"
    const val COMP_SELECT_PADDING_X = "comp.select.paddingX"
    const val COMP_SELECT_PADDING_Y = "comp.select.paddingY"
    const val COMP_SELECT_MENU_BACKGROUND = "comp.select.menu.background"
    const val COMP_SELECT_MENU_SHADOW = "comp.select.menu.shadow"
    const val COMP_SELECT_MENU_RADIUS = "comp.select.menu.radius"
    const val COMP_SELECT_OPTION_HOVER = "comp.select.option.hover"
    const val COMP_SELECT_PLACEHOLDER = "comp.select.placeholder"

    // Toast / Notification
    const val COMP_TOAST_BACKGROUND = "comp.toast.background"
    const val COMP_TOAST_FOREGROUND = "comp.toast.foreground"
    const val COMP_TOAST_RADIUS = "comp.toast.radius"
    const val COMP_TOAST_SHADOW = "comp.toast.shadow"
    const val COMP_TOAST_PADDING = "comp.toast.padding"
    const val COMP_TOAST_SUCCESS_BACKGROUND = "comp.toast.success.background"
    const val COMP_TOAST_SUCCESS_FOREGROUND = "comp.toast.success.foreground"
    const val COMP_TOAST_ERROR_BACKGROUND = "comp.toast.error.background"
    const val COMP_TOAST_ERROR_FOREGROUND = "comp.toast.error.foreground"
    const val COMP_TOAST_WARNING_BACKGROUND = "comp.toast.warning.background"
    const val COMP_TOAST_WARNING_FOREGROUND = "comp.toast.warning.foreground"
    const val COMP_TOAST_INFO_BACKGROUND = "comp.toast.info.background"
    const val COMP_TOAST_INFO_FOREGROUND = "comp.toast.info.foreground"
    const val COMP_TOAST_DISMISSIBLE = "comp.toast.dismissible"

    // Blob Explorer
    const val COMP_BLOB_EXPLORER_BACKGROUND = "comp.blobExplorer.background"
    const val COMP_BLOB_EXPLORER_FOREGROUND = "comp.blobExplorer.foreground"
    const val COMP_BLOB_EXPLORER_BORDER = "comp.blobExplorer.border"
    const val COMP_BLOB_EXPLORER_RADIUS = "comp.blobExplorer.radius"
    const val COMP_BLOB_EXPLORER_PADDING = "comp.blobExplorer.padding"

    // Blob Explorer — toolbar
    const val COMP_BLOB_EXPLORER_TOOLBAR_BACKGROUND = "comp.blobExplorer.toolbar.background"
    const val COMP_BLOB_EXPLORER_TOOLBAR_BORDER = "comp.blobExplorer.toolbar.border"

    // Blob Explorer — sidebar
    const val COMP_BLOB_EXPLORER_SIDEBAR_BACKGROUND = "comp.blobExplorer.sidebar.background"
    const val COMP_BLOB_EXPLORER_SIDEBAR_WIDTH = "comp.blobExplorer.sidebar.width"
    const val COMP_BLOB_EXPLORER_SIDEBAR_BORDER = "comp.blobExplorer.sidebar.border"

    // Blob Explorer — item
    const val COMP_BLOB_EXPLORER_ITEM_BACKGROUND = "comp.blobExplorer.item.background"
    const val COMP_BLOB_EXPLORER_ITEM_BACKGROUND_HOVER = "comp.blobExplorer.item.backgroundHover"
    const val COMP_BLOB_EXPLORER_ITEM_BACKGROUND_SELECTED = "comp.blobExplorer.item.backgroundSelected"
    const val COMP_BLOB_EXPLORER_ITEM_FOREGROUND = "comp.blobExplorer.item.foreground"
    const val COMP_BLOB_EXPLORER_ITEM_FOREGROUND_SECONDARY = "comp.blobExplorer.item.foregroundSecondary"
    const val COMP_BLOB_EXPLORER_ITEM_PADDING = "comp.blobExplorer.item.padding"
    const val COMP_BLOB_EXPLORER_ITEM_RADIUS = "comp.blobExplorer.item.radius"

    // Blob Explorer — breadcrumb
    const val COMP_BLOB_EXPLORER_BREADCRUMB_FOREGROUND = "comp.blobExplorer.breadcrumb.foreground"
    const val COMP_BLOB_EXPLORER_BREADCRUMB_FOREGROUND_ACTIVE = "comp.blobExplorer.breadcrumb.foregroundActive"
    const val COMP_BLOB_EXPLORER_BREADCRUMB_SEPARATOR = "comp.blobExplorer.breadcrumb.separator"

    // Blob Explorer — detail pane
    const val COMP_BLOB_EXPLORER_DETAIL_BACKGROUND = "comp.blobExplorer.detail.background"
    const val COMP_BLOB_EXPLORER_DETAIL_BORDER = "comp.blobExplorer.detail.border"
    const val COMP_BLOB_EXPLORER_DETAIL_WIDTH = "comp.blobExplorer.detail.width"

    // Blob Explorer — empty state
    const val COMP_BLOB_EXPLORER_EMPTY_FOREGROUND = "comp.blobExplorer.empty.foreground"
    const val COMP_BLOB_EXPLORER_EMPTY_ICON_COLOR = "comp.blobExplorer.empty.iconColor"
}
