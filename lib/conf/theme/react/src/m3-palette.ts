/**
 * M3 palette generation using HCT (Hue-Chroma-Tone) color space.
 * Direct TypeScript port of IDK's M3PaletteGenerator.kt, HctColor.kt, and TonalPalette.kt.
 * Produces identical output to the Kotlin implementation for any seed color.
 */

// --- HCT Color Space (port of HctColor.kt) ---

interface HctColor {
  hue: number
  chroma: number
  tone: number
}

function srgbToLinear(c: number): number {
  return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
}

function linearToSrgb(c: number): number {
  return c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1.0 / 2.4) - 0.055
}

function labF(t: number): number {
  const delta = 6.0 / 29.0
  return t > delta * delta * delta ? Math.cbrt(t) : t / (3.0 * delta * delta) + 4.0 / 29.0
}

function labFInv(t: number): number {
  const delta = 6.0 / 29.0
  return t > delta ? t * t * t : 3.0 * delta * delta * (t - 4.0 / 29.0)
}

function clampComponent(v: number): number {
  return Math.min(255, Math.max(0, Math.round(v * 255)))
}

function intToHex(n: number): string {
  return n.toString(16).padStart(2, '0').toUpperCase()
}

function hexToArgb(hex: string): number {
  const clean = hex.replace('#', '')
  const value = parseInt(clean, 16)
  return clean.length === 8 ? value : (0xFF000000 | value) >>> 0
}

function argbToHex(argb: number): string {
  const r = (argb >>> 16) & 0xFF
  const g = (argb >>> 8) & 0xFF
  const b = argb & 0xFF
  return `#${intToHex(r)}${intToHex(g)}${intToHex(b)}`
}

function hctFromArgb(argb: number): HctColor {
  const r = ((argb >>> 16) & 0xFF) / 255
  const g = ((argb >>> 8) & 0xFF) / 255
  const b = (argb & 0xFF) / 255

  const linR = srgbToLinear(r)
  const linG = srgbToLinear(g)
  const linB = srgbToLinear(b)

  const x = 0.4124564 * linR + 0.3575761 * linG + 0.1804375 * linB
  const y = 0.2126729 * linR + 0.7151522 * linG + 0.0721750 * linB
  const z = 0.0193339 * linR + 0.1191920 * linG + 0.9503041 * linB

  const xn = 0.95047
  const yn = 1.0
  const zn = 1.08883

  const fx = labF(x / xn)
  const fy = labF(y / yn)
  const fz = labF(z / zn)

  const lStar = 116.0 * fy - 16.0
  const a = 500.0 * (fx - fy)
  const bLab = 200.0 * (fy - fz)

  const chroma = Math.sqrt(a * a + bLab * bLab)
  let hue = Math.atan2(bLab, a) * 180.0 / Math.PI
  if (hue < 0) hue += 360.0

  return { hue, chroma, tone: lStar }
}

function hctToArgb(hue: number, chroma: number, tone: number): number {
  const hueRad = hue * Math.PI / 180.0
  const a = chroma * Math.cos(hueRad)
  const b = chroma * Math.sin(hueRad)

  const fy = (tone + 16.0) / 116.0
  const fx = a / 500.0 + fy
  const fz = fy - b / 200.0

  const xn = 0.95047
  const yn = 1.0
  const zn = 1.08883

  const x = xn * labFInv(fx)
  const y = yn * labFInv(fy)
  const z = zn * labFInv(fz)

  const linR = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
  const linG = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
  const linB = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z

  const ri = clampComponent(linearToSrgb(linR))
  const gi = clampComponent(linearToSrgb(linG))
  const bi = clampComponent(linearToSrgb(linB))

  return ((0xFF << 24) | (ri << 16) | (gi << 8) | bi) >>> 0
}

// --- Tonal Palette (port of TonalPalette.kt) ---

export interface TonalPaletteResult {
  tone0: string
  tone10: string
  tone20: string
  tone30: string
  tone40: string
  tone50: string
  tone60: string
  tone70: string
  tone80: string
  tone90: string
  tone95: string
  tone99: string
  tone100: string
}

