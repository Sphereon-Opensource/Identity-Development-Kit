/**
 * Maps IDK token dot-keys to CSS custom properties and applies them to :root.
 * Matches IDK's CssVariableGenerator convention: dots become hyphens.
 *
 * This is a pure TypeScript implementation mirroring the Kotlin/JS
 * CssTokenMapper + CssLegacyAliases from @sphereon/theme-web.
 */

import type { ThemeTokenMap } from './types'

/**
 * Convert an IDK token key to a CSS custom property name. camelCase segments are
 * split to kebab-case and dots become hyphens, so the emitted variables are
 * idiomatic kebab CSS that hand-written stylesheets reference directly.
 * Example: `color.primary` -> `--color-primary`, `color.onSurface` -> `--color-on-surface`.
 *
 * Two namespace normalisations keep the emitted vars in lock-step with the
 * canonical design-system CSS (`colors_and_type.css`):
 *   - the `spacing.*` scale is published as `--space-*` (the established CSS prefix)
 *   - fractional steps use a hyphen, not the key's underscore: `spacing.0_5` -> `--space-0-5`
 */
export function tokenKeyToCssVar(key: string): string {
  const kebab = key
    .replace(/^spacing\./, 'space.')
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .toLowerCase()
    .replace(/_/g, '-')
    .replace(/\./g, '-')
  return `--${kebab}`
}

/** Check if a token key is a shadow token (pass-through, no unit conversion) */
function isShadowKey(key: string): boolean {
  return key.startsWith('shadow.')
}

/** Convert IDK dimension units to CSS-compatible values */
function convertUnit(value: string): string {
  return value.replace(/(\d+(?:\.\d+)?)\s*(?:sp|dp)/g, '$1px')
}

/**
 * Convert a flat IDK token map to CSS custom properties.
 * Token keys use dot notation (e.g. `color.primary`) which maps to
 * CSS custom properties with hyphens (e.g. `--color-primary`).
 */
export function tokensToCssVars(tokens: ThemeTokenMap): Record<string, string> {
  const vars: Record<string, string> = {}

  for (const [key, value] of Object.entries(tokens)) {
    // Shadow values use CSS box-shadow syntax — pass through without unit conversion
    const cssValue = isShadowKey(key) ? value : convertUnit(value)
    vars[tokenKeyToCssVar(key)] = cssValue
    // Backward-compat: the `spacing.*` scale is now published as `--space-*`, but
    // existing consumers may still reference the legacy `--spacing-*` name. Emit
    // both so neither old nor new code breaks. (e.g. spacing.0_5 -> --space-0-5
    // AND --spacing-0_5; spacing.inline.md -> --space-inline-md AND --spacing-inline-md.)
    if (key.startsWith('spacing.')) {
      const legacy = `--${key.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase().replace(/\./g, '-')}`
      vars[legacy] = cssValue
    }
  }

  return vars
}

/** Apply a CSS variable map to the document root */
export function applyCssVars(vars: Record<string, string>): void {
  const root = document.documentElement
  for (const [prop, value] of Object.entries(vars)) {
    root.style.setProperty(prop, value)
  }
}

/**
 * Resolve `{reference}` syntax in token values.
 * Component tokens (Tier 3) reference semantic tokens (Tier 2) using
 * `{key.path}` notation — e.g. `'{color.primary}'`.
 * Resolves the full chain so component tokens can reference semantic tokens
 * that reference palette tokens. Missing or cyclic references fall back to a
 * CSS `var(--key-path)` so the property still has a chance of working.
 */
export function resolveTokenReferences(tokens: ThemeTokenMap): ThemeTokenMap {
  const cache: ThemeTokenMap = {}
  const resolving = new Set<string>()
  const hasCached = (key: string): boolean => Object.prototype.hasOwnProperty.call(cache, key)

  const resolveValue = (value: string): string => {
    if (!value.includes('{')) return value

    return value.replace(/\{([^}]+)}/g, (_match, ref: string) => {
      if (!Object.prototype.hasOwnProperty.call(tokens, ref)) {
        return `var(${tokenKeyToCssVar(ref)})`
      }
      if (resolving.has(ref)) {
        return `var(${tokenKeyToCssVar(ref)})`
      }
      if (hasCached(ref)) {
        return cache[ref]
      }

      resolving.add(ref)
      cache[ref] = resolveValue(tokens[ref])
      resolving.delete(ref)
      return cache[ref]
    })
  }

  for (const [key, value] of Object.entries(tokens)) {
    if (hasCached(key)) continue
    resolving.add(key)
    cache[key] = resolveValue(value)
    resolving.delete(key)
  }

  return { ...tokens, ...cache }
}

/** Convenience: convert IDK tokens and apply to :root in one step */
export function applyTokens(tokens: ThemeTokenMap): void {
  applyCssVars(tokensToCssVars(tokens))
}
