/**
 * Maps IDK token dot-keys to CSS custom properties and applies them to :root.
 * Matches IDK's CssVariableGenerator convention: dots become hyphens.
 *
 * This is a pure TypeScript implementation mirroring the Kotlin/JS
 * CssTokenMapper + CssLegacyAliases from @sphereon/theme-web.
 */
import type { ThemeTokenMap } from './types';
/** Convert an IDK token key to a CSS custom property name */
export declare function tokenKeyToCssVar(key: string): string;
/**
 * Convert a flat IDK token map to CSS custom properties.
 * Includes legacy aliases for backwards compatibility with existing portal CSS modules.
 */
export declare function tokensToCssVars(tokens: ThemeTokenMap): Record<string, string>;
/** Apply a CSS variable map to the document root */
export declare function applyCssVars(vars: Record<string, string>): void;
/**
 * Resolve `{reference}` syntax in token values.
 * Component tokens (Tier 3) reference semantic tokens (Tier 2) using
 * `{key.path}` notation — e.g. `'{color.primary}'`.
 * This single-pass resolver replaces each reference with the concrete value
 * from the same merged map. If the referenced key is missing, it falls back
 * to a CSS `var(--key-path)` so the property still has a chance of working.
 */
export declare function resolveTokenReferences(tokens: ThemeTokenMap): ThemeTokenMap;
/** Convenience: convert IDK tokens and apply to :root in one step */
export declare function applyTokens(tokens: ThemeTokenMap): void;
//# sourceMappingURL=css-injector.d.ts.map