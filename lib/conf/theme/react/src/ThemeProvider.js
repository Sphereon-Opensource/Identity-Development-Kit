'use client';
import { jsx as _jsx } from "react/jsx-runtime";
import { createContext, useCallback, useEffect, useMemo, useState } from 'react';
import { generateM3Palette, paletteToTokens } from './m3-palette';
import { applyTokens, resolveTokenReferences } from './css-injector';
import { getSystemDefaults } from './defaults';
const STORAGE_KEY = 'theme-mode';
const COOKIE_NAME = 'sphereon-theme-mode';
// Split contexts to minimize re-renders (per design doc)
export const ThemeModeContext = createContext({
    mode: 'system',
    resolvedMode: 'light',
    setMode: () => { },
});
export const ThemeContext = createContext({
    mode: 'system',
    resolvedMode: 'light',
    setMode: () => { },
    tokens: {},
    branding: {},
    primaryColor: '#7276F7',
    appName: 'Portal',
});
function setCookie(name, value) {
    document.cookie = `${name}=${value};path=/;max-age=${365 * 24 * 60 * 60};SameSite=Lax`;
}
export function ThemeProvider({ children, primaryColor = '#7276F7', appName = 'Portal', logoUrl, logoDarkUrl, defaultMode = 'system', tokenOverrides, }) {
    const [mode, setModeState] = useState(defaultMode);
    const [resolvedMode, setResolvedMode] = useState('light');
    // Load stored mode on mount
    useEffect(() => {
        const stored = localStorage.getItem(STORAGE_KEY);
        if (stored)
            setModeState(stored);
    }, []);
    // Resolve system preference
    useEffect(() => {
        if (mode === 'system') {
            const mq = window.matchMedia('(prefers-color-scheme: dark)');
            setResolvedMode(mq.matches ? 'dark' : 'light');
            const handler = (e) => setResolvedMode(e.matches ? 'dark' : 'light');
            mq.addEventListener('change', handler);
            return () => mq.removeEventListener('change', handler);
        }
        setResolvedMode(mode);
    }, [mode]);
    // Generate tokens and apply to DOM
    const tokens = useMemo(() => {
        // Layer 1: System defaults (IDK M3 baseline)
        const systemDefaults = getSystemDefaults(resolvedMode);
        // Layer 2: Dynamic palette from seed color (M3 HCT)
        const palette = generateM3Palette(primaryColor);
        const paletteTokens = paletteToTokens(palette, resolvedMode);
        // Layer 3: User-provided token overrides (app-specific identity)
        let overrides = {};
        if (typeof tokenOverrides === 'function') {
            overrides = tokenOverrides(resolvedMode);
        }
        else if (tokenOverrides && typeof tokenOverrides === 'object' && 'light' in tokenOverrides && 'dark' in tokenOverrides) {
            const variantOverrides = tokenOverrides;
            overrides = resolvedMode === 'dark' ? variantOverrides.dark : variantOverrides.light;
        }
        else if (tokenOverrides) {
            overrides = tokenOverrides;
        }
        const merged = { ...systemDefaults, ...paletteTokens, ...overrides };
        return resolveTokenReferences(merged);
    }, [resolvedMode, primaryColor, tokenOverrides]);
    useEffect(() => {
        document.documentElement.setAttribute('data-theme', resolvedMode);
        document.documentElement.style.colorScheme = resolvedMode;
        applyTokens(tokens);
    }, [tokens, resolvedMode]);
    const setMode = useCallback((m) => {
        setModeState(m);
        localStorage.setItem(STORAGE_KEY, m);
        setCookie(COOKIE_NAME, m);
    }, []);
    const branding = useMemo(() => ({
        appName,
        logoUrl,
        logoDarkUrl,
    }), [appName, logoUrl, logoDarkUrl]);
    const modeValue = useMemo(() => ({
        mode,
        resolvedMode,
        setMode,
    }), [mode, resolvedMode, setMode]);
    const themeValue = useMemo(() => ({
        ...modeValue,
        tokens,
        branding,
        primaryColor,
        appName: appName ?? 'Portal',
    }), [modeValue, tokens, branding, primaryColor, appName]);
    return (_jsx(ThemeModeContext.Provider, { value: modeValue, children: _jsx(ThemeContext.Provider, { value: themeValue, children: children }) }));
}
//# sourceMappingURL=ThemeProvider.js.map