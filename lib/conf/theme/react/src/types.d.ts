/**
 * Theme types aligned with IDK's ThemeDefinition, ThemeToken, ThemeVariant models.
 * These mirror the Kotlin types in idk/lib/conf/theme/core/ so that the React SDK
 * is a drop-in replacement for portal's local theme implementation.
 */
import type { ReactNode } from 'react';
/** Theme mode selection (user preference) */
export type ThemeMode = 'light' | 'dark' | 'system';
/** Resolved visual variant — maps to IDK's ThemeVariant enum */
export type ThemeVariant = 'light' | 'dark';
/** IDK token types */
export type ThemeTokenType = 'COLOR' | 'DIMENSION' | 'FONT_FAMILY' | 'FONT_WEIGHT' | 'OPACITY' | 'DURATION' | 'EASING' | 'SHADOW' | 'BORDER_WIDTH' | 'SPACING' | 'STRING';
/** Flat token map: IDK dot-key → value */
export type ThemeTokenMap = Record<string, string>;
/** Branding configuration aligned with IDK TokenKeyConstants.BRANDING_* */
export interface ThemeBranding {
    appName?: string;
    logoUrl?: string;
    logoDarkUrl?: string;
    faviconUrl?: string;
}
/** Props for ThemeProvider */
export interface ThemeProviderProps {
    children: ReactNode;
    /** Seed color for M3 palette generation */
    primaryColor?: string;
    /** Application name */
    appName?: string;
    /** Logo URL for light mode */
    logoUrl?: string;
    /** Logo URL for dark mode */
    logoDarkUrl?: string;
    /** Default mode (overridden by user preference in localStorage) */
    defaultMode?: ThemeMode;
    /** Additional token overrides (applied on top of system defaults + palette).
     *  Can be a flat map, a function receiving the resolved variant, or a variant-keyed object. */
    tokenOverrides?: ThemeTokenMap | ((variant: ThemeVariant) => ThemeTokenMap) | {
        light: ThemeTokenMap;
        dark: ThemeTokenMap;
    };
}
/** Value exposed by ThemeModeContext */
export interface ThemeModeContextValue {
    mode: ThemeMode;
    resolvedMode: ThemeVariant;
    setMode: (mode: ThemeMode) => void;
}
/** Value exposed by ThemeContext (full theme state) */
export interface ThemeContextValue extends ThemeModeContextValue {
    tokens: ThemeTokenMap;
    branding: ThemeBranding;
    primaryColor: string;
    /** Convenience accessor — same as branding.appName */
    appName: string;
}
//# sourceMappingURL=types.d.ts.map