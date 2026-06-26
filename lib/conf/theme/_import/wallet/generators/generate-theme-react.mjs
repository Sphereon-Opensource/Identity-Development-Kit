/**
 * Generator: tokens.json (+ tokens.dark.json) -> theme-react TS token source.
 *
 *   tokens.json  ->  src/defaults.ts
 *                      palettes      = palette.*
 *                      sharedTokens  = everything except palette.* / color.* / comp.*
 *                      lightColors   = color.*  (base)
 *                      darkColors    = color.*  (tokens.dark.json overrides)
 *                      getSystemDefaults()
 *   tokens.json  ->  src/component-token-defaults.ts
 *                      one `<group>TokenDefaults` const per build/component-groups.json
 *                      + allComponentTokenDefaults merge
 *
 * Output dir defaults to build/out/ (for round-trip verification). Pass
 * `--out <dir>` to emit straight into a theme-react src/ for packing.
 *
 * Run: node build/generate-theme-react.mjs [--out <dir>]
 */

import {readFileSync, writeFileSync, mkdirSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import {dirname, join} from 'node:path'
import {flattenDtcg} from './lib.mjs'

const here = dirname(fileURLToPath(import.meta.url))
const root = join(here, '..')
const argOut = process.argv.indexOf('--out')
const outDir = argOut !== -1 ? process.argv[argOut + 1] : join(here, 'out')
mkdirSync(outDir, {recursive: true})

const readJson = (p) => JSON.parse(readFileSync(p, 'utf8'))
const base = readJson(join(root, 'tokens.json'))
const darkDoc = readJson(join(root, 'tokens.dark.json'))
const groups = readJson(join(here, 'component-groups.json'))

const baseFlat = flattenDtcg(base)
const darkFlat = flattenDtcg(darkDoc)
const baseMap = new Map(baseFlat)

const top = (k) => k.split('.')[0]
const palettes = baseFlat.filter(([k]) => top(k) === 'palette')
const lightColors = baseFlat.filter(([k]) => top(k) === 'color')
// sharedTokens excludes palette/color/comp; palettes are SPREAD INTO it below
// (mirrors the seed's `...palettes,` at the top of sharedTokens) so that
// getSystemDefaults carries the Tier-1 primitives for {palette.*} resolution.
const sharedOnly = baseFlat.filter(([k]) => !['palette', 'color', 'comp'].includes(top(k)))
const darkColors = darkFlat // dark override layer (color.* only)

/** Single-quoted TS string literal with escaping (matches theme-react style). */
const q = (s) => `'${String(s).replace(/\\/g, '\\\\').replace(/'/g, "\\'")}'`
const emitMap = (pairs, indent = '  ') => pairs.map(([k, v]) => `${indent}${q(k)}: ${q(v)},`).join('\n')

// ---- defaults.ts ----------------------------------------------------------
const defaultsTs = `/**
 * System default token maps — wallet-aligned light + dark themes.
 *
 * GENERATED from tokens.json + tokens.dark.json by build/generate-theme-react.mjs.
 * Do NOT hand-edit; change the DTCG source and regenerate.
 *
 * Three-tier: Tier 1 palette primitives, Tier 2 semantic roles, Tier 3 lives in
 * component-token-defaults.ts. Components consume Tier 2 / Tier 3 only.
 */

import type { ThemeTokenMap, ThemeVariant } from './types'

const palettes: ThemeTokenMap = {
${emitMap(palettes)}
}

const sharedTokens: ThemeTokenMap = {
  ...palettes,
${emitMap(sharedOnly)}
}

const lightColors: ThemeTokenMap = {
${emitMap(lightColors)}
}

const darkColors: ThemeTokenMap = {
${emitMap(darkColors)}
}

export function getSystemDefaults(variant: ThemeVariant): ThemeTokenMap {
  const colors = variant === 'dark' ? darkColors : lightColors
  return { ...sharedTokens, ...colors }
}
`

// ---- component-token-defaults.ts ------------------------------------------
const compConst = (g) => {
  const pairs = g.keys.map((k) => [k, baseMap.get(k)])
  const missing = pairs.filter(([, v]) => v === undefined)
  if (missing.length) console.warn(`[generate] ${g.const}: missing values for ${missing.map((m) => m[0]).join(', ')}`)
  return `export const ${g.const}: ThemeTokenMap = {\n${emitMap(pairs)}\n}`
}
const compTs = `/**
 * Tier 3 component token defaults.
 *
 * GENERATED from tokens.json (comp.*) by build/generate-theme-react.mjs.
 * Do NOT hand-edit; change the DTCG source and regenerate.
 */

import type { ThemeTokenMap } from './types'

${groups.map(compConst).join('\n\n')}

export const allComponentTokenDefaults: ThemeTokenMap = {
${groups.map((g) => `  ...${g.const},`).join('\n')}
}
`

writeFileSync(join(outDir, 'defaults.ts'), defaultsTs)
writeFileSync(join(outDir, 'component-token-defaults.ts'), compTs)
console.log(
  `generated -> ${outDir}\n  defaults.ts: palette=${palettes.length} shared=${sharedOnly.length} ` +
    `light=${lightColors.length} dark=${darkColors.length}\n  component-token-defaults.ts: groups=${groups.length} keys=${groups.reduce((n, g) => n + g.keys.length, 0)}`,
)
