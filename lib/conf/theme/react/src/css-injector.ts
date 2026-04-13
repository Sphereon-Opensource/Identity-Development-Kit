/**
 * Maps IDK token dot-keys to CSS custom properties and applies them to :root.
 * Matches IDK's CssVariableGenerator convention: dots become hyphens.
 *
 * This is a pure TypeScript implementation mirroring the Kotlin/JS
 * CssTokenMapper + CssLegacyAliases from @sphereon/theme-web.
 */

import type { ThemeTokenMap } from './types'

/** Convert an IDK token key to a CSS custom property name */
export function tokenKeyToCssVar(key: string): string {
  return `--${key.replace(/\./g, '-')}`
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
    vars[tokenKeyToCssVar(key)] = isShadowKey(key) ? value : convertUnit(value)
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
 * This single-pass resolver replaces each reference with the concrete value
 * from the same merged map. If the referenced key is missing, it falls back
 * to a CSS `var(--key-path)` so the property still has a chance of working.
 */
export function resolveTokenReferences(tokens: ThemeTokenMap): ThemeTokenMap {
  const resolved: ThemeTokenMap = { ...tokens }

  for (const [key, value] of Object.entries(resolved)) {
    if (!value.includes('{')) continue

    resolved[key] = value.replace(/\{([^}]+)}/g, (_match, ref: string) => {
      if (ref in resolved) {
        return resolved[ref]
      }
      // Fallback: convert dot-path to CSS var reference
      return `var(--${ref.replace(/\./g, '-')})`
    })
  }

  return resolved
}

/** Convenience: convert IDK tokens and apply to :root in one step */
export function applyTokens(tokens: ThemeTokenMap): void {
  applyCssVars(tokensToCssVars(tokens))
}
