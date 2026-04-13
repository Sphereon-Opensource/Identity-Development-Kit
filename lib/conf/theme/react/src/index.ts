// Core provider and hooks
export { ThemeProvider, ThemeModeContext, ThemeContext } from './ThemeProvider'
export { useTheme } from './useTheme'
export { useThemeMode } from './useThemeMode'

// FOUC prevention
export { ThemeScript } from './ThemeScript'
export { getThemeScriptSource } from './theme-script'

// Server-side utilities
export { resolveInitialMode } from './server-utils'

// CSS injection
export { tokenKeyToCssVar, tokensToCssVars, applyCssVars, applyTokens, resolveTokenReferences } from './css-injector'

// M3 palette generation
export { generateM3Palette, generateM3PaletteFromSeeds, paletteToTokens, toneAt } from './m3-palette'
export type { ThemePalette, TonalPaletteResult, TonalPaletteWithParams } from './m3-palette'

// Design system palette
export { resolveDesignSystemPalette, DEFAULT_PALETTE_MAPPING } from './design-system-palette'

// System defaults
export { getSystemDefaults } from './defaults'

// Component token defaults (Tier 3)
export {
  buttonTokenDefaults,
  tabTokenDefaults,
  modalTokenDefaults,
  inputTokenDefaults,
  cardTokenDefaults,
  badgeTokenDefaults,
  checkboxTokenDefaults,
  radioTokenDefaults,
  selectTokenDefaults,
  toastTokenDefaults,
  allComponentTokenDefaults,
} from './component-token-defaults'

// Types
export type {
  ThemeMode,
  ThemeVariant,
  ThemeTokenType,
  ThemeTokenMap,
  ThemeBranding,
  ThemeProviderProps,
  ThemeModeContextValue,
  ThemeContextValue,
  PaletteScale,
  DesignSystemPalette,
  ThemeColorConfig,
  PaletteMapping,
  PaletteRef,
} from './types'
