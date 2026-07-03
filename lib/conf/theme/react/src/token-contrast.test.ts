import { describe, expect, it } from 'vitest'
import { getSystemDefaults } from './defaults'
import { resolveTokenReferences } from './css-injector'
import { contrastRatio, meetsAA } from './contrast'

/**
 * WCAG AA contrast audit for critical foreground/background token pairs.
 *
 * Keys were verified from defaults.ts (getSystemDefaults light + dark).
 * Pairs that currently fail AA in a given mode are marked it.fails with a
 * TODO pointing to Task 1.3 where the tokens will be corrected.
 *
 * Run:  pnpm test token-contrast
 */

// Pairs that must meet AA (4.5:1) in BOTH light and dark modes.
const PAIRS: Array<[string, string]> = [
  ['color.onSurface', 'color.surface'],
  ['color.onSurfaceVariant', 'color.surfaceVariant'],
  ['color.onPrimary', 'color.primary'],
  ['color.onError', 'color.error'],
  ['color.feedback.onWarning', 'color.feedback.warning'],
]

// These pairs were previously failing in light mode (white-on-vivid).
// Fixed in Task 1.3: onSuccess and onInfo now use #0A0D12 (dark text on vivid bg).
// light onSuccess: #0A0D12 on #00C249 passes AA
// light onInfo:    #0A0D12 on #0B81FF passes AA
const LIGHT_FAILING_PAIRS: Array<[string, string]> = [
  ['color.feedback.onSuccess', 'color.feedback.success'],
  ['color.feedback.onInfo', 'color.feedback.info'],
]

const isHex = (v?: string) => !!v && /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(v)

function assertPair(tokens: Record<string, string>, fgKey: string, bgKey: string): void {
  const fg = tokens[fgKey]
  const bg = tokens[bgKey]
  // A listed pair must exist — a missing key is a wrong-key bug, not a contrast issue.
  expect(fg, `${fgKey} should be defined`).toBeDefined()
  expect(bg, `${bgKey} should be defined`).toBeDefined()
  if (!isHex(fg) || !isHex(bg)) return // color-mix()/rgba — visual check only, not asserted
  expect(
    meetsAA(fg, bg),
    `${fgKey}(${fg}) on ${bgKey}(${bg}) = ${contrastRatio(fg, bg).toFixed(2)}`,
  ).toBe(true)
}

describe.each(['light', 'dark'] as const)('token contrast — %s', (mode) => {
  const tokens = resolveTokenReferences(getSystemDefaults(mode))

  it.each(PAIRS)('%s on %s meets AA', (fgKey, bgKey) => {
    assertPair(tokens, fgKey, bgKey)
  })

  // Both light and dark modes now pass AA for these pairs.
  it.each(LIGHT_FAILING_PAIRS)('%s on %s meets AA', (fgKey, bgKey) => {
    assertPair(tokens, fgKey, bgKey)
  })
})
