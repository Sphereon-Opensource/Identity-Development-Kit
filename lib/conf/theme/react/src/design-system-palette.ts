/**
 * Design system palette resolver for React.
 * TypeScript port of IDK's DesignSystemPaletteResolver.kt.
 *
 * Resolves ThemeColorConfig → flat token map with:
 * - Tier 0: palette.{role}.{stop} primitives
 * - Tier 2: color.primary, color.onPrimary, etc. (M3 semantic tokens)
 */

import type {
  PaletteScale,
  DesignSystemPalette,
  ThemeColorConfig,
  PaletteMapping,
  PaletteRef,
} from './types'
import type { ThemeVariant } from './types'
import { generateM3Palette, toneAt, type TonalPaletteWithParams } from './m3-palette'

/** Synthesize a PaletteScale from an M3 TonalPaletteWithParams */
function paletteScaleFromTonal(palette: TonalPaletteWithParams): PaletteScale {
  return {
    s50: toneAt(palette, 95),
    s100: toneAt(palette, 90),
    s200: toneAt(palette, 80),
    s300: toneAt(palette, 70),
    s400: toneAt(palette, 60),
    s500: toneAt(palette, 50),
    s600: toneAt(palette, 40),
    s700: toneAt(palette, 30),
    s800: toneAt(palette, 20),
    s900: toneAt(palette, 10),
  }
}

const STOPS = [50, 100, 200, 300, 400, 500, 600, 700, 800, 900] as const

/** Get a hex color from a PaletteScale by stop number */
function getStop(scale: PaletteScale, stop: number): string {
  switch (stop) {
    case 50: return scale.s50
    case 100: return scale.s100
    case 200: return scale.s200
    case 300: return scale.s300
    case 400: return scale.s400
    case 500: return scale.s500
    case 600: return scale.s600
    case 700: return scale.s700
    case 800: return scale.s800
    case 900: return scale.s900
    default: throw new Error(`Invalid palette stop: ${stop}`)
  }
}

/** Get a named scale from a DesignSystemPalette */
function getScale(palette: DesignSystemPalette, role: string): PaletteScale | undefined {
  switch (role) {
    case 'brand': return palette.brand
    case 'secondary': return palette.secondary
    case 'neutral': return palette.neutral
    case 'error': return palette.error
    case 'success': return palette.success
    case 'warning': return palette.warning
    case 'info': return palette.info
    case 'pending': return palette.pending
    default: return undefined
  }
}

/** Emit palette.{role}.{stop} Tier 0 tokens */
function emitPaletteTokens(tokens: Record<string, string>, role: string, scale: PaletteScale): void {
  for (const stop of STOPS) {
    tokens[`palette.${role}.${stop}`] = getStop(scale, stop)
  }
}

