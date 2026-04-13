import { jsx as _jsx } from "react/jsx-runtime";
import { getThemeScriptSource } from './theme-script';
const COOKIE_NAME = 'sphereon-theme-mode';
/**
 * Renders a blocking inline script in <head> that sets data-theme
 * before React hydrates, preventing FOUC (flash of unstyled content).
 *
 * Usage in layout.tsx:
 *   <head>
 *     <ThemeScript />
 *   </head>
 */
export function ThemeScript({ defaultMode = 'system' }) {
    return (_jsx("script", { dangerouslySetInnerHTML: {
            __html: getThemeScriptSource(defaultMode, COOKIE_NAME),
        } }));
}
//# sourceMappingURL=ThemeScript.js.map