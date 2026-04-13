'use client'

import { useContext } from 'react'
import { ThemeContext } from './ThemeProvider'
import type { ThemeContextValue } from './types'

export function useTheme(): ThemeContextValue {
  return useContext(ThemeContext)
}
