/**
 * Emit the Kotlin for the comp groups that exist in tokens.json but not yet in
 * IDK core (the post-drift additions). Produces two snippets to paste into:
 *   - TokenKeyConstants.kt   (the `const val COMP_*` declarations)
 *   - SystemDefaults.kt      (the `color()/dimension()/spacing()/shadow()/string()` calls
 *                             inside addComponentTokens())
 *
 * Compose + web + react derive from these (compose reads SystemDefaults; react/web are
 * generated from the same tokens.json), so core is the only Kotlin to edit.
 *
 * Run: node build/generate-core-kotlin.mjs <group1> <group2> ...
 *   (groups default to the 10 post-drift additions)
 */

import {readFileSync, writeFileSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import {dirname, join} from 'node:path'
import {flattenDtcg} from './lib.mjs'

const here = dirname(fileURLToPath(import.meta.url))
const tokens = JSON.parse(readFileSync(join(here, '..', 'tokens.json'), 'utf8'))

const GROUPS = process.argv.slice(2).length
  ? process.argv.slice(2)
  : ['menu', 'panel', 'statustab', 'form', 'formsection', 'forminput', 'emptystate', 'selection', 'confirm', 'meatball']

const flat = flattenDtcg({comp: tokens.comp})

/** comp.menu.item.backgroundHover -> COMP_MENU_ITEM_BACKGROUND_HOVER */
const constName = (key) =>
  'COMP_' +
  key
    .replace(/^comp\./, '')
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/\./g, '_')
    .toUpperCase()

/** Pick the TokenBuilder DSL function for a token, mirroring the existing core patterns. */
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

const constsOut = []
const callsOut = []
for (const g of GROUPS) {
  const groupPairs = flat.filter(([k]) => k.startsWith(`comp.${g}.`))
  if (!groupPairs.length) {
    console.warn(`[warn] no tokens for comp.${g}.* in tokens.json`)
    continue
  }
  constsOut.push(`    // ${g}`)
  callsOut.push(`        // ${g[0].toUpperCase()}${g.slice(1)}`)
  for (const [key, value] of groupPairs) {
    const c = constName(key)
    constsOut.push(`    const val ${c} = "${key}"`)
    callsOut.push(`        ${dslFn(key, value)}(TokenKeyConstants.${c}, "${value}")`)
  }
  constsOut.push('')
  callsOut.push('')
}

const out = `// ===== PASTE INTO TokenKeyConstants.kt (inside the object, with the other COMP_* consts) =====\n\n${constsOut.join('\n')}\n\n// ===== PASTE INTO SystemDefaults.kt addComponentTokens() (after the existing comp blocks) =====\n\n${callsOut.join('\n')}\n`
writeFileSync(join(here, 'core-kotlin-additions.txt'), out)
console.log(`wrote core-kotlin-additions.txt — ${GROUPS.length} groups, ${callsOut.filter((l) => l.includes('TokenKeyConstants')).length} tokens`)
