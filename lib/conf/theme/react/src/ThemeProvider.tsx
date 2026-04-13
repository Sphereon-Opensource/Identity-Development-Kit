'use client'

import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { resolveDesignSystemPalette } from './design-system-palette'
import { applyTokens, resolveTokenReferences } from './css-injector'
import { getSystemDefaults } from './defaults'
import type {
  ThemeVariant,
  ThemeTokenMap,
  ThemeBranding,
  ThemeModeContextValue,
  ThemeContextValue,
  ThemeProviderProps,
  ThemeColorConfig,
} from './types'
import { type ThemeMode } from './types'

const STORAGE_KEY = 'theme-mode'
const COOKIE_NAME = 'sphereon-theme-mode'

const DEFAULT_COLOR_CONFIG: ThemeColorConfig = { mode: 'seed', seed: '#7276F7' }

// Split contexts to minimize re-renders (per design doc)
export const ThemeModeContext = createContext<ThemeModeContextValue>({
  mode: 'system',
  resolvedMode: 'light',
  setMode: () => {},
})

export const ThemeContext = createContext<ThemeContextValue>({
  mode: 'system',
  resolvedMode: 'light',
  setMode: () => {},
  tokens: {},
  branding: {},
  colorConfig: DEFAULT_COLOR_CONFIG,
  appName: 'Portal',
})

function setCookie(name: string, value: string) {
  document.cookie = `${name}=${value};path=/;max-age=${365 * 24 * 60 * 60};SameSite=Lax`
}

export function ThemeProvider({
  children,
  colorConfig = DEFAULT_COLOR_CONFIG,
  paletteMapping,
  appName = 'Portal',
  logoUrl,
  logoDarkUrl,
  defaultMode = 'system',
  tokenOverrides,
}: ThemeProviderProps) {
  const [mode, setModeState] = useState<ThemeMode>(defaultMode)
  const [resolvedMode, setResolvedMode] = useState<ThemeVariant>('light')

  // Load stored mode on mount
  useEffect(() => {
    const stored = localStorage.getItem(STORAGE_KEY) as ThemeMode | null
    if (stored) setModeState(stored)
  }, [])

  // Resolve system preference
  useEffect(() => {
    if (mode === 'system') {
      const mq = window.matchMedia('(prefers-color-scheme: dark)')
      setResolvedMode(mq.matches ? 'dark' : 'light')
      const handler = (e: MediaQueryListEvent) => setResolvedMode(e.matches ? 'dark' : 'light')
      mq.addEventListener('change', handler)
      return () => mq.removeEventListener('change', handler)
    }
    setResolvedMode(mode)
  }, [mode])

  // Generate tokens and apply to DOM
  const tokens = useMemo<ThemeTokenMap>(() => {
    // Layer 1: System defaults (IDK M3 baseline)
    const systemDefaults = getSystemDefaults(resolvedMode)

    // Layer 2: Design system palette resolution
    const paletteTokens = resolveDesignSystemPalette(
      colorConfig,
      resolvedMode,
      paletteMapping,
    )

    // Layer 3: User-provided token overrides (app-specific identity)
    let overrides: ThemeTokenMap = {}
    if (typeof tokenOverrides === 'function') {
      overrides = tokenOverrides(resolvedMode)
    } else if (tokenOverrides && typeof tokenOverrides === 'object' && 'light' in tokenOverrides && 'dark' in tokenOverrides) {
      const variantOverrides = tokenOverrides as { light: ThemeTokenMap; dark: ThemeTokenMap }
      overrides = resolvedMode === 'dark' ? variantOverrides.dark : variantOverrides.light
    } else if (tokenOverrides) {
      overrides = tokenOverrides as ThemeTokenMap
    }

    const merged = { ...systemDefaults, ...paletteTokens, ...overrides }
    return resolveTokenReferences(merged)
  }, [resolvedMode, colorConfig, paletteMapping, tokenOverrides])

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', resolvedMode)
    document.documentElement.style.colorScheme = resolvedMode
    applyTokens(tokens)
  }, [tokens, resolvedMode])

  const setMode = useCallback((m: ThemeMode) => {
    setModeState(m)
    localStorage.setItem(STORAGE_KEY, m)
    setCookie(COOKIE_NAME, m)
  }, [])

  const branding = useMemo<ThemeBranding>(() => ({
    appName,
    logoUrl,
    logoDarkUrl,
  }), [appName, logoUrl, logoDarkUrl])

  const modeValue = useMemo<ThemeModeContextValue>(() => ({
    mode,
    resolvedMode,
    setMode,
  }), [mode, resolvedMode, setMode])

  const themeValue = useMemo<ThemeContextValue>(() => ({
    ...modeValue,
    tokens,
    branding,
    colorConfig,
    appName: appName ?? 'Portal',
  }), [modeValue, tokens, branding, colorConfig, appName])

  return (
    <ThemeModeContext.Provider value={modeValue}>
      <ThemeContext.Provider value={themeValue}>
        {children}
      </ThemeContext.Provider>
    </ThemeModeContext.Provider>
  )
}
