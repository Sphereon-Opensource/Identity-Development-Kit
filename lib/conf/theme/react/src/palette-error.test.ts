import { describe, it, expect } from 'vitest'
import { getSystemDefaults } from './defaults'
import { resolveTokenReferences } from './css-injector'
import { menuTokenDefaults, selectionTokenDefaults, confirmTokenDefaults } from './component-token-defaults'

/**
 * Regression test — palette.error crimson ramp (Task 1.4b).
 * Verifies that the 10-stop palette.error ramp was re-tuned from the old
 * burnt-orange values to the approved crimson/rose scale, and that the
 * existing color.error semantic token (unchanged from Task 1.3) still
 * aligns with the new palette anchor (error.700 = #BE123C).
 */
describe('palette.error crimson ramp', () => {
  const t = resolveTokenReferences(getSystemDefaults('light'))

  it('error.700 is the crimson anchor and matches color.error', () => {
    expect(t['palette.error.700']).toBe('#BE123C')
    expect(t['color.error']).toBe('#BE123C')
  })

  it('error.100 matches the error container', () => {
    expect(t['palette.error.100']).toBe('#FFE4E6')
  })

  it('full crimson ramp stops are correct', () => {
    expect(t['palette.error.50']).toBe('#FFF1F2')
    expect(t['palette.error.200']).toBe('#FECDD3')
    expect(t['palette.error.300']).toBe('#FDA4AF')
    expect(t['palette.error.400']).toBe('#FB7185')
    expect(t['palette.error.500']).toBe('#F43F5E')
    expect(t['palette.error.600']).toBe('#E11D48')
    expect(t['palette.error.800']).toBe('#9F1239')
    expect(t['palette.error.900']).toBe('#881337')
  })
})

describe('danger component tokens resolve to crimson values', () => {
  // Component tokens live in separate maps; merge with system defaults then resolve.
  // Task 1.4c: danger foreground/icon now point at color.error (semantic, mode-adaptive);
  // tint backgrounds now point at color.errorContainer (semantic, mode-adaptive).
  // Only the saturated fill (selection.delete.backgroundHover) still uses palette.error.600.
  const t = resolveTokenReferences({
    ...getSystemDefaults('light'),
    ...menuTokenDefaults,
    ...selectionTokenDefaults,
    ...confirmTokenDefaults,
  })

  it('comp.menu.item.danger.foreground → color.error → #BE123C (semantic, AA)', () => {
    expect(t['comp.menu.item.danger.foreground']).toBe('#BE123C')
  })

  it('comp.menu.item.danger.backgroundHover → color.errorContainer → #FFE4E6 (semantic)', () => {
    expect(t['comp.menu.item.danger.backgroundHover']).toBe('#FFE4E6')
  })

  it('comp.selection.delete.backgroundHover → palette.error.600 → #E11D48', () => {
    expect(t['comp.selection.delete.backgroundHover']).toBe('#E11D48')
  })

  it('comp.confirm.icon.background → color.errorContainer → #FFE4E6 (semantic)', () => {
    expect(t['comp.confirm.icon.background']).toBe('#FFE4E6')
  })

  it('comp.confirm.icon.foreground → color.error → #BE123C (semantic, AA)', () => {
    expect(t['comp.confirm.icon.foreground']).toBe('#BE123C')
  })
})
