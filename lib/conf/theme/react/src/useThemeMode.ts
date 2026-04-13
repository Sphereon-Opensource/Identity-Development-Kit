'use client'

import { useContext } from 'react'
import { ThemeModeContext } from './ThemeProvider'
import type { ThemeModeContextValue } from './types'

/** Lightweight hook — only re-renders when mode changes, not when tokens change */
export function useThemeMode(): ThemeModeContextValue {
  return useContext(ThemeModeContext)
}