/** Build the complete token map from a DesignSystemPalette */
function buildTokenMap(
  palette: DesignSystemPalette,
  variant: ThemeVariant,
  mapping: PaletteMapping,
): Record<string, string> {
  const tokens: Record<string, string> = {}

  // Tier 0: Emit palette primitives
  emitPaletteTokens(tokens, 'brand', palette.brand)
  if (palette.secondary) emitPaletteTokens(tokens, 'secondary', palette.secondary)
  if (palette.neutral) emitPaletteTokens(tokens, 'neutral', palette.neutral)
  if (palette.error) emitPaletteTokens(tokens, 'error', palette.error)
  if (palette.success) emitPaletteTokens(tokens, 'success', palette.success)
  if (palette.warning) emitPaletteTokens(tokens, 'warning', palette.warning)
  if (palette.info) emitPaletteTokens(tokens, 'info', palette.info)
  if (palette.pending) emitPaletteTokens(tokens, 'pending', palette.pending)

  // Tier 2: Map palette stops to M3 semantic tokens
  const mappingForVariant = variant === 'dark' ? mapping.dark : mapping.light
  for (const [tokenKey, ref] of Object.entries(mappingForVariant)) {
    const scale = getScale(palette, ref.scale)
    if (!scale) continue
    tokens[tokenKey] = getStop(scale, ref.stop)
  }

  // Fixed tokens
  tokens['color.scrim'] = '#000000'
  tokens['color.shadow'] = '#000000'

  if (variant === 'light') {
    tokens['color.primary'] = getStop(palette.brand, 500)
    tokens['color.onPrimary'] = '#FBFBFB'
    tokens['color.primaryContainer'] = getStop(palette.brand, 50)
    tokens['color.interactive.hover'] = palette.neutral ? getStop(palette.neutral, 100) : '#F2F2F2'
    tokens['color.interactive.pressed'] = palette.neutral ? getStop(palette.neutral, 200) : '#E3E3E3'
    tokens['color.interactive.disabled'] = palette.neutral ? getStop(palette.neutral, 200) : '#E3E3E3'
  }

  if (variant === 'dark') {
    const brand500 = getStop(palette.brand, 500)
    tokens['color.primary'] = brand500
    tokens['color.onPrimary'] = '#FFFFFF'
    tokens['color.primaryContainer'] = `color-mix(in srgb, ${brand500} 18%, transparent)`
    tokens['color.interactive.hover'] = 'color-mix(in srgb, #FFFFFF 6%, transparent)'
    tokens['color.interactive.pressed'] = 'color-mix(in srgb, #FFFFFF 10%, transparent)'
  }

  return tokens
}

function resolveSeed(
  seed: string,
  variant: ThemeVariant,
  mapping: PaletteMapping,
): Record<string, string> {
  const m3 = generateM3Palette(seed)

  const palette: DesignSystemPalette = {
    brand: paletteScaleFromTonal(m3.primary),
    secondary: paletteScaleFromTonal(m3.secondary),
    neutral: paletteScaleFromTonal(m3.neutral),
    error: paletteScaleFromTonal(m3.error),
    success: paletteScaleFromTonal(m3.tertiary), // tertiary maps to a default utility
  }

  // Generate extended palettes for feedback colors using fixed hues
  // (matching M3PaletteGenerator.generateExtended behavior)
  const extendedM3 = generateM3Palette(seed)
  const successPalette = generateFixedTonalScale(145.0, 60.0)
  const warningPalette = generateFixedTonalScale(85.0, 70.0)
  const infoPalette = generateFixedTonalScale(250.0, 50.0)

  const fullPalette: DesignSystemPalette = {
    brand: paletteScaleFromTonal(extendedM3.primary),
    secondary: paletteScaleFromTonal(extendedM3.secondary),
    neutral: paletteScaleFromTonal(extendedM3.neutral),
    error: paletteScaleFromTonal(extendedM3.error),
    success: successPalette,
    warning: warningPalette,
    info: infoPalette,
  }

  return buildTokenMap(fullPalette, variant, mapping)
}

/** Generate a PaletteScale from fixed hue/chroma (for feedback colors) */
function generateFixedTonalScale(hue: number, chroma: number): PaletteScale {
  // Use generateM3Palette's internal toneAt mechanism
  const fakePalette = { hue, chroma } as TonalPaletteWithParams
  return {
    s50: toneAt(fakePalette, 95),
    s100: toneAt(fakePalette, 90),
    s200: toneAt(fakePalette, 80),
    s300: toneAt(fakePalette, 70),
    s400: toneAt(fakePalette, 60),
    s500: toneAt(fakePalette, 50),
    s600: toneAt(fakePalette, 40),
    s700: toneAt(fakePalette, 30),
    s800: toneAt(fakePalette, 20),
    s900: toneAt(fakePalette, 10),
  }
}

