/**
 * Splice the generated comp-group Kotlin (core-kotlin-additions.txt) into:
 *   - TokenKeyConstants.kt  (after the last COMP_* const)
 *   - SystemDefaults.kt     (at the end of addComponentTokens())
 * Idempotent: bails if the first new const is already present.
 */
import {readFileSync, writeFileSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import {dirname, join} from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const core = join(here, '..', '..', '..', 'core', 'public', 'src', 'commonMain', 'kotlin', 'com', 'sphereon', 'conf', 'theme', 'core')
const TKC = join(core, 'token', 'TokenKeyConstants.kt')
const SD = join(core, 'defaults', 'SystemDefaults.kt')

const add = readFileSync(join(here, 'core-kotlin-additions.txt'), 'utf8')
const [, constsBlock, callsBlock] = add.split(/^\/\/ =====.*=====$/m)

const constsText = constsBlock.trim()
const callsText = callsBlock.trim()

// --- TokenKeyConstants.kt: insert after the last `const val COMP_` line ---
let tkc = readFileSync(TKC, 'utf8')
const firstNewConst = constsText.match(/const val (\w+)/)[1]
if (tkc.includes(`const val ${firstNewConst} `)) {
  console.log('TokenKeyConstants: already applied, skipping')
} else {
  const tkcLines = tkc.split('\n')
  let lastComp = -1
  for (let i = 0; i < tkcLines.length; i++) if (/const val COMP_/.test(tkcLines[i])) lastComp = i
  tkcLines.splice(lastComp + 1, 0, '', '    // ── Post-drift comp groups (synced from tokens.json) ──', constsText)
  writeFileSync(TKC, tkcLines.join('\n'))
  console.log(`TokenKeyConstants: inserted ${constsText.split('\n').filter((l) => l.includes('const val')).length} consts after line ${lastComp + 1}`)
}

// --- SystemDefaults.kt: insert before the close of addComponentTokens() ---
let sd = readFileSync(SD, 'utf8')
const firstNewCall = callsText.match(/TokenKeyConstants\.(\w+)/)[1]
if (sd.includes(`TokenKeyConstants.${firstNewCall},`)) {
  console.log('SystemDefaults: already applied, skipping')
} else {
  const anchor = '        color(TokenKeyConstants.COMP_BLOB_EXPLORER_EMPTY_ICON_COLOR, "{color.border.subtle}")'
  if (!sd.includes(anchor)) throw new Error('SystemDefaults anchor not found')
  sd = sd.replace(anchor, anchor + '\n\n' + callsText)
  writeFileSync(SD, sd)
  console.log(`SystemDefaults: inserted ${callsText.split('\n').filter((l) => l.includes('TokenKeyConstants.')).length} calls`)
}
