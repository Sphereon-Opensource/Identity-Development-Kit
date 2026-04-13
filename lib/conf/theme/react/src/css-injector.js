/**
 * Maps IDK token dot-keys to CSS custom properties and applies them to :root.
 * Matches IDK's CssVariableGenerator convention: dots become hyphens.
 *
 * This is a pure TypeScript implementation mirroring the Kotlin/JS
 * CssTokenMapper + CssLegacyAliases from @sphereon/theme-web.
 */
/** Convert an IDK token key to a CSS custom property name */
export function tokenKeyToCssVar(key) {
    return `--${key.replace(/\./g, '-')}`;
}
/** Convert a hex color to rgba with given opacity */
function hexToRgba(hex, alpha) {
    const clean = hex.replace('#', '');
    const r = parseInt(clean.slice(0, 2), 16);
    const g = parseInt(clean.slice(2, 4), 16);
    const b = parseInt(clean.slice(4, 6), 16);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}
/** Check if a token key is a shadow token (pass-through, no unit conversion) */
function isShadowKey(key) {
    return key.startsWith('shadow.');
}
/** Convert IDK dimension units to CSS-compatible values */
function convertUnit(value) {
    return value.replace(/(\d+(?:\.\d+)?)\s*(?:sp|dp)/g, '$1px');
}
/**
 * Convert a flat IDK token map to CSS custom properties.
 * Includes legacy aliases for backwards compatibility with existing portal CSS modules.
 */
export function tokensToCssVars(tokens) {
    const vars = {};
    for (const [key, value] of Object.entries(tokens)) {
        // Shadow values use CSS box-shadow syntax — pass through without unit conversion
        vars[tokenKeyToCssVar(key)] = isShadowKey(key) ? value : convertUnit(value);
    }
    // Legacy aliases for existing portal CSS modules
    if (tokens['color.background'])
        vars['--color-background'] = tokens['color.background'];
    if (tokens['color.onSurface'])
        vars['--color-foreground'] = tokens['color.onSurface'];
    if (tokens['color.surface'])
        vars['--color-surface'] = tokens['color.surface'];
    if (tokens['color.surfaceVariant'])
        vars['--color-surface-variant'] = tokens['color.surfaceVariant'];
    if (tokens['color.primary'])
        vars['--color-primary'] = tokens['color.primary'];
    if (tokens['color.secondary'])
        vars['--color-secondary'] = tokens['color.secondary'];
    if (tokens['color.error'])
        vars['--color-error'] = tokens['color.error'];
    if (tokens['color.outline'])
        vars['--color-outline'] = tokens['color.outline'];
    if (tokens['color.shadow'])
        vars['--color-shadow'] = tokens['color.shadow'];
    if (tokens['color.onPrimary'])
        vars['--color-on-primary'] = tokens['color.onPrimary'];
    if (tokens['color.primaryContainer'])
        vars['--color-primary-light'] = tokens['color.primaryContainer'];
    if (tokens['color.onPrimaryContainer'])
        vars['--color-primary-dark'] = tokens['color.onPrimaryContainer'];
    // Shape aliases
    if (tokens['shape.cornerExtraSmall'])
        vars['--radius-sm'] = convertUnit(tokens['shape.cornerExtraSmall']);
    if (tokens['shape.cornerSmall'])
        vars['--radius-md'] = convertUnit(tokens['shape.cornerSmall']);
    if (tokens['shape.cornerMedium'])
        vars['--radius-lg'] = convertUnit(tokens['shape.cornerMedium']);
    if (tokens['shape.cornerLarge'])
        vars['--radius-xl'] = convertUnit(tokens['shape.cornerLarge']);
    if (tokens['shape.cornerExtraLarge'])
        vars['--radius-2xl'] = convertUnit(tokens['shape.cornerExtraLarge']);
    // Web-wallet semantic aliases
    if (tokens['color.primary']) {
        const pc = tokens['color.primary'];
        vars['--color-primary-bg'] = hexToRgba(pc, 0.1);
        vars['--color-text-link'] = pc;
        vars['--color-border-focus'] = pc;
    }
    if (tokens['color.onSurface'])
        vars['--color-text-primary'] = tokens['color.onSurface'];
    if (tokens['color.onSurfaceVariant'])
        vars['--color-text-secondary'] = tokens['color.onSurfaceVariant'];
    if (tokens['color.background'])
        vars['--color-bg-primary'] = tokens['color.background'];
    if (tokens['color.surfaceContainer'])
        vars['--color-bg-secondary'] = tokens['color.surfaceContainer'];
    if (tokens['color.surfaceContainerHigh'])
        vars['--color-bg-tertiary'] = tokens['color.surfaceContainerHigh'];
    if (tokens['color.surfaceContainerLow'])
        vars['--color-bg-card'] = tokens['color.surfaceContainerLow'];
    if (tokens['color.surfaceContainerHigh'])
        vars['--color-bg-hover'] = tokens['color.surfaceContainerHigh'];
    if (tokens['color.outline'])
        vars['--color-border-primary'] = tokens['color.outline'];
    if (tokens['color.outlineVariant'])
        vars['--color-border-secondary'] = tokens['color.outlineVariant'];
    if (tokens['color.error'])
        vars['--color-border-error'] = tokens['color.error'];
    return vars;
}
/** Apply a CSS variable map to the document root */
export function applyCssVars(vars) {
    const root = document.documentElement;
    for (const [prop, value] of Object.entries(vars)) {
        root.style.setProperty(prop, value);
    }
}
/**
 * Resolve `{reference}` syntax in token values.
 * Component tokens (Tier 3) reference semantic tokens (Tier 2) using
 * `{key.path}` notation — e.g. `'{color.primary}'`.
 * This single-pass resolver replaces each reference with the concrete value
 * from the same merged map. If the referenced key is missing, it falls back
 * to a CSS `var(--key-path)` so the property still has a chance of working.
 */
export function resolveTokenReferences(tokens) {
    const resolved = { ...tokens };
    for (const [key, value] of Object.entries(resolved)) {
        if (!value.includes('{'))
            continue;
        resolved[key] = value.replace(/\{([^}]+)}/g, (_match, ref) => {
            if (ref in resolved) {
                return resolved[ref];
            }
            // Fallback: convert dot-path to CSS var reference
            return `var(--${ref.replace(/\./g, '-')})`;
        });
    }
    return resolved;
}
/** Convenience: convert IDK tokens and apply to :root in one step */
export function applyTokens(tokens) {
    applyCssVars(tokensToCssVars(tokens));
}
//# sourceMappingURL=css-injector.js.map