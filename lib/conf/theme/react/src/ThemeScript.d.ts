interface ThemeScriptProps {
    defaultMode?: string;
}
/**
 * Renders a blocking inline script in <head> that sets data-theme
 * before React hydrates, preventing FOUC (flash of unstyled content).
 *
 * Usage in layout.tsx:
 *   <head>
 *     <ThemeScript />
 *   </head>
 */
export declare function ThemeScript({ defaultMode }: ThemeScriptProps): import("react/jsx-runtime").JSX.Element;
export {};
//# sourceMappingURL=ThemeScript.d.ts.map