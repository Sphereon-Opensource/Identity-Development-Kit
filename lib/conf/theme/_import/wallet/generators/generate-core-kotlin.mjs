/**
 * Emit Kotlin for:
 *  1. (NEW) Semantic color layer — light + dark:
 *     color.*, color.gradient.*, shadow.focusRing/errorRing, comp.focus.ring, comp.error.ring
 *  2. (EXISTING) comp groups that exist in tokens.json (post-drift additions)
 *
 * Reads BOTH tokens.json (light/base) and tokens.dark.json (dark override layer).
 *
 * Produces 5 paste-blocks to generators/core-kotlin-additions.txt:
 *
 *   (A) TokenKeyConstants additions  — ONLY const val declarations for keys NOT yet in
 *       TokenKeyConstants.kt (i.e. genuinely new: COLOR_GRADIENT_*, SHADOW_*_RING,
 *       COMP_FOCUS_RING, COMP_ERROR_RING).
 *   (B) baseline { } semantic block  — DSL calls for every color.* + color.gradient.* +
 *       shadow.focusRing/errorRing + comp.focus.ring/comp.error.ring, using light values.
 *   (C) baselineDark { } override    — DSL calls ONLY for tokens whose dark value differs
 *       from light (shadow.errorRing and color.primary / accent / interactive.focus are the
 *       same in both layers and are correctly omitted here).
 *   (D) TokenKeyConstants comp group const declarations (existing behaviour, additive).
 *   (E) SystemDefaults addComponentTokens() comp group DSL calls (existing behaviour).
 *
 * DSL dispatch rules:
 *   linear/radial/conic-gradient(...) → string()
 *   shadow.focusRing/errorRing   → shadow()
 *   {shadow.*} refs              → shadow()
 *   hex / rgba / color-mix / {color.*} / {palette.*} → color()
 *   comp leaf names (spacing/dimension/string) → as before
 *
 * Run: node generators/generate-core-kotlin.mjs [group1 group2 ...]
 *      groups default to the 10 post-drift additions.
 */

import {readFileSync, writeFileSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import {dirname, join} from 'node:path'
import {flattenDtcg} from './lib.mjs'

const here = dirname(fileURLToPath(import.meta.url))
const tokens = JSON.parse(readFileSync(join(here, '..', 'tokens.json'), 'utf8'))
const tokensDark = JSON.parse(readFileSync(join(here, '..', 'tokens.dark.json'), 'utf8'))

const GROUPS = process.argv.slice(2).length
  ? process.argv.slice(2)
  : ['menu', 'panel', 'statustab', 'form', 'formsection', 'forminput', 'emptystate', 'selection', 'confirm', 'meatball']

// ─── Const-name algorithm ─────────────────────────────────────────────────────
//
// Works for ANY top-level namespace, not just comp.*:
//   color.primary               → COLOR_PRIMARY
//   color.surfaceContainerHigh  → COLOR_SURFACE_CONTAINER_HIGH
//   color.feedback.onSuccess    → COLOR_FEEDBACK_ON_SUCCESS
//   color.navigation.activeForeground → COLOR_NAVIGATION_ACTIVE_FOREGROUND
//   color.gradient.brandSubtle  → COLOR_GRADIENT_BRAND_SUBTLE
//   shadow.focusRing            → SHADOW_FOCUS_RING
//   comp.focus.ring             → COMP_FOCUS_RING
//   comp.menu.item.backgroundHover → COMP_MENU_ITEM_BACKGROUND_HOVER   (unchanged)

const tokenConstName = (key) => {
  const prefix = key.split('.')[0]
  const rest = key.slice(prefix.length + 1) // everything after the first dot
  return (
    prefix.toUpperCase() +
    '_' +
    rest
      .replace(/([a-z0-9])([A-Z])/g, '$1_$2') // camelCase → under_Score
      .replace(/\./g, '_') // dots → underscores
      .toUpperCase()
  )
}

// ─── Parse existing token keys from TokenKeyConstants.kt ──────────────────────
// Used to determine which consts are genuinely new (for Block A).
const KT_PATH = join(
  here,
  '../../../core/public/src/commonMain/kotlin/com/sphereon/conf/theme/core/token/TokenKeyConstants.kt',
)
const ktSrc = readFileSync(KT_PATH, 'utf8')
const existingKeys = new Set()
const ktConstRe = /const val \w+ = "([^"]+)"/g
let m
while ((m = ktConstRe.exec(ktSrc))) {
  existingKeys.add(m[1])
}

// ─── DSL dispatch ─────────────────────────────────────────────────────────────

/**
 * Semantic-layer DSL dispatch.
 * Handles color.*, shadow.focusRing/errorRing, comp.focus.ring, comp.error.ring.
 * Note: color.shadow has leaf "shadow" but its value is rgba — correctly falls through
 * to 'color' since the key-based check fires first.
 */
