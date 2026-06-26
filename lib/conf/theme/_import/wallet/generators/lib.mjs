/**
 * Shared token-pipeline helpers.
 *
 * The design system's single source of truth is `tokens.json` (DTCG: nested
 * `{ "$value": ... }` leaves, with `{dotted.ref}` references) plus
 * `tokens.dark.json` (the dark-mode override layer, same shape, partial).
 *
 * theme-react's TS token maps already speak the same dialect — flat dot-keyed
 * `Record<string,string>` whose values use `{dotted.ref}` — so the import and
 * generate steps are mostly nest/flatten + reference passthrough.
 */

import {readFileSync} from 'node:fs'

/** The bare font-family identifiers referenced inside theme-react's sharedTokens. */
export function extractIdentifierConsts(src) {
  const out = {}
  const re = /const\s+([A-Za-z_]\w*)\s*=\s*(["'])((?:[^\\]|\\.)*?)\2\s*(?:\n|$)/g
  let m
  while ((m = re.exec(src))) out[m[1]] = m[3]
  return out
}

/**
 * Extracts the body text of a `const NAME ... = { ... }` block whose closing
 * brace sits at column 0 (true for every theme-react token map).
 */
export function extractBlockBody(src, constName) {
  const lines = src.split('\n')
  const startRe = new RegExp(`^(?:export\\s+)?const\\s+${constName}\\b[^=]*=\\s*\\{`)
  let i = lines.findIndex((l) => startRe.test(l))
  if (i === -1) return null
  const body = []
  for (i = i + 1; i < lines.length; i++) {
    if (/^\}/.test(lines[i])) break
    body.push(lines[i])
  }
  return body.join('\n')
}

/**
 * Parses `'key': 'value'` / `'key': identifier` pairs from a block body.
 * Quoted values are captured whole (internal commas in `color-mix(...)` are
 * preserved). Bare identifiers are resolved via `idents`. Returns an ordered
 * array of [key, value] so emitted output keeps source order.
 */
export function parsePairs(body, idents = {}) {
  const out = []
  const re = /'([\w.]+)'\s*:\s*(?:'((?:[^'\\]|\\.)*)'|([A-Za-z_]\w*))/g
  let m
  while ((m = re.exec(body))) {
    const key = m[1]
    // Quoted values: unescape JS string escapes for quotes/backslash so a value
    // re-read from generated TS (which escapes `'`) equals the original.
    const val = m[2] !== undefined ? m[2].replace(/\\(['"\\])/g, '$1') : (idents[m[3]] ?? m[3])
    out.push([key, val])
  }
  return out
}

/**
 * Flat dot-key map -> nested DTCG object with `{ "$value": ... }` leaves.
 * Handles keys that are BOTH a value and a parent (e.g. `comp.input.border`
 * and `comp.input.border.focus`): the node carries `$value` AND child keys.
 */
export function nestToDtcg(pairs) {
  const root = {}
  for (const [key, value] of pairs) {
    const parts = key.split('.')
    let node = root
    for (let i = 0; i < parts.length - 1; i++) {
      node[parts[i]] ??= {}
      node = node[parts[i]]
    }
    const last = parts[parts.length - 1]
    if (node[last] && typeof node[last] === 'object') node[last].$value = value
    else node[last] = {$value: value}
  }
  return root
}

/**
 * Nested DTCG object -> ordered flat [[dotKey, value], ...] (depth-first).
 * A node may be both a leaf (`$value`) and a group: emit the leaf, then recurse
 * into its children, so leaf-and-group keys round-trip cleanly.
 */
export function flattenDtcg(obj, prefix = '', out = []) {
  for (const [k, v] of Object.entries(obj)) {
    if (k.startsWith('$')) continue
    if (!v || typeof v !== 'object') continue
    const key = prefix ? `${prefix}.${k}` : k
    if ('$value' in v) out.push([key, v.$value])
    flattenDtcg(v, key, out)
  }
  return out
}

/** Resolves `{dotted.ref}` references against a flat key->value map (transitively). */
export function resolveRefs(flat) {
  const map = new Map(flat)
  const seen = new Set()
  const resolve = (val, trail = []) =>
    String(val).replace(/\{([\w.]+)\}/g, (whole, ref) => {
      if (trail.includes(ref)) return whole // cycle guard
      const next = map.get(ref)
      return next === undefined ? whole : resolve(next, [...trail, ref])
    })
  return flat.map(([k, v]) => [k, resolve(v)])
}

/**
 * Token dot-key -> CSS custom property name. MUST stay identical to theme-react's
 * `tokenKeyToCssVar` (packages/theme-react/src/css-injector.ts) so the generated
 * CSS and theme-react's runtime-injected vars share one vocabulary:
 *   - the `spacing.*` scale publishes as `--space-*`
 *   - fraction steps hyphenate: `spacing.0_5` -> `--space-0-5`
 *   - camelCase -> kebab, dots -> hyphens
 */
export function tokenKeyToCssVar(key) {
  const kebab = key
    .replace(/^spacing\./, 'space.')
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .toLowerCase()
    .replace(/_/g, '-')
    .replace(/\./g, '-')
  return `--${kebab}`
}

/** IDK dimension units (sp/dp) -> px, mirroring theme-react's convertUnit. Shadows pass through. */
export function convertUnit(key, value) {
  if (key.startsWith('shadow.')) return value
  return String(value).replace(/(\d+(?:\.\d+)?)\s*(?:sp|dp)/g, '$1px')
}

/** Rewrites `{dotted.ref}` -> `var(--css-name)` (the CSS-side reference form). */
export function refsToCssVars(value) {
  return String(value).replace(/\{([\w.]+)\}/g, (_m, ref) => `var(${tokenKeyToCssVar(ref)})`)
}

export const readText = (p) => readFileSync(p, 'utf8')