function resolveMultiSeed(
  config: Extract<ThemeColorConfig, { mode: 'multi-seed' }>,
  variant: ThemeVariant,
  mapping: PaletteMapping,
): Record<string, string> {
  const primaryM3 = generateM3Palette(config.primary)

  const secondaryM3 = config.secondary
    ? generateM3Palette(config.secondary)
    : null

  const neutralM3 = config.neutral
    ? generateM3Palette(config.neutral)
    : null

  const palette: DesignSystemPalette = {
    brand: paletteScaleFromTonal(primaryM3.primary),
    secondary: secondaryM3
      ? paletteScaleFromTonal(secondaryM3.primary)
      : paletteScaleFromTonal(primaryM3.secondary),
    neutral: neutralM3
      ? paletteScaleFromTonal(neutralM3.neutral)
      : paletteScaleFromTonal(primaryM3.neutral),
    error: paletteScaleFromTonal(primaryM3.error),
    success: generateFixedTonalScale(145.0, 60.0),
    warning: generateFixedTonalScale(85.0, 70.0),
    info: generateFixedTonalScale(250.0, 50.0),
  }

  return buildTokenMap(palette, variant, mapping)
}

function resolveExplicit(
  palettes: DesignSystemPalette,
  variant: ThemeVariant,
  mapping: PaletteMapping,
): Record<string, string> {
  return buildTokenMap(palettes, variant, mapping)
}

function resolveHybrid(
  config: Extract<ThemeColorConfig, { mode: 'hybrid' }>,
  variant: ThemeVariant,
  mapping: PaletteMapping,
): Record<string, string> {
  const seed = config.fallbackSeed ?? config.palettes.brand.s500
  const m3 = generateM3Palette(seed)

  const merged: DesignSystemPalette = {
    brand: config.palettes.brand,
    secondary: config.palettes.secondary ?? paletteScaleFromTonal(m3.secondary),
    neutral: config.palettes.neutral ?? paletteScaleFromTonal(m3.neutral),
    error: config.palettes.error ?? paletteScaleFromTonal(m3.error),
    success: config.palettes.success ?? generateFixedTonalScale(145.0, 60.0),
    warning: config.palettes.warning ?? generateFixedTonalScale(85.0, 70.0),
    info: config.palettes.info ?? generateFixedTonalScale(250.0, 50.0),
    pending: config.palettes.pending,
  }

  return buildTokenMap(merged, variant, mapping)
}

// ── Default Palette Mapping ──────────────────────────────────────────────