function tonalPaletteTone(hue: number, chroma: number, tone: number): string {
  if (tone <= 0) return '#000000'
  if (tone >= 100) return '#FFFFFF'
  return argbToHex(hctToArgb(hue, chroma, tone))
}

function generateTonalPalette(hue: number, chroma: number): TonalPaletteResult {
  return {
    tone0: tonalPaletteTone(hue, chroma, 0),
    tone10: tonalPaletteTone(hue, chroma, 10),
    tone20: tonalPaletteTone(hue, chroma, 20),
    tone30: tonalPaletteTone(hue, chroma, 30),
    tone40: tonalPaletteTone(hue, chroma, 40),
    tone50: tonalPaletteTone(hue, chroma, 50),
    tone60: tonalPaletteTone(hue, chroma, 60),
    tone70: tonalPaletteTone(hue, chroma, 70),
    tone80: tonalPaletteTone(hue, chroma, 80),
    tone90: tonalPaletteTone(hue, chroma, 90),
    tone95: tonalPaletteTone(hue, chroma, 95),
    tone99: tonalPaletteTone(hue, chroma, 99),
    tone100: tonalPaletteTone(hue, chroma, 100),
  }
}

// --- M3 Palette Generator (port of M3PaletteGenerator.kt) ---

export interface TonalPaletteWithParams extends TonalPaletteResult {
  hue: number
  chroma: number
}

export interface ThemePalette {
  seedColor: string
  primary: TonalPaletteWithParams
  secondary: TonalPaletteWithParams
  tertiary: TonalPaletteWithParams
  neutral: TonalPaletteWithParams
  error: TonalPaletteWithParams
}

/** Get a hex color at any arbitrary tone (0-100) from a tonal palette */
export function toneAt(palette: TonalPaletteWithParams, tone: number): string {
  return tonalPaletteTone(palette.hue, palette.chroma, tone)
}

function generateTonalPaletteWithParams(hue: number, chroma: number): TonalPaletteWithParams {
  return {
    ...generateTonalPalette(hue, chroma),
    hue,
    chroma,
  }
}

/**
 * Generate a full M3 palette from a seed hex color.
 * Identical algorithm to IDK's M3PaletteGenerator.generate().
 */
export function generateM3Palette(seedHex: string): ThemePalette {
  const seed = hctFromArgb(hexToArgb(seedHex))

  const primary = generateTonalPaletteWithParams(seed.hue, Math.max(seed.chroma, 48.0))
  const secondary = generateTonalPaletteWithParams(seed.hue, 16.0)
  const tertiaryHue = (seed.hue + 60.0) % 360.0
  const tertiary = generateTonalPaletteWithParams(tertiaryHue, 24.0)
  const neutral = generateTonalPaletteWithParams(seed.hue, 4.0)
  const error = generateTonalPaletteWithParams(25.0, 84.0)

  return { seedColor: seedHex, primary, secondary, tertiary, neutral, error }
}

/**
 * Generate an M3 palette from multiple seed colors.
 * TypeScript port of M3PaletteGenerator.generateFromSeeds().
 */
export function generateM3PaletteFromSeeds(
  primarySeed: string,
  secondarySeed?: string,
  tertiarySeed?: string,
  neutralSeed?: string,
): ThemePalette {
  const primaryHct = hctFromArgb(hexToArgb(primarySeed))
  const primary = generateTonalPaletteWithParams(primaryHct.hue, Math.max(primaryHct.chroma, 48.0))

  let secondary: TonalPaletteWithParams
  if (secondarySeed) {
    const hct = hctFromArgb(hexToArgb(secondarySeed))
    secondary = generateTonalPaletteWithParams(hct.hue, Math.max(hct.chroma, 16.0))
  } else {
    secondary = generateTonalPaletteWithParams(primaryHct.hue, 16.0)
  }

  let tertiary: TonalPaletteWithParams
  if (tertiarySeed) {
    const hct = hctFromArgb(hexToArgb(tertiarySeed))
    tertiary = generateTonalPaletteWithParams(hct.hue, Math.max(hct.chroma, 24.0))
  } else {
    const tertiaryHue = (primaryHct.hue + 60.0) % 360.0
    tertiary = generateTonalPaletteWithParams(tertiaryHue, 24.0)
  }

  let neutral: TonalPaletteWithParams
  if (neutralSeed) {
    const hct = hctFromArgb(hexToArgb(neutralSeed))
    neutral = generateTonalPaletteWithParams(hct.hue, Math.max(hct.chroma, 4.0))
  } else {
    neutral = generateTonalPaletteWithParams(primaryHct.hue, 4.0)
  }

  const error = generateTonalPaletteWithParams(25.0, 84.0)

  return { seedColor: primarySeed, primary, secondary, tertiary, neutral, error }
}

