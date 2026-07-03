/**
 * Regression test for Task 1.4c — danger component tokens must be AA in both modes.
 *
 * comp.menu.item.danger.* / comp.confirm.icon.* live in allComponentTokenDefaults (Tier 3),
 * so we merge them with getSystemDefaults before resolving references — same pattern as
 * ring-tokens.test.ts.
 *
 * Expected contrast ratios (post 1.4c fix):
 *   danger foreground (color.error) on color.surface:
 *     light (#BE123C on #FFFFFF) = 6.29  ✓ AA
 *     dark  (#FFB3C1 on #161922) = 10.42 ✓ AA
 *   confirm icon foreground (color.error) on icon background (color.errorContainer):
 *     light (#BE123C on #FFE4E6) = 5.24  ✓ AA
 *     dark  (#FFB3C1 on #9F1239) = 4.76  ✓ AA
 */

import { describe, expect, it } from 'vitest'
import { getSystemDefaults } from './defaults'
import { allComponentTokenDefaults } from './component-token-defaults'
import { resolveTokenReferences } from './css-injector'
import { contrastRatio, meetsAA } from './contrast'

const isHex = (v?: string) => !!v && /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(v)

function assertAA(tokens: Record<string, string>, fgKey: string, bgKey: string): void {
  const fg = tokens[fgKey]
  const bg = tokens[bgKey]
  expect(fg, `${fgKey} should resolve to a hex color`).toBeDefined()
  expect(bg, `${bgKey} should resolve to a hex color`).toBeDefined()
  if (!isHex(fg) || !isHex(bg)) return
  expect(
    meetsAA(fg, bg),
    `${fgKey}(${fg}) on ${bgKey}(${bg}) = ${contrastRatio(fg, bg).toFixed(2)} — must be ≥ 4.5`,
  ).toBe(true)
}

describe.each(['light', 'dark'] as const)('danger token contrast — %s', (mode) => {
  const tokens = resolveTokenReferences({ ...getSystemDefaults(mode), ...allComponentTokenDefaults })

  it('comp.menu.item.danger.foreground on color.surface meets AA', () => {
    assertAA(tokens, 'comp.menu.item.danger.foreground', 'color.surface')
  })

  it('comp.confirm.icon.foreground on comp.confirm.icon.background meets AA', () => {
    assertAA(tokens, 'comp.confirm.icon.foreground', 'comp.confirm.icon.background')
  })
})
