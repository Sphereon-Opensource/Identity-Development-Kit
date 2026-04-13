/**
 * Blocking inline script for FOUC prevention.
 * Runs before React hydrates to set data-theme and color-scheme on <html>.
 *
 * This function is serialized to a string and injected as an inline <script>
 * in <head> by ThemeScript.tsx. It must be self-contained (no imports).
 */
export declare function getThemeScriptSource(defaultMode: string, cookieName: string): string;
//# sourceMappingURL=theme-script.d.ts.map