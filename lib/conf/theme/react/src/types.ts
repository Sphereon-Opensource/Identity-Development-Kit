/**
 * Theme types aligned with IDK's ThemeDefinition, ThemeToken, ThemeVariant models.
 * These mirror the Kotlin types in idk/lib/conf/theme/core/ so that the React SDK
 * is a drop-in replacement for portal's local theme implementation.
 */

import type { ReactNode } from 'react'

/** Theme mode selection (user preference) */
export type ThemeMode = 'light' | 'dark' | 'system'

/** Resolved visual variant — maps to IDK's ThemeVariant enum */
export type ThemeVariant = 'light' | 'dark'

/** IDK token types */
export type ThemeTokenType = 'COLOR' | 'DIMENSION' | 'FONT_FAMILY' | 'FONT_WEIGHT' | 'OPACITY' | 'DURATION' | 'EASING' | 'SHADOW' | 'BORDER_WIDTH' | 'SPACING' | 'STRING'

/** Flat token map: IDK dot-key → value */
export type ThemeTokenMap = Record<string, string>

/** Branding configuration aligned with IDK TokenKeyConstants.BRANDING_* */
export interface ThemeBranding {
  appName?: string
  logoUrl?: string
  logoDarkUrl?: string
  faviconUrl?: string
}

// ── Design System Palette Types ─────────────────────────────────────────

/** A 10-stop color scale (50–900) for a single design system palette role */
export interface PaletteScale {
  s50: string; s100: string; s200: string; s300: string; s400: string
  s500: string; s600: string; s700: string; s800: string; s900: string
}

/** Complete design system palette with role-based named scales.
 * Names are role-based, never color-descriptive (no "blue", "grey", "purple"). */
export interface DesignSystemPalette {
  brand: PaletteScale
  secondary?: PaletteScale
  neutral?: PaletteScale
  error?: PaletteScale
  success?: PaletteScale
  warning?: PaletteScale
  info?: PaletteScale
  pending?: PaletteScale
}

/** Reference from an M3 semantic token to a specific palette scale + stop */
export interface PaletteRef {
  scale: string
  stop: number
}

/** Configurable mapping from palette stops to M3 semantic token keys */
export interface PaletteMapping {
  light: Record<string, PaletteRef>
  dark: Record<string, PaletteRef>
}

/**
 * Configuration for how theme colors are sourced.
 *
 * - `seed`: Single hex color, fully algorithmic M3 generation
 * - `multi-seed`: Per-role seed colors, still algorithmic
 * - `explicit`: Full design system palette with hand-crafted 50–900 scales
 * - `hybrid`: Explicit scales where provided, M3 fallback for missing roles
 */
export type ThemeColorConfig =
  | { mode: 'seed'; seed: string }
  | { mode: 'multi-seed'; primary: string; secondary?: string; tertiary?: string; neutral?: string }
  | { mode: 'explicit'; palettes: DesignSystemPalette }
  | { mode: 'hybrid'; palettes: DesignSystemPalette; fallbackSeed?: string }

// ── ThemeProvider Props ─────────────────────────────────────────────────

/** Props for ThemeProvider */
export interface ThemeProviderProps {
  children: ReactNode
  /** Color configuration — replaces primaryColor */
  colorConfig: ThemeColorConfig
  /** Optional custom palette-to-M3 mapping */
  paletteMapping?: PaletteMapping
  /** Application name */
  appName?: string
  /** Logo URL for light mode */
  logoUrl?: string
  /** Logo URL for dark mode */
  logoDarkUrl?: string
  /** Default mode (overridden by user preference in localStorage) */
  defaultMode?: ThemeMode
  /** Additional token overrides (applied on top of system defaults + palette).
   *  Can be a flat map, a function receiving the resolved variant, or a variant-keyed object. */
  tokenOverrides?: ThemeTokenMap | ((variant: ThemeVariant) => ThemeTokenMap) | { light: ThemeTokenMap; dark: ThemeTokenMap }
}

/** Value exposed by ThemeModeContext */
export interface ThemeModeContextValue {
  mode: ThemeMode
  resolvedMode: ThemeVariant
  setMode: (mode: ThemeMode) => void
}

/** Value exposed by ThemeContext (full theme state) */
export interface ThemeContextValue extends ThemeModeContextValue {
  tokens: ThemeTokenMap
  branding: ThemeBranding
  colorConfig: ThemeColorConfig
  /** Convenience accessor — same as branding.appName */
  appName: string
}
