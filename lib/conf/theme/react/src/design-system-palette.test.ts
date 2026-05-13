import { describe, it, expect } from 'vitest'
import { resolveDesignSystemPalette, DEFAULT_PALETTE_MAPPING } from './design-system-palette'
import type { PaletteScale, DesignSystemPalette, ThemeColorConfig } from './types'

const BRAND_SCALE: PaletteScale = {
  s50: '#ECE4FC', s100: '#E0D2FA', s200: '#C7ADF5',
  s300: '#AE89F1', s400: '#9564EC', s500: '#7C40E8',
  s600: '#5D1AD6', s700: '#4714A4', s800: '#320E72',
  s900: '#1C0840',
}

const STOPS = [50, 100, 200, 300, 400, 500, 600, 700, 800, 900]

function isValidHex(value: string): boolean {
  return /^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$/.test(value)
}

describe('resolveDesignSystemPalette', () => {
  describe('seed mode', () => {
    it('should produce all palette primitives', () => {
      const config: ThemeColorConfig = { mode: 'seed', seed: '#6750A4' }
      const tokens = resolveDesignSystemPalette(config, 'light')

      for (const stop of STOPS) {
        expect(tokens[`palette.brand.${stop}`]).toBeDefined()
        expect(tokens[`palette.secondary.${stop}`]).toBeDefined()
        expect(tokens[`palette.neutral.${stop}`]).toBeDefined()
        expect(tokens[`palette.error.${stop}`]).toBeDefined()
      }
    })

    it('should produce M3 semantic tokens', () => {
      const config: ThemeColorConfig = { mode: 'seed', seed: '#6750A4' }
      const tokens = resolveDesignSystemPalette(config, 'light')

      expect(tokens['color.primary']).toBeDefined()
      expect(tokens['color.onPrimary']).toBeDefined()
      expect(tokens['color.surface']).toBeDefined()
      expect(tokens['color.scrim']).toBe('#000000')
      expect(tokens['color.shadow']).toBe('#000000')
    })

    it('should be deterministic', () => {
      const config: ThemeColorConfig = { mode: 'seed', seed: '#7C40E8' }
      const tokens1 = resolveDesignSystemPalette(config, 'light')
      const tokens2 = resolveDesignSystemPalette(config, 'light')
      expect(tokens1).toEqual(tokens2)
    })

    it('should produce valid hex values', () => {
      const config: ThemeColorConfig = { mode: 'seed', seed: '#1565C0' }
      const tokens = resolveDesignSystemPalette(config, 'light')
      for (const [key, value] of Object.entries(tokens)) {
        expect(isValidHex(value), `Token ${key} has invalid hex: ${value}`).toBe(true)
      }
    })
  })

  describe('multi-seed mode', () => {
    it('should use provided seeds', () => {
      const config: ThemeColorConfig = {
        mode: 'multi-seed',
        primary: '#7C40E8',
        secondary: '#FF5722',
        neutral: '#9E9E9E',
      }
      const tokens = resolveDesignSystemPalette(config, 'light')

      expect(tokens['palette.brand.500']).toBeDefined()
      expect(tokens['palette.secondary.500']).toBeDefined()
      expect(tokens['palette.neutral.500']).toBeDefined()
      expect(tokens['color.primary']).toBeDefined()
      expect(tokens['color.secondary']).toBeDefined()
    })
  })

  describe('explicit mode', () => {
    it('should use exact colors', () => {
      const config: ThemeColorConfig = {
        mode: 'explicit',
        palettes: { brand: BRAND_SCALE },
      }
      const tokens = resolveDesignSystemPalette(config, 'light')

      expect(tokens['palette.brand.50']).toBe('#ECE4FC')
      expect(tokens['palette.brand.500']).toBe('#7C40E8')
      expect(tokens['palette.brand.900']).toBe('#1C0840')

      // color.primary should map from brand.500 in default light mapping
      expect(tokens['color.primary']).toBe('#7C40E8')
    })
  })

  describe('hybrid mode', () => {
    it('should fallback for missing scales', () => {
      const config: ThemeColorConfig = {
        mode: 'hybrid',
        palettes: { brand: BRAND_SCALE },
      }
      const tokens = resolveDesignSystemPalette(config, 'light')

      // Brand should use exact values
      expect(tokens['palette.brand.500']).toBe('#7C40E8')

      // Secondary/neutral should be generated (not undefined)
      expect(tokens['palette.secondary.500']).toBeDefined()
      expect(tokens['palette.neutral.500']).toBeDefined()
      expect(tokens['color.surface']).toBeDefined()
    })

    it('should use fallbackSeed when provided', () => {
      const config1: ThemeColorConfig = {
        mode: 'hybrid',
        palettes: { brand: BRAND_SCALE },
      }
      const config2: ThemeColorConfig = {
        mode: 'hybrid',
        palettes: { brand: BRAND_SCALE },
        fallbackSeed: '#FF0000',
      }
      const tokens1 = resolveDesignSystemPalette(config1, 'light')
      const tokens2 = resolveDesignSystemPalette(config2, 'light')

      // Different fallback seeds should produce different neutrals
      expect(tokens1['palette.neutral.500']).not.toBe(tokens2['palette.neutral.500'])
    })
  })

  describe('dark variant', () => {
    it('should use dark mapping', () => {
      const config: ThemeColorConfig = {
        mode: 'explicit',
        palettes: { brand: BRAND_SCALE },
      }
      const lightTokens = resolveDesignSystemPalette(config, 'light')
      const darkTokens = resolveDesignSystemPalette(config, 'dark')

      // Light: brand.500; Dark: brand.300 (lighter for AA on dark surfaces).
      expect(lightTokens['color.primary']).toBe('#7C40E8')
      expect(darkTokens['color.primary']).toBe('#AE89F1')
    })
  })

  describe('DEFAULT_PALETTE_MAPPING', () => {
    it('should have matching keys for light and dark', () => {
      const lightKeys = Object.keys(DEFAULT_PALETTE_MAPPING.light).sort()
      const darkKeys = Object.keys(DEFAULT_PALETTE_MAPPING.dark).sort()
      expect(lightKeys).toEqual(darkKeys)
    })

    it('should use valid stops', () => {
      const validStops = new Set(STOPS)
      for (const [key, ref] of Object.entries(DEFAULT_PALETTE_MAPPING.light)) {
        expect(validStops.has(ref.stop), `Light '${key}' uses invalid stop ${ref.stop}`).toBe(true)
      }
      for (const [key, ref] of Object.entries(DEFAULT_PALETTE_MAPPING.dark)) {
        expect(validStops.has(ref.stop), `Dark '${key}' uses invalid stop ${ref.stop}`).toBe(true)
      }
    })
  })
})