/**
 * Map an M3 palette to IDK token keys for a given variant.
 */
export function paletteToTokens(
  palette: ThemePalette,
  variant: 'light' | 'dark',
): Record<string, string> {
  const { primary, secondary, tertiary, neutral, error } = palette

  if (variant === 'light') {
    return {
      'color.primary': primary.tone40,
      'color.onPrimary': primary.tone100,
      'color.primaryContainer': primary.tone90,
      'color.onPrimaryContainer': primary.tone10,
      'color.secondary': secondary.tone40,
      'color.onSecondary': secondary.tone100,
      'color.secondaryContainer': secondary.tone90,
      'color.onSecondaryContainer': secondary.tone10,
      'color.tertiary': tertiary.tone40,
      'color.onTertiary': tertiary.tone100,
      'color.tertiaryContainer': tertiary.tone90,
      'color.onTertiaryContainer': tertiary.tone10,
      'color.error': error.tone40,
      'color.onError': error.tone100,
      'color.errorContainer': error.tone90,
      'color.onErrorContainer': error.tone10,
      'color.surface': neutral.tone99,
      'color.onSurface': neutral.tone10,
      'color.surfaceVariant': neutral.tone90,
      'color.onSurfaceVariant': neutral.tone30,
      'color.surfaceContainer': toneAt(neutral, 94),
      'color.surfaceContainerHigh': toneAt(neutral, 92),
      'color.surfaceContainerHighest': neutral.tone90,
      'color.surfaceContainerLow': toneAt(neutral, 96),
      'color.surfaceContainerLowest': neutral.tone100,
      'color.background': neutral.tone99,
      'color.onBackground': neutral.tone10,
      'color.outline': neutral.tone50,
      'color.outlineVariant': neutral.tone80,
      'color.inverseSurface': neutral.tone20,
      'color.inverseOnSurface': neutral.tone95,
      'color.inversePrimary': primary.tone80,
      'color.scrim': '#000000',
      'color.shadow': '#000000',
    }
  }

  return {
    'color.primary': primary.tone80,
    'color.onPrimary': primary.tone20,
    'color.primaryContainer': primary.tone30,
    'color.onPrimaryContainer': primary.tone90,
    'color.secondary': secondary.tone80,
    'color.onSecondary': secondary.tone20,
    'color.secondaryContainer': secondary.tone30,
    'color.onSecondaryContainer': secondary.tone90,
    'color.tertiary': tertiary.tone80,
    'color.onTertiary': tertiary.tone20,
    'color.tertiaryContainer': tertiary.tone30,
    'color.onTertiaryContainer': tertiary.tone90,
    'color.error': error.tone80,
    'color.onError': error.tone20,
    'color.errorContainer': error.tone30,
    'color.onErrorContainer': error.tone90,
    'color.surface': toneAt(neutral, 6),
    'color.onSurface': neutral.tone90,
    'color.surfaceVariant': neutral.tone30,
    'color.onSurfaceVariant': neutral.tone80,
    'color.surfaceContainer': toneAt(neutral, 12),
    'color.surfaceContainerHigh': toneAt(neutral, 17),
    'color.surfaceContainerHighest': toneAt(neutral, 22),
    'color.surfaceContainerLow': neutral.tone10,
    'color.surfaceContainerLowest': toneAt(neutral, 4),
    'color.background': toneAt(neutral, 6),
    'color.onBackground': neutral.tone90,
    'color.outline': neutral.tone60,
    'color.outlineVariant': neutral.tone30,
    'color.inverseSurface': neutral.tone90,
    'color.inverseOnSurface': neutral.tone20,
    'color.inversePrimary': primary.tone40,
    'color.scrim': '#000000',
    'color.shadow': '#000000',
  }
}
