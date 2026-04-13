/**
 * M3 palette generation using HCT (Hue-Chroma-Tone) color space.
 * Direct TypeScript port of IDK's M3PaletteGenerator.kt, HctColor.kt, and TonalPalette.kt.
 * Produces identical output to the Kotlin implementation for any seed color.
 */
export interface TonalPaletteResult {
    tone0: string;
    tone10: string;
    tone20: string;
    tone30: string;
    tone40: string;
    tone50: string;
    tone60: string;
    tone70: string;
    tone80: string;
    tone90: string;
    tone95: string;
    tone99: string;
    tone100: string;
}
export interface TonalPaletteWithParams extends TonalPaletteResult {
    hue: number;
    chroma: number;
}
export interface ThemePalette {
    seedColor: string;
    primary: TonalPaletteWithParams;
    secondary: TonalPaletteWithParams;
    tertiary: TonalPaletteWithParams;
    neutral: TonalPaletteWithParams;
    error: TonalPaletteWithParams;
}
/** Get a hex color at any arbitrary tone (0-100) from a tonal palette */
export declare function toneAt(palette: TonalPaletteWithParams, tone: number): string;
/**
 * Generate a full M3 palette from a seed hex color.
 * Identical algorithm to IDK's M3PaletteGenerator.generate().
 */
export declare function generateM3Palette(seedHex: string): ThemePalette;
/**
 * Map an M3 palette to IDK token keys for a given variant.
 */
export declare function paletteToTokens(palette: ThemePalette, variant: 'light' | 'dark'): Record<string, string>;
//# sourceMappingURL=m3-palette.d.ts.map