export const DEFAULT_PALETTE_MAPPING: PaletteMapping = {
  light: {
    'color.primary': { scale: 'brand', stop: 500 },
    'color.onPrimary': { scale: 'brand', stop: 50 },
    'color.primaryContainer': { scale: 'brand', stop: 100 },
    'color.onPrimaryContainer': { scale: 'brand', stop: 900 },
    'color.accent': { scale: 'brand', stop: 500 },
    'color.secondary': { scale: 'secondary', stop: 500 },
    'color.onSecondary': { scale: 'secondary', stop: 50 },
    'color.secondaryContainer': { scale: 'secondary', stop: 100 },
    'color.onSecondaryContainer': { scale: 'secondary', stop: 900 },
    'color.tertiary': { scale: 'brand', stop: 400 },
    'color.onTertiary': { scale: 'brand', stop: 50 },
    'color.tertiaryContainer': { scale: 'brand', stop: 100 },
    'color.onTertiaryContainer': { scale: 'brand', stop: 800 },
    'color.error': { scale: 'error', stop: 500 },
    'color.onError': { scale: 'error', stop: 50 },
    'color.errorContainer': { scale: 'error', stop: 100 },
    'color.onErrorContainer': { scale: 'error', stop: 900 },
    'color.surface': { scale: 'neutral', stop: 50 },
    'color.onSurface': { scale: 'neutral', stop: 800 },
    'color.surfaceVariant': { scale: 'neutral', stop: 100 },
    'color.onSurfaceVariant': { scale: 'neutral', stop: 700 },
    'color.surfaceContainer': { scale: 'neutral', stop: 100 },
    'color.surfaceContainerHigh': { scale: 'neutral', stop: 100 },
    'color.surfaceContainerHighest': { scale: 'neutral', stop: 200 },
    'color.surfaceContainerLow': { scale: 'neutral', stop: 50 },
    'color.surfaceContainerLowest': { scale: 'neutral', stop: 50 },
    'color.background': { scale: 'neutral', stop: 50 },
    'color.onBackground': { scale: 'neutral', stop: 800 },
    'color.outline': { scale: 'neutral', stop: 400 },
    'color.outlineVariant': { scale: 'neutral', stop: 200 },
    'color.inverseSurface': { scale: 'neutral', stop: 800 },
    'color.inverseOnSurface': { scale: 'neutral', stop: 50 },
    'color.inversePrimary': { scale: 'brand', stop: 200 },
    'color.feedback.success': { scale: 'success', stop: 600 },
    'color.feedback.successContainer': { scale: 'success', stop: 100 },
    'color.feedback.onSuccess': { scale: 'success', stop: 50 },
    'color.feedback.onSuccessContainer': { scale: 'success', stop: 900 },
    'color.feedback.warning': { scale: 'warning', stop: 600 },
    'color.feedback.warningContainer': { scale: 'warning', stop: 100 },
    'color.feedback.onWarning': { scale: 'warning', stop: 50 },
    'color.feedback.onWarningContainer': { scale: 'warning', stop: 900 },
    'color.feedback.info': { scale: 'info', stop: 600 },
    'color.feedback.infoContainer': { scale: 'info', stop: 100 },
    'color.feedback.onInfo': { scale: 'info', stop: 50 },
    'color.feedback.onInfoContainer': { scale: 'info', stop: 900 },
    'color.text.primary': { scale: 'neutral', stop: 800 },
    'color.text.secondary': { scale: 'neutral', stop: 600 },
    'color.text.disabled': { scale: 'neutral', stop: 400 },
    'color.text.inverse': { scale: 'neutral', stop: 50 },
    'color.border.default': { scale: 'neutral', stop: 300 },
    'color.border.strong': { scale: 'neutral', stop: 500 },
    'color.border.subtle': { scale: 'neutral', stop: 200 },
    'color.border.disabled': { scale: 'neutral', stop: 200 },
    'color.interactive.hover': { scale: 'neutral', stop: 100 },
    'color.interactive.pressed': { scale: 'neutral', stop: 200 },
    'color.interactive.disabled': { scale: 'neutral', stop: 200 },
    'color.interactive.focus': { scale: 'brand', stop: 500 },
  },
  dark: {
    // Wallet dark mapping: primary-filled actions use the same deep brand
    // stop as light mode and a fixed light foreground.
    'color.primary': { scale: 'brand', stop: 500 },
    'color.onPrimary': { scale: 'brand', stop: 50 },
    'color.primaryContainer': { scale: 'brand', stop: 800 },
    'color.onPrimaryContainer': { scale: 'brand', stop: 100 },
    'color.accent': { scale: 'brand', stop: 400 },
    'color.secondary': { scale: 'secondary', stop: 300 },
    'color.onSecondary': { scale: 'secondary', stop: 50 },
    'color.secondaryContainer': { scale: 'secondary', stop: 700 },
    'color.onSecondaryContainer': { scale: 'secondary', stop: 100 },
    'color.tertiary': { scale: 'brand', stop: 300 },
    'color.onTertiary': { scale: 'brand', stop: 900 },
    'color.tertiaryContainer': { scale: 'brand', stop: 700 },
    'color.onTertiaryContainer': { scale: 'brand', stop: 200 },
    'color.error': { scale: 'error', stop: 300 },
    'color.onError': { scale: 'error', stop: 50 },
    'color.errorContainer': { scale: 'error', stop: 700 },
    'color.onErrorContainer': { scale: 'error', stop: 100 },
    'color.surface': { scale: 'neutral', stop: 900 },
    'color.onSurface': { scale: 'neutral', stop: 100 },
    'color.surfaceVariant': { scale: 'neutral', stop: 700 },
    'color.onSurfaceVariant': { scale: 'neutral', stop: 200 },
    'color.surfaceContainer': { scale: 'neutral', stop: 800 },
    'color.surfaceContainerHigh': { scale: 'neutral', stop: 700 },
    'color.surfaceContainerHighest': { scale: 'neutral', stop: 600 },
    'color.surfaceContainerLow': { scale: 'neutral', stop: 900 },
    'color.surfaceContainerLowest': { scale: 'neutral', stop: 900 },
    'color.background': { scale: 'neutral', stop: 900 },
    'color.onBackground': { scale: 'neutral', stop: 100 },
    'color.outline': { scale: 'neutral', stop: 500 },
    'color.outlineVariant': { scale: 'neutral', stop: 700 },
    'color.inverseSurface': { scale: 'neutral', stop: 100 },
    'color.inverseOnSurface': { scale: 'neutral', stop: 800 },
    'color.inversePrimary': { scale: 'brand', stop: 600 },
    'color.feedback.success': { scale: 'success', stop: 600 },
    'color.feedback.successContainer': { scale: 'success', stop: 800 },
    'color.feedback.onSuccess': { scale: 'success', stop: 50 },
    'color.feedback.onSuccessContainer': { scale: 'success', stop: 100 },
    'color.feedback.warning': { scale: 'warning', stop: 400 },
    'color.feedback.warningContainer': { scale: 'warning', stop: 800 },
    'color.feedback.onWarning': { scale: 'warning', stop: 50 },
    'color.feedback.onWarningContainer': { scale: 'warning', stop: 100 },
    'color.feedback.info': { scale: 'info', stop: 400 },
    'color.feedback.infoContainer': { scale: 'info', stop: 800 },
    'color.feedback.onInfo': { scale: 'info', stop: 50 },
    'color.feedback.onInfoContainer': { scale: 'info', stop: 100 },
    'color.text.primary': { scale: 'neutral', stop: 100 },
    'color.text.secondary': { scale: 'neutral', stop: 300 },
    'color.text.disabled': { scale: 'neutral', stop: 500 },
    'color.text.inverse': { scale: 'neutral', stop: 800 },
    'color.border.default': { scale: 'neutral', stop: 600 },
    'color.border.strong': { scale: 'neutral', stop: 400 },
    'color.border.subtle': { scale: 'neutral', stop: 700 },
    'color.border.disabled': { scale: 'neutral', stop: 700 },
    'color.interactive.hover': { scale: 'brand', stop: 300 },
    'color.interactive.pressed': { scale: 'brand', stop: 400 },
    'color.interactive.disabled': { scale: 'neutral', stop: 600 },
    'color.interactive.focus': { scale: 'brand', stop: 400 },
  },
}

/**
 * Resolve a ThemeColorConfig into a flat map of token key → hex color value.
 *
 * Output always includes:
 * - Tier 0: `palette.brand.50` through `palette.brand.900` (and all provided scales)
 * - Tier 2: `color.primary`, `color.onPrimary`, etc. (mapped from palette or M3)
 */
export function resolveDesignSystemPalette(
  config: ThemeColorConfig,
  variant: ThemeVariant,
  mapping: PaletteMapping = DEFAULT_PALETTE_MAPPING,
): Record<string, string> {
  switch (config.mode) {
    case 'seed':
      return resolveSeed(config.seed, variant, mapping)
    case 'multi-seed':
      return resolveMultiSeed(config, variant, mapping)
    case 'explicit':
      return resolveExplicit(config.palettes, variant, mapping)
    case 'hybrid':
      return resolveHybrid(config, variant, mapping)
  }
}
