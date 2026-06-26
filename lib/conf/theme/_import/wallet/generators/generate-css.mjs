/**
 * Generator: tokens.json (+ tokens.dark.json) -> CSS custom-property blocks.
 *
 * Emits build/out/tokens.generated.css:
 *   :root { ...all light/base token vars... }
 *   [data-theme="dark"] { ...dark color overrides... }
 *
 * Var names + reference form come from the SHARED tokenKeyToCssVar (lib.mjs),
 * identical to theme-react's css-injector, so a product can mix theme-react's
 * runtime-injected vars and this static CSS with one vocabulary.
 *
 * `{refs}` are preserved as `var(--ref)` (CSS resolves them natively) — the CSS
 * stays a thin, readable token layer rather than a flattened value dump.
 *
 * Run: node build/generate-css.mjs
 */

import {readFileSync, writeFileSync, mkdirSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import {dirname, join} from 'node:path'
import {flattenDtcg, tokenKeyToCssVar, convertUnit, refsToCssVars} from './lib.mjs'

const here = dirname(fileURLToPath(import.meta.url))
const root = join(here, '..')
const outDir = join(here, 'out')
mkdirSync(outDir, {recursive: true})

const readJson = (p) => JSON.parse(readFileSync(p, 'utf8'))
const baseFlat = flattenDtcg(readJson(join(root, 'tokens.json')))
const darkFlat = flattenDtcg(readJson(join(root, 'tokens.dark.json')))

const tier = (k) => (k.startsWith('palette.') ? 1 : k.startsWith('comp.') ? 3 : 2)
const decl = ([k, v]) => `  ${tokenKeyToCssVar(k)}: ${refsToCssVars(convertUnit(k, v))};`

// --- ergonomic typography + font aliases ------------------------------------
// The type utility classes (h1, .t-h1, p, …) consume short --fs-*/--lh-*/--font-*
// names rather than the mechanical --text-style-* vars. Emit those aliases from
// the same source so the type scale is single-sourced too. Mobile sizes go in
// :root; the desktop scale goes in a @media (min-width:768px) override.
const baseMap2 = new Map(baseFlat)
const px = (v) => (v == null ? null : convertUnit('x', v))
const STYLES = Object.keys(readJson(join(root, 'tokens.json')).text.style)
const FAMILIES = [['sans', 'sans'], ['secondary', 'secondary'], ['meta', 'meta'], ['mono', 'mono']]

function fontAliases() {
  return FAMILIES.map(([css, key]) => `  --font-${css}: ${baseMap2.get(`text.family.${key}`)};`).join('\n')
}
// Semantic shadow aliases — the CSS uses short --shadow-{focus,active,…} names
// for the shadow.state.* group (focus ring, pressed, selected outline).
function shadowStateAliases() {
  return ['focus', 'error', 'active', 'selected']
    .map((s) => [s, baseMap2.get(`shadow.state.${s}`)])
    .filter(([, v]) => v)
    .map(([s, v]) => `  --shadow-${s}: ${v};`)
    .join('\n')
}
function typeAliases(desktop) {
  const out = []
  for (const s of STYLES) {
    const fs = px(baseMap2.get(desktop ? `text.style.${s}.desktop.fontSize` : `text.style.${s}.fontSize`))
    const lh = px(baseMap2.get(desktop ? `text.style.${s}.desktop.lineHeight` : `text.style.${s}.lineHeight`))
    if (fs) out.push(`  --fs-${s}: ${fs};`)
    if (lh) out.push(`  --lh-${s}: ${lh};`)
  }
  return out.join('\n')
}

// Group by tier for a readable file, preserving source order within each tier.
const byTier = (flat, t) => flat.filter(([k]) => tier(k) === t).map(decl).join('\n')

const css = `/* ==========================================================================
   tokens.generated.css — GENERATED from tokens.json + tokens.dark.json
   by build/generate-css.mjs. DO NOT EDIT.

   Var names match @sphereon/theme-react's runtime emission (shared
   tokenKeyToCssVar), so static CSS and the React provider agree. References are
   kept as var(--ref) so the cascade (incl. [data-theme="dark"]) resolves live.
   ========================================================================== */

:root {
  /* Tier 1 — palette primitives (internal; components consume Tier 2/3) */
${byTier(baseFlat, 1)}

  /* Tier 2 — semantic roles */
${byTier(baseFlat, 2)}

  /* Tier 3 — component tokens */
${byTier(baseFlat, 3)}

  /* Semantic shadow aliases (focus ring / pressed / selected) */
${shadowStateAliases()}

  /* Typography ergonomic aliases — font families + mobile type scale */
${fontAliases()}
${typeAliases(false)}
}

/* Desktop type scale (≥768px) — web/cloud surfaces bump up the reading scale. */
@media (min-width: 768px) {
  :root {
${typeAliases(true).replace(/^/gm, '  ')}
  }
}

[data-theme="dark"] {
${darkFlat.map(decl).join('\n')}
}
`

// Committed artifact at repo root (imported by colors_and_type.css); copy in
// build/out kept for coverage diffs.
writeFileSync(join(root, 'tokens.generated.css'), css)
writeFileSync(join(outDir, 'tokens.generated.css'), css)
console.log(
  `generated -> ${join(outDir, 'tokens.generated.css')}\n  :root vars=${baseFlat.length} (palette=${baseFlat.filter(([k]) => tier(k) === 1).length} ` +
    `semantic=${baseFlat.filter(([k]) => tier(k) === 2).length} comp=${baseFlat.filter(([k]) => tier(k) === 3).length})  dark overrides=${darkFlat.length}`,
)