function dslFnSemantic(key, value) {
  // Gradient strings → string()
  if (/^(?:linear|radial|conic)-gradient\(/.test(value)) return 'string'
  // Named shadow ring keys → shadow()
  if (key === 'shadow.focusRing' || key === 'shadow.errorRing') return 'shadow'
  // Values that ARE a shadow reference → shadow()
  if (/^\{shadow\./.test(value)) return 'shadow'
  // Everything else in the semantic layer is a color value
  // (hex, rgba, color-mix, {color.*}, {palette.*})
  return 'color'
}

/**
 * Comp-group DSL dispatch (unchanged from original).
 */
function dslFn(key, value) {
  const leaf = key.split('.').pop()
  if (leaf === 'fontWeight') return 'string'
  if (/^(padding|paddingX|paddingY|gap)$/.test(leaf)) return 'spacing'
  if (/^(radius|borderWidth|fontSize|size|iconSize|height|minWidth|maxWidth|width)$/.test(leaf)) return 'dimension'
  if (leaf === 'shadow' || /^\{shadow\./.test(value)) return 'shadow'
  // color: hex / transparent / rgba / color-mix / {color.*} / {palette.*}, or a color-ish leaf
  if (/^(#|transparent|rgba|color-mix|\{color\.|\{palette\.)/.test(value)) return 'color'
  if (/(background|foreground|border|overlay|color)/i.test(leaf)) return 'color'
  // dimension fallback for {spacing.*} used as a single value, else string
  if (/^\{spacing\./.test(value)) return 'spacing'
  if (/^\{shape\.|^\{borderWidth\.|dp$|px$/.test(value)) return 'dimension'
  return 'string'
}

// ─── Semantic token set ───────────────────────────────────────────────────────
//
// Scope:
//   color.*        (all semantic colors + color.gradient.*)
//   shadow.focusRing, shadow.errorRing
//   comp.focus.ring, comp.error.ring
//
// Excludes: palette.*, typography, spacing, etc. (those are handled separately).

const flatLight = flattenDtcg(tokens)
const flatDark = flattenDtcg(tokensDark)
const darkMap = new Map(flatDark)

const SEMANTIC_TOKENS = flatLight.filter(
  ([k]) =>
    k.startsWith('color.') ||
    k === 'shadow.focusRing' ||
    k === 'shadow.errorRing' ||
    k === 'comp.focus.ring' ||
    k === 'comp.error.ring',
)

// ─── Block A: new const val declarations ──────────────────────────────────────
const blockALines = []
for (const [key] of SEMANTIC_TOKENS) {
  if (!existingKeys.has(key)) {
    const c = tokenConstName(key)
    blockALines.push(`    const val ${c} = "${key}"`)
  }
}

// ─── Block B: baseline (light) semantic DSL calls ─────────────────────────────
const blockBLines = []
for (const [key, value] of SEMANTIC_TOKENS) {
  const c = tokenConstName(key)
  blockBLines.push(`        ${dslFnSemantic(key, value)}(TokenKeyConstants.${c}, "${value}")`)
}

// ─── Block C: baselineDark override DSL calls ─────────────────────────────────
// Emit only tokens whose dark value DIFFERS from light.
// shadow.errorRing is identical in both layers → correctly omitted.
// color.primary, color.accent, color.interactive.focus are identical → omitted.
const blockCLines = []
for (const [key, value] of SEMANTIC_TOKENS) {
  const darkValue = darkMap.get(key)
  if (darkValue !== undefined && darkValue !== value) {
    const c = tokenConstName(key)
    blockCLines.push(`        ${dslFnSemantic(key, darkValue)}(TokenKeyConstants.${c}, "${darkValue}")`)
  }
}

// ─── Blocks D + E: existing comp group behaviour ─────────────────────────────
const flat = flattenDtcg({comp: tokens.comp})

const blockDLines = [] // comp group const declarations
const blockELines = [] // comp group DSL calls

for (const g of GROUPS) {
  const groupPairs = flat.filter(([k]) => k.startsWith(`comp.${g}.`))
  if (!groupPairs.length) {
    console.warn(`[warn] no tokens for comp.${g}.* in tokens.json`)
    continue
  }
  blockDLines.push(`    // ${g}`)
  blockELines.push(`        // ${g[0].toUpperCase()}${g.slice(1)}`)
  for (const [key, value] of groupPairs) {
    const c = tokenConstName(key)
    blockDLines.push(`    const val ${c} = "${key}"`)
    blockELines.push(`        ${dslFn(key, value)}(TokenKeyConstants.${c}, "${value}")`)
  }
  blockDLines.push('')
  blockELines.push('')
}

// ─── Write output ─────────────────────────────────────────────────────────────
const sep = (label, count = '') =>
  `// ===== ${label}${count ? ` (${count})` : ''} =====`

const out =
  sep('(A) PASTE INTO TokenKeyConstants.kt — NEW const val declarations', `${blockALines.length} new keys`) +
  '\n\n' +
  blockALines.join('\n') +
  '\n\n' +
  sep('(B) PASTE INTO SystemDefaults.kt  baseline { } — semantic light layer', `${blockBLines.length} tokens`) +
  '\n\n' +
  blockBLines.join('\n') +
  '\n\n' +
  sep(
    '(C) PASTE INTO SystemDefaults.kt  baselineDark { } — dark override layer',
    `${blockCLines.length} tokens differ from light`,
  ) +
  '\n\n' +
  blockCLines.join('\n') +
  '\n\n' +
  sep('(D) PASTE INTO TokenKeyConstants.kt — comp group const declarations') +
  '\n\n' +
  blockDLines.join('\n') +
  '\n\n' +
  sep('(E) PASTE INTO SystemDefaults.kt  addComponentTokens() — comp group DSL calls') +
  '\n\n' +
  blockELines.join('\n')

writeFileSync(join(here, 'core-kotlin-additions.txt'), out)
console.log('wrote core-kotlin-additions.txt')
console.log(`  Block A: ${blockALines.length} new const declarations`)
console.log(`  Block B: ${blockBLines.length} semantic baseline tokens`)
console.log(`  Block C: ${blockCLines.length} dark override tokens`)
console.log(`  Blocks D+E: ${GROUPS.length} comp groups`)
const dslCalls = blockELines.filter((l) => l.includes('TokenKeyConstants')).length
console.log(`  Total comp DSL calls: ${dslCalls}`)
