'use client';
import { useContext } from 'react';
import { ThemeModeContext } from './ThemeProvider';
/** Lightweight hook — only re-renders when mode changes, not when tokens change */
export function useThemeMode() {
    return useContext(ThemeModeContext);
}
//# sourceMappingURL=useThemeMode.js.map